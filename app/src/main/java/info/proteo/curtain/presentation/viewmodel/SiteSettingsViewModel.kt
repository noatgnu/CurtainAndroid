package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import info.proteo.curtain.domain.repository.SiteSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for site settings management screen.
 * Manages backend server configurations.
 *
 * Matches iOS site settings functionality.
 */
@HiltViewModel
class SiteSettingsViewModel @Inject constructor(
    private val siteSettingsRepository: SiteSettingsRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * All site settings as reactive Flow.
     */
    val siteSettings: StateFlow<List<CurtainSiteSettingsEntity>> =
        siteSettingsRepository.getAllSiteSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Active sites count.
     */
    val activeSiteCount: StateFlow<Int> = MutableStateFlow(0).apply {
        viewModelScope.launch {
            value = siteSettingsRepository.getActiveSiteCount()
        }
    }

    /**
     * Add a new site.
     *
     * @param hostname Backend server hostname (e.g., "https://example.com/api/")
     * @param description Site description
     * @param apiKey Optional API key
     * @param requiresAuth Whether site requires authentication
     */
    fun addSite(
        hostname: String,
        description: String,
        apiKey: String? = null,
        requiresAuth: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val normalizedHostname = normalizeHostname(hostname)

                if (siteSettingsRepository.siteExists(normalizedHostname)) {
                    _error.value = "Site already exists"
                    return@launch
                }

                val newSite = CurtainSiteSettingsEntity(
                    hostname = normalizedHostname,
                    lastSync = 0L,
                    active = true,
                    apiKey = apiKey,
                    notes = null,
                    siteDescription = description,
                    requiresAuthentication = requiresAuth,
                    createdAt = System.currentTimeMillis()
                )

                siteSettingsRepository.insertSiteSettings(newSite)
            } catch (e: Exception) {
                _error.value = "Failed to add site: ${e.message}"
            }
        }
    }

    /**
     * Toggle active status of a site.
     *
     * @param site Site settings entity
     */
    fun toggleSiteActive(site: CurtainSiteSettingsEntity) {
        viewModelScope.launch {
            try {
                siteSettingsRepository.updateActiveStatus(
                    hostname = site.hostname,
                    active = !site.active
                )
            } catch (e: Exception) {
                _error.value = "Failed to toggle site: ${e.message}"
            }
        }
    }

    /**
     * Update API key for a site.
     *
     * @param site Site settings entity
     * @param apiKey New API key (null to remove)
     */
    fun updateApiKey(site: CurtainSiteSettingsEntity, apiKey: String?) {
        viewModelScope.launch {
            try {
                siteSettingsRepository.updateApiKey(
                    hostname = site.hostname,
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                _error.value = "Failed to update API key: ${e.message}"
            }
        }
    }

    /**
     * Delete a site.
     *
     * @param site Site settings entity to delete
     */
    fun deleteSite(site: CurtainSiteSettingsEntity) {
        viewModelScope.launch {
            try {
                siteSettingsRepository.deleteSiteSettings(site)
            } catch (e: Exception) {
                _error.value = "Failed to delete site: ${e.message}"
            }
        }
    }

    /**
     * Reset to default sites.
     * Clears all sites and inserts predefined defaults.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Delete all existing sites
                siteSettings.value.forEach { site ->
                    siteSettingsRepository.deleteSiteSettings(site)
                }

                // Insert default sites
                siteSettingsRepository.insertDefaultSites()

                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Failed to reset: ${e.message}"
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
     * Normalize hostname to consistent format.
     * Ensures it starts with https:// and ends with /
     *
     * @param hostname Raw hostname input
     * @return Normalized hostname
     */
    private fun normalizeHostname(hostname: String): String {
        var normalized = hostname.trim()

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }

        return normalized
    }
}
