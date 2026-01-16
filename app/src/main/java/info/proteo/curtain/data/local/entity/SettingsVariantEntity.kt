package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings_variants")
data class SettingsVariantEntity(
    @PrimaryKey val id: String,
    val curtainLinkId: String,
    val name: String,
    val description: String,
    val settingsJson: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val modifiedAt: Long
)
