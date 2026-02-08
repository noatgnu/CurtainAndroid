package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.dao.SavedCrossDatasetSearchDao
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.data.local.entity.SavedCrossDatasetSearchEntity
import info.proteo.curtain.domain.model.CrossDatasetAdvancedFilterParams
import info.proteo.curtain.domain.model.CrossDatasetMatrix
import info.proteo.curtain.domain.model.CrossDatasetSearchConfig
import info.proteo.curtain.domain.model.CrossDatasetSearchResult
import info.proteo.curtain.domain.model.DatasetProcessingStatus
import info.proteo.curtain.domain.model.DatasetScope
import info.proteo.curtain.domain.model.InputMode
import info.proteo.curtain.domain.model.MatrixFilterOptions
import info.proteo.curtain.domain.model.ProteinDetailedReport
import info.proteo.curtain.domain.model.ProteinSearchSummary
import info.proteo.curtain.domain.model.ProteinSortOption
import info.proteo.curtain.data.repository.CurtainCollectionRepository
import info.proteo.curtain.domain.repository.DataFilterListRepository
import info.proteo.curtain.domain.service.CrossDatasetSearchService
import info.proteo.curtain.domain.service.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CrossDatasetSearchViewModel @Inject constructor(
    private val crossDatasetSearchService: CrossDatasetSearchService,
    private val collectionRepository: CurtainCollectionRepository,
    private val dataFilterListRepository: DataFilterListRepository,
    private val savedSearchDao: SavedCrossDatasetSearchDao
) : ViewModel() {

    private val gson = Gson()

    private val _searchInput = MutableStateFlow("")
    val searchInput: StateFlow<String> = _searchInput.asStateFlow()

    private val _inputMode = MutableStateFlow(InputMode.LIST)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.GENE_NAMES)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _significantOnly = MutableStateFlow(false)
    val significantOnly: StateFlow<Boolean> = _significantOnly.asStateFlow()

    private val _advancedFiltering = MutableStateFlow<CrossDatasetAdvancedFilterParams?>(null)
    val advancedFiltering: StateFlow<CrossDatasetAdvancedFilterParams?> = _advancedFiltering.asStateFlow()

    private val _datasetScope = MutableStateFlow(DatasetScope.ALL)
    val datasetScope: StateFlow<DatasetScope> = _datasetScope.asStateFlow()

    private val _selectedDatasetIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedDatasetIds: StateFlow<Set<String>> = _selectedDatasetIds.asStateFlow()

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    private val _expandedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedCollectionIds: StateFlow<Set<Long>> = _expandedCollectionIds.asStateFlow()

    private val _collectionSessions = MutableStateFlow<Map<Long, List<CollectionSessionEntity>>>(emptyMap())
    val collectionSessions: StateFlow<Map<Long, List<CollectionSessionEntity>>> = _collectionSessions.asStateFlow()

    private val _availableDatasets = MutableStateFlow<List<CurtainEntity>>(emptyList())
    val availableDatasets: StateFlow<List<CurtainEntity>> = _availableDatasets.asStateFlow()

    private val _searchResults = MutableStateFlow<CrossDatasetSearchResult?>(null)
    val searchResults: StateFlow<CrossDatasetSearchResult?> = _searchResults.asStateFlow()

    private val _selectedProtein = MutableStateFlow<ProteinSearchSummary?>(null)
    val selectedProtein: StateFlow<ProteinSearchSummary?> = _selectedProtein.asStateFlow()

    private val _proteinReport = MutableStateFlow<ProteinDetailedReport?>(null)
    val proteinReport: StateFlow<ProteinDetailedReport?> = _proteinReport.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingReport = MutableStateFlow(false)
    val isLoadingReport: StateFlow<Boolean> = _isLoadingReport.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sortOption = MutableStateFlow(ProteinSortOption.MATCH_COUNT_DESC)
    val sortOption: StateFlow<ProteinSortOption> = _sortOption.asStateFlow()

    val collections: StateFlow<List<CurtainCollectionEntity>> = collectionRepository.getAllCollections()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<String>> = dataFilterListRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val filterLists: StateFlow<List<DataFilterListEntity>> = dataFilterListRepository.getAllFilters()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedSearches: StateFlow<List<SavedCrossDatasetSearchEntity>> = savedSearchDao.getAllSavedSearches()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSavedSearchId = MutableStateFlow<Long?>(null)
    val currentSavedSearchId: StateFlow<Long?> = _currentSavedSearchId.asStateFlow()

    private val _isSyncingFilters = MutableStateFlow(false)
    val isSyncingFilters: StateFlow<Boolean> = _isSyncingFilters.asStateFlow()

    private val _datasetStatuses = MutableStateFlow<Map<String, DatasetProcessingStatus>>(emptyMap())
    val datasetStatuses: StateFlow<Map<String, DatasetProcessingStatus>> = _datasetStatuses.asStateFlow()

    private val _matrixData = MutableStateFlow<CrossDatasetMatrix?>(null)
    val matrixData: StateFlow<CrossDatasetMatrix?> = _matrixData.asStateFlow()

    private val _isLoadingMatrix = MutableStateFlow(false)
    val isLoadingMatrix: StateFlow<Boolean> = _isLoadingMatrix.asStateFlow()

    private val _selectedDatasetType = MutableStateFlow<String?>(null)
    val selectedDatasetType: StateFlow<String?> = _selectedDatasetType.asStateFlow()

    private val _curtainTypeFilter = MutableStateFlow("all")
    val curtainTypeFilter: StateFlow<String> = _curtainTypeFilter.asStateFlow()

    private val _matrixFilterOptions = MutableStateFlow(MatrixFilterOptions())
    val matrixFilterOptions: StateFlow<MatrixFilterOptions> = _matrixFilterOptions.asStateFlow()

    private val _selectedMatrixProtein = MutableStateFlow<String?>(null)
    val selectedMatrixProtein: StateFlow<String?> = _selectedMatrixProtein.asStateFlow()

    init {
        loadAvailableDatasets()
        observeProcessingStatus()
        observeSelectedDatasetType()
    }

    private fun observeSelectedDatasetType() {
        viewModelScope.launch {
            _selectedDatasetIds.collect { ids ->
                val datasets = _availableDatasets.value
                val types = ids.mapNotNull { id -> datasets.find { it.linkId == id }?.curtainType }.distinct()
                _selectedDatasetType.value = if (types.size == 1) types.first() else null
            }
        }
    }

    private fun observeProcessingStatus() {
        viewModelScope.launch {
            crossDatasetSearchService.processingStatus.collect { status ->
                _datasetStatuses.value = _datasetStatuses.value.toMutableMap().apply {
                    put(status.linkId, status)
                }
            }
        }
    }

    private fun loadAvailableDatasets() {
        viewModelScope.launch {
            try {
                val datasets = crossDatasetSearchService.getAvailableDatasets()
                _availableDatasets.value = datasets
            } catch (e: Exception) {
                _error.value = "Failed to load datasets: ${e.message}"
            }
        }
    }

    fun syncFilterLists() {
        val datasets = _availableDatasets.value
        if (datasets.isEmpty()) {
            _error.value = "No datasets available to sync filter lists from"
            return
        }

        val hostname = datasets.first().sourceHostname

        viewModelScope.launch {
            _isSyncingFilters.value = true
            try {
                val result = dataFilterListRepository.syncFilters(hostname)
                result.fold(
                    onSuccess = { filters ->
                        if (filters.isEmpty()) {
                            _error.value = "No new filter lists found"
                        }
                    },
                    onFailure = { e ->
                        _error.value = "Failed to sync filter lists: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                _error.value = "Failed to sync filter lists: ${e.message}"
            } finally {
                _isSyncingFilters.value = false
            }
        }
    }

    fun updateSearchInput(input: String) {
        _searchInput.value = input
    }

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
    }

    fun setSearchType(type: SearchType) {
        _searchType.value = type
    }

    fun toggleUseRegex() {
        _useRegex.value = !_useRegex.value
    }

    fun toggleSignificantOnly() {
        _significantOnly.value = !_significantOnly.value
    }

    fun setDatasetScope(scope: DatasetScope) {
        _datasetScope.value = scope
    }

    fun toggleDatasetSelection(linkId: String) {
        val current = _selectedDatasetIds.value.toMutableSet()
        if (current.contains(linkId)) {
            current.remove(linkId)
        } else {
            current.add(linkId)
        }
        _selectedDatasetIds.value = current
    }

    fun selectAllDatasets() {
        val filter = _curtainTypeFilter.value
        _selectedDatasetIds.value = _availableDatasets.value
            .filter { filter == "all" || it.curtainType == filter }
            .map { it.linkId }
            .toSet()
    }

    fun deselectAllDatasets() {
        _selectedDatasetIds.value = emptySet()
    }

    fun setCurtainTypeFilter(filter: String) {
        if (_curtainTypeFilter.value != filter) {
            _curtainTypeFilter.value = filter
            _selectedDatasetIds.value = emptySet()
        }
    }

    fun selectCollection(collectionId: Long) {
        _selectedCollectionId.value = collectionId
        viewModelScope.launch {
            val sessions = collectionRepository.getSessionsByCollectionIdSync(collectionId)
            _selectedDatasetIds.value = sessions.map { it.linkId }.toSet()
        }
    }

    fun toggleCollectionExpanded(collectionLocalId: Long) {
        viewModelScope.launch {
            val currentExpanded = _expandedCollectionIds.value
            if (currentExpanded.contains(collectionLocalId)) {
                _expandedCollectionIds.value = currentExpanded - collectionLocalId
            } else {
                _expandedCollectionIds.value = currentExpanded + collectionLocalId
                if (!_collectionSessions.value.containsKey(collectionLocalId)) {
                    loadCollectionSessions(collectionLocalId)
                }
            }
        }
    }

    private suspend fun loadCollectionSessions(collectionLocalId: Long) {
        try {
            val sessions = collectionRepository.getSessionsByCollectionIdSync(collectionLocalId)
            _collectionSessions.value = _collectionSessions.value.toMutableMap().apply {
                put(collectionLocalId, sessions)
            }
        } catch (e: Exception) {
            _error.value = "Failed to load collection sessions: ${e.message}"
        }
    }

    fun toggleSessionSelection(linkId: String) {
        val current = _selectedDatasetIds.value.toMutableSet()
        if (current.contains(linkId)) {
            current.remove(linkId)
        } else {
            current.add(linkId)
        }
        _selectedDatasetIds.value = current
    }

    fun selectAllSessionsInCollection(collectionLocalId: Long) {
        val sessions = _collectionSessions.value[collectionLocalId] ?: return
        val current = _selectedDatasetIds.value.toMutableSet()
        sessions.forEach { current.add(it.linkId) }
        _selectedDatasetIds.value = current
    }

    fun deselectAllSessionsInCollection(collectionLocalId: Long) {
        val sessions = _collectionSessions.value[collectionLocalId] ?: return
        val current = _selectedDatasetIds.value.toMutableSet()
        sessions.forEach { current.remove(it.linkId) }
        _selectedDatasetIds.value = current
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun loadFilterListData(filterList: DataFilterListEntity) {
        _searchInput.value = filterList.data
        _inputMode.value = InputMode.CURATED
    }

    fun setAdvancedFiltering(params: CrossDatasetAdvancedFilterParams?) {
        _advancedFiltering.value = params
    }

    fun performSearch() {
        val input = _searchInput.value.trim()
        android.util.Log.d("CrossDatasetSearchVM", "performSearch called with input (length=${input.length}): '${input.take(100)}'")
        android.util.Log.d("CrossDatasetSearchVM", "Input contains newline (\\n): ${input.contains("\n")}, contains CR (\\r): ${input.contains("\r")}")

        if (input.isEmpty()) {
            _error.value = "Please enter search terms"
            return
        }

        val datasetIds = _selectedDatasetIds.value.toList()
        android.util.Log.d("CrossDatasetSearchVM", "Selected datasets: ${datasetIds.size} - $datasetIds")

        if (datasetIds.isEmpty()) {
            _error.value = "Please select at least one dataset"
            return
        }

        val datasets = _availableDatasets.value
        val types = datasetIds.mapNotNull { id -> datasets.find { it.linkId == id }?.curtainType }.distinct()
        if (types.size > 1) {
            _error.value = "Cannot mix dataset types (${types.joinToString(", ")}). Select only TP or only PTM datasets."
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            _selectedProtein.value = null
            _proteinReport.value = null
            _datasetStatuses.value = emptyMap()

            try {
                val config = CrossDatasetSearchConfig(
                    searchTerms = listOf(input),
                    searchType = _searchType.value,
                    datasetLinkIds = datasetIds,
                    significantOnly = _significantOnly.value,
                    useRegex = _useRegex.value,
                    advancedFiltering = _advancedFiltering.value
                )

                val result = withContext(Dispatchers.IO) {
                    crossDatasetSearchService.searchAcrossDatasets(config)
                }
                _searchResults.value = result.copy(
                    proteinSummaries = sortProteinSummaries(result.proteinSummaries)
                )
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectProtein(protein: ProteinSearchSummary) {
        _selectedProtein.value = protein
        loadProteinReport(protein)
    }

    private fun loadProteinReport(protein: ProteinSearchSummary) {
        viewModelScope.launch {
            _isLoadingReport.value = true
            try {
                val report = withContext(Dispatchers.IO) {
                    crossDatasetSearchService.getProteinDetailedReport(
                        searchTerm = protein.searchTerm,
                        primaryId = protein.primaryId,
                        datasetLinkIds = _selectedDatasetIds.value.toList(),
                        searchType = _searchType.value
                    )
                }
                _proteinReport.value = report
            } catch (e: Exception) {
                _error.value = "Failed to load protein details: ${e.message}"
            } finally {
                _isLoadingReport.value = false
            }
        }
    }

    fun clearSelection() {
        _selectedProtein.value = null
        _proteinReport.value = null
    }

    fun setSortOption(option: ProteinSortOption) {
        _sortOption.value = option
        _searchResults.value?.let { result ->
            _searchResults.value = result.copy(
                proteinSummaries = sortProteinSummaries(result.proteinSummaries)
            )
        }
    }

    private fun sortProteinSummaries(summaries: List<ProteinSearchSummary>): List<ProteinSearchSummary> {
        return when (_sortOption.value) {
            ProteinSortOption.NAME_ASC -> summaries.sortedBy { it.geneName ?: it.primaryId ?: it.searchTerm }
            ProteinSortOption.NAME_DESC -> summaries.sortedByDescending { it.geneName ?: it.primaryId ?: it.searchTerm }
            ProteinSortOption.MATCH_COUNT_DESC -> summaries.sortedByDescending { it.datasetsFoundIn }
            ProteinSortOption.AVG_FC_ASC -> summaries.sortedBy { it.averageFoldChange ?: Double.MAX_VALUE }
            ProteinSortOption.AVG_FC_DESC -> summaries.sortedByDescending { it.averageFoldChange ?: Double.MIN_VALUE }
        }
    }

    fun exportCurrentReport(): String? {
        val report = _proteinReport.value ?: return null
        return crossDatasetSearchService.exportProteinReport(report)
    }

    fun exportAllResults(): String? {
        val result = _searchResults.value ?: return null
        return crossDatasetSearchService.exportAllResults(result)
    }

    fun clearError() {
        _error.value = null
    }

    fun buildMatrix() {
        val result = _searchResults.value ?: return

        viewModelScope.launch {
            _isLoadingMatrix.value = true
            try {
                val matrix = withContext(Dispatchers.IO) {
                    crossDatasetSearchService.buildCrossDatasetMatrix(
                        searchResult = result,
                        filterOptions = _matrixFilterOptions.value
                    )
                }
                _matrixData.value = matrix
            } catch (e: Exception) {
                _error.value = "Failed to build matrix: ${e.message}"
            } finally {
                _isLoadingMatrix.value = false
            }
        }
    }

    fun updateMatrixFilter(options: MatrixFilterOptions) {
        _matrixFilterOptions.value = options
        buildMatrix()
    }

    fun selectMatrixProtein(proteinId: String?) {
        _selectedMatrixProtein.value = proteinId
    }

    fun clearMatrixData() {
        _matrixData.value = null
        _selectedMatrixProtein.value = null
        _matrixFilterOptions.value = MatrixFilterOptions()
    }

    fun clearResults() {
        _searchResults.value = null
        _selectedProtein.value = null
        _proteinReport.value = null
        _currentSavedSearchId.value = null
        clearMatrixData()
    }

    fun saveCurrentSearch(name: String) {
        val result = _searchResults.value ?: return
        val config = result.config

        viewModelScope.launch {
            try {
                val summariesJson = gson.toJson(result.proteinSummaries)
                val entity = SavedCrossDatasetSearchEntity(
                    name = name,
                    searchTerms = config.searchTerms.joinToString("\n"),
                    searchType = config.searchType.name,
                    datasetLinkIds = config.datasetLinkIds.joinToString(","),
                    significantOnly = config.significantOnly,
                    useRegex = config.useRegex,
                    resultSummariesJson = summariesJson,
                    proteinCount = result.proteinSummaries.size,
                    datasetCount = config.datasetLinkIds.size,
                    created = result.searchTimestamp
                )
                val id = savedSearchDao.insertSearch(entity)
                _currentSavedSearchId.value = id
            } catch (e: Exception) {
                _error.value = "Failed to save search: ${e.message}"
            }
        }
    }

    fun loadSavedSearch(id: Long) {
        viewModelScope.launch {
            try {
                val entity = savedSearchDao.getSearchById(id) ?: return@launch

                savedSearchDao.updateLastOpened(id)

                _searchInput.value = entity.searchTerms
                _searchType.value = try {
                    SearchType.valueOf(entity.searchType)
                } catch (e: Exception) {
                    SearchType.GENE_NAMES
                }
                _significantOnly.value = entity.significantOnly
                _useRegex.value = entity.useRegex

                val datasetIds = entity.datasetLinkIds.split(",").filter { it.isNotEmpty() }.toSet()
                _selectedDatasetIds.value = datasetIds

                val summariesType = object : TypeToken<List<ProteinSearchSummary>>() {}.type
                val summaries: List<ProteinSearchSummary> = gson.fromJson(entity.resultSummariesJson, summariesType)

                val config = CrossDatasetSearchConfig(
                    searchTerms = entity.searchTerms.split("\n").filter { it.isNotEmpty() },
                    searchType = _searchType.value,
                    datasetLinkIds = datasetIds.toList(),
                    significantOnly = entity.significantOnly,
                    useRegex = entity.useRegex
                )

                _searchResults.value = CrossDatasetSearchResult(
                    config = config,
                    proteinSummaries = sortProteinSummaries(summaries),
                    searchTimestamp = entity.created
                )

                _currentSavedSearchId.value = id
                _selectedProtein.value = null
                _proteinReport.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load saved search: ${e.message}"
            }
        }
    }

    fun renameSavedSearch(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                savedSearchDao.updateName(id, newName)
            } catch (e: Exception) {
                _error.value = "Failed to rename search: ${e.message}"
            }
        }
    }

    fun deleteSavedSearch(id: Long) {
        viewModelScope.launch {
            try {
                savedSearchDao.deleteSearchById(id)
                if (_currentSavedSearchId.value == id) {
                    _currentSavedSearchId.value = null
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete search: ${e.message}"
            }
        }
    }

    fun updateCurrentSearchResults() {
        val currentId = _currentSavedSearchId.value ?: return
        val result = _searchResults.value ?: return

        viewModelScope.launch {
            try {
                val entity = savedSearchDao.getSearchById(currentId) ?: return@launch
                val summariesJson = gson.toJson(result.proteinSummaries)
                savedSearchDao.updateSearch(
                    entity.copy(
                        resultSummariesJson = summariesJson,
                        proteinCount = result.proteinSummaries.size,
                        lastOpened = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _error.value = "Failed to update search: ${e.message}"
            }
        }
    }
}
