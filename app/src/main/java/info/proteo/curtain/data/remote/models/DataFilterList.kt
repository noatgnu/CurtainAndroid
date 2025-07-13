package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DataFilterList(
    val id: Int,
    val name: String,
    val data: String,
    val category: String,
    @Json(name = "default")
    val isDefault: Boolean
)
