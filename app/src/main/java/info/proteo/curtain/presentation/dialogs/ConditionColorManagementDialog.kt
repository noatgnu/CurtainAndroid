package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.R
import info.proteo.curtain.data.services.ConditionColorService
import info.proteo.curtain.databinding.DialogConditionColorManagementBinding
import info.proteo.curtain.databinding.ItemColorPreviewBinding
import info.proteo.curtain.databinding.ItemConditionColorAssignmentBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ConditionColorManagementDialog : DialogFragment() {

    private var _binding: DialogConditionColorManagementBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var conditionColorService: ConditionColorService

    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    private var onColorsUpdated: (() -> Unit)? = null
    private var sampleMap: Map<String, Map<String, String>>? = null

    private lateinit var palettePreviewAdapter: PalettePreviewAdapter
    private lateinit var conditionAssignmentAdapter: ConditionAssignmentAdapter

    companion object {
        fun newInstance(
            sampleMap: Map<String, Map<String, String>>? = null,
            onColorsUpdated: (() -> Unit)? = null
        ): ConditionColorManagementDialog {
            return ConditionColorManagementDialog().apply {
                this.sampleMap = sampleMap
                this.onColorsUpdated = onColorsUpdated
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogConditionColorManagementBinding.inflate(layoutInflater)

        setupViews()
        setupPaletteSelection()
        setupRecyclerViews()
        setupButtons()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    override fun onStart() {
        super.onStart()
        observeColorChanges()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return null
    }

    private fun setupViews() {
        // Extract sample data from current curtain data if available
        extractSampleDataFromCurtain()
        updateStatistics()
        checkEmptyState()
    }
    
    private fun extractSampleDataFromCurtain() {
        try {
            // First try to use the processed sampleMap from curtain settings
            val curtainSettings = viewModel.curtainSettings.value
            if (curtainSettings != null && curtainSettings.sampleMap.isNotEmpty()) {
                // Use the existing sampleMap which contains condition info per sample
                val conditionMap = mutableMapOf<String, MutableMap<String, String>>()
                
                curtainSettings.sampleMap.forEach { (sampleName, sampleInfo) ->
                    val condition = sampleInfo["condition"] ?: "Unknown"
                    val replicate = sampleInfo["replicate"] ?: ""
                    
                    if (!conditionMap.containsKey(condition)) {
                        conditionMap[condition] = mutableMapOf()
                    }
                    
                    // Store replicate info for this sample
                    conditionMap[condition]?.put(sampleName, replicate)
                }
                
                sampleMap = conditionMap
                return
            }
            
            // Second fallback: use sampleOrder if available
            if (curtainSettings != null && curtainSettings.sampleOrder.isNotEmpty()) {
                val conditionMap = mutableMapOf<String, MutableMap<String, String>>()
                
                curtainSettings.sampleOrder.forEach { (condition, samples) ->
                    val sampleDataMap = mutableMapOf<String, String>()
                    samples.forEachIndexed { index, sampleName ->
                        sampleDataMap[sampleName] = "Rep${index + 1}"
                    }
                    conditionMap[condition] = sampleDataMap
                }
                
                sampleMap = conditionMap
                return
            }
            
            // Final fallback: parse sample names from rawForm
            viewModel.curtainData.value?.let { curtainData ->
                val sampleColumns = curtainData.rawForm.samples
                val conditionMap = mutableMapOf<String, MutableMap<String, String>>()
                
                sampleColumns.forEach { sampleName ->
                    // Parse condition using the same logic as processRawData
                    val parts = sampleName.split(".")
                    val replicate = parts.lastOrNull() ?: ""
                    val condition = if (parts.size > 1) parts.dropLast(1).joinToString(".") else sampleName
                    
                    if (!conditionMap.containsKey(condition)) {
                        conditionMap[condition] = mutableMapOf()
                    }
                    
                    conditionMap[condition]?.put(sampleName, replicate)
                }
                
                sampleMap = conditionMap
            } ?: createSampleData()
            
        } catch (e: Exception) {
            // If extraction fails, use sample data for demonstration
            createSampleData()
        }
    }
    
    private fun extractConditionFromSampleName(sampleName: String): String {
        // Try to extract condition from sample name patterns
        return when {
            sampleName.contains("control", ignoreCase = true) -> "Control"
            sampleName.contains("treatment", ignoreCase = true) -> "Treatment"
            sampleName.contains("_") -> sampleName.split("_").first()
            sampleName.length > 3 -> sampleName.substring(0, 3).uppercase()
            else -> "Condition"
        }
    }
    
    private fun createSampleData() {
        sampleMap = mapOf(
            "Control" to mapOf("Control_1" to "1.2", "Control_2" to "1.1", "Control_3" to "1.3"),
            "Treatment A" to mapOf("TreatA_1" to "2.1", "TreatA_2" to "2.3", "TreatA_3" to "2.0"),
            "Treatment B" to mapOf("TreatB_1" to "1.8", "TreatB_2" to "1.9", "TreatB_3" to "1.7")
        )
    }

    private fun setupPaletteSelection() {
        val paletteNames = conditionColorService.availablePalettes.keys.toList()
        val paletteDisplayNames = paletteNames.map { name ->
            name.split("_").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            paletteDisplayNames
        )
        binding.paletteSelector.setAdapter(adapter)

        // Set current palette
        val currentPalette = conditionColorService.currentPalette.value
        val currentIndex = paletteNames.indexOf(currentPalette)
        if (currentIndex >= 0) {
            binding.paletteSelector.setText(paletteDisplayNames[currentIndex], false)
        }

        binding.paletteSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedPalette = paletteNames[position]
            conditionColorService.setCurrentPalette(selectedPalette)
        }
    }

    private fun setupRecyclerViews() {
        // Palette preview
        palettePreviewAdapter = PalettePreviewAdapter()
        binding.palettePreviewGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 10)
            adapter = palettePreviewAdapter
        }

        // Condition assignments
        conditionAssignmentAdapter = ConditionAssignmentAdapter(
            onColorClick = { condition, currentColor ->
                showColorPickerForCondition(condition, currentColor)
            },
            onResetClick = { condition ->
                resetConditionColor(condition)
            },
            onMoveCondition = { fromPosition, toPosition ->
                moveCondition(fromPosition, toPosition)
            }
        )
        binding.conditionAssignmentsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conditionAssignmentAdapter
        }
        
        // Set up drag and drop for reordering
        val itemTouchHelper = ItemTouchHelper(ConditionReorderCallback())
        itemTouchHelper.attachToRecyclerView(binding.conditionAssignmentsList)
    }

    private fun setupButtons() {
        binding.resetButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset All Colors")
                .setMessage("This will reset all condition colors to use the current palette. Custom colors will be lost.")
                .setPositiveButton("Reset") { _, _ ->
                    conditionColorService.resetToCurrentPalette()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.applyButton.setOnClickListener {
            // Sync condition colors back to curtain settings barchartColorMap
            syncColorsToSettings()
            onColorsUpdated?.invoke()
            dismiss()
        }
    }

    private fun observeColorChanges() {
        lifecycleScope.launch {
            conditionColorService.currentPalette.collectLatest { palette ->
                updatePalettePreview()
                updateConditionAssignments()
            }
        }

        lifecycleScope.launch {
            conditionColorService.conditionColors.collectLatest {
                updateConditionAssignments()
                updateStatistics()
                checkEmptyState()
            }
        }
    }

    private fun updatePalettePreview() {
        val colors = conditionColorService.getCurrentPaletteColors()
        palettePreviewAdapter.updateColors(colors)
    }

    private fun updateConditionAssignments() {
        val colorInfo = conditionColorService.getConditionColorInfo(sampleMap)
        conditionAssignmentAdapter.updateConditions(colorInfo)
    }

    private fun updateStatistics() {
        val colorInfo = conditionColorService.getConditionColorInfo(sampleMap)
        val totalSamples = sampleMap?.values?.sumOf { it.size } ?: 0
        val customCount = colorInfo.count { it.isCustom }

        binding.conditionCountText.text = "${colorInfo.size}"
        binding.samplesCountText.text = "$totalSamples"
        binding.customColorsText.text = "$customCount"

        binding.statisticsLayout.visibility = View.VISIBLE
    }

    private fun checkEmptyState() {
        val hasConditions = conditionColorService.conditionColors.value.isNotEmpty()
        binding.emptyStateLayout.visibility = if (hasConditions) View.GONE else View.VISIBLE
        binding.conditionAssignmentsList.visibility = if (hasConditions) View.VISIBLE else View.GONE
    }

    private fun showColorPickerForCondition(condition: String, currentColor: String) {
        val colorPicker = ColorPickerDialog.newInstance(
            listName = condition,
            currentColor = currentColor
        ) { newColor ->
            conditionColorService.setConditionColor(condition, newColor)
        }
        colorPicker.show(parentFragmentManager, "ConditionColorPicker")
    }

    private fun resetConditionColor(condition: String) {
        val palette = conditionColorService.getCurrentPaletteColors()
        val conditions = conditionColorService.conditionColors.value.keys.sorted()
        val index = conditions.indexOf(condition)
        if (index >= 0) {
            val paletteColor = palette[index % palette.size]
            conditionColorService.setConditionColor(condition, paletteColor)
        }
    }

    private fun moveCondition(fromPosition: Int, toPosition: Int) {
        val colorInfo = conditionAssignmentAdapter.getCurrentConditions()
        if (fromPosition >= 0 && fromPosition < colorInfo.size && 
            toPosition >= 0 && toPosition < colorInfo.size) {
            
            val condition = colorInfo[fromPosition].condition
            conditionColorService.moveCondition(condition, toPosition)
        }
    }

    private fun syncColorsToSettings() {
        // Sync condition colors back to curtain settings
        try {
            val currentSettings = viewModel.curtainSettings.value
            if (currentSettings != null) {
                // Get the current color overrides from ConditionColorService
                val colorOverrides = conditionColorService.getColorOverrides(currentSettings.colorMap)
                
                // Create updated barchartColorMap
                val updatedBarchartColorMap = currentSettings.barchartColorMap.toMutableMap()
                
                // Add/update overrides
                colorOverrides.forEach { (condition, color) ->
                    updatedBarchartColorMap[condition] = color
                }
                
                // Remove conditions that now match the general colorMap
                val generalColorMap = currentSettings.colorMap
                updatedBarchartColorMap.keys.removeAll { condition ->
                    generalColorMap[condition] == updatedBarchartColorMap[condition]
                }
                
                // Update settings with new barchartColorMap
                val updatedSettings = currentSettings.copy(
                    barchartColorMap = updatedBarchartColorMap
                )
                
                // Update the ViewModel settings
                viewModel.updateCurtainSettings(updatedSettings)
                
                Log.d("ConditionColorDialog", "Synced ${colorOverrides.size} color overrides to barchartColorMap")
            }
        } catch (e: Exception) {
            Log.e("ConditionColorDialog", "Failed to sync colors to settings", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ConditionReorderCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            
            conditionAssignmentAdapter.moveItem(fromPosition, toPosition)
            moveCondition(fromPosition, toPosition)
            
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not used
        }

        override fun isLongPressDragEnabled(): Boolean = true
        override fun isItemViewSwipeEnabled(): Boolean = false
    }
}

class PalettePreviewAdapter : RecyclerView.Adapter<PalettePreviewAdapter.ColorViewHolder>() {

    private var colors: List<String> = emptyList()

    fun updateColors(newColors: List<String>) {
        colors = newColors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val binding = ItemColorPreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position], position)
    }

    override fun getItemCount(): Int = colors.size

    class ColorViewHolder(
        private val binding: ItemColorPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(color: String, index: Int) {
            try {
                val colorInt = Color.parseColor(color)
                binding.colorCircle.setCardBackgroundColor(colorInt)
                binding.colorIndex.text = (index + 1).toString()
                binding.colorIndex.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.colorCircle.setCardBackgroundColor(Color.GRAY)
                binding.colorIndex.visibility = View.GONE
            }
        }
    }
}

