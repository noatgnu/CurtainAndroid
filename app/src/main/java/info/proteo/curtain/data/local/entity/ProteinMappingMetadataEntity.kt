package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protein_mapping_metadata")
data class ProteinMappingMetadataEntity(
    @PrimaryKey
    val key: String = "schema_version",
    val value: Int
)
