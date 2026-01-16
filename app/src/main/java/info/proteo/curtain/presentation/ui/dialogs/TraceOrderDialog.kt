package info.proteo.curtain.presentation.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import info.proteo.curtain.R
import info.proteo.curtain.domain.model.TraceData
import info.proteo.curtain.presentation.ui.adapters.TraceOrderAdapter

class TraceOrderDialog(
    private val traces: List<TraceData>,
    private val onSave: (List<String>) -> Unit,
    private val onReset: () -> Unit
) : DialogFragment() {

    private lateinit var adapter: TraceOrderAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_trace_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.tracesRecyclerView)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancelButton)
        val resetButton = view.findViewById<MaterialButton>(R.id.resetButton)

        adapter = TraceOrderAdapter(traces.toMutableList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(TraceItemTouchCallback(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)

        saveButton.setOnClickListener {
            val order = adapter.getTraceNames()
            onSave(order)
            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        resetButton.setOnClickListener {
            onReset()
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    private class TraceItemTouchCallback(
        private val adapter: TraceOrderAdapter
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun isLongPressDragEnabled(): Boolean = true
        override fun isItemViewSwipeEnabled(): Boolean = false
    }

    companion object {
        const val TAG = "TraceOrderDialog"
    }
}