class ConditionAssignmentAdapter(
    private val onColorClick: (String, String) -> Unit,
    private val onResetClick: (String) -> Unit,
    private val onMoveCondition: ((Int, Int) -> Unit)? = null
) : RecyclerView.Adapter<ConditionAssignmentAdapter.ConditionViewHolder>() {

    private var conditions: MutableList<ConditionColorService.ConditionColorInfo> = mutableListOf()

    fun updateConditions(newConditions: List<ConditionColorService.ConditionColorInfo>) {
        conditions.clear()
        conditions.addAll(newConditions)
        notifyDataSetChanged()
    }

    fun getCurrentConditions(): List<ConditionColorService.ConditionColorInfo> = conditions

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(conditions, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(conditions, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val binding = ItemConditionColorAssignmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ConditionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        holder.bind(conditions[position])
    }

    override fun getItemCount(): Int = conditions.size

    inner class ConditionViewHolder(
        private val binding: ItemConditionColorAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conditionInfo: ConditionColorService.ConditionColorInfo) {
            binding.conditionName.text = conditionInfo.condition
            binding.colorValue.text = conditionInfo.color
            
            val sampleText = if (conditionInfo.sampleCount == 1) {
                "1 sample"
            } else {
                "${conditionInfo.sampleCount} samples"
            }
            binding.sampleCount.text = sampleText

            // Set color indicator
            try {
                val colorInt = Color.parseColor(conditionInfo.color)
                binding.colorIndicator.setCardBackgroundColor(colorInt)
            } catch (e: Exception) {
                binding.colorIndicator.setCardBackgroundColor(Color.GRAY)
            }

            // Show custom indicator
            binding.customBadge.visibility = if (conditionInfo.isCustom) View.VISIBLE else View.GONE
            binding.customIndicator.visibility = if (conditionInfo.isCustom) View.VISIBLE else View.GONE

            // Set click listeners
            binding.colorIndicator.setOnClickListener {
                onColorClick(conditionInfo.condition, conditionInfo.color)
            }

            binding.editColorButton.setOnClickListener {
                onColorClick(conditionInfo.condition, conditionInfo.color)
            }

            binding.resetColorButton.setOnClickListener {
                onResetClick(conditionInfo.condition)
            }

            binding.root.setOnClickListener {
                onColorClick(conditionInfo.condition, conditionInfo.color)
            }
        }
    }
}