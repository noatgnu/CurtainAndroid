package info.proteo.curtain.domain.service

import android.util.Log
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.domain.database.ProteomicsDataDatabaseManager
import info.proteo.curtain.domain.model.CrossDatasetAdvancedFilterParams
import info.proteo.curtain.domain.model.CrossDatasetMatrix
import info.proteo.curtain.domain.model.CrossDatasetSearchConfig
import info.proteo.curtain.domain.model.CrossDatasetSearchResult
import info.proteo.curtain.domain.model.DatasetComparisonInfo
import info.proteo.curtain.domain.model.DatasetComparisonResult
import info.proteo.curtain.domain.model.DatasetProcessingStatus
import info.proteo.curtain.domain.model.MatrixCell
import info.proteo.curtain.domain.model.MatrixFilterOptions
import info.proteo.curtain.domain.model.MatrixRow
import info.proteo.curtain.domain.model.ProcessingState
import info.proteo.curtain.domain.model.ProteinDetailedReport
import info.proteo.curtain.domain.model.ProteinSearchSummary
import info.proteo.curtain.domain.repository.CurtainRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CrossDatasetSearchService @Inject constructor(
    private val curtainRepository: CurtainRepository,
    private val proteomicsDataService: ProteomicsDataService,
    private val proteinMappingService: ProteinMappingService,
    private val databaseManager: ProteomicsDataDatabaseManager,
    private val curtainDataService: CurtainDataService,
    private val proteinSearchService: ProteinSearchService
) {
    companion object {
        private const val TAG = "CrossDatasetSearch"
    }

    private val _processingStatus = MutableSharedFlow<DatasetProcessingStatus>(replay = 0)
    val processingStatus: SharedFlow<DatasetProcessingStatus> = _processingStatus.asSharedFlow()

    private suspend fun emitStatus(linkId: String, datasetName: String, state: ProcessingState, message: String? = null) {
        _processingStatus.emit(DatasetProcessingStatus(linkId, datasetName, state, message))
    }

    suspend fun getAvailableDatasets(): List<CurtainEntity> {
        return curtainRepository.getAllCurtains().first()
            .filter { it.file != null }
    }

    suspend fun searchAcrossDatasets(
        config: CrossDatasetSearchConfig
    ): CrossDatasetSearchResult = coroutineScope {
        Log.d(TAG, "Starting cross-dataset search: ${config.searchTerms.size} raw terms, ${config.datasetLinkIds.size} datasets")
        Log.d(TAG, "Raw search terms: ${config.searchTerms}")

        val searchTerms = parseSearchInput(config.searchTerms)
        Log.d(TAG, "Parsed search terms: ${searchTerms.size} terms: $searchTerms")

        val datasetResults = config.datasetLinkIds.map { linkId ->
            async {
                searchInDataset(linkId, searchTerms, config.searchType, config.useRegex)
            }
        }.awaitAll()

        val proteinSummaries = aggregateResults(
            searchTerms = searchTerms,
            datasetResults = datasetResults,
            totalDatasets = config.datasetLinkIds.size,
            significantOnly = config.significantOnly,
            advancedFiltering = config.advancedFiltering
        )

        Log.d(TAG, "Search complete: found ${proteinSummaries.size} proteins")

        CrossDatasetSearchResult(
            config = config,
            proteinSummaries = proteinSummaries
        )
    }

    suspend fun getProteinDetailedReport(
        searchTerm: String,
        primaryId: String?,
        datasetLinkIds: List<String>,
        searchType: SearchType
    ): ProteinDetailedReport = coroutineScope {
        Log.d(TAG, "Getting detailed report for $searchTerm in ${datasetLinkIds.size} datasets")

        val results = mutableListOf<DatasetComparisonResult>()
        var resolvedPrimaryId: String? = primaryId
        val accumulatedGeneNames = mutableSetOf<String>()

        datasetLinkIds.forEach { linkId ->
            try {
                val curtain = curtainRepository.getCurtainById(linkId) ?: return@forEach
                val datasetDisplayName = curtain.sessionName?.takeIf { it.isNotBlank() } ?: curtain.dataDescription
                var curtainData = proteomicsDataService.loadCurtainDataFromDatabase(linkId)
                if (curtainData == null && !curtain.file.isNullOrEmpty()) {
                    val loadResult = curtainDataService.loadCurtainDataFromFile(curtain.file)
                    loadResult.getOrNull()?.let { loadedData ->
                        proteinMappingService.ensureMappingsExist(loadedData.curtainData)
                        proteomicsDataService.buildProteomicsDataIfNeeded(
                            linkId = loadedData.curtainData.linkId,
                            rawTsv = loadedData.rawTsv,
                            processedTsv = loadedData.processedTsv,
                            rawForm = loadedData.curtainData.rawForm,
                            differentialForm = loadedData.curtainData.differentialForm,
                            curtainData = loadedData.curtainData
                        )
                        curtainData = loadedData.curtainData
                    }
                }
                if (curtainData == null) return@forEach

                proteinMappingService.ensureMappingsExist(curtainData!!)

                val searchResultsMap = proteinSearchService.batchSearchProteins(
                    curtainData = curtainData,
                    searchInput = searchTerm,
                    searchType = searchType,
                    useRegex = false,
                    significantOnly = false,
                    advancedFiltering = null
                )

                val searchResults = searchResultsMap[searchTerm]
                if (searchResults.isNullOrEmpty()) {
                    val datasetInfo = DatasetComparisonInfo(
                        linkId = linkId,
                        datasetDescription = datasetDisplayName,
                        comparison = "N/A"
                    )
                    results.add(
                        DatasetComparisonResult(
                            datasetInfo = datasetInfo,
                            foldChange = null,
                            pValue = null,
                            isSignificant = false,
                            found = false
                        )
                    )
                } else {
                    searchResults.forEach { searchResult ->
                        if (resolvedPrimaryId == null) {
                            resolvedPrimaryId = searchResult.proteinId
                        }
                        val geneName = getGeneNameForProtein(searchResult.proteinId, linkId, curtainData!!)
                        geneName?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
                            accumulatedGeneNames.add(it)
                        }

                        val datasetInfo = DatasetComparisonInfo(
                            linkId = linkId,
                            datasetDescription = datasetDisplayName,
                            comparison = curtainData.settings.currentComparison.ifEmpty { "1" }
                        )

                        results.add(
                            DatasetComparisonResult(
                                datasetInfo = datasetInfo,
                                foldChange = searchResult.log2FC,
                                pValue = searchResult.pValue,
                                isSignificant = searchResult.isSignificant,
                                found = true
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching in dataset $linkId", e)
            }
        }

        val datasetsFoundIn = results.filter { it.found }.map { it.datasetInfo.linkId }.distinct().size
        val resolvedGeneName = if (accumulatedGeneNames.isNotEmpty()) {
            accumulatedGeneNames.sorted().joinToString(";")
        } else null

        ProteinDetailedReport(
            searchTerm = searchTerm,
            primaryId = resolvedPrimaryId,
            geneName = resolvedGeneName,
            results = results,
            datasetsFoundIn = datasetsFoundIn,
            totalDatasetsSearched = datasetLinkIds.size
        )
    }

    suspend fun buildCrossDatasetMatrix(
        searchResult: CrossDatasetSearchResult,
        filterOptions: MatrixFilterOptions = MatrixFilterOptions()
    ): CrossDatasetMatrix = coroutineScope {
        Log.d(TAG, "Building matrix for ${searchResult.proteinSummaries.size} proteins")

        val proteinIds = searchResult.proteinSummaries.map { it.primaryId ?: it.searchTerm }
        val proteinGeneNames = searchResult.proteinSummaries.associate {
            (it.primaryId ?: it.searchTerm) to it.geneName
        }

        val matrixRows = mutableListOf<MatrixRow>()
        val datasetLinkIds = filterOptions.selectedDatasets?.toList()
            ?: searchResult.config.datasetLinkIds

        datasetLinkIds.forEach { linkId ->
            try {
                val curtain = curtainRepository.getCurtainById(linkId) ?: return@forEach
                val datasetDisplayName = curtain.sessionName?.takeIf { it.isNotBlank() } ?: curtain.dataDescription
                var curtainData = proteomicsDataService.loadCurtainDataFromDatabase(linkId)

                if (curtainData == null && !curtain.file.isNullOrEmpty()) {
                    val loadResult = curtainDataService.loadCurtainDataFromFile(curtain.file)
                    loadResult.getOrNull()?.let { loadedData ->
                        proteinMappingService.ensureMappingsExist(loadedData.curtainData)
                        proteomicsDataService.buildProteomicsDataIfNeeded(
                            linkId = loadedData.curtainData.linkId,
                            rawTsv = loadedData.rawTsv,
                            processedTsv = loadedData.processedTsv,
                            rawForm = loadedData.curtainData.rawForm,
                            differentialForm = loadedData.curtainData.differentialForm,
                            curtainData = loadedData.curtainData
                        )
                        curtainData = loadedData.curtainData
                    }
                }

                if (curtainData == null) return@forEach

                val comparison = curtainData.settings.currentComparison.ifEmpty { "1" }
                val cells = mutableMapOf<String, MatrixCell>()

                searchResult.proteinSummaries.forEach { summary ->
                    val searchTerm = summary.searchTerm
                    val searchResultsMap = proteinSearchService.batchSearchProteins(
                        curtainData = curtainData,
                        searchInput = searchTerm,
                        searchType = searchResult.config.searchType,
                        useRegex = searchResult.config.useRegex,
                        significantOnly = false,
                        advancedFiltering = null
                    )

                    val results = searchResultsMap[searchTerm]
                    val proteinKey = summary.primaryId ?: searchTerm

                    if (results.isNullOrEmpty()) {
                        cells[proteinKey] = MatrixCell(
                            foldChange = null,
                            pValue = null,
                            isSignificant = false,
                            found = false
                        )
                    } else {
                        val firstResult = results.first()
                        val cell = MatrixCell(
                            foldChange = firstResult.log2FC,
                            pValue = firstResult.pValue,
                            isSignificant = firstResult.isSignificant,
                            found = true
                        )

                        val passesFilter = passesMatrixFilter(cell, filterOptions)
                        if (passesFilter) {
                            cells[proteinKey] = cell
                        } else {
                            cells[proteinKey] = cell.copy(found = false)
                        }
                    }
                }

                val conditionLabels = curtainData.settings.volcanoConditionLabels
                val conditionLeft = if (conditionLabels.enabled && conditionLabels.leftCondition.isNotEmpty()) {
                    conditionLabels.leftCondition
                } else null
                val conditionRight = if (conditionLabels.enabled && conditionLabels.rightCondition.isNotEmpty()) {
                    conditionLabels.rightCondition
                } else null

                matrixRows.add(
                    MatrixRow(
                        datasetLinkId = linkId,
                        datasetName = datasetDisplayName,
                        comparison = comparison,
                        conditionLeft = conditionLeft,
                        conditionRight = conditionRight,
                        cells = cells
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error building matrix row for dataset $linkId", e)
            }
        }

        CrossDatasetMatrix(
            proteinIds = proteinIds,
            rows = matrixRows,
            proteinGeneNames = proteinGeneNames
        )
    }

    private fun passesMatrixFilter(cell: MatrixCell, options: MatrixFilterOptions): Boolean {
        if (!cell.found) return !options.hideNotFound
        if (options.showSignificantOnly && !cell.isSignificant) return false
        if (options.minFoldChange != null && cell.foldChange != null) {
            if (abs(cell.foldChange) < options.minFoldChange) return false
        }
        if (options.maxPValue != null && cell.pValue != null) {
            if (cell.pValue > options.maxPValue) return false
        }
        return true
    }

    private suspend fun searchInDataset(
        linkId: String,
        searchTerms: List<String>,
        searchType: SearchType,
        useRegex: Boolean
    ): DatasetSearchResult {
        Log.d(TAG, "searchInDataset called for linkId=$linkId with ${searchTerms.size} terms")
        val results = mutableMapOf<String, ProteinDatasetResult>()

        try {
            val curtain = curtainRepository.getCurtainById(linkId)
            if (curtain == null) {
                Log.e(TAG, "Curtain not found for linkId=$linkId")
                emitStatus(linkId, "Unknown", ProcessingState.FAILED, "Dataset not found")
                return DatasetSearchResult(linkId, emptyMap())
            }
            val datasetName = curtain.sessionName?.takeIf { it.isNotBlank() } ?: curtain.dataDescription
            Log.d(TAG, "Found curtain: $datasetName")

            emitStatus(linkId, datasetName, ProcessingState.LOADING, "Checking database...")

            var curtainData = proteomicsDataService.loadCurtainDataFromDatabase(linkId)
            if (curtainData == null) {
                Log.d(TAG, "CurtainData not in database for linkId=$linkId, loading from file")
                if (curtain.file.isNullOrEmpty()) {
                    Log.e(TAG, "No file available for linkId=$linkId")
                    emitStatus(linkId, datasetName, ProcessingState.FAILED, "No file available")
                    return DatasetSearchResult(linkId, emptyMap())
                }

                emitStatus(linkId, datasetName, ProcessingState.LOADING, "Parsing file...")
                val loadResult = curtainDataService.loadCurtainDataFromFile(curtain.file)
                loadResult.getOrNull()?.let { loadedData ->
                    Log.d(TAG, "Loaded from file, building database for linkId=$linkId")

                    emitStatus(linkId, datasetName, ProcessingState.BUILDING, "Building protein mappings...")
                    proteinMappingService.ensureMappingsExist(loadedData.curtainData)

                    emitStatus(linkId, datasetName, ProcessingState.BUILDING, "Building database...")
                    proteomicsDataService.buildProteomicsDataIfNeeded(
                        linkId = loadedData.curtainData.linkId,
                        rawTsv = loadedData.rawTsv,
                        processedTsv = loadedData.processedTsv,
                        rawForm = loadedData.curtainData.rawForm,
                        differentialForm = loadedData.curtainData.differentialForm,
                        curtainData = loadedData.curtainData
                    )
                    curtainData = loadedData.curtainData
                }
                if (curtainData == null) {
                    Log.e(TAG, "Failed to load curtainData for linkId=$linkId")
                    emitStatus(linkId, datasetName, ProcessingState.FAILED, "Failed to load data")
                    return DatasetSearchResult(linkId, emptyMap())
                }
            }
            Log.d(TAG, "Loaded curtainData for linkId=$linkId")
            emitStatus(linkId, datasetName, ProcessingState.SEARCHING, "Searching...")

            val searchInput = searchTerms.joinToString("\n")
            val searchResultsMap = proteinSearchService.batchSearchProteins(
                curtainData = curtainData,
                searchInput = searchInput,
                searchType = searchType,
                useRegex = useRegex,
                significantOnly = false, // This is handled in the aggregateResults function
                advancedFiltering = null // This is handled in the aggregateResults function
            )

            searchResultsMap.forEach { (searchTerm, searchResults) ->
                if (searchResults.isNotEmpty()) {
                    val firstResult = searchResults.first()
                    val hasSignificant = searchResults.any { it.isSignificant }
                    val foldChanges = searchResults.mapNotNull { it.log2FC }
                    val averageFoldChange = if (foldChanges.isNotEmpty()) foldChanges.average() else null

                    val geneName = getGeneNameForProtein(firstResult.proteinId, linkId, curtainData!!)

                    results[searchTerm] = ProteinDatasetResult(
                        primaryId = firstResult.proteinId,
                        geneName = geneName,
                        found = true,
                        hasSignificant = hasSignificant,
                        averageFoldChange = averageFoldChange
                    )
                }
            }

            emitStatus(linkId, datasetName, ProcessingState.COMPLETED, "Found ${results.size} matches")
        } catch (e: Exception) {
            Log.e(TAG, "Error searching in dataset $linkId: ${e.message}", e)
            emitStatus(linkId, "Unknown", ProcessingState.FAILED, e.message)
        }

        Log.d(TAG, "searchInDataset complete for $linkId, found ${results.size} results")
        return DatasetSearchResult(linkId, results)
    }

    private suspend fun findRegexGeneMatches(
        linkId: String,
        pattern: String,
        curtainData: info.proteo.curtain.domain.model.CurtainData
    ): List<String> {
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return emptyList()
        }

        val allGenes = curtainData.extraData?.data?.allGenes ?: emptyList()
        val matchingGenes = allGenes.filter { regex.containsMatchIn(it) }

        val primaryIds = mutableSetOf<String>()
        matchingGenes.forEach { geneName ->
            primaryIds.addAll(proteinMappingService.getPrimaryIdsFromGeneName(linkId, geneName))
        }

        return primaryIds.toList()
    }

    private suspend fun findRegexPrimaryIdMatches(
        linkId: String,
        pattern: String
    ): List<String> {
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return emptyList()
        }

        val db = databaseManager.getDatabaseForLinkId(linkId)
        val allPrimaryIds = db.proteomicsDataDao().getDistinctPrimaryIds()

        return allPrimaryIds.filter { regex.containsMatchIn(it) }
    }

    private fun aggregateResults(
        searchTerms: List<String>,
        datasetResults: List<DatasetSearchResult>,
        totalDatasets: Int,
        significantOnly: Boolean,
        advancedFiltering: CrossDatasetAdvancedFilterParams? = null
    ): List<ProteinSearchSummary> {
        val summaries = mutableListOf<ProteinSearchSummary>()

        searchTerms.forEach { searchTerm ->
            var resolvedPrimaryId: String? = null
            val accumulatedGeneNames = mutableSetOf<String>()
            var datasetsFoundIn = 0
            var hasSignificant = false
            val allFoldChanges = mutableListOf<Double>()

            datasetResults.forEach { datasetResult ->
                val result = datasetResult.results[searchTerm]
                if (result != null && result.found) {
                    datasetsFoundIn++
                    if (resolvedPrimaryId == null) resolvedPrimaryId = result.primaryId
                    result.geneName?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
                        accumulatedGeneNames.add(it)
                    }
                    if (result.hasSignificant) hasSignificant = true
                    result.averageFoldChange?.let { allFoldChanges.add(it) }
                }
            }

            val resolvedGeneName = if (accumulatedGeneNames.isNotEmpty()) {
                accumulatedGeneNames.sorted().joinToString(";")
            } else null

            if (datasetsFoundIn > 0) {
                val avgFC = if (allFoldChanges.isNotEmpty()) allFoldChanges.average() else null

                val passesAdvancedFilter = advancedFiltering?.let { params ->
                    if (avgFC == null) return@let true

                    val passesLeftFilter = if (params.searchLeft && avgFC < 0) {
                        val absFC = abs(avgFC)
                        absFC >= params.minFCLeft && absFC <= params.maxFCLeft
                    } else !params.searchLeft || avgFC >= 0

                    val passesRightFilter = if (params.searchRight && avgFC > 0) {
                        avgFC >= params.minFCRight && avgFC <= params.maxFCRight
                    } else !params.searchRight || avgFC <= 0

                    if (params.searchLeft && params.searchRight) {
                        passesLeftFilter || passesRightFilter
                    } else if (params.searchLeft) {
                        passesLeftFilter
                    } else if (params.searchRight) {
                        passesRightFilter
                    } else {
                        true
                    }
                } ?: true

                if ((!significantOnly || hasSignificant) && passesAdvancedFilter) {
                    summaries.add(
                        ProteinSearchSummary(
                            searchTerm = searchTerm,
                            primaryId = resolvedPrimaryId,
                            geneName = resolvedGeneName,
                            datasetsFoundIn = datasetsFoundIn,
                            totalDatasetsSearched = totalDatasets,
                            averageFoldChange = avgFC,
                            hasSignificantResult = hasSignificant
                        )
                    )
                }
            }
        }

        return summaries.sortedByDescending { it.datasetsFoundIn }
    }

    private fun parseSearchInput(terms: List<String>): List<String> {
        Log.d(TAG, "parseSearchInput raw terms: $terms")
        return terms.flatMap { term ->
            Log.d(TAG, "Processing term (length=${term.length}): '${term.take(100)}...'")
            Log.d(TAG, "Term contains newline (\\n): ${term.contains("\n")}, contains CR (\\r): ${term.contains("\r")}")
            val splitByNewline = term.split("\n", "\r\n", "\r")
            Log.d(TAG, "After split by newlines: ${splitByNewline.size} parts")
            splitByNewline
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { line ->
                    if (line.contains(";")) {
                        line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        listOf(line)
                    }
                }
        }.distinct()
    }

    fun exportProteinReport(report: ProteinDetailedReport): String {
        val header = "Search Term,Primary ID,Gene Name,Dataset,Comparison,Fold Change,P-Value,Significant,Found"
        val rows = report.results.map { result ->
            listOf(
                report.searchTerm,
                report.primaryId ?: "",
                report.geneName ?: "",
                result.datasetInfo.datasetDescription,
                result.datasetInfo.comparison,
                result.foldChange?.toString() ?: "",
                result.pValue?.toString() ?: "",
                if (result.isSignificant) "Yes" else "No",
                if (result.found) "Yes" else "No"
            ).joinToString(",") { escapeCSV(it) }
        }

        return (listOf(header) + rows).joinToString("\n")
    }

    fun exportAllResults(result: CrossDatasetSearchResult): String {
        val header = "Search Term,Primary ID,Gene Name,Datasets Found,Total Datasets,Average FC,Has Significant"
        val rows = result.proteinSummaries.map { summary ->
            listOf(
                summary.searchTerm,
                summary.primaryId ?: "",
                summary.geneName ?: "",
                summary.datasetsFoundIn.toString(),
                summary.totalDatasetsSearched.toString(),
                summary.averageFoldChange?.let { String.format("%.4f", it) } ?: "",
                if (summary.hasSignificantResult) "Yes" else "No"
            ).joinToString(",") { escapeCSV(it) }
        }

        return (listOf(header) + rows).joinToString("\n")
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private data class DatasetSearchResult(
        val linkId: String,
        val results: Map<String, ProteinDatasetResult>
    )

    private data class ProteinDatasetResult(
        val primaryId: String?,
        val geneName: String?,
        val found: Boolean,
        val hasSignificant: Boolean,
        val averageFoldChange: Double?
    )

    private fun getUniprotFromPrimary(id: String, curtainData: info.proteo.curtain.domain.model.CurtainData): Map<String, Any>? {
        val uniprotDB = curtainData.extraData?.uniprot?.db as? Map<String, Any>
        val dataMap = curtainData.extraData?.uniprot?.dataMap as? Map<String, Any>
        val accMap = curtainData.extraData?.uniprot?.accMap as? Map<String, Any>

        if (uniprotDB == null) return null

        if (uniprotDB.containsKey(id)) {
            return uniprotDB[id] as? Map<String, Any>
        }

        if (accMap != null && accMap.containsKey(id)) {
            val alternatives = accMap[id] as? List<*>
            if (alternatives != null) {
                for (alt in alternatives) {
                    if (dataMap != null && dataMap.containsKey(alt)) {
                        val canonicalEntry = dataMap[alt] as? String
                        if (canonicalEntry != null && uniprotDB.containsKey(canonicalEntry)) {
                            return uniprotDB[canonicalEntry] as? Map<String, Any>
                        }
                    }
                }
            }
        }

        return null
    }

    private fun getGeneNameFromUniProt(id: String, curtainData: info.proteo.curtain.domain.model.CurtainData): String? {
        val uniprotRecord = getUniprotFromPrimary(id, curtainData)
        if (uniprotRecord != null) {
            val geneNames = uniprotRecord["Gene Names"] as? String
            if (!geneNames.isNullOrEmpty()) {
                val firstGeneName = geneNames.split(" ", ";", "\\")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .firstOrNull()
                if (!firstGeneName.isNullOrEmpty()) {
                    return firstGeneName
                }
            }
        }
        return null
    }

    private suspend fun getGeneNameForProtein(
        proteinId: String,
        linkId: String,
        curtainData: info.proteo.curtain.domain.model.CurtainData
    ): String? {
        var geneName: String? = null

        if (curtainData.fetchUniprot) {
            geneName = getGeneNameFromUniProt(proteinId, curtainData)
        }

        if (geneName.isNullOrEmpty()) {
            val processedData = proteomicsDataService.getProcessedDataForProtein(linkId, proteinId)
            geneName = processedData.firstOrNull()?.geneNames?.takeIf { it.isNotEmpty() }
        }

        return geneName
    }
}
