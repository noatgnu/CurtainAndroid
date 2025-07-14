package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.data.models.SearchType
import info.proteo.curtain.data.models.TypeaheadSuggestion
import info.proteo.curtain.data.models.BatchSearchRequest
import info.proteo.curtain.data.models.FilterListImportRequest
import info.proteo.curtain.databinding.DialogProteinSearchBinding
import info.proteo.curtain.DataFilterList
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Wrapper class for filter lists with category information
data class FilterListWithCategory(
    val id: Int,
    val name: String,
    val data: String,
    val category: String,
    val isDefault: Boolean
)

@AndroidEntryPoint
class ProteinSearchDialog : DialogFragment() {

    private var _binding: DialogProteinSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    private lateinit var suggestionsAdapter: TypeaheadSuggestionsAdapter
    private lateinit var filterListAdapter: FilterListAdapter
    private var searchJob: Job? = null
    private var isTypeaheadMode = true
    private var allFilterLists = listOf<FilterListWithCategory>()
    private var filteredFilterLists = listOf<FilterListWithCategory>()
    
    // Separate data for each mode
    private var selectedTypeaheadTerm: String? = null
    private val browseSelectedTerms = mutableSetOf<String>()
    
    companion object {
        fun newInstance(): ProteinSearchDialog {
            return ProteinSearchDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogProteinSearchBinding.inflate(layoutInflater)
        
        // Initialize SearchService with current curtain data
        viewModel.curtainData.value?.let { curtainData ->
            viewModel.searchService.restoreSearchListsFromCurtainData(curtainData)
        }
        
        setupUI()
        setupInputModeToggle()
        setupTypeaheadMode()
        setupBrowseMode()
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Protein Search")
            .setView(binding.root)
            .setPositiveButton("Create Search List", null) // Set to null initially
            .setNegativeButton("Cancel", null)
            .create()
            
        // Override the positive button to prevent auto-dismiss
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                if (isTypeaheadMode) {
                    createTypeaheadSearchList(dialog)
                } else {
                    createBrowseSearchList(dialog)
                }
            }
        }
        
