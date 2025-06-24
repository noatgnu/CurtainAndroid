package info.proteo.curtain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AuthTokenRepository(context: Context, private val baseUrl: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/token/"
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "")
                    val access = respJson.optString("access", null)
                    val refresh = respJson.optString("refresh", null)
                    if (access != null && refresh != null) {
                        saveTokens(access, refresh)
                        return@withContext true
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        false
    }

    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val refresh = getRefreshToken() ?: return@withContext false
        val url = "$baseUrl/token/refresh/"
        val json = JSONObject().apply {
            put("refresh", refresh)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "")
                    val access = respJson.optString("access", null)
                    if (access != null) {
                        saveAccessToken(access)
                        return@withContext true
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        false
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    private fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString("access_token", access).putString("refresh_token", refresh).apply()
    }
    private fun saveAccessToken(access: String) {
        prefs.edit().putString("access_token", access).apply()
    }
    fun clearTokens() {
        prefs.edit().remove("access_token").remove("refresh_token").apply()
    }
}
