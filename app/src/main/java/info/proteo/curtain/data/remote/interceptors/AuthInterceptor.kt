package info.proteo.curtain

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authTokenRepository: AuthTokenRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip authentication for token endpoints
        if (isAuthenticationEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Add the current access token
        var accessToken = authTokenRepository.getAccessToken()
        var requestWithToken = addTokenToRequest(originalRequest, accessToken)
        var response = chain.proceed(requestWithToken)

        // If unauthorized (401), try to refresh the token and retry
        if (response.code == 401) {
            response.close()

            // Synchronously refresh the token
            val tokenRefreshed = runBlocking { authTokenRepository.refreshToken() }

            if (tokenRefreshed) {
                // Retry with new token
                accessToken = authTokenRepository.getAccessToken()
                requestWithToken = addTokenToRequest(originalRequest, accessToken)
                response = chain.proceed(requestWithToken)
            }
        }

        return response
    }

    private fun addTokenToRequest(request: Request, token: String?): Request {
        return if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
    }

    private fun isAuthenticationEndpoint(request: Request): Boolean {
        val url = request.url.toString()
        return url.endsWith("/token/") || url.endsWith("/token/refresh/")
    }
}
