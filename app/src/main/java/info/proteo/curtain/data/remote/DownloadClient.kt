package info.proteo.curtain

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

    /**
     * Downloads a file from a URL to the specified file path
     *
     * @param url The URL to download from
     * @param destinationPath The path where the file should be saved
     * @param progressCallback Optional callback for progress updates (0-100)
     * @return The downloaded file
     * @throws IOException If the download fails
     */
    @Throws(IOException::class)
    fun downloadFile(
        url: String,
        destinationPath: String,
        progressCallback: ((Int) -> Unit)? = null
    ): File {
        // Create the Request
        val request = Request.Builder()
            .url(url)
            .build()

        // Log for debugging
        android.util.Log.d("DownloadClient", "Starting direct download from: $url")
        progressCallback?.invoke(0) // Starting download

        // Execute the request
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected response code: ${response.code}")
        }

        val responseBody = response.body ?: throw IOException("Response body is null")
        val contentLength = responseBody.contentLength()

        progressCallback?.invoke(10) // Connected, starting file write

        return writeResponseToFile(responseBody, destinationPath, contentLength, progressCallback)
    }

    /**
     * Writes the response body to a file with progress tracking
     */
    private fun writeResponseToFile(
        responseBody: ResponseBody,
        destinationPath: String,
        contentLength: Long,
        progressCallback: ((Int) -> Unit)? = null
    ): File {
        val file = File(destinationPath)
        val buffer = ByteArray(8192) // 8KB buffer
        var totalBytesRead = 0L
        var bytesRead: Int

        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()

        responseBody.byteStream().use { inputStream ->
            file.outputStream().use { outputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Calculate and report progress
                    if (contentLength > 0) {
                        val progress = (totalBytesRead * 90 / contentLength).toInt() + 10
                        progressCallback?.invoke(progress.coerceIn(0, 100))
                    }
                }
            }
        }

        progressCallback?.invoke(100) // Download complete
        return file
    }
}
