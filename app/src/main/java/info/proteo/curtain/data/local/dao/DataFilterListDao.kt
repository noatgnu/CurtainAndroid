package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for data filter list operations.
 * Manages predefined protein/gene filter lists.
 */
@Dao
interface DataFilterListDao {

    /**
     * Get all filters ordered by category and name.
     *
     * @return Flow of all filter entities
     */
    @Query("SELECT * FROM data_filter_list ORDER BY category ASC, name ASC")
    fun getAllFilters(): Flow<List<DataFilterListEntity>>

    /**
     * Get filters by category.
     *
     * @param category Filter category (e.g., "GO", "KEGG")
     * @return Flow of filter entities in the specified category
     */
    @Query("SELECT * FROM data_filter_list WHERE category = :category ORDER BY name ASC")
    fun getFiltersByCategory(category: String): Flow<List<DataFilterListEntity>>

    /**
     * Get all default filters.
     *
     * @return Flow of default filter entities
     */
    @Query("SELECT * FROM data_filter_list WHERE isDefault = 1 ORDER BY category ASC, name ASC")
    fun getDefaultFilters(): Flow<List<DataFilterListEntity>>

    /**
     * Get filters created by a specific user.
     *
     * @param userId User ID
     * @return Flow of user-created filter entities
     */
    @Query("SELECT * FROM data_filter_list WHERE user = :userId ORDER BY name ASC")
    fun getUserFilters(userId: Int): Flow<List<DataFilterListEntity>>

    /**
     * Get a specific filter by API ID.
     *
     * @param apiId Filter API ID from backend
     * @return Filter entity or null if not found
     */
    @Query("SELECT * FROM data_filter_list WHERE apiId = :apiId")
    suspend fun getFilterByApiId(apiId: Int): DataFilterListEntity?

    /**
     * Get a specific filter by local ID.
     *
     * @param id Local database ID
     * @return Filter entity or null if not found
     */
    @Query("SELECT * FROM data_filter_list WHERE id = :id")
    suspend fun getFilterById(id: Long): DataFilterListEntity?

    /**
     * Search filters by name.
     *
     * @param searchQuery Search term
     * @return Flow of matching filter entities
     */
    @Query("SELECT * FROM data_filter_list WHERE name LIKE '%' || :searchQuery || '%' ORDER BY category ASC, name ASC")
    fun searchFilters(searchQuery: String): Flow<List<DataFilterListEntity>>

    /**
     * Get all distinct categories.
     *
     * @return Flow of category names
     */
    @Query("SELECT DISTINCT category FROM data_filter_list ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    /**
     * Insert a single filter, replacing on conflict.
     *
     * @param filter Filter entity to insert
     * @return Row ID of inserted filter
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: DataFilterListEntity): Long

    /**
     * Insert multiple filters, replacing on conflict.
     *
     * @param filters List of filter entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filters: List<DataFilterListEntity>)

    /**
     * Update a filter.
     *
     * @param filter Filter entity with updated values
     */
    @Update
    suspend fun updateFilter(filter: DataFilterListEntity)

    /**
     * Delete a filter.
     *
     * @param filter Filter entity to delete
     */
    @Delete
    suspend fun deleteFilter(filter: DataFilterListEntity)

    /**
     * Delete a filter by its local ID.
     *
     * @param id Local database ID
     */
    @Query("DELETE FROM data_filter_list WHERE id = :id")
    suspend fun deleteFilterById(id: Long)

    /**
     * Delete filters by category.
     *
     * @param category Filter category
     */
    @Query("DELETE FROM data_filter_list WHERE category = :category")
    suspend fun deleteFiltersByCategory(category: String)

    /**
     * Delete all filters created by a specific user.
     *
     * @param userId User ID
     */
    @Query("DELETE FROM data_filter_list WHERE user = :userId")
    suspend fun deleteUserFilters(userId: Int)

    /**
     * Get count of filters by category.
     *
     * @param category Filter category
     * @return Number of filters in the category
     */
    @Query("SELECT COUNT(*) FROM data_filter_list WHERE category = :category")
    suspend fun getFilterCountByCategory(category: String): Int

    /**
     * Get total filter count.
     *
     * @return Total number of filters
     */
    @Query("SELECT COUNT(*) FROM data_filter_list")
    suspend fun getFilterCount(): Int
}
