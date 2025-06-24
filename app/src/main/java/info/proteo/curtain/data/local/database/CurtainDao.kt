package info.proteo.curtain

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.local.database.entities.CurtainSiteSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface CurtainDao {
    // Existing CurtainEntity operations
    @Query("SELECT * FROM curtain")
    fun getAll(): Flow<List<CurtainEntity>>

    @Query("SELECT * FROM curtain WHERE source_hostname = :hostname")
    fun getAllByHostname(hostname: String): Flow<List<CurtainEntity>>

    @Query("SELECT * FROM curtain WHERE link_id = :linkId")
    suspend fun getById(linkId: String): CurtainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(curtain: CurtainEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(curtains: List<CurtainEntity>)

    @Update
    suspend fun update(curtain: CurtainEntity)

    @Delete
    suspend fun delete(curtain: CurtainEntity)

    // New CurtainSiteSettings operations
    @Query("SELECT * FROM curtain_site_settings")
    fun getAllSiteSettings(): Flow<List<CurtainSiteSettings>>

    @Query("SELECT * FROM curtain_site_settings WHERE hostname = :hostname")
    suspend fun getSiteSettingsByHostname(hostname: String): CurtainSiteSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteSettings(siteSettings: CurtainSiteSettings)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSiteSettings(siteSettings: List<CurtainSiteSettings>)

    @Update
    suspend fun updateSiteSettings(siteSettings: CurtainSiteSettings)

    @Delete
    suspend fun deleteSiteSettings(siteSettings: CurtainSiteSettings)

    @Query("SELECT * FROM curtain_site_settings WHERE active = 1")
    fun getActiveSiteSettings(): Flow<List<CurtainSiteSettings>>
}