package info.proteo.curtain.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.R
import info.proteo.curtain.data.models.VolcanoPointDetails
import info.proteo.curtain.UniprotService
import java.text.DecimalFormat

class NearbyPointsAdapter(
    private val onPointClick: (VolcanoPointDetails) -> Unit = {},
    private val onCheckboxChanged: (VolcanoPointDetails, Boolean) -> Unit = { _, _ -> },
    private val uniprotService: UniprotService? = null
) : ListAdapter<Pair<VolcanoPointDetails, Double>, NearbyPointsAdapter.NearbyPointViewHolder>(NearbyPointDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NearbyPointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_point, parent, false)
        android.util.Log.d("NearbyPointsAdapter", "onCreateViewHolder called")
        return NearbyPointViewHolder(view, onPointClick, onCheckboxChanged, uniprotService)
    }

    override fun onBindViewHolder(holder: NearbyPointViewHolder, position: Int) {
        android.util.Log.d("NearbyPointsAdapter", "onBindViewHolder called for position $position")
        holder.bind(getItem(position))
    }

    class NearbyPointViewHolder(
        itemView: View,
        private val onPointClick: (VolcanoPointDetails) -> Unit,
        private val onCheckboxChanged: (VolcanoPointDetails, Boolean) -> Unit,
        private val uniprotService: UniprotService? = null
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val cbNearbyPoint: CheckBox = itemView.findViewById(R.id.cbNearbyPoint)
        private val tvNearbyGeneName: TextView = itemView.findViewById(R.id.tvNearbyGeneName)
        private val tvNearbyFoldChange: TextView = itemView.findViewById(R.id.tvNearbyFoldChange)
        private val tvNearbySignificance: TextView = itemView.findViewById(R.id.tvNearbySignificance)
        private val layoutNearbyTraceGroup: View = itemView.findViewById(R.id.layoutNearbyTraceGroup)
        private val tvNearbyTraceGroup: TextView = itemView.findViewById(R.id.tvNearbyTraceGroup)
        private val vNearbyTraceGroupColor: View = itemView.findViewById(R.id.vNearbyTraceGroupColor)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        
        private val decimalFormat = DecimalFormat("#.###")
        private val scientificFormat = DecimalFormat("0.##E0")

        fun bind(pointWithDistance: Pair<VolcanoPointDetails, Double>) {
            val (point, distance) = pointWithDistance
            
            android.util.Log.d("NearbyPointsAdapter", "Binding point: ${point.proteinId} at distance $distance")
            
            // Get gene name from UniProt service (same method as used elsewhere in the app)
            val uniprotRecord = uniprotService?.getUniprotFromPrimary(point.proteinId)
            val uniprotGeneName = uniprotRecord?.get("Gene Names")?.toString()
            
            // Use UniProt gene name if available, otherwise fall back to the point's display name
            val displayName = when {
                !uniprotGeneName.isNullOrEmpty() && uniprotGeneName != point.proteinId -> "$uniprotGeneName (${point.proteinId})"
                else -> point.displayName
            }
            
            tvNearbyGeneName.text = displayName
            tvNearbyFoldChange.text = decimalFormat.format(point.foldChange)
            
            // Format significance value
            tvNearbySignificance.text = if (point.significance < 0.001) {
                scientificFormat.format(point.significance)
            } else {
                decimalFormat.format(point.significance)
            }
            
            // Format distance
            tvDistance.text = decimalFormat.format(distance)
            
            // Show trace group if available
            if (!point.traceGroup.isNullOrEmpty()) {
                tvNearbyTraceGroup.text = point.traceGroup
                layoutNearbyTraceGroup.visibility = View.VISIBLE
                
                // Set trace group color if available
                if (!point.traceGroupColor.isNullOrEmpty()) {
                    try {
                        val color = android.graphics.Color.parseColor(point.traceGroupColor)
                        vNearbyTraceGroupColor.setBackgroundColor(color)
                    } catch (e: Exception) {
                        android.util.Log.w("NearbyPointsAdapter", "Invalid trace group color: ${point.traceGroupColor}")
                    }
                }
            } else {
                layoutNearbyTraceGroup.visibility = View.GONE
            }
            
            // Handle checkbox changes
            cbNearbyPoint.setOnCheckedChangeListener(null) // Clear previous listener
            cbNearbyPoint.isChecked = false // Reset checkbox state
            cbNearbyPoint.setOnCheckedChangeListener { _, isChecked ->
                onCheckboxChanged(point, isChecked)
            }
            
            itemView.setOnClickListener {
                onPointClick(point)
            }
        }
    }

    object NearbyPointDiffCallback : DiffUtil.ItemCallback<Pair<VolcanoPointDetails, Double>>() {
        override fun areItemsTheSame(
            oldItem: Pair<VolcanoPointDetails, Double>, 
            newItem: Pair<VolcanoPointDetails, Double>
        ): Boolean {
            return oldItem.first.proteinId == newItem.first.proteinId
        }

        override fun areContentsTheSame(
            oldItem: Pair<VolcanoPointDetails, Double>, 
            newItem: Pair<VolcanoPointDetails, Double>
        ): Boolean {
            return oldItem == newItem
        }
    }
}