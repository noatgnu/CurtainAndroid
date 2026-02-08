package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.proteo.curtain.data.local.entity.AccessionMappingEntity
import info.proteo.curtain.data.local.entity.GeneNameMappingEntity
import info.proteo.curtain.data.local.entity.PrimaryIdMappingEntity
import info.proteo.curtain.data.local.entity.ProteinMappingMetadataEntity

@Dao
interface ProteinMappingDao {

    @Query("SELECT COUNT(*) FROM primary_id_mapping")
    suspend fun checkMappingsExist(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrimaryIdMappings(mappings: List<PrimaryIdMappingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneNameMappings(mappings: List<GeneNameMappingEntity>)

    @Query("SELECT DISTINCT primaryId FROM primary_id_mapping WHERE splitId = :splitId COLLATE NOCASE")
    suspend fun getPrimaryIdsFromSplitId(splitId: String): List<String>

    @Query("SELECT DISTINCT primaryId FROM gene_name_mapping WHERE geneName = :geneName COLLATE NOCASE")
    suspend fun getPrimaryIdsFromGeneName(geneName: String): List<String>

    @Query("SELECT DISTINCT geneName FROM gene_name_mapping WHERE primaryId = :primaryId LIMIT 1")
    suspend fun getGeneNameFromPrimaryId(primaryId: String): String?

    @Query("DELETE FROM primary_id_mapping")
    suspend fun deletePrimaryIdMappings()

    @Query("SELECT DISTINCT geneName FROM gene_name_mapping")
    suspend fun getAllDistinctGeneNames(): List<String>

    @Query("DELETE FROM gene_name_mapping")
    suspend fun deleteGeneNameMappings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessionMappings(mappings: List<AccessionMappingEntity>)

    @Query("SELECT DISTINCT primaryId FROM accession_mapping WHERE accession = :accession COLLATE NOCASE")
    suspend fun getPrimaryIdsFromAccession(accession: String): List<String>

    @Query("SELECT DISTINCT accession FROM accession_mapping WHERE primaryId = :primaryId LIMIT 1")
    suspend fun getAccessionFromPrimaryId(primaryId: String): String?

    @Query("DELETE FROM accession_mapping")
    suspend fun deleteAccessionMappings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: ProteinMappingMetadataEntity)

    @Query("SELECT value FROM protein_mapping_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadata(key: String): Int?

    @Query("DELETE FROM protein_mapping_metadata")
    suspend fun deleteMetadata()
}
