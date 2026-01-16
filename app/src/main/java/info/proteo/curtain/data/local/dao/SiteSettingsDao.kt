package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for site settings operations.
 * Manages backend server configurations.
 */
@Dao
interface SiteSettingsDao {

    /**
     * Get all site settings ordered by creation date.
     *
     * @return Flow of all site settings
     */
    @Query("SELECT * FROM site_settings ORDER BY createdAt ASC")
    fun getAllSiteSettings(): Flow<List<CurtainSiteSettingsEntity>>

    /**
     * Get all site settings synchronously for migration purposes.
     *
     * @return List of all site settings
     */
    @Query("SELECT * FROM site_settings ORDER BY createdAt ASC")
    suspend fun getAllSiteSettingsSync(): List<CurtainSiteSettingsEntity>

    /**
     * Get all active site settings.
     *
     * @return Flow of active site settings
     */
    @Query("SELECT * FROM site_settings WHERE active = 1 ORDER BY createdAt ASC")
    fun getActiveSiteSettings(): Flow<List<CurtainSiteSettingsEntity>>

    /**
     * Get site settings by hostname.
     *
     * @param hostname Backend server hostname
     * @return Site settings entity or null if not found
     */
    @Query("SELECT * FROM site_settings WHERE hostname = :hostname")
    suspend fun getSiteSettingsByHostname(hostname: String): CurtainSiteSettingsEntity?

    /**
     * Check if a site exists.
     *
     * @param hostname Backend server hostname
     * @return True if site exists, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM site_settings WHERE hostname = :hostname")
    suspend fun siteExists(hostname: String): Boolean

    /**
     * Insert site settings, replacing on conflict.
     *
     * @param settings Site settings entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteSettings(settings: CurtainSiteSettingsEntity)

    /**
     * Insert multiple site settings, replacing on conflict.
     *
     * @param settings List of site settings to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<CurtainSiteSettingsEntity>)

    /**
     * Update site settings.
     *
     * @param settings Site settings entity with updated values
     */
    @Update
    suspend fun updateSiteSettings(settings: CurtainSiteSettingsEntity)

    /**
     * Update the active status of a site.
     *
     * @param hostname Site hostname
     * @param active New active status
     */
    @Query("UPDATE site_settings SET active = :active WHERE hostname = :hostname")
    suspend fun updateActiveStatus(hostname: String, active: Boolean)

    /**
     * Update the last sync timestamp.
     *
     * @param hostname Site hostname
     * @param timestamp Sync timestamp in milliseconds
     */
    @Query("UPDATE site_settings SET lastSync = :timestamp WHERE hostname = :hostname")
    suspend fun updateLastSync(hostname: String, timestamp: Long)

    /**
     * Update the API key for a site.
     *
     * @param hostname Site hostname
     * @param apiKey New API key
     */
    @Query("UPDATE site_settings SET apiKey = :apiKey WHERE hostname = :hostname")
    suspend fun updateApiKey(hostname: String, apiKey: String?)

    /**
     * Update the hostname for a site (for migration purposes).
     *
     * @param oldHostname Current hostname
     * @param newHostname New hostname
     */
    @Query("UPDATE site_settings SET hostname = :newHostname WHERE hostname = :oldHostname")
    suspend fun updateHostname(oldHostname: String, newHostname: String)

    /**
     * Delete site settings.
     *
     * @param settings Site settings entity to delete
     */
    @Delete
    suspend fun deleteSiteSettings(settings: CurtainSiteSettingsEntity)

    /**
     * Delete site settings by hostname.
     *
     * @param hostname Site hostname
     */
    @Query("DELETE FROM site_settings WHERE hostname = :hostname")
    suspend fun deleteSiteSettingsByHostname(hostname: String)

    /**
     * Get count of active sites.
     *
     * @return Number of active sites
     */
    @Query("SELECT COUNT(*) FROM site_settings WHERE active = 1")
    suspend fun getActiveSiteCount(): Int
}
