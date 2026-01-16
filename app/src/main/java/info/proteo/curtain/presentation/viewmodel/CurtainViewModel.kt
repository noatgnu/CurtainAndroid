package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.domain.repository.CurtainRepository
import info.proteo.curtain.domain.repository.SiteSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Curtain dataset list screen.
 * Manages dataset loading, searching, and operations.
 *
 * Matches iOS CurtainViewModel functionality.
 */
@HiltViewModel
class CurtainViewModel @Inject constructor(
    private val curtainRepository: CurtainRepository,
    private val siteSettingsRepository: SiteSettingsRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    /**
     * Curtains list combined with search query.
     * Automatically filters results when search query changes.
     */
    val curtains: StateFlow<List<CurtainEntity>> = combine(
        curtainRepository.getAllCurtains(),
        _searchQuery
    ) { curtainsList, query ->
        if (query.isEmpty()) {
            curtainsList
        } else {
            curtainsList.filter {
                it.dataDescription.contains(query, ignoreCase = true) ||
                        it.linkId.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        initializeDefaultSites()
    }

    /**
     * Initialize default backend sites if database is empty.
     */
    private fun initializeDefaultSites() {
        viewModelScope.launch {
            try {
                val activeSiteCount = siteSettingsRepository.getActiveSiteCount()
                if (activeSiteCount == 0) {
                    siteSettingsRepository.insertDefaultSites()
                }
            } catch (e: Exception) {
                _error.value = "Failed to initialize sites: ${e.message}"
            }
        }
    }

    /**
     * Update search query for filtering curtains.
     *
     * @param query Search text
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }


    /**
     * Download curtain data file with progress tracking.
     * Matches iOS download functionality.
     *
     * @param curtain Curtain entity to download
     */
    fun downloadCurtain(curtain: CurtainEntity) {
        viewModelScope.launch {
            try {
                val result = curtainRepository.downloadCurtainData(
                    curtain = curtain,
                    onProgress = { progress, _ ->
                        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                            put(curtain.linkId, progress)
                        }
                    }
                )

                result.onSuccess {
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        remove(curtain.linkId)
                    }
                }.onFailure { e ->
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        remove(curtain.linkId)
                    }
                    _error.value = "Download failed: ${e.message}"
                }
            } catch (e: Exception) {
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    remove(curtain.linkId)
                }
                _error.value = "Download error: ${e.message}"
            }
        }
    }

    /**
     * Toggle pin status of a curtain.
     *
     * @param curtain Curtain entity to toggle
     */
    fun togglePin(curtain: CurtainEntity) {
        viewModelScope.launch {
            try {
                curtainRepository.updatePinStatus(curtain.linkId, !curtain.isPinned)
            } catch (e: Exception) {
                _error.value = "Failed to toggle pin: ${e.message}"
            }
        }
    }

    /**
     * Update curtain description.
     *
     * @param linkId Curtain link ID
     * @param newDescription New description text
     */
    fun updateDescription(linkId: String, newDescription: String) {
        viewModelScope.launch {
            try {
                curtainRepository.updateCurtainDescription(linkId, newDescription)
            } catch (e: Exception) {
                _error.value = "Failed to update description: ${e.message}"
            }
        }
    }

    /**
     * Delete a curtain and its data file.
     *
     * @param curtain Curtain entity to delete
     */
    fun deleteCurtain(curtain: CurtainEntity) {
        viewModelScope.launch {
            try {
                curtainRepository.deleteCurtain(curtain)
            } catch (e: Exception) {
                _error.value = "Failed to delete: ${e.message}"
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Load example curtain dataset for demonstration.
     * Matches iOS loadExampleCurtain() method.
     */
    fun loadExampleCurtain() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(
                    linkId = info.proteo.curtain.util.CurtainConstants.ExampleData.UNIQUE_ID,
                    hostname = info.proteo.curtain.util.CurtainConstants.ExampleData.API_URL,
                    frontendURL = info.proteo.curtain.util.CurtainConstants.ExampleData.FRONTEND_URL
                )

                result.onSuccess {
                    _isLoading.value = false
                }.onFailure { e ->
                    _error.value = "Failed to load example curtain: ${e.message}"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error loading example: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Load curtain from specific link ID and API URL.
     * Matches iOS loadCurtain(linkId:apiUrl:frontendUrl:) method.
     *
     * @param linkId Unique curtain identifier
     * @param apiUrl Backend API URL
     * @param frontendUrl Frontend URL (optional)
     */
    fun loadCurtain(linkId: String, apiUrl: String, frontendUrl: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(
                    linkId = linkId,
                    hostname = apiUrl,
                    frontendURL = frontendUrl
                )

                result.onSuccess {
                    _isLoading.value = false
                }.onFailure { e ->
                    _error.value = "Failed to load curtain: ${e.message}"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error loading curtain: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Save DOI session data to local storage and create curtain entity.
     *
     * @param doi DOI identifier
     * @param sessionData Session data map
     * @param dataDir Directory to save the data file
     * @return Result with created curtain entity
     */
    suspend fun saveDOISession(
        doi: String,
        sessionData: Map<String, Any>,
        dataDir: java.io.File
    ): Result<CurtainEntity> {
        return try {
            val linkId = "doi-${java.util.UUID.randomUUID()}"
            val filePath = java.io.File(dataDir, "$linkId.json").absolutePath

            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(sessionData)
            java.io.File(filePath).writeText(jsonString)

            val curtainEntity = CurtainEntity(
                linkId = linkId,
                created = java.util.Date().time,
                updated = java.util.Date().time,
                file = filePath,
                dataDescription = "DOI: $doi",
                enable = true,
                curtainType = "DOI",
                sourceHostname = "doi.org",
                frontendURL = null,
                isPinned = false
            )

            curtainRepository.insertCurtain(curtainEntity)
            Result.success(curtainEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
