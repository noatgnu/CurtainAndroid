package info.proteo.curtain

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.util.Locale

class DataFilterListAdapter(
    private val onItemClick: (DataFilterListEntity) -> Unit
) : ListAdapter<DataFilterListEntity, DataFilterListAdapter.ViewHolder>(DiffCallback) {

    private var originalList: List<DataFilterListEntity> = listOf()
    private var currentSearchQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_list, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submitList(list: List<DataFilterListEntity>, searchQuery: String = "") {
        originalList = list
        currentSearchQuery = searchQuery

        if (searchQuery.isEmpty()) {
            super.submitList(list)
        } else {
            filterList(searchQuery)
        }
    }

    fun filterList(query: String) {
        currentSearchQuery = query

        if (query.isEmpty()) {
            super.submitList(originalList)
            return
        }

        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        val filteredList = originalList.filter { entity ->
            entity.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    entity.category.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
        }

        super.submitList(filteredList)
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (DataFilterListEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.tvFilterListName)
        private val categoryTextView: TextView = itemView.findViewById(R.id.tvFilterListCategory)
        private val defaultChip: Chip = itemView.findViewById(R.id.chipDefault)

        fun bind(item: DataFilterListEntity) {
            nameTextView.text = item.name
            categoryTextView.text = item.category

            defaultChip.visibility = if (item.isDefault) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DataFilterListEntity>() {
        override fun areItemsTheSame(oldItem: DataFilterListEntity, newItem: DataFilterListEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DataFilterListEntity, newItem: DataFilterListEntity): Boolean {
            return oldItem == newItem
        }
    }
}
