package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "data_filter_list",
    indices = [Index(value = ["apiId"], unique = true)]
)
data class DataFilterListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiId: Int?,
    val name: String,
    val category: String,
    val data: String,
    val isDefault: Boolean,
    val user: Int?
)
