package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.CurtainSettings
import info.proteo.curtain.domain.model.ProteinPoint
import info.proteo.curtain.domain.repository.CurtainRepository
import info.proteo.curtain.domain.service.CurtainDataService
import info.proteo.curtain.domain.service.PlotlyChartGenerator
import info.proteo.curtain.domain.service.ProteinMappingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

@HiltViewModel
class CurtainDetailsViewModel @Inject constructor(
    private val curtainRepository: CurtainRepository,
    private val curtainDataService: CurtainDataService,
    private val plotlyChartGenerator: PlotlyChartGenerator,
    private val proteinMappingService: ProteinMappingService,
    private val proteomicsDataService: info.proteo.curtain.domain.service.ProteomicsDataService,
    private val volcanoPlotDataService: info.proteo.curtain.domain.service.VolcanoPlotDataService
) : ViewModel() {

    private val _curtainData = MutableStateFlow<CurtainData?>(null)
    val curtainData: StateFlow<CurtainData?> = _curtainData.asStateFlow()

    private val _volcanoPlotHtml = MutableStateFlow<String?>(null)
    val volcanoPlotHtml: StateFlow<String?> = _volcanoPlotHtml.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _mappingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val mappingProgress: StateFlow<Pair<Int, Int>?> = _mappingProgress.asStateFlow()

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus.asStateFlow()

    private val _proteinCount = MutableStateFlow(0)
    val proteinCount: StateFlow<Int> = _proteinCount.asStateFlow()

    fun loadCurtainData(linkId: String) {
        // Don't reload if already loaded for this linkId
        if (_curtainData.value != null && _isLoading.value == false) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _error.value = null
            }

            try {
                val curtain = curtainRepository.getCurtainById(linkId)
                if (curtain == null) {
                    withContext(Dispatchers.Main) {
                        _error.value = "Curtain dataset not found"
                        _isLoading.value = false
                    }
                    return@launch
                }

                if (curtain.file.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        _error.value = "No data file available. Please download the dataset first."
                        _isLoading.value = false
                    }
                    return@launch
                }

                android.util.Log.d("CurtainDetailsViewModel", "Loading curtain data from: ${curtain.file}")

                withContext(Dispatchers.Main) {
                    _loadingStatus.value = "Checking database..."
                }

                val dbCurtainData = proteomicsDataService.loadCurtainDataFromDatabase(curtain.linkId)

                val curtainData = if (dbCurtainData != null) {
                    android.util.Log.d("CurtainDetailsViewModel", "Loaded from database")
                    withContext(Dispatchers.Main) {
                        _loadingStatus.value = "Loading from database..."
                    }
                    dbCurtainData
                } else {
                    android.util.Log.d("CurtainDetailsViewModel", "Parsing from JSON file")
                    withContext(Dispatchers.Main) {
                        _loadingStatus.value = "Parsing dataset file..."
                    }
                    val result = curtainDataService.loadCurtainDataFromFile(curtain.file)
                    result.getOrNull()?.let { loadedData ->
                        withContext(Dispatchers.Main) {
                            _loadingStatus.value = "Building protein mappings..."
                        }
                        proteinMappingService.ensureMappingsExist(loadedData.curtainData) { current, total ->
                            _mappingProgress.value = Pair(current, total)
                        }

                        withContext(Dispatchers.Main) {
                            _loadingStatus.value = "Building database..."
                        }
                        proteomicsDataService.buildProteomicsDataIfNeeded(
                            linkId = loadedData.curtainData.linkId,
                            rawTsv = loadedData.rawTsv,
                            processedTsv = loadedData.processedTsv,
                            rawForm = loadedData.curtainData.rawForm,
                            differentialForm = loadedData.curtainData.differentialForm,
                            curtainData = loadedData.curtainData,
                            onProgress = { status ->
                                _loadingStatus.value = status
                            }
                        )
                        loadedData.curtainData
                    }
                }

                if (curtainData != null) {
                    val db = proteomicsDataService.getDatabaseForLinkId(curtainData.linkId)
                    val proteinCount = db.proteomicsDataDao().getDistinctProteinCount()

                    withContext(Dispatchers.Main) {
                        _mappingProgress.value = null
                        _loadingStatus.value = null
                        _curtainData.value = curtainData
                        _proteinCount.value = proteinCount
                        _isLoading.value = false
                    }

                    // Generate volcano plot in background after UI is ready
                    generateVolcanoPlot(curtainData)
                } else {
                    withContext(Dispatchers.Main) {
                        _error.value = "Failed to load curtain data"
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CurtainDetailsViewModel", "Error loading curtain data", e)
                withContext(Dispatchers.Main) {
                    _error.value = e.message ?: "An unexpected error occurred"
                    _isLoading.value = false
                }
            }
        }
    }

    fun updateSettings(newSettings: CurtainSettings) {
        val currentData = _curtainData.value ?: return
        android.util.Log.d("CurtainDetailsViewModel", "updateSettings called, textAnnotation count: ${newSettings.textAnnotation.size}")
        val updatedData = currentData.copy(_settings = newSettings)
        _curtainData.value = updatedData
        generateVolcanoPlot(updatedData)
    }

    fun loadSettingsVariant(
        newSettings: CurtainSettings,
        variantSelectedMap: Map<String, Map<String, Boolean>>? = null,
        variantSelectionsName: List<String>? = null
    ) {
        val currentData = _curtainData.value ?: return
        val updatedData = currentData.copy(
            _settings = newSettings,
            selectedMap = variantSelectedMap ?: currentData.selectedMap,
            selectionsName = variantSelectionsName ?: currentData.selectionsName
        )
        _curtainData.value = updatedData
        generateVolcanoPlot(updatedData)
    }

    fun getLastGeneratedTraces(): List<info.proteo.curtain.domain.model.TraceData> {
        return plotlyChartGenerator.lastGeneratedTraces
    }

    suspend fun getProteinDataForClick(primaryId: String): ProteinPoint? {
        val currentData = _curtainData.value ?: return null
        val db = proteomicsDataService.getDatabaseForLinkId(currentData.linkId)
        val entities = db.proteomicsDataDao().getProcessedDataByPrimaryId(primaryId)
        if (entities.isEmpty()) return null

        val entity = entities.first()
        return ProteinPoint(
            id = entity.primaryId,
            primaryID = entity.primaryId,
            geneNames = entity.geneNames,
            proteinName = entity.geneNames ?: entity.primaryId,
            log2FC = entity.foldChange ?: 0.0,
            pValue = 10.0.pow(-(entity.significant ?: 0.0)),
            negLog10PValue = entity.significant ?: 0.0,
            color = "#808080",
            isSignificant = false
        )
    }

    suspend fun processVolcanoDataForPointClick(curtainData: CurtainData): info.proteo.curtain.domain.service.VolcanoPlotDataService.VolcanoProcessResult {
        return volcanoPlotDataService.processVolcanoData(curtainData, curtainData.settings)
    }

    fun saveTraceOrder(order: List<String>) {
        val currentData = _curtainData.value ?: return
        val updatedSettings = currentData.settings.copy(volcanoTraceOrder = order)
        updateSettings(updatedSettings)
    }

    fun resetTraceOrder() {
        val currentData = _curtainData.value ?: return
        val updatedSettings = currentData.settings.copy(volcanoTraceOrder = emptyList())
        updateSettings(updatedSettings)
    }

    fun saveIndividualYAxisLimits(
        proteinId: String,
        barChartMin: Double?,
        barChartMax: Double?,
        avgBarChartMin: Double?,
        avgBarChartMax: Double?,
        violinPlotMin: Double?,
        violinPlotMax: Double?
    ) {
        val currentData = _curtainData.value ?: return
        val updatedLimits = currentData.settings.individualYAxisLimits.toMutableMap()

        val proteinLimits = mutableMapOf<String, Map<String, Double?>>()

        if (barChartMin != null || barChartMax != null) {
            proteinLimits["barChart"] = mapOf("min" to barChartMin, "max" to barChartMax)
        }
        if (avgBarChartMin != null || avgBarChartMax != null) {
            proteinLimits["averageBarChart"] = mapOf("min" to avgBarChartMin, "max" to avgBarChartMax)
        }
        if (violinPlotMin != null || violinPlotMax != null) {
            proteinLimits["violinPlot"] = mapOf("min" to violinPlotMin, "max" to violinPlotMax)
        }

        if (proteinLimits.isNotEmpty()) {
            updatedLimits[proteinId] = proteinLimits
        }

        val updatedSettings = currentData.settings.copy(individualYAxisLimits = updatedLimits)
        updateSettings(updatedSettings)
    }

    fun clearIndividualYAxisLimits(proteinId: String) {
        val currentData = _curtainData.value ?: return
        val updatedLimits = currentData.settings.individualYAxisLimits.toMutableMap()
        updatedLimits.remove(proteinId)

        val updatedSettings = currentData.settings.copy(individualYAxisLimits = updatedLimits)
        updateSettings(updatedSettings)
    }

    fun updateAnnotation(
        key: String,
        newText: String? = null,
        newOffset: Pair<Double, Double>? = null
    ) {
        val currentData = _curtainData.value ?: return
        val updatedTextAnnotation = currentData.settings.textAnnotation.toMutableMap()

        val annotationData = updatedTextAnnotation[key] as? MutableMap<String, Any> ?: return
        val dataSection = annotationData["data"] as? MutableMap<String, Any> ?: return

        newText?.let {
            dataSection["text"] = "<b>$it</b>"
        }

        newOffset?.let { (ax, ay) ->
            android.util.Log.d("UpdateAnnotation", "Saving annotation '$key' with offset: ax=$ax, ay=$ay")
            dataSection["ax"] = ax
            dataSection["ay"] = ay
        }

        annotationData["data"] = dataSection
        updatedTextAnnotation[key] = annotationData

        val updatedSettings = currentData.settings.copy(textAnnotation = updatedTextAnnotation)
        updateSettings(updatedSettings)
    }

    fun addAnnotation(
        text: String,
        x: Double,
        y: Double
    ) {
        val currentData = _curtainData.value ?: return
        val updatedTextAnnotation = currentData.settings.textAnnotation.toMutableMap()

        val key = text
        val annotationData = mapOf(
            "title" to text,
            "data" to mapOf(
                "x" to x,
                "y" to y,
                "text" to "<b>$text</b>",
                "showarrow" to true,
                "arrowhead" to 2,
                "arrowsize" to 1.0,
                "arrowwidth" to 2.0,
                "arrowcolor" to "#000000",
                "ax" to -20.0,
                "ay" to -20.0,
                "xanchor" to "auto",
                "yanchor" to "auto",
                "font" to mapOf(
                    "family" to "Arial",
                    "size" to 12.0,
                    "color" to "#000000"
                )
            )
        )

        updatedTextAnnotation[key] = annotationData

        val updatedSettings = currentData.settings.copy(textAnnotation = updatedTextAnnotation)
        updateSettings(updatedSettings)
    }

    fun createSelectionFromProteinIds(
        selectionName: String,
        proteinIds: Set<String>
    ) {
        android.util.Log.d("CurtainDetailsViewModel", "createSelectionFromProteinIds called with name='$selectionName', proteinIds=${proteinIds.size}")
        val currentData = _curtainData.value ?: return

        var finalSelectionName = selectionName

        viewModelScope.launch(Dispatchers.Default) {
            if (proteinIds.isNotEmpty()) {
                val firstProteinId = proteinIds.first()
                val linkId = currentData.linkId
                val proteinDataList = proteomicsDataService.getProcessedDataForProtein(linkId, firstProteinId)

                android.util.Log.d("CurtainDetailsViewModel", "Getting comparison for firstProteinId=$firstProteinId, found ${proteinDataList.size} entries")

                if (proteinDataList.isNotEmpty()) {
                    val proteinData = proteinDataList.first()
                    val comparisonValue = proteinData.comparison
                    android.util.Log.d("CurtainDetailsViewModel", "comparisonValue='$comparisonValue'")

                    if (comparisonValue.isNotEmpty()) {
                        val comparisonRegex = Regex("""\(([^)]*)\)[^(]*$""")
                        val match = comparisonRegex.find(selectionName)

                        finalSelectionName = if (match != null) {
                            val extractedComparison = match.groupValues[1]
                            android.util.Log.d("CurtainDetailsViewModel", "Found parentheses with content='$extractedComparison', actual comparison='$comparisonValue'")

                            if (extractedComparison == comparisonValue) {
                                android.util.Log.d("CurtainDetailsViewModel", "Comparison already matches, keeping name as-is")
                                selectionName
                            } else {
                                android.util.Log.d("CurtainDetailsViewModel", "Comparison doesn't match, appending actual comparison")
                                "$selectionName ($comparisonValue)"
                            }
                        } else {
                            android.util.Log.d("CurtainDetailsViewModel", "No parentheses found, appending comparison")
                            "$selectionName ($comparisonValue)"
                        }
                    }
                }
            }

            android.util.Log.d("CurtainDetailsViewModel", "finalSelectionName='$finalSelectionName'")

            val updatedSelectedMap = currentData.selectedMap?.toMutableMap() ?: mutableMapOf()
            val updatedSelectionsName = currentData.selectionsName?.toMutableList() ?: mutableListOf()

            if (!updatedSelectionsName.contains(finalSelectionName)) {
                updatedSelectionsName.add(finalSelectionName)
            }

            proteinIds.forEach { proteinId ->
                val proteinSelections = updatedSelectedMap[proteinId]?.toMutableMap() ?: mutableMapOf()
                proteinSelections[finalSelectionName] = true
                updatedSelectedMap[proteinId] = proteinSelections
            }

            val colors = listOf("#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F")
            val colorIndex = updatedSelectionsName.size - 1
            val color = colors[colorIndex % colors.size]

            val updatedColorMap = currentData.settings.colorMap.toMutableMap()
            updatedColorMap[finalSelectionName] = color

            android.util.Log.d("CurtainDetailsViewModel", "Adding selection: name='$finalSelectionName', color=$color, proteinCount=${proteinIds.size}")
            android.util.Log.d("CurtainDetailsViewModel", "Updated selectionsName: $updatedSelectionsName")

            val updatedSettings = currentData.settings.copy(colorMap = updatedColorMap)

            withContext(Dispatchers.Main) {
                _curtainData.value = currentData.copy(
                    selectedMap = updatedSelectedMap,
                    selectionsName = updatedSelectionsName,
                    _settings = updatedSettings
                )
                generateVolcanoPlot(_curtainData.value!!)
            }
        }
    }

    fun forceRebuildDataset(linkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                    _loadingStatus.value = "Clearing cached data..."
                    _error.value = null
                }

                proteomicsDataService.clearDatabaseForLinkId(linkId)
                proteinMappingService.clearMappingsForLinkId(linkId)

                withContext(Dispatchers.Main) {
                    _curtainData.value = null
                    _volcanoPlotHtml.value = null
                    _proteinCount.value = 0
                    _isLoading.value = false
                    _loadingStatus.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("CurtainDetailsViewModel", "Error during force rebuild", e)
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to rebuild dataset: ${e.message}"
                    _isLoading.value = false
                    _loadingStatus.value = null
                }
            }
        }
    }

    fun bulkCreateAnnotations(proteins: List<info.proteo.curtain.domain.model.ProteinPoint>) {
        android.util.Log.d("CurtainDetailsViewModel", "bulkCreateAnnotations called with ${proteins.size} proteins")
        val currentData = _curtainData.value ?: return
        android.util.Log.d("CurtainDetailsViewModel", "Current textAnnotation count: ${currentData.settings.textAnnotation.size}")
        android.util.Log.d("CurtainDetailsViewModel", "Current textAnnotation keys: ${currentData.settings.textAnnotation.keys}")
        val updatedTextAnnotation = currentData.settings.textAnnotation.toMutableMap()

        proteins.forEach { protein ->
            android.util.Log.d("CurtainDetailsViewModel", "Processing protein: ${protein.primaryID}, geneNames: ${protein.geneNames}, coords: (${protein.log2FC}, ${protein.negLog10PValue})")
            val annotationTitle = if (!protein.geneNames.isNullOrEmpty() && protein.geneNames != protein.primaryID) {
                "${protein.geneNames}(${protein.primaryID})"
            } else {
                protein.primaryID
            }

            if (updatedTextAnnotation.containsKey(annotationTitle)) {
                return@forEach
            }

            val plotX = protein.log2FC
            val plotY = protein.negLog10PValue

            val annotationData = mapOf(
                "primary_id" to protein.primaryID,
                "title" to annotationTitle,
                "data" to mapOf(
                    "xref" to "x",
                    "yref" to "y",
                    "x" to plotX,
                    "y" to plotY,
                    "text" to "<b>$annotationTitle</b>",
                    "showarrow" to true,
                    "arrowhead" to 1,
                    "arrowsize" to 1.0,
                    "arrowwidth" to 1.0,
                    "arrowcolor" to "#000000",
                    "ax" to -20.0,
                    "ay" to -20.0,
                    "xanchor" to "center",
                    "yanchor" to "bottom",
                    "font" to mapOf(
                        "size" to 15.0,
                        "color" to "#000000",
                        "family" to "Arial, sans-serif"
                    ),
                    "showannotation" to true,
                    "annotationID" to annotationTitle
                )
            )

            updatedTextAnnotation[annotationTitle] = annotationData
            android.util.Log.d("CurtainDetailsViewModel", "Created annotation: $annotationTitle at ($plotX, $plotY)")
        }

        android.util.Log.d("CurtainDetailsViewModel", "Bulk creating ${proteins.size} annotations, total annotations: ${updatedTextAnnotation.size}")
        val updatedSettings = currentData.settings.copy(textAnnotation = updatedTextAnnotation)
        updateSettings(updatedSettings)
    }

    fun generateVolcanoPlotPublic() {
        _curtainData.value?.let { generateVolcanoPlot(it) }
    }

    private fun generateVolcanoPlot(curtainData: CurtainData) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                android.util.Log.d("CurtainDetailsViewModel", "Starting volcano plot generation...")
                val startTime = System.currentTimeMillis()

                // This is the heavy operation, now running on Default dispatcher
                val htmlContent = plotlyChartGenerator.createVolcanoPlotHtml(curtainData)

                val duration = System.currentTimeMillis() - startTime
                android.util.Log.d("CurtainDetailsViewModel", "Volcano plot HTML generated in ${duration}ms, size: ${htmlContent.length}")

                withContext(Dispatchers.Main) {
                    _volcanoPlotHtml.value = htmlContent
                    android.util.Log.d("CurtainDetailsViewModel", "Volcano plot HTML set in state")
                }
            } catch (e: Exception) {
                android.util.Log.e("CurtainDetailsViewModel", "Failed to generate volcano plot", e)
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to generate volcano plot: ${e.message}"
                }
            }
        }
    }
}
