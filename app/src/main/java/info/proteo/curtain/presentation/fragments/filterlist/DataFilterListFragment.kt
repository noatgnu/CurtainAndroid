package info.proteo.curtain

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DataFilterListFragment : Fragment() {

    private val viewModel: DataFilterListViewModel by viewModels()
    private lateinit var filterListAdapter: DataFilterListAdapter

    // Track the current selected category
    private var currentCategory: String? = null
    private var allCategories: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_filter_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFilterListAdapter(view)

        setupMaterialDropdown(view)

        // Setup search functionality
        setupSearch(view)

        view.findViewById<Button>(R.id.btnSync).setOnClickListener {
            viewModel.syncDataFilterLists()
        }

        view.findViewById<ImageButton>(R.id.btnResync).setOnClickListener {
            viewModel.syncDataFilterLists()
        }

        observeViewModel(view)
    }

    private fun setupSearch(view: View) {
        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchFilterLists)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterListAdapter.filterList(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // Not used
            }
        })
    }

    private fun setupFilterListAdapter(view: View) {
        // Set up filter list adapter
        filterListAdapter = DataFilterListAdapter { filterList ->
            // Use Navigation Component to navigate to the detail fragment
            val directions = DataFilterListFragmentDirections
                .actionDataFilterListFragmentToFilterListDetailFragment(
                    filterName = filterList.name,
                    filterData = filterList.data,
                    filterCategory = filterList.category
                )

            // Find NavController and navigate
            findNavController().navigate(directions)
        }
        view.findViewById<RecyclerView>(R.id.rvFilterLists).adapter = filterListAdapter
    }

    private fun setupMaterialDropdown(view: View) {
        val categoryDropdown = view.findViewById<AutoCompleteTextView>(R.id.categoryDropdown)

        val dropdownAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf("All Categories")
        )

        categoryDropdown.setAdapter(dropdownAdapter)

        categoryDropdown.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position) as String

            // If "All Categories" is selected, pass null to show all
            val categoryFilter = if (selectedItem == "All Categories") null else selectedItem
            currentCategory = categoryFilter
            viewModel.filterByCategory(categoryFilter)
        }
    }

    private fun observeViewModel(view: View) {
        // Find UI components
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val emptyView = view.findViewById<View>(R.id.emptyStateLayout)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvFilterLists)
        val categoryLayout = view.findViewById<ConstraintLayout>(R.id.categoryLayout)
        val categoryDropdown = view.findViewById<AutoCompleteTextView>(R.id.categoryDropdown)

        // Sync progress UI components
        val syncProgressLayout = view.findViewById<LinearLayout>(R.id.syncProgressLayout)
        val syncProgressBar = view.findViewById<ProgressBar>(R.id.progressBarSync)
        val syncStatusText = view.findViewById<TextView>(R.id.tvSyncStatus)
        val syncDetailText = view.findViewById<TextView>(R.id.tvSyncDetail)

        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                progressBar.visibility = if (isLoading && !viewModel.isSyncing.value) View.VISIBLE else View.GONE
            }
        }

        // Observe error state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMsg ->
                if (!errorMsg.isNullOrBlank()) {
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Observe categories for dropdown
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collectLatest { categories ->
                // Update dropdown with categories
                val hasCategories = categories.isNotEmpty()
                categoryLayout.visibility = if (hasCategories) View.VISIBLE else View.GONE

                if (hasCategories) {
                    allCategories = categories

                    // Add "All" option at the beginning
                    val dropdownItems = mutableListOf("All Categories")
                    dropdownItems.addAll(categories)

                    // Update dropdown adapter
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        dropdownItems
                    )
                    categoryDropdown.setAdapter(adapter)

                    // Set selected item if we have a current category
                    if (currentCategory != null && categories.contains(currentCategory)) {
                        categoryDropdown.setText(currentCategory, false)
                    } else {
                        categoryDropdown.setText("All Categories", false)
                    }
                }
            }
        }

        // Observe filter lists
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterLists.collectLatest { filterLists ->
                filterListAdapter.submitList(filterLists)

                // Show empty state if no categories and no filter lists
                val isEmpty = filterLists.isEmpty() && viewModel.categories.value.isEmpty()
                emptyView.visibility = if (isEmpty && !viewModel.isSyncing.value) View.VISIBLE else View.GONE
                recyclerView.visibility = if (filterLists.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Observe sync state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSyncing.collectLatest { isSyncing ->
                syncProgressLayout.visibility = if (isSyncing) View.VISIBLE else View.GONE

                // Adjust constraints when sync progress is shown/hidden
                val params = recyclerView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.topToBottom = if (isSyncing) R.id.syncProgressLayout else R.id.categoryLayout
                recyclerView.layoutParams = params
            }
        }

        // Observe sync progress
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncProgress.collectLatest { progress ->
                syncProgressBar.progress = progress
            }
        }

        // Observe sync total
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncTotal.collectLatest { total ->
                syncProgressBar.max = total
            }
        }

        // Observe current sync category
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentSyncCategory.collectLatest { category ->
                if (category != null) {
                    syncDetailText.text = "Category ${viewModel.syncProgress.value + 1}/${viewModel.syncTotal.value}: $category"
                    syncStatusText.text = "Syncing category: $category"
                } else {
                    syncDetailText.text = ""
                    syncStatusText.text = "Syncing categories..."
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data on resume
        viewModel.loadDataFilterLists()
    }

    companion object {
        fun newInstance() = DataFilterListFragment()
    }
}
