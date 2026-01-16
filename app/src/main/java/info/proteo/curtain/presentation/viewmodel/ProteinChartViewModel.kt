package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.service.AverageBarChartGenerator
import info.proteo.curtain.domain.service.BarChartGenerator
import info.proteo.curtain.domain.service.ViolinPlotGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProteinChartViewModel @Inject constructor(
    private val barChartGenerator: BarChartGenerator,
    private val averageBarChartGenerator: AverageBarChartGenerator,
    private val violinPlotGenerator: ViolinPlotGenerator,
    private val proteomicsDataService: info.proteo.curtain.domain.service.ProteomicsDataService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _barChartHtml = MutableStateFlow<String?>(null)
    val barChartHtml: StateFlow<String?> = _barChartHtml.asStateFlow()

    private val _averageBarChartHtml = MutableStateFlow<String?>(null)
    val averageBarChartHtml: StateFlow<String?> = _averageBarChartHtml.asStateFlow()

    private val _violinPlotHtml = MutableStateFlow<String?>(null)
    val violinPlotHtml: StateFlow<String?> = _violinPlotHtml.asStateFlow()

    private val _proteinId = MutableStateFlow<String?>(null)
    val proteinId: StateFlow<String?> = _proteinId.asStateFlow()

    private val _foldChange = MutableStateFlow<Double?>(null)
    val foldChange: StateFlow<Double?> = _foldChange.asStateFlow()

    private val _pValue = MutableStateFlow<Double?>(null)
    val pValue: StateFlow<Double?> = _pValue.asStateFlow()

    fun loadProteinChart(
        curtainData: CurtainData,
        proteinId: String,
        geneName: String? = null,
        enableImputation: Boolean = false,
        useStandardError: Boolean = true,
        showAverageIndividualPoints: Boolean = true
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _proteinId.value = proteinId

            try {
                val displayName = geneName?.takeIf { it.isNotEmpty() } ?: proteinId

                val linkId = curtainData.linkId
                val rawDataList = proteomicsDataService.getRawDataForProtein(linkId, proteinId)

                val processedDataList = proteomicsDataService.getProcessedDataForProtein(linkId, proteinId)
                if (processedDataList.isNotEmpty()) {
                    val processedData = processedDataList.first()
                    _foldChange.value = processedData.foldChange
                    _pValue.value = processedData.significant
                } else {
                    _foldChange.value = null
                    _pValue.value = null
                }

                val individualLimits = curtainData.settings.individualYAxisLimits[proteinId] as? Map<*, *>

                val barChartLimits = (individualLimits?.get("barChart") as? Map<*, *>)?.let { limits ->
                    val min = (limits["min"] as? Number)?.toDouble()
                    val max = (limits["max"] as? Number)?.toDouble()
                    if (min != null && max != null) Pair(min, max) else null
                }

                val avgBarChartLimits = (individualLimits?.get("averageBarChart") as? Map<*, *>)?.let { limits ->
                    val min = (limits["min"] as? Number)?.toDouble()
                    val max = (limits["max"] as? Number)?.toDouble()
                    if (min != null && max != null) Pair(min, max) else null
                }

                val violinPlotLimits = (individualLimits?.get("violinPlot") as? Map<*, *>)?.let { limits ->
                    val min = (limits["min"] as? Number)?.toDouble()
                    val max = (limits["max"] as? Number)?.toDouble()
                    if (min != null && max != null) Pair(min, max) else null
                }

                val barHtml = barChartGenerator.createBarChartHtml(
                    curtainData = curtainData,
                    proteinId = proteinId,
                    geneName = displayName,
                    yAxisTitle = "Intensity",
                    customYAxisRange = barChartLimits,
                    rawDataList = rawDataList,
                    enableImputation = enableImputation
                )
                _barChartHtml.value = barHtml

                val averageBarHtml = averageBarChartGenerator.createAverageBarChartHtml(
                    curtainData = curtainData,
                    proteinId = proteinId,
                    geneName = displayName,
                    yAxisTitle = "Average Intensity",
                    customYAxisRange = avgBarChartLimits,
                    showIndividualPoints = showAverageIndividualPoints,
                    useStandardError = useStandardError,
                    rawDataList = rawDataList,
                    enableImputation = enableImputation
                )
                _averageBarChartHtml.value = averageBarHtml

                val violinHtml = violinPlotGenerator.createViolinPlotHtml(
                    curtainData = curtainData,
                    proteinId = proteinId,
                    geneName = displayName,
                    yAxisTitle = "Intensity",
                    customYAxisRange = violinPlotLimits,
                    rawDataList = rawDataList
                )
                _violinPlotHtml.value = violinHtml

                _isLoading.value = false
            } catch (e: Exception) {
                android.util.Log.e("ProteinChartViewModel", "Error generating charts", e)
                _error.value = "Failed to generate charts: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun exportChart() {
        // TODO: Implement chart export
    }
}
