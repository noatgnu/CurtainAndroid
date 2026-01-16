package info.proteo.curtain.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Data transfer object for Curtain API responses.
 * Matches the backend API JSON structure exactly.
 * Uses @SerializedName for snake_case JSON field names.
 */
data class CurtainDto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("created")
    val created: String,

    @SerializedName("link_id")
    val linkId: String,

    @SerializedName("file")
    val file: String,

    @SerializedName("enable")
    val enable: Boolean,

    @SerializedName("description")
    val description: String,

    @SerializedName("curtain_type")
    val curtainType: String,

    @SerializedName("encrypted")
    val encrypted: Boolean,

    @SerializedName("permanent")
    val permanent: Boolean,

    @SerializedName("data_cite")
    val dataCite: DataCiteDto?
)

/**
 * Data transfer object for DataCite metadata.
 */
data class DataCiteDto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("title")
    val title: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("doi")
    val doi: String?,

    @SerializedName("creators")
    val creators: String?,

    @SerializedName("publication_year")
    val publicationYear: Int?
)

/**
 * Data transfer object for data filter list API responses.
 */
data class DataFilterListDto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("category")
    val category: String,

    @SerializedName("data")
    val data: String,

    @SerializedName("default")
    val isDefault: Boolean,

    @SerializedName("user")
    val user: Int?
)

/**
 * Generic paginated response wrapper for API calls.
 *
 * @param T Type of the results list
 */
data class PaginatedResponse<T>(
    @SerializedName("count")
    val count: Int,

    @SerializedName("next")
    val next: String?,

    @SerializedName("previous")
    val previous: String?,

    @SerializedName("results")
    val results: List<T>
)

/**
 * Response for category list endpoint.
 */
data class CategoryListResponse(
    @SerializedName("categories")
    val categories: List<String>
)

/**
 * Generic API error response.
 */
data class ApiError(
    @SerializedName("detail")
    val detail: String?,

    @SerializedName("message")
    val message: String?,

    @SerializedName("error")
    val error: String?
)
