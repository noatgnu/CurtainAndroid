package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_cross_dataset_search")
data class SavedCrossDatasetSearchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val searchTerms: String,
    val searchType: String,
    val datasetLinkIds: String,
    val significantOnly: Boolean,
    val useRegex: Boolean,
    val resultSummariesJson: String,
    val proteinCount: Int,
    val datasetCount: Int,
    val created: Long = System.currentTimeMillis(),
    val lastOpened: Long = System.currentTimeMillis()
)
