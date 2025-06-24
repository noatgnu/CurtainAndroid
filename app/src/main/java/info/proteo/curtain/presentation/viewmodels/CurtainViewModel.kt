package info.proteo.curtain

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CurtainViewModel @Inject constructor(
    private val curtainDao: CurtainDao,
    private val curtainRepository: CurtainRepository
) : ViewModel() {

    private val _curtains = MutableStateFlow<List<CurtainEntity>>(emptyList())
    val curtains: StateFlow<List<CurtainEntity>> = _curtains
    
    // Pagination state
    private val _allCurtains = mutableListOf<CurtainEntity>()
    private val _loadedCurtains = mutableListOf<CurtainEntity>()
    private var currentPage = 0
    private var hasMoreData = true
    
    companion object {
        private const val PAGE_SIZE = 10
        private const val INITIAL_PAGE_SIZE = 5
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore
    
    private val _totalCurtains = MutableStateFlow(0)
    val totalCurtains: StateFlow<Int> = _totalCurtains

    private lateinit var settingsService: CurtainDataService


    init {
        settingsService = CurtainDataService()
        loadCurtains()
    }

    fun loadCurtains() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Reset pagination state
            currentPage = 0
            hasMoreData = true
            _allCurtains.clear()
            _loadedCurtains.clear()

            try {
                curtainDao.getAll()
                    .catch { e ->
                        _error.value = e.message
                    }
                    .collectLatest { curtains ->
                        _allCurtains.clear()
                        _allCurtains.addAll(curtains)
                        _totalCurtains.value = curtains.size
                        
                        // Load initial page
                        loadInitialPage()
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    private fun loadInitialPage() {
        val initialCurtains = _allCurtains.take(INITIAL_PAGE_SIZE)
        _loadedCurtains.clear()
        _loadedCurtains.addAll(initialCurtains)
        _curtains.value = _loadedCurtains.toList()
        
        hasMoreData = _allCurtains.size > INITIAL_PAGE_SIZE
        Log.d("CurtainViewModel", "Loaded initial ${initialCurtains.size} curtains, total: ${_allCurtains.size}, hasMore: $hasMoreData")
    }
    
    fun loadMoreCurtains() {
        if (isLoadingMore.value || !hasMoreData) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                currentPage++
                val startIndex = INITIAL_PAGE_SIZE + (currentPage - 1) * PAGE_SIZE
                val endIndex = minOf(startIndex + PAGE_SIZE, _allCurtains.size)
                
                if (startIndex >= _allCurtains.size) {
                    hasMoreData = false
                    _isLoadingMore.value = false
                    return@launch
                }
                
                val newCurtains = _allCurtains.subList(startIndex, endIndex)
                _loadedCurtains.addAll(newCurtains)
                _curtains.value = _loadedCurtains.toList()
                
                hasMoreData = _loadedCurtains.size < _allCurtains.size
                Log.d("CurtainViewModel", "Loaded ${newCurtains.size} more curtains, total loaded: ${_loadedCurtains.size}/${_allCurtains.size}")
                
                _isLoadingMore.value = false
                
            } catch (e: Exception) {
                _error.value = e.message
                _isLoadingMore.value = false
            }
        }
    }
    
    fun hasMoreCurtains(): Boolean = hasMoreData
    
    fun getPaginationInfo(): String {
        return "Showing ${_loadedCurtains.size} of ${_allCurtains.size} curtains"
    }

    /**
     * Downloads the curtain data when a user clicks on a curtain item
     * Shows progress updates during the download
     *
     * @param curtain The curtain entity to download data for
     * @return The path to the downloaded file
     */
    suspend fun downloadCurtainData(curtain: CurtainEntity): String {
        _isDownloading.value = true
        _downloadProgress.value = 0
        _error.value = null

        try {
            // Download the curtain data with progress tracking
            val result = curtainRepository.downloadCurtainData(
                linkId = curtain.linkId,
                hostname = curtain.sourceHostname,
                progressCallback = { progress ->
                    _downloadProgress.value = progress
                }
            )

            _isDownloading.value = false
            return result
        } catch (e: Exception) {
            _isDownloading.value = false
            _error.value = e.message
            throw e
        }
    }

    /**
     * Deletes the existing curtain file and redownloads the data
     * Shows progress updates during the download
     *
     * @param curtain The curtain entity to redownload data for
     * @return The path to the newly downloaded file
     */
    suspend fun redownloadCurtainData(curtain: CurtainEntity): String {
        _isDownloading.value = true
        _downloadProgress.value = 0
        _error.value = null

        try {
            // Delete old file if it exists
            curtain.file?.let { filePath ->
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        _error.value = "Failed to delete old file"
                    }
                }
            }

            // Download the curtain data with progress tracking
            val result = curtainRepository.downloadCurtainData(
                linkId = curtain.linkId,
                hostname = curtain.sourceHostname,
                progressCallback = { progress ->
                    _downloadProgress.value = progress
                },
                forceDownload = true // Force download even if data exists
            )

            _isDownloading.value = false
            return result
        } catch (e: Exception) {
            _isDownloading.value = false
            _error.value = e.message
            throw e
        }
    }

    /**
     * Deserializes a downloaded curtain file
     *
     * @param filePath Path to the downloaded curtain data file
     * @return Boolean indicating success/failure of deserialization
     */
    suspend fun deserializeCurtainData(filePath: String): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                _error.value = "File not found: $filePath"
                return false
            }

            // Read file content as string
            val jsonContent = file.readText()

            // Create settings service and pass the JSON content
            settingsService.restoreSettings(jsonContent)

            return true
        } catch (e: Exception) {
            Log.e("CurtainViewModel", "Error deserializing data", e)
            _error.value = "Error deserializing data: ${e.message}"
            return false
        }
    }
}
