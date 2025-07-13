package info.proteo.curtain.presentation.fragments.curtain

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
import info.proteo.curtain.AppData
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.VolcanoAxis
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.databinding.FragmentVolcanoPlotTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.get
import kotlin.collections.set
import kotlin.text.get
import kotlin.toString

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
                        loadVolcanoPlotDefer()
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

            // Request disallow intercept to prevent parent from handling touch events
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // Request parent to not intercept touch events
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        false // Allow WebView to handle the touch
                    }
                    android.view.MotionEvent.ACTION_UP, 
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        // Allow parent to intercept touch events again
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        false // Allow WebView to handle the touch
                    }
                    else -> false // Allow WebView to handle all other touch events
                }
            }

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



    private suspend fun processVolcanoData(
        curtainData: AppData,
        curtainSettings: CurtainSettings
    ): VolcanoProcessResult = withContext(Dispatchers.Default) {
        if (curtainData.differentialForm == null) {
            throw IllegalStateException("Differential form is not set in CurtainData")
        }

        // Get differential form settings
        val diffForm = curtainData.differentialForm
        val fcColumn = diffForm.foldChange
        val sigColumn = diffForm.significant
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        val comparisonColumn = diffForm.comparison

        if (fcColumn.isEmpty() || sigColumn.isEmpty()) {
            throw IllegalArgumentException("Missing fold change or significance columns")
        }

        // Convert differential data to JSON for plotting
        val jsonData = JSONArray()

        var minFC = Double.MAX_VALUE
        var maxFC = -Double.MAX_VALUE
        var maxLogP = 0.0

        // Get color maps and track used colors
        val colorMap = curtainSettings.colorMap.toMutableMap()
        val specialColorMap = mutableMapOf<String, String>()

        // Extract conditions from sampleMap
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
                            currentPosition = 0
                            breakColor = true
                            break
                        }
                    } else if (currentPosition >= defaultColorList.size) {
                        currentPosition = 0
                        colorMap[s] = defaultColorList[currentPosition]
                        repeat = true
                        break
                    } else if (currentPosition != defaultColorList.size) {
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

        val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>

        if (processedData == null) {
            throw IllegalStateException("No differential data available")
        }

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

            val sigValue = when (val sig = row[sigColumn]) {
                is Number -> {
                    val doubleValue = sig.toDouble()
                    if (doubleValue.isNaN()) 0.0 else doubleValue
                }
                is String -> sig.toDoubleOrNull() ?: 0.0
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
                    if (selected && colorMap.containsKey(selectionName)) {
                        selections.add(selectionName)
                        selectionColors.add(colorMap[selectionName]!!)
                    }
                }
            }

            // If not part of any selection, create significance groups or add to background
            if (selections.isEmpty()) {
                if (curtainSettings.backGroundColorGrey) {
                    // Add to Background category
                    selections.add("Background")
                    selectionColors.add("#a4a2a2")  // Gray with opacity
                } else {
                    // Use significance grouping system
                    val (groupText, position) = getSignificantGroup(fcValue, sigValue, curtainSettings)
                    val group = "$groupText ($comparison)"

                    // Modified color assignment for significance groups to match JavaScript implementation
                    if (!colorMap.containsKey(group)) {
                        if (!specialColorMap.containsKey(position)) {
                            // Assign a new color from default list
                            if (currentPosition < defaultColorList.size) {
                                // Create a copy equivalent to slice() in JS
                                val colorToUse = defaultColorList[currentPosition]
                                specialColorMap[position] = colorToUse
                                colorMap[group] = colorToUse
                            } else {
                                currentPosition = 0
                                val colorToUse = defaultColorList[currentPosition]
                                specialColorMap[position] = colorToUse
                                colorMap[group] = colorToUse
                            }

                            currentPosition++
                            if (currentPosition == defaultColorList.size) {
                                currentPosition = 0
                            }
                        } else {
                            // Reuse color for same significance pattern
                            colorMap[group] = specialColorMap[position]!!
                        }
                    } else {
                        specialColorMap[position] = colorMap[group]!!
                    }

                    selections.add(group)
                    selectionColors.add(colorMap[group]!!)
                }
            }

            dataPoint.put("x", fcValue)
            dataPoint.put("y", sigValue)
            dataPoint.put("id", id)
            dataPoint.put("gene", gene)
            dataPoint.put("comparison", comparison)
            dataPoint.put("selections", JSONArray(selections))
            dataPoint.put("colors", JSONArray(selectionColors))

            val pointColor = if (selectionColors.isNotEmpty()) selectionColors[0] else "#808080"
            dataPoint.put("color", pointColor)
            jsonData.put(dataPoint)
        }

        // Create updated volcano axis
        val volcanoAxis = curtainSettings.volcanoAxis
        var updatedVolcanoAxis = VolcanoAxis(
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
        if (updatedVolcanoAxis.x == "") {
            updatedVolcanoAxis = updatedVolcanoAxis.copy(x = "Fold Change")
        }
        if (updatedVolcanoAxis.y == "") {
            updatedVolcanoAxis = updatedVolcanoAxis.copy(y = "-log10(p-value)")
        }

        // Return the processed result
        VolcanoProcessResult(
            jsonData = jsonData.toString(),
            colorMap = colorMap,
            updatedVolcanoAxis = updatedVolcanoAxis
        )
    }

    private data class VolcanoProcessResult(
        val jsonData: String,
        val colorMap: Map<String, String>,
        val updatedVolcanoAxis: VolcanoAxis
    )


    private fun loadVolcanoPlot() {
        Log.d("VolcanoPlot", "Loading volcano plot")
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        // Show loading initially
        showLoading(true)

        try {
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

            // Convert differential data to JSON for plotting
            val jsonData = JSONArray()

            var minFC = Double.MAX_VALUE
            var maxFC = -Double.MAX_VALUE
            var maxLogP = 0.0

            // Get color maps and track used colors
            val colorMap = curtainSettings.colorMap.toMutableMap()
            val specialColorMap = mutableMapOf<String, String>()
            // Extract conditions from sampleMap
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
                                currentPosition = 0
                                breakColor = true
                                break
                            }
                        } else if (currentPosition >= defaultColorList.size) {
                            currentPosition = 0
                            colorMap[s] = defaultColorList[currentPosition]
                            repeat = true
                            break
                        } else if (currentPosition != defaultColorList.size) {
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

                    val sigValue = when (val sig = row[sigColumn]) {
                        is Number -> {
                            val doubleValue = sig.toDouble()
                            if (doubleValue.isNaN()) 0.0 else doubleValue
                        }
                        is String -> sig.toDoubleOrNull() ?: 0.0
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
                            if (selected && colorMap.containsKey(selectionName)) {
                                selections.add(selectionName)
                                selectionColors.add(colorMap[selectionName]!!)
                            }
                        }
                    }

                    // If not part of any selection, create significance groups or add to background
                    if (selections.isEmpty()) {
                        if (curtainSettings.backGroundColorGrey) {
                            // Add to Background category
                            selections.add("Background")
                            selectionColors.add("#a4a2a2")  // Gray with opacity
                        } else {
                            // Use significance grouping system
                            val (groupText, position) = getSignificantGroup(fcValue, sigValue, curtainSettings)
                            val group = "$groupText ($comparison)"

                            // Modified color assignment for significance groups to match JavaScript implementation
                            if (!colorMap.containsKey(group)) {
                                if (!specialColorMap.containsKey(position)) {
                                    // Assign a new color from default list
                                    if (currentPosition < defaultColorList.size) {
                                        // Create a copy equivalent to slice() in JS
                                        val colorToUse = defaultColorList[currentPosition]
                                        specialColorMap[position] = colorToUse
                                        colorMap[group] = colorToUse
                                    } else {
                                        currentPosition = 0
                                        val colorToUse = defaultColorList[currentPosition]
                                        specialColorMap[position] = colorToUse
                                        colorMap[group] = colorToUse
                                    }

                                    currentPosition++
                                    if (currentPosition == defaultColorList.size) {
                                        currentPosition = 0
                                    }
                                } else {
                                    // Reuse color for same significance pattern
                                    colorMap[group] = specialColorMap[position]!!
                                }
                            } else {
                                specialColorMap[position] = colorMap[group]!!
                            }

                            selections.add(group)
                            selectionColors.add(colorMap[group]!!)
                        }
                    }

                    dataPoint.put("x", fcValue)
                    dataPoint.put("y", sigValue)
                    dataPoint.put("id", id)
                    dataPoint.put("gene", gene)
                    dataPoint.put("comparison", comparison)
                    dataPoint.put("selections", JSONArray(selections))
                    dataPoint.put("colors", JSONArray(selectionColors))

                    val pointColor = if (selectionColors.isNotEmpty()) selectionColors[0] else "#808080"
                    dataPoint.put("color", pointColor)
                    jsonData.put(dataPoint)
                }

                // Update settings with the new color map
                val updatedSettings = curtainSettings.copy(colorMap = colorMap)
                Log.d("VolcanoPlot", "Updated color map: $colorMap")
                // Update volcano axis settings
                val settings = viewModel.curtainSettings.value
                if (settings != null) {
                    val volcanoAxis = settings.volcanoAxis
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
                    val updatedSettingsWithAxis = updatedSettings.copy(volcanoAxis = updatedVolcanoAxis)
                    viewModel.updateCurtainSettings(updatedSettingsWithAxis)
                }

                val html = createVolcanoPlotHtml(jsonData.toString())
                binding.webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
                showLoading(false)
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

    private fun loadVolcanoPlotDefer() {
        Log.d("VolcanoPlot", "Loading volcano plot")
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        // Show loading initially
        showLoading(true)

        // Launch a coroutine in the IO dispatcher for background processing
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Process data in background thread
                val result = processVolcanoData(curtainData, curtainSettings)

                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    val updatedSettings = curtainSettings.copy(
                        colorMap = result.colorMap,
                        volcanoAxis = result.updatedVolcanoAxis
                    )
                    Log.d("VolcanoPlot", "${updatedSettings.volcanoAxis}")
                    viewModel.updateCurtainSettings(updatedSettings)
                    val html = createVolcanoPlotHtml(result.jsonData)
                    binding.webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        html,
                        "text/html",
                        "utf-8",
                        null
                    )

                    // Update settings with the new color map

                    Log.d("VolcanoPlot", "Updated color map: ${result.updatedVolcanoAxis}")

                    showLoading(false)
                }
            } catch (e: Exception) {
                // Handle errors on the main thread
                withContext(Dispatchers.Main) {
                    Log.e("VolcanoPlot", "Error loading volcano plot: ${e.message}", e)
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Error loading plot: ${e.message}"
                    showLoading(false)
                }
            }
        }
    }

    private fun getSignificantGroup(fcValue: Double, sigValue: Double, settings: CurtainSettings): Pair<String, String> {
        val ylog = -Math.log10(settings.pCutoff)
        val groups = mutableListOf<String>()
        var position = ""

        if (ylog > sigValue) {
            groups.add("P-value > ${settings.pCutoff}")
            position = "P-value > "
        } else {
            groups.add("P-value <= ${settings.pCutoff}")
            position = "P-value <= "
        }

        if (Math.abs(fcValue) > settings.log2FCCutoff) {
            groups.add("FC > ${settings.log2FCCutoff}")
            position += "FC > "
        } else {
            groups.add("FC <= ${settings.log2FCCutoff}")
            position += "FC <= "
        }

        return Pair(groups.joinToString(";"), position)
    }

    private fun generateTextAnnotationsJS(textAnnotation: Map<String, Any>): String {
        if (textAnnotation.isEmpty()) return ""
        
        val jsCode = StringBuilder()
        textAnnotation.forEach { (title, annotationData) ->
            // Cast annotation data to Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val data = annotationData as? Map<String, Any> ?: return@forEach
            
            // Check if this annotation should be shown
            @Suppress("UNCHECKED_CAST")
            val annotationDetails = data["data"] as? Map<String, Any> ?: return@forEach
            val showAnnotation = annotationDetails["showannotation"] as? Boolean ?: false
            
            if (showAnnotation) {
                // Extract annotation properties
                val text = JSONObject.quote(annotationDetails["text"]?.toString() ?: title)
                val x = when (val xVal = annotationDetails["x"]) {
                    is Number -> xVal.toString()
                    is String -> if (xVal.toDoubleOrNull() != null) xVal else "0"
                    else -> "0"
                }
                val y = when (val yVal = annotationDetails["y"]) {
                    is Number -> yVal.toString()
                    is String -> if (yVal.toDoubleOrNull() != null) yVal else "0"
                    else -> "0"
                }
                val xanchor = JSONObject.quote(annotationDetails["xanchor"]?.toString() ?: "center")
                val yanchor = JSONObject.quote(annotationDetails["yanchor"]?.toString() ?: "bottom")
                val showarrow = annotationDetails["showarrow"] as? Boolean ?: true
                val arrowhead = when (val arrowheadVal = annotationDetails["arrowhead"]) {
                    is Number -> arrowheadVal.toString()
                    is String -> if (arrowheadVal.toIntOrNull() != null) arrowheadVal else "2"
                    else -> "2"
                }
                val arrowsize = when (val arrowsizeVal = annotationDetails["arrowsize"]) {
                    is Number -> arrowsizeVal.toString()
                    is String -> if (arrowsizeVal.toDoubleOrNull() != null) arrowsizeVal else "1"
                    else -> "1"
                }
                val arrowwidth = when (val arrowwidthVal = annotationDetails["arrowwidth"]) {
                    is Number -> arrowwidthVal.toString()
                    is String -> if (arrowwidthVal.toDoubleOrNull() != null) arrowwidthVal else "2"
                    else -> "2"
                }
                val arrowcolor = JSONObject.quote(annotationDetails["arrowcolor"]?.toString() ?: "#000000")
                val ax = when (val axVal = annotationDetails["ax"]) {
                    is Number -> axVal.toString()
                    is String -> if (axVal.toDoubleOrNull() != null) axVal else "0"
                    else -> "0"
                }
                val ay = when (val ayVal = annotationDetails["ay"]) {
                    is Number -> ayVal.toString()
                    is String -> if (ayVal.toDoubleOrNull() != null) ayVal else "-40"
                    else -> "-40"
                }
                val font = annotationDetails["font"] as? Map<String, Any>
                val fontColor = JSONObject.quote(font?.get("color")?.toString() ?: "#000000")
                val fontSize = when (val fontSizeVal = font?.get("size")) {
                    is Number -> fontSizeVal.toString()
                    is String -> if (fontSizeVal.toIntOrNull() != null) fontSizeVal else "12"
                    else -> "12"
                }
                val fontFamily = JSONObject.quote(font?.get("family")?.toString() ?: "Arial")

                jsCode.append("""
                    annotations.push({
                        text: $text,
                        x: $x,
                        y: $y,
                        xanchor: $xanchor,
                        yanchor: $yanchor,
                        showarrow: $showarrow,
                        arrowhead: $arrowhead,
                        arrowsize: $arrowsize,
                        arrowwidth: $arrowwidth,
                        arrowcolor: $arrowcolor,
                        ax: $ax,
                        ay: $ay,
                        font: {
                            color: $fontColor,
                            size: $fontSize,
                            family: $fontFamily
                        }
                    });
                """.trimIndent())
            }
        }
        return jsCode.toString()
    }

    private fun createVolcanoPlotHtml(jsonData: String): String {
        val curtainSettings = viewModel.curtainSettings.value ?: throw IllegalStateException("Curtain settings not available")
        Log.d("VolcanoPlot", "Creating volcano plot HTML with settings: ${curtainSettings.volcanoAxis}")
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

            // Process text annotations
            const annotations = [];
            ${generateTextAnnotationsJS(curtainSettings.textAnnotation)}

            // Create layout with cutoff lines
            const layout = {
                title: ${JSONObject.quote(curtainSettings.volcanoPlotTitle)},
                xaxis: {
                    title: {
                        text: ${JSONObject.quote(curtainSettings.volcanoAxis.x)}
                    },
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
                    title: {
                        text: ${JSONObject.quote(curtainSettings.volcanoAxis.y)}
                    },
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
                    b: ${(curtainSettings.volcanoPlotDimension?.margin?.bottom ?: 50)}, 
                    t: ${curtainSettings.volcanoPlotDimension?.margin?.top ?: 50}
                },
                hovermode: 'closest',
                // Legend at the bottom
                legend: {
                    orientation: 'h',  
                    yanchor: 'top',
                    y: -0.2,
                    xanchor: 'center',
                    x: 0.5 
                },
                paper_bgcolor: ${if (curtainSettings.backGroundColorGrey) "'#f0f0f0'" else "'white'"},
                plot_bgcolor: ${if (curtainSettings.backGroundColorGrey) "'#f0f0f0'" else "'white'"},
                annotations: annotations
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
                    color: 'rgb(21,4,4)',
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
                    color: 'rgb(21,4,4)',
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
                    color: 'rgb(21,4,4)',
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