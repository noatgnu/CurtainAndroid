package info.proteo.curtain.domain.service

import info.proteo.curtain.domain.database.ProteinMappingDatabaseManager
import info.proteo.curtain.domain.model.CurtainData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProteinMappingService @Inject constructor(
    private val databaseManager: ProteinMappingDatabaseManager,
    private val proteomicsDataService: ProteomicsDataService
) {

    suspend fun ensureMappingsExist(
        curtainData: CurtainData,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val linkId = curtainData.linkId
        android.util.Log.d("ProteinMappingService", "ensureMappingsExist called for linkId=$linkId")

        if (databaseManager.checkMappingsExist(linkId)) {
            android.util.Log.d("ProteinMappingService", "Mappings already exist for linkId=$linkId")
            return@withContext
        }

        android.util.Log.d("ProteinMappingService", "Building mappings for linkId=$linkId")
        buildMappings(curtainData, onProgress)
    }

    private suspend fun buildMappings(
        curtainData: CurtainData,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val linkId = curtainData.linkId

        android.util.Log.d("ProteinMappingService", "Clearing existing mappings for linkId=$linkId before rebuild")
        databaseManager.clearAllMappings(linkId)

        val db = proteomicsDataService.getDatabaseForLinkId(linkId)
        val allData = db.proteomicsDataDao().getAllProcessedData()
        val primaryIdsList = allData.map { it.primaryId }.distinct()
        val total = primaryIdsList.size

        val primaryIdMappings = mutableListOf<Pair<String, String>>()
        val geneNameMappings = mutableListOf<Pair<String, String>>()

        primaryIdsList.forEachIndexed { index, primaryId ->
            primaryIdMappings.add(Pair(primaryId, primaryId))

            for (splitId in primaryId.split(";")) {
                val trimmedSplitId = splitId.trim()
                if (trimmedSplitId.isNotEmpty()) {
                    primaryIdMappings.add(Pair(trimmedSplitId, primaryId))
                }
            }

            val geneName = getGeneNameForPrimaryId(primaryId, curtainData)
            if (geneName != null && geneName.isNotEmpty()) {
                geneNameMappings.add(Pair(geneName, primaryId))

                for (namePart in geneName.split(";")) {
                    val trimmedNamePart = namePart.trim()
                    if (trimmedNamePart.isNotEmpty()) {
                        geneNameMappings.add(Pair(trimmedNamePart, primaryId))
                    }
                }
            }

            if (index % 100 == 0 || index == total - 1) {
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, total)
                }
            }
        }

        withContext(Dispatchers.Main) {
            onProgress(-1, -1)
        }

        android.util.Log.d("ProteinMappingService", "Inserting ${primaryIdMappings.size} primary ID mappings and ${geneNameMappings.size} gene name mappings")
        databaseManager.insertPrimaryIdMappings(linkId, primaryIdMappings)
        databaseManager.insertGeneNameMappings(linkId, geneNameMappings)
        android.util.Log.d("ProteinMappingService", "Mappings saved successfully for linkId=$linkId")

        withContext(Dispatchers.Main) {
            onProgress(total, total)
        }
    }

    private fun getGeneNameForPrimaryId(primaryId: String, curtainData: CurtainData): String? {
        val db = curtainData.extraData?.uniprot?.db as? Map<*, *>
        if (db == null) {
            return null
        }

        val uniprotEntry = db[primaryId] as? Map<*, *>
        val geneNames = uniprotEntry?.get("Gene Names") as? String

        return geneNames?.replace(" ", ";")?.uppercase()
    }

    suspend fun getPrimaryIdsFromGeneName(linkId: String, geneName: String): List<String> {
        return databaseManager.getPrimaryIdsFromGeneName(linkId, geneName)
    }

    suspend fun getPrimaryIdsFromSplitId(linkId: String, splitId: String): List<String> {
        return databaseManager.getPrimaryIdsFromSplitId(linkId, splitId)
    }

    suspend fun clearMappingsForLinkId(linkId: String) {
        databaseManager.clearAllMappings(linkId)
        android.util.Log.d("ProteinMappingService", "Cleared all protein mappings for $linkId")
    }
}
