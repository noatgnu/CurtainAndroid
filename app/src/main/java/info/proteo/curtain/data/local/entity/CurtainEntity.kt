package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Curtain dataset metadata.
 * Matches iOS CurtainEntity SwiftData model.
 *
 * @property linkId Unique identifier for the curtain dataset
 * @property created Timestamp when the dataset was created (milliseconds)
 * @property updated Timestamp when the dataset was last updated (milliseconds)
 * @property file Local file path to downloaded JSON data, null if not downloaded
 * @property dataDescription User-friendly description of the dataset
 * @property enable Whether the dataset is enabled for analysis
 * @property curtainType Type of dataset ("TP" for Total Proteome, "CC" for Comparative)
 * @property sourceHostname Backend server hostname where dataset originates
 * @property frontendURL Optional URL to frontend web interface
 * @property isPinned Whether the dataset is pinned for quick access
 */
@Entity(
    tableName = "curtain",
    indices = [Index(value = ["linkId"], unique = true)]
)
data class CurtainEntity(
    @PrimaryKey
    val linkId: String,
    val created: Long,
    val updated: Long,
    val file: String?,
    val dataDescription: String,
    val enable: Boolean,
    val curtainType: String,
    val sourceHostname: String,
    val frontendURL: String?,
    val isPinned: Boolean = false
)
