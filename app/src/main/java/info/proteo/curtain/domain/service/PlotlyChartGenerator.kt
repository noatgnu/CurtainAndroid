package info.proteo.curtain.domain.service

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.CurtainSettings
import info.proteo.curtain.domain.model.VolcanoAxis
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

@Singleton
class PlotlyChartGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val volcanoPlotDataService: VolcanoPlotDataService
) {

    private val volcanoPlotJs: String by lazy {
        context.assets.open("js/volcano-plot.js").bufferedReader().use { it.readText() }
    }

    private val volcanoPlotHtml: String by lazy {
        context.assets.open("html/volcano-plot.html").bufferedReader().use { it.readText() }
    }

    private val _lastGeneratedTraces = mutableListOf<info.proteo.curtain.domain.model.TraceData>()
    val lastGeneratedTraces: List<info.proteo.curtain.domain.model.TraceData>
        get() = _lastGeneratedTraces.toList()

    suspend fun createVolcanoPlotHtml(curtainData: CurtainData): String {
        val settings = curtainData.settings
        val volcanoResult = volcanoPlotDataService.processVolcanoData(curtainData, settings)
        val plotData = createAndroidCompatiblePlotData(volcanoResult, settings, curtainData.selectionsName ?: emptyList())

        val plotDataJson = try {
            val json = gson.toJson(plotData)
            if (json.contains("NaN") || json.contains("Infinity") || json.contains("-Infinity")) {
                android.util.Log.e("PlotlyChartGenerator", "JSON contains invalid values")
                throw IllegalStateException("JSON contains NaN or Infinity values")
            }
            json.replace("</script>", "<\\/script>", ignoreCase = true)
        } catch (e: Exception) {
            android.util.Log.e("PlotlyChartGenerator", "Error serializing plot data", e)
            throw IllegalStateException("Failed to serialize plot data: ${e.message}", e)
        }

        val editMode = false
        val volcanoJs = volcanoPlotJs
            .replace("{{PLOT_DATA}}", plotDataJson)
            .replace("{{EDIT_MODE}}", editMode.toString())

        val backgroundColor = "#ffffff" 
        val textColor = "#000000"

        return volcanoPlotHtml
            .replace("{{BACKGROUND_COLOR}}", backgroundColor)
            .replace("{{TEXT_COLOR}}", textColor)
            .replace("{{VOLCANO_PLOT_JS}}", volcanoJs)
    }

    private fun createAndroidCompatiblePlotData(
        volcanoResult: VolcanoPlotDataService.VolcanoProcessResult,
        settings: CurtainSettings,
        selectionsName: List<String>
    ): Map<String, Any> {
        val traces = createAndroidCompatibleTraces(volcanoResult.jsonData, settings, selectionsName)
        val layout = createAndroidCompatibleLayout(volcanoResult, settings)
        val config = createDefaultPlotConfig()

        return mapOf(
            "data" to traces,
            "layout" to layout,
            "config" to config
        )
    }

    private fun createAndroidCompatibleTraces(
        jsonData: List<Map<String, Any>>,
        settings: CurtainSettings,
        selectionsName: List<String>
    ): List<Map<String, Any>> {
        val selectionGroups = mutableMapOf<String, Pair<String, MutableList<Map<String, Any>>>>()

        for (dataPoint in jsonData) {
            @Suppress("UNCHECKED_CAST")
            val selections = dataPoint["selections"] as? List<String> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val colors = dataPoint["colors"] as? List<String> ?: emptyList()

            selections.forEachIndexed {
                index, selectionName ->
                val selectionColor = if (index < colors.size) colors[index] else "#808080"
                if (!selectionGroups.containsKey(selectionName)) {
                    selectionGroups[selectionName] = Pair(selectionColor, mutableListOf())
                }
                selectionGroups[selectionName]?.second?.add(dataPoint)
            }
        }

        val traces = mutableListOf<Map<String, Any>>()
        val allGroupNames = selectionGroups.keys.toList()

        val userSelectionNames = if (selectionsName.isNotEmpty()) {
            selectionsName.filter { selectionGroups.containsKey(it) }
        } else {
            allGroupNames.filter {
                selectionName ->
                selectionName != "Background" &&
                selectionName != "Other" &&
                !selectionName.contains("P-value") &&
                !selectionName.contains("FC")
            }.sorted()
        }

        for (selectionName in userSelectionNames) {
            val groupData = selectionGroups[selectionName] ?: continue
            val trace = createAndroidCompatibleTrace(
                dataPoints = groupData.second,
                name = selectionName,
                color = groupData.first,
                markerSize = getMarkerSize(selectionName, settings)
            )
            traces.add(trace)
        }

        val backgroundAndSignificanceNames = allGroupNames.filter {
            selectionName ->
            selectionName == "Background" ||
            selectionName == "Other" ||
            selectionName.contains("P-value") ||
            selectionName.contains("FC")
        }.sorted()

        for (selectionName in backgroundAndSignificanceNames) {
            val groupData = selectionGroups[selectionName] ?: continue
            val trace = createAndroidCompatibleTrace(
                dataPoints = groupData.second,
                name = selectionName,
                color = groupData.first,
                markerSize = getMarkerSize(selectionName, settings)
            )
            traces.add(trace)
        }

        val sortedTraces = reorderTraces(traces, settings.volcanoTraceOrder)
        val finalTraces = if (settings.volcanoTraceOrder.isEmpty()) {
            sortedTraces.asReversed()
        } else {
            sortedTraces
        }

        _lastGeneratedTraces.clear()
        _lastGeneratedTraces.addAll(
            finalTraces.mapIndexed { index, trace ->
                info.proteo.curtain.domain.model.TraceData(
                    name = trace["name"] as String,
                    color = getTraceColor(trace),
                    originalIndex = index
                )
            }
        )

        return finalTraces
    }

    private fun getTraceColor(trace: Map<String, Any>): String {
        val marker = trace["marker"] as? Map<*, *>
        val color = marker?.get("color") as? String
        return color ?: "#999999"
    }

    private fun createAndroidCompatibleTrace(
        dataPoints: List<Map<String, Any>>,
        name: String,
        color: String,
        markerSize: Double
    ): Map<String, Any> {
        val xValues = dataPoints.map { (it["x"] as Number).toDouble() }
        val yValues = dataPoints.map { (it["y"] as Number).toDouble() }
        
        val textValues = dataPoints.map {
            point ->
            val customText = point["customText"] as? String
            if (!customText.isNullOrEmpty()) {
                return@map customText
            }
            val geneName = (point["gene"] as? String)?.trim() ?: ""
            val primaryId = (point["id"] as? String)?.trim() ?: ""
            if (geneName.isNotEmpty() && geneName != primaryId) {
                "$geneName($primaryId)"
            } else {
                primaryId
            }
        }

        val customData = dataPoints.map {
            point ->
            mapOf(
                "id" to point["id"],
                "gene" to point["gene"],
                "comparison" to point["comparison"],
                "x" to point["x"],
                "y" to point["y"],
                "pValue" to 10.0.pow(-(point["y"] as Number).toDouble()),
                "selections" to point["selections"],
                "colors" to point["colors"]
            )
        }

        return mapOf(
            "x" to xValues,
            "y" to yValues,
            "mode" to "markers",
            "type" to "scatter",
            "name" to name,
            "text" to textValues,
            "customdata" to customData,
            "marker" to mapOf(
                "size" to markerSize,
                "color" to color,
                "symbol" to "circle",
                "line" to mapOf(
                    "color" to "white",
                    "width" to 0.5
                )
            ),
            "hovertemplate" to "<b>%{text}</b><br>Log2FC: %{x:.3f}<br>-Log10(p-value): %{y:.3f}<br>p-value: %{customdata.pValue:.2e}<extra></extra>"
        )
    }

    private fun createAndroidCompatibleLayout(
        volcanoResult: VolcanoPlotDataService.VolcanoProcessResult,
        settings: CurtainSettings
    ): Map<String, Any> {
        val volcanoAxis = volcanoResult.updatedVolcanoAxis
        val textColor = "#000000"
        val gridColor = "#e0e0e0"

        val xaxisZerolineColor = if (settings.volcanoPlotYaxisPosition.contains("middle")) "#000000" else "rgba(0,0,0,0)"

        val shapes = createAndroidCompatibleThresholdShapes(settings, volcanoAxis).toMutableList()

        if (settings.volcanoPlotYaxisPosition.contains("left")) {
             shapes.add(mapOf(
                "type" to "line",
                "x0" to (volcanoAxis.minX ?: -3.0),
                "x1" to (volcanoAxis.minX ?: -3.0),
                "y0" to (volcanoAxis.minY ?: 0.0),
                "y1" to (volcanoAxis.maxY ?: 5.0),
                "xref" to "x",
                "yref" to "y",
                "line" to mapOf("color" to textColor, "width" to 1)
            ))
        }

        val annotations = convertTextAnnotations(settings.textAnnotation, false).toMutableList()
        annotations.addAll(createVolcanoConditionLabelAnnotations(settings, false))

        return mapOf(
            "title" to mapOf(
                "text" to settings.volcanoPlotTitle,
                "font" to mapOf(
                    "family" to settings.plotFontFamily,
                    "size" to 16,
                    "color" to textColor
                )
            ),
            "xaxis" to mapOf(
                "title" to mapOf(
                    "text" to volcanoAxis.x,
                    "font" to mapOf("family" to settings.plotFontFamily, "size" to 12, "color" to textColor)
                ),
                "zeroline" to true,
                "zerolinecolor" to xaxisZerolineColor,
                "gridcolor" to gridColor,
                "linecolor" to textColor,
                "range" to listOf(volcanoAxis.minX ?: -3.0, volcanoAxis.maxX ?: 3.0),
                "tickfont" to mapOf("family" to settings.plotFontFamily, "size" to 10, "color" to textColor),
                "showgrid" to (settings.volcanoPlotGrid["x"] ?: true),
                "automargin" to true
            ),
            "yaxis" to mapOf(
                "title" to mapOf(
                    "text" to volcanoAxis.y,
                    "font" to mapOf("family" to settings.plotFontFamily, "size" to 12, "color" to textColor)
                ),
                "zeroline" to false,
                "showline" to false,
                "gridcolor" to gridColor,
                "linecolor" to textColor,
                "range" to listOf(volcanoAxis.minY ?: 0.0, volcanoAxis.maxY ?: 5.0),
                "tickfont" to mapOf("family" to settings.plotFontFamily, "size" to 10, "color" to textColor),
                "showgrid" to (settings.volcanoPlotGrid["y"] ?: true),
                "automargin" to true
            ),
            "autosize" to true,
            "useResizeHandler" to true,
            "hovermode" to "closest",
            "showlegend" to true,
            "plot_bgcolor" to "rgba(0,0,0,0)",
            "paper_bgcolor" to "rgba(0,0,0,0)",
            "font" to mapOf("family" to settings.plotFontFamily, "size" to 12, "color" to textColor),
            "shapes" to shapes,
            "annotations" to annotations,
            "legend" to mapOf(
                "orientation" to "h",
                "x" to 0.5,
                "xanchor" to "center",
                "y" to (settings.volcanoPlotLegendY ?: -0.15),
                "yanchor" to "top"
            ),
            "margin" to buildMarginMap(settings)
        )
    }

    private fun buildMarginMap(settings: CurtainSettings): Map<String, Any?> {
        val margin = settings.volcanoPlotDimension.margin
        return mapOf(
            "l" to margin.left,
            "r" to margin.right,
            "b" to margin.bottom,
            "t" to margin.top
        )
    }

    private fun createAndroidCompatibleThresholdShapes(settings: CurtainSettings, volcanoAxis: VolcanoAxis): List<Map<String, Any>> {
        val maxY = volcanoAxis.maxY ?: 5.0
        val minX = volcanoAxis.minX ?: -3.0
        val maxX = volcanoAxis.maxX ?: 3.0
        val pValueThreshold = -log10(settings.pCutoff)

        return listOf(
            mapOf(
                "type" to "line",
                "x0" to -settings.log2FCCutoff,
                "x1" to -settings.log2FCCutoff,
                "y0" to 0,
                "y1" to maxY,
                "xref" to "x",
                "yref" to "y",
                "line" to mapOf("color" to "rgb(21,4,4)", "width" to 1, "dash" to "dash")
            ),
            mapOf(
                "type" to "line",
                "x0" to settings.log2FCCutoff,
                "x1" to settings.log2FCCutoff,
                "y0" to 0,
                "y1" to maxY,
                "xref" to "x",
                "yref" to "y",
                "line" to mapOf("color" to "rgb(21,4,4)", "width" to 1, "dash" to "dash")
            ),
            mapOf(
                "type" to "line",
                "x0" to minX,
                "x1" to maxX,
                "y0" to pValueThreshold,
                "y1" to pValueThreshold,
                "xref" to "x",
                "yref" to "y",
                "line" to mapOf("color" to "rgb(21,4,4)", "width" to 1, "dash" to "dash")
            )
        )
    }
    
    private fun convertTextAnnotations(textAnnotations: Map<String, Any>, isDarkMode: Boolean): List<Map<String, Any>> {
        val annotations = mutableListOf<Map<String, Any>>()

        for ((key, value) in textAnnotations) {
            val annotationData = value as? Map<String, Any> ?: continue
            val dataSection = annotationData["data"] as? Map<String, Any> ?: continue

            val x = dataSection["x"] as? Double ?: continue
            val y = dataSection["y"] as? Double ?: continue
            val text = dataSection["text"] as? String ?: continue

            val showarrow = dataSection["showarrow"] as? Boolean ?: true
            val arrowhead = dataSection["arrowhead"] as? Number
            val arrowsize = dataSection["arrowsize"] as? Number
            val arrowwidth = dataSection["arrowwidth"] as? Number
            val arrowcolor = dataSection["arrowcolor"] as? String
            val ax = dataSection["ax"] as? Number
            val ay = dataSection["ay"] as? Number
            val xanchor = dataSection["xanchor"] as? String
            val yanchor = dataSection["yanchor"] as? String

            val fontData = dataSection["font"] as? Map<String, Any>
            val fontFamily = fontData?.get("family") as? String ?: "Arial"
            val fontSize = (fontData?.get("size") as? Number)?.toDouble() ?: 12.0
            val fontColor = fontData?.get("color") as? String ?: if (isDarkMode) "#FFFFFF" else "#000000"

            val annotation = mutableMapOf<String, Any>(
                "x" to x,
                "y" to y,
                "text" to text,
                "showarrow" to showarrow,
                "font" to mapOf(
                    "family" to fontFamily,
                    "size" to fontSize,
                    "color" to fontColor
                )
            )

            xanchor?.let { annotation["xanchor"] = it }
            yanchor?.let { annotation["yanchor"] = it }
            arrowhead?.let { annotation["arrowhead"] = it.toInt() }
            arrowsize?.let { annotation["arrowsize"] = it.toDouble() }
            arrowwidth?.let { annotation["arrowwidth"] = it.toDouble() }
            arrowcolor?.let { annotation["arrowcolor"] = it }
            ax?.let { annotation["ax"] = it.toDouble() }
            ay?.let { annotation["ay"] = it.toDouble() }

            annotations.add(annotation)
        }

        return annotations
    }

    private fun createVolcanoConditionLabelAnnotations(settings: CurtainSettings, isDarkMode: Boolean): List<Map<String, Any>> {
        if (!settings.volcanoConditionLabels.enabled) return emptyList()

        val leftCondition = settings.volcanoConditionLabels.leftCondition
        val rightCondition = settings.volcanoConditionLabels.rightCondition

        if (leftCondition.isEmpty() || rightCondition.isEmpty() || leftCondition == rightCondition) return emptyList()

        val fontColor = if (isDarkMode && (settings.volcanoConditionLabels.fontColor == "#000000" || settings.volcanoConditionLabels.fontColor == "black")) "#FFFFFF" else settings.volcanoConditionLabels.fontColor
        val fontSize = settings.volcanoConditionLabels.fontSize

        val leftAnnotation = mapOf(
            "x" to settings.volcanoConditionLabels.leftX,
            "y" to settings.volcanoConditionLabels.yPosition,
            "xref" to "paper",
            "yref" to "paper",
            "text" to leftCondition,
            "showarrow" to false,
            "font" to mapOf("family" to settings.plotFontFamily, "size" to fontSize, "color" to fontColor),
            "xanchor" to "center",
            "yanchor" to "top"
        )

        val rightAnnotation = mapOf(
            "x" to settings.volcanoConditionLabels.rightX,
            "y" to settings.volcanoConditionLabels.yPosition,
            "xref" to "paper",
            "yref" to "paper",
            "text" to rightCondition,
            "showarrow" to false,
            "font" to mapOf("family" to settings.plotFontFamily, "size" to fontSize, "color" to fontColor),
            "xanchor" to "center",
            "yanchor" to "top"
        )

        return listOf(leftAnnotation, rightAnnotation)
    }

    private fun createDefaultPlotConfig(): Map<String, Any> {
        return mapOf(
            "responsive" to true,
            "displayModeBar" to false,
            "editable" to false,
            "scrollZoom" to true,
            "doubleClick" to "reset"
        )
    }

    private fun getMarkerSize(groupName: String, settings: CurtainSettings): Double {
        val customSize = settings.markerSizeMap[groupName]
        return when (customSize) {
            is Number -> customSize.toDouble()
            is String -> customSize.toDoubleOrNull() ?: settings.scatterPlotMarkerSize
            else -> settings.scatterPlotMarkerSize
        }
    }

    private fun reorderTraces(traces: List<Map<String, Any>>, order: List<String>): List<Map<String, Any>> {
        if (order.isEmpty()) {
            return traces
        }

        val tracesByName = traces.associateBy { it["name"] as String }.toMutableMap()
        val reordered = mutableListOf<Map<String, Any>>()

        for (name in order) {
            tracesByName.remove(name)?.let { reordered.add(it) }
        }

        reordered.addAll(tracesByName.values)
        return reordered
    }
}
