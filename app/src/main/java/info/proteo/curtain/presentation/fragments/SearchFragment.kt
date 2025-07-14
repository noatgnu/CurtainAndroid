package info.proteo.curtain.presentation.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.data.models.*
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.DataFilterList
import info.proteo.curtain.databinding.FragmentSearchBinding
import info.proteo.curtain.databinding.ItemSearchListBinding
import info.proteo.curtain.databinding.ItemFilterListImportBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.presentation.dialogs.ColorPickerDialog
import info.proteo.curtain.presentation.dialogs.ColorManagementDialog
import info.proteo.curtain.presentation.dialogs.RenameSearchListDialog
import info.proteo.curtain.presentation.dialogs.SearchListDetailsDialog
import info.proteo.curtain.presentation.dialogs.BulkSearchListActionsDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// Wrapper class for filter lists with category information
data class FilterListWithCategory(
    val id: Int,
    val name: String,
    val data: String,
    val category: String,
    val isDefault: Boolean
)

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var searchService: SearchService

    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var searchListAdapter: SearchListAdapter
    private lateinit var filterListAdapter: FilterListImportAdapter

    private var typeaheadJob: Job? = null
    private var currentSearchType = SearchType.PRIMARY_ID
    private var availableFilterLists = listOf<FilterListWithCategory>()
    private var selectedCategory = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupSearchTypeSelector()
        setupSingleSearch()
        setupBatchSearch()
        setupFilterListImport()
        setupSearchListsRecyclerView()
        observeSearchState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupSearchTypeSelector() {
        binding.searchTypeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentSearchType = when (checkedIds.firstOrNull()) {
                R.id.primaryIdChip -> SearchType.PRIMARY_ID
                R.id.geneNameChip -> SearchType.GENE_NAME
                R.id.accessionIdChip -> SearchType.ACCESSION_ID
                else -> SearchType.PRIMARY_ID
            }
            updateSearchHints()
        }
    }

    private fun updateSearchHints() {
        val hint = when (currentSearchType) {
            SearchType.PRIMARY_ID -> "Enter protein ID"
            SearchType.GENE_NAME -> "Enter gene name"
            SearchType.ACCESSION_ID -> "Enter accession ID"
        }
        binding.singleSearchInputLayout.hint = hint
        binding.batchSearchInputLayout.hint = when (currentSearchType) {
            SearchType.PRIMARY_ID -> "Enter protein IDs (one per line)"
            SearchType.GENE_NAME -> "Enter gene names (one per line)"
            SearchType.ACCESSION_ID -> "Enter accession IDs (one per line)"
        }
    }

    private fun setupSingleSearch() {
        // Setup typeahead functionality
        binding.singleSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    performTypeaheadSearch(query)
                }
            }
        })

        binding.singleSearchInput.setOnEditorActionListener { _, _, _ ->
            performSingleSearch()
            true
        }

        binding.singleSearchButton.setOnClickListener {
            performSingleSearch()
        }
    }

    private fun setupBatchSearch() {
        binding.batchSearchButton.setOnClickListener {
            performBatchSearch()
        }
    }
    
    private fun setupFilterListImport() {
        setupFilterListRecyclerView()
        setupCategoryFilter()
        
        binding.syncFilterListsButton.setOnClickListener {
            syncFilterLists()
        }
        
        // Load initial filter lists
        loadFilterLists()
    }
    
    private fun setupFilterListRecyclerView() {
        filterListAdapter = FilterListImportAdapter { filterList ->
            importFilterList(filterList)
        }
        
        binding.filterListsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = filterListAdapter
        }
    }
    
    private fun setupCategoryFilter() {
        binding.filterListCategorySpinner.setOnItemClickListener { _, _, position, _ ->
            val adapter = binding.filterListCategorySpinner.adapter
            selectedCategory = adapter.getItem(position) as String
            filterFilterLists()
        }
    }

    private fun setupSearchListsRecyclerView() {
        searchListAdapter = SearchListAdapter { action, searchList ->
            when (action) {
                SearchListAction.TOGGLE_FILTER -> {
                    searchService.toggleSearchListFilter(searchList.id)
                }
                SearchListAction.DELETE -> {
                    deleteSearchList(searchList)
                }
                SearchListAction.RENAME -> {
                    renameSearchList(searchList)
                }
                SearchListAction.VIEW_DETAILS -> {
                    viewSearchListDetails(searchList)
                }
                SearchListAction.EXPORT -> {
                    exportSearchList(searchList)
                }
                SearchListAction.CHANGE_COLOR -> {
                    changeSearchListColor(searchList)
                }
            }
        }

        binding.searchListsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchListAdapter
        }

        binding.clearFiltersButton.setOnClickListener {
            searchService.clearAllFilters()
        }
        
        binding.bulkActionsButton.setOnClickListener {
            showBulkActionsDialog()
        }
        
        binding.manageColorsButton.setOnClickListener {
            showColorManagementDialog()
        }
    }

    private fun observeSearchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            searchService.searchSession.collect { session ->
                searchListAdapter.submitList(session.searchLists, session.activeFilters)
                binding.noSearchListsText.visibility = 
                    if (session.searchLists.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            searchService.isSearching.collect { isSearching ->
                binding.loadingOverlay.visibility = if (isSearching) View.VISIBLE else View.GONE
            }
        }
    }

    private fun performTypeaheadSearch(query: String) {
        typeaheadJob?.cancel()
        typeaheadJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300) // Debounce
            
            val curtainData = viewModel.curtainData.value ?: return@launch
            val suggestions = searchService.performTypeaheadSearch(query, currentSearchType, curtainData)
            
            // Update autocomplete adapter
            val suggestionTexts = suggestions.map { it.text }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestionTexts)
            binding.singleSearchInput.setAdapter(adapter)
        }
    }

    private fun performSingleSearch() {
        val query = binding.singleSearchInput.text?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            showSnackbar("Please enter a search term")
            return
        }

        val curtainData = viewModel.curtainData.value
        if (curtainData == null) {
            showSnackbar("No data loaded")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val listName = "Search: $query"
            val (results, statistics) = searchService.performSingleSearch(
                searchTerm = query,
                searchType = currentSearchType,
                curtainData = curtainData,
                createList = true,
                listName = listName
            )

            showSearchResults(results, statistics)
            binding.singleSearchInput.setText("")
        }
    }

    private fun performBatchSearch() {
        val listName = binding.listNameInput.text?.toString()?.trim()
        val searchText = binding.batchSearchInput.text?.toString()?.trim()

        if (listName.isNullOrEmpty()) {
            showSnackbar("Please enter a list name")
            return
        }

        if (searchText.isNullOrEmpty()) {
            showSnackbar("Please enter search terms")
            return
        }

        val curtainData = viewModel.curtainData.value
        if (curtainData == null) {
            showSnackbar("No data loaded")
            return
        }

        // Process search terms exactly like the frontend batch-search component
        val searchTerms = listOf(searchText) // Pass as single string to be processed by the service

        if (searchTerms.isEmpty()) {
            showSnackbar("No valid search terms found")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val request = BatchSearchRequest(
                searchTerms = searchTerms,
                searchType = currentSearchType,
                listName = listName,
                overwriteExisting = true
            )

            val (results, statistics) = searchService.performBatchSearch(request, curtainData)
            showSearchResults(results, statistics)
            
            // Clear inputs
            binding.listNameInput.setText("")
            binding.batchSearchInput.setText("")
        }
    }

    private fun loadFilterLists() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get filter lists with categories from SearchService
            val filterListsByCategory = searchService.getFilterListsByCategory()
            availableFilterLists = filterListsByCategory.flatMap { (category, filterLists) ->
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
            updateCategorySpinner()
            filterFilterLists()
        }
    }
    
    private fun updateCategorySpinner() {
        val categories = listOf("All") + availableFilterLists.map { it.category }.distinct().sorted()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.filterListCategorySpinner.setAdapter(adapter)
        
        if (selectedCategory !in categories) {
            selectedCategory = "All"
        }
        binding.filterListCategorySpinner.setText(selectedCategory, false)
    }
    
    private fun filterFilterLists() {
        val filteredLists = if (selectedCategory == "All") {
            availableFilterLists
        } else {
            availableFilterLists.filter { it.category == selectedCategory }
        }
        
        filterListAdapter.submitList(filteredLists)
        binding.noFilterListsText.visibility = if (filteredLists.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun syncFilterLists() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.syncFilterListsButton.isEnabled = false
            binding.syncFilterListsButton.text = "Syncing..."
            
            val success = searchService.syncFilterLists()
            if (success) {
                loadFilterLists()
                showSnackbar("Filter lists synced successfully")
            } else {
                showSnackbar("Failed to sync filter lists")
            }
            
            binding.syncFilterListsButton.isEnabled = true
            binding.syncFilterListsButton.text = "Sync"
        }
    }
    
    private fun importFilterList(filterList: FilterListWithCategory) {
        val curtainData = viewModel.curtainData.value
        if (curtainData == null) {
            showSnackbar("No data loaded")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val request = FilterListImportRequest(
                filterListId = filterList.id,
                listName = filterList.name,
                overwriteExisting = true
            )
            
            val (searchList, statistics) = searchService.importFilterList(request, curtainData)
            
            if (searchList != null) {
                val message = "Imported '${searchList.name}': ${statistics.matchedProteins} proteins"
                showSnackbar(message)
            } else {
                showSnackbar("Failed to import filter list: no matching proteins found")
            }
        }
    }

    private fun showSearchResults(results: List<SearchResult>, statistics: SearchStatistics) {
        val message = buildString {
            append("Found ${statistics.matchedProteins} proteins")
            if (statistics.unmatchedTerms.isNotEmpty()) {
                append(" (${statistics.unmatchedTerms.size} terms not found)")
            }
        }
        showSnackbar(message)
    }

    private fun deleteSearchList(searchList: SearchList) {
        searchService.removeSearchList(searchList.id)
        showSnackbar("Deleted '${searchList.name}'")
    }

    private fun renameSearchList(searchList: SearchList) {
        val renameDialog = RenameSearchListDialog.newInstance(
            currentName = searchList.name
        ) { newName ->
            val success = searchService.renameSearchList(searchList.id, newName)
            if (success) {
                showSnackbar("Renamed to '$newName'")
            } else {
                showSnackbar("Failed to rename search list")
            }
        }
        
        renameDialog.show(parentFragmentManager, "RenameSearchListDialog")
    }

    private fun viewSearchListDetails(searchList: SearchList) {
        val detailsDialog = SearchListDetailsDialog.newInstance(searchList)
        detailsDialog.show(parentFragmentManager, "SearchListDetailsDialog")
    }
    
    private fun exportSearchList(searchList: SearchList) {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = searchService.exportSearchListAsFilterList(searchList, "Custom")
            if (success) {
                showSnackbar("Exported '${searchList.name}' as filter list")
            } else {
                showSnackbar("Failed to export search list")
            }
        }
    }
    
    private fun changeSearchListColor(searchList: SearchList) {
        val colorPickerDialog = ColorPickerDialog.newInstance(
            listName = searchList.name,
            currentColor = searchList.color
        ) { newColor ->
            val success = searchService.changeSearchListColor(searchList.id, newColor)
            if (success) {
                showSnackbar("Color changed for '${searchList.name}'")
            } else {
                showSnackbar("Failed to change color")
            }
        }
        
        colorPickerDialog.show(parentFragmentManager, "ColorPickerDialog")
    }
    
    private fun showColorManagementDialog() {
        val colorManagementDialog = ColorManagementDialog.newInstance()
        colorManagementDialog.show(parentFragmentManager, "ColorManagementDialog")
    }
    
    private fun showBulkActionsDialog() {
        val bulkActionsDialog = BulkSearchListActionsDialog.newInstance()
        bulkActionsDialog.show(parentFragmentManager, "BulkSearchListActionsDialog")
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        typeaheadJob?.cancel()
        _binding = null
    }
}

