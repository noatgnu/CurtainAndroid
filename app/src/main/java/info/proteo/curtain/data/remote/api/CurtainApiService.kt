package info.proteo.curtain.data.remote.api

import info.proteo.curtain.data.remote.model.CurtainDto
import info.proteo.curtain.data.remote.model.DataFilterListDto
import info.proteo.curtain.data.remote.model.PaginatedResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit service interface for Curtain backend API.
 * Defines all HTTP endpoints for dataset and filter management.
 *
 * Base URL is configured per site in NetworkModule.
 * All endpoints match the iOS URLSession implementation.
 */
interface CurtainApiService {

    /**
     * Get all curtains with pagination.
     * Matches iOS: GET /curtain/
     *
     * @param limit Maximum number of results (default 10, max 10)
     * @param offset Pagination offset
     * @return Paginated list of curtain DTOs
     */
    @GET("curtain/")
    suspend fun getAllCurtains(
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): PaginatedResponse<CurtainDto>

    /**
     * Get a specific curtain by its link ID.
     * Matches iOS: GET /curtain/{linkId}/
     *
     * @param linkId Unique identifier of the curtain
     * @return Curtain DTO
     */
    @GET("curtain/{linkId}/")
    suspend fun getCurtainByLinkId(
        @Path("linkId") linkId: String
    ): CurtainDto

    /**
     * Download curtain data - can return either presigned URL or direct file.
     * Matches iOS: GET /curtain/{linkId}/download/token={token}/
     *
     * Backend can be configured to:
     * 1. Return JSON with presigned URL: {"url": "https://..."}
     * 2. Return file directly as ResponseBody
     *
     * @param linkId Unique identifier of the curtain
     * @param token Optional authentication token (empty string if not required)
     * @return Raw response body
     */
    @GET("curtain/{linkId}/download/token={token}/")
    suspend fun downloadCurtainData(
        @Path("linkId") linkId: String,
        @Path("token") token: String = ""
    ): ResponseBody

    /**
     * Get all data filter lists with pagination.
     * Matches iOS: GET /data_filter_list/
     *
     * @param limit Maximum number of results (default 10, max 10)
     * @param offset Pagination offset
     * @return Paginated list of filter DTOs
     */
    @GET("data_filter_list/")
    suspend fun getAllDataFilterLists(
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): PaginatedResponse<DataFilterListDto>

    /**
     * Get data filter lists by exact category match.
     * Matches iOS: GET /data_filter_list/?category_exact={category}
     *
     * @param category Exact category name (e.g., "GO", "KEGG", "Custom")
     * @param limit Maximum number of results (default 10, max 10)
     * @param offset Pagination offset
     * @return Paginated list of filter DTOs in the category
     */
    @GET("data_filter_list/")
    suspend fun getDataFilterListsByCategory(
        @Query("category_exact") category: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): PaginatedResponse<DataFilterListDto>

    /**
     * Get all available filter categories.
     * Matches iOS: GET /data_filter_list/get_all_category/
     *
     * @return List of category names
     */
    @GET("data_filter_list/get_all_category/")
    suspend fun getAllCategories(): List<String>

    /**
     * Get a specific data filter list by ID.
     * Matches iOS: GET /data_filter_list/{id}/
     *
     * @param id Filter list ID
     * @return Filter list DTO
     */
    @GET("data_filter_list/{id}/")
    suspend fun getDataFilterListById(
        @Path("id") id: Int
    ): DataFilterListDto

    /**
     * Search curtains by description.
     * Matches iOS search functionality.
     *
     * @param search Search query
     * @param limit Maximum number of results
     * @return Paginated list of matching curtain DTOs
     */
    @GET("curtain/")
    suspend fun searchCurtains(
        @Query("search") search: String,
        @Query("limit") limit: Int = 10
    ): PaginatedResponse<CurtainDto>
}
