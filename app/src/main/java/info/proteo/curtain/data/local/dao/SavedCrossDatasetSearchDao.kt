package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.proteo.curtain.data.local.entity.SavedCrossDatasetSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCrossDatasetSearchDao {

    @Query("SELECT * FROM saved_cross_dataset_search ORDER BY lastOpened DESC")
    fun getAllSavedSearches(): Flow<List<SavedCrossDatasetSearchEntity>>

    @Query("SELECT * FROM saved_cross_dataset_search ORDER BY lastOpened DESC LIMIT :limit")
    fun getRecentSearches(limit: Int): Flow<List<SavedCrossDatasetSearchEntity>>

    @Query("SELECT * FROM saved_cross_dataset_search WHERE id = :id")
    suspend fun getSearchById(id: Long): SavedCrossDatasetSearchEntity?

    @Query("SELECT * FROM saved_cross_dataset_search WHERE name LIKE '%' || :query || '%' ORDER BY lastOpened DESC")
    fun searchByName(query: String): Flow<List<SavedCrossDatasetSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SavedCrossDatasetSearchEntity): Long

    @Update
    suspend fun updateSearch(search: SavedCrossDatasetSearchEntity)

    @Query("UPDATE saved_cross_dataset_search SET lastOpened = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_cross_dataset_search SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Delete
    suspend fun deleteSearch(search: SavedCrossDatasetSearchEntity)

    @Query("DELETE FROM saved_cross_dataset_search WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM saved_cross_dataset_search")
    suspend fun deleteAllSearches()

    @Query("SELECT COUNT(*) FROM saved_cross_dataset_search")
    suspend fun getSearchCount(): Int
}
