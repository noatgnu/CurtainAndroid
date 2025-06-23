package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>
)

interface DataFilterListApi {
    @GET("data_filter_list/")
    suspend fun getAllDataFilterLists(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<PaginatedResponse<DataFilterList>>

    @GET("data_filter_list/")
    suspend fun getDataFilterListsByCategory(
        @Query("category_exact") category: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<PaginatedResponse<DataFilterList>>

    @GET("data_filter_list/{id}/")
    suspend fun getDataFilterList(@Path("id") id: Int): Response<DataFilterList>

    @POST("data_filter_list/")
    suspend fun createDataFilterList(@Body dataFilterList: DataFilterListRequest): Response<DataFilterList>

    @PUT("data_filter_list/{id}/")
    suspend fun updateDataFilterList(
        @Path("id") id: Int,
        @Body dataFilterList: DataFilterListRequest
    ): Response<DataFilterList>

    @DELETE("data_filter_list/{id}/")
    suspend fun deleteDataFilterList(@Path("id") id: Int): Response<Unit>

    @GET("data_filter_list/get_all_category/")
    suspend fun getAllCategory(): Response<List<String>>
}

// Request model for creating/updating filter lists
@JsonClass(generateAdapter = true)
data class DataFilterListRequest(
    val name: String,
    val category: String,
    val data: String,
    @Json(name = "default")
    val isDefault: Boolean = false
)
