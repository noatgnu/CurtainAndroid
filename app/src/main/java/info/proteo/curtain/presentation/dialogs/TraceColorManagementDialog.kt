package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
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
import info.proteo.curtain.databinding.DialogTraceColorManagementBinding
import info.proteo.curtain.databinding.ItemColorPreviewBinding
import info.proteo.curtain.databinding.ItemTraceColorAssignmentBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TraceColorManagementDialog : DialogFragment() {

    private var _binding: DialogTraceColorManagementBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var conditionColorService: ConditionColorService

    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    private var onColorsUpdated: (() -> Unit)? = null
    private var traceGroups: Map<String, Int>? = null

    private lateinit var palettePreviewAdapter: PalettePreviewAdapter
    private lateinit var traceAssignmentAdapter: TraceAssignmentAdapter

    companion object {
        fun newInstance(
            traceGroups: Map<String, Int>? = null,
            onColorsUpdated: (() -> Unit)? = null
        ): TraceColorManagementDialog {
            return TraceColorManagementDialog().apply {
                this.traceGroups = traceGroups
                this.onColorsUpdated = onColorsUpdated
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTraceColorManagementBinding.inflate(layoutInflater)

        setupViews()
        setupPaletteSelection()
        setupRecyclerViews()
        setupButtons()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Apply") { _, _ ->
                onColorsUpdated?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reset All Colors")
                    .setMessage("This will reset all trace colors to use the current palette. Custom colors will be lost.")
                    .setPositiveButton("Reset") { _, _ ->
                        // Reset trace colors logic would go here
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .create().apply {
                setOnShowListener {
                    // Set icons for buttons after dialog is shown
                    getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_check), 
                            null, null, null
                        )
                        contentDescription = "Apply"
                    }
                    getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_close), 
                            null, null, null
                        )
                        contentDescription = "Cancel"
                    }
                    getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_refresh), 
                            null, null, null
                        )
                        contentDescription = "Reset All"
                    }
                }
            }
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
        updateStatistics()
        checkEmptyState()
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

        // Trace assignments
        traceAssignmentAdapter = TraceAssignmentAdapter(
            onColorClick = { traceGroup, currentColor ->
                showColorPickerForTraceGroup(traceGroup, currentColor)
            },
            onResetClick = { traceGroup ->
                resetTraceGroupColor(traceGroup)
            },
            onMoveTraceGroup = { fromPosition, toPosition ->
                moveTraceGroup(fromPosition, toPosition)
            }
        )
        binding.traceAssignmentsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = traceAssignmentAdapter
        }
        
        // Set up drag and drop for reordering
        val itemTouchHelper = ItemTouchHelper(TraceReorderCallback())
        itemTouchHelper.attachToRecyclerView(binding.traceAssignmentsList)
    }

    private fun setupButtons() {
        // Buttons are now handled by dialog buttons - no custom button setup needed
    }

    private fun observeColorChanges() {
        lifecycleScope.launch {
            conditionColorService.currentPalette.collectLatest { palette ->
                updatePalettePreview()
                updateTraceAssignments()
            }
        }
    }

    private fun updatePalettePreview() {
        val colors = conditionColorService.getCurrentPaletteColors()
        palettePreviewAdapter.updateColors(colors)
    }

    private fun updateTraceAssignments() {
        val traceColorInfo = getTraceColorInfo()
        traceAssignmentAdapter.updateTraceGroups(traceColorInfo)
    }

    private fun getTraceColorInfo(): List<TraceColorInfo> {
        val colors = conditionColorService.getCurrentPaletteColors()
        val curtainSettings = viewModel.curtainSettings.value
        val colorMap = curtainSettings?.colorMap ?: mapOf()
        
        return traceGroups?.entries?.filter { (_, pointCount) -> 
            // Only show trace groups that have data points
            pointCount > 0 
        }?.mapIndexed { index, (traceGroup, pointCount) ->
            val finalColor = colorMap[traceGroup] ?: colors[index % colors.size]
            val isCustom = !colors.contains(finalColor)
            
            TraceColorInfo(
                traceGroup = traceGroup,
                color = finalColor,
                pointCount = pointCount,
                isCustom = isCustom
            )
        } ?: emptyList()
    }

    private fun updateStatistics() {
        val traceColorInfo = getTraceColorInfo()
        val totalPoints = traceGroups?.values?.sum() ?: 0
        val customCount = traceColorInfo.count { it.isCustom }

        binding.traceGroupCountText.text = "${traceColorInfo.size}"
        binding.customColorsText.text = "$customCount"

        binding.statisticsLayout.visibility = View.VISIBLE
    }

    private fun checkEmptyState() {
        val hasTraceGroups = !traceGroups.isNullOrEmpty()
        binding.emptyStateLayout.visibility = if (hasTraceGroups) View.GONE else View.VISIBLE
        binding.traceAssignmentsList.visibility = if (hasTraceGroups) View.VISIBLE else View.GONE
    }

    private fun showColorPickerForTraceGroup(traceGroup: String, currentColor: String) {
        val colorPicker = ColorPickerDialog.newInstance(
            listName = traceGroup,
            currentColor = currentColor
        ) { newColor ->
            // Save custom trace color to curtain settings colorMap
            val curtainSettings = viewModel.curtainSettings.value
            if (curtainSettings != null) {
                val updatedColorMap = curtainSettings.colorMap.toMutableMap()
                updatedColorMap[traceGroup] = newColor
                val updatedSettings = curtainSettings.copy(colorMap = updatedColorMap)
                viewModel.updateCurtainSettings(updatedSettings)
            }
            updateTraceAssignments()
        }
        colorPicker.show(parentFragmentManager, "TraceColorPicker")
    }

    private fun resetTraceGroupColor(traceGroup: String) {
        val colors = conditionColorService.getCurrentPaletteColors()
        val traceGroups = traceGroups?.keys?.toList() ?: emptyList()
        val index = traceGroups.indexOf(traceGroup)
        if (index >= 0) {
            val paletteColor = colors[index % colors.size]
            // TODO: Reset custom trace color in settings
            updateTraceAssignments()
        }
    }

    private fun resetAllTraceColors() {
        // TODO: Reset all custom trace colors to palette colors
        updateTraceAssignments()
    }

    private fun moveTraceGroup(fromPosition: Int, toPosition: Int) {
        val traceColorInfo = traceAssignmentAdapter.getCurrentTraceGroups()
        if (fromPosition >= 0 && fromPosition < traceColorInfo.size && 
            toPosition >= 0 && toPosition < traceColorInfo.size) {
            
            // TODO: Implement trace group reordering in settings
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class TraceReorderCallback : ItemTouchHelper.SimpleCallback(
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
            
            traceAssignmentAdapter.moveItem(fromPosition, toPosition)
            moveTraceGroup(fromPosition, toPosition)
            
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not used
        }

        override fun isLongPressDragEnabled(): Boolean = true
        override fun isItemViewSwipeEnabled(): Boolean = false
    }

    data class TraceColorInfo(
        val traceGroup: String,
        val color: String,
        val pointCount: Int,
        val isCustom: Boolean
    )
}

