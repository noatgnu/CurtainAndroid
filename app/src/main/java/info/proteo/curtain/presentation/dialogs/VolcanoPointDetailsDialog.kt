package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.proteo.curtain.R
import info.proteo.curtain.data.models.VolcanoPointDetails
import info.proteo.curtain.data.models.VolcanoPointSelection
import info.proteo.curtain.databinding.DialogVolcanoPointDetailsBinding
import info.proteo.curtain.presentation.adapters.NearbyPointsAdapter
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.DecimalFormat

class VolcanoPointDetailsDialog : DialogFragment() {
    
    private var _binding: DialogVolcanoPointDetailsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var nearbyPointsAdapter: NearbyPointsAdapter
    
    private val decimalFormat = DecimalFormat("#.###")
    private val scientificFormat = DecimalFormat("0.##E0")
    
    // Track selected proteins for new selection group
    private val selectedProteins = mutableSetOf<String>()
    
    // Store point selection for dialog button access
    private lateinit var currentPointSelection: VolcanoPointSelection
    
    companion object {
        private const val TAG = "VolcanoPointDetailsDialog"
        private const val ARG_POINT_SELECTION = "point_selection"
        
        fun newInstance(pointSelection: VolcanoPointSelection): VolcanoPointDetailsDialog {
            val dialog = VolcanoPointDetailsDialog()
            val args = Bundle().apply {
                putSerializable(ARG_POINT_SELECTION, pointSelection)
            }
            dialog.arguments = args
            return dialog
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use full-screen dialog theme
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogVolcanoPointDetailsBinding.inflate(layoutInflater)
        
        currentPointSelection = arguments?.getSerializable(ARG_POINT_SELECTION) as? VolcanoPointSelection
            ?: throw IllegalArgumentException("Point selection is required")
        
        setupDialog(currentPointSelection)
        
        Log.d(TAG, "Creating dialog with point selection: ${currentPointSelection.selectedPoint.proteinId}")
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Close") { _, _ -> 
                Log.d(TAG, "Close button clicked")
                dismiss() 
            }
            .setNeutralButton("Select", null) // Set null to override later
            .setNegativeButton("Annotate") { _, _ ->
                Log.d(TAG, "Annotate button clicked")
                try {
                    annotateSelectedPoints()
                    dismiss() // Close dialog after annotation
                } catch (e: Exception) {
                    Log.e(TAG, "Error in annotateSelectedPoints", e)
                }
            }
            .create()
        
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        
        // Override the Select button to create selection immediately
        val alertDialog = dialog as? androidx.appcompat.app.AlertDialog
        alertDialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            Log.d(TAG, "Select button clicked (custom handler)")
            try {
                createNearbySelection()
            } catch (e: Exception) {
                Log.e(TAG, "Error in createNearbySelection", e)
            }
        }
    }
    
    private fun setupDialog(pointSelection: VolcanoPointSelection) {
        val selectedPoint = pointSelection.selectedPoint
        
        // Setup main point details
        setupMainPointDetails(selectedPoint)
        
        // Setup nearby points if any
        if (pointSelection.nearbyPoints.isNotEmpty()) {
            setupNearbyPoints(pointSelection)
        }
        
        // Setup buttons
        setupButtons(selectedPoint)
        
        // Setup main point checkbox
        setupMainPointCheckbox(selectedPoint)
    }
    
    private fun setupMainPointDetails(point: VolcanoPointDetails) {
        // Get gene name from UniProt service (same method as used elsewhere in the app)
        val uniprotRecord = viewModel.uniprotService.getUniprotFromPrimary(point.proteinId)
        val uniprotGeneName = uniprotRecord?.get("Gene Names")?.toString()
        
        // Use UniProt gene name if available, otherwise fall back to the point's gene name
        val displayGeneName = when {
            !uniprotGeneName.isNullOrEmpty() && uniprotGeneName != point.proteinId -> uniprotGeneName
            !point.geneName.isNullOrEmpty() && point.geneName != point.proteinId -> point.geneName
            else -> null
        }
        
        // Show gene name and protein ID separately for clarity
        if (displayGeneName != null) {
            binding.tvMainGeneName.text = "Gene: $displayGeneName"
            binding.tvMainProteinId.text = "Protein ID: ${point.proteinId}"
        } else {
            // Only protein ID available - show it as the main identifier
            binding.tvMainGeneName.text = point.proteinId
            binding.tvMainProteinId.text = "Protein ID: ${point.proteinId}"
        }
        
        // Set fold change and significance
        binding.tvMainFoldChange.text = decimalFormat.format(point.foldChange)
        
        // Format significance value
        binding.tvMainSignificance.text = if (point.significance < 0.001) {
            scientificFormat.format(point.significance)
        } else {
            decimalFormat.format(point.significance)
        }
        
        // Show trace group if available
        if (!point.traceGroup.isNullOrEmpty()) {
            binding.tvTraceGroup.text = "Group: ${point.traceGroup}"
            binding.layoutTraceGroup.visibility = View.VISIBLE
            
            // Set trace group color if available
            if (!point.traceGroupColor.isNullOrEmpty()) {
                try {
                    val color = android.graphics.Color.parseColor(point.traceGroupColor)
                    binding.vTraceGroupColor.setBackgroundColor(color)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid trace group color: ${point.traceGroupColor}")
                }
            }
        } else {
            binding.layoutTraceGroup.visibility = View.GONE
        }
    }
    
    private fun setupNearbyPoints(pointSelection: VolcanoPointSelection) {
        Log.d(TAG, "Setting up nearby points: ${pointSelection.nearbyPoints.size} points found")
        
        if (pointSelection.nearbyPoints.isEmpty()) {
            binding.tvNearbyPointsTitle.visibility = View.GONE
            binding.rvNearbyPoints.visibility = View.GONE
            return
        }
        
        binding.tvNearbyPointsTitle.visibility = View.VISIBLE
        binding.rvNearbyPoints.visibility = View.VISIBLE
        
        // Calculate distances and sort by distance (closest to furthest)
        val nearbyWithDistances = pointSelection.nearbyPoints.map { nearbyPoint ->
            val distance = pointSelection.selectedPoint.distanceTo(nearbyPoint)
            Pair(nearbyPoint, distance)
        }.sortedBy { it.second }
        
        // Setup RecyclerView
        nearbyPointsAdapter = NearbyPointsAdapter(
            onPointClick = { selectedNearbyPoint ->
                // Replace current selection with nearby point selection
                val newSelection = VolcanoPointSelection(
                    selectedPoint = selectedNearbyPoint,
                    nearbyPoints = emptyList(), // Don't show nested nearby points
                    clickX = selectedNearbyPoint.x,
                    clickY = selectedNearbyPoint.y
                )
                setupDialog(newSelection)
            },
            onCheckboxChanged = { point, isChecked ->
                if (isChecked) {
                    selectedProteins.add(point.proteinId)
                } else {
                    selectedProteins.remove(point.proteinId)
                }
                updateSelectionButtonState()
            },
            uniprotService = viewModel.uniprotService
        )
        
        binding.rvNearbyPoints.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = nearbyPointsAdapter
            // Force scrolling to be enabled
            isNestedScrollingEnabled = true
            setHasFixedSize(false)
            
            // Add a layout listener to debug the RecyclerView dimensions
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                Log.d(TAG, "RecyclerView dimensions: ${width} x ${height}")
                Log.d(TAG, "RecyclerView child count: ${childCount}")
            }
        }
        
        nearbyPointsAdapter.submitList(nearbyWithDistances)
        
        Log.d(TAG, "Submitting ${nearbyWithDistances.size} nearby points to RecyclerView")
        
        // Update title to show count
        binding.tvNearbyPointsTitle.text = "Nearby Points (${nearbyWithDistances.size})"
    }
    
    private fun setupButtons(selectedPoint: VolcanoPointDetails) {
        // Only setup the create selection button since other buttons are now dialog buttons
        binding.btnCreateSelection.setOnClickListener {
            createNewSelection()
        }
        
        // Setup text watcher for selection name
        binding.etSelectionName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSelectionButtonState()
            }
        })
    }
    
    private fun setupMainPointCheckbox(selectedPoint: VolcanoPointDetails) {
        binding.cbMainPoint.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedProteins.add(selectedPoint.proteinId)
            } else {
                selectedProteins.remove(selectedPoint.proteinId)
            }
            updateSelectionButtonState()
        }
    }
    
    private fun createNearbySelection() {
        Log.d(TAG, "createNearbySelection called")
        
        try {
            val selectedPoint = currentPointSelection.selectedPoint
            val nearbyPoints = currentPointSelection.nearbyPoints
            
            // Create automatic selection name using gene name or protein ID
            val baseName = if (selectedPoint.geneName.isNotEmpty() && selectedPoint.geneName != selectedPoint.proteinId) {
                selectedPoint.geneName
            } else {
                selectedPoint.proteinId
            }
            val selectionName = "Nearby $baseName"
            
            // Collect selected protein IDs (from checkboxes)
            val allProteins = mutableSetOf<String>()
            
            // Add main point if its checkbox is checked
            if (binding.cbMainPoint.isChecked) {
                allProteins.add(selectedPoint.proteinId)
            }
            
            // Add any checked nearby points (selectedProteins tracks checkbox states)
            allProteins.addAll(selectedProteins)
            
            Log.d(TAG, "Creating selection '$selectionName' with ${allProteins.size} proteins: ${allProteins.joinToString(", ")}")
            
            // Use the SearchService to create a new search list
            lifecycleScope.launch {
                viewModel.searchService.createSearchList(
                    name = selectionName,
                    proteinIds = allProteins.toList(),
                    searchTerms = emptyList(),
                    searchType = info.proteo.curtain.data.models.SearchType.PRIMARY_ID
                )
                
                Log.d(TAG, "Successfully created selection: $selectionName")
                
                // Trigger volcano plot refresh to show the new selection
                withContext(Dispatchers.Main) {
                    viewModel.refreshFromSearchUpdate()
                }
                
                // Close the dialog
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating nearby selection", e)
        }
    }
    
    private fun toggleSelectionCreation() {
        Log.d(TAG, "toggleSelectionCreation called, current visibility: ${binding.layoutCreateSelection.visibility}")
        if (binding.layoutCreateSelection.visibility == View.VISIBLE) {
            binding.layoutCreateSelection.visibility = View.GONE
            Log.d(TAG, "Selection creation hidden")
        } else {
            binding.layoutCreateSelection.visibility = View.VISIBLE
            Log.d(TAG, "Selection creation shown")
        }
    }
    
    private fun updateSelectionButtonState() {
        val hasSelection = selectedProteins.isNotEmpty()
        binding.btnCreateSelection.isEnabled = hasSelection && binding.etSelectionName.text?.isNotBlank() == true
    }
    
    private fun createNewSelection() {
        val selectionName = binding.etSelectionName.text?.toString()?.trim()
        if (selectionName.isNullOrBlank()) {
            binding.etSelectionName.error = "Please enter a selection name"
            return
        }
        
        if (selectedProteins.isEmpty()) {
            return
        }
        
        try {
            // Use the SearchService to create a new search list
            lifecycleScope.launch {
                // Create search list using protein IDs
                viewModel.searchService.createSearchList(
                    name = selectionName,
                    proteinIds = selectedProteins.toList(),
                    searchTerms = emptyList(),
                    searchType = info.proteo.curtain.data.models.SearchType.PRIMARY_ID
                )
                
                // Trigger volcano plot refresh to show the new selection
                withContext(Dispatchers.Main) {
                    viewModel.refreshFromSearchUpdate()
                }
                
                // Close the dialog
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating selection", e)
        }
    }
    
    private fun annotateSelectedPoints() {
        Log.d(TAG, "annotateSelectedPoints called")
        
        try {
            val pointsToAnnotate = mutableListOf<VolcanoPointDetails>()
            
            // Add main point if its checkbox is checked
            if (binding.cbMainPoint.isChecked) {
                pointsToAnnotate.add(currentPointSelection.selectedPoint)
            }
            
            // Add any checked nearby points
            currentPointSelection.nearbyPoints.forEach { nearbyPoint ->
                if (selectedProteins.contains(nearbyPoint.proteinId)) {
                    pointsToAnnotate.add(nearbyPoint)
                }
            }
            
            Log.d(TAG, "Annotating ${pointsToAnnotate.size} selected points")
            
            if (pointsToAnnotate.isEmpty()) {
                Log.d(TAG, "No points selected for annotation")
                return
            }
            
            // Annotate each selected point
            pointsToAnnotate.forEach { point ->
                addAnnotationForPoint(point)
            }
            
            // Trigger volcano plot refresh once after all annotations are added
            viewModel.refreshFromSearchUpdate()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error annotating selected points", e)
        }
    }
    
    private fun addAnnotationForPoint(point: VolcanoPointDetails) {
        Log.d(TAG, "addAnnotationForPoint called for protein: ${point.proteinId}")
        try {
            val curtainSettings = viewModel.curtainSettings.value ?: return
            
            // Generate unique annotation title exactly like Angular frontend
            val title = generateAnnotationTitle(point.proteinId)
            
            Log.d(TAG, "Generated annotation title: '$title' for protein: ${point.proteinId}")
            
            // Skip if annotation already exists
            val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
            if (existingAnnotations.containsKey(title)) {
                Log.d(TAG, "Annotation already exists for: $title")
                return
            }
            
            Log.d(TAG, "Creating new annotation for: $title at (${point.x}, ${point.y})")
            
            // Create annotation data structure (matching Angular frontend exactly)
            val annotationData = mapOf(
                "primary_id" to point.proteinId,
                "title" to title,
                "data" to mapOf(
                    "xref" to "x",
                    "yref" to "y", 
                    "x" to point.x,
                    "y" to point.y,
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
            
            // Update settings
            val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
            viewModel.updateCurtainSettings(updatedSettings)
            
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding annotation", e)
        }
    }
    
    /**
     * Generate annotation title exactly like Angular frontend:
     * - If UniProt has gene names and they're not empty: "geneName(proteinId)"
     * - Otherwise: just "proteinId"
     * This title is unique and never changes - only annotation text may change
     */
    private fun generateAnnotationTitle(proteinId: String): String {
        val uniprotRecord = viewModel.uniprotService.getUniprotFromPrimary(proteinId)
        val geneNames = uniprotRecord?.get("Gene Names")?.toString()
        
        return if (!geneNames.isNullOrEmpty() && geneNames != proteinId) {
            "${geneNames}(${proteinId})"
        } else {
            proteinId
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}