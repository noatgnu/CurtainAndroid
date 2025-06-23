package info.proteo.curtain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurtainRepository @Inject constructor(
    private val curtainApi: CurtainApi,
    private val curtainDao: CurtainDao,
    private val okHttpClient: OkHttpClient,
    private val moshiConverterFactory: MoshiConverterFactory,
    private val downloadClient: DownloadClient
) {
    // Get all curtains from the database
    fun getAllCurtains(): Flow<List<CurtainEntity>> {
        return curtainDao.getAll()
    }

    // Get curtains by hostname
    fun getCurtainsByHostname(hostname: String): Flow<List<CurtainEntity>> {
        return curtainDao.getAllByHostname(hostname)
    }

    // Get site settings
    fun getAllSiteSettings(): Flow<List<CurtainSiteSettings>> {
        return curtainDao.getAllSiteSettings()
    }

    fun getActiveSiteSettings(): Flow<List<CurtainSiteSettings>> {
        return curtainDao.getActiveSiteSettings()
    }

    // Create a custom API client for a specific host
    private fun createApiForHost(apiUrl: String): CurtainApi {
        return Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(okHttpClient)
            .addConverterFactory(moshiConverterFactory)
            .build()
            .create(CurtainApi::class.java)
    }

    // Sync curtains with the server
    suspend fun syncCurtains(hostname: String): List<CurtainEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = createApiForHost(hostname)
                val response = apiClient.getAllCurtains()

                if (response.isSuccessful && response.body() != null) {
                    val curtains = response.body()!!.map { it.toCurtainEntity(hostname) }
                    curtainDao.insertAll(curtains)
                    curtains
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to sync curtains: ${e.message}")
            }
        }
    }

    // Create a new curtain
    suspend fun createCurtain(
        hostname: String,
        file: File,
        description: String,
        curtainType: String,
        enable: Boolean,
        encrypted: Boolean,
        permanent: Boolean
    ): CurtainEntity {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = createApiForHost(hostname)

                val fileRequestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)

                val descriptionPart = description.toRequestBody("text/plain".toMediaTypeOrNull())
                val curtainTypePart = curtainType.toRequestBody("text/plain".toMediaTypeOrNull())
                val enablePart = enable.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val encryptedPart = encrypted.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val permanentPart = permanent.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiClient.createCurtain(
                    filePart,
                    descriptionPart,
                    curtainTypePart,
                    enablePart,
                    encryptedPart,
                    permanentPart
                )

                if (response.isSuccessful && response.body() != null) {
                    val curtainEntity = response.body()!!.toCurtainEntity(hostname)
                    curtainDao.insert(curtainEntity)
                    curtainEntity
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to create curtain: ${e.message}")
            }
        }
    }

    // Update an existing curtain
    suspend fun updateCurtain(
        hostname: String,
        linkId: String,
        file: File?,
        description: String,
        curtainType: String,
        enable: Boolean,
        encrypted: Boolean,
        permanent: Boolean
    ): CurtainEntity {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = createApiForHost(hostname)

                val response = if (file != null) {
                    val fileRequestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)

                    val descriptionPart = description.toRequestBody("text/plain".toMediaTypeOrNull())
                    val curtainTypePart = curtainType.toRequestBody("text/plain".toMediaTypeOrNull())
                    val enablePart = enable.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val encryptedPart = encrypted.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val permanentPart = permanent.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                    apiClient.updateCurtainWithFile(
                        linkId,
                        filePart,
                        descriptionPart,
                        curtainTypePart,
                        enablePart,
                        encryptedPart,
                        permanentPart
                    )
                } else {
                    val updateRequest = CurtainUpdateRequest(
                        description = description,
                        curtainType = curtainType,
                        enable = enable,
                        encrypted = encrypted,
                        permanent = permanent
                    )

                    apiClient.updateCurtainWithoutFile(linkId, updateRequest)
                }

                if (response.isSuccessful && response.body() != null) {
                    val curtainEntity = response.body()!!.toCurtainEntity(hostname)
                    curtainDao.update(curtainEntity)
                    curtainEntity
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to update curtain: ${e.message}")
            }
        }
    }

    // Delete a curtain
    suspend fun deleteCurtain(hostname: String, linkId: String) {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = createApiForHost(hostname)
                val response = apiClient.deleteCurtain(linkId)

                if (response.isSuccessful) {
                    // Delete from local database if exists
                    val curtain = curtainDao.getById(linkId)
                    if (curtain != null) {
                        curtainDao.delete(curtain)
                    }
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to delete curtain: ${e.message}")
            }
        }
    }

    // Update or insert site settings
    suspend fun updateSiteSettings(siteSettings: CurtainSiteSettings) {
        return withContext(Dispatchers.IO) {
            curtainDao.insertSiteSettings(siteSettings)
        }
    }

    // Get a curtain by linkId
    suspend fun getCurtainById(linkId: String): CurtainEntity? {
        return withContext(Dispatchers.IO) {
            curtainDao.getById(linkId)
        }
    }

    /**
     * Fetches a curtain by its link ID from a specific host API URL
     * This is used for deep linking when the app is opened from a browser
     *
     * @param linkId The unique ID of the curtain to fetch
     * @param apiUrl The base URL of the API to use (hostname)
     * @return The fetched Curtain if successful
     * @throws Exception if the curtain cannot be fetched
     */
    suspend fun fetchCurtainByLinkIdAndHost(linkId: String, apiUrl: String): CurtainEntity {
        return withContext(Dispatchers.IO) {
            try {
                // First check if we already have this curtain stored locally
                val localCurtain = curtainDao.getById(linkId)

                // If we have a cached version, return it
                if (localCurtain != null) {
                    return@withContext localCurtain
                }

                // Otherwise fetch from the network using a specific API client for this host
                val apiClient = createApiForHost(apiUrl)
                val response = apiClient.getCurtainByLinkId(linkId)

                if (response.isSuccessful && response.body() != null) {
                    // Convert API response to entity
                    val curtainEntity = response.body()!!.toCurtainEntity(apiUrl)

                    // Check and store the site settings BEFORE inserting the curtain
                    // This is used to satisfy the foreign key constraint
                    val existingSiteSettings = curtainDao.getSiteSettingsByHostname(apiUrl)

                    if (existingSiteSettings == null) {
                        val siteSettings = CurtainSiteSettings(
                            hostname = apiUrl,
                            active = true,
                        )
                        curtainDao.insertSiteSettings(siteSettings)
                    }

                    // Now insert the curtain after the site settings have been created
                    curtainDao.insert(curtainEntity)

                    curtainEntity
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                // Log the error
                e.printStackTrace()
                throw Exception("Failed to fetch curtain: ${e.message}")
            }
        }
    }

    /**
     * Downloads the curtain data for a specific curtain
     * If the data isn't already locally available, it will download it from the API
     * After downloading, it will update the CurtainEntity with the local file path
     *
     * @param linkId The unique ID of the curtain to download data for
     * @param hostname The API URL to download from
     * @param token Optional authentication token
     * @param progressCallback Callback for progress updates (0-100)
     * @return The path to the downloaded file
     */
    suspend fun downloadCurtainData(
        linkId: String,
        hostname: String,
        token: String? = null,
        progressCallback: ((Int) -> Unit)? = null,
        forceDownload: Boolean = false
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                progressCallback?.invoke(0) // Start at 0%

                // Check if we already have the file locally and not forcing a redownload
                val localFilePath = getLocalFilePath(linkId)
                if (!forceDownload && File(localFilePath).exists()) {
                    // If file exists, make sure the entity has the file path set
                    updateCurtainEntityWithLocalFilePath(linkId, localFilePath)
                    progressCallback?.invoke(100) // Already complete
                    return@withContext localFilePath
                }

                // Otherwise, download it from the API
                val apiClient = createApiForHost(hostname)
                val downloadEndpoint = if (token != null) {
                    "$linkId/download/token=$token"
                } else {
                    "$linkId/download/token="
                }

                progressCallback?.invoke(10) // API request started
                val response = apiClient.downloadCurtain(downloadEndpoint)

                if (response.isSuccessful && response.body() != null) {
                    progressCallback?.invoke(20)
                    val responseBody = response.body()!!

                    val responseString = responseBody.string()
                    var filePath: String? = null

                    try {
                        val moshi = com.squareup.moshi.Moshi.Builder().build()
                        val jsonAdapter = moshi.adapter(Map::class.java)
                        val jsonMap = jsonAdapter.fromJson(responseString)

                        progressCallback?.invoke(30)

                        if (jsonMap?.containsKey("url") == true) {
                            val downloadUrl = jsonMap["url"] as String
                            progressCallback?.invoke(40) // Starting file download

                            // Ensure we have an absolute URL
                            val absoluteUrl = if (downloadUrl.startsWith("http")) {
                                downloadUrl // Already an absolute URL
                            } else {
                                // In case it's a relative URL, prepend the base URL
                                val baseUrl = if (hostname.endsWith("/")) hostname else "$hostname/"
                                baseUrl + downloadUrl
                            }

                            android.util.Log.d("CurtainRepository", "Using direct download client for URL: $absoluteUrl")

                            val file = downloadClient.downloadFile(
                                url = absoluteUrl,
                                destinationPath = getLocalFilePath(linkId),
                                progressCallback = { progress ->
                                    val mappedProgress = (progress * 50 / 100) + 40
                                    progressCallback?.invoke(mappedProgress.coerceIn(0, 90))
                                }
                            )

                            filePath = file.absolutePath
                        } else {
                            progressCallback?.invoke(40) // Writing direct response to file
                            val file = File(getLocalFilePath(linkId))
                            file.outputStream().use { outputStream ->
                                outputStream.write(responseString.toByteArray())
                                progressCallback?.invoke(70)
                            }
                            filePath = file.absolutePath
                        }
                    } catch (e: Exception) {
                        progressCallback?.invoke(40) // Fallback to raw response
                        val file = File(getLocalFilePath(linkId))
                        file.outputStream().use { outputStream ->
                            outputStream.write(responseString.toByteArray())
                            progressCallback?.invoke(70)
                        }
                        filePath = file.absolutePath
                    }

                    if (filePath != null) {
                        progressCallback?.invoke(90) // Updating database
                        updateCurtainEntityWithLocalFilePath(linkId, filePath)
                        progressCallback?.invoke(100) // Complete
                    }

                    return@withContext filePath ?: throw Exception("Failed to save downloaded file")
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                progressCallback?.invoke(0) // Reset progress on error
                throw Exception("Failed to download curtain data: ${e.message}")
            }
        }
    }

    /**
     * Gets the local file path for a curtain's data file
     */
    private fun getLocalFilePath(linkId: String): String {
        val context = AppContext.get()
        val dir = context.filesDir
        return File(dir, "$linkId.json").absolutePath
    }

    /**
     * Updates a curtain entity with the local file path
     */
    private suspend fun updateCurtainEntityWithLocalFilePath(linkId: String, filePath: String) {
        val curtainEntity = curtainDao.getById(linkId)
        if (curtainEntity != null && (curtainEntity.file == null || curtainEntity.file != filePath)) {
            val updatedEntity = curtainEntity.copy(file = filePath)
            curtainDao.update(updatedEntity)
        }
    }

    private fun Curtain.toCurtainEntity(hostname: String): CurtainEntity {
        return CurtainEntity(
            linkId = this.linkId,
            description = this.description,
            curtainType = this.curtainType,
            file = null,
            enable = this.enable,
            sourceHostname = hostname,
            created = parseCreatedDateToTimestamp(this.created),
            updated = System.currentTimeMillis()
        )
    }

    private fun parseCreatedDateToTimestamp(createdDateString: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            format.parse(createdDateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                format.parse(createdDateString)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
