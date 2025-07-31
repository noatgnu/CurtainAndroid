package info.proteo.curtain.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.R
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import java.io.File
import java.text.DecimalFormat

class CurtainAdapter(
    private val onItemClick: (CurtainEntity) -> Unit,
    private val onRedownloadClick: (CurtainEntity) -> Unit,
    private val onEditDescription: (CurtainEntity) -> Unit = {},
    private val onDelete: (CurtainEntity) -> Unit = {},
    private val onTogglePin: (CurtainEntity) -> Unit = {}
) : ListAdapter<CurtainEntity, CurtainAdapter.CurtainViewHolder>(CurtainDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurtainViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_curtain, parent, false)
        return CurtainViewHolder(view, onItemClick, onRedownloadClick, onEditDescription, onDelete, onTogglePin)
    }

    override fun onBindViewHolder(holder: CurtainViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CurtainViewHolder(
        itemView: View,
        private val onItemClick: (CurtainEntity) -> Unit,
        private val onRedownloadClick: (CurtainEntity) -> Unit,
        private val onEditDescription: (CurtainEntity) -> Unit,
        private val onDelete: (CurtainEntity) -> Unit,
        private val onTogglePin: (CurtainEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvLinkId: TextView = itemView.findViewById(R.id.tvLinkId)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val ivMenuIndicator: ImageView = itemView.findViewById(R.id.ivMenuIndicator)
        private val ivPinIndicator: ImageView = itemView.findViewById(R.id.ivPinIndicator)

        fun bind(curtain: CurtainEntity) {
            tvLinkId.text = curtain.linkId
            
            // Handle description visibility - show if not empty, hide if empty
            if (!curtain.description.isNullOrBlank()) {
                tvDescription.text = curtain.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }
            
            tvType.text = curtain.curtainType

            // Show/hide pin indicator based on pin status
            ivPinIndicator.visibility = if (curtain.isPinned) View.VISIBLE else View.GONE

            // Display file size
            curtain.file?.let { filePath ->
                val file = File(filePath)
                if (file.exists()) {
                    val sizeString = formatFileSize(file.length())
                    tvFileSize.text = sizeString
                    tvFileSize.visibility = View.VISIBLE
                } else {
                    tvFileSize.visibility = View.GONE
                }
            } ?: run {
                tvFileSize.visibility = View.GONE
            }

            // Set click listeners
            itemView.setOnClickListener {
                onItemClick(curtain)
            }
            
            // Set long click listener for context menu
            itemView.setOnLongClickListener {
                showContextMenu(it, curtain)
                true
            }

            
            // Also allow clicking the menu indicator to show context menu
            ivMenuIndicator.setOnClickListener {
                showContextMenu(it, curtain)
            }
        }

        private fun showContextMenu(view: View, curtain: CurtainEntity) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.curtain_context_menu, popup.menu)
            
            // Update pin menu item based on current pin status
            val pinMenuItem = popup.menu.findItem(R.id.action_toggle_pin)
            if (curtain.isPinned) {
                pinMenuItem.title = "Unpin"
                pinMenuItem.setIcon(R.drawable.ic_favorite)
            } else {
                pinMenuItem.title = "Pin"
                pinMenuItem.setIcon(R.drawable.ic_favorite_border)
            }
            
            // Update download/resync menu item based on file status
            val downloadMenuItem = popup.menu.findItem(R.id.action_download_resync)
            val hasDownloadedFile = curtain.file?.let { filePath ->
                File(filePath).exists()
            } ?: false
            
            if (hasDownloadedFile) {
                downloadMenuItem.title = "Resync"
                downloadMenuItem.setIcon(R.drawable.ic_refresh)
            } else {
                downloadMenuItem.title = "Download"
                downloadMenuItem.setIcon(R.drawable.ic_download)
            }
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_toggle_pin -> {
                        onTogglePin(curtain)
                        true
                    }
                    R.id.action_download_resync -> {
                        onRedownloadClick(curtain)
                        true
                    }
                    R.id.action_edit_description -> {
                        onEditDescription(curtain)
                        true
                    }
                    R.id.action_delete -> {
                        onDelete(curtain)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }

    object CurtainDiffCallback : DiffUtil.ItemCallback<CurtainEntity>() {
        override fun areItemsTheSame(oldItem: CurtainEntity, newItem: CurtainEntity): Boolean {
            return oldItem.linkId == newItem.linkId
        }

        override fun areContentsTheSame(oldItem: CurtainEntity, newItem: CurtainEntity): Boolean {
            return oldItem == newItem
        }
    }
}
