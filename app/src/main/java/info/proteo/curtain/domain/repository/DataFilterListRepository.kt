package info.proteo.curtain.domain.repository

import info.proteo.curtain.data.local.entity.DataFilterListEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for data filter list operations.
 * Manages predefined protein/gene filter lists from backend.
 *
 * Matches iOS filter list management.
 */
interface DataFilterListRepository {

    /**
     * Get all filters as Flow.
     *
     * @return Flow of all filter entities
     */
    fun getAllFilters(): Flow<List<DataFilterListEntity>>

    /**
     * Get filters by category.
     *
     * @param category Filter category (e.g., "GO", "KEGG")
     * @return Flow of filter entities in the category
     */
    fun getFiltersByCategory(category: String): Flow<List<DataFilterListEntity>>

    /**
     * Get all default filters.
     *
     * @return Flow of default filter entities
     */
    fun getDefaultFilters(): Flow<List<DataFilterListEntity>>

    /**
     * Search filters by name.
     *
     * @param query Search query
     * @return Flow of matching filter entities
     */
    fun searchFilters(query: String): Flow<List<DataFilterListEntity>>

    /**
     * Get all distinct categories.
     *
     * @return Flow of category names
     */
    fun getAllCategories(): Flow<List<String>>

    /**
     * Get a specific filter by API ID.
     *
     * @param apiId Filter API ID from backend
     * @return Filter entity or null if not found
     */
    suspend fun getFilterByApiId(apiId: Int): DataFilterListEntity?

    /**
     * Get a specific filter by local ID.
     *
     * @param id Local database ID
     * @return Filter entity or null if not found
     */
    suspend fun getFilterById(id: Long): DataFilterListEntity?

    /**
     * Sync filters from backend API to local database.
     *
     * @param hostname Backend server hostname
     * @param limit Maximum number of results to fetch (default 10, max 10)
     * @return Result with list of synced filter entities
     */
    suspend fun syncFilters(
        hostname: String,
        limit: Int = 10
    ): Result<List<DataFilterListEntity>>

    /**
     * Sync filters by category from backend.
     *
     * @param hostname Backend server hostname
     * @param category Filter category
     * @param limit Maximum number of results
     * @return Result with list of synced filter entities
     */
    suspend fun syncFiltersByCategory(
        hostname: String,
        category: String,
        limit: Int = 10
    ): Result<List<DataFilterListEntity>>

    /**
     * Insert a single filter.
     *
     * @param filter Filter entity to insert
     * @return Row ID of inserted filter
     */
    suspend fun insertFilter(filter: DataFilterListEntity): Long

    /**
     * Insert multiple filters.
     *
     * @param filters List of filter entities
     */
    suspend fun insertAll(filters: List<DataFilterListEntity>)

    /**
     * Update a filter.
     *
     * @param filter Filter entity with updated values
     */
    suspend fun updateFilter(filter: DataFilterListEntity)

    /**
     * Delete a filter.
     *
     * @param filter Filter entity to delete
     */
    suspend fun deleteFilter(filter: DataFilterListEntity)

    /**
     * Delete a filter by local ID.
     *
     * @param id Local database ID
     */
    suspend fun deleteFilterById(id: Long)

    /**
     * Delete filters by category.
     *
     * @param category Filter category
     */
    suspend fun deleteFiltersByCategory(category: String)

    /**
     * Get count of filters by category.
     *
     * @param category Filter category
     * @return Number of filters in the category
     */
    suspend fun getFilterCountByCategory(category: String): Int

    /**
     * Get total filter count.
     *
     * @return Total number of filters
     */
    suspend fun getFilterCount(): Int
}
