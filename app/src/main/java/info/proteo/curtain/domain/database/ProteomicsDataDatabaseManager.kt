package info.proteo.curtain.domain.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.data.local.ProteomicsDataDatabase
import info.proteo.curtain.data.local.entity.ProteomicsDataMetadataEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProteomicsDataDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val databases = mutableMapOf<String, ProteomicsDataDatabase>()

    companion object {
        const val SCHEMA_VERSION_KEY = "schema_version"
        const val CURRENT_SCHEMA_VERSION = 5
    }

    suspend fun getDatabaseForLinkId(linkId: String): ProteomicsDataDatabase {
        return databases.getOrPut(linkId) {
            val dbName = "proteomics_data_${linkId}.db"
            Log.d("ProteomicsDataDB", "Creating/opening database: $dbName")
            Room.databaseBuilder(
                context,
                ProteomicsDataDatabase::class.java,
                dbName
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    suspend fun checkDataExists(linkId: String): Boolean {
        val db = getDatabaseForLinkId(linkId)
        val processedCount = db.proteomicsDataDao().getProcessedDataCount()
        val rawCount = db.proteomicsDataDao().getRawDataCount()
        val storedVersion = db.proteomicsDataDao().getMetadata(SCHEMA_VERSION_KEY)?.toIntOrNull()

        Log.d("ProteomicsDataDB", "checkDataExists for $linkId: processedCount=$processedCount, rawCount=$rawCount, storedVersion=$storedVersion")

        if ((processedCount > 0 || rawCount > 0) && storedVersion != CURRENT_SCHEMA_VERSION) {
            Log.d("ProteomicsDataDB", "Schema version mismatch for $linkId. Will rebuild.")
            return false
        }

        return (processedCount > 0 || rawCount > 0) && storedVersion == CURRENT_SCHEMA_VERSION
    }

    suspend fun clearAllData(linkId: String) {
        Log.d("ProteomicsDataDB", "Clearing all data for $linkId")
        val db = getDatabaseForLinkId(linkId)
        db.proteomicsDataDao().deleteAllProcessedData()
        db.proteomicsDataDao().deleteAllRawData()
        db.proteomicsDataDao().deleteAllMetadata()
        db.proteomicsDataDao().deleteAllGenesMap()
        db.proteomicsDataDao().deleteAllPrimaryIdsMap()
        db.proteomicsDataDao().deleteAllGeneNameToAcc()
        db.proteomicsDataDao().deleteAllGenes()
        db.proteomicsDataDao().deleteCurtainMetadata()
    }

    suspend fun storeSchemaVersion(linkId: String) {
        val db = getDatabaseForLinkId(linkId)
        db.proteomicsDataDao().insertMetadata(
            ProteomicsDataMetadataEntity(
                key = SCHEMA_VERSION_KEY,
                value = CURRENT_SCHEMA_VERSION.toString()
            )
        )
        Log.d("ProteomicsDataDB", "Stored schema version $CURRENT_SCHEMA_VERSION for $linkId")
    }

    fun closeDatabase(linkId: String) {
        databases.remove(linkId)?.close()
        Log.d("ProteomicsDataDB", "Closed database for $linkId")
    }

    fun closeAllDatabases() {
        databases.values.forEach { it.close() }
        databases.clear()
        Log.d("ProteomicsDataDB", "Closed all databases")
    }
}
