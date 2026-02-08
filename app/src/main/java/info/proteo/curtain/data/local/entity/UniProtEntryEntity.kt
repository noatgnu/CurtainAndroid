package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "uniprot_entries",
    indices = [Index(value = ["accession"], unique = true)]
)
data class UniProtEntryEntity(
    @PrimaryKey
    val accession: String,
    val dataJson: String
)
