package info.proteo.curtain.data.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing condition-specific color assignments in the Curtain app.
 * This service handles color mapping for experimental conditions, integrating with
 * the frontend color management system.
 */
@Singleton
class ConditionColorService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "condition_colors"
        private const val KEY_COLOR_MAPPINGS = "color_mappings"
        private const val KEY_CURRENT_PALETTE = "current_palette"
        private const val KEY_CUSTOM_PALETTES = "custom_palettes"
        private const val KEY_CONDITION_ORDER = "condition_order"
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _conditionColors = MutableStateFlow<Map<String, String>>(loadStoredColorMappings())
    val conditionColors: StateFlow<Map<String, String>> = _conditionColors.asStateFlow()
    
    private val _currentPalette = MutableStateFlow(loadCurrentPalette())
    val currentPalette: StateFlow<String> = _currentPalette.asStateFlow()
    
    private val _conditionOrder = MutableStateFlow<List<String>>(loadStoredConditionOrder())
    val conditionOrder: StateFlow<List<String>> = _conditionOrder.asStateFlow()

    // Predefined color palettes matching frontend implementation
    val availablePalettes = mapOf(
        "pastel" to listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999"
        ),
        "retro" to listOf(
            "#ea5545", "#f46a9b", "#ef9b20", "#edbf33", "#ede15b",
            "#bdcf32", "#87bc45", "#27aeef", "#b33dc6", "#9b59b6"
        ),
        "solid" to listOf(
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
            "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
        ),
        "okabe_ito" to listOf(
            "#E69F00", "#56B4E9", "#009E73", "#F0E442", "#0072B2",
            "#D55E00", "#CC79A7", "#000000", "#999999", "#F4A261"
        ),
        "viridis" to listOf(
            "#440154", "#482777", "#3f4a8a", "#31678e", "#26838f",
            "#1f9d8a", "#6cce5a", "#b6de2b", "#fee825", "#f0f921"
        ),
        "colorbrewer_set3" to listOf(
            "#8dd3c7", "#ffffb3", "#bebada", "#fb8072", "#80b1d3",
            "#fdb462", "#b3de69", "#fccde5", "#d9d9d9", "#bc80bd"
        )
    )

    data class ConditionColorInfo(
        val condition: String,
        val color: String,
        val isCustom: Boolean = false,
        val sampleCount: Int = 0
    )

    /**
     * Get the current color palette
     */
    fun getCurrentPaletteColors(): List<String> {
        return availablePalettes[_currentPalette.value] ?: availablePalettes["pastel"]!!
    }

    /**
     * Set the current color palette and reassign colors to existing conditions
     */
    fun setCurrentPalette(paletteName: String) {
        if (paletteName in availablePalettes.keys) {
            _currentPalette.value = paletteName
            sharedPrefs.edit { putString(KEY_CURRENT_PALETTE, paletteName) }
            
            // Reassign colors to existing conditions using new palette
            reassignColorsFromPalette()
        }
    }

    /**
     * Get color for a specific condition, assigning one if not exists
     */
    fun getColorForCondition(condition: String): String {
        val currentColors = _conditionColors.value
        return currentColors[condition] ?: assignNewColor(condition)
    }

    /**
     * Set a custom color for a specific condition
     */
    fun setConditionColor(condition: String, color: String) {
        val updatedColors = _conditionColors.value.toMutableMap()
        updatedColors[condition] = color
        _conditionColors.value = updatedColors
        saveColorMappings(updatedColors)
    }

    /**
     * Remove color assignment for a condition
     */
    fun removeConditionColor(condition: String) {
        val updatedColors = _conditionColors.value.toMutableMap()
        updatedColors.remove(condition)
        _conditionColors.value = updatedColors
        saveColorMappings(updatedColors)
    }

    /**
     * Get all condition colors with additional information, ordered by user preference
     */
    fun getConditionColorInfo(sampleMap: Map<String, Map<String, String>>? = null): List<ConditionColorInfo> {
        val currentColors = _conditionColors.value
        val currentOrder = _conditionOrder.value
        val sampleCounts = mutableMapOf<String, Int>()
        
        // Count samples per condition if sampleMap provided
        sampleMap?.values?.forEach { sampleInfo ->
            val condition = sampleInfo["condition"]
            if (!condition.isNullOrEmpty()) {
                sampleCounts[condition] = sampleCounts.getOrDefault(condition, 0) + 1
            }
        }
        
        val colorInfoList = currentColors.map { (condition, color) ->
            ConditionColorInfo(
                condition = condition,
                color = color,
                isCustom = !getCurrentPaletteColors().contains(color),
                sampleCount = sampleCounts[condition] ?: 0
            )
        }
        
        // Sort by user-defined order, then alphabetically for new conditions
        return colorInfoList.sortedWith(compareBy<ConditionColorInfo> { colorInfo ->
            val orderIndex = currentOrder.indexOf(colorInfo.condition)
            if (orderIndex >= 0) orderIndex else Int.MAX_VALUE
        }.thenBy { it.condition })
    }

    /**
     * Generate color map for visualization components
     */
    fun generateVisualizationColorMap(
        conditions: List<String>,
        existingColorMap: Map<String, String>? = null
    ): Map<String, String> {
        val colorMap = mutableMapOf<String, String>()
        
        // Use existing colors from curtain settings if available
        existingColorMap?.let { colorMap.putAll(it) }
        
        // Use stored condition colors
        _conditionColors.value.let { conditionColors ->
            conditions.forEach { condition ->
                if (condition !in colorMap && condition in conditionColors) {
                    colorMap[condition] = conditionColors[condition]!!
                }
            }
        }
        
        // Assign new colors for remaining conditions
        val palette = getCurrentPaletteColors()
        val usedColors = colorMap.values.toSet()
        var colorIndex = 0
        
        conditions.forEach { condition ->
            if (condition !in colorMap) {
                // Find next available color
                var color = palette[colorIndex % palette.size]
                while (color in usedColors && colorIndex < palette.size * 2) {
                    colorIndex++
                    color = palette[colorIndex % palette.size]
                }
                
                colorMap[condition] = color
                setConditionColor(condition, color)
                colorIndex++
            }
        }
        
        return colorMap
    }

    /**
     * Reset all condition colors to use current palette
     */
    fun resetToCurrentPalette() {
        val currentColors = _conditionColors.value
        val palette = getCurrentPaletteColors()
        val updatedColors = mutableMapOf<String, String>()
        
        currentColors.keys.sorted().forEachIndexed { index, condition ->
            updatedColors[condition] = palette[index % palette.size]
        }
        
        _conditionColors.value = updatedColors
        saveColorMappings(updatedColors)
    }

    /**
     * Bulk update condition colors
     */
    fun updateConditionColors(colorMap: Map<String, String>) {
        val validColors = colorMap.filterValues { isValidHexColor(it) }
        _conditionColors.value = validColors
        saveColorMappings(validColors)
    }

    /**
     * Set the order of conditions
     */
    fun setConditionOrder(conditionOrder: List<String>) {
        _conditionOrder.value = conditionOrder
        saveConditionOrder(conditionOrder)
    }

    /**
     * Move a condition to a new position in the order
     */
    fun moveCondition(condition: String, newPosition: Int) {
        val currentOrder = _conditionOrder.value.toMutableList()
        val currentIndex = currentOrder.indexOf(condition)
        
        if (currentIndex >= 0) {
            // Remove from current position
            currentOrder.removeAt(currentIndex)
            // Insert at new position (clamped to valid range)
            val targetPosition = newPosition.coerceIn(0, currentOrder.size)
            currentOrder.add(targetPosition, condition)
            
            _conditionOrder.value = currentOrder
            saveConditionOrder(currentOrder)
        } else if (condition !in currentOrder) {
            // Add new condition at specified position
            val targetPosition = newPosition.coerceIn(0, currentOrder.size)
            currentOrder.add(targetPosition, condition)
            
            _conditionOrder.value = currentOrder
            saveConditionOrder(currentOrder)
        }
    }

    /**
     * Get ordered conditions for visualization
     */
    fun getOrderedConditions(allConditions: List<String>): List<String> {
        val currentOrder = _conditionOrder.value
        val orderedConditions = mutableListOf<String>()
        
        // Add conditions in user-defined order
        currentOrder.forEach { condition ->
            if (condition in allConditions) {
                orderedConditions.add(condition)
            }
        }
        
        // Add any new conditions not in the order
        allConditions.forEach { condition ->
            if (condition !in orderedConditions) {
                orderedConditions.add(condition)
            }
        }
        
        return orderedConditions
    }

    /**
     * Get next available color from current palette
     */
    fun getNextAvailableColor(): String {
        val usedColors = _conditionColors.value.values.toSet()
        val palette = getCurrentPaletteColors()
        
        return palette.firstOrNull { it !in usedColors } ?: palette.first()
    }

    /**
     * Check if a hex color is valid
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
     * Import color mappings from curtain settings
     */
    fun importFromCurtainSettings(
        colorMap: Map<String, String>,
        sampleMap: Map<String, Map<String, String>>,
        defaultColorList: List<String>,
        conditionOrder: List<String>? = null
    ) {
        val conditions = mutableSetOf<String>()
        
        // Extract conditions from sample map
        sampleMap.values.forEach { sampleInfo ->
            val condition = sampleInfo["condition"]
            if (!condition.isNullOrEmpty()) {
                conditions.add(condition)
            }
        }
        
        val updatedColors = mutableMapOf<String, String>()
        
        // Use colors from curtain settings colorMap if available
        conditions.forEach { condition ->
            val color = colorMap[condition]
            if (!color.isNullOrEmpty() && isValidHexColor(color)) {
                updatedColors[condition] = color
            } else {
                // Assign from default color list or current palette
                val palette = if (defaultColorList.isNotEmpty()) defaultColorList else getCurrentPaletteColors()
                val colorIndex = conditions.indexOf(condition) % palette.size
                updatedColors[condition] = palette[colorIndex]
            }
        }
        
        _conditionColors.value = updatedColors
        saveColorMappings(updatedColors)
        
        // Import condition order if provided
        if (!conditionOrder.isNullOrEmpty()) {
            val validOrder = conditionOrder.filter { it in conditions }
            if (validOrder.isNotEmpty()) {
                _conditionOrder.value = validOrder
                saveConditionOrder(validOrder)
            }
        } else {
            // Set alphabetical order for new conditions
            val sortedConditions = conditions.sorted()
            _conditionOrder.value = sortedConditions
            saveConditionOrder(sortedConditions)
        }
    }

    private fun assignNewColor(condition: String): String {
        val color = getNextAvailableColor()
        setConditionColor(condition, color)
        return color
    }

    private fun reassignColorsFromPalette() {
        val currentColors = _conditionColors.value
        val palette = getCurrentPaletteColors()
        val updatedColors = mutableMapOf<String, String>()
        
        currentColors.keys.sorted().forEachIndexed { index, condition ->
            updatedColors[condition] = palette[index % palette.size]
        }
        
        _conditionColors.value = updatedColors
        saveColorMappings(updatedColors)
    }

    private fun loadStoredColorMappings(): Map<String, String> {
        val json = sharedPrefs.getString(KEY_COLOR_MAPPINGS, "{}")
        return try {
            val jsonObject = JSONObject(json ?: "{}")
            val result = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveColorMappings(colorMap: Map<String, String>) {
        val jsonObject = JSONObject()
        colorMap.forEach { (condition, color) ->
            jsonObject.put(condition, color)
        }
        sharedPrefs.edit { putString(KEY_COLOR_MAPPINGS, jsonObject.toString()) }
    }

    private fun loadCurrentPalette(): String {
        return sharedPrefs.getString(KEY_CURRENT_PALETTE, "pastel") ?: "pastel"
    }

    private fun loadStoredConditionOrder(): List<String> {
        val json = sharedPrefs.getString(KEY_CONDITION_ORDER, "[]")
        return try {
            val jsonArray = org.json.JSONArray(json ?: "[]")
            val result = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getString(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveConditionOrder(conditionOrder: List<String>) {
        val jsonArray = org.json.JSONArray()
        conditionOrder.forEach { condition ->
            jsonArray.put(condition)
        }
        sharedPrefs.edit { putString(KEY_CONDITION_ORDER, jsonArray.toString()) }
    }
}