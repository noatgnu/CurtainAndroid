package info.proteo.curtain.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey

@Entity(
    tableName = "curtain",
    foreignKeys = [
        ForeignKey(
            entity = CurtainSiteSettings::class,
            parentColumns = ["hostname"],
            childColumns = ["source_hostname"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CurtainEntity(
    @PrimaryKey
    @ColumnInfo(name = "link_id")
    val linkId: String,
    val created: Long,
    val updated: Long,
    val file: String?,
    val description: String,
    val enable: Boolean = true,
    @ColumnInfo(name = "curtain_type")
    val curtainType: String = "TP",
    @ColumnInfo(name = "source_hostname")
    val sourceHostname: String
)
