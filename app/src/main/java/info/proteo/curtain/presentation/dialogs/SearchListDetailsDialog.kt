package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import info.proteo.curtain.data.models.SearchList
import info.proteo.curtain.data.models.SearchType
import info.proteo.curtain.databinding.DialogSearchListDetailsBinding
import info.proteo.curtain.databinding.ItemProteinIdBinding
import java.text.SimpleDateFormat
import java.util.*

class SearchListDetailsDialog : DialogFragment() {

    private var _binding: DialogSearchListDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var proteinAdapter: ProteinIdAdapter
    private var searchList: SearchList? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchListDetailsBinding.inflate(layoutInflater)
        
        searchList = arguments?.getSerializable(ARG_SEARCH_LIST) as? SearchList
        
        setupViews()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Search List Details")
            .setView(binding.root)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy All") { _, _ ->
                copyAllProteinsToClipboard()
            }
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
        val searchList = this.searchList ?: return
        
        // Set basic information
        binding.listNameText.text = searchList.name
        binding.proteinCountText.text = "${searchList.proteinIds.size} proteins"
        
        // Set search type
        val searchTypeText = when (searchList.searchType) {
            SearchType.PRIMARY_ID -> "Primary ID"
            SearchType.GENE_NAME -> "Gene Name"
            SearchType.ACCESSION_ID -> "Accession ID"
        }
        binding.searchTypeText.text = "Search Type: $searchTypeText"
        
        // Set creation date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        binding.createdDateText.text = "Created: ${dateFormat.format(Date(searchList.createdAt))}"
        
        // Set description if available
        if (searchList.description.isNullOrEmpty()) {
            binding.descriptionText.visibility = View.GONE
            binding.descriptionLabel.visibility = View.GONE
        } else {
            binding.descriptionText.text = searchList.description
            binding.descriptionText.visibility = View.VISIBLE
            binding.descriptionLabel.visibility = View.VISIBLE
        }
        
        // Set color indicator
        try {
            val color = android.graphics.Color.parseColor(searchList.color)
            binding.colorIndicator.setBackgroundColor(color)
            binding.colorValueText.text = searchList.color
        } catch (e: Exception) {
            binding.colorIndicator.setBackgroundColor(android.graphics.Color.GRAY)
            binding.colorValueText.text = "Invalid color"
        }
        
        // Setup protein list
        setupProteinList()
    }

    private fun setupProteinList() {
        val searchList = this.searchList ?: return
        
        proteinAdapter = ProteinIdAdapter(searchList.proteinIds) { proteinId ->
            copyProteinToClipboard(proteinId)
        }
        
        binding.proteinListRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = proteinAdapter
        }
        
        // Show empty state if no proteins
        if (searchList.proteinIds.isEmpty()) {
            binding.proteinListRecyclerView.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
        } else {
            binding.proteinListRecyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        }
    }

    private fun copyProteinToClipboard(proteinId: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Protein ID", proteinId)
        clipboard.setPrimaryClip(clip)
        
        // Show confirmation
        view?.let { 
            Snackbar.make(it, "Copied '$proteinId' to clipboard", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyAllProteinsToClipboard() {
        val searchList = this.searchList ?: return
        
        val allProteins = searchList.proteinIds.joinToString("\n")
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Protein IDs", allProteins)
        clipboard.setPrimaryClip(clip)
        
        // Show confirmation
        view?.let {
            Snackbar.make(it, "Copied ${searchList.proteinIds.size} protein IDs to clipboard", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SEARCH_LIST = "search_list"

        fun newInstance(searchList: SearchList): SearchListDetailsDialog {
            return SearchListDetailsDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_SEARCH_LIST, searchList)
                }
            }
        }
    }
}

class ProteinIdAdapter(
    private val proteinIds: List<String>,
    private val onProteinClick: (String) -> Unit
) : RecyclerView.Adapter<ProteinIdAdapter.ProteinViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProteinViewHolder {
        val binding = ItemProteinIdBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProteinViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProteinViewHolder, position: Int) {
        holder.bind(proteinIds[position], position + 1)
    }

    override fun getItemCount(): Int = proteinIds.size

    inner class ProteinViewHolder(
        private val binding: ItemProteinIdBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(proteinId: String, index: Int) {
            binding.proteinIdText.text = proteinId
            binding.indexText.text = "$index."
            
            binding.root.setOnClickListener {
                onProteinClick(proteinId)
            }
            
            // Add copy icon click
            binding.copyIcon.setOnClickListener {
                onProteinClick(proteinId)
            }
        }
    }
}