package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processed_proteomics_data",
    indices = [Index(value = ["primaryId", "comparison"], unique = true)]
)
data class ProcessedProteomicsDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val primaryId: String,
    val geneNames: String?,
    val foldChange: Double?,
    val significant: Double?,
    val comparison: String
)

@Entity(
    tableName = "raw_proteomics_data",
    indices = [Index(value = ["primaryId", "sampleName"], unique = true)]
)
data class RawProteomicsDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val primaryId: String,
    val sampleName: String,
    val sampleValue: Double?
)

@Entity(tableName = "proteomics_data_metadata")
data class ProteomicsDataMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
