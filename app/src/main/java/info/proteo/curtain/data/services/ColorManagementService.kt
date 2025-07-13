package info.proteo.curtain.data.services

import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.data.models.SearchList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to manage colors across different parts of the application
 * Bridges search list colors with the global color management system
 */
@Singleton
class ColorManagementService @Inject constructor(
    private val searchService: SearchService
) {
    
    private val _globalColorMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val globalColorMap: StateFlow<Map<String, String>> = _globalColorMap.asStateFlow()
    
    // Default color palettes matching frontend implementation
    private val defaultColorPalettes = mapOf(
        "pastel" to listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999"
        ),
        "retro" to listOf(
            "#ea5545", "#f46a9b", "#ef9b20", "#edbf33", "#ede15b",
            "#bdcf32", "#87bc45", "#27aeef", "#b33dc6"
        ),
        "solid" to listOf(
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
            "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
        ),
        "okabe_ito" to listOf(
            "#E69F00", "#56B4E9", "#009E73", "#F0E442", "#0072B2",
            "#D55E00", "#CC79A7", "#000000"
        )
    )
    
    private var currentPalette = "pastel"
    
    /**
     * Gets the current default color palette
     */
    fun getCurrentColorPalette(): List<String> {
        return defaultColorPalettes[currentPalette] ?: defaultColorPalettes["pastel"]!!
    }
    
    /**
     * Sets the current color palette
     */
    fun setCurrentColorPalette(paletteName: String) {
        if (defaultColorPalettes.containsKey(paletteName)) {
            currentPalette = paletteName
        }
    }
    
    /**
     * Gets all available color palettes
     */
    fun getAvailableColorPalettes(): Map<String, List<String>> {
        return defaultColorPalettes
    }
    
    /**
     * Syncs search list colors with the global color map
     */
    fun syncSearchListColorsWithGlobal(curtainSettings: CurtainSettings): CurtainSettings {
        val searchLists = searchService.getSearchLists()
        val updatedColorMap = curtainSettings.colorMap.toMutableMap()
        
        // Add search list colors to global color map
        searchLists.forEach { searchList ->
            updatedColorMap[searchList.name] = searchList.color
        }
        
        _globalColorMap.value = updatedColorMap
        return curtainSettings.copy(colorMap = updatedColorMap)
    }
    
    /**
     * Updates search list colors from global color map changes
     */
    fun updateSearchListColorsFromGlobal(colorMap: Map<String, String>) {
        val searchLists = searchService.getSearchLists()
        
        searchLists.forEach { searchList ->
            val globalColor = colorMap[searchList.name]
            if (globalColor != null && globalColor != searchList.color) {
                searchService.changeSearchListColor(searchList.id, globalColor)
            }
        }
        
        _globalColorMap.value = colorMap
    }
    
    /**
     * Gets the next available color from the current palette
     */
    fun getNextAvailableColor(usedColors: Set<String>): String {
        val palette = getCurrentColorPalette()
        return palette.firstOrNull { it !in usedColors } ?: palette.first()
    }
    
    /**
     * Validates if a color is a valid hex color
     */
    fun isValidHexColor(color: String): Boolean {
        return try {
            if (!color.startsWith("#") || color.length != 7) {
                false
            } else {
                android.graphics.Color.parseColor(color)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generates a color map for protein visualizations
     * Includes both search list colors and global selection colors
     */
    fun generateVisualizationColorMap(curtainSettings: CurtainSettings): Map<String, String> {
        val combinedColorMap = mutableMapOf<String, String>()
        
        // Add existing global colors
        combinedColorMap.putAll(curtainSettings.colorMap)
        
        // Add search list colors
        val searchLists = searchService.getSearchLists()
        searchLists.forEach { searchList ->
            combinedColorMap[searchList.name] = searchList.color
        }
        
        return combinedColorMap
    }
    
    /**
     * Creates a color mapping for active search lists
     */
    fun getActiveSearchListColorMap(): Map<String, String> {
        val activeSearchLists = searchService.getActiveSearchLists()
        return activeSearchLists.associate { it.name to it.color }
    }
    
    /**
     * Resets colors to default palette
     */
    fun resetColorsToDefault(): Map<String, String> {
        val palette = getCurrentColorPalette()
        val searchLists = searchService.getSearchLists()
        val newColorMap = mutableMapOf<String, String>()
        
        searchLists.forEachIndexed { index, searchList ->
            val newColor = palette[index % palette.size]
            searchService.changeSearchListColor(searchList.id, newColor)
            newColorMap[searchList.name] = newColor
        }
        
        _globalColorMap.value = newColorMap
        return newColorMap
    }
    
    /**
     * Exports current color configuration
     */
    fun exportColorConfiguration(): Map<String, Any> {
        return mapOf(
            "palette" to currentPalette,
            "searchListColors" to searchService.getSearchLists().associate { it.name to it.color },
            "globalColorMap" to _globalColorMap.value
        )
    }
    
    /**
     * Imports color configuration
     */
    fun importColorConfiguration(config: Map<String, Any>) {
        // Set palette
        (config["palette"] as? String)?.let { palette ->
            setCurrentColorPalette(palette)
        }
        
        // Update search list colors
        (config["searchListColors"] as? Map<String, String>)?.forEach { (listName, color) ->
            val searchList = searchService.getSearchLists().find { it.name == listName }
            searchList?.let {
                searchService.changeSearchListColor(it.id, color)
            }
        }
        
        // Update global color map
        (config["globalColorMap"] as? Map<String, String>)?.let { colorMap ->
            _globalColorMap.value = colorMap
        }
    }
}