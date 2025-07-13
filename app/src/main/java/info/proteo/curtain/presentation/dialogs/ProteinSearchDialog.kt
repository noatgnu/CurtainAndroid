package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
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
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.databinding.DialogProteinSearchBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProteinSearchDialog : DialogFragment() {

    private var _binding: DialogProteinSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    private lateinit var searchService: SearchService
    
    private lateinit var suggestionsAdapter: TypeaheadSuggestionsAdapter
    private var searchJob: Job? = null
    
    companion object {
        fun newInstance(): ProteinSearchDialog {
            return ProteinSearchDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogProteinSearchBinding.inflate(layoutInflater)
        
        // Initialize SearchService with current curtain data
        viewModel.curtainData.value?.let { curtainData ->
            searchService!!.restoreSearchListsFromCurtainData(curtainData)
        }
        
        setupUI()
        setupTypeaheadSearch()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Protein Search")
            .setView(binding.root)
            .setPositiveButton("Create Search List") { _, _ ->
                createSearchList()
            }
            .setNegativeButton("Cancel", null)
            .create()
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
        
        // Setup suggestions RecyclerView
        suggestionsAdapter = TypeaheadSuggestionsAdapter { suggestion ->
            addSelectedTerm(suggestion.text)
        }
        binding.suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionsAdapter
        }
        
        // Setup selected terms display
        updateSelectedTermsDisplay()
    }
    
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
                val suggestions = searchService.performTypeaheadSearch(
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
    
    private val selectedTerms = mutableSetOf<String>()
    
    private fun addSelectedTerm(term: String) {
        selectedTerms.add(term)
        updateSelectedTermsDisplay()
        binding.searchQueryEdit.setText("")
    }
    
    private fun updateSelectedTermsDisplay() {
        if (selectedTerms.isEmpty()) {
            binding.selectedTermsText.text = "No terms selected"
            binding.selectedTermsText.visibility = View.VISIBLE
            binding.clearTermsButton.visibility = View.GONE
        } else {
            binding.selectedTermsText.text = "Selected: ${selectedTerms.joinToString(", ")}"
            binding.selectedTermsText.visibility = View.VISIBLE
            binding.clearTermsButton.visibility = View.VISIBLE
        }
        
        binding.clearTermsButton.setOnClickListener {
            selectedTerms.clear()
            updateSelectedTermsDisplay()
        }
    }
    
    private fun createSearchList() {
        val searchListName = binding.searchListNameEdit.text?.toString()?.trim()
        
        if (searchListName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please enter a search list name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedTerms.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one search term", Toast.LENGTH_SHORT).show()
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
                val request = BatchSearchRequest(
                    searchTerms = selectedTerms.toList(),
                    searchType = selectedSearchType,
                    listName = searchListName,
                    overwriteExisting = false
                )
                
                val (searchResults, statistics) = searchService.performBatchSearch(
                    request = request,
                    curtainData = curtainData
                )
                
                if (statistics.matchedProteins > 0) {
                    Toast.makeText(
                        requireContext(), 
                        "Created search list '$searchListName' with ${statistics.matchedProteins} proteins",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(), 
                        "No proteins found matching the search criteria",
                        Toast.LENGTH_LONG
                    ).show()
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