package info.proteo.curtain

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FilterListDetailFragment : Fragment() {

    private lateinit var adapter: FilterListItemAdapter
    private val allItems = mutableListOf<String>()
    private var filteredItems = mutableListOf<String>()

    // Use Navigation Component's safe args to receive arguments
    private val args: FilterListDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_filter_list_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filterName = args.filterName
        val filterData = args.filterData
        val filterCategory = args.filterCategory

        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = filterName
        toolbar.subtitle = filterCategory
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Parse the data string into individual items (splitting by new line)
        allItems.addAll(filterData.split("\n").filter { it.isNotBlank() })
        filteredItems.addAll(allItems)

        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvFilterItems)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        // Set up adapter with the filtered list
        adapter = FilterListItemAdapter(filteredItems)
        recyclerView.adapter = adapter

        // Display initial count
        updateItemCount(view)

        // Set up search functionality
        setupSearch(view)
    }

    private fun setupSearch(view: View) {
        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterItems(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // Not used
            }
        })
    }

    private fun filterItems(query: String) {
        filteredItems.clear()

        if (query.isEmpty()) {
            // If query is empty, show all items
            filteredItems.addAll(allItems)
        } else {
            // Filter items that contain the query (case insensitive)
            filteredItems.addAll(allItems.filter {
                it.contains(query, ignoreCase = true)
            })
        }

        // Update the adapter and item count
        adapter.notifyDataSetChanged()
        updateItemCount(requireView())
    }

    private fun updateItemCount(view: View) {
        val countView = view.findViewById<TextView>(R.id.tvItemCount)
        val totalCount = allItems.size
        val shownCount = filteredItems.size

        if (filteredItems.size == allItems.size) {
            countView.text = "$totalCount items"
        } else {
            countView.text = "$shownCount of $totalCount items"
        }
    }
}

// Adapter for the individual filter list items
class FilterListItemAdapter(
    private val items: List<String>
) : RecyclerView.Adapter<FilterListItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_entry, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvItem: TextView = itemView.findViewById(R.id.tvFilterItem)

        fun bind(item: String) {
            tvItem.text = item
        }
    }
}
