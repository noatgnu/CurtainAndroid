package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gene_name_to_acc")
data class GeneNameToAccEntity(
    @PrimaryKey
    val geneName: String,
    val accession: String
)
