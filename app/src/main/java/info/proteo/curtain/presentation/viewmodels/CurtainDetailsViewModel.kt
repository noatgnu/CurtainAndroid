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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * ViewModel to handle interaction with CurtainDetailsFragment
 * Loads and deserializes curtain data based on the provided curtain ID
 */
@HiltViewModel
class CurtainDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val curtainDao: CurtainDao,

) : ViewModel() {

    // Get the curtain ID from navigation arguments
    private val curtainId: String = savedStateHandle.get<String>("curtainId") ?: ""

    // Expose necessary data as StateFlows
    private val _curtainData = MutableStateFlow<AppData?>(null)
    val curtainData: StateFlow<AppData?> = _curtainData.asStateFlow()

    private val _uniprotData = MutableStateFlow<UniprotData?>(null)
    val uniprotData: StateFlow<UniprotData?> = _uniprotData.asStateFlow()

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _curtainSettings = MutableStateFlow<CurtainSettings?>(null)
    val curtainSettings: StateFlow<CurtainSettings?> = _curtainSettings.asStateFlow()

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
        _isDataLoaded.value = false
        _error.value = null
        _curtainData.value = null
        _uniprotData.value = null
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Get the curtain entity from the database
                val curtain = curtainDao.getById(id)

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
                _uniprotData.value = curtainDataService.uniprotData
                _curtainSettings.value = curtainDataService.curtainSettings
                uniprotService.results = curtainDataService.uniprotData.results.toMutableMap()
                uniprotService.dataMap = curtainDataService.uniprotData.dataMap.toMutableMap()
                uniprotService.accMap = curtainDataService.uniprotData.accMap.toMutableMap()
                uniprotService.db = curtainDataService.uniprotData.db
                uniprotService.geneNameToAcc = curtainDataService.uniprotData.geneNameToAcc
                Log.d("CurtainDetailsViewModel", "Uniprot data loaded: ${_uniprotData.value?.results?.size} entries")
                Log.d("CurtainDetailsViewModel", "Uniprot db loaded: ${uniprotService.db.size} entries")

                _isDataLoaded.value = true
            } catch (e: Exception) {
                _error.value = "Error deserializing file: ${e.message}"
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
                _uniprotData.value = null
                _isDataLoaded.value = false
            } catch (e: Exception) {
                Log.e("CurtainDetailsViewModel", "Error clearing memory: ${e.message}", e)
            }
        }
    }

    fun updateCurtainSettings(settings: CurtainSettings) {
        _curtainSettings.value = settings
    }
}