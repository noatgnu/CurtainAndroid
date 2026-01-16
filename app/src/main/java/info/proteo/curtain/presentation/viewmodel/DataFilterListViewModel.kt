package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.domain.repository.DataFilterListRepository
import info.proteo.curtain.domain.repository.SiteSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataFilterListViewModel @Inject constructor(
    private val dataFilterListRepository: DataFilterListRepository,
    private val siteSettingsRepository: SiteSettingsRepository
) : ViewModel() {

    private val _filters = MutableStateFlow<List<DataFilterListEntity>>(emptyList())
    val filters: StateFlow<List<DataFilterListEntity>> = _filters.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedFilters = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFilters: StateFlow<Set<Long>> = _selectedFilters.asStateFlow()

    init {
        loadFilters()
    }

    fun loadFilters() {
        viewModelScope.launch {
            _isLoading.value = true
            dataFilterListRepository.getAllFilters().collect { filterList ->
                _filters.value = filterList
                _isLoading.value = false
            }
        }
    }

    fun syncFiltersFromBackend() {
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null
            _syncMessage.value = null

            try {
                val activeSites = siteSettingsRepository.getActiveSiteSettings().first()
                if (activeSites.isEmpty()) {
                    _error.value = "No active backend sites configured"
                    _isSyncing.value = false
                    return@launch
                }

                var totalNewFilters = 0
                val syncedSites = mutableListOf<String>()

                for (site in activeSites) {
                    val countBefore = dataFilterListRepository.getFilterCount()

                    dataFilterListRepository.syncFilters(site.hostname)
                        .onSuccess {
                            val countAfter = dataFilterListRepository.getFilterCount()
                            val newFilters = countAfter - countBefore
                            totalNewFilters += newFilters
                            syncedSites.add(site.hostname)
                        }
                        .onFailure { exception ->
                            _error.value = "Failed to sync from ${site.hostname}: ${exception.message}"
                        }
                }

                if (syncedSites.isNotEmpty()) {
                    _syncMessage.value = if (totalNewFilters > 0) {
                        "Downloaded $totalNewFilters new filter lists from ${syncedSites.size} server(s)"
                    } else {
                        "All filter lists are up to date (synced with ${syncedSites.size} server(s))"
                    }
                }

                _isSyncing.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to sync filters"
                _isSyncing.value = false
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredList(): List<DataFilterListEntity> {
        var filtered = if (_selectedCategory.value == "All") {
            _filters.value
        } else {
            _filters.value.filter { it.category == _selectedCategory.value }
        }

        if (_searchQuery.value.isNotEmpty()) {
            val queryLower = _searchQuery.value.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(queryLower) ||
                it.category.lowercase().contains(queryLower) ||
                it.data.lowercase().contains(queryLower)
            }
        }

        return filtered
    }

    fun getCategories(): List<String> {
        val categories = _filters.value.map { it.category }.distinct().sorted()
        return listOf("All") + categories
    }

    fun deleteFilter(filter: DataFilterListEntity) {
        viewModelScope.launch {
            try {
                dataFilterListRepository.deleteFilter(filter)
            } catch (e: Exception) {
                _error.value = "Failed to delete filter: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) {
            _selectedFilters.value = emptySet()
        }
    }

    fun toggleFilterSelection(filterId: Long) {
        val currentSelection = _selectedFilters.value.toMutableSet()
        if (currentSelection.contains(filterId)) {
            currentSelection.remove(filterId)
        } else {
            currentSelection.add(filterId)
        }
        _selectedFilters.value = currentSelection
    }

    fun selectAllDeletableFilters() {
        val deletableFilters = _filters.value
            .filter { !it.isDefault }
            .map { it.id }
            .toSet()
        _selectedFilters.value = deletableFilters
    }

    fun clearSelection() {
        _selectedFilters.value = emptySet()
    }

    fun deleteSelectedFilters() {
        viewModelScope.launch {
            try {
                val filtersToDelete = _filters.value.filter { it.id in _selectedFilters.value }
                filtersToDelete.forEach { filter ->
                    dataFilterListRepository.deleteFilter(filter)
                }
                _selectedFilters.value = emptySet()
                _selectionMode.value = false
            } catch (e: Exception) {
                _error.value = "Failed to delete selected filters: ${e.message}"
            }
        }
    }

    fun deleteAllNonDefaultFilters() {
        viewModelScope.launch {
            try {
                val nonDefaultFilters = _filters.value.filter { !it.isDefault }
                nonDefaultFilters.forEach { filter ->
                    dataFilterListRepository.deleteFilter(filter)
                }
                _selectedFilters.value = emptySet()
                _selectionMode.value = false
            } catch (e: Exception) {
                _error.value = "Failed to delete all filters: ${e.message}"
            }
        }
    }
}
