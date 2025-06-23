package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Curtain(
    val id: Int,
    val created: String,  // ISO date format
    @Json(name = "link_id")
    val linkId: String,
    val file: String,
    val enable: Boolean,
    val description: String,
    @Json(name = "curtain_type")
    val curtainType: String,
    val encrypted: Boolean,
    val permanent: Boolean,
    @Json(name = "data_cite")
    val dataCite: DataCite?
)

@JsonClass(generateAdapter = true)
data class DataCite(
    val id: Int,
    // Add other DataCite fields based on DataCiteSerializer
    // Since we don't have the full definition, we're using basic fields
    val title: String?,
    val description: String?
    // Add other fields as needed
)
