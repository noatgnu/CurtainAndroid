package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "accession_mapping",
    primaryKeys = ["accession", "primaryId"],
    indices = [
        Index(value = ["accession"]),
        Index(value = ["primaryId"])
    ]
)
data class AccessionMappingEntity(
    val accession: String,
    val primaryId: String
)
