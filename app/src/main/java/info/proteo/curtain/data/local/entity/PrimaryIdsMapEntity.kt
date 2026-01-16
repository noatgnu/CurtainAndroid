package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "primary_ids_map")
data class PrimaryIdsMapEntity(
    @PrimaryKey
    val primaryId: String,
    val value: String
)
