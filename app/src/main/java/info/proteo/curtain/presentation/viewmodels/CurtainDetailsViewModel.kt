package info.proteo.curtain.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.AppData
import info.proteo.curtain.CurtainDao
import info.proteo.curtain.CurtainDataService
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.UniprotData
import info.proteo.curtain.UniprotService
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.services.SearchService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Data class for annotation commands (matching Angular frontend pattern)
 */
data class AnnotationCommand(
    val primaryIds: List<String>,
    val remove: Boolean = false
)

/**
 * ViewModel to handle interaction with CurtainDetailsFragment
 * Loads and deserializes curtain data based on the provided curtain ID
 */
@HiltViewModel
class CurtainDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val curtainDao: CurtainDao,
    val searchService: SearchService,
) : ViewModel() {

    // Get the curtain ID from navigation arguments
    private val curtainId: String = savedStateHandle.get<String>("curtainId") ?: ""

    // Expose necessary data as StateFlows
    private val _curtainEntity = MutableStateFlow<CurtainEntity?>(null)
    val curtainEntity: StateFlow<CurtainEntity?> = _curtainEntity.asStateFlow()
    
    private val _curtainData = MutableStateFlow<AppData?>(null)
    val curtainData: StateFlow<AppData?> = _curtainData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Event to trigger volcano plot refresh directly
    private val _volcanoPlotRefreshTrigger = MutableStateFlow(0)
    val volcanoPlotRefreshTrigger: StateFlow<Int> = _volcanoPlotRefreshTrigger.asStateFlow()

    private val _curtainSettings = MutableStateFlow<CurtainSettings?>(null)
    val curtainSettings: StateFlow<CurtainSettings?> = _curtainSettings.asStateFlow()

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    // Annotation service for reactive communication (matching Angular frontend pattern)
    private val _annotationService = MutableSharedFlow<AnnotationCommand>(extraBufferCapacity = 1)
    val annotationService: SharedFlow<AnnotationCommand> = _annotationService.asSharedFlow()

    var curtainDataService: CurtainDataService
    var uniprotService: UniprotService


    init {
        curtainDataService = CurtainDataService()
        uniprotService = UniprotService()
    }

    /**
     * Loads the curtain data based on the provided ID
     */
    fun loadCurtainData(id: String) {
        // Reset states when loading new data
        _curtainData.value = null
        _error.value = null
        _isLoading.value = true
        _isDataLoaded.value = false

        viewModelScope.launch {
            try {
                // Get the curtain entity from the database
                val curtain = curtainDao.getById(id)
                _curtainEntity.value = curtain

                if (curtain != null && curtain.file != null) {
                    // Deserialize the curtain file
                    deserializeFile(curtain.file)
                } else {
                    _error.value = "Curtain file not found"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error loading curtain data: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Deserializes the curtain file
     */
    private fun deserializeFile(filePath: String) {
        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _error.value = "File not found: $filePath"
                    _isLoading.value = false
                    return@launch
                }

                // Process the file with InputStreamReader to handle large files efficiently
                file.inputStream().use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        // Pass the reader to the service for deserialization
                        curtainDataService.restoreSettingsFromReader(reader)
                    }
                }

                // Get the deserialized data
                _curtainData.value = curtainDataService.curtainData
                uniprotService.results = curtainDataService.uniprotData.results.toMutableMap()
                uniprotService.dataMap = curtainDataService.uniprotData.dataMap.toMutableMap()
                uniprotService.accMap = curtainDataService.uniprotData.accMap.toMutableMap()
                uniprotService.db = curtainDataService.uniprotData.db
                uniprotService.geneNameToAcc = curtainDataService.uniprotData.geneNameToAcc

                // Initialize SearchService with the services from this ViewModel
                searchService.initializeWithViewModelServices(uniprotService, curtainDataService)

                _curtainSettings.value = curtainDataService.curtainSettings
                _isLoading.value = false
                _isDataLoaded.value = true
            } catch (e: Exception) {
                _error.value = "Error deserializing file: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            try {
                // Clear data in the service
                curtainDataService.clearMemory()

                // Also clear local references
                _curtainData.value = null
            } catch (e: Exception) {
                Log.e("CurtainDetailsViewModel", "Error clearing memory: ${e.message}", e)
            }
        }
    }

    fun updateCurtainSettings(settings: CurtainSettings) {
        _curtainSettings.value = settings
        // Also update the service's curtainSettings so settings variants can capture it
        curtainDataService.curtainSettings = settings
    }
    
    /**
     * Refreshes the UI after settings have been updated externally (e.g., from settings variant loading)
     * This triggers re-emission of StateFlows to notify observing UI components
     */
    fun refreshFromSettingsUpdate() {
        viewModelScope.launch {
            try {
                // Re-emit current data to trigger UI updates
                val currentData = _curtainData.value
                if (currentData != null) {
                    // Trigger re-emission by setting to null then back to current value
                    _curtainData.value = null
                    _curtainData.value = currentData
                }
                
                // Update settings from the service
                _curtainSettings.value = curtainDataService.curtainSettings
                
                Log.d("CurtainDetailsViewModel", "Settings refresh triggered")
            } catch (e: Exception) {
                Log.e("CurtainDetailsViewModel", "Error refreshing after settings update", e)
            }
        }
    }
    
    /**
     * Refreshes the curtain data after search operations have been performed
     * This forces StateFlow re-emission to notify observers of search selection changes
     */
    fun refreshFromSearchUpdate() {
        viewModelScope.launch {
            try {
                val currentData = _curtainData.value
                if (currentData != null) {
                    // Update search selections in the AppData
                    searchService.saveSearchListsToCurtainData(currentData)
                    
                    // Force StateFlow re-emission to trigger observers
                    _curtainData.value = null
                    _curtainData.value = currentData
                    
                    // Also trigger the direct volcano plot refresh
                    _volcanoPlotRefreshTrigger.value = _volcanoPlotRefreshTrigger.value + 1
                }
            } catch (e: Exception) {
                Log.e("CurtainDetailsViewModel", "Error refreshing after search update", e)
            }
        }
    }
    
    /**
     * Send annotation command to subscribers (matching Angular frontend pattern)
     */
    fun sendAnnotationCommand(primaryIds: List<String>, remove: Boolean = false) {
        android.util.Log.d("CurtainDetailsViewModel", "sendAnnotationCommand: primaryIds=$primaryIds, remove=$remove")
        viewModelScope.launch {
            _annotationService.emit(AnnotationCommand(primaryIds, remove))
            android.util.Log.d("CurtainDetailsViewModel", "Annotation command emitted successfully")
        }
    }
    
    /**
     * Add annotations for specific primary IDs
     */
    fun addAnnotations(primaryIds: List<String>) {
        sendAnnotationCommand(primaryIds, remove = false)
    }
    
    /**
     * Remove annotations for specific primary IDs
     */
    fun removeAnnotations(primaryIds: List<String>) {
        sendAnnotationCommand(primaryIds, remove = true)
    }
}