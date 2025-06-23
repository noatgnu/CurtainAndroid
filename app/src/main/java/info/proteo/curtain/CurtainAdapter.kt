package info.proteo.curtain

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DecimalFormat

class CurtainAdapter(
    private val onItemClick: (CurtainEntity) -> Unit,
    private val onRedownloadClick: (CurtainEntity) -> Unit
) : ListAdapter<CurtainEntity, CurtainAdapter.CurtainViewHolder>(CurtainDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurtainViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_curtain, parent, false)
        return CurtainViewHolder(view, onItemClick, onRedownloadClick)
    }

    override fun onBindViewHolder(holder: CurtainViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CurtainViewHolder(
        itemView: View,
        private val onItemClick: (CurtainEntity) -> Unit,
        private val onRedownloadClick: (CurtainEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvLinkId: TextView = itemView.findViewById(R.id.tvLinkId)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val btnRedownload: ImageButton = itemView.findViewById(R.id.btnRedownload)

        fun bind(curtain: CurtainEntity) {
            tvLinkId.text = curtain.linkId
            tvDescription.text = curtain.description
            tvType.text = curtain.curtainType

            // Display file size if file exists
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

            btnRedownload.setOnClickListener {
                onRedownloadClick(curtain)
            }
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
