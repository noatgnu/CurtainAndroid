package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "gene_name_mapping",
    primaryKeys = ["geneName", "primaryId"],
    indices = [Index(value = ["geneName"])]
)
data class GeneNameMappingEntity(
    val geneName: String,
    val primaryId: String
)
