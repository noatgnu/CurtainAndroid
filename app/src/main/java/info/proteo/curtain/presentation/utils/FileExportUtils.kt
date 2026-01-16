package info.proteo.curtain.presentation.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object FileExportUtils {

    suspend fun exportHtmlToFile(
        context: Context,
        fileName: String,
        htmlContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.html")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(htmlContent.toByteArray())
                        outputStream.flush()
                    }
                    Result.success("File saved to Downloads/$fileName.html")
                } ?: Result.failure(Exception("Failed to create file"))
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val file = File(downloadsDir, "$fileName.html")
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(htmlContent.toByteArray())
                    outputStream.flush()
                }
                Result.success("File saved to Downloads/$fileName.html")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportPngToFile(
        context: Context,
        fileName: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.flush()
                    }
                    Result.success("Image saved to Pictures/$fileName.png")
                } ?: Result.failure(Exception("Failed to create file"))
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }

                val file = File(picturesDir, "$fileName.png")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }
                Result.success("Image saved to Pictures/$fileName.png")
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmap.recycle()
        }
    }

    fun captureWebViewBitmap(webView: WebView, onBitmapReady: (Bitmap) -> Unit) {
        webView.post {
            try {
                val bitmap = Bitmap.createBitmap(
                    webView.width,
                    webView.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                webView.draw(canvas)
                onBitmapReady(bitmap)
            } catch (e: Exception) {
                android.util.Log.e("FileExportUtils", "Failed to capture WebView bitmap", e)
            }
        }
    }
}
