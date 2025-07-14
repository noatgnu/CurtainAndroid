package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.data.models.*
import info.proteo.curtain.data.services.SettingsVariantService
import info.proteo.curtain.databinding.DialogCurtainSettingsManagerBinding
import info.proteo.curtain.databinding.ItemSettingsVariantLoadBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class CurtainSettingsManagerDialog : DialogFragment() {
    
    private var _binding: DialogCurtainSettingsManagerBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    
    @Inject
    lateinit var settingsVariantService: SettingsVariantService
    
    private lateinit var loadAdapter: SettingsVariantLoadAdapter
    private var curtainId: String = ""
    private var currentTab = 0 // 0 = Load, 1 = Save
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCurtainSettingsManagerBinding.inflate(layoutInflater)
        
        curtainId = arguments?.getString("curtainId") ?: ""
        val showSaveTab = arguments?.getBoolean("showSaveTab", false) ?: false
        
        setupTabs()
        setupLoadTab()
        setupSaveTab()
        updateCurrentSettingsPreview()
        
        // Show save tab if requested
        if (showSaveTab) {
            binding.settingsTabLayout.getTabAt(1)?.select()
            showSaveTab()
        } else {
            showLoadTab()
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupTabs() {
        binding.settingsTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                when (currentTab) {
                    0 -> showLoadTab()
                    1 -> showSaveTab()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Show load tab initially
        showLoadTab()
    }
    
    private fun showLoadTab() {
        binding.loadTabContent.visibility = View.VISIBLE
        binding.saveTabContent.visibility = View.GONE
        binding.actionButton.text = "Load"
        binding.actionButton.setIconResource(R.drawable.ic_file_download)
        
        binding.actionButton.setOnClickListener {
            val selectedVariant = loadAdapter.getSelectedVariant()
            if (selectedVariant != null) {
                loadSettingsVariant(selectedVariant)
            } else {
                Toast.makeText(requireContext(), "Please select a variant to load", Toast.LENGTH_SHORT).show()
            }
        }
        
        loadVariants()
    }
    
    private fun showSaveTab() {
        binding.loadTabContent.visibility = View.GONE
        binding.saveTabContent.visibility = View.VISIBLE
        binding.actionButton.text = "Save"
        binding.actionButton.setIconResource(R.drawable.ic_save)
        
        binding.actionButton.setOnClickListener {
            saveCurrentSettings()
        }
    }
    
    private fun setupLoadTab() {
        loadAdapter = SettingsVariantLoadAdapter(
            onLoadClick = { variant ->
                loadSettingsVariant(variant)
            },
            onDeleteClick = { variant ->
                deleteSettingsVariant(variant)
            }
        )
        
        binding.loadRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = loadAdapter
        }
        
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }
    
    private fun setupSaveTab() {
        // Pre-fill with current curtain name if available
        viewModel.curtainEntity.value?.let { curtainEntity ->
            val defaultName = "Settings for ${curtainEntity.description}"
            binding.variantNameInput.setText(defaultName)
        }
    }
    
    private fun loadVariants() {
        lifecycleScope.launch {
            try {
                val variants = settingsVariantService.getAllVariantsForCurtain(curtainId)
                if (variants.isEmpty()) {
                    binding.loadEmptyState.visibility = View.VISIBLE
                    binding.loadRecyclerView.visibility = View.GONE
                } else {
                    binding.loadEmptyState.visibility = View.GONE
                    binding.loadRecyclerView.visibility = View.VISIBLE
                    loadAdapter.submitList(variants)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load variants: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveCurrentSettings() {
        val name = binding.variantNameInput.text.toString().trim()
        if (name.isEmpty()) {
            binding.variantNameLayout.error = "Name is required"
            return
        }
        
        val description = binding.variantDescriptionInput.text.toString().trim()
        
        lifecycleScope.launch {
            try {
                val result = settingsVariantService.createVariantFromCurrentState(
                    name = name,
                    curtainDataService = viewModel.curtainDataService,
                    description = description.ifEmpty { null }
                )
                
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Settings saved as '$name'", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to save settings: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to save settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadSettingsVariant(variant: SettingsVariant) {
        lifecycleScope.launch {
            try {
                // Apply the settings variant to the current session
                applySettingsVariant(variant)
                
                // Update last used timestamp
                settingsVariantService.updateLastUsed(variant.id)
                
                Toast.makeText(requireContext(), "Settings '${variant.name}' loaded", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    
    private fun applySettingsVariant(variant: SettingsVariant) {
        lifecycleScope.launch {
            try {
                // Apply all settings categories
                val categories = listOf(
                    SettingsCategory.VISUAL,
                    SettingsCategory.ANALYSIS,
                    SettingsCategory.SEARCH,
                    SettingsCategory.CONDITIONS,
                    SettingsCategory.PLOTS,
                    SettingsCategory.PREFERENCES
                )
                
                settingsVariantService.applyVariant(variant, viewModel.curtainDataService, categories)
                
                // Trigger UI refresh - notify viewModel that settings have changed
                viewModel.refreshFromSettingsUpdate()
                
                // Also trigger search update to refresh search lists
                viewModel.refreshFromSearchUpdate()
                
                Toast.makeText(
                    requireContext(),
                    "Settings variant '${variant.name}' applied successfully",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e("CurtainSettingsManagerDialog", "Error applying settings variant", e)
                Toast.makeText(
                    requireContext(),
                    "Error applying settings: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun deleteSettingsVariant(variant: SettingsVariant) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Settings Variant")
            .setMessage("Are you sure you want to delete '${variant.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val result = settingsVariantService.deleteVariant(variant.id)
                        if (result.isSuccess) {
                            Toast.makeText(
                                requireContext(),
                                "Settings variant '${variant.name}' deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            // Refresh the list
                            loadVariants()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Failed to delete variant: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CurtainSettingsManagerDialog", "Error deleting variant", e)
                        Toast.makeText(
                            requireContext(),
                            "Error deleting variant: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateCurrentSettingsPreview() {
        val preview = buildString {
            append("• Color scheme: Default\n")
            append("• Analysis settings: Standard\n")
            append("• Search filters: Active\n")
            append("• Condition colors: Auto-assigned\n")
            append("• Chart preferences: Individual bars")
        }
        binding.currentSettingsPreview.text = preview
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance(curtainId: String): CurtainSettingsManagerDialog {
            return CurtainSettingsManagerDialog().apply {
                arguments = Bundle().apply {
                    putString("curtainId", curtainId)
                }
            }
        }
    }
}

class SettingsVariantLoadAdapter(
    private val onLoadClick: (SettingsVariant) -> Unit,
    private val onDeleteClick: (SettingsVariant) -> Unit
) : RecyclerView.Adapter<SettingsVariantLoadAdapter.ViewHolder>() {
    
    private var variants = listOf<SettingsVariant>()
    private var selectedPosition = -1
    
    fun submitList(newVariants: List<SettingsVariant>) {
        variants = newVariants
        notifyDataSetChanged()
    }
    
    fun getSelectedVariant(): SettingsVariant? {
        return if (selectedPosition >= 0 && selectedPosition < variants.size) {
            variants[selectedPosition]
        } else null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsVariantLoadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(variants[position], position == selectedPosition)
    }
    
    override fun getItemCount() = variants.size
    
    inner class ViewHolder(private val binding: ItemSettingsVariantLoadBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(variant: SettingsVariant, isSelected: Boolean) {
            binding.variantName.text = variant.name
            binding.variantDescription.text = variant.description ?: "No description"
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val createdDate = dateFormat.format(Date(variant.createdAt))
            val categoryCount = countSettingsCategories(variant)
            binding.variantInfo.text = "Created: $createdDate • $categoryCount categories"
            
            // Update selection state
            binding.root.isSelected = isSelected
            binding.root.alpha = if (isSelected) 1.0f else 0.8f
            
            binding.root.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = if (selectedPosition == adapterPosition) -1 else adapterPosition
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
            }
            
            binding.loadButton.setOnClickListener {
                onLoadClick(variant)
            }
            
            binding.deleteButton.setOnClickListener {
                onDeleteClick(variant)
            }
        }
        
        private fun countSettingsCategories(variant: SettingsVariant): Int {
            var count = 0
            if (variant.visualSettings != null) count++
            if (variant.analysisSettings != null) count++
            if (variant.searchSettings != null) count++
            if (variant.conditionSettings != null) count++
            if (variant.plotSettings != null) count++
            if (variant.appPreferences != null) count++
            return count
        }
    }
}