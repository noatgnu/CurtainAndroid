package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.data.models.SettingsCategory
import info.proteo.curtain.data.models.SettingsVariant
import info.proteo.curtain.data.services.SettingsVariantService
import info.proteo.curtain.databinding.DialogSettingsVariantManagerBinding
import info.proteo.curtain.presentation.adapters.SettingsVariantAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsVariantManagerDialog : DialogFragment() {

    @Inject
    lateinit var settingsVariantService: SettingsVariantService

    private var _binding: DialogSettingsVariantManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var variantAdapter: SettingsVariantAdapter
    private lateinit var managementAdapter: SettingsVariantAdapter
    
    private var selectedVariant: SettingsVariant? = null
    private var selectedVariants = mutableSetOf<String>()
    
    private var onVariantApplied: ((SettingsVariant, List<SettingsCategory>) -> Unit)? = null
    private var onVariantSaved: ((SettingsVariant) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSettingsVariantManagerBinding.inflate(layoutInflater)
        
        setupAdapters()
        setupTabs()
        setupLoadTab()
        setupSaveTab()
        setupManageTab()
        setupActionButtons()
        observeVariants()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    private fun setupAdapters() {
        // Load tab adapter
        variantAdapter = SettingsVariantAdapter(
            onVariantClick = { variant ->
                selectedVariant = variant
                updateApplyButtonState()
            },
            onFavoriteClick = { variant ->
                lifecycleScope.launch {
                    settingsVariantService.toggleFavorite(variant.id)
                }
            },
            onMoreClick = { variant ->
                showVariantOptionsMenu(variant)
            }
        )

        // Management tab adapter
        managementAdapter = SettingsVariantAdapter(
            onVariantClick = { variant ->
                toggleVariantSelection(variant.id)
            },
            onFavoriteClick = { variant ->
                lifecycleScope.launch {
                    settingsVariantService.toggleFavorite(variant.id)
                }
            },
            onMoreClick = { variant ->
                showVariantOptionsMenu(variant)
            },
            selectionMode = true
        )

        // Setup RecyclerViews
        binding.loadTabContent.variantsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = variantAdapter
        }

        binding.manageTabContent.managementRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = managementAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showLoadTab()
                    1 -> showSaveTab()
                    2 -> showManageTab()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Start with Load tab
        showLoadTab()
    }

    private fun setupLoadTab() {
        // Search functionality
        binding.loadTabContent.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterVariants(s?.toString() ?: "")
            }
        })

        // Filter chips
        binding.loadTabContent.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            applyFilters(checkedIds)
        }

        // Category selection chips for loading
        binding.loadTabContent.categoryChips.setOnCheckedStateChangeListener { _, _ ->
            updateApplyButtonState()
        }
    }

    private fun setupSaveTab() {
        // Generate preview when inputs change
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        }

        binding.saveTabContent.nameInput.addTextChangedListener(textWatcher)
        binding.saveTabContent.descriptionInput.addTextChangedListener(textWatcher)
        binding.saveTabContent.tagsInput.addTextChangedListener(textWatcher)

        // Category checkboxes
        listOf(
            binding.saveTabContent.visualCheckbox,
            binding.saveTabContent.analysisCheckbox,
            binding.saveTabContent.searchCheckbox,
            binding.saveTabContent.conditionsCheckbox,
            binding.saveTabContent.plotsCheckbox,
            binding.saveTabContent.preferencesCheckbox
        ).forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                updatePreview()
            }
        }
    }

    private fun setupManageTab() {
        binding.manageTabContent.selectAllButton.setOnClickListener {
            toggleSelectAll()
        }

        binding.manageTabContent.deleteSelectedButton.setOnClickListener {
            deleteSelectedVariants()
        }

        binding.manageTabContent.exportSelectedButton.setOnClickListener {
            exportSelectedVariants()
        }

        binding.manageTabContent.duplicateSelectedButton.setOnClickListener {
            duplicateSelectedVariants()
        }

        binding.manageTabContent.backupAllButton.setOnClickListener {
            backupAllVariants()
        }
    }

    private fun setupActionButtons() {
        binding.newVariantButton.setOnClickListener {
            showSaveTab()
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1))
        }

        binding.importButton.setOnClickListener {
            importVariants()
        }

        binding.exportButton.setOnClickListener {
            exportAllVariants()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.applyButton.setOnClickListener {
            when (binding.tabLayout.selectedTabPosition) {
                0 -> applySelectedVariant()
                1 -> saveCurrentVariant()
                2 -> dismiss() // Manage tab doesn't need apply action
            }
        }
    }

    private fun observeVariants() {
        lifecycleScope.launch {
            settingsVariantService.allVariants.collectLatest { variants ->
                variantAdapter.submitList(variants)
                managementAdapter.submitList(variants)
                updateEmptyStates(variants)
                updateStorageInfo(variants)
            }
        }
    }

    private fun showLoadTab() {
        binding.loadTabContent.root.visibility = View.VISIBLE
        binding.saveTabContent.root.visibility = View.GONE
        binding.manageTabContent.root.visibility = View.GONE
        
        binding.applyButton.text = "Apply"
        binding.applyButton.setIconResource(R.drawable.ic_check)
        updateApplyButtonState()
    }

    private fun showSaveTab() {
        binding.loadTabContent.root.visibility = View.GONE
        binding.saveTabContent.root.visibility = View.VISIBLE
        binding.manageTabContent.root.visibility = View.GONE
        
        binding.applyButton.text = "Save"
        binding.applyButton.setIconResource(R.drawable.ic_save)
        binding.applyButton.isEnabled = binding.saveTabContent.nameInput.text?.isNotBlank() == true
    }

    private fun showManageTab() {
        binding.loadTabContent.root.visibility = View.GONE
        binding.saveTabContent.root.visibility = View.GONE
        binding.manageTabContent.root.visibility = View.VISIBLE
        
        binding.applyButton.visibility = View.GONE
        selectedVariants.clear()
        updateBulkActionsVisibility()
    }

    private fun filterVariants(query: String) {
        lifecycleScope.launch {
            val filteredVariants = settingsVariantService.searchVariants(query)
            variantAdapter.submitList(filteredVariants)
        }
    }

    private fun applyFilters(checkedIds: List<Int>) {
        // Implementation for filter chips (All, Default, Recent, Favorites)
        val showAll = checkedIds.contains(R.id.chipAll)
        val showDefault = checkedIds.contains(R.id.chipDefault)
        val showRecent = checkedIds.contains(R.id.chipRecent)
        val showFavorites = checkedIds.contains(R.id.chipFavorites)
        
        lifecycleScope.launch {
            val filteredVariants = settingsVariantService.getFilteredVariants(
                showDefault = showDefault,
                showRecent = showRecent,
                showFavorites = showFavorites,
                showAll = showAll
            )
            variantAdapter.submitList(filteredVariants)
        }
    }

    private fun updatePreview() {
        val selectedCategories = getSelectedSaveCategories()
        val preview = buildString {
            append("This variant will include: ")
            append(selectedCategories.joinToString(", ") { it.displayName })
        }
        binding.saveTabContent.previewText.text = preview
        
        // Update save button state
        val hasName = binding.saveTabContent.nameInput.text?.isNotBlank() == true
        binding.applyButton.isEnabled = hasName && selectedCategories.isNotEmpty()
    }

    private fun updateApplyButtonState() {
        val hasSelection = selectedVariant != null
        val hasCategories = getSelectedLoadCategories().isNotEmpty()
        binding.applyButton.isEnabled = hasSelection && hasCategories
    }

    private fun getSelectedSaveCategories(): List<SettingsCategory> {
        return buildList {
            if (binding.saveTabContent.visualCheckbox.isChecked) add(SettingsCategory.VISUAL)
            if (binding.saveTabContent.analysisCheckbox.isChecked) add(SettingsCategory.ANALYSIS)
            if (binding.saveTabContent.searchCheckbox.isChecked) add(SettingsCategory.SEARCH)
            if (binding.saveTabContent.conditionsCheckbox.isChecked) add(SettingsCategory.CONDITIONS)
            if (binding.saveTabContent.plotsCheckbox.isChecked) add(SettingsCategory.PLOTS)
            if (binding.saveTabContent.preferencesCheckbox.isChecked) add(SettingsCategory.PREFERENCES)
        }
    }

    private fun getSelectedLoadCategories(): List<SettingsCategory> {
        return buildList {
            if (binding.loadTabContent.chipVisual.isChecked) add(SettingsCategory.VISUAL)
            if (binding.loadTabContent.chipAnalysis.isChecked) add(SettingsCategory.ANALYSIS)
            if (binding.loadTabContent.chipSearch.isChecked) add(SettingsCategory.SEARCH)
            if (binding.loadTabContent.chipConditions.isChecked) add(SettingsCategory.CONDITIONS)
            if (binding.loadTabContent.chipPlots.isChecked) add(SettingsCategory.PLOTS)
            if (binding.loadTabContent.chipPreferences.isChecked) add(SettingsCategory.PREFERENCES)
        }
    }

    private fun applySelectedVariant() {
        val variant = selectedVariant ?: return
        val categories = getSelectedLoadCategories()
        
        if (categories.isEmpty()) {
            Toast.makeText(context, "Please select at least one category to apply", Toast.LENGTH_SHORT).show()
            return
        }

        onVariantApplied?.invoke(variant, categories)
        dismiss()
    }

    private fun saveCurrentVariant() {
        val name = binding.saveTabContent.nameInput.text?.toString()?.trim()
        if (name.isNullOrBlank()) {
            binding.saveTabContent.nameInputLayout.error = "Name is required"
            return
        }

        val description = binding.saveTabContent.descriptionInput.text?.toString()?.trim()
        val tagsText = binding.saveTabContent.tagsInput.text?.toString()?.trim()
        val tags = tagsText?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val categories = getSelectedSaveCategories()

        if (categories.isEmpty()) {
            Toast.makeText(context, "Please select at least one category to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = settingsVariantService.createVariantFromCurrentState(
                    name = name,
                    description = description,
                    tags = tags,
                    includeCategories = categories
                )
                
                result.fold(
                    onSuccess = { variant ->
                        onVariantSaved?.invoke(variant)
                        Toast.makeText(context, "Settings variant saved successfully", Toast.LENGTH_SHORT).show()
                        dismiss()
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "Failed to save variant: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving variant: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleVariantSelection(variantId: String) {
        if (selectedVariants.contains(variantId)) {
            selectedVariants.remove(variantId)
        } else {
            selectedVariants.add(variantId)
        }
        
        managementAdapter.updateSelectedItems(selectedVariants)
        updateBulkActionsVisibility()
        binding.manageTabContent.deleteSelectedButton.isEnabled = selectedVariants.isNotEmpty()
    }

    private fun toggleSelectAll() {
        val allVariants = managementAdapter.currentList.map { it.id }
        if (selectedVariants.containsAll(allVariants)) {
            selectedVariants.clear()
        } else {
            selectedVariants.addAll(allVariants)
        }
        
        managementAdapter.updateSelectedItems(selectedVariants)
        updateBulkActionsVisibility()
        binding.manageTabContent.deleteSelectedButton.isEnabled = selectedVariants.isNotEmpty()
    }

    private fun updateBulkActionsVisibility() {
        val hasSelection = selectedVariants.isNotEmpty()
        binding.manageTabContent.bulkActionsLayout.visibility = if (hasSelection) View.VISIBLE else View.GONE
        binding.manageTabContent.selectAllButton.text = if (selectedVariants.size == managementAdapter.currentList.size) "Deselect All" else "Select All"
    }

    private fun deleteSelectedVariants() {
        if (selectedVariants.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Variants")
            .setMessage("Are you sure you want to delete ${selectedVariants.size} variant(s)? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    selectedVariants.forEach { variantId ->
                        settingsVariantService.deleteVariant(variantId)
                    }
                    selectedVariants.clear()
                    updateBulkActionsVisibility()
                    Toast.makeText(context, "Variants deleted successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportSelectedVariants() {
        lifecycleScope.launch {
            try {
                val variants = selectedVariants.mapNotNull { variantId ->
                    settingsVariantService.getVariant(variantId)
                }
                settingsVariantService.exportVariants(variants)
                Toast.makeText(context, "Variants exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun duplicateSelectedVariants() {
        lifecycleScope.launch {
            try {
                selectedVariants.forEach { variantId ->
                    settingsVariantService.duplicateVariant(variantId)
                }
                selectedVariants.clear()
                updateBulkActionsVisibility()
                Toast.makeText(context, "Variants duplicated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Duplication failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun backupAllVariants() {
        lifecycleScope.launch {
            try {
                settingsVariantService.backupAllVariants()
                Toast.makeText(context, "All variants backed up successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importVariants() {
        lifecycleScope.launch {
            try {
                val importedCount = settingsVariantService.importVariants()
                Toast.makeText(context, "$importedCount variants imported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportAllVariants() {
        lifecycleScope.launch {
            try {
                settingsVariantService.exportAllVariants()
                Toast.makeText(context, "All variants exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showVariantOptionsMenu(variant: SettingsVariant) {
        val options = arrayOf("Duplicate", "Rename", "Export", "Delete")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(variant.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> duplicateVariant(variant)
                    1 -> renameVariant(variant)
                    2 -> exportVariant(variant)
                    3 -> deleteVariant(variant)
                }
            }
            .show()
    }

    private fun duplicateVariant(variant: SettingsVariant) {
        lifecycleScope.launch {
            try {
                settingsVariantService.duplicateVariant(variant.id)
                Toast.makeText(context, "Variant duplicated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Duplication failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renameVariant(variant: SettingsVariant) {
        // Implementation for rename dialog would go here
        Toast.makeText(context, "Rename functionality not yet implemented", Toast.LENGTH_SHORT).show()
    }

    private fun exportVariant(variant: SettingsVariant) {
        lifecycleScope.launch {
            try {
                settingsVariantService.exportVariants(listOf(variant))
                Toast.makeText(context, "Variant exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteVariant(variant: SettingsVariant) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Variant")
            .setMessage("Are you sure you want to delete '${variant.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    settingsVariantService.deleteVariant(variant.id)
                    Toast.makeText(context, "Variant deleted successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyStates(variants: List<SettingsVariant>) {
        binding.loadTabContent.emptyStateLoad.visibility = if (variants.isEmpty()) View.VISIBLE else View.GONE
        binding.manageTabContent.emptyStateManage.visibility = if (variants.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStorageInfo(variants: List<SettingsVariant>) {
        binding.manageTabContent.totalVariantsText.text = variants.size.toString()
        
        lifecycleScope.launch {
            val storageInfo = settingsVariantService.getStorageInfo()
            binding.manageTabContent.storageUsedText.text = storageInfo.formattedSize
            binding.manageTabContent.lastBackupText.text = storageInfo.lastBackupDate ?: "Never"
        }
    }

    fun setOnVariantAppliedListener(listener: (SettingsVariant, List<SettingsCategory>) -> Unit) {
        onVariantApplied = listener
    }

    fun setOnVariantSavedListener(listener: (SettingsVariant) -> Unit) {
        onVariantSaved = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingsVariantManagerDialog {
            return SettingsVariantManagerDialog()
        }
    }
}

private val SettingsCategory.displayName: String
    get() = when (this) {
        SettingsCategory.VISUAL -> "Visual Settings"
        SettingsCategory.ANALYSIS -> "Analysis Parameters"
        SettingsCategory.SEARCH -> "Search & Filters"
        SettingsCategory.CONDITIONS -> "Condition Management"
        SettingsCategory.PLOTS -> "Plot Configurations"
        SettingsCategory.PREFERENCES -> "App Preferences"
        SettingsCategory.ALL -> "All Categories"
    }