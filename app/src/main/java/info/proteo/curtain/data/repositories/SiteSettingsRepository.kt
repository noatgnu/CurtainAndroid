package info.proteo.curtain

import info.proteo.curtain.data.local.database.entities.CurtainSiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteSettingsRepository @Inject constructor(
    private val curtainDao: CurtainDao
) {
    // Default fallback URL when no settings exist
    private val defaultBaseUrl = "https://celsus.muttsu.xyz/"

    /**
     * Gets the active site hostname, or null if none is configured
     */
    suspend fun getActiveHostname(): String? {
        return curtainDao.getActiveSiteSettings().firstOrNull()?.firstOrNull()?.hostname
    }

    /**
     * Gets all site settings
     */
    fun getAllSiteSettings(): Flow<List<CurtainSiteSettings>> {
        return curtainDao.getAllSiteSettings()
    }

    /**
     * Adds or updates a site setting
     */
    suspend fun saveSiteSettings(siteSettings: CurtainSiteSettings) {
        curtainDao.insertSiteSettings(siteSettings)
    }

    /**
     * Gets the base URL for the active site or default if none exists
     */
    suspend fun getBaseUrl(): okhttp3.HttpUrl {
        val hostname = getActiveHostname()
        val urlString = if (hostname != null) {
            formatBaseUrl(hostname)
        } else {
            defaultBaseUrl
        }

        return urlString.toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid base URL: $urlString")
    }

    /**
     * Gets the full API base URL for the active site as a String
     */
    suspend fun getActiveBaseUrl(): String? {
        val hostname = getActiveHostname() ?: return null
        return formatBaseUrl(hostname)
    }

    /**
     * Formats a hostname into a proper base URL
     */
    fun formatBaseUrl(hostname: String): String {
        return if (hostname.startsWith("http")) {
            // If it already has a protocol, use it as is
            if (hostname.endsWith("/")) hostname else "$hostname/"
        } else {
            // Otherwise, add HTTPS protocol
            if (hostname.endsWith("/")) "https://$hostname" else "https://$hostname/"
        }
    }
}
