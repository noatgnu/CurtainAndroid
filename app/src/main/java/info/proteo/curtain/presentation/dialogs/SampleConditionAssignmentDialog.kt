package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.R
import info.proteo.curtain.data.services.ConditionColorService
import info.proteo.curtain.databinding.DialogSampleConditionAssignmentBinding
import info.proteo.curtain.databinding.ItemSampleConditionAssignmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class SampleConditionAssignmentDialog : DialogFragment() {

    private var _binding: DialogSampleConditionAssignmentBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var conditionColorService: ConditionColorService

    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    private var onConditionsUpdated: (() -> Unit)? = null
    private lateinit var sampleAssignmentAdapter: SampleAssignmentAdapter

    // Data structures matching Angular frontend
    private var originalSampleMap: Map<String, Map<String, String>> = mapOf()
    private var editedSampleMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    companion object {
        fun newInstance(
            onConditionsUpdated: (() -> Unit)? = null
        ): SampleConditionAssignmentDialog {
            return SampleConditionAssignmentDialog().apply {
                this.onConditionsUpdated = onConditionsUpdated
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSampleConditionAssignmentBinding.inflate(layoutInflater)

        setupData()
        setupRecyclerView()
        setupButtons()
        updateStatistics()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return null
    }

    private fun setupData() {
        // Get current sample map from curtain settings
        val currentSettings = viewModel.curtainSettings.value
        if (currentSettings != null && currentSettings.sampleMap.isNotEmpty()) {
            originalSampleMap = currentSettings.sampleMap
            
            // Create a deep copy for editing (matching Angular's approach)
            editedSampleMap = originalSampleMap.mapValues { (_, sampleInfo) ->
                sampleInfo.toMutableMap()
            }.toMutableMap()
        } else {
            // Fallback: create sample map from raw data if available
            createSampleMapFromRawData()
        }
    }

    private fun createSampleMapFromRawData() {
        try {
            val curtainData = viewModel.curtainData.value
            if (curtainData != null) {
                val sampleColumns = curtainData.rawForm.samples
                val tempSampleMap = mutableMapOf<String, MutableMap<String, String>>()
                
                sampleColumns.forEachIndexed { index, sampleName ->
                    // Parse condition using the same logic as the main app
                    val parts = sampleName.split(".")
                    val replicate = parts.lastOrNull() ?: (index + 1).toString()
                    val condition = if (parts.size > 1) parts.dropLast(1).joinToString(".") else "Condition_${index + 1}"
                    
                    tempSampleMap[sampleName] = mutableMapOf(
                        "name" to sampleName,
                        "condition" to condition,
                        "replicate" to replicate
                    )
                }
                
                originalSampleMap = tempSampleMap
                editedSampleMap = tempSampleMap
            }
        } catch (e: Exception) {
            Log.e("SampleConditionDialog", "Failed to create sample map from raw data", e)
        }
    }

    private fun setupRecyclerView() {
        sampleAssignmentAdapter = SampleAssignmentAdapter { sampleName, newCondition ->
            // Update condition when user edits it
            editedSampleMap[sampleName]?.put("condition", newCondition)
            updateStatistics()
        }
        
        binding.sampleAssignmentsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sampleAssignmentAdapter
        }
        
        // Load data into adapter
        val assignments = editedSampleMap.map { (sampleName, sampleInfo) ->
            SampleAssignment(
                sampleName = sampleName,
                condition = sampleInfo["condition"] ?: "",
                replicate = sampleInfo["replicate"] ?: ""
            )
        }.sortedBy { it.sampleName }
        
        sampleAssignmentAdapter.updateAssignments(assignments)
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            saveConditionChanges()
        }
    }

    private fun saveConditionChanges() {
        try {
            val currentSettings = viewModel.curtainSettings.value ?: return
            
            // Get the list of new conditions
            val newConditions = editedSampleMap.values
                .mapNotNull { it["condition"] }
                .distinct()
                .sorted()
            
            // Transfer colors from old conditions to new conditions (matching Angular logic)
            val updatedColorMap = currentSettings.colorMap.toMutableMap()
            val updatedBarchartColorMap = currentSettings.barchartColorMap.toMutableMap()
            
            editedSampleMap.forEach { (sampleName, newSampleInfo) ->
                val newCondition = newSampleInfo["condition"] ?: return@forEach
                val originalCondition = originalSampleMap[sampleName]?.get("condition")
                
                if (originalCondition != null && originalCondition != newCondition) {
                    // Transfer color from old condition to new condition
                    val oldColor = updatedColorMap[originalCondition]
                    if (oldColor != null) {
                        updatedColorMap[newCondition] = oldColor
                    }
                    
                    // Transfer bar chart color if it exists
                    val oldBarchartColor = updatedBarchartColorMap[originalCondition] as? String
                    if (oldBarchartColor != null) {
                        updatedBarchartColorMap[newCondition] = oldBarchartColor
                    }
                }
            }
            
            // Update curtain settings with new sample map and colors
            val updatedSettings = currentSettings.copy(
                sampleMap = editedSampleMap.mapValues { it.value.toMap() },
                colorMap = updatedColorMap,
                barchartColorMap = updatedBarchartColorMap,
                conditionOrder = newConditions
            )
            
            // Update the ViewModel
            viewModel.updateCurtainSettings(updatedSettings)
            
            // Update condition color service with new conditions and colors
            conditionColorService.importFromCurtainSettings(
                colorMap = updatedColorMap,
                sampleMap = editedSampleMap.mapValues { it.value.toMap() },
                defaultColorList = currentSettings.defaultColorList,
                conditionOrder = newConditions,
                barchartColorMap = updatedBarchartColorMap
            )
            
            Log.d("SampleConditionDialog", "Saved ${editedSampleMap.size} sample assignments with ${newConditions.size} conditions")
            
            // Notify parent that conditions were updated
            onConditionsUpdated?.invoke()
            dismiss()
            
        } catch (e: Exception) {
            Log.e("SampleConditionDialog", "Failed to save condition changes", e)
        }
    }

    private fun updateStatistics() {
        val totalSamples = editedSampleMap.size
        val uniqueConditions = editedSampleMap.values
            .mapNotNull { it["condition"] }
            .distinct()
            .size
        
        binding.totalSamplesText.text = totalSamples.toString()
        binding.uniqueConditionsText.text = uniqueConditions.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class SampleAssignment(
        val sampleName: String,
        val condition: String,
        val replicate: String
    )
}

class SampleAssignmentAdapter(
    private val onConditionChanged: (String, String) -> Unit
) : RecyclerView.Adapter<SampleAssignmentAdapter.SampleAssignmentViewHolder>() {

    private var assignments: List<SampleConditionAssignmentDialog.SampleAssignment> = emptyList()

    fun updateAssignments(newAssignments: List<SampleConditionAssignmentDialog.SampleAssignment>) {
        assignments = newAssignments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SampleAssignmentViewHolder {
        val binding = ItemSampleConditionAssignmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SampleAssignmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SampleAssignmentViewHolder, position: Int) {
        holder.bind(assignments[position])
    }

    override fun getItemCount(): Int = assignments.size

    inner class SampleAssignmentViewHolder(
        private val binding: ItemSampleConditionAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentAssignment: SampleConditionAssignmentDialog.SampleAssignment? = null

        fun bind(assignment: SampleConditionAssignmentDialog.SampleAssignment) {
            currentAssignment = assignment
            
            binding.sampleName.text = assignment.sampleName
            binding.conditionInput.setText(assignment.condition)
            
            // Set up text change listener
            binding.conditionInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newCondition = s?.toString()?.trim() ?: ""
                    if (newCondition.isNotEmpty() && newCondition != assignment.condition) {
                        onConditionChanged(assignment.sampleName, newCondition)
                    }
                }
            })
        }
    }
}