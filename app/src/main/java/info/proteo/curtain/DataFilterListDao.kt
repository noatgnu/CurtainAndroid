package info.proteo.curtain

import androidx.room.*

@Dao
interface DataFilterListDao {
    @Query("SELECT * FROM data_filter_list WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): DataFilterListEntity?

    @Query("SELECT * FROM data_filter_list")
    suspend fun getAll(): List<DataFilterListEntity>

    @Query("SELECT DISTINCT category FROM data_filter_list WHERE category != '' ORDER BY category")
    suspend fun getAllCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DataFilterListEntity)

    @Update
    suspend fun update(entity: DataFilterListEntity)

    @Delete
    suspend fun delete(entity: DataFilterListEntity)

    @Transaction
    suspend fun insertAll(entities: List<DataFilterListEntity>) {
        clearAllFilterLists()
        entities.forEach { insert(it) }
    }

    @Query("DELETE FROM data_filter_list")
    suspend fun clearAllFilterLists()
}
