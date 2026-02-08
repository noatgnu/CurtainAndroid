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
        val accessionMappings = mutableListOf<Pair<String, String>>()
        val isPTM = curtainData.differentialForm.isPTM

        primaryIdsList.forEachIndexed { index, primaryId ->
            primaryIdMappings.add(Pair(primaryId, primaryId))

            for (splitId in primaryId.split(";")) {
                val trimmedSplitId = splitId.trim()
                if (trimmedSplitId.isNotEmpty()) {
                    primaryIdMappings.add(Pair(trimmedSplitId, primaryId))
                }
            }

            var geneName = getGeneNameForPrimaryId(primaryId, linkId, curtainData)

            if (geneName == null && isPTM) {
                val accessions = allData
                    .filter { it.primaryId == primaryId }
                    .mapNotNull { it.accession }
                    .flatMap { it.split(";") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                for (acc in accessions) {
                    geneName = getGeneNameForAccession(acc, linkId, curtainData)
                    if (geneName != null) break
                }
            }

            if (geneName != null && geneName.isNotEmpty()) {
                geneNameMappings.add(Pair(geneName, primaryId))

                for (namePart in geneName.split(";")) {
                    val trimmedNamePart = namePart.trim()
                    if (trimmedNamePart.isNotEmpty()) {
                        geneNameMappings.add(Pair(trimmedNamePart, primaryId))
                    }
                }
            }

            if (isPTM) {
                val accessionData = allData.filter { it.primaryId == primaryId }
                accessionData.forEach { data ->
                    val accession = data.accession
                    if (accession != null && accession.isNotEmpty()) {
                        accessionMappings.add(Pair(accession, primaryId))
                        for (splitAcc in accession.split(";")) {
                            val trimmedAcc = splitAcc.trim()
                            if (trimmedAcc.isNotEmpty()) {
                                accessionMappings.add(Pair(trimmedAcc, primaryId))
                            }
                        }
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

        android.util.Log.d("ProteinMappingService", "Inserting ${primaryIdMappings.size} primary ID mappings, ${geneNameMappings.size} gene name mappings, and ${accessionMappings.size} accession mappings")
        databaseManager.insertPrimaryIdMappings(linkId, primaryIdMappings)
        databaseManager.insertGeneNameMappings(linkId, geneNameMappings)
        if (accessionMappings.isNotEmpty()) {
            databaseManager.insertAccessionMappings(linkId, accessionMappings)
        }
        android.util.Log.d("ProteinMappingService", "Mappings saved successfully for linkId=$linkId")

        withContext(Dispatchers.Main) {
            onProgress(total, total)
        }
    }

    private suspend fun getGeneNameForAccession(accession: String, linkId: String, curtainData: CurtainData): String? {
        val db = curtainData.extraData?.uniprot?.db as? Map<*, *>
        val accMap = curtainData.extraData?.uniprot?.accMap as? Map<*, *>

        if (db != null) {
            var uniprotEntry = db[accession] as? Map<*, *>

            if (uniprotEntry == null && accMap != null) {
                val canonical = accMap[accession] as? String
                if (canonical != null) {
                    uniprotEntry = db[canonical] as? Map<*, *>
                }
            }

            if (uniprotEntry == null) {
                val regex = Regex("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}")
                val match = regex.find(accession)
                if (match != null) {
                    uniprotEntry = db[match.value] as? Map<*, *>
                }
            }

            val geneNames = uniprotEntry?.get("Gene Names") as? String
            if (!geneNames.isNullOrEmpty()) {
                return geneNames.replace(" ", ";").uppercase()
            }
        }

        var uniprotData = proteomicsDataService.getUniProtData(linkId, accession)

        if (uniprotData == null) {
            val regex = Regex("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}")
            val match = regex.find(accession)
            if (match != null && match.value != accession) {
                uniprotData = proteomicsDataService.getUniProtData(linkId, match.value)
            }
        }

        val geneNames = uniprotData?.get("Gene Names") as? String
        return geneNames?.replace(" ", ";")?.uppercase()
    }

    private suspend fun getGeneNameForPrimaryId(primaryId: String, linkId: String, curtainData: CurtainData): String? {
        val db = curtainData.extraData?.uniprot?.db as? Map<*, *>

        if (db != null) {
            var uniprotEntry = db[primaryId] as? Map<*, *>
            if (uniprotEntry == null) {
                for (splitId in primaryId.split(";")) {
                    val trimmedId = splitId.trim()
                    if (trimmedId.isNotEmpty()) {
                        uniprotEntry = db[trimmedId] as? Map<*, *>
                        if (uniprotEntry != null) break
                    }
                }
            }
            val geneNames = uniprotEntry?.get("Gene Names") as? String
            if (!geneNames.isNullOrEmpty()) {
                return geneNames.replace(" ", ";").uppercase()
            }
        }

        var uniprotData = proteomicsDataService.getUniProtData(linkId, primaryId)
        if (uniprotData == null) {
            for (splitId in primaryId.split(";")) {
                val trimmedId = splitId.trim()
                if (trimmedId.isNotEmpty()) {
                    uniprotData = proteomicsDataService.getUniProtData(linkId, trimmedId)
                    if (uniprotData != null) break
                }
            }
        }

        val geneNames = uniprotData?.get("Gene Names") as? String
        return geneNames?.replace(" ", ";")?.uppercase()
    }

    suspend fun getAllDistinctGeneNames(linkId: String): List<String> {
        return databaseManager.getAllDistinctGeneNames(linkId)
    }

    suspend fun getPrimaryIdsFromGeneName(linkId: String, geneName: String): List<String> {
        return databaseManager.getPrimaryIdsFromGeneName(linkId, geneName)
    }

    suspend fun getPrimaryIdsFromSplitId(linkId: String, splitId: String): List<String> {
        return databaseManager.getPrimaryIdsFromSplitId(linkId, splitId)
    }

    suspend fun getGeneNameFromPrimaryId(linkId: String, primaryId: String): String? {
        return databaseManager.getGeneNameFromPrimaryId(linkId, primaryId)
    }

    suspend fun getPrimaryIdsFromAccession(linkId: String, accession: String): List<String> {
        return databaseManager.getPrimaryIdsFromAccession(linkId, accession)
    }

    suspend fun getAccessionFromPrimaryId(linkId: String, primaryId: String): String? {
        return databaseManager.getAccessionFromPrimaryId(linkId, primaryId)
    }

    suspend fun clearMappingsForLinkId(linkId: String) {
        databaseManager.clearAllMappings(linkId)
        android.util.Log.d("ProteinMappingService", "Cleared all protein mappings for $linkId")
    }
}
