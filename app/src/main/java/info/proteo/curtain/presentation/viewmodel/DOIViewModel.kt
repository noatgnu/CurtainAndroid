package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.*
import info.proteo.curtain.domain.service.DOIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DOILoadingState {
    object Idle : DOILoadingState()
    data class Loading(val status: String) : DOILoadingState()
    data class Collection(
        val metadata: DataCiteMetadata,
        val parsedData: DOIParsedData
    ) : DOILoadingState()
    data class LoadingSession(val status: String) : DOILoadingState()
    data class Completed(val sessionData: Map<String, Any>) : DOILoadingState()
    data class Error(val error: Exception) : DOILoadingState()
}

@HiltViewModel
class DOIViewModel @Inject constructor(
    private val doiService: DOIService
) : ViewModel() {

    private val _state = MutableStateFlow<DOILoadingState>(DOILoadingState.Idle)
    val state: StateFlow<DOILoadingState> = _state.asStateFlow()

    private val _doi = MutableStateFlow("")
    val doi: StateFlow<String> = _doi.asStateFlow()

    fun loadDOI(doiString: String, sessionId: String? = null) {
        _doi.value = doiString
        _state.value = DOILoadingState.Loading("Fetching DOI metadata from DataCite...")

        viewModelScope.launch {
            try {
                val metadata = doiService.fetchMetadata(doiString)

                if (metadata.data.attributes.alternateIdentifiers.isEmpty()) {
                    _state.value = DOILoadingState.Error(DOIError.NoAlternateIdentifiers)
                    return@launch
                }

                _state.value = DOILoadingState.Loading("Parsing session data...")

                val parsedData = doiService.parseAlternateIdentifiers(
                    metadata.data.attributes.alternateIdentifiers
                )

                if (parsedData != null) {
                    if (sessionId != null) {
                        loadSpecificSession(parsedData, sessionId, metadata)
                    } else if (parsedData.collectionMetadata != null &&
                        parsedData.collectionMetadata.allSessionLinks.isNotEmpty()
                    ) {
                        _state.value = DOILoadingState.Collection(metadata, parsedData)
                    } else if (parsedData.mainSessionUrl != null) {
                        loadSessionFromURL(parsedData.mainSessionUrl)
                    } else {
                        tryAlternateIdentifiers(metadata.data.attributes.alternateIdentifiers)
                    }
                } else {
                    tryAlternateIdentifiers(metadata.data.attributes.alternateIdentifiers)
                }
            } catch (e: Exception) {
                android.util.Log.e("DOIViewModel", "Error loading DOI", e)
                _state.value = DOILoadingState.Error(e)
            }
        }
    }

    fun loadSessionFromURL(urlString: String) {
        _state.value = DOILoadingState.LoadingSession("Loading session data...")

        viewModelScope.launch {
            try {
                val sessionData = doiService.fetchSessionData(urlString)
                _state.value = DOILoadingState.Completed(sessionData)
            } catch (e: Exception) {
                android.util.Log.e("DOIViewModel", "Error loading session", e)
                _state.value = DOILoadingState.Error(e)
            }
        }
    }

    fun loadSessionFromCollection(sessionUrl: String) {
        loadSessionFromURL(sessionUrl)
    }

    private fun loadSpecificSession(
        parsedData: DOIParsedData,
        sessionId: String,
        metadata: DataCiteMetadata
    ) {
        viewModelScope.launch {
            try {
                if (parsedData.collectionMetadata != null) {
                    for (session in parsedData.collectionMetadata.allSessionLinks) {
                        if (session.sessionId == sessionId) {
                            loadSessionFromURL(session.sessionUrl)
                            return@launch
                        }
                    }
                }

                _state.value = DOILoadingState.Error(DOIError.SessionDataFetchFailed)
            } catch (e: Exception) {
                android.util.Log.e("DOIViewModel", "Error loading specific session", e)
                _state.value = DOILoadingState.Error(e)
            }
        }
    }

    private fun tryAlternateIdentifiers(identifiers: List<AlternateIdentifier>) {
        viewModelScope.launch {
            for (identifier in identifiers.reversed()) {
                if (identifier.alternateIdentifierType.lowercase() == "url") {
                    try {
                        loadSessionFromURL(identifier.alternateIdentifier)
                        return@launch
                    } catch (e: Exception) {
                        continue
                    }
                }
            }

            _state.value = DOILoadingState.Error(DOIError.NoAlternateIdentifiers)
        }
    }

    fun retry() {
        if (_doi.value.isNotEmpty()) {
            loadDOI(_doi.value)
        }
    }
}
