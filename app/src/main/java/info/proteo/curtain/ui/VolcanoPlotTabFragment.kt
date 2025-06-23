package info.proteo.curtain.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.proteo.curtain.VolcanoAxis
import info.proteo.curtain.databinding.FragmentVolcanoPlotTabBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.text.get

class VolcanoPlotTabFragment : Fragment() {
    private var _binding: FragmentVolcanoPlotTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CurtainDetailsViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVolcanoPlotTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.curtainData.collect { curtainData ->
                    if (curtainData != null) {
                        loadVolcanoPlot()
                    }
                }
            }
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        showLoading(false)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e("VolcanoPlot", "WebView error: $description")
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Error loading plot: $description"
                    showLoading(false)
                }
            }
        }
    }

    private fun loadVolcanoPlot() {
        Log.d("VolcanoPlot", "Loading volcano plot")
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        // Show loading initially
        showLoading(true)

        // Get differential form settings
        val diffForm = curtainData.differentialForm
        val fcColumn = diffForm.foldChange
        val sigColumn = diffForm.significant
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        val comparisonColumn = diffForm.comparison


        if (fcColumn.isEmpty() || sigColumn.isEmpty()) {
            Log.e("VolcanoPlot", "Missing fold change or significance columns")
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Missing fold change or significance columns"
            showLoading(false)
            return
        }

        try {
            // Convert differential data to JSON for plotting
            val jsonData = JSONArray()

            var minFC = Double.MAX_VALUE
            var maxFC = -Double.MAX_VALUE
            var maxLogP = 0.0

            val selectedData = curtainData.selected ?: mapOf()
            var colorMap = curtainSettings.colorMap.toMutableMap()

            val conditions = mutableListOf<String>()
            curtainSettings.sampleMap.forEach { (_, sampleInfo) ->
                val condition = sampleInfo["condition"]
                if (condition != null && !conditions.contains(condition)) {
                    conditions.add(condition)
                }
            }

            // Color assignment logic from web app
            val currentColors = mutableListOf<String>()
            val defaultColorList = curtainSettings.defaultColorList

            // Collect currently used colors
            if (colorMap.isNotEmpty()) {
                colorMap.forEach { (s, color) ->
                    if (!conditions.contains(s)) {
                        if (defaultColorList.contains(color)) {
                            currentColors.add(color)
                        }
                    }
                }
            } else {
                colorMap = mutableMapOf()
            }

            // Set current position for color assignment
            var currentPosition = 0
            if (currentColors.size != defaultColorList.size) {
                if (currentColors.size >= defaultColorList.size) {
                    currentPosition = 0
                } else {
                    currentPosition = currentColors.size
                }
            }

            // Get selection operation names
            val selectOperationNames = mutableSetOf<String>()
            curtainData.selectedMap.forEach { (_, selections) ->
                @Suppress("UNCHECKED_CAST")
                val selectionMap = selections as? Map<String, Boolean>
                selectionMap?.forEach { (selectionName, isSelected) ->
                    if (isSelected) {
                        selectOperationNames.add(selectionName)
                    }
                }
            }

            // Assign colors using the logic from web app
            var breakColor = false
            var repeat = false

            for (s in selectOperationNames) {
                if (!colorMap.containsKey(s)) {
                    while (true) {
                        if (breakColor) {
                            colorMap[s] = defaultColorList[currentPosition]
                            break
                        }

                        if (currentColors.contains(defaultColorList[currentPosition])) {
                            currentPosition++
                            if (repeat) {
                                colorMap[s] = defaultColorList[currentPosition]
                                break
                            }
                        } else if (currentPosition >= defaultColorList.size) {
                            currentPosition = 0
                            colorMap[s] = defaultColorList[currentPosition]
                            repeat = true
                            break
                        } else if (currentPosition < defaultColorList.size) {
                            colorMap[s] = defaultColorList[currentPosition]
                            break
                        } else {
                            breakColor = true
                            currentPosition = 0
                        }
                    }

                    currentPosition++
                    if (currentPosition == defaultColorList.size) {
                        currentPosition = 0
                    }
                }
            }

            // Update settings with the new color map
            val updatedSettings = curtainSettings.copy(colorMap = colorMap)
            viewModel.updateCurtainSettings(updatedSettings)
            // Use processedDifferentialData if available
            val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>

            if (processedData != null) {
                for (row in processedData) {
                    val comparison = if (comparisonColumn.isNotEmpty()) row[comparisonColumn]?.toString() ?: "" else ""
                    val id = row[idColumn]?.toString() ?: ""
                    val gene = if (geneColumn.isNotEmpty()) row[geneColumn]?.toString() ?: id else id

                    val dataPoint = JSONObject()

                    val fcValue = when (val fc = row[fcColumn]) {
                        is Number -> {
                            val doubleValue = fc.toDouble()
                            if (doubleValue.isNaN()) 0.0 else doubleValue
                        }
                        is String -> fc.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val sigValue = when (val fc = row[sigColumn]) {
                        is Number -> {
                            val doubleValue = fc.toDouble()
                            if (doubleValue.isNaN()) 0.0 else doubleValue
                        }
                        is String -> fc.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    minFC = minOf(minFC, fcValue)
                    maxFC = maxOf(maxFC, fcValue)
                    maxLogP = maxOf(maxLogP, sigValue)

                    val selections = mutableListOf<String>()
                    val selectionColors = mutableListOf<String>()


                    @Suppress("UNCHECKED_CAST")
                    val selectionForId: Map<String, Boolean>? = curtainData.selectedMap[id] as? Map<String, Boolean>

                    if (selectionForId != null) {
                        // Add each selection this ID belongs to
                        for ((selectionName, selected) in selectionForId) {
                            if (selected) {
                                // Check if the selection is comparison-specific
                                val matchResult = Regex("\\(([^)]*)\\)[^(]*$").find(selectionName)
                                if (matchResult != null) {
                                    val matchedComparison = matchResult.groupValues[1]
                                    // Only include if the comparison matches
                                    if (matchedComparison == comparison) {
                                        Log.d("VolcanoPlot", "Adding selection: $selectionName for ID: $id")
                                        Log.d("VolcanoPlot", "Color for selection: ${colorMap[selectionName]}")
                                        selections.add(selectionName)
                                        val color = colorMap[selectionName] ?: "#808080"
                                        selectionColors.add(color)
                                    }
                                } else {
                                    // Not comparison-specific, include it
                                    selections.add(selectionName)
                                    val color = colorMap[selectionName] ?: "#808080"
                                    selectionColors.add(color)
                                }
                            }
                        }
                    }  else if (curtainSettings.backGroundColorGrey) {
                        selections.add("Background")
                        selectionColors.add("#a4a2a2")
                    }

                    // Get ID and gene name for labels

                    dataPoint.put("x", fcValue)
                    dataPoint.put("y", sigValue)
                    dataPoint.put("id", id)
                    dataPoint.put("gene", gene)
                    dataPoint.put("selections", JSONArray(selections))
                    dataPoint.put("colors", JSONArray(selectionColors))


                    val pointColor = if (selectionColors.isNotEmpty()) selectionColors[0] else "#808080"
                    dataPoint.put("color", pointColor)

                    jsonData.put(dataPoint)
                }

                val settings = viewModel.curtainSettings.value
                if (settings != null) {
                    val volcanoAxis = settings.volcanoAxis

                    // Create a new VolcanoAxis with automatically calculated boundaries
                    val updatedVolcanoAxis = VolcanoAxis(
                        minX = volcanoAxis.minX ?: (minFC - 1.0),
                        maxX = volcanoAxis.maxX ?: (maxFC + 1.0),
                        minY = volcanoAxis.minY ?: 0.0,
                        maxY = volcanoAxis.maxY ?: (maxLogP + 1.0),
                        x = volcanoAxis.x,
                        y = volcanoAxis.y,
                        dtickX = volcanoAxis.dtickX,
                        dtickY = volcanoAxis.dtickY,
                        ticklenX = volcanoAxis.ticklenX,
                        ticklenY = volcanoAxis.ticklenY
                    )

                    // For debugging
                    Log.d("VolcanoPlot", "Original axis: $volcanoAxis")
                    Log.d("VolcanoPlot", "Updated axis: $updatedVolcanoAxis")

                    // Create a temporary settings object with the updated axis
                    val updatedSettings = settings.copy(volcanoAxis = updatedVolcanoAxis)
                    viewModel.updateCurtainSettings(updatedSettings)
                }

                val html = createVolcanoPlotHtml(
                    jsonData.toString()
                )


                binding.webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            } else {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "No differential data available"
                showLoading(false)
            }
        } catch (e: Exception) {
            Log.e("VolcanoPlot", "Error loading volcano plot: ${e.message}", e)
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Error loading plot: ${e.message}"
            showLoading(false)
        }
    }

    private fun createVolcanoPlotHtml(jsonData: String): String {
        // Get settings from CurtainDataService
        val curtainSettings = viewModel.curtainSettings.value ?: throw IllegalStateException("Curtain settings not available")

        // Get axis titles
        val xAxisTitle = curtainSettings.volcanoAxis.x
        val yAxisTitle = curtainSettings.volcanoAxis.y

        // Get grid settings
        val showGridX = curtainSettings.volcanoPlotGrid["x"] as? Boolean ?: false
        val showGridY = curtainSettings.volcanoPlotGrid["y"] as? Boolean ?: false

        // Get axis range settings
        val xAxisRange = if (curtainSettings.volcanoAxis.minX != null || curtainSettings.volcanoAxis.maxX != null) {
            "[${curtainSettings.volcanoAxis.minX ?: "null"}, ${curtainSettings.volcanoAxis.maxX ?: "null"}]"
        } else {
            "null"
        }

        val yAxisRange = if (curtainSettings.volcanoAxis.minY != null || curtainSettings.volcanoAxis.maxY != null) {
            "[${curtainSettings.volcanoAxis.minY ?: "null"}, ${curtainSettings.volcanoAxis.maxY ?: "null"}]"
        } else {
            "null"
        }

        // Get tick settings
        val dtickX = curtainSettings.volcanoAxis.dtickX?.toString() ?: "null"
        val dtickY = curtainSettings.volcanoAxis.dtickY?.toString() ?: "null"
        val ticklenX = curtainSettings.volcanoAxis.ticklenX
        val ticklenY = curtainSettings.volcanoAxis.ticklenY

        // Background color setting
        val backgroundColor = if (curtainSettings.backGroundColorGrey) {
            "'#f0f0f0'"
        } else {
            "'white'"
        }

        // Margin settings
        val marginSettings = if (curtainSettings.volcanoPlotDimension?.margin?.left != null) {
            """margin: {
                l: ${curtainSettings.volcanoPlotDimension.margin.left},
                r: ${curtainSettings.volcanoPlotDimension.margin.right},
                b: ${curtainSettings.volcanoPlotDimension.margin.bottom},
                t: ${curtainSettings.volcanoPlotDimension.margin.top}
            }"""
        } else {
            "margin: {l: 50, r: 50, b: 100, t: 50}"  // Increased bottom margin from 50 to 100
        }

        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script src="plotly.min.js"></script>
        <style>
            body {
                margin: 0;
                padding: 0;
                font-family: ${curtainSettings.plotFontFamily}, sans-serif;
            }
            #plot {
                width: 100%;
                height: 100vh;
            }
        </style>
    </head>
    <body>
        <div id="plot"></div>
        <script>
            const data = JSON.parse('${jsonData}');

            // Group data points by selections for legend
            const selectionGroups = {};

            // First, identify all unique selection groups
            data.forEach(point => {
                if (point.selections && point.selections.length > 0) {
                    for (let i = 0; i < point.selections.length; i++) {
                        const selName = point.selections[i];
                        if (!selectionGroups[selName]) {
                            selectionGroups[selName] = {
                                name: selName,
                                color: point.colors[i],
                                points: []
                            };
                        }
                        selectionGroups[selName].points.push(point);
                    }
                } else {
                    // For points with no selection, add to "Other" group
                    if (!selectionGroups["Other"]) {
                        selectionGroups["Other"] = {
                            name: "Other",
                            color: "#808080",
                            points: []
                        };
                    }
                    selectionGroups["Other"].points.push(point);
                }
            });

            // Create a trace for each selection group
            const traces = [];
            for (const groupName in selectionGroups) {
                const group = selectionGroups[groupName];
                traces.push({
                    x: group.points.map(point => point.x),
                    y: group.points.map(point => point.y),
                    mode: 'markers',
                    type: 'scatter',
                    marker: {
                        color: group.color,
                        size: ${curtainSettings.scatterPlotMarkerSize},
                        opacity: groupName === "Background" ? 0.3 : 0.7
                    },
                    text: group.points.map(point => point.gene),
                    hoverinfo: 'text+x+y',
                    name: group.name
                });
            }

            // If no selection groups were found, create a default trace
            if (traces.length === 0) {
                traces.push({
                    x: data.map(point => point.x),
                    y: data.map(point => point.y),
                    mode: 'markers',
                    type: 'scatter',
                    marker: {
                        color: '#808080',
                        size: ${curtainSettings.scatterPlotMarkerSize},
                        opacity: 0.7
                    },
                    text: data.map(point => point.gene),
                    hoverinfo: 'text+x+y',
                    name: 'Data points'
                });
            }

            // Create layout with cutoff lines
            const layout = {
                title: '${curtainSettings.volcanoPlotTitle}',
                xaxis: {
                    title: '${curtainSettings.volcanoAxis.x}',
                    range: ${if (curtainSettings.volcanoAxis.minX != null || curtainSettings.volcanoAxis.maxX != null)
            "[${curtainSettings.volcanoAxis.minX ?: "null"}, ${curtainSettings.volcanoAxis.maxX ?: "null"}]"
        else "null"},
                    dtick: ${curtainSettings.volcanoAxis.dtickX?.toString() ?: "null"},
                    ticklen: ${curtainSettings.volcanoAxis.ticklenX},
                    zeroline: true,
                    showgrid: ${curtainSettings.volcanoPlotGrid["x"] as? Boolean ?: false},
                    gridcolor: '#e0e0e0'
                },
                yaxis: {
                    title: '${curtainSettings.volcanoAxis.y}',
                    range: ${if (curtainSettings.volcanoAxis.minY != null || curtainSettings.volcanoAxis.maxY != null)
            "[${curtainSettings.volcanoAxis.minY ?: "null"}, ${curtainSettings.volcanoAxis.maxY ?: "null"}]"
        else "null"},
                    dtick: ${curtainSettings.volcanoAxis.dtickY?.toString() ?: "null"},
                    ticklen: ${curtainSettings.volcanoAxis.ticklenY},
                    zeroline: true,
                    showgrid: ${curtainSettings.volcanoPlotGrid["y"] as? Boolean ?: false},
                    gridcolor: '#e0e0e0'
                },
                margin: {
                    l: ${curtainSettings.volcanoPlotDimension?.margin?.left ?: 50},
                    r: ${curtainSettings.volcanoPlotDimension?.margin?.right ?: 50},
                    b: ${(curtainSettings.volcanoPlotDimension?.margin?.bottom ?: 50) + 50},  // Add extra space for legend
                    t: ${curtainSettings.volcanoPlotDimension?.margin?.top ?: 50}
                },
                hovermode: 'closest',
                // Legend at the bottom
                legend: {
                    orientation: 'h',  // horizontal orientation
                    yanchor: 'top',
                    y: -0.2,          // position below the plot
                    xanchor: 'center',
                    x: 0.5            // center horizontally
                },
                paper_bgcolor: ${if (curtainSettings.backGroundColorGrey) "'#f0f0f0'" else "'white'"},
                plot_bgcolor: ${if (curtainSettings.backGroundColorGrey) "'#f0f0f0'" else "'white'"}
            };

            // Calculate max Y value for vertical cutoff lines
            const yMax = ${curtainSettings.volcanoAxis.maxY ?: "null"} || 
                Math.max(...data.map(point => point.y), ${-Math.log10(curtainSettings.pCutoff)}) * 1.1;

            // Add cutoff lines
            const cutOff = [];

            // Add vertical line for negative fold change cutoff - extends to max Y value
            cutOff.push({
                type: 'line',
                x0: ${-curtainSettings.log2FCCutoff},
                y0: 0,
                x1: ${-curtainSettings.log2FCCutoff},
                y1: yMax,
                line: {
                    color: 'grey',
                    width: 1,
                    dash: 'dash'
                }
            });

            // Add vertical line for positive fold change cutoff - extends to max Y value
            cutOff.push({
                type: 'line',
                x0: ${curtainSettings.log2FCCutoff},
                y0: 0,
                x1: ${curtainSettings.log2FCCutoff},
                y1: yMax,
                line: {
                    color: 'grey',
                    width: 1,
                    dash: 'dash'
                }
            });

            // Calculate x-axis extent for horizontal significance line
            let x0 = ${curtainSettings.volcanoAxis.minX ?: -10};
            let x1 = ${curtainSettings.volcanoAxis.maxX ?: 10};

            // Add horizontal p-value significance threshold line
            cutOff.push({
                type: 'line',
                x0: x0,
                y0: ${-Math.log10(curtainSettings.pCutoff)},
                x1: x1,
                y1: ${-Math.log10(curtainSettings.pCutoff)},
                line: {
                    color: 'grey',
                    width: 1,
                    dash: 'dash'
                }
            });

            // Add cutoff shapes to the layout
            layout.shapes = cutOff;

            // Adjust background color and opacity
            const backgroundTrace = traces.find(trace => trace.name === 'Background');
            if (backgroundTrace && ${curtainSettings.backGroundColorGrey}) {
                backgroundTrace.marker.color = '#a4a2a2';
                backgroundTrace.marker.opacity = 0.3;
            }

            // Create the plot with all traces
            Plotly.newPlot('plot', traces, layout, {responsive: true});
        </script>
    </body>
    </html>
    """.trimIndent()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            plotProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            plotLoadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
            webView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}