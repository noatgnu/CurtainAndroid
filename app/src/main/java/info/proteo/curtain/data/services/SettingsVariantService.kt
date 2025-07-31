package info.proteo.curtain.data.services

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.CurtainDataService
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.VolcanoAxis
import info.proteo.curtain.data.models.*
import info.proteo.curtain.utils.PlotlyChartGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing local settings variants.
 * Allows users to save, load, and manage different configurations
 * that can be applied to curtain data analysis sessions.
 */
@Singleton
class SettingsVariantService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val searchService: SearchService,
    private val conditionColorService: ConditionColorService
) {
    
    companion object {
        private const val SETTINGS_DIR = "settings_variants"
        private const val METADATA_FILE = "variants_metadata.json"
        private const val DEFAULT_VARIANT_ID = "default"
        private const val FILE_EXTENSION = ".json"
    }

    private val settingsDir: File by lazy {
        File(context.filesDir, SETTINGS_DIR).apply { mkdirs() }
    }

    private val metadataFile: File by lazy {
        File(settingsDir, METADATA_FILE)
    }

    // JSON Adapters
    private val settingsVariantAdapter: JsonAdapter<SettingsVariant> by lazy {
        moshi.adapter(SettingsVariant::class.java)
    }

    private val metadataListAdapter: JsonAdapter<List<SettingsVariantMetadata>> by lazy {
        val type = Types.newParameterizedType(List::class.java, SettingsVariantMetadata::class.java)
        moshi.adapter(type)
    }

    // StateFlow for reactive updates
    private val _availableVariants = MutableStateFlow<List<SettingsVariantMetadata>>(emptyList())
    val availableVariants: StateFlow<List<SettingsVariantMetadata>> = _availableVariants.asStateFlow()

    private val _currentVariant = MutableStateFlow<SettingsVariant?>(null)
    val currentVariant: StateFlow<SettingsVariant?> = _currentVariant.asStateFlow()

    private val _variantsFlow = MutableStateFlow<List<SettingsVariant>>(emptyList())
    val allVariants: StateFlow<List<SettingsVariant>> = _variantsFlow.asStateFlow()

    init {
        // Load available variants on initialization
        loadAvailableVariants()
    }

    /**
     * Create a new settings variant from current application state
     */
    suspend fun createVariantFromCurrentState(
        name: String,
        curtainDataService: CurtainDataService,
        description: String? = null,
        tags: List<String> = emptyList(),
        includeCategories: List<SettingsCategory> = SettingsCategory.values().toList()
    ): Result<SettingsVariant> = withContext(Dispatchers.IO) {
        try {
            val id = generateUniqueId()
            val variant = SettingsVariant(
                id = id,
                name = name,
                description = description,
                tags = tags,
                visualSettings = if (SettingsCategory.VISUAL in includeCategories) captureVisualSettings(curtainDataService) else VisualSettings(),
                analysisSettings = if (SettingsCategory.ANALYSIS in includeCategories) captureAnalysisSettings(curtainDataService) else AnalysisSettings(),
                searchSettings = if (SettingsCategory.SEARCH in includeCategories) captureSearchSettings() else SearchSettings(),
                conditionSettings = if (SettingsCategory.CONDITIONS in includeCategories) captureConditionSettings() else ConditionSettings(),
                plotSettings = if (SettingsCategory.PLOTS in includeCategories) capturePlotSettings(curtainDataService) else PlotSettings(),
                appPreferences = if (SettingsCategory.PREFERENCES in includeCategories) captureAppPreferences() else AppPreferences()
            )

            val result = saveVariant(variant)
            if (result.isSuccess) {
                _currentVariant.value = variant
                refreshAvailableVariants()
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save a settings variant to local storage
     */
    suspend fun saveVariant(variant: SettingsVariant): Result<SettingsVariant> = withContext(Dispatchers.IO) {
        try {
            val variantFile = File(settingsDir, "${variant.id}$FILE_EXTENSION")
            val json = settingsVariantAdapter.toJson(variant)
            
            variantFile.writeText(json, StandardCharsets.UTF_8)
            
            // Update metadata
            val metadata = SettingsVariantMetadata(
                id = variant.id,
                name = variant.name,
                description = variant.description,
                createdAt = variant.createdAt,
                lastModified = variant.lastModified,
                isDefault = variant.isDefault,
                tags = variant.tags,
                size = variantFile.length(),
                version = variant.version,
                checksum = calculateFileChecksum(variantFile)
            )
            
            updateMetadata(metadata)
            Result.success(variant)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a settings variant by ID
     */
    suspend fun loadVariant(variantId: String): Result<SettingsVariant> = withContext(Dispatchers.IO) {
        try {
            val variantFile = File(settingsDir, "$variantId$FILE_EXTENSION")
            if (!variantFile.exists()) {
                return@withContext Result.failure(Exception("Settings variant not found: $variantId"))
            }

            val json = variantFile.readText(StandardCharsets.UTF_8)
            
            // Debug logging for JSON content
            android.util.Log.d("SettingsVariantService", "Loading variant $variantId, JSON length: ${json.length}")
            if (json.contains("textAnnotation")) {
                android.util.Log.d("SettingsVariantService", "JSON contains 'textAnnotation' field")
                val textAnnotationIndex = json.indexOf("textAnnotation")
                val snippet = json.substring(maxOf(0, textAnnotationIndex - 50), minOf(json.length, textAnnotationIndex + 200))
                android.util.Log.d("SettingsVariantService", "textAnnotation JSON snippet: $snippet")
            } else {
                android.util.Log.w("SettingsVariantService", "JSON does NOT contain 'textAnnotation' field")
            }
            
            val variant = settingsVariantAdapter.fromJson(json)
                ?: return@withContext Result.failure(Exception("Failed to parse settings variant"))

            // Debug logging for deserialized variant
            android.util.Log.d("SettingsVariantService", "Loaded variant '${variant.name}' with textAnnotation: size=${variant.plotSettings.textAnnotation.size}")
            android.util.Log.d("SettingsVariantService", "textAnnotation keys: ${variant.plotSettings.textAnnotation.keys}")
            android.util.Log.d("SettingsVariantService", "textAnnotation full map: ${variant.plotSettings.textAnnotation}")

            _currentVariant.value = variant
            Result.success(variant)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Apply a settings variant to current application state
     */
    suspend fun applyVariant(
        variant: SettingsVariant,
        curtainDataService: CurtainDataService,
        applyCategories: List<SettingsCategory> = SettingsCategory.values().toList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (SettingsCategory.VISUAL in applyCategories) {
                applyVisualSettings(variant.visualSettings, curtainDataService)
            }
            if (SettingsCategory.ANALYSIS in applyCategories) {
                applyAnalysisSettings(variant.analysisSettings, curtainDataService)
            }
            if (SettingsCategory.SEARCH in applyCategories) {
                applySearchSettings(variant.searchSettings)
            }
            if (SettingsCategory.CONDITIONS in applyCategories) {
                applyConditionSettings(variant.conditionSettings, curtainDataService)
            }
            if (SettingsCategory.PLOTS in applyCategories) {
                applyPlotSettings(variant.plotSettings, curtainDataService)
            }
            if (SettingsCategory.PREFERENCES in applyCategories) {
                applyAppPreferences(variant.appPreferences)
            }

            _currentVariant.value = variant
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a settings variant
     */
    suspend fun deleteVariant(variantId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val variantFile = File(settingsDir, "$variantId$FILE_EXTENSION")
            if (variantFile.exists()) {
                variantFile.delete()
            }
            
            removeFromMetadata(variantId)
            refreshAvailableVariants()
            
            // If this was the current variant, clear it
            if (_currentVariant.value?.id == variantId) {
                _currentVariant.value = null
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export settings variant to external storage
     */
    suspend fun exportVariant(
        variantId: String,
        outputFile: File,
        includeMetadata: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val variant = loadVariant(variantId).getOrThrow()
            
            val exportData = if (includeMetadata) {
                val metadata = _availableVariants.value.find { it.id == variantId }
                mapOf(
                    "variant" to variant,
                    "metadata" to metadata,
                    "exportedAt" to System.currentTimeMillis(),
                    "exportedBy" to "Curtain Android ${android.os.Build.MODEL}"
                )
            } else {
                variant
            }

            val json = moshi.adapter(Any::class.java).toJson(exportData)
            outputFile.writeText(json, StandardCharsets.UTF_8)
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import settings variant from external file
     */
    suspend fun importVariant(
        inputFile: File,
        newName: String? = null,
        overwriteExisting: Boolean = false
    ): Result<SettingsImportResult> = withContext(Dispatchers.IO) {
        try {
            val json = inputFile.readText(StandardCharsets.UTF_8)
            
            // Try to parse as variant with metadata first
            val variant = try {
                val exportData = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any>
                if (exportData?.containsKey("variant") == true) {
                    val variantJson = moshi.adapter(Any::class.java).toJson(exportData["variant"])
                    settingsVariantAdapter.fromJson(variantJson)
                } else {
                    settingsVariantAdapter.fromJson(json)
                }
            } catch (e: Exception) {
                null
            }

            if (variant == null) {
                return@withContext Result.success(
                    SettingsImportResult(
                        success = false,
                        errors = listOf("Invalid settings variant format")
                    )
                )
            }

            // Generate new ID if variant already exists and not overwriting
            val finalVariant = if (!overwriteExisting && variantExists(variant.id)) {
                variant.copy(
                    id = generateUniqueId(),
                    name = newName ?: "${variant.name} (Imported)",
                    lastModified = System.currentTimeMillis()
                )
            } else {
                newName?.let { variant.copy(name = it) } ?: variant
            }

            val saveResult = saveVariant(finalVariant)
            
            Result.success(
                SettingsImportResult(
                    success = saveResult.isSuccess,
                    settingsVariant = if (saveResult.isSuccess) finalVariant else null,
                    errors = if (saveResult.isFailure) listOf(saveResult.exceptionOrNull()?.message ?: "Unknown error") else emptyList(),
                    imported = SettingsCategory.values().toList()
                )
            )
        } catch (e: Exception) {
            Result.success(
                SettingsImportResult(
                    success = false,
                    errors = listOf("Import failed: ${e.message}")
                )
            )
        }
    }

    /**
     * Check if a variant exists
     */
    suspend fun variantExists(variantId: String): Boolean = withContext(Dispatchers.IO) {
        File(settingsDir, "$variantId$FILE_EXTENSION").exists()
    }

    /**
     * Create default settings variant
     */
    suspend fun createDefaultVariant(curtainDataService: CurtainDataService): Result<SettingsVariant> {
        return createVariantFromCurrentState(
            name = "Default Settings",
            curtainDataService = curtainDataService,
            description = "Default application settings",
            tags = listOf("default", "system")
        )
    }

    // Core CRUD operations

    /**
     * Save a settings variant to file
     */
    private suspend fun saveVariantToFile(variant: SettingsVariant) = withContext(Dispatchers.IO) {
        val file = File(settingsDir, "${variant.id}$FILE_EXTENSION")
        val json = settingsVariantAdapter.toJson(variant)
        file.writeText(json, StandardCharsets.UTF_8)
    }

    /**
     * Get a variant by ID
     */
    suspend fun getVariant(variantId: String): SettingsVariant? = withContext(Dispatchers.IO) {
        try {
            val file = File(settingsDir, "$variantId$FILE_EXTENSION")
            if (file.exists()) {
                val json = file.readText(StandardCharsets.UTF_8)
                settingsVariantAdapter.fromJson(json)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all variants synchronously
     */
    private suspend fun getAllVariantsSync(): List<SettingsVariant> = withContext(Dispatchers.IO) {
        try {
            settingsDir.listFiles { _, name -> name.endsWith(FILE_EXTENSION) }
                ?.mapNotNull { file ->
                    try {
                        val json = file.readText(StandardCharsets.UTF_8)
                        settingsVariantAdapter.fromJson(json)
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.lastModified }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get all variants for a specific curtain
     */
    suspend fun getAllVariantsForCurtain(curtainId: String): List<SettingsVariant> = withContext(Dispatchers.IO) {
        // For now, return all variants since SettingsVariant doesn't have curtainId field
        // TODO: Add curtainId field to SettingsVariant or use a different approach
        getAllVariantsSync()
    }

    /**
     * Update the last used timestamp for a variant
     */
    suspend fun updateLastUsed(variantId: String) = withContext(Dispatchers.IO) {
        try {
            val variant = getVariant(variantId)
            if (variant != null) {
                val updatedVariant = variant.copy(
                    lastUsed = System.currentTimeMillis(),
                    usageCount = variant.usageCount + 1,
                    lastModified = System.currentTimeMillis()
                )
                saveVariant(updatedVariant)
            }
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    /**
     * Generate a unique ID for a variant
     */
    private fun generateId(): String {
        return "variant_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}"
    }

    // Private helper methods for capturing current state

    private fun captureVisualSettings(curtainDataService: CurtainDataService): VisualSettings {
        val currentCurtainSettings = curtainDataService.curtainSettings
        
        // Capture comprehensive color state from both services
        val conditionColors = conditionColorService.conditionColors.value
        val settingsColorMap = currentCurtainSettings.colorMap  // This is Map<String, String>
        
        // Merge both color sources for complete capture
        val combinedColorMap = mutableMapOf<String, String>()
        
        // Merge settingsColorMap (already Map<String, String>)
        combinedColorMap.putAll(settingsColorMap)
        
        // Merge conditionColors (Map<String, String>)
        combinedColorMap.putAll(conditionColors)
        
        return VisualSettings(
            colorPalette = conditionColorService.currentPalette.value,
            customColorMap = combinedColorMap,
            backgroundColorGrey = currentCurtainSettings.backGroundColorGrey,
            defaultColorList = currentCurtainSettings.defaultColorList
        )
    }

    private fun captureAnalysisSettings(curtainDataService: CurtainDataService): AnalysisSettings {
        val currentCurtainSettings = curtainDataService.curtainSettings
        return AnalysisSettings(
            pCutoff = currentCurtainSettings.pCutoff,
            log2FCCutoff = currentCurtainSettings.log2FCCutoff,
            sampleVisible = currentCurtainSettings.sampleVisible,
            legendStatus = currentCurtainSettings.legendStatus.mapValues { (_, value) -> 
                when (value) {
                    is Boolean -> value
                    else -> true
                }
            }
        )
    }

    private fun captureSearchSettings(): SearchSettings {
        return SearchSettings(
            searchLists = searchService.getSearchLists().map { searchList ->
                SavedSearchList(
                    id = searchList.id,
                    name = searchList.name,
                    color = searchList.color,
                    proteinIds = searchList.proteinIds,
                    searchTerms = searchList.searchTerms,
                    searchType = searchList.searchType.name,
                    description = searchList.description
                )
            }
        )
    }

    private fun captureConditionSettings(): ConditionSettings {
        return ConditionSettings(
            conditionOrder = conditionColorService.conditionOrder.value,
            conditionColors = conditionColorService.conditionColors.value
        )
    }

    private fun capturePlotSettings(curtainDataService: CurtainDataService): PlotSettings {
        val currentCurtainSettings = curtainDataService.curtainSettings
        
        return PlotSettings(
            volcanoAxis = currentCurtainSettings.volcanoAxis?.let { axis ->
                VolcanoAxisSettings(
                    minX = axis.minX,
                    maxX = axis.maxX,
                    minY = axis.minY,
                    maxY = axis.maxY
                )
            },
            volcanoPlotTitle = currentCurtainSettings.volcanoPlotTitle,
            textAnnotation = currentCurtainSettings.textAnnotation
        )
    }

    private fun captureAppPreferences(): AppPreferences {
        val sharedPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return AppPreferences(
            theme = sharedPrefs.getString("theme", "system") ?: "system",
            autoSave = sharedPrefs.getBoolean("auto_save", true),
            maxProteinsPerPage = sharedPrefs.getInt("max_proteins_per_page", 20),
            enableAnimations = sharedPrefs.getBoolean("enable_animations", true)
        )
    }

    // Private helper methods for applying settings

    private suspend fun applyVisualSettings(settings: VisualSettings, curtainDataService: CurtainDataService) {
        // Update condition color service first
        conditionColorService.setCurrentPalette(settings.colorPalette)
        conditionColorService.updateConditionColors(settings.customColorMap)
        
        // Update curtain settings in the main data service
        val currentSettings = curtainDataService.curtainSettings
        
        // Prepare the complete color map for CurtainSettings (String -> String)
        val completeColorMapForSettings = mutableMapOf<String, String>()
        completeColorMapForSettings.putAll(currentSettings.colorMap)
        completeColorMapForSettings.putAll(settings.customColorMap)
        
        curtainDataService.curtainSettings = currentSettings.copy(
            colorMap = completeColorMapForSettings,
            backGroundColorGrey = settings.backgroundColorGrey,
            defaultColorList = settings.defaultColorList.ifEmpty { currentSettings.defaultColorList }
        )
        
        // Ensure condition color service state is synchronized (String -> String)
        conditionColorService.updateConditionColors(settings.customColorMap)
    }

    private suspend fun applyAnalysisSettings(settings: AnalysisSettings, curtainDataService: CurtainDataService) {
        // Update curtain settings with analysis parameters
        val currentSettings = curtainDataService.curtainSettings
        curtainDataService.curtainSettings = currentSettings.copy(
            pCutoff = settings.pCutoff,
            log2FCCutoff = settings.log2FCCutoff,
            sampleVisible = settings.sampleVisible,
            legendStatus = settings.legendStatus.mapValues { (_, value) -> value as Any }
        )
    }

    private suspend fun applySearchSettings(settings: SearchSettings) {
        try {
            // Apply search lists to the search service
            if (settings.searchLists.isNotEmpty()) {
                // Clear existing search lists
                searchService.clearAllSearchLists()
                
                // Add the saved search lists
                settings.searchLists.forEach { savedSearchList ->
                    try {
                        searchService.createSearchList(
                            name = savedSearchList.name,
                            proteinIds = savedSearchList.proteinIds,
                            searchTerms = savedSearchList.searchTerms,
                            searchType = SearchType.valueOf(savedSearchList.searchType),
                            color = savedSearchList.color,
                            description = savedSearchList.description
                        )
                    } catch (e: Exception) {
                        // Log but don't fail the entire operation for individual search list issues
                        android.util.Log.w("SettingsVariantService", "Failed to restore search list '${savedSearchList.name}': ${e.message}")
                    }
                }
                
                // Search lists have been restored successfully
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsVariantService", "Error applying search settings", e)
        }
    }

    private suspend fun applyConditionSettings(settings: ConditionSettings, curtainDataService: CurtainDataService) {
        // Update condition color service
        conditionColorService.setConditionOrder(settings.conditionOrder)
        conditionColorService.updateConditionColors(settings.conditionColors)
        
        // Update curtain settings
        val currentSettings = curtainDataService.curtainSettings
        curtainDataService.curtainSettings = currentSettings.copy(
            sampleMap = buildSampleMapWithConditions(settings, curtainDataService)
        )
    }

    private suspend fun applyPlotSettings(settings: PlotSettings, curtainDataService: CurtainDataService) {
        // Update curtain settings with plot-specific settings
        val currentSettings = curtainDataService.curtainSettings
        
        val newTextAnnotation = if (settings.textAnnotation.isNotEmpty()) settings.textAnnotation else currentSettings.textAnnotation
        
        curtainDataService.curtainSettings = currentSettings.copy(
            volcanoAxis = settings.volcanoAxis?.let { axis ->
                VolcanoAxis(
                    minX = axis.minX,
                    maxX = axis.maxX,
                    minY = axis.minY,
                    maxY = axis.maxY
                )
            } ?: currentSettings.volcanoAxis,
            volcanoPlotTitle = settings.volcanoPlotTitle.ifEmpty { currentSettings.volcanoPlotTitle },
            textAnnotation = newTextAnnotation
        )
        
        android.util.Log.d("SettingsVariantService", "Applied plot settings. New curtainSettings textAnnotation: size=${curtainDataService.curtainSettings.textAnnotation.size}")
    }

    private suspend fun applyAppPreferences(preferences: AppPreferences) {
        val sharedPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("theme", preferences.theme)
            putBoolean("auto_save", preferences.autoSave)
            putInt("max_proteins_per_page", preferences.maxProteinsPerPage)
            putBoolean("enable_animations", preferences.enableAnimations)
            apply()
        }
    }
    
    /**
     * Helper method to build sample map with condition information
     */
    private fun buildSampleMapWithConditions(settings: ConditionSettings, curtainDataService: CurtainDataService): MutableMap<String, Map<String, String>> {
        val currentSampleMap = curtainDataService.curtainSettings.sampleMap.toMutableMap()
        
        // Update condition information in sample map
        settings.conditionOrder.forEachIndexed { index, condition ->
            // Find samples that belong to this condition and update their condition field
            currentSampleMap.forEach { (sampleId, sampleInfo) ->
                if (sampleInfo["condition"] == condition) {
                    currentSampleMap[sampleId] = sampleInfo.toMutableMap().apply {
                        put("condition", condition)
                    }
                }
            }
        }
        
        return currentSampleMap
    }

    // Utility methods

    private fun generateUniqueId(): String {
        return "variant_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun calculateFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun loadAvailableVariants() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText(StandardCharsets.UTF_8)
                val metadata = metadataListAdapter.fromJson(json) ?: emptyList()
                _availableVariants.value = metadata
            }
        } catch (e: Exception) {
            _availableVariants.value = emptyList()
        }
    }

    private fun refreshAvailableVariants() {
        loadAvailableVariants()
    }

    private suspend fun updateMetadata(metadata: SettingsVariantMetadata) = withContext(Dispatchers.IO) {
        val currentMetadata = _availableVariants.value.toMutableList()
        val existingIndex = currentMetadata.indexOfFirst { it.id == metadata.id }
        
        if (existingIndex >= 0) {
            currentMetadata[existingIndex] = metadata
        } else {
            currentMetadata.add(metadata)
        }
        
        val json = metadataListAdapter.toJson(currentMetadata)
        metadataFile.writeText(json, StandardCharsets.UTF_8)
        _availableVariants.value = currentMetadata
    }

    private suspend fun removeFromMetadata(variantId: String) = withContext(Dispatchers.IO) {
        val currentMetadata = _availableVariants.value.toMutableList()
        currentMetadata.removeAll { it.id == variantId }
        
        val json = metadataListAdapter.toJson(currentMetadata)
        metadataFile.writeText(json, StandardCharsets.UTF_8)
        _availableVariants.value = currentMetadata
    }

    // Additional methods needed by the dialog

    /**
     * Search variants by name, description, or tags
     */
    suspend fun searchVariants(query: String): List<SettingsVariant> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext getAllVariantsSync()
        }
        
        val lowercaseQuery = query.lowercase()
        getAllVariantsSync().filter { variant ->
            variant.name.lowercase().contains(lowercaseQuery) ||
            variant.description?.lowercase()?.contains(lowercaseQuery) == true ||
            variant.tags.any { it.lowercase().contains(lowercaseQuery) }
        }
    }

    /**
     * Get filtered variants based on criteria
     */
    suspend fun getFilteredVariants(
        showDefault: Boolean = false,
        showRecent: Boolean = false,
        showFavorites: Boolean = false,
        showAll: Boolean = true
    ): List<SettingsVariant> = withContext(Dispatchers.IO) {
        val allVariants = getAllVariantsSync()
        
        if (showAll && !showDefault && !showRecent && !showFavorites) {
            return@withContext allVariants
        }
        
        allVariants.filter { variant ->
            when {
                showDefault && variant.isDefault -> true
                showFavorites && variant.isFavorite -> true
                showRecent && isRecentVariant(variant) -> true
                else -> false
            }
        }
    }

    /**
     * Toggle favorite status of a variant
     */
    suspend fun toggleFavorite(variantId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val variant = getVariant(variantId) ?: return@withContext Result.failure(
                IllegalArgumentException("Variant not found: $variantId")
            )
            
            val updatedVariant = variant.copy(
                isFavorite = !variant.isFavorite,
                lastModified = System.currentTimeMillis()
            )
            
            saveVariantToFile(updatedVariant)
            _variantsFlow.value = getAllVariantsSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Duplicate a variant with a new name
     */
    suspend fun duplicateVariant(variantId: String): Result<SettingsVariant> = withContext(Dispatchers.IO) {
        try {
            val originalVariant = getVariant(variantId) ?: return@withContext Result.failure(
                IllegalArgumentException("Variant not found: $variantId")
            )
            
            val duplicatedVariant = originalVariant.copy(
                id = generateId(),
                name = "${originalVariant.name} (Copy)",
                isDefault = false,
                isFavorite = false,
                createdAt = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
                usageCount = 0
            )
            
            saveVariantToFile(duplicatedVariant)
            _variantsFlow.value = getAllVariantsSync()
            Result.success(duplicatedVariant)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export specific variants
     */
    suspend fun exportVariants(variants: List<SettingsVariant>): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exportData = mapOf(
                "variants" to variants,
                "metadata" to mapOf(
                    "exported_at" to Date(),
                    "version" to "1.0",
                    "count" to variants.size,
                    "app_version" to getAppVersion()
                )
            )
            
            val fileName = if (variants.size == 1) {
                "variant_${variants.first().name.replace(Regex("[^A-Za-z0-9]"), "_")}_${System.currentTimeMillis()}"
            } else {
                "variants_${variants.size}_${System.currentTimeMillis()}"
            }
            
            val exportFile = createExportFile(fileName)
            val mapAdapter = moshi.adapter<Map<String, Any>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )
            val json = mapAdapter.toJson(exportData)
            exportFile.writeText(json, StandardCharsets.UTF_8)
            
            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export all settings variants to external storage
     */
    suspend fun exportAllVariants(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val allVariants = getAllVariantsSync()
            exportVariants(allVariants)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import variants from external file
     */
    suspend fun importVariants(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // This would typically use a file picker - for now return 0
            // Implementation would depend on Android file picker integration
            Result.success(0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Backup all variants to external storage
     */
    suspend fun backupAllVariants(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val result = exportAllVariants()
            
            // Update metadata with backup info
            if (result.isSuccess) {
                val metadata = loadMetadata().toMutableMap()
                metadata["last_backup"] = Date()
                saveMetadata(metadata)
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get storage information
     */
    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val variants = getAllVariantsSync()
        val totalSize = settingsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
        
        val metadata = loadMetadata()
        val lastBackup = metadata["last_backup"] as? Date
        
        StorageInfo(
            variantCount = variants.size,
            totalSizeBytes = totalSize,
            formattedSize = formatFileSize(totalSize),
            lastBackupDate = lastBackup?.let { 
                java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it) 
            }
        )
    }

    private fun isRecentVariant(variant: SettingsVariant): Boolean {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        
        return (variant.lastUsed ?: 0) > sevenDaysAgo ||
               variant.lastModified > sevenDaysAgo
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return when {
            kb < 1 -> "${bytes} B"
            kb < 1024 -> "${String.format("%.1f", kb)} KB"
            else -> "${String.format("%.1f", kb / 1024)} MB"
        }
    }

    private fun createExportFile(fileName: String): File {
        val externalDir = File(context.getExternalFilesDir(null), "exports")
        externalDir.mkdirs()
        return File(externalDir, "$fileName.json")
    }

    private fun loadMetadata(): Map<String, Any> {
        return try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText(StandardCharsets.UTF_8)
                moshi.adapter<Map<String, Any>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                ).fromJson(json) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveMetadata(metadata: Map<String, Any>) {
        try {
            val json = moshi.adapter<Map<String, Any>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            ).toJson(metadata)
            metadataFile.writeText(json, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Log error but don't fail
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    data class StorageInfo(
        val variantCount: Int,
        val totalSizeBytes: Long,
        val formattedSize: String,
        val lastBackupDate: String?
    )
}