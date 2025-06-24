package info.proteo.curtain.utils

import info.proteo.curtain.presentation.fragments.curtain.ProteinDetailListTabFragment.ConditionData
import org.json.JSONArray
import org.json.JSONObject

object PlotlyChartGenerator {
    
    fun generateBarChartHtml(
        proteinId: String,
        geneName: String,
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>
    ): String {
        val chartData = generateChartData(conditionDataList, colorMap)
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Protein Data Distribution</title>
            <style>
                body {
                    margin: 0;
                    padding: 8px;
                    font-family: Arial, sans-serif;
                    background-color: #ffffff;
                }
                #chart {
                    width: 100%;
                    height: 280px;
                }
                .chart-title {
                    text-align: center;
                    font-size: 14px;
                    font-weight: bold;
                    margin-bottom: 8px;
                    color: #333;
                }
            </style>
        </head>
        <body>
            <div class="chart-title">$geneName ($proteinId) - Raw Data Distribution</div>
            <div id="chart"></div>
            
            <script src="plotly.min.js"></script>
            <script>
                const data = $chartData;
                
                const layout = {
                    autosize: true,
                    margin: { l: 50, r: 20, t: 20, b: 100 },
                    xaxis: {
                        title: 'Samples',
                        tickangle: -45,
                        tickfont: { size: 9 },
                        automargin: true,
                        type: 'category'
                    },
                    yaxis: {
                        title: 'Intensity',
                        tickfont: { size: 10 },
                        automargin: true
                    },
                    showlegend: data.length > 1,
                    legend: {
                        orientation: 'h',
                        x: 0,
                        y: -0.25,
                        font: { size: 9 }
                    },
                    plot_bgcolor: '#ffffff',
                    paper_bgcolor: '#ffffff',
                    hovermode: 'closest'
                };
                
                const config = {
                    responsive: true,
                    displayModeBar: false,
                    staticPlot: false
                };
                
                Plotly.newPlot('chart', data, layout, config);
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun generateChartData(
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>
    ): String {
        val traces = JSONArray()
        
        // Group samples by condition and create a trace for each condition
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            val sampleNames = mutableListOf<String>()
            val sampleValues = mutableListOf<Double>()
            
            conditionData.sampleData.forEach { sample ->
                sampleNames.add(sample.sampleName)
                val value = try {
                    when {
                        sample.value == "N/A" || sample.value.isBlank() -> Double.NaN
                        sample.value.equals("inf", ignoreCase = true) -> Double.POSITIVE_INFINITY
                        sample.value.equals("-inf", ignoreCase = true) -> Double.NEGATIVE_INFINITY
                        else -> sample.value.toDoubleOrNull() ?: Double.NaN
                    }
                } catch (e: Exception) {
                    Double.NaN
                }
                sampleValues.add(value)
            }
            
            // Get color for this condition from colorMap
            val color = colorMap[condition] ?: getDefaultColor(conditionDataList.indexOf(conditionData))
            
            val trace = JSONObject().apply {
                put("x", JSONArray(sampleNames))
                put("y", JSONArray(sampleValues))
                put("type", "bar")
                put("name", condition)
                put("marker", JSONObject().apply {
                    put("color", color)
                    put("line", JSONObject().apply {
                        put("color", "rgba(0,0,0,0.3)")
                        put("width", 1)
                    })
                })
                put("hovertemplate", "<b>%{x}</b><br>Value: %{y}<br>Condition: $condition<extra></extra>")
            }
            
            traces.put(trace)
        }
        
        return traces.toString()
    }
    
    private fun getDefaultColor(index: Int): String {
        val defaultColors = listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999"
        )
        return defaultColors[index % defaultColors.size]
    }
}