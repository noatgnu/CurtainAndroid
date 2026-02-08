package info.proteo.curtain.domain.model

import info.proteo.curtain.domain.service.SearchType

data class CrossDatasetSearchConfig(
    val searchTerms: List<String>,
    val searchType: SearchType,
    val datasetLinkIds: List<String>,
    val significantOnly: Boolean = false,
    val useRegex: Boolean = false,
    val advancedFiltering: CrossDatasetAdvancedFilterParams? = null
)

data class CrossDatasetAdvancedFilterParams(
    val minP: Double = 0.0,
    val maxP: Double = 1.0,
    val minFCLeft: Double = 0.0,
    val maxFCLeft: Double = Double.MAX_VALUE,
    val minFCRight: Double = 0.0,
    val maxFCRight: Double = Double.MAX_VALUE,
    val searchLeft: Boolean = false,
    val searchRight: Boolean = false
)

data class DatasetComparisonInfo(
    val linkId: String,
    val datasetDescription: String,
    val comparison: String
)

data class DatasetComparisonResult(
    val datasetInfo: DatasetComparisonInfo,
    val foldChange: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val found: Boolean = true
)

data class ProteinSearchSummary(
    val searchTerm: String,
    val primaryId: String?,
    val geneName: String?,
    val datasetsFoundIn: Int,
    val totalDatasetsSearched: Int,
    val averageFoldChange: Double?,
    val hasSignificantResult: Boolean,
    val accession: String? = null,
    val position: String? = null,
    val peptideSequence: String? = null,
    val score: Double? = null
)

data class ProteinDetailedReport(
    val searchTerm: String,
    val primaryId: String?,
    val geneName: String?,
    val results: List<DatasetComparisonResult>,
    val datasetsFoundIn: Int,
    val totalDatasetsSearched: Int
)

data class CrossDatasetSearchResult(
    val config: CrossDatasetSearchConfig,
    val proteinSummaries: List<ProteinSearchSummary>,
    val searchTimestamp: Long = System.currentTimeMillis()
)

enum class ProteinSortOption {
    NAME_ASC,
    NAME_DESC,
    MATCH_COUNT_DESC,
    AVG_FC_ASC,
    AVG_FC_DESC
}

enum class InputMode {
    SINGLE,
    LIST,
    CURATED
}

enum class DatasetScope {
    ALL,
    COLLECTION,
    CUSTOM
}

data class CrossDatasetFilterOptions(
    val showSignificantOnly: Boolean = false,
    val hideNotFound: Boolean = false,
    val minMatchCount: Int = 1
)

data class DatasetProcessingStatus(
    val linkId: String,
    val datasetName: String,
    val status: ProcessingState,
    val message: String? = null
)

enum class ProcessingState {
    PENDING,
    LOADING,
    BUILDING,
    SEARCHING,
    COMPLETED,
    FAILED
}

data class MatrixCell(
    val foldChange: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val found: Boolean
)

data class MatrixRow(
    val datasetLinkId: String,
    val datasetName: String,
    val comparison: String,
    val conditionLeft: String? = null,
    val conditionRight: String? = null,
    val cells: Map<String, MatrixCell>
)

data class CrossDatasetMatrix(
    val proteinIds: List<String>,
    val rows: List<MatrixRow>,
    val proteinGeneNames: Map<String, String?>
)

data class MatrixFilterOptions(
    val showSignificantOnly: Boolean = false,
    val hideNotFound: Boolean = false,
    val minFoldChange: Double? = null,
    val maxPValue: Double? = null,
    val selectedDatasets: Set<String>? = null
)