class TraceAssignmentAdapter(
    private val onColorClick: (String, String) -> Unit,
    private val onResetClick: (String) -> Unit,
    private val onMoveTraceGroup: ((Int, Int) -> Unit)? = null
) : RecyclerView.Adapter<TraceAssignmentAdapter.TraceViewHolder>() {

    private var traceGroups: MutableList<TraceColorManagementDialog.TraceColorInfo> = mutableListOf()

    fun updateTraceGroups(newTraceGroups: List<TraceColorManagementDialog.TraceColorInfo>) {
        traceGroups.clear()
        traceGroups.addAll(newTraceGroups)
        notifyDataSetChanged()
    }

    fun getCurrentTraceGroups(): List<TraceColorManagementDialog.TraceColorInfo> = traceGroups

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(traceGroups, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(traceGroups, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraceViewHolder {
        val binding = ItemTraceColorAssignmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TraceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TraceViewHolder, position: Int) {
        holder.bind(traceGroups[position])
    }

    override fun getItemCount(): Int = traceGroups.size

    inner class TraceViewHolder(
        private val binding: ItemTraceColorAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(traceInfo: TraceColorManagementDialog.TraceColorInfo) {
            binding.traceGroupName.text = traceInfo.traceGroup
            binding.colorValue.text = traceInfo.color
            
            val pointText = if (traceInfo.pointCount == 1) {
                "1 point"
            } else {
                "${traceInfo.pointCount} points"
            }
            binding.pointCount.text = pointText

            // Set color indicator
            try {
                val colorInt = Color.parseColor(traceInfo.color)
                binding.colorIndicator.setBackgroundColor(colorInt)
            } catch (e: Exception) {
                binding.colorIndicator.setBackgroundColor(Color.GRAY)
            }


            // Set click listeners
            binding.colorIndicator.setOnClickListener {
                onColorClick(traceInfo.traceGroup, traceInfo.color)
            }

            binding.editColorButton.setOnClickListener {
                onColorClick(traceInfo.traceGroup, traceInfo.color)
            }

            binding.resetColorButton.setOnClickListener {
                onResetClick(traceInfo.traceGroup)
            }

            binding.root.setOnClickListener {
                onColorClick(traceInfo.traceGroup, traceInfo.color)
            }
        }
    }
}