package info.proteo.curtain

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private val onCategorySelected: (String) -> Unit
) : ListAdapter<String, CategoryAdapter.ViewHolder>(CategoryDiffCallback) {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view, onCategorySelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.bind(category, position == selectedPosition)

        holder.itemView.setOnClickListener {
            val previousSelectedPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            // Update previous selected item
            if (previousSelectedPosition >= 0) {
                notifyItemChanged(previousSelectedPosition)
            }

            // Update newly selected item
            notifyItemChanged(selectedPosition)

            // Call listener
            onCategorySelected(category)
        }
    }

    fun selectCategory(category: String?) {
        val position = currentList.indexOf(category)
        if (position != selectedPosition && position >= 0) {
            val previousSelected = selectedPosition
            selectedPosition = position

            if (previousSelected >= 0) {
                notifyItemChanged(previousSelected)
            }

            notifyItemChanged(selectedPosition)
        }
    }

    class ViewHolder(
        itemView: View,
        private val onCategorySelected: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        fun bind(category: String, isSelected: Boolean) {
            categoryName.text = category

            // Update selection state
            if (isSelected) {
                cardView.setCardBackgroundColor(cardView.context.getColor(R.color.purple_500))
                categoryName.setTextColor(cardView.context.getColor(android.R.color.white))
            } else {
                cardView.setCardBackgroundColor(cardView.context.getColor(androidx.appcompat.R.color.material_grey_100))
                categoryName.setTextColor(cardView.context.getColor(androidx.appcompat.R.color.primary_text_default_material_light))
            }
        }
    }

    object CategoryDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
