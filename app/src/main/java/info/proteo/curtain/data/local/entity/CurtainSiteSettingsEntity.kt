package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for backend site settings.
 * Stores configuration for different Curtain backend servers.
 * Matches iOS CurtainSiteSettings SwiftData model.
 *
 * @property hostname Backend server hostname (e.g., "celsus.muttsu.xyz")
 * @property lastSync Timestamp of last successful sync (milliseconds)
 * @property active Whether this site is currently active for queries
 * @property apiKey Optional API key for authenticated requests
 * @property notes User notes about this site
 * @property siteDescription Description of the site/server
 * @property requiresAuthentication Whether this site requires API key
 * @property createdAt Timestamp when site was added (milliseconds)
 */
@Entity(
    tableName = "site_settings",
    indices = [Index(value = ["hostname"], unique = true)]
)
data class CurtainSiteSettingsEntity(
    @PrimaryKey
    val hostname: String,
    val lastSync: Long,
    val active: Boolean,
    val apiKey: String?,
    val notes: String?,
    val siteDescription: String?,
    val requiresAuthentication: Boolean,
    val createdAt: Long
)
