package info.proteo.curtain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "data_filter_list")
data class DataFilterListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    val data: String,
    @ColumnInfo(name = "default")
    val isDefault: Boolean = false,
    val user: Int? = null // userId, nullable
)

