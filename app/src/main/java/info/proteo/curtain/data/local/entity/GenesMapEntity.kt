package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "genes_map")
data class GenesMapEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
