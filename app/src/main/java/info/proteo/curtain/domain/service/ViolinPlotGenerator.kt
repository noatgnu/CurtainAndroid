package info.proteo.curtain.domain.service

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import info.proteo.curtain.domain.model.CurtainData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViolinPlotGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val proteomicsDataService: ProteomicsDataService
) {

    private val violinPlotJs: String by lazy {
        context.assets.open("js/violin-plot.js").bufferedReader().use { it.readText() }
    }

    private val violinPlotHtml: String by lazy {
        context.assets.open("html/violin-plot.html").bufferedReader().use { it.readText() }
    }

    suspend fun createViolinPlotHtml(
        curtainData: CurtainData,
        proteinId: String,
        geneName: String = proteinId,
        yAxisTitle: String = "Intensity",
        showBox: Boolean = true,
        showPoints: Boolean = true,
        pointJitter: Double = 0.3,
        customYAxisRange: Pair<Double, Double>? = null,
        rawDataList: List<info.proteo.curtain.data.local.entity.RawProteomicsDataEntity>
    ): String {
        val settings = curtainData.settings
        if (rawDataList.isEmpty()) {
            return createErrorHtml("Protein $proteinId not found in dataset")
        }

        val sampleMap = curtainData.settings.sampleMap
        val sampleVisible = curtainData.settings.sampleVisible

        val conditionValuesTmp = mutableMapOf<String, MutableList<Double>>()
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

            if (isVisible) {
                conditionValuesTmp.getOrPut(condition) { mutableListOf() }.add(value)
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

        val conditionValues = mutableMapOf<String, MutableList<Double>>()
        for (condition in finalConditionOrder) {
            conditionValuesTmp[condition]?.let { values ->
                conditionValues[condition] = values
            }
        }

        if (conditionValues.isEmpty()) {
            return createErrorHtml("No sample data available for protein $proteinId")
        }

        val updatedSettings = if (sampleMap.isEmpty() && parsedConditions.isNotEmpty()) {
            settings.copy(colorMap = colorMap)
        } else {
            settings
        }

        val traces = createViolinTraces(
            conditionValues,
            updatedSettings,
            finalConditionOrder,
            showBox,
            showPoints,
            pointJitter
        )

        val layout = createViolinLayout(
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

        val violinPlotJsWithData = violinPlotJs
            .replace("{{PLOT_DATA}}", plotDataJson)
            .replace("{{PROTEIN_NAME}}", proteinNameJson)

        val backgroundColor = "#ffffff"
        val textColor = "#000000"

        return violinPlotHtml
            .replace("{{BACKGROUND_COLOR}}", backgroundColor)
            .replace("{{TEXT_COLOR}}", textColor)
            .replace("{{VIOLIN_PLOT_JS}}", "<script>$violinPlotJsWithData</script>")
    }

    private fun createViolinTraces(
        conditionValues: Map<String, List<Double>>,
        settings: info.proteo.curtain.domain.model.CurtainSettings,
        conditionOrder: List<String>,
        showBox: Boolean,
        showPoints: Boolean,
        pointJitter: Double
    ): List<Map<String, Any>> {
        val traces = mutableListOf<Map<String, Any>>()
        val pointColor = "#654949"
        val barchartColorMap = settings.barchartColorMap as? Map<String, String> ?: emptyMap()

        for (condition in conditionOrder) {
            val values = conditionValues[condition] ?: continue
            val color = barchartColorMap[condition] ?: settings.colorMap[condition] ?: getDefaultColor(condition)

            traces.add(
                mapOf(
                    "y" to values,
                    "x" to List(values.size) { condition },
                    "type" to "violin",
                    "name" to condition,
                    "box" to mapOf(
                        "visible" to showBox
                    ),
                    "points" to if (showPoints) "all" else false,
                    "pointpos" to pointJitter,
                    "jitter" to 0.3,
                    "scalemode" to "width",
                    "meanline" to mapOf(
                        "visible" to true
                    ),
                    "marker" to mapOf(
                        "size" to 4,
                        "opacity" to 0.8,
                        "color" to pointColor
                    ),
                    "line" to mapOf(
                        "color" to color,
                        "width" to 2
                    ),
                    "fillcolor" to addAlpha(color, 0.5),
                    "hovertemplate" to "<b>$condition</b><br>Value: %{y:.2f}<extra></extra>"
                )
            )
        }

        return traces
    }

    private fun createViolinLayout(
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
            "violinmode" to "group"
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

    private fun addAlpha(hexColor: String, alpha: Double): String {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return "rgba($r,$g,$b,$alpha)"
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