        return dialog
    }
    
    private fun setupUI() {
        // Setup search type spinner
        val searchTypes = SearchType.values()
        val searchTypeNames = searchTypes.map { it.displayName }
        val searchTypeAdapter = ArrayAdapter(
            requireContext(), 
            android.R.layout.simple_spinner_item, 
            searchTypeNames
        )
        searchTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.searchTypeSpinner.adapter = searchTypeAdapter
        
        // Default to PRIMARY_ID which should work with curtainData.allGenes
        binding.searchTypeSpinner.setSelection(SearchType.PRIMARY_ID.ordinal)
        
        // Default to typeahead mode
        binding.inputModeToggle.check(binding.typeaheadModeButton.id)
        
        // Set up mode-specific displays
        updateTypeaheadSelectedTermDisplay()
        updateBrowseSelectedTermsDisplay()
        
        // Setup suggestions RecyclerView
        suggestionsAdapter = TypeaheadSuggestionsAdapter { suggestion ->
            selectTypeaheadTerm(suggestion.text)
        }
        binding.suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionsAdapter
        }
        
        // Mode switching will handle displays
    }
    
    private fun setupInputModeToggle() {
        binding.inputModeToggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.typeaheadModeButton.id -> {
                        isTypeaheadMode = true
                        binding.typeaheadModeLayout.visibility = View.VISIBLE
                        binding.browseModeLayout.visibility = View.GONE
                    }
                    binding.browseModeButton.id -> {
                        isTypeaheadMode = false
                        binding.typeaheadModeLayout.visibility = View.GONE
                        binding.browseModeLayout.visibility = View.VISIBLE
                        loadFilterLists()
                    }
                }
            }
        }
    }
    
    private fun setupTypeaheadMode() {
        setupTypeaheadSearch()
    }
    
    private fun setupBrowseMode() {
        setupFilterListBrowser()
        setupBulkInput()
    }
    
    private fun setupBulkInput() {
        // Auto-process bulk input as user types
        binding.bulkInputEdit.addTextChangedListener { editable ->
            val input = editable?.toString()?.trim() ?: ""
            if (input.isNotEmpty()) {
                val parsedTerms = processBulkInput(input)
                browseSelectedTerms.clear()
                browseSelectedTerms.addAll(parsedTerms)
                updateBrowseSelectedTermsDisplay()
            } else {
                browseSelectedTerms.clear()
                updateBrowseSelectedTermsDisplay()
            }
        }
    }
    
    /**
     * Process bulk input exactly like SearchService.processBatchSearchInput()
     * Based on batch-search.component.ts handleSubmit() method
     */
    private fun processBulkInput(input: String): List<String> {
        val results = mutableSetOf<String>()
        
        // Replicate frontend logic: for (const r of this.data.replace("\r", "").split("\n"))
        val lines = input.replace("\r", "").split("\n")
        
        for (line in lines) {
            // const a = r.trim().toUpperCase()
            val processedLine = line.trim().uppercase()
            
            // if (a !== "")
            if (processedLine.isNotEmpty()) {
                // const e = a.split(";")
                val semicolonSplit = processedLine.split(";")
                
                // for (let f of e) { f = f.trim(); result[a].push(f) }
                for (subTerm in semicolonSplit) {
                    val trimmedSubTerm = subTerm.trim()
                    if (trimmedSubTerm.isNotEmpty()) {
                        results.add(trimmedSubTerm)
                    }
                }
            }
        }
        
        return results.toList()
    }
    
    private fun setupFilterListBrowser() {
        // Setup filter list RecyclerView
        filterListAdapter = FilterListAdapter { filterList ->
            onFilterListSelected(filterList)
        }
        binding.filterListsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = filterListAdapter
        }
        
        // Setup search functionality
        binding.filterListSearchEdit.addTextChangedListener { editable ->
            val query = editable?.toString() ?: ""
            filterFilterLists(query)
        }
        
        // Setup refresh button
        binding.refreshFilterListsButton.setOnClickListener {
            loadFilterLists()
        }
        
        // Setup clear button for browse mode
        binding.clearBrowseTermsButton.setOnClickListener {
            browseSelectedTerms.clear()
            binding.bulkInputEdit.setText("")
            binding.filterListSearchEdit.setText("")
            updateBrowseSelectedTermsDisplay()
            filterListAdapter.setSelectedFilterList(-1)
        }
    }
    
    private fun loadFilterLists() {
        lifecycleScope.launch {
            try {
                binding.refreshFilterListsButton.isEnabled = false
                binding.refreshFilterListsButton.text = "Loading..."
                
                // Load filter lists with categories from SearchService
                val filterListsByCategory = viewModel.searchService.getFilterListsByCategory()
                allFilterLists = filterListsByCategory.flatMap { (category, filterLists) ->
                    filterLists.map { filterList ->
                        FilterListWithCategory(
                            id = filterList.id,
                            name = filterList.name,
                            data = filterList.data,
                            category = category,
                            isDefault = filterList.isDefault
                        )
                    }
                }
                filteredFilterLists = allFilterLists
                
                // Setup category filter
                setupCategoryFilter()
                
                // Update adapter
                filterListAdapter.updateFilterLists(filteredFilterLists)
                
                Log.d("ProteinSearchDialog", "Loaded ${allFilterLists.size} filter lists")
            } catch (e: Exception) {
                Log.e("ProteinSearchDialog", "Error loading filter lists", e)
                Toast.makeText(requireContext(), "Error loading filter lists: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.refreshFilterListsButton.isEnabled = true
                binding.refreshFilterListsButton.text = "Refresh"
            }
        }
    }
    
    private fun setupCategoryFilter() {
        val categories = listOf("All Categories") + allFilterLists.map { it.category }.distinct().sorted()
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categoryFilterSpinner.adapter = categoryAdapter
        
        binding.categoryFilterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterFilterLists(binding.filterListSearchEdit.text?.toString() ?: "")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun filterFilterLists(searchQuery: String) {
        val selectedCategory = binding.categoryFilterSpinner.selectedItem?.toString()
        
        filteredFilterLists = allFilterLists.filter { filterList ->
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                filterList.name.contains(searchQuery, ignoreCase = true) ||
                filterList.category.contains(searchQuery, ignoreCase = true)
            }
            
            val matchesCategory = if (selectedCategory == "All Categories" || selectedCategory.isNullOrBlank()) {
                true
            } else {
                filterList.category == selectedCategory
            }
            
            matchesSearch && matchesCategory
        }
        
        filterListAdapter.updateFilterLists(filteredFilterLists)
    }
    
    private fun onFilterListSelected(filterList: FilterListWithCategory) {
        filterListAdapter.setSelectedFilterList(filterList.id)
        
        // Auto-fill search list name and textarea
        binding.filterListSearchEdit.setText(filterList.name)
        
        // Parse and format the filter list data
        val filterData = filterList.data
        val formattedData = filterData.replace(";", "\n") // Convert to line-separated
        binding.bulkInputEdit.setText(formattedData)
        
        // This will trigger the text watcher to update browseSelectedTerms
        
        Toast.makeText(
            requireContext(),
            "Loaded '${filterList.name}' with data",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    // Method removed - auto-fill happens directly in onFilterListSelected
    
    private fun setupTypeaheadSearch() {

        binding.searchQueryEdit.addTextChangedListener { editable ->
            val query = editable?.toString() ?: ""
            Log.d("ProteinSearchDialog", "Search query changed: '$query' (length: ${query.length})")
            
            // Cancel previous search
            searchJob?.cancel()
            
            if (query.length >= 2) {
                Log.d("ProteinSearchDialog", "Starting typeahead search for: '$query'")
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    performTypeaheadSearch(query)
                }
            } else {
                Log.d("ProteinSearchDialog", "Query too short, clearing suggestions")
                suggestionsAdapter.updateSuggestions(emptyList())
            }
        }
    }
    
    private suspend fun performTypeaheadSearch(query: String) {
        try {
            Log.d("ProteinSearchDialog", "performTypeaheadSearch called with query: '$query'")
            val selectedSearchType = SearchType.values()[binding.searchTypeSpinner.selectedItemPosition]
            val curtainData = viewModel.curtainData.value
            
            Log.d("ProteinSearchDialog", "Search type: $selectedSearchType, curtainData available: ${curtainData != null}")
            
            if (curtainData != null) {
                Log.d("ProteinSearchDialog", "CurtainData debug:")
                Log.d("ProteinSearchDialog", "  allGenes size: ${curtainData.allGenes?.size}")
                Log.d("ProteinSearchDialog", "  allGenes sample: ${curtainData.allGenes?.take(5)}")
                
                Log.d("ProteinSearchDialog", "Calling searchService.performTypeaheadSearch...")
                val suggestions = viewModel.searchService.performTypeaheadSearch(
                    query = query,
                    searchType = selectedSearchType,
                    curtainData = curtainData,
                    limit = 10
                )
                Log.d("ProteinSearchDialog", "Got ${suggestions.size} suggestions: ${suggestions.map { it.text }}")
                suggestionsAdapter.updateSuggestions(suggestions)
            } else {
                Log.w("ProteinSearchDialog", "CurtainData is null, cannot perform search")
            }
        } catch (e: Exception) {
            Log.e("ProteinSearchDialog", "Error performing typeahead search", e)
        }
    }
    
    private fun selectTypeaheadTerm(term: String) {
        selectedTypeaheadTerm = term
        updateTypeaheadSelectedTermDisplay()
        binding.searchQueryEdit.setText("")
    }
    
    private fun updateTypeaheadSelectedTermDisplay() {
        if (selectedTypeaheadTerm == null) {
            binding.selectedTermDisplay.text = "No term selected"
        } else {
            binding.selectedTermDisplay.text = "Selected: $selectedTypeaheadTerm"
        }
    }
    
    private fun updateBrowseSelectedTermsDisplay() {
        if (browseSelectedTerms.isEmpty()) {
            binding.browseSelectedTermsText.text = "No terms selected"
            binding.clearBrowseTermsButton.visibility = View.GONE
        } else {
            val termsText = if (browseSelectedTerms.size <= 10) {
                browseSelectedTerms.joinToString(", ")
            } else {
                browseSelectedTerms.take(10).joinToString(", ") + "... (${browseSelectedTerms.size} total)"
            }
            binding.browseSelectedTermsText.text = "Selected (${browseSelectedTerms.size}): $termsText"
            binding.clearBrowseTermsButton.visibility = View.VISIBLE
        }
    }
    
    private fun createTypeaheadSearchList(dialog: AlertDialog) {
        val searchListName = binding.typeaheadSearchListNameEdit.text?.toString()?.trim()
        
        if (searchListName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please enter a search list name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedTypeaheadTerm == null) {
            Toast.makeText(requireContext(), "Please select a search term", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedSearchType = SearchType.values()[binding.searchTypeSpinner.selectedItemPosition]
        val curtainData = viewModel.curtainData.value
        
        if (curtainData == null) {
            Toast.makeText(requireContext(), "No data available for search", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Get active comparisons from curtain data
                val activeComparisons = curtainData.differentialForm.comparisonSelect
                
                if (activeComparisons.isEmpty()) {
                    // No comparisons selected, create single search list
                    val request = BatchSearchRequest(
                        searchTerms = listOf(selectedTypeaheadTerm!!),
                        searchType = selectedSearchType,
                        listName = searchListName,
                        overwriteExisting = false
                    )
                    
                    val (searchResults, statistics) = viewModel.searchService.performBatchSearch(
                        request = request,
                        curtainData = curtainData
                    )
                    
                    if (statistics.matchedProteins > 0) {
                        Toast.makeText(
                            requireContext(), 
                            "Created search list '$searchListName' with ${statistics.matchedProteins} proteins",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Force refresh all fragments in the parent activity that might need updates
                        viewModel.refreshFromSearchUpdate()
                        
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(), 
                            "No proteins found matching the search criteria",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Create one search list per active comparison
                    var totalCreated = 0
                    var totalProteins = 0
                    
                    for (comparison in activeComparisons) {
                        val comparisonListName = "$searchListName ($comparison)"
                        val request = BatchSearchRequest(
                            searchTerms = listOf(selectedTypeaheadTerm!!),
                            searchType = selectedSearchType,
                            listName = comparisonListName,
                            overwriteExisting = false
                        )
                        
                        val (searchResults, statistics) = viewModel.searchService.performBatchSearch(
                            request = request,
                            curtainData = curtainData
                        )
                        
                        if (statistics.matchedProteins > 0) {
                            totalCreated++
                            totalProteins += statistics.matchedProteins
                        }
                    }
                    
                    if (totalCreated > 0) {
                        Toast.makeText(
                            requireContext(), 
                            "Created $totalCreated search lists with $totalProteins total proteins",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Force refresh all fragments in the parent activity that might need updates
                        viewModel.refreshFromSearchUpdate()
                        
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(), 
                            "No proteins found matching the search criteria in any comparison",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ProteinSearchDialog", "Error creating search list", e)
                Toast.makeText(
                    requireContext(), 
                    "Error creating search list: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun createBrowseSearchList(dialog: AlertDialog) {
        val searchListName = binding.filterListSearchEdit.text?.toString()?.trim()
        
        if (searchListName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please enter a search list name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (browseSelectedTerms.isEmpty()) {
            Toast.makeText(requireContext(), "Please add some search terms", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedSearchType = SearchType.values()[binding.searchTypeSpinner.selectedItemPosition]
        val curtainData = viewModel.curtainData.value
        
        if (curtainData == null) {
            Toast.makeText(requireContext(), "No data available for search", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Get active comparisons from curtain data
                val activeComparisons = curtainData.differentialForm.comparisonSelect
                
                if (activeComparisons.isEmpty()) {
                    // No comparisons selected, create single search list
                    val request = BatchSearchRequest(
                        searchTerms = browseSelectedTerms.toList(),
                        searchType = selectedSearchType,
                        listName = searchListName,
                        overwriteExisting = false
                    )
                    
                    val (searchResults, statistics) = viewModel.searchService.performBatchSearch(
                        request = request,
                        curtainData = curtainData
                    )
                    
                    if (statistics.matchedProteins > 0) {
                        Toast.makeText(
                            requireContext(), 
                            "Created search list '$searchListName' with ${statistics.matchedProteins} proteins",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Force refresh all fragments in the parent activity that might need updates
                        viewModel.refreshFromSearchUpdate()
                        
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(), 
                            "No proteins found matching the search criteria",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Create one search list per active comparison
                    var totalCreated = 0
                    var totalProteins = 0
                    
                    for (comparison in activeComparisons) {
                        val comparisonListName = "$searchListName ($comparison)"
                        val request = BatchSearchRequest(
                            searchTerms = browseSelectedTerms.toList(),
                            searchType = selectedSearchType,
                            listName = comparisonListName,
                            overwriteExisting = false
                        )
                        
                        val (searchResults, statistics) = viewModel.searchService.performBatchSearch(
                            request = request,
                            curtainData = curtainData
                        )
                        
                        if (statistics.matchedProteins > 0) {
                            totalCreated++
                            totalProteins += statistics.matchedProteins
                        }
                    }
                    
                    if (totalCreated > 0) {
                        Toast.makeText(
                            requireContext(), 
                            "Created $totalCreated search lists with $totalProteins total proteins",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Force refresh all fragments in the parent activity that might need updates
                        viewModel.refreshFromSearchUpdate()
                        
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(), 
                            "No proteins found matching the search criteria in any comparison",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ProteinSearchDialog", "Error creating search list", e)
                Toast.makeText(
                    requireContext(), 
                    "Error creating search list: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}

// Adapter for filter lists
private class FilterListAdapter(
    private val onFilterListClick: (FilterListWithCategory) -> Unit
) : RecyclerView.Adapter<FilterListAdapter.ViewHolder>() {
    
    private var filterLists = listOf<FilterListWithCategory>()
    private var selectedFilterListId: Int? = null
    
    fun updateFilterLists(newFilterLists: List<FilterListWithCategory>) {
        filterLists = newFilterLists
        notifyDataSetChanged()
    }
    
    fun setSelectedFilterList(filterListId: Int) {
        selectedFilterListId = filterListId
        notifyDataSetChanged()
    }
    
    fun getSelectedFilterList(): FilterListWithCategory? {
        return filterLists.find { it.id == selectedFilterListId }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filterLists[position])
    }
    
    override fun getItemCount() = filterLists.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
        private val subtitleText = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
        
        fun bind(filterList: FilterListWithCategory) {
            titleText.text = filterList.name
            subtitleText.text = "Category: ${filterList.category}"
            
            // Highlight selected item
            itemView.isSelected = filterList.id == selectedFilterListId
            itemView.setBackgroundColor(
                if (filterList.id == selectedFilterListId) {
                    0x4000FF00 // Light green tint
                } else {
                    0x00000000 // Transparent
                }
            )
            
            itemView.setOnClickListener {
                onFilterListClick(filterList)
            }
        }
    }
}

// Adapter for typeahead suggestions
private class TypeaheadSuggestionsAdapter(
    private val onSuggestionClick: (TypeaheadSuggestion) -> Unit
) : RecyclerView.Adapter<TypeaheadSuggestionsAdapter.ViewHolder>() {
    
    private var suggestions = listOf<TypeaheadSuggestion>()
    
    fun updateSuggestions(newSuggestions: List<TypeaheadSuggestion>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }
    
    override fun getItemCount() = suggestions.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
        private val subtitleText = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
        
        fun bind(suggestion: TypeaheadSuggestion) {
            titleText.text = suggestion.text
            subtitleText.text = suggestion.searchType.displayName
            
            itemView.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }
    }
}