package info.proteo.curtain.domain.service

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.domain.model.CurtainData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

data class ConditionStats(
    val condition: String,
    val mean: Double,
    val stdDev: Double,
    val stdError: Double,
    val values: List<Double>,
    val sampleCount: Int
)

@Singleton
class AverageBarChartGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val proteomicsDataService: ProteomicsDataService
) {

    private val averageBarChartJs: String by lazy {
        context.assets.open("js/average-bar-chart.js").bufferedReader().use { it.readText() }
    }

    private val averageBarChartHtml: String by lazy {
        context.assets.open("html/average-bar-chart.html").bufferedReader().use { it.readText() }
    }

    suspend fun createAverageBarChartHtml(
        curtainData: CurtainData,
        proteinId: String,
        geneName: String = proteinId,
        yAxisTitle: String = "Mean Intensity",
        showIndividualPoints: Boolean = true,
        useStandardError: Boolean = true,
        customYAxisRange: Pair<Double, Double>? = null,
        rawDataList: List<info.proteo.curtain.data.local.entity.RawProteomicsDataEntity>,
        enableImputation: Boolean = false
    ): String {
        val settings = curtainData.settings
        if (rawDataList.isEmpty()) {
            return createErrorHtml("Protein $proteinId not found in dataset")
        }

        val imputationMap = (settings.imputationMap[proteinId] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { true } ?: emptyMap()

        val sampleMap = curtainData.settings.sampleMap
        val sampleVisible = curtainData.settings.sampleVisible

        data class SampleData(val value: Double, val sampleName: String, val isImputed: Boolean)

        val conditionSamplesTmp = mutableMapOf<String, MutableList<SampleData>>()
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

            val isVisible = sampleVisible[sampleName] ?: true
            val isImputed = imputationMap.containsKey(sampleName)

            if (isImputed && enableImputation) {
                return@forEach
            }

            if (isVisible) {
                conditionSamplesTmp.getOrPut(condition) { mutableListOf() }.add(
                    SampleData(value, sampleName, isImputed)
                )
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

        val conditionSamples = mutableMapOf<String, MutableList<SampleData>>()
        for (condition in finalConditionOrder) {
            conditionSamplesTmp[condition]?.let { samples ->
                conditionSamples[condition] = samples
            }
        }

        if (conditionSamples.isEmpty()) {
            return createErrorHtml("No sample data available for protein $proteinId")
        }

        val conditionStats = conditionSamples.map { (condition, samples) ->
            calculateStats(condition, samples.map { it.value })
        }

        val updatedSettings = if (sampleMap.isEmpty() && parsedConditions.isNotEmpty()) {
            settings.copy(colorMap = colorMap)
        } else {
            settings
        }

        val conditionSamplesMap = conditionSamples.mapValues { (_, samples) ->
            samples.map { sample ->
                mapOf(
                    "value" to sample.value,
                    "sampleName" to sample.sampleName,
                    "isImputed" to sample.isImputed
                )
            }
        }

        val traces = createAverageBarTraces(
            conditionStats,
            conditionSamplesMap,
            updatedSettings,
            showIndividualPoints,
            useStandardError
        )

        val layout = createAverageBarLayout(
            proteinId,
            geneName,
            curtainData,
            yAxisTitle,
            customYAxisRange
        )

        val plotData = mapOf(
            "data" to traces,
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

        val averageBarChartJsWithData = averageBarChartJs
            .replace("{{PLOT_DATA}}", plotDataJson)
            .replace("{{PROTEIN_NAME}}", proteinNameJson)
            .replace("{{SHOW_INDIVIDUAL_POINTS}}", showIndividualPoints.toString())

        val backgroundColor = "#ffffff"
        val textColor = "#000000"

        return averageBarChartHtml
            .replace("{{BACKGROUND_COLOR}}", backgroundColor)
            .replace("{{TEXT_COLOR}}", textColor)
            .replace("{{AVERAGE_BAR_CHART_JS}}", "<script>$averageBarChartJsWithData</script>")
    }

    private fun calculateStats(condition: String, values: List<Double>): ConditionStats {
        val n = values.size
        val mean = values.average()

        val variance = values.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)
        val stdError = stdDev / sqrt(n.toDouble())

        return ConditionStats(
            condition = condition,
            mean = mean,
            stdDev = stdDev,
            stdError = stdError,
            values = values,
            sampleCount = n
        )
    }

    private fun createAverageBarTraces(
        conditionStats: List<ConditionStats>,
        conditionSamples: Map<String, List<Any>>,
        settings: info.proteo.curtain.domain.model.CurtainSettings,
        showIndividualPoints: Boolean,
        useStandardError: Boolean
    ): List<Map<String, Any>> {
        val traces = mutableListOf<Map<String, Any>>()

        val conditions = conditionStats.map { it.condition }
        val means = conditionStats.map { it.mean }
        val errors = conditionStats.map { if (useStandardError) it.stdError else it.stdDev }

        val barchartColorMap = settings.barchartColorMap as? Map<String, String> ?: emptyMap()
        val colors = conditions.map { condition ->
            barchartColorMap[condition] ?: settings.colorMap[condition] ?: getDefaultColor(condition)
        }

        traces.add(
            mapOf(
                "x" to conditions,
                "y" to means,
                "type" to "bar",
                "name" to "Mean ± ${if (useStandardError) "SE" else "SD"}",
                "marker" to mapOf(
                    "color" to colors,
                    "line" to mapOf(
                        "color" to "rgba(0,0,0,0.3)",
                        "width" to 1
                    )
                ),
                "error_y" to mapOf(
                    "type" to "data",
                    "array" to errors,
                    "visible" to true,
                    "color" to "#000000",
                    "thickness" to 2,
                    "width" to 4
                ),
                "showlegend" to false,
                "hovertemplate" to "<b>%{x}</b><br>Mean: %{y:.2f}<br>N: ${conditionStats.map { it.sampleCount }}<extra></extra>"
            )
        )

        if (showIndividualPoints) {
            val pointColor = "#654949"
            val transparentColor = "rgba(0,0,0,0)"

            conditionStats.forEach { stats ->
                val samples = conditionSamples[stats.condition] ?: emptyList()

                data class SampleData(val value: Double, val sampleName: String, val isImputed: Boolean)

                val sampleDataList = samples.mapNotNull { sample ->
                    @Suppress("UNCHECKED_CAST")
                    val map = sample as? Map<String, Any>
                    val value = (map?.get("value") as? Number)?.toDouble()
                    val sampleName = map?.get("sampleName") as? String
                    val isImputed = map?.get("isImputed") as? Boolean
                    if (value != null && sampleName != null && isImputed != null) {
                        SampleData(value, sampleName, isImputed)
                    } else null
                }

                val markerColors = sampleDataList.map { if (it.isImputed) transparentColor else pointColor }

                traces.add(
                    mapOf(
                        "x" to List(sampleDataList.size) { stats.condition },
                        "y" to sampleDataList.map { it.value },
                        "type" to "scatter",
                        "mode" to "markers",
                        "name" to "${stats.condition} (n=${stats.sampleCount})",
                        "marker" to mapOf(
                            "size" to 8,
                            "color" to markerColors,
                            "opacity" to 0.8,
                            "line" to mapOf(
                                "color" to pointColor,
                                "width" to 1
                            )
                        ),
                        "showlegend" to false,
                        "hovertemplate" to "<b>%{x}</b><br>Value: %{y:.2f}<extra></extra>"
                    )
                )
            }
        }

        return traces
    }

    private fun createAverageBarLayout(
        proteinId: String,
        geneName: String,
        curtainData: CurtainData,
        yAxisTitle: String,
        customYAxisRange: Pair<Double, Double>?
    ): Map<String, Any> {
        val title = geneName

        val settings = curtainData.settings

        val layout = mutableMapOf<String, Any>(
            "title" to mapOf(
                "text" to title,
                "font" to mapOf(
                    "family" to settings.plotFontFamily,
                    "size" to 16
                )
            ),
            "xaxis" to mapOf(
                "title" to "Conditions",
                "showgrid" to true
            ),
            "yaxis" to mapOf(
                "title" to yAxisTitle,
                "showgrid" to true,
                "zeroline" to true
            ),
            "showlegend" to false,
            "hovermode" to "closest",
            "plot_bgcolor" to "rgba(0,0,0,0)",
            "paper_bgcolor" to "rgba(0,0,0,0)",
            "font" to mapOf(
                "family" to settings.plotFontFamily
            ),
            "barmode" to "group"
        )

        customYAxisRange?.let { (minY, maxY) ->
            (layout["yaxis"] as MutableMap<String, Any>)["range"] = listOf(minY, maxY)
        }

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
