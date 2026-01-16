package info.proteo.curtain.domain.repository

import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for backend site settings operations.
 * Manages configuration for multiple Curtain backend servers.
 *
 * Matches iOS site settings management.
 */
interface SiteSettingsRepository {

    /**
     * Get all site settings as Flow.
     *
     * @return Flow of all site settings
     */
    fun getAllSiteSettings(): Flow<List<CurtainSiteSettingsEntity>>

    /**
     * Get only active site settings.
     *
     * @return Flow of active site settings
     */
    fun getActiveSiteSettings(): Flow<List<CurtainSiteSettingsEntity>>

    /**
     * Get site settings by hostname.
     *
     * @param hostname Backend server hostname
     * @return Site settings entity or null if not found
     */
    suspend fun getSiteSettingsByHostname(hostname: String): CurtainSiteSettingsEntity?

    /**
     * Check if a site exists in database.
     *
     * @param hostname Backend server hostname
     * @return True if site exists
     */
    suspend fun siteExists(hostname: String): Boolean

    /**
     * Insert or update site settings.
     *
     * @param settings Site settings entity
     */
    suspend fun insertSiteSettings(settings: CurtainSiteSettingsEntity)

    /**
     * Insert predefined default sites.
     * Matches iOS predefined hosts:
     * - celsus.muttsu.xyz
     * - curtain-backend.omics.quest
     * - curtain.proteo.info
     */
    suspend fun insertDefaultSites()

    /**
     * Update active status of a site.
     *
     * @param hostname Site hostname
     * @param active New active status
     */
    suspend fun updateActiveStatus(hostname: String, active: Boolean)

    /**
     * Update last sync timestamp.
     *
     * @param hostname Site hostname
     * @param timestamp Sync timestamp in milliseconds
     */
    suspend fun updateLastSync(hostname: String, timestamp: Long)

    /**
     * Update API key for a site.
     *
     * @param hostname Site hostname
     * @param apiKey New API key (null to remove)
     */
    suspend fun updateApiKey(hostname: String, apiKey: String?)

    /**
     * Delete site settings.
     *
     * @param settings Site settings entity
     */
    suspend fun deleteSiteSettings(settings: CurtainSiteSettingsEntity)

    /**
     * Delete site settings by hostname.
     *
     * @param hostname Site hostname
     */
    suspend fun deleteSiteSettingsByHostname(hostname: String)

    /**
     * Get count of active sites.
     *
     * @return Number of active sites
     */
    suspend fun getActiveSiteCount(): Int
}
