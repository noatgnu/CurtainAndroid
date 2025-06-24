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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.AppData
import info.proteo.curtain.CurtainSettings
import info.proteo.curtain.databinding.FragmentProteinDetailListTabBinding
import info.proteo.curtain.databinding.ItemProteinDetailBinding
import info.proteo.curtain.databinding.ItemSampleDataBinding
import info.proteo.curtain.databinding.ItemConditionDataBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.dataframe.api.getColumn

class ProteinDetailListTabFragment : Fragment() {
    private var _binding: FragmentProteinDetailListTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var proteinDetailAdapter: ProteinDetailAdapter

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

    private fun setupRecyclerView() {
        proteinDetailAdapter = ProteinDetailAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = proteinDetailAdapter
        }
    }

    private fun loadProteinDetails() {
        val curtainData = viewModel.curtainData.value ?: return
        val curtainSettings = viewModel.curtainSettings.value ?: return

        showLoading(true)

        try {
            // Check if there are any selected proteins
            val selectedProteins = getSelectedProteins(curtainData)
            
            if (selectedProteins.isEmpty()) {
                showNoSelection()
                return
            }

            val proteinDetails = processProteinDetails(curtainData, curtainSettings, selectedProteins)
            proteinDetailAdapter.submitList(proteinDetails)
            
            showContent()
        } catch (e: Exception) {
            Log.e("ProteinDetailList", "Error loading protein details: ${e.message}", e)
            showError("Error loading protein details: ${e.message}")
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

    private fun processProteinDetails(
        curtainData: AppData,
        curtainSettings: CurtainSettings,
        selectedProteins: List<String>
    ): List<ProteinDetail> {
        val details = mutableListOf<ProteinDetail>()
        
        // Get raw data
        val rawData = curtainData.raw.df
        val primaryIdColumn = curtainData.rawForm.primaryIDs
        val sampleColumns = curtainData.rawForm.samples
        
        // Group samples by condition and order by conditionOrder
        val samplesByCondition = groupSamplesByConditionOrdered(sampleColumns, curtainSettings)

        // Process each selected protein
        selectedProteins.forEach { proteinId ->
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
                        return@forEach
                    }
                }
            }
        }
        
        return details.sortedBy { it.proteinId }
    }

    private fun groupSamplesByConditionOrdered(
        sampleColumns: List<String>,
        curtainSettings: CurtainSettings
    ): LinkedHashMap<String, List<String>> {
        val samplesByCondition = mutableMapOf<String, MutableList<String>>()
        
        // Use the sample map from settings if available
        if (curtainSettings.sampleMap.isNotEmpty()) {
            curtainSettings.sampleMap.forEach { (sampleName, sampleInfo) ->
                val condition = sampleInfo["condition"] ?: "Unknown"
                if (curtainSettings.sampleVisible.get(sampleName) == true) {
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
        
        return orderedResult
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.GONE
        }
    }

    private fun showContent() {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            recyclerView.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = message
            noSelectionText.visibility = View.GONE
        }
    }

    private fun showNoSelection() {
        binding.apply {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            recyclerView.visibility = View.GONE
            errorText.visibility = View.GONE
            noSelectionText.visibility = View.VISIBLE
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
    class ProteinDetailAdapter : RecyclerView.Adapter<ProteinDetailAdapter.ProteinDetailViewHolder>() {
        private var proteinDetails = listOf<ProteinDetail>()

        fun submitList(details: List<ProteinDetail>) {
            proteinDetails = details
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProteinDetailViewHolder {
            val binding = ItemProteinDetailBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ProteinDetailViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ProteinDetailViewHolder, position: Int) {
            holder.bind(proteinDetails[position])
        }

        override fun getItemCount(): Int = proteinDetails.size

        class ProteinDetailViewHolder(private val binding: ItemProteinDetailBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(proteinDetail: ProteinDetail) {
                binding.apply {
                    // Update main protein info - no longer showing single condition
                    conditionTitle.text = "${proteinDetail.geneName} (${proteinDetail.proteinId})"
                    proteinId.text = "Protein ID: ${proteinDetail.proteinId}"
                    geneName.text = "Gene Name: ${proteinDetail.geneName}"

                    // Setup conditions RecyclerView to show all conditions for this protein
                    val conditionAdapter = ConditionDataAdapter()
                    sampleRecyclerView.apply {
                        layoutManager = LinearLayoutManager(itemView.context)
                        adapter = conditionAdapter
                    }
                    conditionAdapter.submitList(proteinDetail.conditionDataList)
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