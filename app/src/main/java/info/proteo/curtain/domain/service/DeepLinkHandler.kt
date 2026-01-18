package info.proteo.curtain.domain.service

import android.content.Intent
import android.net.Uri
import info.proteo.curtain.domain.repository.CurtainRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkHandler @Inject constructor(
    private val curtainRepository: CurtainRepository
) {

    suspend fun handleIntent(intent: Intent): DeepLinkResult? {
        val data = intent.data ?: return null
        return processUri(data)
    }

    suspend fun handleQRCode(qrContent: String): DeepLinkResult? {
        val uri = try {
            Uri.parse(qrContent)
        } catch (e: Exception) {
            return null
        }
        return processUri(uri)
    }

    fun parseQRCodeForDialog(qrContent: String): DeepLinkResult? {
        val uri = try {
            Uri.parse(qrContent)
        } catch (e: Exception) {
            return null
        }

        return when {
            uri.scheme == "curtain" && uri.host == "collection" -> {
                val collectionId = uri.getQueryParameter("collectionId")?.toIntOrNull()
                val apiURL = uri.getQueryParameter("apiURL")
                val frontendURL = uri.getQueryParameter("frontendURL")
                if (collectionId != null) {
                    DeepLinkResult.ParsedCollectionQRData(
                        collectionId = collectionId,
                        apiURL = apiURL,
                        frontendURL = frontendURL
                    )
                } else {
                    null
                }
            }
            uri.scheme == "curtain" && uri.host == "open" -> {
                val collectionId = uri.getQueryParameter("collectionId")?.toIntOrNull()
                if (collectionId != null) {
                    val apiURL = uri.getQueryParameter("apiURL")
                    val frontendURL = uri.getQueryParameter("frontendURL")
                    DeepLinkResult.ParsedCollectionQRData(
                        collectionId = collectionId,
                        apiURL = apiURL,
                        frontendURL = frontendURL
                    )
                } else {
                    val uniqueId = uri.getQueryParameter("uniqueId")
                    val apiURL = uri.getQueryParameter("apiURL")
                    val frontendURL = uri.getQueryParameter("frontendURL")
                    if (uniqueId != null) {
                        DeepLinkResult.ParsedQRData(
                            linkId = uniqueId,
                            apiURL = apiURL,
                            frontendURL = frontendURL
                        )
                    } else {
                        null
                    }
                }
            }
            uri.host?.contains("curtain") == true -> {
                val fragment = uri.fragment
                val pathOrFragment = fragment?.removePrefix("/") ?: uri.path?.removePrefix("/")

                if (pathOrFragment?.startsWith("collection/") == true) {
                    val collectionId = pathOrFragment.removePrefix("collection/").split("/").firstOrNull()?.toIntOrNull()
                    if (collectionId != null) {
                        val baseUrl = "${uri.scheme}://${uri.host}"
                        val apiUrl = if (uri.host?.contains("curtain.proteo.info") == true) {
                            "https://api.curtain.proteo.info"
                        } else {
                            "$baseUrl/api"
                        }
                        return DeepLinkResult.ParsedCollectionQRData(
                            collectionId = collectionId,
                            apiURL = apiUrl,
                            frontendURL = qrContent
                        )
                    }
                }

                val linkId = if (fragment != null && fragment.isNotEmpty()) {
                    fragment.removePrefix("/").split("/").firstOrNull { it.isNotEmpty() && it != "home" }
                } else {
                    uri.pathSegments.lastOrNull()
                }

                if (linkId != null && linkId.isNotEmpty() && linkId.length > 10) {
                    val baseUrl = "${uri.scheme}://${uri.host}"
                    val apiUrl = if (uri.host?.contains("curtain.proteo.info") == true) {
                        "https://api.curtain.proteo.info"
                    } else {
                        "$baseUrl/api"
                    }
                    DeepLinkResult.ParsedQRData(
                        linkId = linkId,
                        apiURL = apiUrl,
                        frontendURL = qrContent
                    )
                } else {
                    null
                }
            }
            else -> {
                if (qrContent.length in 20..100 && !qrContent.contains(" ") && !qrContent.contains("/")) {
                    DeepLinkResult.ParsedQRData(
                        linkId = qrContent,
                        apiURL = null,
                        frontendURL = null
                    )
                } else {
                    null
                }
            }
        }
    }

    private suspend fun processUri(uri: Uri): DeepLinkResult? {
        return when {
            uri.scheme == "curtain" && uri.host == "collection" -> {
                processCollectionDeepLink(uri)
            }
            uri.scheme == "curtain" && uri.host == "open" -> {
                val collectionId = uri.getQueryParameter("collectionId")?.toIntOrNull()
                if (collectionId != null) {
                    processCollectionDeepLink(uri)
                } else {
                    processCurtainDeepLink(uri)
                }
            }
            uri.toString().contains("doi.org") -> {
                processDOILink(uri)
            }
            uri.host?.contains("curtain.proteo.info") == true -> {
                val path = uri.path ?: ""
                val fragment = uri.fragment ?: ""
                if (path.contains("collection/") || fragment.contains("collection/")) {
                    processCollectionWebLink(uri)
                } else {
                    processWebLink(uri)
                }
            }
            else -> null
        }
    }

    private fun processCollectionDeepLink(uri: Uri): DeepLinkResult? {
        val collectionId = uri.getQueryParameter("collectionId")?.toIntOrNull() ?: return null
        val apiURL = uri.getQueryParameter("apiURL") ?: return null
        val frontendURL = uri.getQueryParameter("frontendURL")

        return DeepLinkResult.CollectionData(
            collectionId = collectionId,
            apiURL = apiURL,
            frontendURL = frontendURL
        )
    }

    private fun processCollectionWebLink(uri: Uri): DeepLinkResult? {
        val path = uri.path ?: uri.fragment ?: return null
        val collectionPath = path.removePrefix("/").removePrefix("#/")

        if (!collectionPath.startsWith("collection/")) return null

        val collectionId = collectionPath.removePrefix("collection/")
            .split("/")
            .firstOrNull()
            ?.toIntOrNull() ?: return null

        return DeepLinkResult.CollectionData(
            collectionId = collectionId,
            apiURL = "https://api.curtain.proteo.info",
            frontendURL = uri.toString()
        )
    }

    private suspend fun processCurtainDeepLink(uri: Uri): DeepLinkResult? {
        val uniqueId = uri.getQueryParameter("uniqueId")
        val apiURL = uri.getQueryParameter("apiURL")
        val frontendURL = uri.getQueryParameter("frontendURL")

        return when {
            uniqueId != null && apiURL != null -> {
                val hostname = try {
                    Uri.parse(apiURL).host ?: return null
                } catch (e: Exception) {
                    return null
                }
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(uniqueId, hostname, frontendURL)
                if (result.isSuccess) {
                    DeepLinkResult.CurtainDataset(
                        linkId = uniqueId,
                        apiURL = apiURL,
                        frontendURL = frontendURL
                    )
                } else {
                    null
                }
            }
            else -> {
                val doi = uri.getQueryParameter("doi")
                val sessionId = uri.getQueryParameter("sessionId")
                if (doi != null) {
                    DeepLinkResult.DOIReference(
                        doi = doi,
                        sessionId = sessionId
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun processDOILink(uri: Uri): DeepLinkResult? {
        val doiPath = uri.path?.removePrefix("/") ?: return null
        return DeepLinkResult.DOIReference(
            doi = doiPath,
            sessionId = null
        )
    }

    private fun processWebLink(uri: Uri): DeepLinkResult? {
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) return null

        val linkId = pathSegments.lastOrNull()?.removePrefix("#/") ?: return null

        return DeepLinkResult.CurtainDataset(
            linkId = linkId,
            apiURL = "https://api.curtain.proteo.info",
            frontendURL = uri.toString()
        )
    }
}

sealed class DeepLinkResult {
    data class CurtainDataset(
        val linkId: String,
        val apiURL: String,
        val frontendURL: String?
    ) : DeepLinkResult()

    data class DOIReference(
        val doi: String,
        val sessionId: String?
    ) : DeepLinkResult()

    data class ParsedQRData(
        val linkId: String,
        val apiURL: String?,
        val frontendURL: String?
    ) : DeepLinkResult()

    data class CollectionData(
        val collectionId: Int,
        val apiURL: String,
        val frontendURL: String?
    ) : DeepLinkResult()

    data class ParsedCollectionQRData(
        val collectionId: Int,
        val apiURL: String?,
        val frontendURL: String?
    ) : DeepLinkResult()
}
