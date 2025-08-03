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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.proteo.curtain.AppData
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.VolcanoAxis
import info.proteo.curtain.presentation.viewmodels.AnnotationCommand
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.databinding.FragmentVolcanoPlotTabBinding
import info.proteo.curtain.utils.EdgeToEdgeHelper
import info.proteo.curtain.data.models.VolcanoPointDetails
import info.proteo.curtain.data.models.VolcanoPointSelection
import info.proteo.curtain.presentation.dialogs.VolcanoPointDetailsDialog
import android.webkit.JavascriptInterface
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
    
    // Variable to track if a volcano plot refresh is in progress
    private var isRefreshing = false
    
    // Store the color map for trace groups to use in point selection dialogs
    private var currentColorMap: Map<String, String> = emptyMap()
    
    // Dictionary to track trace group counts as volcano plot is drawn
    private var traceGroupCounts: MutableMap<String, Int> = mutableMapOf()


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
        setupEditToggle()
        
        // Primary observer for curtain data changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.curtainData.collect { curtainData ->
                    if (curtainData != null) {
                        loadVolcanoPlotDefer()
                        // Update FAB visibility when data changes
                        updateFabVisibility()
                    }
                }
            }
        }
        
        // Direct volcano plot refresh trigger for search updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastTriggerValue = -1
                viewModel.volcanoPlotRefreshTrigger.collect { triggerValue ->
                    if (lastTriggerValue != -1 && triggerValue != lastTriggerValue) {
                        val curtainData = viewModel.curtainData.value
                        if (curtainData != null) {
                            viewModel.searchService.saveSearchListsToCurtainData(curtainData)
                            loadVolcanoPlotDefer()
                        }
                    }
                    lastTriggerValue = triggerValue
                }
            }
        }
        
        // Note: Annotations are now handled via direct settings modification
        // The volcano plot will automatically refresh when switching tabs via onResume()
    }
    
    private var lastSettingsHash: Int = 0
    
    override fun onResume() {
        super.onResume()
        // Force refresh when returning to this tab to ensure search changes and annotations are reflected
        val curtainData = viewModel.curtainData.value
        val curtainSettings = viewModel.curtainSettings.value
        
        if (curtainData != null) {
            // Check if settings have changed (particularly annotations)
            val currentSettingsHash = curtainSettings?.textAnnotation?.hashCode() ?: 0
            val shouldRefresh = currentSettingsHash != lastSettingsHash
            
            if (shouldRefresh) {
                android.util.Log.d("VolcanoPlot", "Settings changed, refreshing plot (annotations may have been added/removed)")
                lastSettingsHash = currentSettingsHash
            }
            
            // Ensure differential data is processed first
            lifecycleScope.launch {
                viewModel.curtainDataService.processDataAfterImport()
                viewModel.searchService.saveSearchListsToCurtainData(curtainData)
                loadVolcanoPlotDefer()
                
                // Update FAB visibility after plot is loaded
                withContext(Dispatchers.Main) {
                    updateFabVisibility()
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

            // Add JavaScript interface for point selection
            addJavascriptInterface(VolcanoPlotJavaScriptInterface(), "Android")

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
    
    private var isEditModeEnabled = false
    
    private fun setupEditToggle() {
        binding.fabEditToggle.setOnClickListener {
            isEditModeEnabled = !isEditModeEnabled
            Log.d("VolcanoPlot", "FAB clicked, edit mode now: $isEditModeEnabled")
            toggleEditMode(isEditModeEnabled)
            updateFabAppearance()
        }
    }
    
    private fun updateFabAppearance() {
        // Update FAB icon based on mode
        val iconRes = if (isEditModeEnabled) {
            android.R.drawable.ic_menu_close_clear_cancel
        } else {
            info.proteo.curtain.R.drawable.ic_edit_24
        }
        binding.fabEditToggle.setImageResource(iconRes)
        
        // Update FAB color
        val colorTint = if (isEditModeEnabled) {
            androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
        } else {
            // Use theme's primary color
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            typedValue.data.toInt()
        }
        binding.fabEditToggle.backgroundTintList = 
            android.content.res.ColorStateList.valueOf(colorTint)
    }
    
    private fun updateFabVisibility() {
        val curtainSettings = viewModel.curtainSettings.value
        val hasAnnotations = curtainSettings?.textAnnotation?.isNotEmpty() == true
        
        if (hasAnnotations) {
            binding.fabEditToggle.visibility = View.VISIBLE
        } else {
            binding.fabEditToggle.visibility = View.GONE
            // If FAB is hidden, make sure edit mode is disabled
            if (isEditModeEnabled) {
                isEditModeEnabled = false
                toggleEditMode(false)
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

        // Clear trace group counts for fresh tracking
        traceGroupCounts.clear()

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

        // Debug: Log all available keys in dataMap
        Log.d("VolcanoPlot", "Available keys in dataMap: ${curtainData.dataMap.keys}")
        
        var processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>

        if (processedData == null) {
            // Try the correct key from web frontend
            processedData = curtainData.dataMap["processed"] as? List<Map<String, Any>>
            
            if (processedData != null) {
                Log.d("VolcanoPlot", "Using alternative data key, found ${processedData.size} rows")
            } else {
                Log.e("VolcanoPlot", "No differential data found in any expected key")
                throw IllegalStateException("No differential analysis data available")
            }
        }

        Log.d("VolcanoPlot", "Processing ${processedData.size} rows for volcano plot")
        for (row in processedData) {
            val comparison = if (comparisonColumn.isNotEmpty()) row[comparisonColumn]?.toString() ?: "" else ""
            val id = row[idColumn]?.toString() ?: ""
            
            if (id.isEmpty()) {
                Log.w("VolcanoPlot", "Empty ID found, skipping row")
                continue
            }
            // Get gene name following web frontend workflow: UniProt data > gene column > ID
            var gene = id
            if (curtainData.fetchUniprot) {
                try {
                    val uniprotData = viewModel.uniprotService.getUniprotFromPrimary(id)
                    val geneNames = uniprotData?.get("Gene Names") as? String
                    if (!geneNames.isNullOrEmpty()) {
                        gene = geneNames
                    }
                } catch (e: Exception) {
                    Log.w("VolcanoPlot", "Error getting UniProt data for $id: ${e.message}")
                }
            }
            
            // Fallback to gene column if no UniProt gene name found
            if (gene == id && geneColumn.isNotEmpty()) {
                val geneFromColumn = row[geneColumn]?.toString()
                if (!geneFromColumn.isNullOrEmpty()) {
                    gene = geneFromColumn
                }
            }

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
                        
                        // Track count for user selection trace group
                        traceGroupCounts[selectionName] = traceGroupCounts.getOrDefault(selectionName, 0) + 1
                    }
                }
            }

            // If not part of any selection, create significance groups or add to background
            if (selections.isEmpty()) {
                if (curtainSettings.backGroundColorGrey) {
                    // Add to Background category
                    selections.add("Background")
                    selectionColors.add("#a4a2a2")  // Gray with opacity
                    
                    // Track count for background trace group
                    traceGroupCounts["Background"] = traceGroupCounts.getOrDefault("Background", 0) + 1
                } else {
                    // Use significance grouping system
                    val (groupText, position) = getSignificantGroup(fcValue, sigValue, curtainSettings)
                    val group = "$groupText ($comparison)"
                    
                    // Track count for trace group
                    traceGroupCounts[group] = traceGroupCounts.getOrDefault(group, 0) + 1

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
            // Escape gene name to prevent JavaScript issues
            dataPoint.put("gene", gene.replace("\"", "\\\"").replace("'", "\\'"))
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
            // Debug: Log all available keys in dataMap
            Log.d("VolcanoPlot", "Available keys in dataMap: ${curtainData.dataMap.keys}")
            
            var processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>

            if (processedData == null) {
                // Try the correct key from web frontend
                processedData = curtainData.dataMap["processed"] as? List<Map<String, Any>>
                
                if (processedData != null) {
                    Log.d("VolcanoPlot", "Using alternative data key, found ${processedData.size} rows")
                }
            }

            if (processedData != null) {
                Log.d("VolcanoPlot", "Processing ${processedData.size} rows for volcano plot")
                for (row in processedData) {
                    val comparison = if (comparisonColumn.isNotEmpty()) row[comparisonColumn]?.toString() ?: "" else ""
                    val id = row[idColumn]?.toString() ?: ""
                    
                    if (id.isEmpty()) {
                        Log.w("VolcanoPlot", "Empty ID found, skipping row")
                        continue
                    }
                    // Get gene name following web frontend workflow: UniProt data > gene column > ID
            var gene = id
            if (curtainData.fetchUniprot) {
                try {
                    val uniprotData = viewModel.uniprotService.getUniprotFromPrimary(id)
                    val geneNames = uniprotData?.get("Gene Names") as? String
                    if (!geneNames.isNullOrEmpty()) {
                        gene = geneNames
                    }
                } catch (e: Exception) {
                    Log.w("VolcanoPlot", "Error getting UniProt data for $id: ${e.message}")
                }
            }
            
            // Fallback to gene column if no UniProt gene name found
            if (gene == id && geneColumn.isNotEmpty()) {
                val geneFromColumn = row[geneColumn]?.toString()
                if (!geneFromColumn.isNullOrEmpty()) {
                    gene = geneFromColumn
                }
            }

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
                                
                                // Track count for user selection trace group
                                traceGroupCounts[selectionName] = traceGroupCounts.getOrDefault(selectionName, 0) + 1
                            }
                        }
                    }

                    // If not part of any selection, create significance groups or add to background
                    if (selections.isEmpty()) {
                        if (curtainSettings.backGroundColorGrey) {
                            // Add to Background category
                            selections.add("Background")
                            selectionColors.add("#a4a2a2")  // Gray with opacity
                            
                            // Track count for background trace group
                            traceGroupCounts["Background"] = traceGroupCounts.getOrDefault("Background", 0) + 1
                        } else {
                            // Use significance grouping system
                            val (groupText, position) = getSignificantGroup(fcValue, sigValue, curtainSettings)
                            val group = "$groupText ($comparison)"
                            
                            // Track count for trace group
                            traceGroupCounts[group] = traceGroupCounts.getOrDefault(group, 0) + 1

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
                    // Escape gene name to prevent JavaScript issues
                    dataPoint.put("gene", gene.replace("\"", "\\\"").replace("'", "\\'"))
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
                binding.errorText.text = "No differential analysis data available. Please select a comparison first."
                showLoading(false)
            }
        } catch (e: Exception) {
            Log.e("VolcanoPlot", "Error loading volcano plot: ${e.message}", e)
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Error loading plot: ${e.message}"
            showLoading(false)
        }
    }

    /**
     * Public method to force refresh the volcano plot from external components
     */
    fun refreshVolcanoPlot() {
        val curtainData = viewModel.curtainData.value
        if (curtainData != null) {
            lifecycleScope.launch {
                viewModel.curtainDataService.processDataAfterImport()
                viewModel.searchService.saveSearchListsToCurtainData(curtainData)
                loadVolcanoPlotDefer()
            }
        }
    }
    
    private fun loadVolcanoPlotDefer() {
        // Prevent multiple simultaneous refreshes
        if (isRefreshing) {
            return
        }
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        isRefreshing = true
        showLoading(true)

        // Launch a coroutine in the IO dispatcher for background processing
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Process data in background thread
                val result = processVolcanoData(curtainData, curtainSettings)

                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    // Store color map for use in point selection dialogs
                    currentColorMap = result.colorMap
                    
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

                    showLoading(false)
                    isRefreshing = false
                }
            } catch (e: Exception) {
                // Handle errors on the main thread
                withContext(Dispatchers.Main) {
                    Log.e("VolcanoPlot", "Error loading volcano plot: ${e.message}", e)
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Error loading plot: ${e.message}"
                    showLoading(false)
                    isRefreshing = false
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

                // Get annotationID for dragging support (it's nested under "data")  
                val annotationId = JSONObject.quote(annotationDetails["annotationID"]?.toString() ?: title)

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
                        },
                        annotationID: $annotationId
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
                position: relative;
            }
            #touchOverlay {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                z-index: 1000;
                pointer-events: none;
                background: transparent;
            }
            #touchOverlay.edit-mode {
                pointer-events: auto;
            }
        </style>
    </head>
    <body>
        <div id="plot"></div>
        <div id="touchOverlay"></div>
        <script>
            const data = ${jsonData};

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

            // Create traces exactly matching Angular frontend creation order
            // Angular creates: user selections first, then background/significance groups
            const traces = [];
            
            // First: Add user selection traces (matching Angular creation order)
            for (const groupName in selectionGroups) {
                const group = selectionGroups[groupName];
                
                // Skip background/significance groups in this pass
                if (groupName === "Background" || 
                    groupName === "Other" || 
                    (groupName.includes("(") && (groupName.includes("P-value") || groupName.includes("FC")))) {
                    continue;
                }
                
                // User selection operations (from search, manual selection, etc.)
                traces.push({
                    x: group.points.map(point => point.x),
                    y: group.points.map(point => point.y),
                    mode: 'markers',
                    type: 'scatter',
                    marker: {
                        color: group.color,
                        size: ${curtainSettings.scatterPlotMarkerSize},
                        opacity: 0.7
                    },
                    text: group.points.map(point => point.gene),
                    hoverinfo: 'text+x+y',
                    name: group.name
                });
            }
            
            // Second: Add background and significance group traces
            for (const groupName in selectionGroups) {
                const group = selectionGroups[groupName];
                
                // Only process background/significance groups in this pass
                if (!(groupName === "Background" || 
                      groupName === "Other" || 
                      (groupName.includes("(") && (groupName.includes("P-value") || groupName.includes("FC"))))) {
                    continue;
                }
                
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

            // CRITICAL: Reverse traces array to match Angular frontend (volcano-plot.component.ts line 484)
            // This replicates the exact same ordering: user selections on top, background behind
            traces.reverse();

            // Adjust background color and opacity
            const backgroundTrace = traces.find(trace => trace.name === 'Background');
            if (backgroundTrace && ${curtainSettings.backGroundColorGrey}) {
                backgroundTrace.marker.color = '#a4a2a2';
                backgroundTrace.marker.opacity = 0.3;
            }
            
            // Create the plot with all traces (now in exact Angular frontend order)
            Plotly.newPlot('plot', traces, layout, {
                responsive: true,
                displayModeBar: false,
                editable: false
            });
            
            // Add event listener for annotation drag/relayout events
            document.getElementById('plot').on('plotly_relayout', function(eventData) {
                console.log('plotly_relayout event:', Object.keys(eventData));
                
                // Check if any annotations were moved
                for (const key in eventData) {
                    if (key.startsWith('annotations[') && key.includes('].x')) {
                        try {
                            const annotationIndex = key.match(/annotations\\[(\\d+)\\]/)[1];
                            const xKey = 'annotations[' + annotationIndex + '].x';
                            const yKey = 'annotations[' + annotationIndex + '].y';
                            
                            if (eventData[xKey] !== undefined && eventData[yKey] !== undefined) {
                                const newX = eventData[xKey];
                                const newY = eventData[yKey];
                                const annotationId = layout.annotations[annotationIndex].annotationID;
                                
                                console.log('Annotation moved:', annotationId, 'to:', newX, newY);
                                
                                // Call Android method to update annotation position
                                Android.updateAnnotationPosition(annotationId, newX, newY);
                            }
                        } catch (e) {
                            console.log('Error processing annotation move:', e);
                        }
                    }
                }
            });
            
            // Track edit mode state
            let isEditMode = false;
            
            // Touch overlay drag handling
            let isDragging = false;
            let draggedAnnotation = null;
            let dragStartX = 0;
            let dragStartY = 0;
            let dragStartAnnotationX = 0;
            let dragStartAnnotationY = 0;
            let finalTextX = 0;
            let finalTextY = 0;
            
            // Function to convert screen coordinates to plot coordinates
            function screenToPlotCoords(screenX, screenY) {
                const plotDiv = document.getElementById('plot');
                if (!plotDiv) {
                    console.error('Plot div not found');
                    return { x: 0, y: 0 };
                }
                
                const plotBounds = plotDiv.getBoundingClientRect();
                
                // Try to find the actual plot area - check multiple possible selectors
                let plotInnerBounds = null;
                const possibleSelectors = [
                    '.plot-container .plotly',
                    '.plotly',
                    '.main-svg',
                    '.svg-container'
                ];
                
                for (const selector of possibleSelectors) {
                    const element = plotDiv.querySelector(selector);
                    if (element) {
                        plotInnerBounds = element.getBoundingClientRect();
                        break;
                    }
                }
                
                // Fallback to using the main plot div bounds
                if (!plotInnerBounds) {
                    plotInnerBounds = plotBounds;
                }
                
                // Calculate relative position within the plot area
                const relativeX = screenX - plotInnerBounds.left;
                const relativeY = screenY - plotInnerBounds.top;
                
                // Convert to plot coordinates using Plotly's coordinate system
                if (plotDiv._fullLayout) {
                    const xaxis = plotDiv._fullLayout.xaxis;
                    const yaxis = plotDiv._fullLayout.yaxis;
                    
                    if (xaxis && yaxis) {
                        // Use Plotly's coordinate conversion if available
                        const plotWidth = plotInnerBounds.width;
                        const plotHeight = plotInnerBounds.height;
                        
                        // Convert from pixel coordinates to data coordinates
                        const xRange = xaxis.range || [-2, 2];
                        const yRange = yaxis.range || [0, 10];
                        
                        const plotX = xRange[0] + (relativeX / plotWidth) * (xRange[1] - xRange[0]);
                        const plotY = yRange[0] + ((plotHeight - relativeY) / plotHeight) * (yRange[1] - yRange[0]);
                        
                        return { x: plotX, y: plotY };
                    }
                }
                
                // Fallback: return normalized coordinates
                return { 
                    x: relativeX / plotInnerBounds.width, 
                    y: relativeY / plotInnerBounds.height 
                };
            }
            
            // Function to find annotation near screen coordinates
            function findAnnotationNear(screenX, screenY, threshold = 50) {
                const plotDiv = document.getElementById('plot');
                if (!plotDiv._fullLayout || !plotDiv._fullLayout.annotations) {
                    console.log('No plot layout or annotations found');
                    return null;
                }
                
                const annotations = plotDiv._fullLayout.annotations;
                console.log('Checking', annotations.length, 'annotations');
                
                for (let i = 0; i < annotations.length; i++) {
                    const ann = annotations[i];
                    console.log('Annotation', i, ':', ann.annotationID, 'at plot coords:', ann.x, ann.y);
                    console.log('Full annotation object:', ann);
                    
                    // Don't skip annotations without annotationID - we'll match by text instead
                    
                    // Convert annotation plot coordinates to screen coordinates
                    const xaxis = plotDiv._fullLayout.xaxis;
                    const yaxis = plotDiv._fullLayout.yaxis;
                    
                    console.log('Axis info - xaxis:', xaxis ? 'exists' : 'missing', 'yaxis:', yaxis ? 'exists' : 'missing');
                    
                    // Use Plotly's coordinate conversion methods
                    let screenAnnX, screenAnnY;
                    try {
                        screenAnnX = xaxis._offset + xaxis._length * (ann.x - xaxis.range[0]) / (xaxis.range[1] - xaxis.range[0]);
                        screenAnnY = yaxis._offset + yaxis._length * (1 - (ann.y - yaxis.range[0]) / (yaxis.range[1] - yaxis.range[0]));
                    } catch (e) {
                        console.log('Coordinate conversion error:', e);
                        screenAnnX = ann.x;
                        screenAnnY = ann.y;
                    }
                    
                    console.log('Converted to screen coords:', screenAnnX, screenAnnY);
                    console.log('Touch at:', screenX, screenY);
                    
                    // Calculate distance
                    const distance = Math.sqrt(
                        Math.pow(screenX - screenAnnX, 2) + 
                        Math.pow(screenY - screenAnnY, 2)
                    );
                    
                    console.log('Distance:', distance, 'threshold:', threshold);
                    
                    if (distance <= threshold) {
                        console.log('Found matching annotation!');
                        return { annotation: ann, index: i };
                    }
                }
                
                console.log('No annotation found near touch point');
                return null;
            }
            
            // Function to toggle edit mode
            window.toggleEditMode = function(enable) {
                isEditMode = enable;
                console.log('Edit mode:', isEditMode ? 'enabled' : 'disabled');
                
                // Enable/disable touch overlay
                const overlay = document.getElementById('touchOverlay');
                if (isEditMode) {
                    overlay.classList.add('edit-mode');
                    setupTouchHandlers();
                } else {
                    overlay.classList.remove('edit-mode');
                    removeTouchHandlers();
                }
                
                // Update layout to disable/enable interactions
                const updatedLayout = Object.assign({}, layout);
                console.log('Number of annotations:', updatedLayout.annotations ? updatedLayout.annotations.length : 0);
                if (updatedLayout.annotations && updatedLayout.annotations.length > 0) {
                    console.log('Annotation IDs:', updatedLayout.annotations.map(ann => ann.annotationID));
                }
                if (isEditMode) {
                    // Disable plot interactions to prevent interference with annotation dragging
                    updatedLayout.dragmode = false;
                    updatedLayout.hovermode = false;
                } else {
                    // Restore normal interactions
                    updatedLayout.dragmode = 'zoom';
                    updatedLayout.hovermode = 'closest';
                }
                
                // Update plot configuration to enable/disable editing
                const config = {
                    responsive: true,
                    displayModeBar: false, // Always hide the mode bar
                    scrollZoom: !isEditMode, // Disable scroll zoom in edit mode
                    doubleClick: isEditMode ? false : 'reset', // Disable double-click zoom in edit mode
                    editable: false, // Disable Plotly's built-in editing
                };
                
                // Update plot with new config and layout to enable/disable editing
                Plotly.react('plot', traces, updatedLayout, config);
            };
            
            // Touch event handlers
            function setupTouchHandlers() {
                const overlay = document.getElementById('touchOverlay');
                
                overlay.addEventListener('touchstart', handleTouchStart, { passive: false });
                overlay.addEventListener('touchmove', handleTouchMove, { passive: false });
                overlay.addEventListener('touchend', handleTouchEnd, { passive: false });
            }
            
            function removeTouchHandlers() {
                const overlay = document.getElementById('touchOverlay');
                
                overlay.removeEventListener('touchstart', handleTouchStart);
                overlay.removeEventListener('touchmove', handleTouchMove);
                overlay.removeEventListener('touchend', handleTouchEnd);
            }
            
            function handleTouchStart(event) {
                event.preventDefault();
                
                const touch = event.touches[0];
                const rect = event.target.getBoundingClientRect();
                const x = touch.clientX - rect.left;
                const y = touch.clientY - rect.top;
                
                console.log('Touch start at:', x, y);
                
                // Find annotation near touch point
                const found = findAnnotationNear(x, y);
                if (found) {
                    console.log('Found annotation:', found.annotation.annotationID);
                    isDragging = true;
                    draggedAnnotation = found;
                    dragStartX = x;
                    dragStartY = y;
                    dragStartAnnotationX = found.annotation.x;
                    dragStartAnnotationY = found.annotation.y;
                }
            }
            
            function handleTouchMove(event) {
                if (!isDragging || !draggedAnnotation) return;
                
                event.preventDefault();
                
                const touch = event.touches[0];
                
                // Use the overlay element instead of event.target
                const overlay = document.getElementById('touchOverlay');
                if (!overlay) return;
                
                const rect = overlay.getBoundingClientRect();
                const x = touch.clientX - rect.left;
                const y = touch.clientY - rect.top;
                
                // Calculate drag delta
                const deltaX = x - dragStartX;
                const deltaY = y - dragStartY;
                
                console.log('Touch move - current:', x, y, 'start:', dragStartX, dragStartY, 'delta:', deltaX, deltaY);
                
                // Convert screen delta to plot coordinates delta
                const plotCoords = screenToPlotCoords(dragStartX + deltaX, dragStartY + deltaY);
                const startPlotCoords = screenToPlotCoords(dragStartX, dragStartY);
                
                const newX = dragStartAnnotationX + (plotCoords.x - startPlotCoords.x);
                const newY = dragStartAnnotationY + (plotCoords.y - startPlotCoords.y);
                
                // Store the final text position for handleTouchEnd
                finalTextX = newX;
                finalTextY = newY;
                
                console.log('Moving annotation from:', dragStartAnnotationX, dragStartAnnotationY, 'to:', newX, newY);
                
                // Update annotation text position in real-time by changing ax, ay offset
                // Keep arrow tip (x, y) unchanged, only move text via offset
                const currentAnnotation = layout.annotations[draggedAnnotation.index];
                if (currentAnnotation) {
                    const arrowTipX = currentAnnotation.x;  // Original data point position
                    const arrowTipY = currentAnnotation.y;  // Original data point position
                    
                    // Calculate arrow offset for real-time feedback
                    const axOffset = (newX - arrowTipX) * 50;  // Convert to pixel offset
                    const ayOffset = (newY - arrowTipY) * -50; // Negative for Plotly's inverted y
                    
                    // Update only the arrow offset (ax, ay) for real-time visual feedback
                    const update = {};
                    update['annotations[' + draggedAnnotation.index + '].ax'] = axOffset;
                    update['annotations[' + draggedAnnotation.index + '].ay'] = ayOffset;
                    
                    Plotly.relayout('plot', update);
                }
            }
            
            function handleTouchEnd(event) {
                if (!isDragging || !draggedAnnotation) return;
                
                event.preventDefault();
                
                console.log('Touch end - final position:', finalTextX, finalTextY);
                
                // Notify Android of final text position (not arrow tip position)
                // Use annotationID if available, otherwise use annotation text as identifier (strip HTML tags)
                let annotationId = draggedAnnotation.annotation.annotationID;
                if (!annotationId && draggedAnnotation.annotation.text) {
                    // Strip HTML tags from text to match textAnnotation key
                    annotationId = draggedAnnotation.annotation.text.replace(/<[^>]*>/g, '');
                }
                if (!annotationId) {
                    annotationId = 'annotation_' + draggedAnnotation.index;
                }
                
                Android.updateAnnotationPosition(
                    annotationId,
                    finalTextX,
                    finalTextY
                );
                
                // Reset drag state
                isDragging = false;
                draggedAnnotation = null;
            }
            
            // Function to enable annotation editing
            window.enableAnnotationEditing = function() {
                window.toggleEditMode(true);
            };
            
            // Function to disable annotation editing
            window.disableAnnotationEditing = function() {
                window.toggleEditMode(false);
            };
            
            // Add click event handler for point selection (only when not in edit mode)
            document.getElementById('plot').on('plotly_click', function(eventData) {
                if (isEditMode) {
                    // In edit mode, ignore point clicks
                    return;
                }
                
                if (eventData.points && eventData.points.length > 0) {
                    const point = eventData.points[0];
                    const traceGroup = point.fullData.name; // This is the trace name/group
                    const pointIndex = point.pointIndex;
                    
                    // Find the corresponding data point
                    const dataPoint = data.find(d => 
                        d.x === point.x && 
                        d.y === point.y
                    );
                    
                    if (dataPoint && typeof Android !== 'undefined') {
                        try {
                            const pointJson = JSON.stringify({
                                id: dataPoint.id,
                                gene: dataPoint.gene,
                                x: dataPoint.x,
                                y: dataPoint.y,
                                comparison: dataPoint.comparison || '',
                                traceGroup: traceGroup || ''
                            });
                            
                            // Call Android interface with point data and click coordinates
                            Android.onPointClicked(pointJson, point.x, point.y);
                        } catch (e) {
                            console.error('Error calling Android interface:', e);
                        }
                    }
                }
            });
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

    /**
     * Handle annotation commands from the annotation service (matching Angular frontend pattern)
     */
    // DEPRECATED: Annotation handling now done via direct settings modification
    // private fun handleAnnotationCommand(command: AnnotationCommand) { ... }
    
    /**
     * Create annotations for data points using primary IDs (matching Angular frontend pattern)
     */
    private fun annotateDataPoints(primaryIds: List<String>) {
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        // Get differential data (equivalent to Angular's currentDF)
        val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>
        if (processedData == null) {
            Log.e("VolcanoPlot", "No processed differential data available for annotation")
            return
        }
        
        // Get differential form settings (equivalent to Angular's differentialForm)
        val diffForm = curtainData.differentialForm
        val fcColumn = diffForm.foldChange
        val sigColumn = diffForm.significant
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        
        // Filter data by primary IDs (equivalent to Angular's DataFrame.where())
        val annotatedData = processedData.filter { row ->
            val primaryId = row[idColumn]?.toString()
            primaryId != null && primaryIds.contains(primaryId)
        }
        
        if (annotatedData.isEmpty()) {
            Log.w("VolcanoPlot", "No data found for primary IDs: $primaryIds")
            return
        }
        
        // Create annotations for each filtered data point
        val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
        val newAnnotations = mutableListOf<Map<String, Any>>()
        
        for (dataPoint in annotatedData) {
            val primaryId = dataPoint[idColumn]?.toString() ?: continue
            // Get gene name from UniProt data if available, fallback to gene column or ID
            val uniprotData = viewModel.uniprotService.getUniprotFromPrimary(primaryId)
            val gene = if (uniprotData != null) {
                uniprotData["Gene Names"] as? String ?: primaryId
            } else if (geneColumn.isNotEmpty()) {
                dataPoint[geneColumn]?.toString() ?: primaryId
            } else {
                primaryId
            }
            
            // Create annotation title (same logic as Angular frontend)
            val title = if (gene.isNotEmpty() && gene != primaryId) {
                "$gene($primaryId)"
            } else {
                primaryId
            }
            
            // Skip if annotation already exists
            if (existingAnnotations.containsKey(title)) {
                Log.d("VolcanoPlot", "Annotation already exists for: $title")
                continue
            }
            
            // Get coordinates from data
            val x = when (val fc = dataPoint[fcColumn]) {
                is Number -> fc.toDouble()
                is String -> fc.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            
            val y = when (val sig = dataPoint[sigColumn]) {
                is Number -> sig.toDouble()
                is String -> sig.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            
            // Create annotation data structure (matching Angular frontend exactly)
            val annotationData = mapOf(
                "primary_id" to primaryId,
                "title" to title,
                "data" to mapOf(
                    "xref" to "x",
                    "yref" to "y",
                    "x" to x,
                    "y" to y,
                    "text" to "<b>$title</b>",
                    "showarrow" to true,
                    "arrowhead" to 1,
                    "arrowsize" to 1,
                    "arrowwidth" to 1,
                    "ax" to -20,
                    "ay" to -20,
                    "font" to mapOf(
                        "size" to 15,
                        "color" to "#000000",
                        "family" to "Arial, sans-serif"
                    ),
                    "showannotation" to true,
                    "annotationID" to title
                )
            )
            
            existingAnnotations[title] = annotationData
            newAnnotations.add(annotationData)
            
            Log.d("VolcanoPlot", "Created annotation: $title at ($x, $y)")
        }
        
        if (newAnnotations.isNotEmpty()) {
            // Update settings (matching Angular frontend pattern)
            val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
            viewModel.updateCurtainSettings(updatedSettings)
            
            // Refresh plot to show new annotations
            Log.d("VolcanoPlot", "Reloading plot to show new annotations")
            loadVolcanoPlotDefer()
            
            Log.d("VolcanoPlot", "Added ${newAnnotations.size} annotations")
        }
    }
    
    /**
     * Remove annotations for specific primary IDs (matching Angular frontend pattern)
     */
    private fun removeAnnotatedDataPoints(primaryIds: List<String>) {
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
        var removedCount = 0
        
        // Find and remove annotations by primary ID or title
        val annotationsToRemove = mutableListOf<String>()
        
        for ((annotationKey, annotationValue) in existingAnnotations) {
            @Suppress("UNCHECKED_CAST")
            val annotation = annotationValue as? Map<String, Any>
            val annotationPrimaryId = annotation?.get("primary_id")?.toString()
            
            // Remove if primary ID matches or if annotation key contains the primary ID
            if (annotationPrimaryId != null && primaryIds.contains(annotationPrimaryId)) {
                annotationsToRemove.add(annotationKey)
            } else {
                // Also check if any primary ID is contained in the annotation key
                for (primaryId in primaryIds) {
                    if (annotationKey.contains(primaryId)) {
                        annotationsToRemove.add(annotationKey)
                        break
                    }
                }
            }
        }
        
        // Remove found annotations
        for (key in annotationsToRemove) {
            existingAnnotations.remove(key)
            removedCount++
        }
        
        if (removedCount > 0) {
            // Update settings
            val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
            viewModel.updateCurtainSettings(updatedSettings)
            
            // Refresh plot to hide removed annotations
            Log.d("VolcanoPlot", "Reloading plot to hide removed annotations")
            loadVolcanoPlotDefer()
            
            Log.d("VolcanoPlot", "Removed $removedCount annotations")
        }
    }
    
    /**
     * Refresh plot annotations without full reload (matching Angular frontend pattern)
     */
    private fun refreshPlotWithAnnotations() {
        val curtainSettings = viewModel.curtainSettings.value ?: return
        val annotationsJS = generateTextAnnotationsJS(curtainSettings.textAnnotation)
        
        val jsCode = """
            (function() {
                const annotations = [];
                $annotationsJS
                
                const update = {
                    'annotations': annotations
                };
                
                if (typeof Plotly !== 'undefined' && document.getElementById('plot')) {
                    Plotly.relayout('plot', update);
                }
            })();
        """.trimIndent()
        
        binding.webView.evaluateJavascript(jsCode) { result ->
            Log.d("VolcanoPlot", "Updated annotations via JavaScript")
        }
    }

    /**
     * JavaScript interface for handling volcano plot interactions
     */
    inner class VolcanoPlotJavaScriptInterface {
        @JavascriptInterface
        fun onPointClicked(pointDataJson: String, clickX: Double, clickY: Double) {
            Log.d("VolcanoPlot", "Point clicked: $pointDataJson at ($clickX, $clickY)")
            
            lifecycleScope.launch {
                try {
                    val pointData = JSONObject(pointDataJson)
                    val proteinId = pointData.getString("id")
                    val geneName = pointData.getString("gene")
                    val foldChange = pointData.getDouble("x")
                    val significance = pointData.getDouble("y")
                    val comparison = pointData.optString("comparison", "")
                    val traceGroup = pointData.optString("traceGroup", "")
                    
                    val selectedPoint = VolcanoPointDetails(
                        proteinId = proteinId,
                        geneName = geneName,
                        foldChange = foldChange,
                        significance = significance,
                        comparison = comparison,
                        traceGroup = traceGroup.takeIf { it.isNotEmpty() },
                        traceGroupColor = getTraceGroupColor(traceGroup, currentColorMap)
                    )
                    
                    // Find nearby points
                    val nearbyPoints = findNearbyPoints(selectedPoint, clickX, clickY)
                    
                    val pointSelection = VolcanoPointSelection(
                        selectedPoint = selectedPoint,
                        nearbyPoints = nearbyPoints,
                        clickX = clickX,
                        clickY = clickY
                    )
                    
                    // Show dialog on main thread
                    withContext(Dispatchers.Main) {
                        if (isAdded && !isDetached) {
                            val dialog = VolcanoPointDetailsDialog.newInstance(pointSelection)
                            dialog.show(parentFragmentManager, "VolcanoPointDetailsDialog")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("VolcanoPlot", "Error processing point click", e)
                }
            }
        }
        
        @JavascriptInterface
        fun updateAnnotationPosition(annotationId: String, newX: Double, newY: Double) {
            Log.d("VolcanoPlot", "JavaScript called updateAnnotationPosition: $annotationId to ($newX, $newY)")
            
            lifecycleScope.launch {
                try {
                    val curtainSettings = viewModel.curtainSettings.value ?: return@launch
                    val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
                    
                    // Find annotation by annotationID and update position
                    val annotationToUpdate = existingAnnotations[annotationId]
                    
                    if (annotationToUpdate != null) {
                        val annotationMap = annotationToUpdate as? Map<String, Any>
                        val annotationDataInner = annotationMap?.get("data") as? Map<String, Any>
                        
                        if (annotationDataInner != null) {
                            val updatedDataInner = annotationDataInner.toMutableMap()
                            
                            // Get current arrow tip position (keep it unchanged)
                            val arrowTipX = annotationDataInner["x"] as? Double ?: 0.0
                            val arrowTipY = annotationDataInner["y"] as? Double ?: 0.0
                            
                            // Calculate arrow offset (text position relative to arrow tip)
                            val axOffset = (newX - arrowTipX) * 50  // Convert to pixel offset approximation
                            val ayOffset = (newY - arrowTipY) * -50 // Negative because Plotly's ay is inverted
                            
                            // Update arrow offset instead of arrow tip position
                            updatedDataInner["ax"] = axOffset
                            updatedDataInner["ay"] = ayOffset
                            
                            // Keep x, y unchanged (arrow tip stays at data point)
                            
                            val updatedAnnotationData = (annotationMap as Map<String, Any>).toMutableMap()
                            updatedAnnotationData["data"] = updatedDataInner
                            
                            existingAnnotations[annotationId] = updatedAnnotationData
                            
                            // Update settings
                            val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
                            viewModel.updateCurtainSettings(updatedSettings)
                            
                            // Update the plot display immediately
                            updatePlotAnnotationOffset(annotationId, axOffset, ayOffset)
                            
                            Log.d("VolcanoPlot", "Updated annotation position for: $annotationId")
                        }
                    } else {
                        Log.w("VolcanoPlot", "Annotation not found for update: $annotationId")
                    }
                } catch (e: Exception) {
                    Log.e("VolcanoPlot", "Error updating annotation position", e)
                }
            }
        }
    }
    
    private fun updatePlotAnnotationOffset(annotationId: String, axOffset: Double, ayOffset: Double) {
        // Execute JavaScript to update the annotation position in the plot
        binding.webView.evaluateJavascript("""
            (function() {
                try {
                    const plotDiv = document.getElementById('plot');
                    if (!plotDiv || !plotDiv.layout || !plotDiv.layout.annotations) {
                        console.log('Plot or annotations not found');
                        return;
                    }
                    
                    const annotations = plotDiv.layout.annotations;
                    let annotationIndex = -1;
                    
                    // Find the annotation by annotationID
                    for (let i = 0; i < annotations.length; i++) {
                        if (annotations[i].annotationID === '$annotationId') {
                            annotationIndex = i;
                            break;
                        }
                    }
                    
                    if (annotationIndex >= 0) {
                        // Update only the arrow offset (ax, ay) to move text while keeping arrow tip fixed
                        const update = {};
                        update['annotations[' + annotationIndex + '].ax'] = $axOffset;
                        update['annotations[' + annotationIndex + '].ay'] = $ayOffset;
                        
                        // Apply the update to the plot
                        Plotly.relayout('plot', update);
                        console.log('Updated annotation text position for: $annotationId, ax: $axOffset, ay: $ayOffset');
                    } else {
                        console.log('Annotation not found in plot: $annotationId');
                    }
                } catch (e) {
                    console.error('Error updating annotation display:', e);
                }
            })();
        """.trimIndent(), null)
    }
    
    /**
     * Find nearby points within a reasonable distance from the click point
     * Also determines the trace group for each nearby point
     */
    private fun findNearbyPoints(selectedPoint: VolcanoPointDetails, clickX: Double, clickY: Double): List<VolcanoPointDetails> {
        val curtainData = viewModel.curtainData.value ?: return emptyList()
        val curtainSettings = viewModel.curtainSettings.value ?: return emptyList()
        
        Log.d("VolcanoPlot", "Finding nearby points for ${selectedPoint.proteinId} at click ($clickX, $clickY)")
        
        try {
            // Get differential data
            val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>
                ?: run {
                    Log.w("VolcanoPlot", "No processedDifferentialData found")
                    return emptyList()
                }
            
            Log.d("VolcanoPlot", "Found ${processedData.size} data rows to search")
            
            // Get column mappings
            val diffForm = curtainData.differentialForm
            val fcColumn = diffForm.foldChange
            val sigColumn = diffForm.significant
            val idColumn = diffForm.primaryIDs
            val geneColumn = diffForm.geneNames
            val comparisonColumn = diffForm.comparison
            
            Log.d("VolcanoPlot", "Column mappings: FC=$fcColumn, Sig=$sigColumn, ID=$idColumn")
            
            // Define search radius (in plot coordinates) - default cutoff distance
            val searchRadius = 1.0 // Default cutoff distance
            
            val nearbyPoints = mutableListOf<VolcanoPointDetails>()
            
            for (row in processedData) {
                val id = row[idColumn]?.toString() ?: continue
                
                // Skip the selected point itself
                if (id == selectedPoint.proteinId) continue
                
                // Get gene name following web frontend workflow: UniProt data > gene column > ID
            var gene = id
            if (curtainData.fetchUniprot) {
                try {
                    val uniprotData = viewModel.uniprotService.getUniprotFromPrimary(id)
                    val geneNames = uniprotData?.get("Gene Names") as? String
                    if (!geneNames.isNullOrEmpty()) {
                        gene = geneNames
                    }
                } catch (e: Exception) {
                    Log.w("VolcanoPlot", "Error getting UniProt data for $id: ${e.message}")
                }
            }
            
            // Fallback to gene column if no UniProt gene name found
            if (gene == id && geneColumn.isNotEmpty()) {
                val geneFromColumn = row[geneColumn]?.toString()
                if (!geneFromColumn.isNullOrEmpty()) {
                    gene = geneFromColumn
                }
            }
                val comparison = if (comparisonColumn.isNotEmpty()) row[comparisonColumn]?.toString() ?: "" else ""
                
                val fcValue = when (val fc = row[fcColumn]) {
                    is Number -> fc.toDouble()
                    is String -> fc.toDoubleOrNull() ?: continue
                    else -> continue
                }
                
                val sigValue = when (val sig = row[sigColumn]) {
                    is Number -> sig.toDouble()
                    is String -> sig.toDoubleOrNull() ?: continue
                    else -> continue
                }
                
                // Calculate distance from click point
                val dx = fcValue - clickX
                val dy = sigValue - clickY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                
                // Include if within search radius
                if (distance <= searchRadius) {
                    // Determine trace group for this point using the same logic as volcano plot
                    val traceGroup = determineTraceGroup(id, fcValue, sigValue, comparison, curtainData, curtainSettings)
                    
                    val nearbyPoint = VolcanoPointDetails(
                        proteinId = id,
                        geneName = gene,
                        foldChange = fcValue,
                        significance = sigValue,
                        comparison = comparison,
                        traceGroup = traceGroup,
                        traceGroupColor = getTraceGroupColor(traceGroup, currentColorMap)
                    )
                    nearbyPoints.add(nearbyPoint)
                }
                
                // Limit to avoid too many nearby points
                if (nearbyPoints.size >= 10) break
            }
            
            // Sort by distance to selected point (closest to furthest)
            val sortedPoints = nearbyPoints.sortedBy { it.distanceTo(selectedPoint) }
            Log.d("VolcanoPlot", "Found ${sortedPoints.size} nearby points")
            
            
            return sortedPoints
            
        } catch (e: Exception) {
            Log.e("VolcanoPlot", "Error finding nearby points", e)
            return emptyList()
        }
    }
    
    /**
     * Get the color for a trace group from the color map
     */
    private fun getTraceGroupColor(traceGroup: String?, colorMap: Map<String, String>): String? {
        return if (!traceGroup.isNullOrEmpty() && colorMap.containsKey(traceGroup)) {
            colorMap[traceGroup]
        } else {
            null
        }
    }
    
    /**
     * Determine which trace group a data point belongs to (same logic as volcano plot)
     */
    private fun determineTraceGroup(
        proteinId: String, 
        fcValue: Double, 
        sigValue: Double, 
        comparison: String,
        curtainData: AppData,
        curtainSettings: CurtainSettings
    ): String? {
        // Check if protein is in any user selection first
        @Suppress("UNCHECKED_CAST")
        val selectionForId: Map<String, Boolean>? = curtainData.selectedMap[proteinId] as? Map<String, Boolean>
        
        if (selectionForId != null) {
            // Find the first selection this protein belongs to
            for ((selectionName, selected) in selectionForId) {
                if (selected) {
                    return selectionName
                }
            }
        }
        
        // If not in any selection, check if background or significance grouping
        if (curtainSettings.backGroundColorGrey) {
            return "Background"
        } else {
            // Use significance grouping system (same as volcano plot)
            val (groupText, _) = getSignificantGroup(fcValue, sigValue, curtainSettings)
            return "$groupText ($comparison)"
        }
    }
    
    /**
     * Enable edit mode for annotations
     */
    fun enableEditMode() {
        binding.webView.evaluateJavascript("if (typeof enableAnnotationEditing === 'function') { enableAnnotationEditing(); }", null)
    }
    
    /**
     * Disable edit mode for annotations
     */
    fun disableEditMode() {
        binding.webView.evaluateJavascript("if (typeof disableAnnotationEditing === 'function') { disableAnnotationEditing(); }", null)
    }
    
    /**
     * Get the current trace group counts from the volcano plot
     */
    fun getTraceGroupCounts(): Map<String, Int> {
        return traceGroupCounts.toMap()
    }
    
    /**
     * Toggle edit mode for annotations
     */
    fun toggleEditMode(enable: Boolean) {
        Log.d("VolcanoPlot", "toggleEditMode called with enable=$enable")
        binding.webView.evaluateJavascript("if (typeof toggleEditMode === 'function') { toggleEditMode($enable); }", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}