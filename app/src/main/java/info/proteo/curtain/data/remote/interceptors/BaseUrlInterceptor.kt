package info.proteo.curtain

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that dynamically changes the base URL based on the current
 * active site settings. This allows switching between different backend servers
 * without recreating the Retrofit instance.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val siteSettingsRepository: SiteSettingsRepository
) : Interceptor {

    // Default fallback URL when no settings exist
    private val defaultBaseUrl = "https://celsus.muttsu.xyz/"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip URL change for authentication endpoints
        if (isAuthenticationEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Get active base URL from repository
        val baseUrl = runBlocking {
            siteSettingsRepository.getActiveBaseUrl() ?: defaultBaseUrl
        }.toHttpUrlOrNull()

        // If we have a valid base URL, update the request
        val newRequest = if (baseUrl != null) {
            updateRequestUrl(originalRequest, baseUrl)
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }

    private fun isAuthenticationEndpoint(request: Request): Boolean {
        val url = request.url.toString()
        return url.contains("/auth/") || url.contains("/login")
    }


    private fun updateRequestUrl(request: Request, baseUrl: HttpUrl): Request {
        val newUrl = request.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()

        return request.newBuilder()
            .url(newUrl)
            .build()
    }
}
