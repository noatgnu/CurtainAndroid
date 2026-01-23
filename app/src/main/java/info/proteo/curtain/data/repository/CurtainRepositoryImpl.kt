package info.proteo.curtain.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.data.local.dao.CurtainDao
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.data.remote.api.CurtainApiService
import info.proteo.curtain.di.NetworkModule
import info.proteo.curtain.domain.repository.CurtainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CurtainRepository.
 * Manages dataset operations combining local Room database and remote API.
 */
@Singleton
class CurtainRepositoryImpl @Inject constructor(
    private val curtainDao: CurtainDao,
    private val apiService: CurtainApiService,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : CurtainRepository {

    /**
     * Directory for storing downloaded curtain data files.
     */
    private val dataDirectory: File by lazy {
        File(context.filesDir, "CurtainData").apply {
            if (!exists()) mkdirs()
        }
    }

    override fun getAllCurtains(): Flow<List<CurtainEntity>> {
        return curtainDao.getAllCurtains()
    }

    override fun getCurtainsByHostname(hostname: String): Flow<List<CurtainEntity>> {
        return curtainDao.getCurtainsByHostname(hostname)
    }

    override fun getPinnedCurtains(): Flow<List<CurtainEntity>> {
        return curtainDao.getPinnedCurtains()
    }

    override fun searchCurtains(query: String): Flow<List<CurtainEntity>> {
        return curtainDao.searchCurtains(query)
    }

    override suspend fun getCurtainById(linkId: String): CurtainEntity? {
        return curtainDao.getCurtainById(linkId)
    }

    /**
     * Download curtain data file (JSON) from backend with progress tracking.
     * Matches iOS CurtainRepository.downloadCurtainData() method (lines 112-220).
     *
     * Implements streaming download:
     * 1. Creates dynamic API service for the curtain's source hostname
     * 2. Downloads file as ResponseBody stream
     * 3. Writes to local storage with progress callbacks
     * 4. Updates entity with file path
     * 5. Returns local file path on success
     *
     * @param curtain Curtain entity to download
     * @param onProgress Callback for download progress (progress %, speed KB/s)
     * @return Result with local file path
     */
    override suspend fun downloadCurtainData(
        curtain: CurtainEntity,
        onProgress: (Int, Double) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress(0, 0.0)

            val retrofit = NetworkModule.createRetrofitForHost(
                curtain.sourceHostname,
                okHttpClient
            )
            val dynamicApiService = retrofit.create(CurtainApiService::class.java)

            onProgress(5, 0.0)
            val initialResponse = dynamicApiService.downloadCurtainData(curtain.linkId)
            val contentType = initialResponse.contentType()?.toString() ?: ""
            val contentLength = initialResponse.contentLength()

            android.util.Log.d("CurtainRepo", "Initial response: contentType=$contentType, contentLength=$contentLength")

            val file = File(dataDirectory, "${curtain.linkId}.json")
            val startTime = System.currentTimeMillis()

            val responseBytes = initialResponse.bytes()
            val responseString = String(responseBytes, Charsets.UTF_8)

            android.util.Log.d("CurtainRepo", "Response size: ${responseBytes.size} bytes, first 200 chars: ${responseString.take(200)}")

            if (responseString.contains("\"url\"") && responseString.contains("http")) {
                try {
                    val gson = com.google.gson.Gson()
                    val urlResponse = gson.fromJson(responseString, info.proteo.curtain.data.remote.model.DownloadUrlResponse::class.java)

                    if (urlResponse?.url != null && urlResponse.url.startsWith("http")) {
                        android.util.Log.d("CurtainRepo", "Got presigned URL, downloading from: ${urlResponse.url.take(100)}...")
                        onProgress(10, 0.0)

                        downloadFromPresignedUrl(urlResponse.url, file, onProgress, startTime)
                    } else {
                        android.util.Log.d("CurtainRepo", "Response is direct JSON data")
                        file.writeBytes(responseBytes)
                        onProgress(90, 0.0)
                    }
                } catch (e: Exception) {
                    android.util.Log.d("CurtainRepo", "Failed to parse as URL response, treating as direct data: ${e.message}")
                    file.writeBytes(responseBytes)
                    onProgress(90, 0.0)
                }
            } else {
                android.util.Log.d("CurtainRepo", "Response is direct data (no URL field)")
                file.writeBytes(responseBytes)
                onProgress(90, 0.0)
            }

            curtainDao.updateFile(curtain.linkId, file.absolutePath)
            onProgress(100, 0.0)

            android.util.Log.d("CurtainRepo", "Download complete: ${file.absolutePath}, size: ${file.length()}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e("CurtainRepo", "Download failed", e)
            Result.failure(e)
        }
    }

    private fun downloadFromPresignedUrl(
        url: String,
        file: File,
        onProgress: (Int, Double) -> Unit,
        startTime: Long
    ) {
        var downloadedBytes = 0L
        var totalBytes = 0L
        var lastProgressUpdate = 0L
        var retryCount = 0
        val maxRetries = 3

        android.util.Log.d("CurtainRepo", "Starting download from S3")

        while (retryCount <= maxRetries) {
            try {
                val connection = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000

                if (downloadedBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                    android.util.Log.d("CurtainRepo", "Resuming download from byte $downloadedBytes")
                }

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != 200 && responseCode != 206) {
                    connection.disconnect()
                    throw Exception("Download failed: $responseCode ${connection.responseMessage}")
                }

                if (totalBytes == 0L) {
                    totalBytes = connection.contentLengthLong + downloadedBytes
                }

                val appendMode = downloadedBytes > 0
                java.io.FileOutputStream(file, appendMode).buffered(262144).use { output ->
                    java.io.BufferedInputStream(connection.inputStream, 262144).use { input ->
                        val buffer = ByteArray(262144)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate >= 200) {
                                val progress = if (totalBytes > 0) {
                                    (10 + downloadedBytes * 80 / totalBytes).toInt().coerceIn(10, 90)
                                } else {
                                    ((downloadedBytes / 1024) % 80 + 10).toInt()
                                }

                                val elapsedTime = (currentTime - startTime) / 1000.0
                                val speed = if (elapsedTime > 0) (downloadedBytes / 1024.0) / elapsedTime else 0.0

                                android.util.Log.d("CurtainRepo", "Progress: $downloadedBytes / $totalBytes (${progress}%), ${speed.toInt()} KB/s")
                                onProgress(progress, speed)
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }

                connection.disconnect()
                android.util.Log.d("CurtainRepo", "Download complete: $downloadedBytes bytes")
                return

            } catch (e: javax.net.ssl.SSLException) {
                retryCount++
                android.util.Log.w("CurtainRepo", "SSL error after $downloadedBytes bytes, retry $retryCount/$maxRetries: ${e.message}")
                if (retryCount > maxRetries) {
                    if (downloadedBytes > 0 && totalBytes > 0 && downloadedBytes >= totalBytes * 0.99) {
                        android.util.Log.w("CurtainRepo", "Download 99%+ complete, keeping partial file")
                        return
                    }
                    file.delete()
                    throw Exception("SSL error during download after $retryCount retries: ${e.message}", e)
                }
                Thread.sleep(1000)
            } catch (e: java.io.IOException) {
                retryCount++
                android.util.Log.w("CurtainRepo", "IO error after $downloadedBytes bytes, retry $retryCount/$maxRetries: ${e.message}")
                if (retryCount > maxRetries) {
                    file.delete()
                    throw Exception("Download failed after $retryCount retries: ${e.message}", e)
                }
                Thread.sleep(1000)
            }
        }
    }

    private fun downloadFileFromResponse(
        responseBody: okhttp3.ResponseBody,
        linkId: String,
        onProgress: (Int, Double) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val file = File(dataDirectory, "$linkId.json")

        onProgress(20, 0.0)

        val bytes = responseBody.bytes()

        onProgress(80, 0.0)

        file.writeBytes(bytes)

        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (elapsedTime > 0) (bytes.size / 1024.0) / elapsedTime else 0.0
        android.util.Log.d("CurtainRepo", "Downloaded ${bytes.size} bytes in ${elapsedTime}s (${speed.toInt()} KB/s)")
    }

    override suspend fun insertCurtain(curtain: CurtainEntity) {
        curtainDao.insertCurtain(curtain)
    }

    override suspend fun insertAll(curtains: List<CurtainEntity>) {
        curtainDao.insertAll(curtains)
    }

    override suspend fun updateCurtainDescription(linkId: String, description: String) {
        curtainDao.updateDescription(linkId, description)
    }

    override suspend fun updatePinStatus(linkId: String, isPinned: Boolean) {
        curtainDao.updatePinStatus(linkId, isPinned)
    }

    override suspend fun updateFilePath(linkId: String, filePath: String) {
        curtainDao.updateFile(linkId, filePath)
    }

    /**
     * Delete curtain and associated data file.
     *
     * @param curtain Curtain entity to delete
     */
    override suspend fun deleteCurtain(curtain: CurtainEntity) {
        curtain.file?.let { filePath ->
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        curtainDao.deleteCurtain(curtain)
    }

    override suspend fun deleteCurtainById(linkId: String) {
        val curtain = curtainDao.getCurtainById(linkId)
        curtain?.let { deleteCurtain(it) }
    }

    override suspend fun deleteCurtainsByHostname(hostname: String) {
        curtainDao.deleteCurtainsByHostname(hostname)
    }

    override suspend fun getCurtainCount(): Int {
        return curtainDao.getCurtainCount()
    }

    override suspend fun fetchCurtainByLinkIdAndHost(
        linkId: String,
        hostname: String,
        frontendURL: String?
    ): Result<CurtainEntity> = withContext(Dispatchers.IO) {
        try {
            val retrofit = NetworkModule.createRetrofitForHost(hostname, okHttpClient)
            val dynamicApiService = retrofit.create(CurtainApiService::class.java)

            val dto = dynamicApiService.getCurtainByLinkId(linkId)

            val entity = CurtainEntity(
                linkId = dto.linkId,
                created = parseIsoDateToMillis(dto.created),
                updated = System.currentTimeMillis(),
                file = null,
                sessionName = dto.name,
                dataDescription = dto.description,
                enable = dto.enable,
                curtainType = dto.curtainType,
                sourceHostname = hostname,
                frontendURL = frontendURL,
                isPinned = false
            )

            curtainDao.insertCurtain(entity)

            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse ISO 8601 date string to milliseconds.
     * Handles formats: "2024-01-08T10:30:00Z" or "2024-01-08T10:30:00.123Z"
     *
     * @param isoDate ISO 8601 date string
     * @return Timestamp in milliseconds
     */
    private fun parseIsoDateToMillis(isoDate: String): Long {
        return try {
            val instant = java.time.Instant.parse(isoDate)
            instant.toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    override fun getApiServiceForHost(hostname: String): CurtainApiService? {
        return try {
            val retrofit = NetworkModule.createRetrofitForHost(hostname, okHttpClient)
            retrofit.create(CurtainApiService::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
