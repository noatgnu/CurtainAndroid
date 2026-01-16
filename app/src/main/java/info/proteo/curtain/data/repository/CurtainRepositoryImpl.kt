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
 *
 * Matches iOS CurtainRepository.swift implementation.
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

            val initialResponse = dynamicApiService.downloadCurtainData(curtain.linkId)
            val contentType = initialResponse.contentType()?.toString() ?: ""

            val responseBodyToDownload = if (contentType.contains("application/json", ignoreCase = true) &&
                                              initialResponse.contentLength() < 1000) {
                val responseString = initialResponse.string()

                try {
                    val gson = com.google.gson.Gson()
                    val urlResponse = gson.fromJson(responseString, info.proteo.curtain.data.remote.model.DownloadUrlResponse::class.java)

                    if (urlResponse?.url != null && urlResponse.url.startsWith("http")) {
                        val request = okhttp3.Request.Builder()
                            .url(urlResponse.url)
                            .build()

                        val response = okHttpClient.newCall(request).execute()

                        if (!response.isSuccessful) {
                            return@withContext Result.failure(Exception("Download from presigned URL failed: ${response.code}"))
                        }

                        response.body ?: return@withContext Result.failure(Exception("Empty response body from presigned URL"))
                    } else {
                        val bytes = responseString.toByteArray(Charsets.UTF_8)
                        okhttp3.ResponseBody.create(
                            initialResponse.contentType(),
                            bytes.size.toLong(),
                            okio.Buffer().write(bytes)
                        )
                    }
                } catch (e: Exception) {
                    val bytes = responseString.toByteArray(Charsets.UTF_8)
                    okhttp3.ResponseBody.create(
                        initialResponse.contentType(),
                        bytes.size.toLong(),
                        okio.Buffer().write(bytes)
                    )
                }
            } else {
                initialResponse
            }

            downloadFileFromResponse(responseBodyToDownload, curtain.linkId, onProgress)

            curtainDao.updateFile(curtain.linkId, File(dataDirectory, "${curtain.linkId}.json").absolutePath)

            onProgress(100, 0.0)

            Result.success(File(dataDirectory, "${curtain.linkId}.json").absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFileFromResponse(
        responseBody: okhttp3.ResponseBody,
        linkId: String,
        onProgress: (Int, Double) -> Unit
    ) {
        val totalBytes = responseBody.contentLength()
        val file = File(dataDirectory, "$linkId.json")
        var downloadedBytes = 0L
        val startTime = System.currentTimeMillis()
        var lastProgressUpdate = 0L

        FileOutputStream(file).buffered(65536).use { output ->
            responseBody.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressUpdate >= 250) {
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        val elapsedTime = (currentTime - startTime) / 1000.0
                        val speed = if (elapsedTime > 0) {
                            (downloadedBytes / 1024.0) / elapsedTime
                        } else {
                            0.0
                        }

                        onProgress(progress, speed)
                        lastProgressUpdate = currentTime
                    }
                }
            }
        }
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
}
