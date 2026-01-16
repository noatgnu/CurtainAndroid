package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.domain.model.AdvancedFilterParams
import info.proteo.curtain.domain.model.BatchSearchRequest
import info.proteo.curtain.domain.model.BatchSearchResultGroup
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.ProteinSearchList
import info.proteo.curtain.domain.model.SearchQuery
import info.proteo.curtain.domain.model.SearchResult
import info.proteo.curtain.domain.repository.DataFilterListRepository
import info.proteo.curtain.domain.repository.ProteinSearchListRepository
import info.proteo.curtain.domain.service.ProteinSearchService
import info.proteo.curtain.domain.service.SearchType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProteinSearchViewModel @Inject constructor(
    private val searchService: ProteinSearchService,
    private val searchListRepository: ProteinSearchListRepository,
    private val dataFilterListRepository: DataFilterListRepository,
    private val proteomicsDataService: info.proteo.curtain.domain.service.ProteomicsDataService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val curtainLinkId: String = checkNotNull(savedStateHandle["linkId"])

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectionTitle = MutableStateFlow("")
    val selectionTitle: StateFlow<String> = _selectionTitle.asStateFlow()

    private val _caseSensitive = MutableStateFlow(false)
    val caseSensitive: StateFlow<Boolean> = _caseSensitive.asStateFlow()

    private val _exactMatch = MutableStateFlow(false)
    val exactMatch: StateFlow<Boolean> = _exactMatch.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _searchInProteinIds = MutableStateFlow(true)
    val searchInProteinIds: StateFlow<Boolean> = _searchInProteinIds.asStateFlow()

    private val _searchInGeneNames = MutableStateFlow(true)
    val searchInGeneNames: StateFlow<Boolean> = _searchInGeneNames.asStateFlow()

    private val _batchMode = MutableStateFlow(false)
    val batchMode: StateFlow<Boolean> = _batchMode.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.GENE_NAMES)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _significantOnly = MutableStateFlow(false)
    val significantOnly: StateFlow<Boolean> = _significantOnly.asStateFlow()

    private val _advancedFiltering = MutableStateFlow<AdvancedFilterParams?>(null)
    val advancedFiltering: StateFlow<AdvancedFilterParams?> = _advancedFiltering.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _batchSearchResults = MutableStateFlow<List<BatchSearchResultGroup>>(emptyList())
    val batchSearchResults: StateFlow<List<BatchSearchResultGroup>> = _batchSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _curtainData = MutableStateFlow<CurtainData?>(null)

    private val _availableSuggestions = MutableStateFlow<List<String>>(emptyList())
    val availableSuggestions: StateFlow<List<String>> = _availableSuggestions.asStateFlow()

    private var allSuggestions: List<String> = emptyList()

    val categories: StateFlow<List<String>> = dataFilterListRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val filterLists: StateFlow<List<DataFilterListEntity>> = _selectedCategory.flatMapLatest { category ->
        if (category != null) {
            dataFilterListRepository.getFiltersByCategory(category)
        } else {
            dataFilterListRepository.getAllFilters()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val searchLists: StateFlow<List<ProteinSearchList>> = searchListRepository
        .getSearchListsByCurtainId(curtainLinkId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSearchListId = MutableStateFlow<String?>(null)
    val selectedSearchListId: StateFlow<String?> = _selectedSearchListId.asStateFlow()

    fun setCurtainData(data: CurtainData) {
        _curtainData.value = data
        viewModelScope.launch {
            updateSuggestionsForSearchType()
        }
    }

    private suspend fun updateSuggestionsForSearchType() {
        val data = _curtainData.value ?: return
        allSuggestions = when (_searchType.value) {
            SearchType.GENE_NAMES -> {
                data.extraData?.data?.allGenes ?: emptyList()
            }
            SearchType.PRIMARY_IDS -> {
                val db = proteomicsDataService.getDatabaseForLinkId(data.linkId)
                val allData = db.proteomicsDataDao().getAllProcessedData()
                allData.map { it.primaryId }.distinct()
            }
        }
        _availableSuggestions.value = emptyList()
    }

    fun filterSuggestions(query: String) {
        if (query.isEmpty()) {
            _availableSuggestions.value = emptyList()
            return
        }

        val queryLower = query.lowercase()
        _availableSuggestions.value = allSuggestions
            .filter { it.lowercase().contains(queryLower) }
            .sortedBy {
                when {
                    it.lowercase() == queryLower -> 0
                    it.lowercase().startsWith(queryLower) -> 1
                    else -> 2
                }
            }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectionTitle(title: String) {
        _selectionTitle.value = title
    }

    fun toggleCaseSensitive() {
        _caseSensitive.value = !_caseSensitive.value
    }

    fun toggleExactMatch() {
        _exactMatch.value = !_exactMatch.value
    }

    fun toggleUseRegex() {
        _useRegex.value = !_useRegex.value
    }

    fun toggleSearchInProteinIds() {
        _searchInProteinIds.value = !_searchInProteinIds.value
    }

    fun toggleSearchInGeneNames() {
        _searchInGeneNames.value = !_searchInGeneNames.value
    }

    fun toggleBatchMode() {
        _batchMode.value = !_batchMode.value
        if (!_batchMode.value) {
            _batchSearchResults.value = emptyList()
        }
    }

    fun setSearchType(type: SearchType) {
        _searchType.value = type
        viewModelScope.launch {
            updateSuggestionsForSearchType()
        }
    }

    fun toggleSignificantOnly() {
        _significantOnly.value = !_significantOnly.value
    }

    fun setAdvancedFiltering(params: AdvancedFilterParams?) {
        _advancedFiltering.value = params
    }

    fun performBatchSearch() {
        val data = _curtainData.value
        if (data == null) {
            _error.value = "No data available"
            return
        }

        if (_searchQuery.value.isEmpty()) {
            _batchSearchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null

            try {
                val resultsMap = searchService.batchSearchProteins(
                    curtainData = data,
                    searchInput = _searchQuery.value,
                    searchType = _searchType.value,
                    useRegex = _useRegex.value,
                    significantOnly = _significantOnly.value,
                    advancedFiltering = _advancedFiltering.value
                )

                android.util.Log.d("ProteinSearchVM", "Search service returned ${resultsMap.size} result groups")

                val batchResults = resultsMap.map { (searchTerm, results) ->
                    BatchSearchResultGroup(
                        searchTerm = searchTerm,
                        results = results,
                        totalCount = results.size
                    )
                }

                android.util.Log.d("ProteinSearchVM", "Setting ${batchResults.size} batch result groups in state")
                _batchSearchResults.value = batchResults
                android.util.Log.d("ProteinSearchVM", "Batch search results set, current value: ${_batchSearchResults.value.size} groups")
            } catch (e: Exception) {
                _error.value = "Batch search failed: ${e.message}"
                _batchSearchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun performSearch() {
        val data = _curtainData.value
        if (data == null) {
            _error.value = "No data available"
            return
        }

        if (_searchQuery.value.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null

            try {
                val resultsMap = searchService.batchSearchProteins(
                    curtainData = data,
                    searchInput = _searchQuery.value,
                    searchType = _searchType.value,
                    useRegex = false,
                    significantOnly = false,
                    advancedFiltering = null
                )

                _searchResults.value = resultsMap.values.flatten()
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun saveSearchList(name: String, description: String = "") {
        viewModelScope.launch {
            try {
                val proteinIds = _searchResults.value.map { it.proteinId }
                searchListRepository.createSearchList(
                    curtainLinkId = curtainLinkId,
                    name = name,
                    proteinIds = proteinIds,
                    description = description
                )
            } catch (e: Exception) {
                _error.value = "Failed to save search list: ${e.message}"
            }
        }
    }

    fun loadSearchList(searchListId: String) {
        viewModelScope.launch {
            try {
                val searchList = searchListRepository.getSearchListById(searchListId)
                if (searchList != null) {
                    _selectedSearchListId.value = searchListId
                    val data = _curtainData.value
                    if (data != null) {
                        val results = searchService.filterBySearchList(data, searchList.proteinIds)
                        _searchResults.value = results
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load search list: ${e.message}"
            }
        }
    }

    fun deleteSearchList(searchListId: String) {
        viewModelScope.launch {
            try {
                searchListRepository.deleteSearchList(searchListId)
                if (_selectedSearchListId.value == searchListId) {
                    _selectedSearchListId.value = null
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete search list: ${e.message}"
            }
        }
    }

    fun deleteFilterList(filterId: Long) {
        viewModelScope.launch {
            try {
                dataFilterListRepository.deleteFilterById(filterId)
            } catch (e: Exception) {
                _error.value = "Failed to delete filter list: ${e.message}"
            }
        }
    }

    fun exportResults(): String {
        return searchService.exportSearchResults(_searchResults.value)
    }

    fun clearError() {
        _error.value = null
    }

    fun clearResults() {
        _searchResults.value = emptyList()
        _batchSearchResults.value = emptyList()
        _selectedSearchListId.value = null
        _selectionTitle.value = ""
    }

    fun getAllPrimaryIdsFromBatchResults(): List<String> {
        val ids = _batchSearchResults.value
            .flatMap { group -> group.results.map { it.proteinId } }
            .distinct()
        android.util.Log.d("ProteinSearchVM", "getAllPrimaryIdsFromBatchResults: returning ${ids.size} distinct protein IDs from ${_batchSearchResults.value.size} groups")
        return ids
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun loadFilterListData(filterList: DataFilterListEntity) {
        try {
            val data = org.json.JSONArray(filterList.data)
            val proteinIds = mutableListOf<String>()
            for (i in 0 until data.length()) {
                proteinIds.add(data.getString(i))
            }
            _searchQuery.value = proteinIds.joinToString("\n")
        } catch (e: Exception) {
            _error.value = "Failed to load filter list: ${e.message}"
        }
    }
}
