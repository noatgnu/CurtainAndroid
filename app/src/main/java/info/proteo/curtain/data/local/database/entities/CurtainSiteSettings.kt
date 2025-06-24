package info.proteo.curtain.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "curtain_site_settings")
data class CurtainSiteSettings(
    @PrimaryKey
    val hostname: String,

    @ColumnInfo(name = "last_sync")
    val lastSync: Long = 0,

    @ColumnInfo(name = "active")
    val active: Boolean = true,

    @ColumnInfo(name = "api_key")
    val apiKey: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)