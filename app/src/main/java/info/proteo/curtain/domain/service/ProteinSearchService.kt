package info.proteo.curtain.domain.service

import info.proteo.curtain.domain.model.AdvancedFilterParams
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.SearchMatchType
import info.proteo.curtain.domain.model.SearchQuery
import info.proteo.curtain.domain.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ProteinSearchService @Inject constructor(
    private val idMappingService: IDMappingService,
    private val proteomicsDataService: ProteomicsDataService
) {

    suspend fun searchProteins(
        curtainData: CurtainData,
        searchQuery: SearchQuery
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val query = if (searchQuery.caseSensitive) {
            searchQuery.query
        } else {
            searchQuery.query.lowercase()
        }

        val settings = curtainData.settings
        val pCutoff = settings.pCutoff
        val log2FCCutoff = settings.log2FCCutoff
        val linkId = curtainData.linkId

        val db = proteomicsDataService.getDatabaseForLinkId(linkId)
        val allData = db.proteomicsDataDao().getAllProcessedData()

        allData.forEach { proteinData ->
            val proteinId = proteinData.primaryId
            val geneName = proteinData.geneNames
            val fc = proteinData.foldChange
            val p = proteinData.significant

            val isSignificant = if (fc != null && p != null) {
                p < pCutoff && abs(fc) > log2FCCutoff
            } else {
                false
            }

            val matchType = findMatch(
                proteinId,
                geneName,
                query,
                searchQuery
            )

            if (matchType != null) {
                results.add(
                    SearchResult(
                        proteinId = proteinId,
                        geneName = geneName,
                        log2FC = fc,
                        pValue = p,
                        isSignificant = isSignificant,
                        matchType = matchType
                    )
                )
            }
        }

        return results.sortedWith(
            compareByDescending<SearchResult> { it.isSignificant }
                .thenBy { it.matchType.ordinal }
                .thenByDescending { it.log2FC?.let { fc -> abs(fc) } ?: 0.0 }
        )
    }

    suspend fun batchSearchProteins(
        curtainData: CurtainData,
        searchInput: String,
        searchType: SearchType,
        useRegex: Boolean,
        significantOnly: Boolean = false,
        advancedFiltering: AdvancedFilterParams? = null
    ): Map<String, List<SearchResult>> {
        val searchTerms = parseSearchInput(searchInput, useRegex)
        val primaryIdsMap = idMappingService.batchSearchProteins(
            curtainData,
            searchTerms,
            searchType,
            useRegex
        )

        val groupedResults = mutableMapOf<String, List<SearchResult>>()
        val settings = curtainData.settings
        val pCutoff = settings.pCutoff
        val log2FCCutoff = settings.log2FCCutoff
        val foldChangeColumn = curtainData.differentialForm.foldChange
        val significantColumn = curtainData.differentialForm.significant
        val geneNameColumn = curtainData.differentialForm.geneNames

        android.util.Log.d("ProteinSearchService", "Processing ${primaryIdsMap.size} search term groups")
        android.util.Log.d("ProteinSearchService", "Search parameters: significantOnly=$significantOnly, advancedFiltering=${advancedFiltering != null}, pCutoff=$pCutoff, log2FCCutoff=$log2FCCutoff")

        val linkId = curtainData.linkId
        primaryIdsMap.forEach { (searchTerm, primaryIds) ->
            android.util.Log.d("ProteinSearchService", "Term '$searchTerm': ${primaryIds.size} primary IDs")
            val results = mutableListOf<SearchResult>()

            primaryIds.forEach { proteinId ->
                val processedDataList = proteomicsDataService.getProcessedDataForProtein(linkId, proteinId)

                if (processedDataList.isEmpty()) {
                    android.util.Log.d("ProteinSearchService", "Primary ID '$proteinId' NOT FOUND in database")
                } else {
                    android.util.Log.d("ProteinSearchService", "Primary ID '$proteinId' FOUND in database with ${processedDataList.size} entries")

                    processedDataList.forEach { proteinData ->
                        val geneName = proteinData.geneNames
                        val fc = proteinData.foldChange
                        val p = proteinData.significant

                        val isSignificant = if (fc != null && p != null) {
                            p < pCutoff && abs(fc) > log2FCCutoff
                        } else {
                            false
                        }

                        android.util.Log.d("ProteinSearchService", "Protein '$proteinId': fc=$fc, p=$p, isSignificant=$isSignificant, significantOnly=$significantOnly, advancedFiltering=${advancedFiltering != null}")

                        if (!significantOnly || isSignificant) {
                            android.util.Log.d("ProteinSearchService", "Protein '$proteinId': Passed significantOnly filter")
                            if (advancedFiltering == null || matchesAdvancedFilter(fc, p, advancedFiltering, pCutoff)) {
                                android.util.Log.d("ProteinSearchService", "Protein '$proteinId': Passed advancedFilter, ADDING to results")
                            results.add(
                                SearchResult(
                                    proteinId = proteinId,
                                    geneName = geneName,
                                    log2FC = fc,
                                    pValue = p,
                                    isSignificant = isSignificant,
                                    matchType = if (searchType == SearchType.GENE_NAMES)
                                        SearchMatchType.EXACT_GENE_NAME
                                    else
                                        SearchMatchType.EXACT_PROTEIN_ID
                                )
                            )
                            } else {
                                android.util.Log.d("ProteinSearchService", "Protein '$proteinId': FAILED advancedFilter")
                            }
                        } else {
                            android.util.Log.d("ProteinSearchService", "Protein '$proteinId': FAILED significantOnly filter (not significant)")
                        }
                    }
                }
            }

            android.util.Log.d("ProteinSearchService", "Term '$searchTerm': ${results.size} SearchResult objects created")
            if (results.isNotEmpty()) {
                groupedResults[searchTerm] = results.sortedWith(
                    compareByDescending<SearchResult> { it.isSignificant }
                        .thenByDescending { it.log2FC?.let { fc -> abs(fc) } ?: 0.0 }
                )
            }
        }

        android.util.Log.d("ProteinSearchService", "Returning ${groupedResults.size} result groups")
        return groupedResults
    }

    private fun parseSearchInput(input: String, useRegex: Boolean): List<String> {
        return input.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { line ->
                if (!useRegex && line.contains(";")) {
                    line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf(line)
                }
            }
    }

    private fun matchesAdvancedFilter(
        fc: Double?,
        p: Double?,
        params: AdvancedFilterParams,
        pCutoff: Double
    ): Boolean {
        if (fc == null || p == null) return false

        val pValueInRange = p >= params.minP && p <= params.maxP

        if (!params.searchLeft && !params.searchRight) {
            return pValueInRange
        }

        val matchesLeft = if (params.searchLeft) {
            fc >= -params.maxFCLeft && fc <= -params.minFCLeft
        } else {
            false
        }

        val matchesRight = if (params.searchRight) {
            fc >= params.minFCRight && fc <= params.maxFCRight
        } else {
            false
        }

        return pValueInRange && (matchesLeft || matchesRight)
    }

    private fun findMatch(
        proteinId: String,
        geneName: String?,
        query: String,
        searchQuery: SearchQuery
    ): SearchMatchType? {
        val proteinIdToSearch = if (searchQuery.caseSensitive) proteinId else proteinId.lowercase()
        val geneNameToSearch = if (searchQuery.caseSensitive) geneName else geneName?.lowercase()

        if (searchQuery.useRegex) {
            return try {
                val regex = Regex(query, if (searchQuery.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))

                when {
                    searchQuery.searchInProteinIds && regex.matches(proteinIdToSearch) -> SearchMatchType.REGEX_MATCH
                    searchQuery.searchInGeneNames && geneNameToSearch != null && regex.matches(geneNameToSearch) -> SearchMatchType.REGEX_MATCH
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        if (searchQuery.exactMatch) {
            return when {
                searchQuery.searchInProteinIds && proteinIdToSearch == query -> SearchMatchType.EXACT_PROTEIN_ID
                searchQuery.searchInGeneNames && geneNameToSearch == query -> SearchMatchType.EXACT_GENE_NAME
                else -> null
            }
        }

        return when {
            searchQuery.searchInProteinIds && proteinIdToSearch == query -> SearchMatchType.EXACT_PROTEIN_ID
            searchQuery.searchInGeneNames && geneNameToSearch == query -> SearchMatchType.EXACT_GENE_NAME
            searchQuery.searchInProteinIds && proteinIdToSearch.contains(query) -> SearchMatchType.CONTAINS_PROTEIN_ID
            searchQuery.searchInGeneNames && geneNameToSearch?.contains(query) == true -> SearchMatchType.CONTAINS_GENE_NAME
            else -> null
        }
    }

    suspend fun filterBySearchList(
        curtainData: CurtainData,
        proteinIds: List<String>
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        val settings = curtainData.settings
        val pCutoff = settings.pCutoff
        val log2FCCutoff = settings.log2FCCutoff
        val linkId = curtainData.linkId

        proteinIds.forEach { proteinId ->
            val processedDataList = proteomicsDataService.getProcessedDataForProtein(linkId, proteinId)

            processedDataList.forEach { proteinData ->
                val geneName = proteinData.geneNames
                val fc = proteinData.foldChange
                val p = proteinData.significant

                val isSignificant = if (fc != null && p != null) {
                    p < pCutoff && abs(fc) > log2FCCutoff
                } else {
                    false
                }

                results.add(
                    SearchResult(
                        proteinId = proteinId,
                        geneName = geneName,
                        log2FC = fc,
                        pValue = p,
                        isSignificant = isSignificant,
                        matchType = SearchMatchType.EXACT_PROTEIN_ID
                    )
                )
            }
        }

        return results.sortedWith(
            compareByDescending<SearchResult> { it.isSignificant }
                .thenByDescending { it.log2FC?.let { fc -> abs(fc) } ?: 0.0 }
        )
    }

    fun exportSearchResults(results: List<SearchResult>): String {
        val header = "Protein ID,Gene Name,Log2FC,P-Value,Significant,Match Type"
        val rows = results.map { result ->
            listOf(
                result.proteinId,
                result.geneName ?: "",
                result.log2FC?.toString() ?: "",
                result.pValue?.toString() ?: "",
                result.isSignificant.toString(),
                result.matchType.name
            ).joinToString(",")
        }

        return (listOf(header) + rows).joinToString("\n")
    }
}
