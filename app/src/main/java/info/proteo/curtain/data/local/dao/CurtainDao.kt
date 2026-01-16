package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.proteo.curtain.data.local.entity.CurtainEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Curtain dataset operations.
 * Provides reactive queries using Flow for observing database changes.
 */
@Dao
interface CurtainDao {

    /**
     * Get all curtains ordered by most recently updated.
     *
     * @return Flow of all curtain entities
     */
    @Query("SELECT * FROM curtain ORDER BY updated DESC")
    fun getAllCurtains(): Flow<List<CurtainEntity>>

    /**
     * Get a specific curtain by its link ID.
     *
     * @param linkId Unique identifier of the curtain
     * @return Curtain entity or null if not found
     */
    @Query("SELECT * FROM curtain WHERE linkId = :linkId")
    suspend fun getCurtainById(linkId: String): CurtainEntity?

    /**
     * Get all curtains from a specific backend hostname.
     *
     * @param hostname Backend server hostname
     * @return Flow of curtain entities from the specified host
     */
    @Query("SELECT * FROM curtain WHERE sourceHostname = :hostname ORDER BY updated DESC")
    fun getCurtainsByHostname(hostname: String): Flow<List<CurtainEntity>>

    /**
     * Get all pinned curtains.
     *
     * @return Flow of pinned curtain entities
     */
    @Query("SELECT * FROM curtain WHERE isPinned = 1 ORDER BY updated DESC")
    fun getPinnedCurtains(): Flow<List<CurtainEntity>>

    /**
     * Search curtains by description or link ID.
     *
     * @param searchQuery Search term
     * @return Flow of matching curtain entities
     */
    @Query("SELECT * FROM curtain WHERE dataDescription LIKE '%' || :searchQuery || '%' OR linkId LIKE '%' || :searchQuery || '%' ORDER BY updated DESC")
    fun searchCurtains(searchQuery: String): Flow<List<CurtainEntity>>

    /**
     * Insert a single curtain, replacing on conflict.
     *
     * @param curtain Curtain entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurtain(curtain: CurtainEntity)

    /**
     * Insert multiple curtains, replacing on conflict.
     *
     * @param curtains List of curtain entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(curtains: List<CurtainEntity>)

    /**
     * Update an existing curtain.
     *
     * @param curtain Curtain entity with updated values
     */
    @Update
    suspend fun updateCurtain(curtain: CurtainEntity)

    /**
     * Update the pin status of a curtain.
     *
     * @param linkId Curtain link ID
     * @param isPinned New pin status
     */
    @Query("UPDATE curtain SET isPinned = :isPinned WHERE linkId = :linkId")
    suspend fun updatePinStatus(linkId: String, isPinned: Boolean)

    /**
     * Update the description of a curtain.
     *
     * @param linkId Curtain link ID
     * @param description New description
     */
    @Query("UPDATE curtain SET dataDescription = :description WHERE linkId = :linkId")
    suspend fun updateDescription(linkId: String, description: String)

    /**
     * Update the file path of a downloaded curtain.
     *
     * @param linkId Curtain link ID
     * @param file Local file path
     */
    @Query("UPDATE curtain SET file = :file WHERE linkId = :linkId")
    suspend fun updateFile(linkId: String, file: String)

    /**
     * Delete a curtain.
     *
     * @param curtain Curtain entity to delete
     */
    @Delete
    suspend fun deleteCurtain(curtain: CurtainEntity)

    /**
     * Delete a curtain by its link ID.
     *
     * @param linkId Curtain link ID
     */
    @Query("DELETE FROM curtain WHERE linkId = :linkId")
    suspend fun deleteCurtainById(linkId: String)

    /**
     * Delete all curtains from a specific hostname.
     *
     * @param hostname Backend server hostname
     */
    @Query("DELETE FROM curtain WHERE sourceHostname = :hostname")
    suspend fun deleteCurtainsByHostname(hostname: String)

    /**
     * Get count of all curtains.
     *
     * @return Total number of curtains in database
     */
    @Query("SELECT COUNT(*) FROM curtain")
    suspend fun getCurtainCount(): Int
}
