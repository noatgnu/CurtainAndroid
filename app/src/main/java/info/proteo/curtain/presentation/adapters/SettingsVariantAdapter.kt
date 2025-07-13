package info.proteo.curtain.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.R
import info.proteo.curtain.data.models.SettingsCategory
import info.proteo.curtain.data.models.SettingsVariant
import info.proteo.curtain.databinding.ItemSettingsVariantBinding
import info.proteo.curtain.databinding.ItemSettingsVariantManagementBinding
import java.text.SimpleDateFormat
import java.util.*

class SettingsVariantAdapter(
    private val onVariantClick: (SettingsVariant) -> Unit,
    private val onFavoriteClick: (SettingsVariant) -> Unit,
    private val onMoreClick: (SettingsVariant) -> Unit,
    private val selectionMode: Boolean = false,
    private val onDuplicateClick: ((SettingsVariant) -> Unit)? = null,
    private val onExportClick: ((SettingsVariant) -> Unit)? = null,
    private val onDeleteClick: ((SettingsVariant) -> Unit)? = null
) : ListAdapter<SettingsVariant, RecyclerView.ViewHolder>(SettingsVariantDiffCallback()) {

    private val selectedItems = mutableSetOf<String>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    companion object {
        const val VIEW_TYPE_NORMAL = 0
        const val VIEW_TYPE_MANAGEMENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (selectionMode) VIEW_TYPE_MANAGEMENT else VIEW_TYPE_NORMAL
    }

    inner class SettingsVariantViewHolder(
        private val binding: ItemSettingsVariantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(variant: SettingsVariant) {
            binding.apply {
                variantName.text = variant.name
                
                // Description
                if (variant.description.isNullOrBlank()) {
                    variantDescription.visibility = View.GONE
                } else {
                    variantDescription.text = variant.description
                    variantDescription.visibility = View.VISIBLE
                }

                // Tags
                if (variant.tags.isEmpty()) {
                    tagsGroup.visibility = View.GONE
                } else {
                    tagsGroup.removeAllViews()
                    variant.tags.take(3).forEach { tag ->
                        val chip = com.google.android.material.chip.Chip(binding.root.context).apply {
                            text = tag
                            textSize = 10f
                            setChipBackgroundColorResource(R.color.chip_background)
                        }
                        tagsGroup.addView(chip)
                    }
                    tagsGroup.visibility = View.VISIBLE
                }

                // Default badge
                defaultBadge.visibility = if (variant.isDefault) View.VISIBLE else View.GONE

                // Created date
                createdDate.text = "Created ${dateFormatter.format(Date(variant.createdAt))}"

                // Size (estimated)
                val estimatedSize = calculateEstimatedSize(variant)
                variantSize.text = formatFileSize(estimatedSize)

                // Version
                variantVersion.text = "v${variant.version}"

                // Selection indicator for management mode
                if (selectionMode) {
                    selectionIndicator.visibility = if (selectedItems.contains(variant.id)) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                } else {
                    selectionIndicator.visibility = View.GONE
                }

                // Favorite state
                favoriteButton.setImageResource(
                    if (variant.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                )
                favoriteButton.alpha = if (variant.isFavorite) 1.0f else 0.7f

                // Click listeners
                root.setOnClickListener {
                    onVariantClick(variant)
                }

                favoriteButton.setOnClickListener {
                    onFavoriteClick(variant)
                }

                moreOptionsButton.setOnClickListener {
                    onMoreClick(variant)
                }
            }
        }

    }

    private fun calculateEstimatedSize(variant: SettingsVariant): Long {
        // Rough estimation based on data complexity
        var size = 1024L // Base size

        // Visual settings
        size += variant.visualSettings.colorPalettes.size * 100
        size += variant.visualSettings.customColors.size * 50
        if (variant.visualSettings.customTheme != null) size += 500

        // Analysis settings
        size += variant.analysisSettings.comparisonSettings.size * 200
        size += variant.analysisSettings.statisticalMethods.size * 100

        // Search settings
        size += variant.searchSettings.savedSearches.size * 300
        size += variant.searchSettings.customLists.size * 500
        size += variant.searchSettings.activeFilters.size * 100

        // Condition settings
        size += variant.conditionSettings.conditionColors.size * 50
        size += variant.conditionSettings.conditionOrder.size * 20

        // Plot settings
        size += variant.plotSettings.chartConfigs.size * 400
        size += variant.plotSettings.defaultLayouts.size * 200

        // App preferences - fixed overhead
        size += 500

        return size
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return when {
            kb < 1 -> "${bytes} B"
            kb < 1024 -> "${String.format("%.1f", kb)} KB"
            else -> "${String.format("%.1f", kb / 1024)} MB"
        }
    }

    inner class SettingsVariantManagementViewHolder(
        private val binding: ItemSettingsVariantManagementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(variant: SettingsVariant) {
            binding.apply {
                variantName.text = variant.name
                
                // Categories count
                val categoryCount = countIncludedCategories(variant)
                categoriesCount.text = "$categoryCount categories"
                
                // File size estimation
                val estimatedSize = calculateEstimatedSize(variant)
                variantSize.text = formatFileSize(estimatedSize)
                
                // Last used
                lastUsed.text = variant.lastUsed?.let { timestamp ->
                    val diffInDays = ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000)).toInt()
                    when {
                        diffInDays == 0 -> "Used today"
                        diffInDays == 1 -> "Used yesterday"
                        diffInDays < 7 -> "Used $diffInDays days ago"
                        else -> "Used ${dateFormatter.format(Date(timestamp))}"
                    }
                } ?: "Never used"
                
                // Default badge and favorite icon
                defaultBadge.visibility = if (variant.isDefault) View.VISIBLE else View.GONE
                favoriteIcon.visibility = if (variant.isFavorite) View.VISIBLE else View.GONE
                
                // Category chips
                showCategoryChips(variant)
                
                // Selection state
                selectionCheckbox.isChecked = selectedItems.contains(variant.id)
                
                // Click listeners
                root.setOnClickListener {
                    onVariantClick(variant)
                }
                
                selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedItems.add(variant.id)
                    } else {
                        selectedItems.remove(variant.id)
                    }
                }
                
                duplicateButton.setOnClickListener {
                    onDuplicateClick?.invoke(variant)
                }
                
                exportButton.setOnClickListener {
                    onExportClick?.invoke(variant)
                }
                
                deleteButton.setOnClickListener {
                    onDeleteClick?.invoke(variant)
                }
            }
        }
        
        private fun showCategoryChips(variant: SettingsVariant) {
            binding.categoryTags.removeAllViews()
            
            // Show chips based on what categories are included
            val categories = mutableListOf<String>()
            if (variant.visualSettings != null) categories.add("Visual")
            if (variant.analysisSettings != null) categories.add("Analysis")
            if (variant.searchSettings != null) categories.add("Search")
            if (variant.conditionSettings != null) categories.add("Conditions")
            if (variant.plotSettings != null) categories.add("Plots")
            if (variant.appPreferences != null) categories.add("Preferences")
            
            categories.forEach { category ->
                val chip = com.google.android.material.chip.Chip(binding.root.context).apply {
                    text = category
                    textSize = 9f
                    setChipBackgroundColorResource(R.color.chip_background)
                }
                binding.categoryTags.addView(chip)
            }
        }
        
        private fun countIncludedCategories(variant: SettingsVariant): Int {
            var count = 0
            if (variant.visualSettings != null) count++
            if (variant.analysisSettings != null) count++
            if (variant.searchSettings != null) count++
            if (variant.conditionSettings != null) count++
            if (variant.plotSettings != null) count++
            if (variant.appPreferences != null) count++
            return count
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MANAGEMENT -> {
                val binding = ItemSettingsVariantManagementBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SettingsVariantManagementViewHolder(binding)
            }
            else -> {
                val binding = ItemSettingsVariantBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SettingsVariantViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val variant = getItem(position)
        when (holder) {
            is SettingsVariantViewHolder -> holder.bind(variant)
            is SettingsVariantManagementViewHolder -> holder.bind(variant)
        }
    }

    fun updateSelectedItems(selectedIds: Set<String>) {
        selectedItems.clear()
        selectedItems.addAll(selectedIds)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<String> = selectedItems.toSet()

    class SettingsVariantDiffCallback : DiffUtil.ItemCallback<SettingsVariant>() {
        override fun areItemsTheSame(oldItem: SettingsVariant, newItem: SettingsVariant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SettingsVariant, newItem: SettingsVariant): Boolean {
            return oldItem == newItem
        }
    }
}