package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import info.proteo.curtain.R
import info.proteo.curtain.data.local.database.entities.CurtainEntity

class EditDescriptionDialog : DialogFragment() {

    private lateinit var curtain: CurtainEntity
    private var onSave: ((String) -> Unit)? = null
    
    // Views
    private lateinit var tvCurtainId: TextView
    private lateinit var tilDescription: TextInputLayout
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton

    companion object {
        fun newInstance(
            curtain: CurtainEntity,
            onSave: (String) -> Unit
        ): EditDescriptionDialog {
            return EditDescriptionDialog().apply {
                this.curtain = curtain
                this.onSave = onSave
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_description, null)
        
        initViews(view)
        setupViews()
        setupClickListeners()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    private fun initViews(view: View) {
        tvCurtainId = view.findViewById(R.id.tvCurtainId)
        tilDescription = view.findViewById(R.id.tilDescription)
        etDescription = view.findViewById(R.id.etDescription)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSave)
    }

    private fun setupViews() {
        // Set curtain ID
        tvCurtainId.text = "Curtain ID: ${curtain.linkId}"
        
        // Set current description
        etDescription.setText(curtain.description)
        etDescription.setSelection(etDescription.text?.length ?: 0)
        
        // Enable save button based on text changes
        etDescription.addTextChangedListener { text ->
            val newDescription = text?.toString()?.trim() ?: ""
            val hasChanged = newDescription != curtain.description.trim()
            btnSave.isEnabled = hasChanged
        }
        
        // Initial state of save button
        btnSave.isEnabled = false
    }

    private fun setupClickListeners() {
        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val newDescription = etDescription.text?.toString()?.trim() ?: ""
            onSave?.invoke(newDescription)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Set dialog width to 90% of screen width
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Show keyboard and focus on text field
        etDescription.requestFocus()
    }
}