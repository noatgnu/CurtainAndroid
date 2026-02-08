package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.data.repository.CurtainCollectionRepository
import info.proteo.curtain.domain.preferences.ThemePreference
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
    private val siteSettingsRepository: SiteSettingsRepository,
    private val collectionRepository: CurtainCollectionRepository,
    private val themePreference: ThemePreference
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _expandedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedCollectionIds: StateFlow<Set<Long>> = _expandedCollectionIds.asStateFlow()

    private val _collectionSessions = MutableStateFlow<Map<Long, List<CollectionSessionEntity>>>(emptyMap())
    val collectionSessions: StateFlow<Map<Long, List<CollectionSessionEntity>>> = _collectionSessions.asStateFlow()

    private val _isLoadingCollections = MutableStateFlow(false)
    val isLoadingCollections: StateFlow<Boolean> = _isLoadingCollections.asStateFlow()

    private val _selectedSessionIds = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val selectedSessionIds: StateFlow<Map<Long, Set<String>>> = _selectedSessionIds.asStateFlow()

    private val _selectionModeCollectionId = MutableStateFlow<Long?>(null)
    val selectionModeCollectionId: StateFlow<Long?> = _selectionModeCollectionId.asStateFlow()

    private val _curtainTypeFilter = MutableStateFlow(ThemePreference.FILTER_ALL)
    val curtainTypeFilter: StateFlow<String> = _curtainTypeFilter.asStateFlow()

    val curtains: StateFlow<List<CurtainEntity>> = combine(
        curtainRepository.getAllCurtains(),
        _searchQuery,
        _curtainTypeFilter
    ) { curtainsList, query, typeFilter ->
        var filtered = curtainsList
        if (typeFilter != ThemePreference.FILTER_ALL) {
            filtered = filtered.filter { it.curtainType == typeFilter }
        }
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.dataDescription.contains(query, ignoreCase = true) ||
                        it.linkId.contains(query, ignoreCase = true)
            }
        }
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        initializeDefaultSites()
        viewModelScope.launch {
            themePreference.curtainTypeFilter.collect { filter ->
                _curtainTypeFilter.value = filter
            }
        }
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

    fun updateCurtainTypeFilter(filter: String) {
        _curtainTypeFilter.value = filter
        viewModelScope.launch {
            themePreference.setCurtainTypeFilter(filter)
        }
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

    fun loadExamplePTMCurtain() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(
                    linkId = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.UNIQUE_ID,
                    hostname = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.API_URL,
                    frontendURL = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.FRONTEND_URL
                )

                result.onSuccess {
                    _isLoading.value = false
                }.onFailure { e ->
                    _error.value = "Failed to load example PTM curtain: ${e.message}"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error loading PTM example: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun loadBothExampleCurtains() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val tpResult = curtainRepository.fetchCurtainByLinkIdAndHost(
                    linkId = info.proteo.curtain.util.CurtainConstants.ExampleData.UNIQUE_ID,
                    hostname = info.proteo.curtain.util.CurtainConstants.ExampleData.API_URL,
                    frontendURL = info.proteo.curtain.util.CurtainConstants.ExampleData.FRONTEND_URL
                )
                tpResult.onFailure { e ->
                    _error.value = "Failed to load TP example: ${e.message}"
                }

                val ptmResult = curtainRepository.fetchCurtainByLinkIdAndHost(
                    linkId = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.UNIQUE_ID,
                    hostname = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.API_URL,
                    frontendURL = info.proteo.curtain.util.CurtainConstants.ExamplePTMData.FRONTEND_URL
                )
                ptmResult.onFailure { e ->
                    _error.value = "Failed to load PTM example: ${e.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error loading examples: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load example collection for demonstration.
     */
    fun loadExampleCollection() {
        loadCollection(
            collectionId = info.proteo.curtain.util.CurtainConstants.ExampleCollection.COLLECTION_ID,
            apiUrl = info.proteo.curtain.util.CurtainConstants.ExampleCollection.API_URL,
            frontendUrl = info.proteo.curtain.util.CurtainConstants.ExampleCollection.FRONTEND_URL
        )
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
                sessionName = null,
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

    val collections: StateFlow<List<CurtainCollectionEntity>> = combine(
        collectionRepository.getAllCollections(),
        _searchQuery
    ) { collectionsList, query ->
        if (query.isEmpty()) {
            collectionsList
        } else {
            collectionsList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

    fun loadCollection(collectionId: Int, apiUrl: String, frontendUrl: String? = null) {
        viewModelScope.launch {
            _isLoadingCollections.value = true
            _error.value = null

            try {
                val apiService = curtainRepository.getApiServiceForHost(apiUrl)
                if (apiService != null) {
                    val result = collectionRepository.fetchCollectionFromApi(
                        apiService = apiService,
                        collectionId = collectionId,
                        hostname = apiUrl,
                        frontendURL = frontendUrl,
                        curtainType = "TP"
                    )

                    result.onSuccess { collection ->
                        _expandedCollectionIds.value = _expandedCollectionIds.value + collection.localId
                        loadCollectionSessions(collection.localId)
                        _isLoadingCollections.value = false
                    }.onFailure { e ->
                        _error.value = "Failed to load collection: ${e.message}"
                        _isLoadingCollections.value = false
                    }
                } else {
                    _error.value = "API service not available for $apiUrl"
                    _isLoadingCollections.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error loading collection: ${e.message}"
                _isLoadingCollections.value = false
            }
        }
    }

    fun refreshCollection(collectionLocalId: Long) {
        viewModelScope.launch {
            _isLoadingCollections.value = true
            try {
                val collection = collectionRepository.getCollectionByLocalId(collectionLocalId)
                if (collection != null) {
                    val apiService = curtainRepository.getApiServiceForHost(collection.sourceHostname)
                    if (apiService != null) {
                        val result = collectionRepository.refreshCollection(apiService, collectionLocalId, "TP")
                        result.onSuccess {
                            loadCollectionSessions(collectionLocalId)
                        }.onFailure { e ->
                            _error.value = "Failed to refresh collection: ${e.message}"
                        }
                    } else {
                        _error.value = "API service not available"
                    }
                } else {
                    _error.value = "Collection not found"
                }
            } catch (e: Exception) {
                _error.value = "Failed to refresh collection: ${e.message}"
            } finally {
                _isLoadingCollections.value = false
            }
        }
    }

    fun deleteCollection(collectionLocalId: Long) {
        viewModelScope.launch {
            try {
                collectionRepository.deleteCollection(collectionLocalId)
                _expandedCollectionIds.value = _expandedCollectionIds.value - collectionLocalId
                _collectionSessions.value = _collectionSessions.value.toMutableMap().apply {
                    remove(collectionLocalId)
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete collection: ${e.message}"
            }
        }
    }

    fun loadSessionFromCollection(session: CollectionSessionEntity, collection: CurtainCollectionEntity) {
        loadCurtain(
            linkId = session.linkId,
            apiUrl = collection.sourceHostname,
            frontendUrl = collection.frontendURL
        )
    }

    fun enterSelectionMode(collectionLocalId: Long) {
        _selectionModeCollectionId.value = collectionLocalId
        _selectedSessionIds.value = _selectedSessionIds.value.toMutableMap().apply {
            put(collectionLocalId, emptySet())
        }
    }

    fun exitSelectionMode() {
        val collectionId = _selectionModeCollectionId.value
        _selectionModeCollectionId.value = null
        if (collectionId != null) {
            _selectedSessionIds.value = _selectedSessionIds.value.toMutableMap().apply {
                remove(collectionId)
            }
        }
    }

    fun toggleSessionSelection(collectionLocalId: Long, linkId: String) {
        _selectedSessionIds.value = _selectedSessionIds.value.toMutableMap().apply {
            val currentSet = get(collectionLocalId) ?: emptySet()
            put(collectionLocalId, if (currentSet.contains(linkId)) {
                currentSet - linkId
            } else {
                currentSet + linkId
            })
        }
    }

    fun selectAllSessions(collectionLocalId: Long) {
        val sessions = _collectionSessions.value[collectionLocalId] ?: return
        _selectedSessionIds.value = _selectedSessionIds.value.toMutableMap().apply {
            put(collectionLocalId, sessions.map { it.linkId }.toSet())
        }
    }

    fun deselectAllSessions(collectionLocalId: Long) {
        _selectedSessionIds.value = _selectedSessionIds.value.toMutableMap().apply {
            put(collectionLocalId, emptySet())
        }
    }

    fun downloadSelectedSessions(collectionLocalId: Long) {
        val collection = viewModelScope.launch {
            val collection = collectionRepository.getCollectionByLocalId(collectionLocalId) ?: return@launch
            val selectedIds = _selectedSessionIds.value[collectionLocalId] ?: return@launch
            val sessions = _collectionSessions.value[collectionLocalId] ?: return@launch

            val sessionsToDownload = sessions.filter { selectedIds.contains(it.linkId) }

            sessionsToDownload.forEach { session ->
                loadCurtain(
                    linkId = session.linkId,
                    apiUrl = collection.sourceHostname,
                    frontendUrl = collection.frontendURL
                )
            }

            exitSelectionMode()
        }
    }

    fun getSelectedCount(collectionLocalId: Long): Int {
        return _selectedSessionIds.value[collectionLocalId]?.size ?: 0
    }
}
