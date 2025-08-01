package info.proteo.curtain.presentation.fragments.curtain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import info.proteo.curtain.R
import info.proteo.curtain.presentation.adapters.CurtainAdapter
import info.proteo.curtain.CurtainViewModel
import info.proteo.curtain.presentation.dialogs.AddCurtainDialog
import info.proteo.curtain.presentation.dialogs.EditDescriptionDialog
import info.proteo.curtain.presentation.fragments.curtain.CurtainListFragmentDirections
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CurtainListFragment : Fragment() {

    private val viewModel: CurtainViewModel by viewModels()
    private lateinit var adapter: CurtainAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_curtain_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        observeViewModel()
    }

    private fun setupViews(view: View) {
        // Initialize RecyclerView and adapter
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvCurtains)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val downloadProgressContainer = view.findViewById<View>(R.id.downloadProgressContainer)
        val downloadProgressBar = view.findViewById<ProgressBar>(R.id.downloadProgressBar)
        val downloadProgressText = view.findViewById<TextView>(R.id.tvDownloadProgress)
        val downloadSpeedText = view.findViewById<TextView>(R.id.tvDownloadSpeed)
        val cancelDownloadButton = view.findViewById<Button>(R.id.btnCancelDownload)
        val contentContainer = view.findViewById<LinearLayout>(R.id.contentContainer)
        val paginationInfo = view.findViewById<TextView>(R.id.paginationInfo)
        val loadMoreLayout = view.findViewById<LinearLayout>(R.id.loadMoreLayout)
        val loadMoreButton = view.findViewById<Button>(R.id.loadMoreButton)
        val loadMoreProgress = view.findViewById<ProgressBar>(R.id.loadMoreProgress)

        // Initialize the download progress views as invisible
        downloadProgressContainer?.visibility = View.GONE

        adapter = CurtainAdapter(
            onItemClick = { curtain ->
                // Navigate to the details fragment with curtain ID as argument
                curtain.file?.let { filePath ->
                    val action = CurtainListFragmentDirections.actionToCurtainDetailsFragment(curtainId = curtain.linkId)
                    findNavController().navigate(action)
                } ?: run {
                    // File path is null, notify user
                    Toast.makeText(
                        requireContext(),
                        "Curtain data not available. Please download first.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRedownloadClick = { curtain ->
                // Handle redownload button click - only download, no deserialization
                lifecycleScope.launch {
                    try {
                        // Show download progress indicators
                        downloadProgressContainer?.visibility = View.VISIBLE
                        downloadProgressBar?.progress = 0
                        downloadProgressText?.text = "0%"
                        downloadSpeedText?.text = "0 KB/s"

                        // Redownload curtain data (force download and delete old data)
                        viewModel.redownloadCurtainData(curtain)

                        // Hide progress indicators when done
                        downloadProgressContainer?.visibility = View.GONE

                        // Show success message
                        Toast.makeText(
                            requireContext(),
                            "Curtain data redownloaded successfully. Click the card to open it.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Refresh the list to show updated file size
                        // The adapter will be refreshed automatically via StateFlow
                    } catch (e: Exception) {
                        // Hide progress indicators on error
                        downloadProgressContainer?.visibility = View.GONE

                        // Show error message
                        Toast.makeText(
                            requireContext(),
                            "Error redownloading curtain: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onEditDescription = { curtain ->
                // Show edit description dialog
                val dialog = EditDescriptionDialog.newInstance(curtain) { newDescription ->
                    viewModel.updateCurtainDescription(curtain, newDescription)
                }
                dialog.show(parentFragmentManager, "EditDescriptionDialog")
            },
            onDelete = { curtain ->
                // Show confirmation dialog before deleting
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Curtain")
                    .setMessage("Are you sure you want to delete this curtain?\n\nID: ${curtain.linkId}\nDescription: ${curtain.description.ifBlank { "No description" }}")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteCurtain(curtain)
                        Toast.makeText(requireContext(), "Curtain deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onTogglePin = { curtain ->
                viewModel.togglePinStatus(curtain)
                val action = if (curtain.isPinned) "Unpinned" else "Pinned"
                Toast.makeText(requireContext(), "$action curtain", Toast.LENGTH_SHORT).show()
            }
        )
        
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@CurtainListFragment.adapter
            
            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()
                    
                    // Load more when user scrolls to near the end
                    if (viewModel.hasMoreCurtains() && !viewModel.isLoadingMore.value) {
                        if (visibleItemCount + pastVisibleItems >= totalItemCount - 2) {
                            viewModel.loadMoreCurtains()
                        }
                    }
                }
            })
        }
        
        // Set up load more button
        loadMoreButton?.setOnClickListener {
            viewModel.loadMoreCurtains()
        }

        // Set up cancel download button
        cancelDownloadButton?.setOnClickListener {
            viewModel.cancelDownload()
            Toast.makeText(requireContext(), "Download cancelled", Toast.LENGTH_SHORT).show()
        }

        // Set up FAB click listener
        view.findViewById<FloatingActionButton>(R.id.fabAddCurtain).setOnClickListener {
            val dialog = AddCurtainDialog.newInstance {
                // Refresh the curtain list when a new curtain is added
                viewModel.loadCurtains()
            }
            dialog.show(childFragmentManager, "AddCurtainDialog")
        }
    }

    private fun observeViewModel() {
        val progressBar = requireView().findViewById<ProgressBar>(R.id.progressBar)
        val emptyStateView = requireView().findViewById<TextView>(R.id.tvEmptyState)
        val downloadProgressBar = requireView().findViewById<ProgressBar>(R.id.downloadProgressBar)
        val downloadProgressText = requireView().findViewById<TextView>(R.id.tvDownloadProgress)
        val downloadSpeedText = requireView().findViewById<TextView>(R.id.tvDownloadSpeed)
        val downloadProgressContainer = requireView().findViewById<View>(R.id.downloadProgressContainer)
        val contentContainer = requireView().findViewById<LinearLayout>(R.id.contentContainer)
        val paginationInfo = requireView().findViewById<TextView>(R.id.paginationInfo)
        val loadMoreLayout = requireView().findViewById<LinearLayout>(R.id.loadMoreLayout)
        val loadMoreButton = requireView().findViewById<Button>(R.id.loadMoreButton)
        val loadMoreProgress = requireView().findViewById<ProgressBar>(R.id.loadMoreProgress)

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe loading state
            viewModel.isLoading.collectLatest { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                contentContainer?.visibility = if (isLoading) View.GONE else View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe download progress
            viewModel.downloadProgress.collectLatest { progress ->
                downloadProgressBar?.progress = progress
                downloadProgressText?.text = "$progress%"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe download speed
            viewModel.downloadSpeed.collectLatest { speed ->
                val speedText = if (speed > 0) {
                    if (speed >= 1024) {
                        String.format("%.1f MB/s", speed / 1024.0)
                    } else {
                        String.format("%.0f KB/s", speed)
                    }
                } else {
                    "0 KB/s"
                }
                downloadSpeedText?.text = speedText
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe download state
            viewModel.isDownloading.collectLatest { isDownloading ->
                downloadProgressContainer?.visibility = if (isDownloading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe load more state
            viewModel.isLoadingMore.collectLatest { isLoadingMore ->
                loadMoreProgress?.visibility = if (isLoadingMore) View.VISIBLE else View.GONE
                loadMoreButton?.isEnabled = !isLoadingMore
                loadMoreButton?.text = if (isLoadingMore) "Loading..." else "Load More Curtains"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe error state
            viewModel.error.collectLatest { error ->
                if (!error.isNullOrBlank()) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe curtains list
            viewModel.curtains.collectLatest { curtains ->
                adapter.submitList(curtains)

                // Show empty state if list is empty (when not loading)
                val isEmpty = curtains.isEmpty() && !viewModel.isLoading.value
                emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                
                // Update pagination info
                if (curtains.isNotEmpty()) {
                    paginationInfo?.apply {
                        text = viewModel.getPaginationInfo()
                        visibility = View.VISIBLE
                    }
                    
                    // Show/hide load more button
                    loadMoreLayout?.visibility = if (viewModel.hasMoreCurtains()) View.VISIBLE else View.GONE
                    
                    val remaining = viewModel.totalCurtains.value - curtains.size
                    loadMoreButton?.text = if (viewModel.hasMoreCurtains()) {
                        "Load More Curtains ($remaining remaining)"
                    } else {
                        "All curtains loaded"
                    }
                } else {
                    paginationInfo?.visibility = View.GONE
                    loadMoreLayout?.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when coming back to this screen
        viewModel.loadCurtains()
    }

    companion object {
        fun newInstance() = CurtainListFragment()
    }
}
