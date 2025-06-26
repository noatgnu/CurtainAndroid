package info.proteo.curtain.utils

import info.proteo.curtain.presentation.fragments.curtain.ProteinDetailListTabFragment.ConditionData
import org.json.JSONArray
import org.json.JSONObject

object PlotlyChartGenerator {
    
    fun generateBarChartHtml(
        proteinId: String,
        geneName: String,
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>,
        peptideCountData: Map<String, Map<String, String>>? = null,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        viewPeptideCount: Boolean = false,
        enableImputation: Boolean = false
    ): String {
        val chartData = generateChartData(conditionDataList, colorMap, imputationMap, enableImputation)
        val layoutData = generateLayoutData(conditionDataList, peptideCountData, viewPeptideCount, imputationMap, enableImputation)
        val heatmapData = if (viewPeptideCount && peptideCountData != null) {
            generateHeatmapData(conditionDataList, peptideCountData, imputationMap, enableImputation)
        } else null
        
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
                const layoutData = $layoutData;
                ${if (heatmapData != null) "const heatmapData = $heatmapData;" else ""}
                
                // Combine bar chart and heatmap data if heatmap is available
                const plotData = [${if (heatmapData != null) "...data, heatmapData" else "...data"}];
                
                const layout = {
                    autosize: true,
                    margin: { l: 50, r: 20, t: 20, b: 100 },
                    xaxis: {
                        title: 'Conditions',
                        tickangle: 0,
                        tickfont: { size: 10 },
                        automargin: true,
                        type: 'category',
                        tickmode: 'array',
                        tickvals: layoutData.tickvals,
                        ticktext: layoutData.ticktext,
                        ${if (viewPeptideCount && heatmapData != null) "domain: [0, 1]" else ""}
                    },
                    yaxis: {
                        title: 'Intensity',
                        tickfont: { size: 10 },
                        automargin: true,
                        ${if (viewPeptideCount && heatmapData != null) "domain: [0.3, 0.9]" else "domain: [0, 1]"}
                    },
                    ${if (viewPeptideCount && heatmapData != null) """
                    yaxis2: {
                        domain: [0, 0.1],
                        showgrid: false,
                        showticklabels: false
                    },""" else ""}
                    showlegend: false,
                    plot_bgcolor: '#ffffff',
                    paper_bgcolor: '#ffffff',
                    hovermode: 'closest',
                    shapes: layoutData.shapes,
                    ${if (viewPeptideCount && heatmapData != null) "annotations: layoutData.annotations || []" else ""}
                };
                
                const config = {
                    responsive: true,
                    displayModeBar: false,
                    staticPlot: false
                };
                
                Plotly.newPlot('chart', plotData, layout, config);
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun generateChartData(
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        enableImputation: Boolean = false
    ): String {
        val traces = JSONArray()
        val allXValues = mutableListOf<String>()
        val allYValues = mutableListOf<Double>()
        val allColors = mutableListOf<String>()
        val allHoverText = mutableListOf<String>()
        val allPatterns = mutableListOf<String>()
        
        // Combine all samples from all conditions into a single trace
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            val color = colorMap[condition] ?: getDefaultColor(conditionDataList.indexOf(conditionData))
            
            conditionData.sampleData.forEach { sample ->
                // Check if sample is imputed
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                
                // Skip imputed samples if imputation is disabled
                if (isImputed && !enableImputation) {
                    return@forEach
                }
                
                allXValues.add(sample.sampleName)
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
                allYValues.add(value)
                allColors.add(color)
                
                allPatterns.add(if (isImputed) "/" else "")
                
                val hoverText = if (isImputed) {
                    "<b>${sample.sampleName}</b><br>Value: $value (imputed)<br>Condition: $condition"
                } else {
                    "<b>${sample.sampleName}</b><br>Value: $value<br>Condition: $condition"
                }
                allHoverText.add(hoverText)
            }
        }
        
        // Create a single trace with all samples
        val trace = JSONObject().apply {
            put("x", JSONArray(allXValues))
            put("y", JSONArray(allYValues))
            put("type", "bar")
            put("showlegend", false)
            put("marker", JSONObject().apply {
                put("color", JSONArray(allColors))
                put("pattern", JSONObject().apply {
                    put("shape", JSONArray(allPatterns))
                })
                put("line", JSONObject().apply {
                    put("color", "rgba(0,0,0,0.3)")
                    put("width", 1)
                })
            })
            put("hovertemplate", "%{customdata}<extra></extra>")
            put("customdata", JSONArray(allHoverText))
        }
        
        traces.put(trace)
        return traces.toString()
    }
    
