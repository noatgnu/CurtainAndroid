package info.proteo.curtain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
                        adapter.notifyDataSetChanged()
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
            }
        )
        recyclerView.adapter = adapter

        // Set up FAB click listener
        view.findViewById<FloatingActionButton>(R.id.fabAddCurtain).setOnClickListener {
            // TODO: Navigate to add new curtain screen
            Toast.makeText(
                requireContext(),
                "Add new curtain",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeViewModel() {
        val progressBar = requireView().findViewById<ProgressBar>(R.id.progressBar)
        val emptyStateView = requireView().findViewById<TextView>(R.id.tvEmptyState)
        val downloadProgressBar = requireView().findViewById<ProgressBar>(R.id.downloadProgressBar)
        val downloadProgressText = requireView().findViewById<TextView>(R.id.tvDownloadProgress)
        val downloadProgressContainer = requireView().findViewById<View>(R.id.downloadProgressContainer)

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe loading state
            viewModel.isLoading.collectLatest { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
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
            // Observe download state
            viewModel.isDownloading.collectLatest { isDownloading ->
                downloadProgressContainer?.visibility = if (isDownloading) View.VISIBLE else View.GONE
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

                // Show empty state if list is empty
                emptyStateView.visibility = if (curtains.isEmpty()) View.VISIBLE else View.GONE
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
