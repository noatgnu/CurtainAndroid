package info.proteo.curtain.data.local.dao

import androidx.room.*
import info.proteo.curtain.data.local.entity.SelectionGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectionGroupDao {

    @Query("SELECT * FROM selection_groups WHERE curtainLinkId = :curtainLinkId ORDER BY createdAt DESC")
    fun getSelectionGroupsByCurtainId(curtainLinkId: String): Flow<List<SelectionGroupEntity>>

    @Query("SELECT * FROM selection_groups WHERE curtainLinkId = :curtainLinkId AND isActive = 1")
    fun getActiveSelectionGroups(curtainLinkId: String): Flow<List<SelectionGroupEntity>>

    @Query("SELECT * FROM selection_groups WHERE id = :id")
    suspend fun getSelectionGroupById(id: String): SelectionGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelectionGroup(selectionGroup: SelectionGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelectionGroups(selectionGroups: List<SelectionGroupEntity>)

    @Update
    suspend fun updateSelectionGroup(selectionGroup: SelectionGroupEntity)

    @Query("UPDATE selection_groups SET isActive = :isActive, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean, modifiedAt: Long)

    @Query("UPDATE selection_groups SET proteins = :proteins, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateProteins(id: String, proteins: String, modifiedAt: Long)

    @Query("UPDATE selection_groups SET name = :name, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, modifiedAt: Long)

    @Query("UPDATE selection_groups SET color = :color, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateColor(id: String, color: String, modifiedAt: Long)

    @Delete
    suspend fun deleteSelectionGroup(selectionGroup: SelectionGroupEntity)

    @Query("DELETE FROM selection_groups WHERE id = :id")
    suspend fun deleteSelectionGroupById(id: String)

    @Query("DELETE FROM selection_groups WHERE curtainLinkId = :curtainLinkId")
    suspend fun deleteAllSelectionGroupsForCurtain(curtainLinkId: String)

    @Query("SELECT COUNT(*) FROM selection_groups WHERE curtainLinkId = :curtainLinkId")
    suspend fun getSelectionGroupCount(curtainLinkId: String): Int

    @Query("SELECT * FROM selection_groups WHERE curtainLinkId = :curtainLinkId AND proteins LIKE '%' || :proteinId || '%'")
    suspend fun getGroupsContainingProtein(curtainLinkId: String, proteinId: String): List<SelectionGroupEntity>
}
