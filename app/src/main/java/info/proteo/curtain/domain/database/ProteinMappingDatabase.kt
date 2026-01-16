package info.proteo.curtain.domain.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.data.local.ProteinMappingDatabase
import info.proteo.curtain.data.local.entity.GeneNameMappingEntity
import info.proteo.curtain.data.local.entity.PrimaryIdMappingEntity
import info.proteo.curtain.data.local.entity.ProteinMappingMetadataEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProteinMappingDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val databaseInstances = mutableMapOf<String, ProteinMappingDatabase>()

    companion object {
        const val SCHEMA_VERSION_KEY = "schema_version"
        const val CURRENT_SCHEMA_VERSION = 2
    }

    private fun getDatabaseForLinkId(linkId: String): ProteinMappingDatabase {
        return databaseInstances.getOrPut(linkId) {
            Room.databaseBuilder(
                context,
                ProteinMappingDatabase::class.java,
                "protein_mapping_$linkId.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    suspend fun checkMappingsExist(linkId: String): Boolean {
        return try {
            val db = getDatabaseForLinkId(linkId)
            val count = db.proteinMappingDao().checkMappingsExist()
            val storedVersion = db.proteinMappingDao().getMetadata(SCHEMA_VERSION_KEY)

            val exists = count > 0
            val versionMatches = storedVersion == CURRENT_SCHEMA_VERSION

            Log.d("ProteinMappingDB", "checkMappingsExist for linkId '$linkId': count=$count, storedVersion=$storedVersion, currentVersion=$CURRENT_SCHEMA_VERSION")

            if (exists && !versionMatches) {
                Log.d("ProteinMappingDB", "Schema version mismatch for linkId '$linkId'. Stored: $storedVersion, Current: $CURRENT_SCHEMA_VERSION. Will rebuild.")
                return false
            }

            exists && versionMatches
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error checking mappings for $linkId", e)
            false
        }
    }

    suspend fun insertPrimaryIdMappings(linkId: String, mappings: List<Pair<String, String>>) {
        try {
            val db = getDatabaseForLinkId(linkId)
            val entities = mappings.map { (splitId, primaryId) ->
                PrimaryIdMappingEntity(
                    splitId = splitId,
                    primaryId = primaryId
                )
            }
            Log.d("ProteinMappingDB", "Inserting ${entities.size} primary ID mappings for linkId '$linkId'")
            db.proteinMappingDao().insertPrimaryIdMappings(entities)

            val metadata = ProteinMappingMetadataEntity(
                key = SCHEMA_VERSION_KEY,
                value = CURRENT_SCHEMA_VERSION
            )
            db.proteinMappingDao().insertMetadata(metadata)
            Log.d("ProteinMappingDB", "Successfully inserted primary ID mappings and schema version $CURRENT_SCHEMA_VERSION for linkId '$linkId'")
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error inserting primary ID mappings for $linkId", e)
            throw e
        }
    }

    suspend fun insertGeneNameMappings(linkId: String, mappings: List<Pair<String, String>>) {
        try {
            val db = getDatabaseForLinkId(linkId)
            val entities = mappings.map { (geneName, primaryId) ->
                GeneNameMappingEntity(
                    geneName = geneName,
                    primaryId = primaryId
                )
            }
            Log.d("ProteinMappingDB", "Inserting ${entities.size} gene name mappings for linkId '$linkId'")
            db.proteinMappingDao().insertGeneNameMappings(entities)
            Log.d("ProteinMappingDB", "Successfully inserted gene name mappings for linkId '$linkId'")
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error inserting gene name mappings for $linkId", e)
            throw e
        }
    }

    suspend fun getPrimaryIdsFromSplitId(linkId: String, splitId: String): List<String> {
        return try {
            val db = getDatabaseForLinkId(linkId)
            val results = db.proteinMappingDao().getPrimaryIdsFromSplitId(splitId)
            Log.d("ProteinMappingDB", "Query splitId '$splitId' for linkId '$linkId': ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error querying primary IDs for $linkId", e)
            emptyList()
        }
    }

    suspend fun getPrimaryIdsFromGeneName(linkId: String, geneName: String): List<String> {
        return try {
            val db = getDatabaseForLinkId(linkId)
            val results = db.proteinMappingDao().getPrimaryIdsFromGeneName(geneName)
            Log.d("ProteinMappingDB", "Query geneName '$geneName' for linkId '$linkId': ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error querying gene names for $linkId", e)
            emptyList()
        }
    }

    suspend fun clearAllMappings(linkId: String) {
        try {
            val db = getDatabaseForLinkId(linkId)
            db.proteinMappingDao().deletePrimaryIdMappings()
            db.proteinMappingDao().deleteGeneNameMappings()
            db.proteinMappingDao().deleteMetadata()
            Log.d("ProteinMappingDB", "Cleared all mappings for linkId '$linkId'")
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error clearing mappings for $linkId", e)
            throw e
        }
    }

    suspend fun deleteMappingsForLinkId(linkId: String) {
        try {
            databaseInstances[linkId]?.let { db ->
                db.close()
                databaseInstances.remove(linkId)
            }

            val dbFile = context.getDatabasePath("protein_mapping_$linkId.db")
            if (dbFile.exists()) {
                dbFile.delete()
            }

            val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
            if (shmFile.exists()) {
                shmFile.delete()
            }

            val walFile = File(dbFile.parent, "${dbFile.name}-wal")
            if (walFile.exists()) {
                walFile.delete()
            }
        } catch (e: Exception) {
            Log.e("ProteinMappingDB", "Error deleting mappings for $linkId", e)
        }
    }

    fun closeAll() {
        databaseInstances.values.forEach { it.close() }
        databaseInstances.clear()
    }
}
