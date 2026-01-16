package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "primary_id_mapping",
    primaryKeys = ["splitId", "primaryId"],
    indices = [Index(value = ["splitId"])]
)
data class PrimaryIdMappingEntity(
    val splitId: String,
    val primaryId: String
)