    private fun generateHeatmapData(
        conditionDataList: List<ConditionData>,
        peptideCountData: Map<String, Map<String, String>>,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        enableImputation: Boolean = false
    ): String {
        val xValues = mutableListOf<String>()
        val yValues = mutableListOf<String>()
        val zValues = mutableListOf<Double>()
        val textValues = mutableListOf<String>()
        
        // Collect peptide count data for all samples
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            conditionData.sampleData.forEach { sample ->
                // Check if sample is imputed
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                
                // Skip imputed samples if imputation is disabled
                if (isImputed && !enableImputation) {
                    return@forEach
                }
                
                val peptideCount = peptideCountData[sample.sampleName]?.values?.firstOrNull()
                if (peptideCount != null) {
                    xValues.add(sample.sampleName)
                    yValues.add("Peptide Count")
                    val countValue = peptideCount.toDoubleOrNull() ?: 0.0
                    zValues.add(countValue)
                    textValues.add(peptideCount)
                }
            }
        }
        
        return JSONObject().apply {
            put("x", JSONArray(xValues))
            put("y", JSONArray(yValues))
            put("z", JSONArray().apply {
                put(JSONArray(zValues))
            })
            put("text", JSONArray().apply {
                put(JSONArray(textValues))
            })
            put("type", "heatmap")
            put("colorscale", JSONArray().apply {
                put(JSONArray().apply { put(0); put("#EE6677") })
                put(JSONArray().apply { put(0.5); put("#BBBBBB") })
                put(JSONArray().apply { put(1); put("#4477AA") })
            })
            put("showscale", true)
            put("hoverinfo", "z")
            put("yaxis", "y2")
            put("xaxis", "x")
            put("colorbar", JSONObject().apply {
                put("thickness", 10)
                put("len", 0.5)
                put("y", 0.5)
                put("x", 1.1)
            })
            put("texttemplate", "%{text}")
            put("textfont", JSONObject().apply {
                put("color", "white")
                put("family", "Arial")
                put("size", 12)
            })
        }.toString()
    }
    
    private fun generateLayoutData(
        conditionDataList: List<ConditionData>,
        peptideCountData: Map<String, Map<String, String>>? = null,
        viewPeptideCount: Boolean = false,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        enableImputation: Boolean = false
    ): String {
        val tickvals = mutableListOf<Int>()
        val ticktext = mutableListOf<String>()
        val shapes = mutableListOf<JSONObject>()
        val annotations = mutableListOf<JSONObject>()
        
        var currentSampleNumber = 0
        
        // Calculate total samples after filtering
        val totalSamples = conditionDataList.sumOf { conditionData ->
            val condition = conditionData.condition
            conditionData.sampleData.count { sample ->
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                !(isImputed && !enableImputation)
            }
        }
        
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            
            // Filter samples based on imputation settings
            val filteredSamples = conditionData.sampleData.filter { sample ->
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                !(isImputed && !enableImputation)
            }
            
            val sampleCount = filteredSamples.size
            
            if (sampleCount > 0) {
                // Calculate the middle position for this condition group
                val middlePosition = currentSampleNumber + (sampleCount / 2)
                tickvals.add(middlePosition)
                
                // Add imputation count to condition name if there are filtered imputed samples
                val imputedCount = conditionData.sampleData.count { sample ->
                    imputationMap?.get(condition)?.get(sample.sampleName) == true
                }
                val conditionLabel = if (imputedCount > 0 && enableImputation) {
                    "$condition ($imputedCount imputed)"
                } else {
                    condition
                }
                ticktext.add(conditionLabel)
                
                // Add peptide count annotations if enabled
                if (viewPeptideCount && peptideCountData != null) {
                    filteredSamples.forEach { sample ->
                        val peptideCount = peptideCountData[sample.sampleName]?.values?.firstOrNull()
                        if (peptideCount != null) {
                            annotations.add(JSONObject().apply {
                                put("xref", "x")
                                put("yref", "y2")
                                put("x", sample.sampleName)
                                put("y", "Peptide Count")
                                put("text", peptideCount)
                                put("showarrow", false)
                                put("font", JSONObject().apply {
                                    put("size", 12)
                                    put("color", "white")
                                })
                            })
                        }
                    }
                }
                
                currentSampleNumber += sampleCount
                
                // Add separator line after each condition group (except the last one)
                if (currentSampleNumber < totalSamples) {
                    val separatorPosition = currentSampleNumber.toDouble() / totalSamples.toDouble()
                    shapes.add(JSONObject().apply {
                        put("type", "line")
                        put("xref", "paper")
                        put("yref", "paper")
                        put("x0", separatorPosition)
                        put("x1", separatorPosition)
                        put("y0", 0)
                        put("y1", 1)
                        put("line", JSONObject().apply {
                            put("dash", "dash")
                            put("color", "rgba(0,0,0,0.5)")
                            put("width", 1)
                        })
                    })
                }
            }
        }
        
        return JSONObject().apply {
            put("tickvals", JSONArray(tickvals))
            put("ticktext", JSONArray(ticktext))
            put("shapes", JSONArray(shapes))
            if (annotations.isNotEmpty()) {
                put("annotations", JSONArray(annotations))
            }
        }.toString()
    }
    
    private fun getDefaultColor(index: Int): String {
        val defaultColors = listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999"
        )
        return defaultColors[index % defaultColors.size]
    }
}