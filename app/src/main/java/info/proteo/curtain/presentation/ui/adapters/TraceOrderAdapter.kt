package info.proteo.curtain.presentation.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import info.proteo.curtain.R
import info.proteo.curtain.domain.model.TraceData
import java.util.Collections

class TraceOrderAdapter(
    private val traces: MutableList<TraceData>
) : RecyclerView.Adapter<TraceOrderAdapter.TraceViewHolder>() {

    inner class TraceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.dragHandle)
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val traceName: TextView = view.findViewById(R.id.traceName)
        val positionBadge: TextView = view.findViewById(R.id.positionBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trace_order, parent, false)
        return TraceViewHolder(view)
    }

    override fun onBindViewHolder(holder: TraceViewHolder, position: Int) {
        val trace = traces[position]

        holder.traceName.text = trace.name
        holder.positionBadge.text = (position + 1).toString()

        try {
            val color = Color.parseColor(trace.color)
            holder.colorIndicator.setBackgroundColor(color)
        } catch (e: IllegalArgumentException) {
            holder.colorIndicator.setBackgroundColor(Color.parseColor("#999999"))
        }
    }

    override fun getItemCount(): Int = traces.size

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(traces, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(traces, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        notifyItemRangeChanged(minOf(fromPosition, toPosition), kotlin.math.abs(toPosition - fromPosition) + 1)
        return true
    }

    fun getTraceNames(): List<String> {
        return traces.map { it.name }
    }
}
