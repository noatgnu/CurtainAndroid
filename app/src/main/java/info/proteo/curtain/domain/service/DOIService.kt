package info.proteo.curtain.domain.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import info.proteo.curtain.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DOIService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private val dataciteBaseURL = "https://api.datacite.org/dois"

    suspend fun fetchMetadata(doi: String): DataCiteMetadata = withContext(Dispatchers.IO) {
        val cleanDOI = doi.replace("doi.org/", "")

        val request = Request.Builder()
            .url("$dataciteBaseURL/$cleanDOI")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw DOIError.MetadataFetchFailed
        }

        val body = response.body?.string() ?: throw DOIError.MetadataFetchFailed

        try {
            gson.fromJson(body, DataCiteMetadata::class.java)
        } catch (e: Exception) {
            android.util.Log.e("DOIService", "Failed to parse metadata", e)
            throw DOIError.MetadataFetchFailed
        }
    }

    suspend fun parseAlternateIdentifiers(alternateIdentifiers: List<AlternateIdentifier>): DOIParsedData? =
        withContext(Dispatchers.IO) {
            for (identifier in alternateIdentifiers) {
                if (identifier.alternateIdentifierType.lowercase() == "url") {
                    val urlString = identifier.alternateIdentifier

                    val parsedData = try {
                        fetchAndParseCollectionMetadata(urlString)
                    } catch (e: Exception) {
                        null
                    }

                    if (parsedData != null) {
                        return@withContext parsedData
                    }

                    return@withContext DOIParsedData(
                        mainSessionUrl = urlString,
                        collectionMetadata = null
                    )
                }
            }

            null
        }

    private suspend fun fetchAndParseCollectionMetadata(urlString: String): DOIParsedData? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null

                val jsonObject = gson.fromJson(body, JsonObject::class.java)

                val mainSessionUrl = jsonObject.get("mainSessionUrl")?.asString
                val collectionData = jsonObject.getAsJsonObject("collectionMetadata")

                if (mainSessionUrl != null && collectionData != null) {
                    val title = collectionData.get("title")?.asString
                    val description = collectionData.get("description")?.asString
                    val sessionLinks = mutableListOf<DOISessionLink>()

                    val allSessions = collectionData.getAsJsonArray("allSessionLinks")
                    if (allSessions != null) {
                        for (sessionElement in allSessions) {
                            val session = sessionElement.asJsonObject
                            val sessionId = session.get("sessionId")?.asString
                            val sessionUrl = session.get("sessionUrl")?.asString
                            val sessionTitle = session.get("title")?.asString

                            if (sessionId != null && sessionUrl != null) {
                                sessionLinks.add(
                                    DOISessionLink(
                                        sessionId = sessionId,
                                        sessionUrl = sessionUrl,
                                        title = sessionTitle
                                    )
                                )
                            }
                        }
                    }

                    val metadata = DOICollectionMetadata(
                        title = title,
                        description = description,
                        allSessionLinks = sessionLinks
                    )

                    return@withContext DOIParsedData(
                        mainSessionUrl = mainSessionUrl,
                        collectionMetadata = metadata
                    )
                }

                null
            } catch (e: Exception) {
                android.util.Log.e("DOIService", "Failed to parse collection metadata", e)
                null
            }
        }

    suspend fun fetchSessionData(urlString: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlString)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw DOIError.SessionDataFetchFailed
        }

        val body = response.body?.string() ?: throw DOIError.SessionDataFetchFailed

        try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(body, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            android.util.Log.e("DOIService", "Failed to parse session data", e)
            throw DOIError.SessionDataFetchFailed
        }
    }

    suspend fun loadSessionFromDOI(doi: String, sessionId: String? = null): Map<String, Any> =
        withContext(Dispatchers.IO) {
            val metadata = fetchMetadata(doi)

            if (metadata.data.attributes.alternateIdentifiers.isEmpty()) {
                throw DOIError.NoAlternateIdentifiers
            }

            val parsedData = parseAlternateIdentifiers(metadata.data.attributes.alternateIdentifiers)

            if (parsedData != null) {
                if (sessionId != null && parsedData.collectionMetadata != null) {
                    for (session in parsedData.collectionMetadata.allSessionLinks) {
                        if (session.sessionId == sessionId) {
                            return@withContext fetchSessionData(session.sessionUrl)
                        }
                    }
                }

                if (parsedData.mainSessionUrl != null) {
                    return@withContext fetchSessionData(parsedData.mainSessionUrl)
                }
            }

            for (identifier in metadata.data.attributes.alternateIdentifiers.reversed()) {
                if (identifier.alternateIdentifierType.lowercase() == "url") {
                    try {
                        return@withContext fetchSessionData(identifier.alternateIdentifier)
                    } catch (e: Exception) {
                        continue
                    }
                }
            }

            throw DOIError.NoAlternateIdentifiers
        }
}
