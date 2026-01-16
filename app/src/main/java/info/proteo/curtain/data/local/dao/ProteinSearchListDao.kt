package info.proteo.curtain.data.local.dao

import androidx.room.*
import info.proteo.curtain.data.local.entity.ProteinSearchListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProteinSearchListDao {

    @Query("SELECT * FROM protein_search_lists WHERE curtainLinkId = :curtainLinkId ORDER BY modifiedAt DESC")
    fun getSearchListsByCurtainId(curtainLinkId: String): Flow<List<ProteinSearchListEntity>>

    @Query("SELECT * FROM protein_search_lists WHERE id = :id")
    suspend fun getSearchListById(id: String): ProteinSearchListEntity?

    @Query("SELECT * FROM protein_search_lists WHERE curtainLinkId = :curtainLinkId AND name = :name LIMIT 1")
    suspend fun getSearchListByName(curtainLinkId: String, name: String): ProteinSearchListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchList(searchList: ProteinSearchListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchLists(searchLists: List<ProteinSearchListEntity>)

    @Update
    suspend fun updateSearchList(searchList: ProteinSearchListEntity)

    @Query("UPDATE protein_search_lists SET name = :name, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, modifiedAt: Long)

    @Query("UPDATE protein_search_lists SET description = :description, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateDescription(id: String, description: String, modifiedAt: Long)

    @Query("UPDATE protein_search_lists SET proteinIds = :proteinIds, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateProteinIds(id: String, proteinIds: String, modifiedAt: Long)

    @Delete
    suspend fun deleteSearchList(searchList: ProteinSearchListEntity)

    @Query("DELETE FROM protein_search_lists WHERE id = :id")
    suspend fun deleteSearchListById(id: String)

    @Query("DELETE FROM protein_search_lists WHERE curtainLinkId = :curtainLinkId")
    suspend fun deleteAllSearchListsForCurtain(curtainLinkId: String)

    @Query("SELECT COUNT(*) FROM protein_search_lists WHERE curtainLinkId = :curtainLinkId")
    suspend fun getSearchListCount(curtainLinkId: String): Int

    @Query("SELECT * FROM protein_search_lists ORDER BY modifiedAt DESC LIMIT :limit")
    suspend fun getRecentSearchLists(limit: Int): List<ProteinSearchListEntity>
}
