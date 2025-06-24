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
import org.jetbrains.kotlinx.dataframe.api.getColumn

class ProteinDetailListTabFragment : Fragment() {
    private var _binding: FragmentProteinDetailListTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var proteinDetailAdapter: ProteinDetailAdapter
    
    // Pagination constants and state
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_PAGE_SIZE = 10
    }
    
    private var currentPage = 0
    private var totalProteins = 0
    private var isLoading = false
    private var hasMoreData = true
    private var allSelectedProteins = listOf<String>()
    private val loadedProteinDetails = mutableListOf<ProteinDetail>()

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
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isDataLoaded.collect { isDataLoaded ->
                    if (isDataLoaded && viewModel.curtainData.value != null) {
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

    private fun setupRecyclerView() {
        proteinDetailAdapter = ProteinDetailAdapter { viewModel.curtainSettings.value?.colorMap ?: mapOf() }
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
    
    private fun updatePaginationInfo() {
        val loadedCount = loadedProteinDetails.size
        val totalCount = allSelectedProteins.size
        
        Log.d("ProteinDetailList", "Pagination: $loadedCount/$totalCount proteins loaded, hasMore: $hasMoreData")
        
        // Update pagination info text
        binding.paginationInfo.apply {
            text = "Showing $loadedCount of $totalCount proteins"
            visibility = if (totalCount > 0) View.VISIBLE else View.GONE
        }
        
        // Update load more button visibility
        binding.loadMoreLayout.visibility = if (hasMoreData && totalCount > 0) View.VISIBLE else View.GONE
        binding.loadMoreButton.text = if (hasMoreData) {
            "Load More Proteins (${totalCount - loadedCount} remaining)"
        } else {
            "All proteins loaded"
        }
        binding.loadMoreButton.isEnabled = hasMoreData
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
                    totalProteins = selectedProteins.size
                    
                    // Update loading message
                    withContext(Dispatchers.Main) {
                        binding.loadingText.text = "Loading initial ${INITIAL_PAGE_SIZE} of ${totalProteins} proteins..."
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
                    hasMoreData = allSelectedProteins.size > INITIAL_PAGE_SIZE
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
        val selectedProteins = mutableListOf<String>()
        
        // Get selected proteins from selectedMap
        curtainData.selectedMap.forEach { (proteinId, selections) ->
            selections.forEach { (selectionName, isSelected) ->
                if (isSelected) {
                    selectedProteins.add(proteinId)
                }
            }
        }
        
        return selectedProteins.distinct()
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

    // Adapter classes
    class ProteinDetailAdapter(private val colorMapProvider: () -> Map<String, String>) : RecyclerView.Adapter<ProteinDetailAdapter.ProteinDetailViewHolder>() {
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
            return ProteinDetailViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ProteinDetailViewHolder, position: Int) {
            val currentColorMap = colorMapProvider()
            holder.bind(proteinDetails[position], currentColorMap)
        }

        override fun getItemCount(): Int = proteinDetails.size

        class ProteinDetailViewHolder(private val binding: ItemProteinDetailBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(proteinDetail: ProteinDetail, colorMap: Map<String, String>) {
                binding.apply {
                    // Update main protein info
                    conditionTitle.text = "${proteinDetail.geneName} (${proteinDetail.proteinId})"
                    proteinId.text = "Protein ID: ${proteinDetail.proteinId}"
                    geneName.text = "Gene Name: ${proteinDetail.geneName}"

                    // Setup chart WebView
                    setupChartWebView(chartWebView, proteinDetail, colorMap)

                    // Setup raw data button
                    viewRawDataButton.setOnClickListener {
                        showRawDataDialog(proteinDetail)
                    }
                }
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
                val conditionAdapter = ConditionDataAdapter()
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
                colorMap: Map<String, String>
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
                            colorMap = colorMap
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

    class ConditionDataAdapter : RecyclerView.Adapter<ConditionDataAdapter.ConditionDataViewHolder>() {
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

        class ConditionDataViewHolder(private val binding: ItemConditionDataBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(conditionData: ConditionData) {
                binding.apply {
                    conditionName.text = conditionData.condition

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
}