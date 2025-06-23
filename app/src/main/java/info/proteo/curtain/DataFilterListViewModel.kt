package info.proteo.curtain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataFilterListViewModel @Inject constructor(
    private val repository: DataFilterListRepository
) : ViewModel() {

    private val _filterLists = MutableStateFlow<List<DataFilterListEntity>>(emptyList())
    val filterLists: StateFlow<List<DataFilterListEntity>> = _filterLists

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Sync progress tracking
    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress

    private val _syncTotal = MutableStateFlow(0)
    val syncTotal: StateFlow<Int> = _syncTotal

    private val _currentSyncCategory = MutableStateFlow<String?>(null)
    val currentSyncCategory: StateFlow<String?> = _currentSyncCategory

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    init {
        loadDataFilterLists()
    }

    /**
     * Load data filter lists from the local database
     */
    fun loadDataFilterLists() {
        viewModelScope.launch {
            try {
                // Load filter lists
                val lists = repository.getAllDataFilterLists()
                _filterLists.value = lists

                // Load categories directly from database
                loadCategories()
            } catch (e: Exception) {
                _error.value = "Failed to load filter lists: ${e.message}"
            }
        }
    }

    /**
     * Load categories directly from the database
     */
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = repository.getAllCategoriesLocal()
                _categories.value = categories
            } catch (e: Exception) {
                _error.value = "Failed to load categories: ${e.message}"
            }
        }
    }

    /**
     * Sync data filter lists from the remote API with detailed progress tracking
     */
    fun syncDataFilterLists() {
        viewModelScope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            _error.value = null
            _syncProgress.value = 0

            try {
                // First get all remote categories
                val categoriesResult = repository.getAllCategories()
                if (categoriesResult.isSuccess) {
                    val remoteCategories = categoriesResult.getOrNull() ?: emptyList()
                    _syncTotal.value = remoteCategories.size

                    var processedCount = 0
                    val allFilterLists = mutableListOf<Pair<String, DataFilterList>>()

                    for (category in remoteCategories) {
                        _currentSyncCategory.value = category
                        _syncProgress.value = processedCount

                        try {
                            val result = repository.fetchDataFilterListsByCategory(category)
                            if (result.isSuccess) {
                                val categoryLists = result.getOrNull() ?: emptyList()
                                allFilterLists.addAll(categoryLists)
                            } else {
                                _error.value = "Failed to sync category '$category': ${result.exceptionOrNull()?.message}"
                            }
                        } catch (e: Exception) {
                            _error.value = "Error processing category '$category': ${e.message}"
                        }

                        processedCount++
                    }

                    // Update progress to show completion
                    _syncProgress.value = remoteCategories.size

                    // Save all fetched data to database
                    val entityFilterLists = allFilterLists.map { (category, filterList) ->
                        // Log each category-filterlist pair for debugging
                        println("DEBUG: Saving filter list ${filterList.name} with category '$category'")
                        repository.mapApiToEntity(filterList, category)
                    }

                    // Log total number of items being saved
                    println("DEBUG: Saving total of ${entityFilterLists.size} filter lists to database")

                    // Group by category for logging
                    val categoryCounts = entityFilterLists.groupBy { it.category }.mapValues { it.value.size }
                    println("DEBUG: Category breakdown: $categoryCounts")

                    repository.saveDataFilterLists(entityFilterLists)

                    // After sync, check and log the categories in the database
                    val localCategories = repository.getAllCategoriesLocal()
                    println("DEBUG: After sync, found ${localCategories.size} categories in database: $localCategories")

                    // Refresh local data after sync
                    loadDataFilterLists()
                } else {
                    _error.value = "Failed to fetch categories: ${categoriesResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Failed to sync filter lists: ${e.message}"
            } finally {
                _isLoading.value = false
                _isSyncing.value = false
                _currentSyncCategory.value = null
            }
        }
    }

    /**
     * Filter lists by category
     */
    fun filterByCategory(category: String?) {
        viewModelScope.launch {
            try {
                if (category == null || category.isEmpty()) {
                    // No filter, show all
                    loadDataFilterLists()
                } else {
                    // Filter by selected category
                    val lists = repository.getAllDataFilterLists()
                    _filterLists.value = lists.filter { it.category == category }
                }
            } catch (e: Exception) {
                _error.value = "Error filtering lists: ${e.message}"
            }
        }
    }
}
