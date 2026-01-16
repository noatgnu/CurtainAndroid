package info.proteo.curtain.domain.service

import android.content.Intent
import android.net.Uri
import info.proteo.curtain.domain.repository.CurtainRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkHandler @Inject constructor(
    private val curtainRepository: CurtainRepository
) {

    suspend fun handleIntent(intent: Intent): DeepLinkResult? {
        val data = intent.data ?: return null
        return processUri(data)
    }

    suspend fun handleQRCode(qrContent: String): DeepLinkResult? {
        val uri = try {
            Uri.parse(qrContent)
        } catch (e: Exception) {
            return null
        }
        return processUri(uri)
    }

    private suspend fun processUri(uri: Uri): DeepLinkResult? {
        return when {
            uri.scheme == "curtain" && uri.host == "open" -> {
                processCurtainDeepLink(uri)
            }
            uri.toString().contains("doi.org") -> {
                processDOILink(uri)
            }
            uri.host?.contains("curtain.proteo.info") == true -> {
                processWebLink(uri)
            }
            else -> null
        }
    }

    private suspend fun processCurtainDeepLink(uri: Uri): DeepLinkResult? {
        val uniqueId = uri.getQueryParameter("uniqueId")
        val apiURL = uri.getQueryParameter("apiURL")
        val frontendURL = uri.getQueryParameter("frontendURL")

        return when {
            uniqueId != null && apiURL != null -> {
                DeepLinkResult.CurtainDataset(
                    linkId = uniqueId,
                    apiURL = apiURL,
                    frontendURL = frontendURL
                )
            }
            else -> {
                val doi = uri.getQueryParameter("doi")
                val sessionId = uri.getQueryParameter("sessionId")
                if (doi != null) {
                    DeepLinkResult.DOIReference(
                        doi = doi,
                        sessionId = sessionId
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun processDOILink(uri: Uri): DeepLinkResult? {
        val doiPath = uri.path?.removePrefix("/") ?: return null
        return DeepLinkResult.DOIReference(
            doi = doiPath,
            sessionId = null
        )
    }

    private fun processWebLink(uri: Uri): DeepLinkResult? {
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) return null

        val linkId = pathSegments.lastOrNull()?.removePrefix("#/") ?: return null

        return DeepLinkResult.CurtainDataset(
            linkId = linkId,
            apiURL = "https://api.curtain.proteo.info",
            frontendURL = uri.toString()
        )
    }
}

sealed class DeepLinkResult {
    data class CurtainDataset(
        val linkId: String,
        val apiURL: String,
        val frontendURL: String?
    ) : DeepLinkResult()

    data class DOIReference(
        val doi: String,
        val sessionId: String?
    ) : DeepLinkResult()
}
