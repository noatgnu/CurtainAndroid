package info.proteo.curtain.data.local.dao

import androidx.room.*
import info.proteo.curtain.data.local.entity.SettingsVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsVariantDao {

    @Query("SELECT * FROM settings_variants WHERE curtainLinkId = :curtainLinkId ORDER BY isDefault DESC, modifiedAt DESC")
    fun getVariantsByCurtainId(curtainLinkId: String): Flow<List<SettingsVariantEntity>>

    @Query("SELECT * FROM settings_variants WHERE id = :id")
    suspend fun getVariantById(id: String): SettingsVariantEntity?

    @Query("SELECT * FROM settings_variants WHERE curtainLinkId = :curtainLinkId AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultVariant(curtainLinkId: String): SettingsVariantEntity?

    @Query("SELECT * FROM settings_variants WHERE curtainLinkId = :curtainLinkId AND name = :name LIMIT 1")
    suspend fun getVariantByName(curtainLinkId: String, name: String): SettingsVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variant: SettingsVariantEntity)

    @Update
    suspend fun update(variant: SettingsVariantEntity)

    @Delete
    suspend fun delete(variant: SettingsVariantEntity)

    @Query("DELETE FROM settings_variants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM settings_variants WHERE curtainLinkId = :curtainLinkId")
    suspend fun deleteAllForCurtain(curtainLinkId: String)

    @Query("UPDATE settings_variants SET isDefault = 0 WHERE curtainLinkId = :curtainLinkId")
    suspend fun clearDefaultFlags(curtainLinkId: String)

    @Query("UPDATE settings_variants SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: String)

    @Query("SELECT COUNT(*) FROM settings_variants WHERE curtainLinkId = :curtainLinkId")
    suspend fun getVariantCount(curtainLinkId: String): Int
}