enum class SearchListAction {
    TOGGLE_FILTER,
    DELETE,
    RENAME,
    VIEW_DETAILS,
    EXPORT,
    CHANGE_COLOR
}

class SearchListAdapter(
    private val onAction: (SearchListAction, SearchList) -> Unit
) : RecyclerView.Adapter<SearchListAdapter.SearchListViewHolder>() {

    private var searchLists = listOf<SearchList>()
    private var activeFilterIds = setOf<String>()

    fun submitList(lists: List<SearchList>, activeFilters: Set<String> = emptySet()) {
        searchLists = lists
        activeFilterIds = activeFilters
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchListViewHolder {
        val binding = ItemSearchListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchListViewHolder, position: Int) {
        holder.bind(searchLists[position])
    }

    override fun getItemCount(): Int = searchLists.size

    inner class SearchListViewHolder(
        private val binding: ItemSearchListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(searchList: SearchList) {
            binding.apply {
                // Set list name
                listName.text = searchList.name

                // Set details
                val searchTypeText = when (searchList.searchType) {
                    SearchType.PRIMARY_ID -> "Primary ID"
                    SearchType.GENE_NAME -> "Gene Name"
                    SearchType.ACCESSION_ID -> "Accession ID"
                }
                
                val dateText = SimpleDateFormat("MMM dd", Locale.getDefault())
                    .format(Date(searchList.createdAt))
                
                listDetails.text = "$searchTypeText • ${searchList.proteinIds.size} proteins • $dateText"

                // Set description
                if (searchList.description.isNullOrEmpty()) {
                    listDescription.visibility = View.GONE
                } else {
                    listDescription.visibility = View.VISIBLE
                    listDescription.text = searchList.description
                }

                // Set color indicator
                try {
                    val color = android.graphics.Color.parseColor(searchList.color)
                    colorIndicator.backgroundTintList = 
                        ContextCompat.getColorStateList(itemView.context, android.R.color.transparent)
                    colorIndicator.setBackgroundColor(color)
                } catch (e: Exception) {
                    // Fallback to default color
                    colorIndicator.backgroundTintList = 
                        ContextCompat.getColorStateList(itemView.context, R.color.primary)
                }
                
                // Make color indicator clickable to change color
                colorIndicator.setOnClickListener {
                    onAction(SearchListAction.CHANGE_COLOR, searchList)
                }

                // Set filter checkbox based on current filter state
                filterCheckbox.isChecked = searchList.id in activeFilterIds
                filterCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    onAction(SearchListAction.TOGGLE_FILTER, searchList)
                }

                // Set menu button
                menuButton.setOnClickListener { view ->
                    showListMenu(view, searchList)
                }
            }
        }

        private fun showListMenu(anchor: View, searchList: SearchList) {
            val popupMenu = PopupMenu(anchor.context, anchor)
            popupMenu.menuInflater.inflate(R.menu.search_list_menu, popupMenu.menu)
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_view_details -> {
                        onAction(SearchListAction.VIEW_DETAILS, searchList)
                        true
                    }
                    R.id.action_rename -> {
                        onAction(SearchListAction.RENAME, searchList)
                        true
                    }
                    R.id.action_change_color -> {
                        onAction(SearchListAction.CHANGE_COLOR, searchList)
                        true
                    }
                    R.id.action_export -> {
                        onAction(SearchListAction.EXPORT, searchList)
                        true
                    }
                    R.id.action_delete -> {
                        onAction(SearchListAction.DELETE, searchList)
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
        }
    }
}

class FilterListImportAdapter(
    private val onImport: (FilterListWithCategory) -> Unit
) : RecyclerView.Adapter<FilterListImportAdapter.FilterListViewHolder>() {

    private var filterLists = listOf<FilterListWithCategory>()

    fun submitList(lists: List<FilterListWithCategory>) {
        filterLists = lists
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterListViewHolder {
        val binding = ItemFilterListImportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FilterListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterListViewHolder, position: Int) {
        holder.bind(filterLists[position])
    }

    override fun getItemCount(): Int = filterLists.size

    inner class FilterListViewHolder(
        private val binding: ItemFilterListImportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filterList: FilterListWithCategory) {
            binding.apply {
                filterListName.text = filterList.name
                categoryChip.text = filterList.category
                
                // Count proteins in the filter list
                val proteinCount = filterList.data.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .size
                proteinCountText.text = "$proteinCount proteins"
                
                // Show default badge
                defaultBadge.visibility = if (filterList.isDefault) View.VISIBLE else View.GONE
                
                // Set import button click
                importButton.setOnClickListener {
                    onImport(filterList)
                }
                
                // Set card click for details (optional)
                root.setOnClickListener {
                    // Could show more details or preview
                }
            }
        }
    }
}