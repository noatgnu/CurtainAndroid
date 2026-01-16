package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "all_genes",
    indices = [Index(value = ["geneName"], unique = true)]
)
data class AllGenesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val geneName: String
)
