package info.proteo.curtain

import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.local.database.entities.CurtainSiteSettings
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
                // Get the curtain from local database
                val curtain = curtainDao.getById(linkId)
                if (curtain != null) {
                    // Delete associated local file if it exists
                    curtain.file?.let { filePath ->
                        val file = File(filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    
                    // Delete from local database
                    curtainDao.delete(curtain)
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
     * Creates a curtain entry locally without fetching data from the network
     * This is used when adding curtains manually or from QR codes/URLs
     * The actual data will be downloaded when the user clicks on the curtain
     *
     * @param linkId The unique ID of the curtain
     * @param apiUrl The base URL of the API (hostname)
     * @param frontendURL The frontend URL for the curtain (optional)
     * @param description Optional description for the curtain
     * @return The created curtain entity
     */
    suspend fun createCurtainEntry(
        linkId: String, 
        apiUrl: String, 
        frontendURL: String? = null, 
        description: String = ""
    ): CurtainEntity {
        return withContext(Dispatchers.IO) {
            // Check if curtain already exists
            val existingCurtain = curtainDao.getById(linkId)
            if (existingCurtain != null) {
                return@withContext existingCurtain
            }

            // Ensure site settings exist (foreign key constraint)
            val existingSiteSettings = curtainDao.getSiteSettingsByHostname(apiUrl)
            if (existingSiteSettings == null) {
                val siteSettings = CurtainSiteSettings(
                    hostname = apiUrl,
                    active = true,
                )
                curtainDao.insertSiteSettings(siteSettings)
            }

            // Create curtain entity without network data
            val curtainEntity = CurtainEntity(
                linkId = linkId,
                created = System.currentTimeMillis(),
                updated = System.currentTimeMillis(),
                file = null, // Will be populated when downloaded
                description = description.ifEmpty { "Manual import" },
                enable = true,
                curtainType = "TP",
                sourceHostname = apiUrl,
                frontendURL = frontendURL,
                isPinned = false
            )

            // Insert into database
            curtainDao.insert(curtainEntity)
            curtainEntity
        }
    }

    /**
     * Fetches a curtain by its link ID from a specific host API URL
     * This is used for deep linking when the app is opened from a browser
     *
     * @param linkId The unique ID of the curtain to fetch
     * @param apiUrl The base URL of the API to use (hostname)
     * @param frontendURL The frontend URL for the curtain (optional)
     * @return The fetched Curtain if successful
     * @throws Exception if the curtain cannot be fetched
     */
    suspend fun fetchCurtainByLinkIdAndHost(linkId: String, apiUrl: String, frontendURL: String? = null): CurtainEntity {
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
                    val curtainEntity = response.body()!!.toCurtainEntity(apiUrl).copy(
                        frontendURL = frontendURL
                    )

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
        progressCallback: ((Int, Double) -> Unit)? = null,
        forceDownload: Boolean = false
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                progressCallback?.invoke(0, 0.0) // Start at 0%

                // Check if we already have the file locally and not forcing a redownload
                val localFilePath = getLocalFilePath(linkId)
                if (!forceDownload && File(localFilePath).exists()) {
                    // If file exists, make sure the entity has the file path set
                    updateCurtainEntityWithLocalFilePath(linkId, localFilePath)
                    progressCallback?.invoke(100, 0.0) // Already complete
                    return@withContext localFilePath
                }

                // Otherwise, download it from the API
                val apiClient = createApiForHost(hostname)
                val downloadEndpoint = if (token != null) {
                    "$linkId/download/token=$token"
                } else {
                    "$linkId/download/token="
                }

                progressCallback?.invoke(10, 0.0) // API request started
                val response = apiClient.downloadCurtain(downloadEndpoint)

                if (response.isSuccessful && response.body() != null) {
                    progressCallback?.invoke(20, 0.0)
                    val responseBody = response.body()!!

                    val responseString = responseBody.string()
                    var filePath: String? = null

                    try {
                        val moshi = com.squareup.moshi.Moshi.Builder().build()
                        val jsonAdapter = moshi.adapter(Map::class.java)
                        val jsonMap = jsonAdapter.fromJson(responseString)

                        progressCallback?.invoke(30, 0.0)

                        if (jsonMap?.containsKey("url") == true) {
                            val downloadUrl = jsonMap["url"] as String
                            progressCallback?.invoke(40, 0.0) // Starting file download

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
                                progressCallback = { progress, speed ->
                                    val mappedProgress = (progress * 50 / 100) + 40
                                    progressCallback?.invoke(mappedProgress.coerceIn(0, 90), speed)
                                }
                            )

                            filePath = file.absolutePath
                        } else {
                            progressCallback?.invoke(40, 0.0) // Writing direct response to file
                            val file = File(getLocalFilePath(linkId))
                            file.outputStream().use { outputStream ->
                                outputStream.write(responseString.toByteArray())
                                progressCallback?.invoke(70, 0.0)
                            }
                            filePath = file.absolutePath
                        }
                    } catch (e: Exception) {
                        progressCallback?.invoke(40, 0.0) // Fallback to raw response
                        val file = File(getLocalFilePath(linkId))
                        file.outputStream().use { outputStream ->
                            outputStream.write(responseString.toByteArray())
                            progressCallback?.invoke(70, 0.0)
                        }
                        filePath = file.absolutePath
                    }

                    if (filePath != null) {
                        progressCallback?.invoke(90, 0.0) // Updating database
                        updateCurtainEntityWithLocalFilePath(linkId, filePath)
                        progressCallback?.invoke(100, 0.0) // Complete
                    }

                    return@withContext filePath ?: throw Exception("Failed to save downloaded file")
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                progressCallback?.invoke(0, 0.0) // Reset progress on error
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

    // Update only the description of an existing curtain
    suspend fun updateCurtainDescription(linkId: String, description: String) {
        return withContext(Dispatchers.IO) {
            try {
                val existingCurtain = curtainDao.getById(linkId)
                if (existingCurtain != null) {
                    val updatedCurtain = existingCurtain.copy(
                        description = description,
                        updated = System.currentTimeMillis()
                    )
                    curtainDao.update(updatedCurtain)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't throw exception for description updates, just log
                android.util.Log.w("CurtainRepository", "Failed to update curtain description: ${e.message}")
            }
        }
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
    
    /**
     * Updates the pin status of a curtain
     */
    suspend fun updatePinStatus(linkId: String, isPinned: Boolean) {
        return withContext(Dispatchers.IO) {
            try {
                curtainDao.updatePinStatus(linkId, isPinned)
            } catch (e: Exception) {
                android.util.Log.e("CurtainRepository", "Failed to update pin status: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Get all pinned curtains
     */
    fun getPinnedCurtains(): Flow<List<CurtainEntity>> {
        return curtainDao.getPinnedCurtains()
    }

    /**
     * Cancels the current download operation
     */
    fun cancelDownload() {
        downloadClient.cancelDownload()
    }
}
