package info.proteo.curtain.domain.service

import info.proteo.curtain.domain.model.CurtainData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IDMappingService @Inject constructor(
    private val proteinMappingService: ProteinMappingService,
    private val proteomicsDataService: ProteomicsDataService
) {

    suspend fun getPrimaryIDsFromGeneNames(
        geneName: String,
        curtainData: CurtainData
    ): List<String> {
        return proteinMappingService.getPrimaryIdsFromGeneName(curtainData.linkId, geneName)
    }

    suspend fun getPrimaryIDsFromAcc(
        primaryID: String,
        curtainData: CurtainData
    ): List<String> {
        return proteinMappingService.getPrimaryIdsFromSplitId(curtainData.linkId, primaryID)
    }

    suspend fun batchSearchProteins(
        curtainData: CurtainData,
        searchTerms: List<String>,
        searchType: SearchType,
        useRegex: Boolean
    ): Map<String, List<String>> {
        android.util.Log.d("IDMappingService", "batchSearchProteins called: ${searchTerms.size} terms, searchType=$searchType, useRegex=$useRegex")
        val results = mutableMapOf<String, List<String>>()

        searchTerms.forEach { searchTerm ->
            val matches = if (useRegex) {
                findRegexMatches(searchTerm, searchType, curtainData)
            } else {
                listOf(searchTerm)
            }
            android.util.Log.d("IDMappingService", "Search term '$searchTerm' expanded to ${matches.size} matches")

            val primaryIds = mutableSetOf<String>()
            matches.forEach { match ->
                val ids = when (searchType) {
                    SearchType.GENE_NAMES ->
                        getPrimaryIDsFromGeneNames(match, curtainData)
                    SearchType.PRIMARY_IDS ->
                        getPrimaryIDsFromAcc(match, curtainData)
                }
                android.util.Log.d("IDMappingService", "Match '$match' resolved to ${ids.size} primary IDs")
                primaryIds.addAll(ids)
            }

            if (primaryIds.isNotEmpty()) {
                results[searchTerm] = primaryIds.toList()
            }
            android.util.Log.d("IDMappingService", "Search term '$searchTerm' total: ${primaryIds.size} primary IDs")
        }

        android.util.Log.d("IDMappingService", "batchSearchProteins complete: ${results.size} result groups")
        return results
    }

    private suspend fun findRegexMatches(
        pattern: String,
        searchType: SearchType,
        curtainData: CurtainData
    ): List<String> {
        val matches = mutableListOf<String>()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return emptyList()
        }

        when (searchType) {
            SearchType.GENE_NAMES -> {
                val allGenes = curtainData.extraData?.data?.allGenes ?: emptyList()
                allGenes.forEach { geneName ->
                    if (regex.containsMatchIn(geneName)) {
                        matches.add(geneName)
                    }
                }
            }
            SearchType.PRIMARY_IDS -> {
                val db = proteomicsDataService.getDatabaseForLinkId(curtainData.linkId)
                val allData = db.proteomicsDataDao().getAllProcessedData()
                allData.forEach { proteinData ->
                    if (regex.containsMatchIn(proteinData.primaryId)) {
                        matches.add(proteinData.primaryId)
                    }
                }
            }
        }

        return matches.distinct()
    }
}

enum class SearchType {
    GENE_NAMES,
    PRIMARY_IDS
}
