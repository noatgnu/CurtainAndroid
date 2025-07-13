package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.data.models.SearchList
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.databinding.DialogBulkSearchListActionsBinding
import info.proteo.curtain.databinding.ItemBulkSearchListBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BulkSearchListActionsDialog : DialogFragment() {

    private var _binding: DialogBulkSearchListActionsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var searchService: SearchService

    private lateinit var bulkListAdapter: BulkSearchListAdapter
    private var searchLists = listOf<SearchList>()
    private var selectedListIds = mutableSetOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBulkSearchListActionsBinding.inflate(layoutInflater)
        
        setupViews()
        loadSearchLists()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bulk Actions")
            .setView(binding.root)
            .setPositiveButton("Apply") { _, _ ->
                performBulkActions()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Return null since we're using AlertDialog
        return null
    }

    private fun setupViews() {
        setupRecyclerView()
        setupActionButtons()
    }

    private fun setupRecyclerView() {
        bulkListAdapter = BulkSearchListAdapter { listId, isSelected ->
            if (isSelected) {
                selectedListIds.add(listId)
            } else {
                selectedListIds.remove(listId)
            }
            updateActionButtonStates()
        }
        
        binding.searchListsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bulkListAdapter
        }
    }

    private fun setupActionButtons() {
        binding.selectAllButton.setOnClickListener {
            selectedListIds.clear()
            selectedListIds.addAll(searchLists.map { it.id })
            bulkListAdapter.updateSelections(selectedListIds)
            updateActionButtonStates()
        }
        
        binding.selectNoneButton.setOnClickListener {
            selectedListIds.clear()
            bulkListAdapter.updateSelections(selectedListIds)
            updateActionButtonStates()
        }
        
        binding.deleteSelectedButton.setOnClickListener {
            showDeleteConfirmation()
        }
        
        binding.enableAllFiltersButton.setOnClickListener {
            enableSelectedFilters()
        }
        
        binding.disableAllFiltersButton.setOnClickListener {
            disableSelectedFilters()
        }
    }

    private fun loadSearchLists() {
        lifecycleScope.launch {
            searchService.searchSession.collect { session ->
                searchLists = session.searchLists
                bulkListAdapter.submitList(searchLists)
                
                binding.emptyStateText.visibility = 
                    if (searchLists.isEmpty()) View.VISIBLE else View.GONE
                    
                updateActionButtonStates()
            }
        }
    }

    private fun updateActionButtonStates() {
        val hasSelection = selectedListIds.isNotEmpty()
        val hasLists = searchLists.isNotEmpty()
        
        binding.deleteSelectedButton.isEnabled = hasSelection
        binding.enableAllFiltersButton.isEnabled = hasSelection
        binding.disableAllFiltersButton.isEnabled = hasSelection
        binding.selectAllButton.isEnabled = hasLists
        
        binding.selectionCountText.text = "${selectedListIds.size} of ${searchLists.size} selected"
    }

    private fun showDeleteConfirmation() {
        if (selectedListIds.isEmpty()) return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Search Lists")
            .setMessage("Are you sure you want to delete ${selectedListIds.size} search list(s)? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedLists()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedLists() {
        selectedListIds.forEach { listId ->
            searchService.removeSearchList(listId)
        }
        
        val count = selectedListIds.size
        selectedListIds.clear()
        
        view?.let {
            Snackbar.make(it, "Deleted $count search list(s)", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun enableSelectedFilters() {
        selectedListIds.forEach { listId ->
            searchService.toggleSearchListFilter(listId)
        }
        
        view?.let {
            Snackbar.make(it, "Enabled ${selectedListIds.size} filter(s)", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun disableSelectedFilters() {
        selectedListIds.forEach { listId ->
            if (searchService.isSearchListFilterActive(listId)) {
                searchService.toggleSearchListFilter(listId)
            }
        }
        
        view?.let {
            Snackbar.make(it, "Disabled ${selectedListIds.size} filter(s)", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun performBulkActions() {
        // Actions are performed immediately when buttons are clicked
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BulkSearchListActionsDialog {
            return BulkSearchListActionsDialog()
        }
    }
}

class BulkSearchListAdapter(
    private val onSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<BulkSearchListAdapter.BulkSearchListViewHolder>() {

    private var searchLists = listOf<SearchList>()
    private var selectedIds = setOf<String>()

    fun submitList(lists: List<SearchList>) {
        searchLists = lists
        notifyDataSetChanged()
    }
    
    fun updateSelections(newSelectedIds: Set<String>) {
        selectedIds = newSelectedIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BulkSearchListViewHolder {
        val binding = ItemBulkSearchListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BulkSearchListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BulkSearchListViewHolder, position: Int) {
        holder.bind(searchLists[position])
    }

    override fun getItemCount(): Int = searchLists.size

    inner class BulkSearchListViewHolder(
        private val binding: ItemBulkSearchListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(searchList: SearchList) {
            binding.apply {
                listNameText.text = searchList.name
                proteinCountText.text = "${searchList.proteinIds.size} proteins"
                
                // Set color indicator
                try {
                    val color = android.graphics.Color.parseColor(searchList.color)
                    colorIndicator.setBackgroundColor(color)
                } catch (e: Exception) {
                    colorIndicator.setBackgroundColor(android.graphics.Color.GRAY)
                }
                
                // Set selection state
                selectionCheckbox.isChecked = searchList.id in selectedIds
                
                // Handle selection changes
                selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(searchList.id, isChecked)
                }
                
                // Make whole item clickable to toggle selection
                root.setOnClickListener {
                    selectionCheckbox.isChecked = !selectionCheckbox.isChecked
                }
            }
        }
    }
}