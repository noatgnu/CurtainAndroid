package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.proteo.curtain.data.local.entity.AllGenesEntity
import info.proteo.curtain.data.local.entity.CurtainMetadataEntity
import info.proteo.curtain.data.local.entity.GeneNameToAccEntity
import info.proteo.curtain.data.local.entity.GenesMapEntity
import info.proteo.curtain.data.local.entity.PrimaryIdsMapEntity
import info.proteo.curtain.data.local.entity.ProcessedProteomicsDataEntity
import info.proteo.curtain.data.local.entity.ProteomicsDataMetadataEntity
import info.proteo.curtain.data.local.entity.RawProteomicsDataEntity

@Dao
interface ProteomicsDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessedData(data: List<ProcessedProteomicsDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawData(data: List<RawProteomicsDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: ProteomicsDataMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurtainMetadata(metadata: CurtainMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenesMap(data: List<GenesMapEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrimaryIdsMap(data: List<PrimaryIdsMapEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneNameToAcc(data: List<GeneNameToAccEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGenes(data: List<AllGenesEntity>)

    @Query("SELECT * FROM curtain_metadata WHERE id = 1")
    suspend fun getCurtainMetadata(): CurtainMetadataEntity?

    @Query("SELECT * FROM processed_proteomics_data WHERE primaryId = :primaryId")
    suspend fun getProcessedDataByPrimaryId(primaryId: String): List<ProcessedProteomicsDataEntity>

    @Query("SELECT * FROM processed_proteomics_data WHERE primaryId IN (:primaryIds)")
    suspend fun getProcessedDataByPrimaryIds(primaryIds: List<String>): List<ProcessedProteomicsDataEntity>

    @Query("SELECT * FROM processed_proteomics_data WHERE comparison = :comparison")
    suspend fun getProcessedDataByComparison(comparison: String): List<ProcessedProteomicsDataEntity>

    @Query("SELECT * FROM processed_proteomics_data")
    suspend fun getAllProcessedData(): List<ProcessedProteomicsDataEntity>

    @Query("SELECT * FROM raw_proteomics_data WHERE primaryId = :primaryId")
    suspend fun getRawDataByPrimaryId(primaryId: String): List<RawProteomicsDataEntity>

    @Query("SELECT * FROM raw_proteomics_data WHERE primaryId IN (:primaryIds)")
    suspend fun getRawDataByPrimaryIds(primaryIds: List<String>): List<RawProteomicsDataEntity>

    @Query("SELECT * FROM genes_map")
    suspend fun getAllGenesMap(): List<GenesMapEntity>

    @Query("SELECT * FROM primary_ids_map")
    suspend fun getAllPrimaryIdsMap(): List<PrimaryIdsMapEntity>

    @Query("SELECT * FROM gene_name_to_acc")
    suspend fun getAllGeneNameToAcc(): List<GeneNameToAccEntity>

    @Query("SELECT * FROM all_genes")
    suspend fun getAllGenes(): List<AllGenesEntity>

    @Query("SELECT COUNT(*) FROM processed_proteomics_data")
    suspend fun getProcessedDataCount(): Int

    @Query("SELECT COUNT(DISTINCT primaryId) FROM processed_proteomics_data")
    suspend fun getDistinctProteinCount(): Int

    @Query("SELECT DISTINCT primaryId FROM processed_proteomics_data ORDER BY primaryId")
    suspend fun getDistinctPrimaryIds(): List<String>

    @Query("SELECT COUNT(*) FROM raw_proteomics_data")
    suspend fun getRawDataCount(): Int

    @Query("SELECT value FROM proteomics_data_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): String?

    @Query("DELETE FROM processed_proteomics_data")
    suspend fun deleteAllProcessedData()

    @Query("DELETE FROM raw_proteomics_data")
    suspend fun deleteAllRawData()

    @Query("DELETE FROM proteomics_data_metadata")
    suspend fun deleteAllMetadata()

    @Query("DELETE FROM genes_map")
    suspend fun deleteAllGenesMap()

    @Query("DELETE FROM primary_ids_map")
    suspend fun deleteAllPrimaryIdsMap()

    @Query("DELETE FROM gene_name_to_acc")
    suspend fun deleteAllGeneNameToAcc()

    @Query("DELETE FROM all_genes")
    suspend fun deleteAllGenes()

    @Query("DELETE FROM curtain_metadata")
    suspend fun deleteCurtainMetadata()
}
