package info.proteo.curtain

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadClient @Inject constructor() {

    // Create a completely independent OkHttpClient with no interceptors
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Track current download call for cancellation
    private var currentCall: Call? = null

    /**
     * Downloads a file from a URL to the specified file path
     *
     * @param url The URL to download from
     * @param destinationPath The path where the file should be saved
     * @param progressCallback Optional callback for progress updates and speed (progress: Int, speedKBps: Double)
     * @return The downloaded file
     * @throws IOException If the download fails
     */
    @Throws(IOException::class)
    fun downloadFile(
        url: String,
        destinationPath: String,
        progressCallback: ((Int, Double) -> Unit)? = null
    ): File {
        // Create the Request
        val request = Request.Builder()
            .url(url)
            .build()

        // Log for debugging
        android.util.Log.d("DownloadClient", "Starting direct download from: $url")
        progressCallback?.invoke(0, 0.0) // Starting download

        // Create and store the call for cancellation support
        currentCall = client.newCall(request)
        
        // Execute the request
        val response = currentCall!!.execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected response code: ${response.code}")
        }

        val responseBody = response.body ?: throw IOException("Response body is null")
        val contentLength = responseBody.contentLength()

        progressCallback?.invoke(10, 0.0) // Connected, starting file write

        return writeResponseToFile(responseBody, destinationPath, contentLength, progressCallback)
    }

    /**
     * Cancels the current download
     */
    fun cancelDownload() {
        currentCall?.cancel()
        currentCall = null
    }

    /**
     * Writes the response body to a file with progress and speed tracking
     */
    private fun writeResponseToFile(
        responseBody: ResponseBody,
        destinationPath: String,
        contentLength: Long,
        progressCallback: ((Int, Double) -> Unit)? = null
    ): File {
        val file = File(destinationPath)
        val buffer = ByteArray(8192) // 8KB buffer
        var totalBytesRead = 0L
        var bytesRead: Int
        val startTime = System.currentTimeMillis()
        var lastSpeedUpdate = startTime

        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()

        responseBody.byteStream().use { inputStream ->
            file.outputStream().use { outputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // Check if download was cancelled
                    if (currentCall?.isCanceled() == true) {
                        throw IOException("Download cancelled")
                    }
                    
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Calculate progress and speed
                    if (contentLength > 0) {
                        val progress = (totalBytesRead * 90 / contentLength).toInt() + 10
                        
                        // Calculate speed every 500ms to avoid too frequent updates
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSpeedUpdate >= 500) {
                            val elapsedSeconds = (currentTime - startTime) / 1000.0
                            val speedKBps = if (elapsedSeconds > 0) {
                                (totalBytesRead / 1024.0) / elapsedSeconds
                            } else 0.0
                            
                            progressCallback?.invoke(progress.coerceIn(0, 100), speedKBps)
                            lastSpeedUpdate = currentTime
                        }
                    }
                }
            }
        }

        progressCallback?.invoke(100, 0.0) // Download complete
        currentCall = null // Clear the call reference
        return file
    }
}
