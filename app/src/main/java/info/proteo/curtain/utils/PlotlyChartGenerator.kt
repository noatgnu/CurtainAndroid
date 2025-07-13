package info.proteo.curtain.utils

import info.proteo.curtain.data.services.ConditionColorService
import info.proteo.curtain.presentation.fragments.curtain.ProteinDetailListTabFragment.ConditionData
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

object PlotlyChartGenerator {
    
    enum class ChartType {
        INDIVIDUAL_BAR,
        AVERAGE_BAR,
        VIOLIN_PLOT
    }
    
    enum class ErrorBarType {
        STANDARD_ERROR,
        STANDARD_DEVIATION
    }
    
    fun generateBarChartHtml(
        proteinId: String,
        geneName: String,
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>,
        peptideCountData: Map<String, Map<String, String>>? = null,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        viewPeptideCount: Boolean = false,
        enableImputation: Boolean = false,
        chartType: ChartType = ChartType.INDIVIDUAL_BAR,
        conditionColorService: ConditionColorService? = null,
        errorBarType: ErrorBarType = ErrorBarType.STANDARD_ERROR,
        showIndividualPoints: Boolean = false,
        violinPointPosition: Double = -1.2
    ): String {
        // Generate enhanced color map using ConditionColorService if available
        val enhancedColorMap = if (conditionColorService != null) {
            val conditions = conditionDataList.map { it.condition }.distinct()
            conditionColorService.generateVisualizationColorMap(conditions, colorMap)
        } else {
            colorMap
        }
        
        // Order condition data based on user preferences if ConditionColorService is available
        val orderedConditionDataList = if (conditionColorService != null) {
            val allConditions = conditionDataList.map { it.condition }.distinct()
            val orderedConditions = conditionColorService.getOrderedConditions(allConditions)
            
            // Reorder conditionDataList to match user preferences
            orderedConditions.mapNotNull { condition ->
                conditionDataList.find { it.condition == condition }
            }
        } else {
            conditionDataList
        }
        
        val chartData = when (chartType) {
            ChartType.INDIVIDUAL_BAR -> generateChartData(orderedConditionDataList, enhancedColorMap, imputationMap, enableImputation)
            ChartType.AVERAGE_BAR -> generateAverageBarChartData(orderedConditionDataList, enhancedColorMap, imputationMap, enableImputation, errorBarType, showIndividualPoints)
            ChartType.VIOLIN_PLOT -> generateViolinPlotData(orderedConditionDataList, enhancedColorMap, imputationMap, enableImputation, violinPointPosition)
        }
        val layoutData = generateLayoutData(orderedConditionDataList, peptideCountData, viewPeptideCount, imputationMap, enableImputation, chartType)
        val heatmapData = if (viewPeptideCount && peptideCountData != null) {
            generateHeatmapData(orderedConditionDataList, peptideCountData, imputationMap, enableImputation)
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
            <div class="chart-title">$geneName ($proteinId) - ${getChartTitle(chartType)}</div>
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
    
    private fun generateAverageBarChartData(
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        enableImputation: Boolean = false,
        errorBarType: ErrorBarType = ErrorBarType.STANDARD_ERROR,
        showIndividualPoints: Boolean = false
    ): String {
        val traces = JSONArray()
        
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            val color = colorMap[condition] ?: getDefaultColor(conditionDataList.indexOf(conditionData))
            
            // Filter samples based on imputation settings
            val filteredSamples = conditionData.sampleData.filter { sample ->
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                !(isImputed && !enableImputation)
            }
            
            if (filteredSamples.isNotEmpty()) {
                // Calculate statistics for this condition
                val values = filteredSamples.mapNotNull { sample ->
                    when {
                        sample.value == "N/A" || sample.value.isBlank() -> null
                        sample.value.equals("inf", ignoreCase = true) -> null
                        sample.value.equals("-inf", ignoreCase = true) -> null
                        else -> sample.value.toDoubleOrNull()
                    }
                }.filter { !it.isNaN() }
                
                if (values.isNotEmpty()) {
                    // Enhanced statistical calculations matching frontend
                    val mean = values.average()
                    val variance = values.map { (it - mean) * (it - mean) }.average()
                    val std = sqrt(variance)
                    val standardError = std / sqrt(values.size.toDouble())
                    
                    // Select error type based on frontend logic
                    val errorValue = when (errorBarType) {
                        ErrorBarType.STANDARD_ERROR -> standardError
                        ErrorBarType.STANDARD_DEVIATION -> std
                    }
                    
                    val errorTypeName = when (errorBarType) {
                        ErrorBarType.STANDARD_ERROR -> "Std Error"
                        ErrorBarType.STANDARD_DEVIATION -> "Std Dev"
                    }
                    
                    // Count imputed samples
                    val imputedCount = conditionData.sampleData.count { sample ->
                        imputationMap?.get(condition)?.get(sample.sampleName) == true
                    }
                    
                    // Main bar trace
                    val barTrace = JSONObject().apply {
                        put("x", JSONArray().apply { put(condition) })
                        put("y", JSONArray().apply { put(mean) })
                        put("type", "bar")
                        put("showlegend", false)
                        put("error_y", JSONObject().apply {
                            put("type", "data")
                            put("array", JSONArray().apply { put(errorValue) })
                            put("visible", true)
                            put("color", "black")
                            put("thickness", 2)
                            put("width", 4)
                        })
                        put("marker", JSONObject().apply {
                            put("color", color)
                            put("line", JSONObject().apply {
                                put("color", "black")
                                put("width", 1)
                            })
                        })
                        put("hovertemplate", "<b>%{x}</b><br>" +
                            "Mean: %{y:.3f}<br>" +
                            "$errorTypeName: ${String.format("%.3f", errorValue)}<br>" +
                            "Std: ${String.format("%.3f", std)}<br>" +
                            "N: ${values.size}" +
                            (if (imputedCount > 0 && enableImputation) "<br>Imputed: $imputedCount" else "") +
                            "<extra></extra>")
                    }
                    traces.put(barTrace)
                    
                    // Add individual points as scatter plot if enabled (matching frontend)
                    if (showIndividualPoints) {
                        val pointTrace = JSONObject().apply {
                            put("x", JSONArray().apply {
                                values.forEach { _ -> put(condition) }
                            })
                            put("y", JSONArray(values))
                            put("type", "scatter")
                            put("mode", "markers")
                            put("showlegend", false)
                            put("marker", JSONObject().apply {
                                put("color", "rgba(0,0,0,0.6)")
                                put("size", 6)
                                put("symbol", "circle")
                            })
                            put("hovertemplate", "<b>%{x}</b><br>" +
                                "Value: %{y:.3f}<br>" +
                                "<extra></extra>")
                        }
                        traces.put(pointTrace)
                    }
                }
            }
        }
        
        return traces.toString()
    }
    
    private fun generateViolinPlotData(
        conditionDataList: List<ConditionData>,
        colorMap: Map<String, String>,
        imputationMap: Map<String, Map<String, Boolean>>? = null,
        enableImputation: Boolean = false,
        violinPointPosition: Double = -1.2
    ): String {
        val traces = JSONArray()
        
        conditionDataList.forEach { conditionData ->
            val condition = conditionData.condition
            val color = colorMap[condition] ?: getDefaultColor(conditionDataList.indexOf(conditionData))
            
            // Filter samples based on imputation settings
            val filteredSamples = conditionData.sampleData.filter { sample ->
                val isImputed = imputationMap?.get(condition)?.get(sample.sampleName) == true
                !(isImputed && !enableImputation)
            }
            
            if (filteredSamples.isNotEmpty()) {
                val values = mutableListOf<Double>()
                val violinX = mutableListOf<String>()
                
                filteredSamples.forEach { sample ->
                    val value = when {
                        sample.value == "N/A" || sample.value.isBlank() -> null
                        sample.value.equals("inf", ignoreCase = true) -> null
                        sample.value.equals("-inf", ignoreCase = true) -> null
                        else -> sample.value.toDoubleOrNull()
                    }
                    
                    if (value != null && !value.isNaN()) {
                        values.add(value)
                        violinX.add(condition)
                    }
                }
                
                if (values.isNotEmpty()) {
                    // Count imputed samples
                    val imputedCount = conditionData.sampleData.count { sample ->
                        imputationMap?.get(condition)?.get(sample.sampleName) == true
                    }
                    
                    // Calculate additional statistics for enhanced hover info
                    val mean = values.average()
                    val variance = values.map { (it - mean) * (it - mean) }.average()
                    val std = sqrt(variance)
                    val median = values.sorted().let { sorted ->
                        if (sorted.size % 2 == 0) {
                            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                        } else {
                            sorted[sorted.size / 2]
                        }
                    }
                    
                    val trace = JSONObject().apply {
                        put("x", JSONArray(violinX))
                        put("y", JSONArray(values))
                        put("type", "violin")
                        put("showlegend", false)
                        put("points", "all")
                        put("pointpos", violinPointPosition) // Configurable position matching frontend
                        put("box", JSONObject().apply {
                            put("visible", true)
                            put("fillcolor", "rgba(255,255,255,0.8)")
                            put("line", JSONObject().apply {
                                put("color", "black")
                                put("width", 2)
                            })
                        })
                        put("meanline", JSONObject().apply {
                            put("visible", true)
                            put("color", "red")
                            put("width", 2)
                        })
                        put("line", JSONObject().apply {
                            put("color", "black")
                            put("width", 1)
                        })
                        put("fillcolor", color)
                        put("marker", JSONObject().apply {
                            put("color", color)
                            put("size", 4)
                            put("opacity", 0.7)
                            put("symbol", "circle")
                        })
                        put("spanmode", "soft") // Smooth kernel density estimation
                        put("name", condition)
                        put("bandwidth", "auto") // Automatic bandwidth selection
                        put("scalemode", "width") // Scale violin width consistently
                        put("hovertemplate", "<b>%{x}</b><br>" +
                            "Value: %{y:.3f}<br>" +
                            "Mean: ${String.format("%.3f", mean)}<br>" +
                            "Median: ${String.format("%.3f", median)}<br>" +
                            "Std: ${String.format("%.3f", std)}<br>" +
                            "N: ${values.size}" +
                            (if (imputedCount > 0 && enableImputation) "<br>Imputed: $imputedCount" else "") +
                            "<extra></extra>")
                        
                        // Add selection capabilities matching frontend
                        put("selected", JSONObject().apply {
                            put("marker", JSONObject().apply {
                                put("color", "#e61010")
                                put("size", 6)
                            })
                        })
                        put("unselected", JSONObject().apply {
                            put("marker", JSONObject().apply {
                                put("opacity", 0.3)
                            })
                        })
                    }
                    traces.put(trace)
                }
            }
        }
        
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
        enableImputation: Boolean = false,
        chartType: ChartType = ChartType.INDIVIDUAL_BAR
    ): String {
        val tickvals = mutableListOf<Int>()
        val ticktext = mutableListOf<String>()
        val shapes = mutableListOf<JSONObject>()
        val annotations = mutableListOf<JSONObject>()
        
        // For average bar charts and violin plots, use condition-based layout
        if (chartType == ChartType.AVERAGE_BAR || chartType == ChartType.VIOLIN_PLOT) {
            conditionDataList.forEachIndexed { index, conditionData ->
                val condition = conditionData.condition
                
                // Count imputed samples
                val imputedCount = conditionData.sampleData.count { sample ->
                    imputationMap?.get(condition)?.get(sample.sampleName) == true
                }
                
                val conditionLabel = if (imputedCount > 0 && enableImputation) {
                    "$condition ($imputedCount imputed)"
                } else {
                    condition
                }
                
                tickvals.add(index)
                ticktext.add(conditionLabel)
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
        
        // Original individual sample layout for INDIVIDUAL_BAR chart type
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
    
    private fun getChartTitle(chartType: ChartType): String {
        return when (chartType) {
            ChartType.INDIVIDUAL_BAR -> "Individual Sample Values"
            ChartType.AVERAGE_BAR -> "Mean ± Error"
            ChartType.VIOLIN_PLOT -> "Distribution & Density"
        }
    }
}