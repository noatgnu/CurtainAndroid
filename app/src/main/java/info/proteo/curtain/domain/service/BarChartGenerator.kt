package info.proteo.curtain.domain.service

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.domain.model.CurtainData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarChartGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val proteomicsDataService: ProteomicsDataService
) {

    private val barChartJs: String by lazy {
        context.assets.open("js/bar-chart.js").bufferedReader().use { it.readText() }
    }

    private val barChartHtml: String by lazy {
        context.assets.open("html/bar-chart.html").bufferedReader().use { it.readText() }
    }

    suspend fun createBarChartHtml(
        curtainData: CurtainData,
        proteinId: String,
        geneName: String = proteinId,
        yAxisTitle: String = "Intensity",
        customYAxisRange: Pair<Double, Double>? = null,
        rawDataList: List<info.proteo.curtain.data.local.entity.RawProteomicsDataEntity>,
        enableImputation: Boolean = false
    ): String {
        val settings = curtainData.settings

        if (rawDataList.isEmpty()) {
            return createErrorHtml("Protein $proteinId not found in dataset")
        }

        val sampleMap = curtainData.settings.sampleMap
        val rawDataMap = rawDataList.associateBy { it.sampleName }

        val imputationMap = (settings.imputationMap[proteinId] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { true } ?: emptyMap()

        val imputationCountByCondition = mutableMapOf<String, Int>()

        val samplesByCondition = mutableMapOf<String, MutableMap<String, Double>>()
        val parsedConditions = mutableSetOf<String>()

        rawDataList.forEach { rawData ->
            val sampleName = rawData.sampleName
            val value = rawData.sampleValue ?: return@forEach

            val condition = if (sampleMap.isEmpty()) {
                val parts = sampleName.split(".")
                val parsed = parts.dropLast(1).joinToString(".")
                parsedConditions.add(parsed)
                parsed
            } else {
                sampleMap[sampleName]?.get("condition") ?: return@forEach
            }

            val isVisible = settings.sampleVisible[sampleName] ?: true
            val isImputed = imputationMap.containsKey(sampleName)

            if (isImputed) {
                imputationCountByCondition[condition] = (imputationCountByCondition[condition] ?: 0) + 1
                if (enableImputation) {
                    return@forEach
                }
            }

            if (isVisible) {
                samplesByCondition.getOrPut(condition) { mutableMapOf() }[sampleName] = value
            }
        }

        val finalConditionOrder = if (settings.conditionOrder.isNotEmpty()) {
            settings.conditionOrder
        } else {
            parsedConditions.sorted()
        }

        val colorMap = settings.colorMap.toMutableMap()
        if (sampleMap.isEmpty() && parsedConditions.isNotEmpty()) {
            var colorIndex = 0
            for (condition in finalConditionOrder) {
                if (!colorMap.containsKey(condition)) {
                    if (colorIndex >= settings.defaultColorList.size) colorIndex = 0
                    colorMap[condition] = settings.defaultColorList[colorIndex]
                    colorIndex++
                }
            }
        }

        val sampleValues = mutableMapOf<String, Double>()
        val sampleConditions = mutableMapOf<String, String>()

        for (condition in finalConditionOrder) {
            val samplesInCondition = samplesByCondition[condition] ?: continue
            val orderedSamples = settings.sampleOrder[condition] ?: samplesInCondition.keys.toList()

            for (sampleName in orderedSamples) {
                val value = samplesInCondition[sampleName] ?: continue
                sampleValues[sampleName] = value
                sampleConditions[sampleName] = condition
            }
        }

        if (sampleValues.isEmpty()) {
            return createErrorHtml("No sample data available for protein $proteinId")
        }

        val updatedSettings = if (sampleMap.isEmpty() && parsedConditions.isNotEmpty()) {
            settings.copy(colorMap = colorMap)
        } else {
            settings
        }

        val tracesAndTicks = createBarTracesAndTicks(sampleValues, sampleConditions, updatedSettings, imputationCountByCondition)
        val shapes = createBarShapes(sampleValues, sampleConditions, updatedSettings)
        val layout = createBarLayout(
            proteinId,
            geneName,
            curtainData,
            yAxisTitle,
            customYAxisRange,
            sampleValues.size,
            shapes,
            tracesAndTicks.second,
            tracesAndTicks.third
        )

        val plotData = mapOf(
            "data" to tracesAndTicks.first,
            "layout" to layout,
            "config" to mapOf(
                "displayModeBar" to true,
                "displaylogo" to false,
                "responsive" to true,
                "modeBarButtonsToRemove" to listOf("sendDataToCloud", "editInChartStudio")
            )
        )

        val plotDataJson = gson.toJson(plotData)
        val proteinName = geneName
        val proteinNameJson = gson.toJson(proteinName)

        val barChartJsWithData = barChartJs
            .replace("{{PLOT_DATA}}", plotDataJson)
            .replace("{{PROTEIN_NAME}}", proteinNameJson)
            .replace("{{EDIT_MODE}}", "false")

        val backgroundColor = "#ffffff"
        val textColor = "#000000"

        return barChartHtml
            .replace("{{BACKGROUND_COLOR}}", backgroundColor)
            .replace("{{TEXT_COLOR}}", textColor)
            .replace("{{BAR_CHART_JS}}", "<script>$barChartJsWithData</script>")
    }

    private fun createBarShapes(
        sampleValues: Map<String, Double>,
        sampleConditions: Map<String, String>,
        settings: info.proteo.curtain.domain.model.CurtainSettings
    ): List<Map<String, Any>> {
        val conditionGroups = sampleConditions.entries.groupBy { it.value }
        val shapes = mutableListOf<Map<String, Any>>()

        val totalSampleCount = sampleValues.size
        var currentSampleNumber = 0
        var previousSampleNumber = 0
        var leftConditionPos: Pair<Double, Double>? = null
        var rightConditionPos: Pair<Double, Double>? = null

        val volcanoConditionLeft = settings.volcanoConditionLabels.leftCondition
        val volcanoConditionRight = settings.volcanoConditionLabels.rightCondition

        val conditionOrder = if (settings.conditionOrder.isEmpty()) {
            conditionGroups.keys.sorted()
        } else {
            settings.conditionOrder
        }

        for (condition in conditionOrder) {
            val samples = conditionGroups[condition] ?: continue
            val sampleCount = samples.size

            previousSampleNumber = currentSampleNumber
            currentSampleNumber += sampleCount

            val isLeftCondition = volcanoConditionLeft.isNotEmpty() && condition == volcanoConditionLeft
            val isRightCondition = volcanoConditionRight.isNotEmpty() && condition == volcanoConditionRight

            if (isLeftCondition || isRightCondition) {
                val x0 = previousSampleNumber.toDouble() / totalSampleCount
                val x1 = currentSampleNumber.toDouble() / totalSampleCount

                if (settings.barChartConditionBracket.showBracket) {
                    val width = x1 - x0
                    val padding = width * 0.1
                    shapes.add(
                        mapOf(
                            "type" to "line",
                            "xref" to "paper",
                            "yref" to "paper",
                            "x0" to (x0 + padding),
                            "x1" to (x1 - padding),
                            "y0" to 1.02,
                            "y1" to 1.02,
                            "line" to mapOf(
                                "color" to settings.barChartConditionBracket.bracketColor,
                                "width" to settings.barChartConditionBracket.bracketWidth
                            )
                        )
                    )
                }

                if (isLeftCondition) {
                    leftConditionPos = Pair(x0, x1)
                }
                if (isRightCondition) {
                    rightConditionPos = Pair(x0, x1)
                }
            }

            if (totalSampleCount != currentSampleNumber) {
                shapes.add(
                    mapOf(
                        "type" to "line",
                        "xref" to "paper",
                        "yref" to "paper",
                        "x0" to (currentSampleNumber.toDouble() / totalSampleCount),
                        "x1" to (currentSampleNumber.toDouble() / totalSampleCount),
                        "y0" to 0,
                        "y1" to 1,
                        "line" to mapOf(
                            "dash" to "dash"
                        )
                    )
                )
            }
        }

        if (leftConditionPos != null && rightConditionPos != null && settings.barChartConditionBracket.showBracket) {
            val bracketY = 1.02 + settings.barChartConditionBracket.bracketHeight
            val leftMidX = (leftConditionPos.first + leftConditionPos.second) / 2
            val rightMidX = (rightConditionPos.first + rightConditionPos.second) / 2

            shapes.add(
                mapOf(
                    "type" to "line",
                    "xref" to "paper",
                    "yref" to "paper",
                    "x0" to leftMidX,
                    "x1" to leftMidX,
                    "y0" to 1.02,
                    "y1" to bracketY,
                    "line" to mapOf(
                        "color" to settings.barChartConditionBracket.bracketColor,
                        "width" to settings.barChartConditionBracket.bracketWidth
                    )
                )
            )
            shapes.add(
                mapOf(
                    "type" to "line",
                    "xref" to "paper",
                    "yref" to "paper",
                    "x0" to leftMidX,
                    "x1" to rightMidX,
                    "y0" to bracketY,
                    "y1" to bracketY,
                    "line" to mapOf(
                        "color" to settings.barChartConditionBracket.bracketColor,
                        "width" to settings.barChartConditionBracket.bracketWidth
                    )
                )
            )
            shapes.add(
                mapOf(
                    "type" to "line",
                    "xref" to "paper",
                    "yref" to "paper",
                    "x0" to rightMidX,
                    "x1" to rightMidX,
                    "y0" to bracketY,
                    "y1" to 1.02,
                    "line" to mapOf(
                        "color" to settings.barChartConditionBracket.bracketColor,
                        "width" to settings.barChartConditionBracket.bracketWidth
                    )
                )
            )
        }

        return shapes
    }

    private fun createBarTracesAndTicks(
        sampleValues: Map<String, Double>,
        sampleConditions: Map<String, String>,
        settings: info.proteo.curtain.domain.model.CurtainSettings,
        imputationCountByCondition: Map<String, Int> = emptyMap()
    ): Triple<List<Map<String, Any>>, List<String>, List<String>> {
        val conditionGroups = sampleConditions.entries.groupBy { it.value }

        val traces = mutableListOf<Map<String, Any>>()
        val tickvals = mutableListOf<String>()
        val ticktext = mutableListOf<String>()

        val conditionOrder = if (settings.conditionOrder.isEmpty()) {
            conditionGroups.keys.sorted()
        } else {
            settings.conditionOrder
        }

        for (condition in conditionOrder) {
            val samples = conditionGroups[condition] ?: continue

            val xValues = samples.map { it.key }
            val yValues = samples.mapNotNull { sampleValues[it.key] }

            val barchartColorMap = settings.barchartColorMap as? Map<String, String> ?: emptyMap()
            val color = barchartColorMap[condition] ?: settings.colorMap[condition] ?: getDefaultColor(condition)

            traces.add(
                mapOf(
                    "x" to xValues,
                    "y" to yValues,
                    "type" to "bar",
                    "name" to condition,
                    "marker" to mapOf(
                        "color" to color,
                        "line" to mapOf(
                            "color" to "rgba(0,0,0,0.3)",
                            "width" to 1
                        )
                    ),
                    "showlegend" to false,
                    "hovertemplate" to "<b>%{x}</b><br>$condition<br>Intensity: %{y:.2f}<extra></extra>"
                )
            )

            val middleIndex = (xValues.size / 2).coerceAtLeast(0)
            if (xValues.isNotEmpty()) {
                tickvals.add(xValues[middleIndex])
                val imputedCount = imputationCountByCondition[condition] ?: 0
                val label = if (imputedCount > 0) {
                    "$condition ($imputedCount imputed)"
                } else {
                    condition
                }
                ticktext.add(label)
            }
        }

        return Triple(traces, tickvals, ticktext)
    }

    private fun createBarLayout(
        proteinId: String,
        geneName: String,
        curtainData: CurtainData,
        yAxisTitle: String,
        customYAxisRange: Pair<Double, Double>?,
        sampleCount: Int,
        shapes: List<Map<String, Any>>,
        tickvals: List<String>,
        ticktext: List<String>
    ): Map<String, Any> {
        val title = geneName
        val settings = curtainData.settings

        val margin = mapOf(
            "r" to 50,
            "l" to 100,
            "b" to 100,
            "t" to 100
        )

        val xaxis = mutableMapOf<String, Any>(
            "title" to "Samples",
            "showgrid" to true,
            "tickangle" to -45,
            "fixedrange" to true,
            "tickfont" to mapOf(
                "size" to 17
            ),
            "tickvals" to tickvals,
            "ticktext" to ticktext
        )

        val yaxis = mutableMapOf<String, Any>(
            "title" to yAxisTitle,
            "showgrid" to true,
            "zeroline" to true,
            "fixedrange" to true,
            "tickfont" to mapOf(
                "size" to 17
            )
        )

        var minY: Double? = null
        var maxY: Double? = null

        settings.chartYAxisLimits["barChart"]?.let { limits ->
            minY = limits.min
            maxY = limits.max
        }

        (settings.individualYAxisLimits[proteinId] as? Map<*, *>)?.let { proteinLimits ->
            (proteinLimits["barChart"] as? Map<*, *>)?.let { chartLimits ->
                (chartLimits["min"] as? Number)?.toDouble()?.let { min -> minY = min }
                (chartLimits["max"] as? Number)?.toDouble()?.let { max -> maxY = max }
            }
        }

        customYAxisRange?.let { (min, max) ->
            minY = min
            maxY = max
        }

        if (minY != null || maxY != null) {
            yaxis["range"] = listOf(minY ?: 0, maxY ?: 0)
            yaxis["autorange"] = false
        } else {
            yaxis["autorange"] = true
        }

        val layout = mutableMapOf<String, Any>(
            "title" to mapOf(
                "text" to title,
                "font" to mapOf(
                    "family" to settings.plotFontFamily,
                    "size" to 16
                )
            ),
            "xaxis" to xaxis,
            "yaxis" to yaxis,
            "showlegend" to false,
            "hovermode" to "closest",
            "plot_bgcolor" to "rgba(0,0,0,0)",
            "paper_bgcolor" to "rgba(0,0,0,0)",
            "font" to mapOf(
                "family" to settings.plotFontFamily
            ),
            "margin" to margin,
            "barmode" to "group",
            "shapes" to shapes
        )

        return layout
    }

    private fun getDefaultColor(condition: String): String {
        val colors = listOf(
            "#2196F3",
            "#f44336",
            "#4CAF50",
            "#FF9800",
            "#9C27B0",
            "#00BCD4",
            "#FFEB3B",
            "#795548"
        )
        val hash = condition.hashCode()
        val index = (hash % colors.size).let { if (it < 0) it + colors.size else it }
        return colors[index]
    }

    private fun createErrorHtml(errorMessage: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background-color: #f5f5f5;
                    }
                    .error {
                        text-align: center;
                        padding: 20px;
                        background-color: white;
                        border-radius: 8px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                        max-width: 80%;
                    }
                    .error h3 {
                        color: #d32f2f;
                        margin-bottom: 10px;
                    }
                </style>
            </head>
            <body>
                <div class="error">
                    <h3>Error</h3>
                    <p>$errorMessage</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
