package info.proteo.curtain.presentation.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileExportUtils {

    suspend fun exportFromDataUrl(
        context: Context,
        fileName: String,
        dataUrl: String,
        format: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val extension = format.lowercase()
            val mimeType = when (extension) {
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                else -> return@withContext Result.failure(Exception("Unsupported format: $format"))
            }

            val bytes = decodeDataUrl(dataUrl, extension)
                ?: return@withContext Result.failure(Exception("Failed to decode data URL"))

            val fullFileName = "$fileName.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    Result.success("Saved to Downloads/$fullFileName")
                } ?: Result.failure(Exception("Failed to create file"))
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val file = File(downloadsDir, fullFileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(bytes)
                    outputStream.flush()
                }
                Result.success("Saved to Downloads/$fullFileName")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeDataUrl(dataUrl: String, format: String): ByteArray? {
        if (format == "svg") {
            val svgPrefix = "data:image/svg+xml,"
            val svgBase64Prefix = "data:image/svg+xml;base64,"
            return when {
                dataUrl.startsWith(svgBase64Prefix) -> {
                    val base64Data = dataUrl.removePrefix(svgBase64Prefix)
                    Base64.decode(base64Data, Base64.DEFAULT)
                }
                dataUrl.startsWith(svgPrefix) -> {
                    java.net.URLDecoder.decode(dataUrl.removePrefix(svgPrefix), "UTF-8").toByteArray()
                }
                else -> dataUrl.toByteArray()
            }
        }

        val base64Prefix = "data:image/png;base64,"
        if (dataUrl.startsWith(base64Prefix)) {
            val base64Data = dataUrl.removePrefix(base64Prefix)
            return Base64.decode(base64Data, Base64.DEFAULT)
        }

        val genericPrefix = Regex("^data:[^;]+;base64,")
        val match = genericPrefix.find(dataUrl)
        if (match != null) {
            val base64Data = dataUrl.substring(match.range.last + 1)
            return Base64.decode(base64Data, Base64.DEFAULT)
        }

        return null
    }
}
