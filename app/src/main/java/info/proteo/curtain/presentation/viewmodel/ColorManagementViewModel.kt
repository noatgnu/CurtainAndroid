package info.proteo.curtain.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.CurtainSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ColorManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _curtainData = MutableStateFlow<CurtainData?>(null)
    val curtainData: StateFlow<CurtainData?> = _curtainData.asStateFlow()

    private val _conditions = MutableStateFlow<List<String>>(emptyList())
    val conditions: StateFlow<List<String>> = _conditions.asStateFlow()

    private val _proteinGroups = MutableStateFlow<List<String>>(emptyList())
    val proteinGroups: StateFlow<List<String>> = _proteinGroups.asStateFlow()

    private val _currentColors = MutableStateFlow<Map<String, Color>>(emptyMap())
    val currentColors: StateFlow<Map<String, Color>> = _currentColors.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentLinkId: String? = null

    fun loadColorSettings(curtainData: CurtainData) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            currentLinkId = curtainData.linkId

            try {
                _curtainData.value = curtainData

                val conditionsList = curtainData.settings.conditionOrder
                _conditions.value = conditionsList

                val selectionNames = curtainData.selectionsName ?: emptyList()
                _proteinGroups.value = selectionNames

                val colorMap = mutableMapOf<String, Color>()

                conditionsList.forEachIndexed { index, condition ->
                    val colorValue = curtainData.settings.colorMap[condition]
                    if (colorValue != null) {
                        colorMap[condition] = parseColor(colorValue)
                    } else {
                        colorMap[condition] = getDefaultConditionColor(index)
                    }
                }

                selectionNames.forEach { groupName ->
                    colorMap[groupName] = Color.Blue
                }

                _currentColors.value = colorMap

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun updateColor(itemId: String, color: Color) {
        val updatedColors = _currentColors.value.toMutableMap()
        updatedColors[itemId] = color
        _currentColors.value = updatedColors
    }

    fun resetColors() {
        val conditions = _conditions.value
        val proteinGroups = _proteinGroups.value
        val colorMap = mutableMapOf<String, Color>()

        conditions.forEachIndexed { index, condition ->
            colorMap[condition] = getDefaultConditionColor(index)
        }

        proteinGroups.forEach { groupName ->
            colorMap[groupName] = Color.Blue
        }

        _currentColors.value = colorMap
    }

    fun applyColors(onApply: (CurtainSettings) -> Unit) {
        val currentData = _curtainData.value ?: return
        val updatedSettings = currentData.settings.copy(
            colorMap = _currentColors.value
                .filterKeys { key: String -> key in _conditions.value }
                .mapValues { entry: Map.Entry<String, Color> -> colorToHex(entry.value) }
        )
        onApply(updatedSettings)
    }

    private fun parseColor(colorValue: Any): Color {
        return when (colorValue) {
            is String -> {
                try {
                    val hex = colorValue.removePrefix("#")
                    val colorInt = hex.toLong(16).toInt()
                    Color(colorInt or 0xFF000000.toInt())
                } catch (e: Exception) {
                    Color.Gray
                }
            }
            is Number -> Color(colorValue.toInt() or 0xFF000000.toInt())
            else -> Color.Gray
        }
    }

    private fun colorToHex(color: Color): String {
        val red = (color.red * 255).toInt()
        val green = (color.green * 255).toInt()
        val blue = (color.blue * 255).toInt()
        return "#%02X%02X%02X".format(red, green, blue)
    }

    private fun getDefaultConditionColor(index: Int): Color {
        val defaultColors = listOf(
            Color(0xFF1f77b4),
            Color(0xFFff7f0e),
            Color(0xFF2ca02c),
            Color(0xFFd62728),
            Color(0xFF9467bd),
            Color(0xFF8c564b),
            Color(0xFFe377c2),
            Color(0xFF7f7f7f),
            Color(0xFFbcbd22),
            Color(0xFF17becf)
        )
        return defaultColors[index % defaultColors.size]
    }
}
