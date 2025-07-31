package info.proteo.curtain.presentation.fragments.curtain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.AppData
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.databinding.FragmentProteinDetailListTabBinding
import info.proteo.curtain.databinding.ItemProteinDetailBinding
import info.proteo.curtain.databinding.ItemSampleDataBinding
import info.proteo.curtain.databinding.ItemConditionDataBinding
import info.proteo.curtain.data.services.ConditionColorService
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.utils.PlotlyChartGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.TextView
import info.proteo.curtain.R
import info.proteo.curtain.utils.PlotlyChartGenerator.ChartType
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.data.models.SearchList
import dagger.hilt.android.AndroidEntryPoint
import org.jetbrains.kotlinx.dataframe.api.getColumn
import javax.inject.Inject

@AndroidEntryPoint
class ProteinDetailListTabFragment : Fragment() {
    private var _binding: FragmentProteinDetailListTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var proteinDetailAdapter: ProteinDetailAdapter
    
    @Inject
    lateinit var searchService: SearchService
    
    @Inject
    lateinit var conditionColorService: ConditionColorService
    
    // Pagination constants and state
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_PAGE_SIZE = 10
    }
    
    private var currentPage = 0
    private var isLoading = false
    private var hasMoreData = true
    private var allSelectedProteins = listOf<String>()
    private val loadedProteinDetails = mutableListOf<ProteinDetail>()
    private var isImputationEnabled = false
    private var isPeptideCountEnabled = false
    private var selectedChartType = ChartType.INDIVIDUAL_BAR
    private var useSearchFilter = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProteinDetailListTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupPaginationControls()
        setupChartControls()
        setupSearchFilter()
        observeSearchState()
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isDataLoaded.collect { isDataLoaded ->
                    if (isDataLoaded && viewModel.curtainData.value != null) {
                        initializeConditionColors()
                        restoreStoredSearchLists()
                        loadProteinDetails()
                    }
                }
            }
        }
    }
    
    private fun setupPaginationControls() {
        binding.loadMoreButton.setOnClickListener {
            loadMoreProteins()
        }
    }
    
    private fun setupChartControls() {
        // Initialize imputation toggle state from settings
        isImputationEnabled = viewModel.curtainSettings.value?.enableImputation ?: false
        binding.imputationToggle.isChecked = isImputationEnabled
        
        // Initialize peptide count toggle state from settings
        isPeptideCountEnabled = viewModel.curtainSettings.value?.viewPeptideCount ?: false
        binding.peptideCountToggle.isChecked = isPeptideCountEnabled
        
        // Set up imputation toggle listener
        binding.imputationToggle.setOnCheckedChangeListener { _, isChecked ->
            isImputationEnabled = isChecked
            refreshAllCharts()
        }
        
        // Set up peptide count toggle listener
        binding.peptideCountToggle.setOnCheckedChangeListener { _, isChecked ->
            isPeptideCountEnabled = isChecked
            refreshAllCharts()
        }
    }
    
    
    private fun setupSearchFilter() {
        binding.searchFilterButton.setOnClickListener {
            Log.d("ProteinDetailList", "Search filter button clicked")
            showSearchFilterDialog()
        }
    }
    
    private fun observeSearchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            searchService.searchSession.collect { session ->
                updateSearchFilterButton(session.searchLists, session.activeFilters)
            }
        }
    }
    
    private fun updateSearchFilterButton(searchLists: List<SearchList>, activeFilters: Set<String>) {
        val searchSession = searchService.searchSession.value
        val activeSearchLists = searchLists.filter { it.id in activeFilters }
        val totalActiveFilters = activeFilters.size + searchSession.activeStoredSelections.size
        
        binding.searchFilterButton.text = when {
            totalActiveFilters == 0 -> "All Proteins"
            totalActiveFilters == 1 && activeSearchLists.isNotEmpty() -> activeSearchLists.first().name
            totalActiveFilters == 1 && searchSession.activeStoredSelections.isNotEmpty() -> searchSession.activeStoredSelections.first()
            else -> "$totalActiveFilters Filters"
        }
        
        val wasUsingFilter = useSearchFilter
        useSearchFilter = totalActiveFilters > 0
        
        // Only refresh if filter state changed and we have data loaded
        if (wasUsingFilter != useSearchFilter && (hasMoreData || loadedProteinDetails.isNotEmpty())) {
            Log.d("ProteinDetailList", "Filter changed: wasUsingFilter=$wasUsingFilter, useSearchFilter=$useSearchFilter")
            loadProteinDetails()
        }
    }
    
    private fun showSearchFilterDialog() {
        Log.d("ProteinDetailList", "showSearchFilterDialog() called")
        
        // Get both search lists and all stored selection operations
        val searchLists = searchService.getSearchLists()
        val allStoredSelections = searchService.getAllStoredSelections()
        
        Log.d("ProteinDetailList", "Search lists count: ${searchLists.size}")
        Log.d("ProteinDetailList", "Stored selections count: ${allStoredSelections.size}")
        
        if (searchLists.isEmpty() && allStoredSelections.isEmpty()) {
            Log.d("ProteinDetailList", "No selections available, showing toast")
            android.widget.Toast.makeText(
                requireContext(), 
                "No selection operations available. Operations are created through search lists or volcano plot selections.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Create a combined list with categories
        val filterOptions = mutableListOf<FilterOption>()
        filterOptions.add(FilterOption("All Proteins", "", FilterOption.Type.ALL))
        
        // Add search lists (user-created)
        if (searchLists.isNotEmpty()) {
            filterOptions.add(FilterOption("── Search Lists ──", "", FilterOption.Type.HEADER))
            searchLists.forEach { searchList ->
                val proteinCount = searchList.proteinIds.size
                filterOptions.add(FilterOption(
                    name = "${searchList.name} ($proteinCount proteins)",
                    id = searchList.id,
                    type = FilterOption.Type.SEARCH_LIST
                ))
            }
        }
        
        // Add stored selections (including volcano plot selections)
        val storedOnlySelections = allStoredSelections.filterKeys { operationName ->
            // Only show operations that aren't already in search lists
            searchLists.none { it.name == operationName }
        }
        
        if (storedOnlySelections.isNotEmpty()) {
            filterOptions.add(FilterOption("── Stored Selections ──", "", FilterOption.Type.HEADER))
            storedOnlySelections.forEach { (operationName, proteinIds) ->
                filterOptions.add(FilterOption(
                    name = "$operationName (${proteinIds.size} proteins)",
                    id = operationName,
                    type = FilterOption.Type.STORED_SELECTION
                ))
            }
        }
        
        val listNames = filterOptions.map { it.name }.toTypedArray()
        val searchSession = searchService.searchSession.value
        val checkedItems = BooleanArray(filterOptions.size) { index ->
            val option = filterOptions[index]
            when (option.type) {
                FilterOption.Type.ALL -> searchSession.activeFilters.isEmpty() && searchSession.activeStoredSelections.isEmpty()
                FilterOption.Type.SEARCH_LIST -> option.id in searchSession.activeFilters
                FilterOption.Type.STORED_SELECTION -> option.id in searchSession.activeStoredSelections
                FilterOption.Type.HEADER -> false
            }
        }
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Filter Protein Details")
            .setMultiChoiceItems(listNames, checkedItems) { dialog, which, isChecked ->
                val option = filterOptions[which]
                if (option.type != FilterOption.Type.HEADER) {
                    checkedItems[which] = isChecked
                    
                    // Handle mutual exclusivity between "All Proteins" and other options
                    if (option.type == FilterOption.Type.ALL && isChecked) {
                        // If "All Proteins" is checked, uncheck all other options
                        for (i in checkedItems.indices) {
                            if (i != which && filterOptions[i].type != FilterOption.Type.HEADER) {
                                checkedItems[i] = false
                            }
                        }
                        // Update the dialog to reflect changes
                        (dialog as AlertDialog).listView.post {
                            for (i in checkedItems.indices) {
                                dialog.listView.setItemChecked(i, checkedItems[i])
                            }
                        }
                    } else if (option.type != FilterOption.Type.ALL && isChecked) {
                        // If any other option is checked, uncheck "All Proteins"
                        val allProteinsIndex = filterOptions.indexOfFirst { it.type == FilterOption.Type.ALL }
                        if (allProteinsIndex >= 0) {
                            checkedItems[allProteinsIndex] = false
                            // Update the dialog to reflect changes
                            (dialog as AlertDialog).listView.post {
                                dialog.listView.setItemChecked(allProteinsIndex, false)
                            }
                        }
                    }
                }
            }
            .setPositiveButton("Apply") { _, _ ->
                applyAdvancedSearchFilter(filterOptions, checkedItems)
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
    }
    
    private data class FilterOption(
        val name: String,
        val id: String,
        val type: Type
    ) {
        enum class Type {
            ALL, SEARCH_LIST, STORED_SELECTION, HEADER
        }
    }
    
    private fun applyAdvancedSearchFilter(filterOptions: List<FilterOption>, checkedItems: BooleanArray) {
        Log.d("ProteinDetailList", "applyAdvancedSearchFilter called")
        
        val selectedOptions = filterOptions.filterIndexed { index, option ->
            checkedItems[index] && option.type != FilterOption.Type.HEADER
        }
        
        Log.d("ProteinDetailList", "Selected options: ${selectedOptions.map { "${it.name} (${it.type})" }}")
        
        if (selectedOptions.any { it.type == FilterOption.Type.ALL }) {
            // "All Proteins" selected - clear all filters
            Log.d("ProteinDetailList", "Clearing all filters")
            searchService.clearAllFilters()
        } else {
            // Apply selected search list filters
            val selectedSearchListIds = selectedOptions
                .filter { it.type == FilterOption.Type.SEARCH_LIST }
                .map { it.id }
            
            // Apply selected stored selection filters
            val selectedStoredSelectionNames = selectedOptions
                .filter { it.type == FilterOption.Type.STORED_SELECTION }
                .map { it.id }
            
            Log.d("ProteinDetailList", "Setting search list filters: $selectedSearchListIds")
            Log.d("ProteinDetailList", "Setting stored selection filters: $selectedStoredSelectionNames")
            
            searchService.setSearchListFilters(selectedSearchListIds)
            searchService.setStoredSelectionFilters(selectedStoredSelectionNames)
        }
    }
    
    private fun applySearchFilter(searchLists: List<SearchList>, checkedItems: BooleanArray) {
        if (checkedItems[0]) {
            // "All Proteins" selected - clear all filters
            searchService.clearAllFilters()
        } else {
            // Apply selected search list filters
            val selectedListIds = searchLists.filterIndexed { index, _ ->
                checkedItems[index + 1]
            }.map { it.id }
            
            searchService.setSearchListFilters(selectedListIds)
        }
    }
    
    private fun initializeConditionColors() {
        // Import condition colors from curtain settings when data is loaded
        viewModel.curtainSettings.value?.let { settings ->
            conditionColorService.importFromCurtainSettings(
                colorMap = settings.colorMap,
                sampleMap = settings.sampleMap,
                defaultColorList = settings.defaultColorList,
                conditionOrder = settings.conditionOrder,
                barchartColorMap = settings.barchartColorMap
            )
        }
    }
    
    private fun restoreStoredSearchLists() {
        // Restore search lists from stored CurtainDataService data
        viewModel.curtainData.value?.let { curtainData ->
            Log.d("ProteinDetailList", "Restoring search lists from curtain data")
            Log.d("ProteinDetailList", "selectedMap size: ${curtainData.selectedMap.size}")
            Log.d("ProteinDetailList", "selectOperationNames size: ${curtainData.selectOperationNames.size}")
            searchService.restoreSearchListsFromCurtainData(curtainData)
        } ?: Log.d("ProteinDetailList", "No curtain data available for restoring search lists")
    }
    

    private fun setupRecyclerView() {
        proteinDetailAdapter = ProteinDetailAdapter(
            colorMapProvider = { viewModel.curtainSettings.value?.colorMap ?: mapOf() },
            imputationProvider = { 
                ImputationSettings(
                    isEnabled = isImputationEnabled,
                    imputationMap = viewModel.curtainSettings.value?.imputationMap ?: mapOf(),
                    peptideCountData = viewModel.curtainSettings.value?.peptideCountData ?: mapOf(),
                    viewPeptideCount = isPeptideCountEnabled,
                    chartType = selectedChartType
                )
            },
            fragment = this
        )
        val layoutManager = LinearLayoutManager(requireContext())
        
        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = proteinDetailAdapter
            
            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()
                    
                    // Load more when user scrolls to near the end
                    if (!isLoading && hasMoreData) {
                        if (visibleItemCount + pastVisibleItems >= totalItemCount - 3) {
                            loadMoreProteins()
                        }
                    }
                }
            })
        }
    }
    
    private fun refreshAllCharts() {
        // Force refresh all charts with new imputation settings
        proteinDetailAdapter.notifyDataSetChanged()
    }
    
    fun refreshChartsWithNewColors() {
        // Public method to refresh charts when condition colors are updated
        refreshAllCharts()
    }
    
    private fun updatePaginationInfo() {
        val loadedCount = loadedProteinDetails.size
        val totalCount = allSelectedProteins.size
        val remainingCount = maxOf(0, totalCount - loadedCount)
        
        Log.d("ProteinDetailList", "Pagination: loaded=$loadedCount, total=$totalCount, remaining=$remainingCount, hasMore=$hasMoreData, useFilter=$useSearchFilter")
        
        // Update pagination info text
        binding.paginationInfo.apply {
            text = "Showing $loadedCount of $totalCount proteins"
            visibility = if (totalCount > 0) View.VISIBLE else View.GONE
        }
        
        // Update load more button visibility and text
        binding.loadMoreLayout.visibility = if (hasMoreData && totalCount > 0) View.VISIBLE else View.GONE
        binding.loadMoreButton.text = if (hasMoreData && remainingCount > 0) {
            "Load More Proteins ($remainingCount remaining)"
        } else {
            "All proteins loaded"
        }
        binding.loadMoreButton.isEnabled = hasMoreData && remainingCount > 0
    }

    private fun loadProteinDetails() {
        // Reset pagination state
        currentPage = 0
        hasMoreData = true
        loadedProteinDetails.clear()
        
        loadInitialPage()
    }
    
    private fun loadInitialPage() {
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        showLoading(true)
        isLoading = true

        // Run the heavy processing in a background thread
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Background processing
                val result = withContext(Dispatchers.IO) {
                    // Update loading message
                    withContext(Dispatchers.Main) {
                        binding.loadingText.text = "Checking selected proteins..."
                    }
                    
                    // Check if there are any selected proteins
                    val selectedProteins = getSelectedProteins(curtainData)
                    
                    if (selectedProteins.isEmpty()) {
                        return@withContext null // Signal no selection
                    }
                    
                    // Store all selected proteins for pagination
                    allSelectedProteins = selectedProteins
                    
                    // Update loading message
                    withContext(Dispatchers.Main) {
                        binding.loadingText.text = "Loading initial ${INITIAL_PAGE_SIZE} of ${selectedProteins.size} proteins..."
                    }

                    // Process only the first page
                    val pageProteins = selectedProteins.take(INITIAL_PAGE_SIZE)
                    processProteinDetails(curtainData, curtainSettings, pageProteins)
                }
                
                // Back on main thread for UI updates
                if (result == null) {
                    showNoSelection()
                } else {
                    loadedProteinDetails.addAll(result)
                    proteinDetailAdapter.submitList(loadedProteinDetails.toList())
                    proteinDetailAdapter.updateColorMap(viewModel.curtainSettings.value?.colorMap ?: mapOf())
                    
                    // Check if there's more data
                    hasMoreData = loadedProteinDetails.size < allSelectedProteins.size
                    updatePaginationInfo()
                    
                    showContent()
                }
                
                isLoading = false
            } catch (e: Exception) {
                Log.e("ProteinDetailList", "Error loading protein details: ${e.message}", e)
                showError("Error loading protein details: ${e.message}")
                isLoading = false
            }
        }
    }
    
    private fun loadMoreProteins() {
        if (isLoading || !hasMoreData) return
        
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        isLoading = true
        currentPage++
        
        showLoadMoreProgress(true)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    
                    val startIndex = INITIAL_PAGE_SIZE + (currentPage - 1) * PAGE_SIZE
                    val endIndex = minOf(startIndex + PAGE_SIZE, allSelectedProteins.size)
                    
                    if (startIndex >= allSelectedProteins.size) {
                        return@withContext emptyList<ProteinDetail>()
                    }
                    
                    val pageProteins = allSelectedProteins.subList(startIndex, endIndex)
                    
                    processProteinDetails(curtainData, curtainSettings, pageProteins)
                }
                
                if (result.isNotEmpty()) {
                    loadedProteinDetails.addAll(result)
                    proteinDetailAdapter.submitList(loadedProteinDetails.toList())
                    proteinDetailAdapter.updateColorMap(viewModel.curtainSettings.value?.colorMap ?: mapOf())
                }
                
                // Check if there's more data
                hasMoreData = loadedProteinDetails.size < allSelectedProteins.size
                updatePaginationInfo()
                
                showLoadMoreProgress(false)
                isLoading = false
                
            } catch (e: Exception) {
                Log.e("ProteinDetailList", "Error loading more proteins: ${e.message}", e)
                showLoadMoreProgress(false)
                isLoading = false
            }
        }
    }

    private fun getSelectedProteins(curtainData: AppData): List<String> {
        // Apply search filter if enabled
        return if (useSearchFilter) {
            val filteredProteins = searchService.getFilteredProteinIds()
            Log.d("ProteinDetailList", "Using search filter: ${filteredProteins.size} filtered proteins")
            if (filteredProteins.isNotEmpty()) {
                filteredProteins
            } else {
                // If no search filter results, show all proteins with any selection
                val allProteins = getAllSelectedProteins(curtainData)
                Log.d("ProteinDetailList", "Search filter returned empty, showing all ${allProteins.size} proteins")
                allProteins
            }
        } else {
            // Show all proteins with any selection
            val allProteins = getAllSelectedProteins(curtainData)
            Log.d("ProteinDetailList", "No search filter, showing all ${allProteins.size} proteins")
            allProteins
        }
    }
    
    private fun getAllSelectedProteins(curtainData: AppData): List<String> {
        val selectedProteins = mutableSetOf<String>()
        
        // Get all proteins that are selected in any operation
        curtainData.selectedMap.forEach { (proteinId, selections) ->
            // Since selectedMap only contains true values, any protein in the map is selected
            if (selections.isNotEmpty()) {
                selectedProteins.add(proteinId)
            }
        }
        
        return selectedProteins.toList()
    }

    private suspend fun processProteinDetails(
        curtainData: AppData,
        curtainSettings: CurtainSettings,
        selectedProteins: List<String>
    ): List<ProteinDetail> = withContext(Dispatchers.IO) {
        val details = mutableListOf<ProteinDetail>()
        
        // Get raw data
        val rawData = curtainData.raw.df
        val primaryIdColumn = curtainData.rawForm.primaryIDs
        val sampleColumns = curtainData.rawForm.samples
        
        // Group samples by condition and order by conditionOrder
        val samplesByCondition = groupSamplesByConditionOrdered(sampleColumns, curtainSettings)
        
        Log.d("ProteinDetailList", "Processing ${selectedProteins.size} selected proteins...")

        // Process each selected protein
        selectedProteins.forEachIndexed { index, proteinId ->
            Log.d("ProteinDetailList", "Processing protein ${index + 1}/${selectedProteins.size}: $proteinId")
            
            // Update progress on main thread every 3 proteins or on last protein (more frequent for smaller batches)
            if (index % 3 == 0 || index == selectedProteins.size - 1) {
                withContext(Dispatchers.Main) {
                    binding.loadingText.text = "Processing protein ${index + 1}/${selectedProteins.size}..."
                }
            }
            
            // Find the row for this protein
            for (rowIndex in 0 until rawData.rowsCount()) {
                curtainData.raw.df.getColumn(primaryIdColumn).get(rowIndex)?.let { rowId ->
                    if (rowId.toString() == proteinId) {
                        val uniprotRecord = viewModel.uniprotService.getUniprotFromPrimary(proteinId)
                        val geneName = uniprotRecord?.get("Gene Names")
                        
                        // Collect all condition data for this protein
                        val conditionDataList = mutableListOf<ConditionData>()
                        
                        samplesByCondition.forEach { (condition, samples) ->
                            val sampleData = mutableListOf<SampleData>()
                            
                            samples.forEach { sampleName ->
                                val value = try {
                                    curtainData.raw.df.getColumn(sampleName).get(rowIndex)?.toString() ?: "N/A"
                                } catch (e: Exception) {
                                    "N/A"
                                }
                                sampleData.add(SampleData(sampleName, value))
                            }
                            
                            conditionDataList.add(ConditionData(condition, sampleData))
                        }
                        
                        details.add(
                            ProteinDetail(
                                proteinId = proteinId,
                                geneName = geneName?.toString() ?: proteinId,
                                conditionDataList = conditionDataList
                            )
                        )
                        return@forEachIndexed
                    }
                }
            }
        }
        
        Log.d("ProteinDetailList", "Finished processing. Found ${details.size} protein details.")
        details.sortedBy { it.proteinId }
    }

    private suspend fun groupSamplesByConditionOrdered(
        sampleColumns: List<String>,
        curtainSettings: CurtainSettings
    ): LinkedHashMap<String, List<String>> = withContext(Dispatchers.IO) {
        val samplesByCondition = mutableMapOf<String, MutableList<String>>()
        
        // Use the sample map from settings if available
        if (curtainSettings.sampleMap.isNotEmpty()) {
            curtainSettings.sampleMap.forEach { (sampleName, sampleInfo) ->
                val condition = sampleInfo["condition"] ?: "Unknown"
                if (curtainSettings.sampleVisible[sampleName] == true) {
                    samplesByCondition.getOrPut(condition) { mutableListOf() }.add(sampleName)
                }
            }
        } else {
            // Fallback: parse condition from sample names (assuming format like "condition.replicate")
            sampleColumns.forEach { sampleName ->
                val condition = if (sampleName.contains(".")) {
                    sampleName.substringBeforeLast(".")
                } else {
                    "Unknown"
                }
                samplesByCondition.getOrPut(condition) { mutableListOf() }.add(sampleName)
            }
        }
        
        // Order conditions based on conditionOrder
        val orderedResult = LinkedHashMap<String, List<String>>()
        
        // First add conditions in the specified order
        curtainSettings.conditionOrder.forEach { condition ->
            samplesByCondition[condition]?.let { samples ->
                orderedResult[condition] = samples
            }
        }
        
        // Then add any remaining conditions not in conditionOrder
        samplesByCondition.forEach { (condition, samples) ->
            if (!orderedResult.containsKey(condition)) {
                orderedResult[condition] = samples
            }
        }
        
        orderedResult
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
            contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.GONE
        }
    }

    private fun showContent() {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.GONE
            
            // Show chart controls - always show to include search filter, hide specific toggles when not needed
            val hasImputationData = viewModel.curtainSettings.value?.imputationMap?.isNotEmpty() == true
            val hasPeptideCountData = viewModel.curtainSettings.value?.peptideCountData?.isNotEmpty() == true
            
            // Always show chart controls to include search filter
            chartControlsLayout.visibility = View.VISIBLE
            
            // Show imputation and peptide count controls only when relevant
            imputationToggleLayout.visibility = if (hasImputationData) View.VISIBLE else View.GONE
            peptideCountToggleLayout.visibility = if (hasPeptideCountData) View.VISIBLE else View.GONE
        }
    }

    private fun showError(message: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            contentLayout.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = message
            noSelectionText.visibility = View.GONE
        }
    }

    private fun showNoSelection() {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            contentLayout.visibility = View.GONE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.VISIBLE
        }
    }
    
    private fun showLoadMoreProgress(show: Boolean) {
        binding.apply {
            loadMoreProgress.visibility = if (show) View.VISIBLE else View.GONE
            loadMoreButton.isEnabled = !show
            if (show) {
                loadMoreButton.text = "Loading..."
            }
        }
    }

    /**
     * Check if a protein is currently annotated in the volcano plot
     */
    fun checkIfProteinIsAnnotated(proteinId: String): Boolean {
        val curtainSettings = viewModel.curtainSettings.value ?: return false
        
        // Generate the title for this protein and check if it exists as a key
        val title = generateAnnotationTitle(proteinId)
        val isAnnotated = curtainSettings.textAnnotation.containsKey(title)
        
        android.util.Log.d("ProteinDetails", "=== CHECKING ANNOTATION FOR $proteinId ===")
        android.util.Log.d("ProteinDetails", "Generated title: '$title'")
        android.util.Log.d("ProteinDetails", "Total annotations: ${curtainSettings.textAnnotation.size}")
        android.util.Log.d("ProteinDetails", "All annotation keys: ${curtainSettings.textAnnotation.keys}")
        android.util.Log.d("ProteinDetails", "Key exists check: ${curtainSettings.textAnnotation.containsKey(title)}")
        android.util.Log.d("ProteinDetails", "Final result: $isAnnotated")
        
        return isAnnotated
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
        
        android.util.Log.d("ProteinDetails", "=== GENERATING TITLE FOR $proteinId ===")
        android.util.Log.d("ProteinDetails", "UniProt record found: ${uniprotRecord != null}")
        android.util.Log.d("ProteinDetails", "Raw gene names: '$geneNames'")
        
        val title = if (!geneNames.isNullOrEmpty() && geneNames != proteinId) {
            "${geneNames}(${proteinId})"
        } else {
            proteinId
        }
        
        android.util.Log.d("ProteinDetails", "Generated title: '$title'")
        return title
    }
    
    /**
     * Toggle annotation for a specific protein (add or remove)
     */
    fun toggleProteinAnnotation(proteinId: String, annotate: Boolean) {
        android.util.Log.d("ProteinDetails", "toggleProteinAnnotation called: proteinId=$proteinId, annotate=$annotate")
        android.util.Log.d("ProteinDetails", "Fragment lifecycle state: ${lifecycle.currentState}")
        android.util.Log.d("ProteinDetails", "View bound: ${_binding != null}")
        
        if (annotate) {
            // Add annotation directly to settings
            android.util.Log.d("ProteinDetails", "Adding annotation for $proteinId")
            showAddAnnotationDialog(proteinId)
        } else {
            // Remove annotation directly from settings
            android.util.Log.d("ProteinDetails", "Removing annotation for $proteinId")
            removeAnnotationFromSettings(proteinId)
            android.widget.Toast.makeText(
                requireContext(),
                "Annotation removed for $proteinId",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Show dialog to add annotation with custom text
     */
    private fun showAddAnnotationDialog(proteinId: String) {
        val curtainData = viewModel.curtainData.value ?: return
        
        // Get gene name for default text
        val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>
        val diffForm = curtainData.differentialForm
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        
        val dataPoint = processedData?.find { row ->
            val primaryId = row[idColumn]?.toString()
            primaryId == proteinId
        }
        
        val geneName = dataPoint?.get(geneColumn)?.toString() ?: proteinId
        val defaultText = if (geneName.isNotBlank() && geneName != proteinId) "$geneName ($proteinId)" else proteinId
        
        // Create input dialog
        val input = android.widget.EditText(requireContext())
        input.setText(defaultText)
        input.selectAll()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Annotation")
            .setMessage("Enter annotation text for $proteinId:")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val customText = input.text.toString().trim()
                if (customText.isNotEmpty()) {
                    addAnnotationToSettings(proteinId, customText)
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Annotation added for $proteinId",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Text cannot be empty",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Add annotation directly to curtain settings
     */
    private fun addAnnotationToSettings(proteinId: String, customText: String? = null) {
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        // Get differential data to find the data point
        val processedData = curtainData.dataMap["processedDifferentialData"] as? List<Map<String, Any>>
        if (processedData == null) {
            android.util.Log.e("ProteinDetails", "No processed differential data available for annotation")
            return
        }
        
        // Get differential form settings
        val diffForm = curtainData.differentialForm
        val fcColumn = diffForm.foldChange
        val sigColumn = diffForm.significant
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        
        // Find the data point for this protein ID
        val dataPoint = processedData.find { row ->
            val primaryId = row[idColumn]?.toString()
            primaryId == proteinId
        }
        
        if (dataPoint == null) {
            android.util.Log.w("ProteinDetails", "No data found for protein ID: $proteinId")
            return
        }
        
        // Create annotation data structure (matching Angular frontend pattern)
        // Use the same coordinate extraction logic as volcano plot
        val x = when (val fc = dataPoint[fcColumn]) {
            is Number -> {
                val doubleValue = fc.toDouble()
                if (doubleValue.isNaN()) 0.0 else doubleValue
            }
            is String -> fc.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        
        val y = when (val sig = dataPoint[sigColumn]) {
            is Number -> {
                val doubleValue = sig.toDouble()
                if (doubleValue.isNaN()) 0.0 else doubleValue
            }
            is String -> sig.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        // Generate unique annotation title exactly like Angular frontend
        val title = generateAnnotationTitle(proteinId)
        
        // Check if annotation already exists using the title as key
        val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
        if (existingAnnotations.containsKey(title)) {
            android.util.Log.d("ProteinDetails", "Annotation already exists for $proteinId with title: $title")
            return
        }
        
        // Use custom text for the annotation display if provided, otherwise use title
        val annotationText = customText ?: title
        
        // Create annotation data structure exactly matching Angular frontend
        val annotationData = mapOf(
            "primary_id" to proteinId,
            "title" to title,
            "data" to mapOf(
                "xref" to "x",
                "yref" to "y",
                "x" to x,
                "y" to y,
                "text" to "<b>$annotationText</b>",
                "showarrow" to true,
                "arrowhead" to 1,
                "arrowsize" to 1.0,
                "arrowwidth" to 1.0,
                "ax" to -20.0,
                "ay" to -20.0,
                "font" to mapOf(
                    "size" to 15,
                    "color" to "#000000",
                    "family" to "Arial, sans-serif"
                ),
                "showannotation" to true,
                "annotationID" to title,
                "draggable" to true
            )
        )
        
        // Update settings with new annotation
        existingAnnotations[title] = annotationData
        
        val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
        viewModel.updateCurtainSettings(updatedSettings)
        
        // Trigger volcano plot refresh to show the new annotation
        viewModel.refreshFromSearchUpdate()
        
        android.util.Log.d("ProteinDetails", "=== ANNOTATION CREATED ===")
        android.util.Log.d("ProteinDetails", "Protein ID: $proteinId")
        android.util.Log.d("ProteinDetails", "Generated title: '$title'")
        android.util.Log.d("ProteinDetails", "Annotation coordinates: ($x, $y)")
        android.util.Log.d("ProteinDetails", "Total annotations now: ${existingAnnotations.size}")
        android.util.Log.d("ProteinDetails", "All annotation keys after creation: ${existingAnnotations.keys}")
        android.util.Log.d("ProteinDetails", "Key '$title' exists in map: ${existingAnnotations.containsKey(title)}")
        android.util.Log.d("ProteinDetails", "Triggering volcano plot refresh")
    }
    
    /**
     * Remove annotation directly from curtain settings
     */
    private fun removeAnnotationFromSettings(proteinId: String) {
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        // Generate the title for this protein
        val title = generateAnnotationTitle(proteinId)
        val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
        
        // Remove annotation using the title as key
        val wasRemoved = existingAnnotations.remove(title) != null
        
        if (wasRemoved) {
            // Update settings
            val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
            viewModel.updateCurtainSettings(updatedSettings)
            
            // Trigger volcano plot refresh to show the annotation removal
            viewModel.refreshFromSearchUpdate()
            
            android.util.Log.d("ProteinDetails", "Removed annotation '$title' for $proteinId from settings")
        } else {
            android.util.Log.w("ProteinDetails", "No annotation found to remove for $proteinId with title '$title'")
        }
    }
    
    /**
     * Show dialog to edit annotation text for a protein
     */
    fun showEditAnnotationDialog(proteinId: String) {
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        // Generate the title for this protein and find the annotation
        val title = generateAnnotationTitle(proteinId)
        val existingAnnotations = curtainSettings.textAnnotation
        val annotationData = existingAnnotations[title]
        
        if (annotationData == null) {
            android.util.Log.w("ProteinDetails", "No annotation found for $proteinId with title '$title'")
            android.widget.Toast.makeText(
                requireContext(),
                "No annotation found for $proteinId",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        android.util.Log.d("ProteinDetails", "Found annotation for editing: title='$title'")
        
        // Extract current text from annotation
        val annotationMap = annotationData as? Map<*, *>
        val annotationDataInner = annotationMap?.get("data") as? Map<*, *>
        val currentText = annotationDataInner?.get("text")?.toString() ?: ""
        val cleanCurrentText = currentText.replace("<b>", "").replace("</b>", "")
        
        // Create input dialog
        val input = android.widget.EditText(requireContext())
        input.setText(cleanCurrentText)
        input.selectAll()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Annotation Text")
            .setMessage("Edit the text for $proteinId annotation:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    updateAnnotationText(proteinId, newText)
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Annotation text updated",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Text cannot be empty",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Update the text content of an existing annotation
     */
    private fun updateAnnotationText(proteinId: String, newText: String) {
        val curtainSettings = viewModel.curtainSettings.value ?: return
        
        // Generate the title for this protein and find the annotation
        val title = generateAnnotationTitle(proteinId)
        val existingAnnotations = curtainSettings.textAnnotation.toMutableMap()
        val annotationData = existingAnnotations[title]
        
        if (annotationData != null) {
            // Update the annotation text while keeping the same title/key
            val annotationMap = annotationData as? Map<*, *>
            val annotationDataInner = annotationMap?.get("data") as? Map<*, *>
            
            if (annotationDataInner != null) {
                val updatedDataInner = annotationDataInner.toMutableMap()
                updatedDataInner["text"] = "<b>$newText</b>"
                // Keep the same annotationID (which should match the title)
                updatedDataInner["annotationID"] = title
                
                val updatedAnnotationData = (annotationMap as Map<*, *>).toMutableMap()
                updatedAnnotationData["data"] = updatedDataInner
                // Keep the same title - only the display text changes
                
                // Update the annotation using the same key/title
                existingAnnotations[title] = updatedAnnotationData
                
                // Update settings
                val updatedSettings = curtainSettings.copy(textAnnotation = existingAnnotations)
                viewModel.updateCurtainSettings(updatedSettings)
                
                // Trigger volcano plot refresh
                viewModel.refreshFromSearchUpdate()
                
                android.util.Log.d("ProteinDetails", "Updated annotation text for '$title': '$newText'")
            }
        } else {
            android.util.Log.w("ProteinDetails", "No annotation found to update for $proteinId with title '$title'")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Data classes
    data class ProteinDetail(
        val proteinId: String,
        val geneName: String,
        val conditionDataList: List<ConditionData>
    )
    
    data class ConditionData(
        val condition: String,
        val sampleData: List<SampleData>
    )

    data class SampleData(
        val sampleName: String,
        val value: String
    )
    
    data class ImputationSettings(
        val isEnabled: Boolean,
        val imputationMap: Map<String, Any>,
        val peptideCountData: Map<String, Any>,
        val viewPeptideCount: Boolean,
        val chartType: ChartType
    )

    // Adapter classes
    class ProteinDetailAdapter(
        private val colorMapProvider: () -> Map<String, String>,
        private val imputationProvider: () -> ImputationSettings,
        private val fragment: ProteinDetailListTabFragment
    ) : RecyclerView.Adapter<ProteinDetailAdapter.ProteinDetailViewHolder>() {
        private var proteinDetails = listOf<ProteinDetail>()
        private var colorMap = mapOf<String, String>()

        fun submitList(details: List<ProteinDetail>) {
            proteinDetails = details
            notifyDataSetChanged()
        }
        
        fun updateColorMap(newColorMap: Map<String, String>) {
            colorMap = newColorMap
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProteinDetailViewHolder {
            val binding = ItemProteinDetailBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ProteinDetailViewHolder(binding, fragment)
        }

        override fun onBindViewHolder(holder: ProteinDetailViewHolder, position: Int) {
            val currentColorMap = colorMapProvider()
            val currentImputationSettings = imputationProvider()
            holder.bind(proteinDetails[position], currentColorMap, currentImputationSettings)
        }

        override fun getItemCount(): Int = proteinDetails.size

        class ProteinDetailViewHolder(
            private val binding: ItemProteinDetailBinding,
            private val fragment: ProteinDetailListTabFragment
        ) : RecyclerView.ViewHolder(binding.root) {

            private var currentChartType = ChartType.INDIVIDUAL_BAR
            private var currentErrorBarType = PlotlyChartGenerator.ErrorBarType.STANDARD_ERROR
            private var showIndividualPoints = false

            fun bind(proteinDetail: ProteinDetail, colorMap: Map<String, String>, imputationSettings: ImputationSettings) {
                binding.apply {
                    // Update main protein info
                    conditionTitle.text = "${proteinDetail.geneName} (${proteinDetail.proteinId})"
                    proteinId.text = "Protein ID: ${proteinDetail.proteinId}"
                    geneName.text = "Gene Name: ${proteinDetail.geneName}"

                    // Setup condition colors preview
                    setupConditionColorsPreview(proteinDetail, colorMap)

                    // Setup protein menu button
                    proteinMenuButton.setOnClickListener {
                        android.util.Log.d("ProteinDetails", "Menu button clicked for ${proteinDetail.proteinId}")
                        showProteinMenuDialog(proteinDetail, colorMap, imputationSettings)
                    }

                    // Setup chart WebView with current chart type
                    refreshChart(proteinDetail, colorMap, imputationSettings)

                    // Setup raw data button
                    viewRawDataButton.setOnClickListener {
                        showRawDataDialog(proteinDetail)
                    }
                }
            }

            private fun showProteinMenuDialog(proteinDetail: ProteinDetail, colorMap: Map<String, String>, imputationSettings: ImputationSettings) {
                android.util.Log.d("ProteinDetails", "showProteinMenuDialog called for ${proteinDetail.proteinId}")
                val isAnnotated = checkIfProteinIsAnnotated(proteinDetail.proteinId)
                android.util.Log.d("ProteinDetails", "Is protein annotated: $isAnnotated")
                val options = if (isAnnotated) {
                    arrayOf(
                        "Visualization Options",
                        "Edit Annotation Text",
                        "Remove Annotation"
                    )
                } else {
                    arrayOf(
                        "Visualization Options",
                        "Add Annotation"
                    )
                }
                
                androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                    .setTitle("${proteinDetail.geneName} (${proteinDetail.proteinId})")
                    .setItems(options) { _, which ->
                        android.util.Log.d("ProteinDetails", "Menu item $which selected for ${proteinDetail.proteinId}")
                        android.util.Log.d("ProteinDetails", "Available options: ${options.contentToString()}")
                        when (which) {
                            0 -> {
                                android.util.Log.d("ProteinDetails", "Visualization Options selected")
                                showProteinOptionsDialog(proteinDetail, colorMap, imputationSettings)
                            }
                            1 -> {
                                android.util.Log.d("ProteinDetails", "Menu option 1 selected (isAnnotated: $isAnnotated)")
                                if (isAnnotated) {
                                    // Edit annotation text
                                    showEditAnnotationDialog(proteinDetail.proteinId)
                                } else {
                                    // Add annotation directly without dialog
                                    android.util.Log.d("ProteinDetails", "Add annotation selected for ${proteinDetail.proteinId}")
                                    fragment.addAnnotationToSettings(proteinDetail.proteinId)
                                    android.widget.Toast.makeText(
                                        itemView.context,
                                        "Annotation added for ${proteinDetail.proteinId}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            2 -> {
                                // Remove annotation directly (only available when annotated)
                                android.util.Log.d("ProteinDetails", "Remove annotation selected for ${proteinDetail.proteinId}")
                                fragment.removeAnnotationFromSettings(proteinDetail.proteinId)
                                android.widget.Toast.makeText(
                                    itemView.context,
                                    "Annotation removed for ${proteinDetail.proteinId}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun showProteinOptionsDialog(proteinDetail: ProteinDetail, colorMap: Map<String, String>, imputationSettings: ImputationSettings) {
                val dialogView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.dialog_protein_options, null)
                
                // Setup dialog views
                val dialogTitle = dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle)
                val proteinInfo = dialogView.findViewById<android.widget.TextView>(R.id.proteinInfo)
                val chartTypeChips = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chartTypeChips)
                val errorBarSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.errorBarSection)
                val errorBarChips = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.errorBarChips)
                val pointsSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.pointsSection)
                val showPointsSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.showPointsSwitch)
                
                
                val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.cancelButton)
                val applyButton = dialogView.findViewById<android.widget.Button>(R.id.applyButton)
                
                // Set current values
                dialogTitle.text = "Visualization Options"
                proteinInfo.text = "${proteinDetail.geneName} (${proteinDetail.proteinId})"
                
                // Set current chart type
                when (currentChartType) {
                    ChartType.INDIVIDUAL_BAR -> chartTypeChips.check(R.id.chipIndividualBar)
                    ChartType.AVERAGE_BAR -> chartTypeChips.check(R.id.chipAverageBar)
                    ChartType.VIOLIN_PLOT -> chartTypeChips.check(R.id.chipViolin)
                }
                
                // Set current error bar type
                when (currentErrorBarType) {
                    PlotlyChartGenerator.ErrorBarType.STANDARD_ERROR -> errorBarChips.check(R.id.chipStandardError)
                    PlotlyChartGenerator.ErrorBarType.STANDARD_DEVIATION -> errorBarChips.check(R.id.chipStandardDev)
                }
                
                showPointsSwitch.isChecked = showIndividualPoints
                
                // Handle chart type changes to show/hide options
                fun updateOptionsVisibility() {
                    val selectedChartType = when (chartTypeChips.checkedChipId) {
                        R.id.chipAverageBar -> ChartType.AVERAGE_BAR
                        R.id.chipViolin -> ChartType.VIOLIN_PLOT
                        else -> ChartType.INDIVIDUAL_BAR
                    }
                    
                    errorBarSection.visibility = if (selectedChartType == ChartType.AVERAGE_BAR) android.view.View.VISIBLE else android.view.View.GONE
                    pointsSection.visibility = if (selectedChartType == ChartType.AVERAGE_BAR) android.view.View.VISIBLE else android.view.View.GONE
                }
                
                chartTypeChips.setOnCheckedStateChangeListener { _, _ ->
                    updateOptionsVisibility()
                }
                
                updateOptionsVisibility()
                
                // Create and show dialog
                val dialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                    .setView(dialogView)
                    .create()
                
                cancelButton.setOnClickListener {
                    dialog.dismiss()
                }
                
                applyButton.setOnClickListener {
                    // Apply changes
                    val newChartType = when (chartTypeChips.checkedChipId) {
                        R.id.chipAverageBar -> ChartType.AVERAGE_BAR
                        R.id.chipViolin -> ChartType.VIOLIN_PLOT
                        else -> ChartType.INDIVIDUAL_BAR
                    }
                    
                    val newErrorBarType = when (errorBarChips.checkedChipId) {
                        R.id.chipStandardDev -> PlotlyChartGenerator.ErrorBarType.STANDARD_DEVIATION
                        else -> PlotlyChartGenerator.ErrorBarType.STANDARD_ERROR
                    }
                    
                    val newShowPoints = showPointsSwitch.isChecked
                    
                    // Update settings and refresh chart
                    currentChartType = newChartType
                    currentErrorBarType = newErrorBarType
                    showIndividualPoints = newShowPoints
                    
                    refreshChart(proteinDetail, colorMap, imputationSettings)
                    dialog.dismiss()
                }
                
                dialog.show()
            }
            
            /**
             * Check if a protein is currently annotated in the volcano plot
             */
            private fun checkIfProteinIsAnnotated(proteinId: String): Boolean {
                return fragment.checkIfProteinIsAnnotated(proteinId)
            }
            
            /**
             * Toggle annotation for a specific protein
             */
            private fun toggleProteinAnnotation(proteinId: String, annotate: Boolean) {
                fragment.toggleProteinAnnotation(proteinId, annotate)
            }
            
            /**
             * Show dialog to edit annotation text
             */
            private fun showEditAnnotationDialog(proteinId: String) {
                fragment.showEditAnnotationDialog(proteinId)
            }
            

            private fun setupConditionColorsPreview(proteinDetail: ProteinDetail, colorMap: Map<String, String>) {
                val conditions = proteinDetail.conditionDataList.map { it.condition }.distinct()
                if (conditions.isNotEmpty()) {
                    binding.conditionColorsPreview.visibility = View.VISIBLE
                    
                    val colorChipsAdapter = ConditionColorChipsAdapter(conditions, colorMap) { condition ->
                        // Condition color management is now in the overflow menu
                        android.widget.Toast.makeText(
                            itemView.context,
                            "Use the overflow menu in the toolbar to manage condition colors",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    binding.conditionColorsRecyclerView.apply {
                        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                            context, 
                            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, 
                            false
                        )
                        adapter = colorChipsAdapter
                    }
                } else {
                    binding.conditionColorsPreview.visibility = View.GONE
                }
            }

            private fun refreshChart(proteinDetail: ProteinDetail, colorMap: Map<String, String>, imputationSettings: ImputationSettings) {
                setupChartWebView(binding.chartWebView, proteinDetail, colorMap, imputationSettings.copy(chartType = currentChartType))
            }
            
            private fun showRawDataDialog(proteinDetail: ProteinDetail) {
                val dialogView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.dialog_protein_raw_data, null)
                
                // Setup dialog views
                val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
                val proteinInfo = dialogView.findViewById<TextView>(R.id.proteinInfo)
                val conditionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.conditionsRecyclerView)
                val closeButton = dialogView.findViewById<Button>(R.id.closeButton)
                
                dialogTitle.text = "Raw Data Values"
                proteinInfo.text = "${proteinDetail.geneName} (${proteinDetail.proteinId})"
                
                // Setup RecyclerView in dialog
                val conditionAdapter = ConditionDataAdapter(null)
                conditionsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = conditionAdapter
                }
                conditionAdapter.submitList(proteinDetail.conditionDataList)
                
                // Create and show dialog
                val dialog = AlertDialog.Builder(itemView.context)
                    .setView(dialogView)
                    .create()
                
                closeButton.setOnClickListener {
                    dialog.dismiss()
                }
                
                dialog.show()
                
                // Set dialog size
                dialog.window?.setLayout(
                    (itemView.context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            private fun setupChartWebView(
                webView: WebView,
                proteinDetail: ProteinDetail,
                colorMap: Map<String, String>,
                imputationSettings: ImputationSettings
            ) {
                webView.apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    
                    // Generate chart HTML
                    try {
                        val chartHtml = PlotlyChartGenerator.generateBarChartHtml(
                            proteinId = proteinDetail.proteinId,
                            geneName = proteinDetail.geneName,
                            conditionDataList = proteinDetail.conditionDataList,
                            colorMap = colorMap,
                            peptideCountData = imputationSettings.peptideCountData as? Map<String, Map<String, String>>,
                            imputationMap = imputationSettings.imputationMap as? Map<String, Map<String, Boolean>>,
                            viewPeptideCount = imputationSettings.viewPeptideCount,
                            enableImputation = imputationSettings.isEnabled,
                            chartType = imputationSettings.chartType,
                            conditionColorService = fragment.conditionColorService,
                            errorBarType = currentErrorBarType,
                            showIndividualPoints = showIndividualPoints,
                            violinPointPosition = -1.2
                        )
                        
                        loadDataWithBaseURL("file:///android_asset/", chartHtml, "text/html", "UTF-8", null)
                    } catch (e: Exception) {
                        // Fallback: show error message
                        val errorHtml = "<html><body><div style='text-align:center; padding:20px; color:#666;'>Chart unavailable</div></body></html>"
                        loadDataWithBaseURL("file:///android_asset/", errorHtml, "text/html", "UTF-8", null)
                    }
                }
            }
        }
    }

    class ConditionDataAdapter(
        private val onColorClick: ((String, String) -> Unit)? = null
    ) : RecyclerView.Adapter<ConditionDataAdapter.ConditionDataViewHolder>() {
        private var conditionData = listOf<ConditionData>()

        fun submitList(data: List<ConditionData>) {
            conditionData = data
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionDataViewHolder {
            val binding = ItemConditionDataBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ConditionDataViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ConditionDataViewHolder, position: Int) {
            holder.bind(conditionData[position])
        }

        override fun getItemCount(): Int = conditionData.size

        inner class ConditionDataViewHolder(private val binding: ItemConditionDataBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(conditionData: ConditionData) {
                binding.apply {
                    conditionName.text = conditionData.condition

                    // Set condition color indicator
                    val conditionColor = "#808080" // Default gray color
                    try {
                        val colorInt = android.graphics.Color.parseColor(conditionColor)
                        conditionColorIndicator.setCardBackgroundColor(colorInt)
                    } catch (e: Exception) {
                        conditionColorIndicator.setCardBackgroundColor(android.graphics.Color.GRAY)
                    }

                    // Set color indicator click listener
                    conditionColorIndicator.setOnClickListener {
                        // Color picker functionality disabled in this context
                    }

                    // Setup sample data RecyclerView
                    val sampleAdapter = SampleDataAdapter()
                    sampleRecyclerView.apply {
                        layoutManager = LinearLayoutManager(itemView.context)
                        adapter = sampleAdapter
                    }
                    sampleAdapter.submitList(conditionData.sampleData)
                }
            }
        }
    }

    class SampleDataAdapter : RecyclerView.Adapter<SampleDataAdapter.SampleDataViewHolder>() {
        private var sampleData = listOf<SampleData>()

        fun submitList(data: List<SampleData>) {
            sampleData = data
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SampleDataViewHolder {
            val binding = ItemSampleDataBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SampleDataViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SampleDataViewHolder, position: Int) {
            holder.bind(sampleData[position])
        }

        override fun getItemCount(): Int = sampleData.size

        class SampleDataViewHolder(private val binding: ItemSampleDataBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(sampleData: SampleData) {
                binding.apply {
                    sampleName.text = sampleData.sampleName
                    sampleValue.text = sampleData.value
                }
            }
        }
    }

    // Simple adapter for condition color chips
    class ConditionColorChipsAdapter(
        private val conditions: List<String>,
        private val colorMap: Map<String, String>,
        private val onColorClick: (String) -> Unit
    ) : RecyclerView.Adapter<ConditionColorChipsAdapter.ColorChipViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorChipViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_condition_color_chip, parent, false)
            return ColorChipViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorChipViewHolder, position: Int) {
            val condition = conditions[position]
            val color = colorMap[condition] ?: "#fd7f6f"
            holder.bind(condition, color, onColorClick)
        }

        override fun getItemCount(): Int = conditions.size

        class ColorChipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorIndicator: androidx.cardview.widget.CardView = itemView.findViewById(R.id.colorIndicator)
            private val conditionName: android.widget.TextView = itemView.findViewById(R.id.conditionName)

            fun bind(condition: String, color: String, onColorClick: (String) -> Unit) {
                conditionName.text = condition
                
                try {
                    val colorInt = android.graphics.Color.parseColor(color)
                    colorIndicator.setCardBackgroundColor(colorInt)
                } catch (e: Exception) {
                    colorIndicator.setCardBackgroundColor(android.graphics.Color.GRAY)
                }
                
                itemView.setOnClickListener {
                    onColorClick(condition)
                }
            }
        }
    }
}