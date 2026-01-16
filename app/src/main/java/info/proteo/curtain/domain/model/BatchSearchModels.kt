package info.proteo.curtain.domain.model

import info.proteo.curtain.domain.service.SearchType

data class BatchSearchRequest(
    val searchInput: String,
    val searchType: SearchType,
    val useRegex: Boolean = false,
    val significantOnly: Boolean = false,
    val advancedFiltering: AdvancedFilterParams? = null,
    val title: String = ""
)

data class AdvancedFilterParams(
    val minP: Double = 0.0,
    val maxP: Double = Double.MAX_VALUE,
    val minFCLeft: Double = 0.0,
    val maxFCLeft: Double = Double.MAX_VALUE,
    val minFCRight: Double = 0.0,
    val maxFCRight: Double = Double.MAX_VALUE,
    val searchLeft: Boolean = false,
    val searchRight: Boolean = false
)

data class BatchSearchResultGroup(
    val searchTerm: String,
    val results: List<SearchResult>,
    val totalCount: Int = results.size
)
