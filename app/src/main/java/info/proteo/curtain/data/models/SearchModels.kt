package info.proteo.curtain.data.models

import java.io.Serializable

/**
 * Data models for search functionality
 * Based on the frontend Angular implementation patterns
 */

enum class SearchType(val displayName: String) : Serializable {
    PRIMARY_ID("Primary ID"),
    GENE_NAME("Gene Name"),
    ACCESSION_ID("Accession ID")
}

data class SearchResult(
    val proteinId: String,
    val geneName: String? = null,
    val accessionId: String? = null,
    val searchType: SearchType,
    val searchTerm: String,
    val matchType: MatchType = MatchType.PARTIAL
)

enum class MatchType {
    EXACT,
    PARTIAL,
    FUZZY
}

data class SearchList(
    val id: String,
    val name: String,
    val color: String,
    val proteinIds: List<String>,
    val searchTerms: List<String> = emptyList(),
    val searchType: SearchType = SearchType.PRIMARY_ID,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String? = null
) : Serializable

data class SearchFilter(
    val query: String = "",
    val searchType: SearchType = SearchType.PRIMARY_ID,
    val selectedListIds: List<String> = emptyList(),
    val showOnlySelected: Boolean = false,
    val includeEmptyLists: Boolean = true
)

data class BatchSearchRequest(
    val searchTerms: List<String>,
    val searchType: SearchType,
    val listName: String,
    val color: String? = null,
    val overwriteExisting: Boolean = false
)

data class FilterListImportRequest(
    val filterListId: Int,
    val listName: String? = null,
    val color: String? = null,
    val overwriteExisting: Boolean = false
)

data class TypeaheadSuggestion(
    val text: String,
    val searchType: SearchType,
    val matchCount: Int = 1
)

data class SearchStatistics(
    val totalProteins: Int,
    val matchedProteins: Int,
    val unmatchedTerms: List<String>,
    val exactMatches: Int,
    val partialMatches: Int,
    val searchDurationMs: Long
)

data class SearchSession(
    val searchLists: MutableList<SearchList> = mutableListOf(),
    val activeFilters: MutableSet<String> = mutableSetOf(),
    val activeStoredSelections: MutableSet<String> = mutableSetOf(),
    val defaultColors: List<String> = listOf(
        "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
        "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999"
    )
) {
    fun getNextAvailableColor(): String {
        val usedColors = searchLists.map { it.color }.toSet()
        return defaultColors.firstOrNull { it !in usedColors } ?: defaultColors.first()
    }
    
    fun addSearchList(list: SearchList): SearchList {
        val colorToUse = list.color.ifEmpty { getNextAvailableColor() }
        val finalList = list.copy(color = colorToUse)
        searchLists.add(finalList)
        return finalList
    }
    
    fun removeSearchList(listId: String) {
        searchLists.removeAll { it.id == listId }
        activeFilters.remove(listId)
    }
    
    fun getFilteredProteins(storedSelections: Map<String, List<String>> = emptyMap()): List<String> {
        if (activeFilters.isEmpty() && activeStoredSelections.isEmpty()) return emptyList()
        
        val searchListProteins = searchLists
            .filter { it.id in activeFilters }
            .flatMap { it.proteinIds }
        
        val storedSelectionProteins = activeStoredSelections
            .flatMap { selectionName -> storedSelections[selectionName] ?: emptyList() }
        
        return (searchListProteins + storedSelectionProteins).distinct()
    }
}