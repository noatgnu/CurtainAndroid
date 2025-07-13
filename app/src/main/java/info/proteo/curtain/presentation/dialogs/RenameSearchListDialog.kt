package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.proteo.curtain.databinding.DialogRenameSearchListBinding

class RenameSearchListDialog : DialogFragment() {

    private var _binding: DialogRenameSearchListBinding? = null
    private val binding get() = _binding!!

    private var currentName: String = ""
    private var onRename: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRenameSearchListBinding.inflate(layoutInflater)
        
        currentName = arguments?.getString(ARG_CURRENT_NAME) ?: ""
        
        setupViews()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Search List")
            .setView(binding.root)
            .setPositiveButton("Rename") { _, _ ->
                val newName = binding.nameInput.text?.toString()?.trim()
                if (!newName.isNullOrEmpty() && newName != currentName) {
                    onRename?.invoke(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Return null since we're using AlertDialog
        return null
    }

    private fun setupViews() {
        binding.nameInput.setText(currentName)
        binding.nameInput.selectAll()
        
        // Enable/disable rename button based on input validity
        binding.nameInput.addTextChangedListener { text ->
            val newName = text?.toString()?.trim()
            val isValid = !newName.isNullOrEmpty() && 
                         newName != currentName && 
                         newName.length <= 100 // Reasonable limit
            
            // Update dialog button state through the dialog
            (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            )?.isEnabled = isValid
            
            // Show error for invalid names
            binding.nameInputLayout.error = when {
                newName.isNullOrEmpty() -> "Name cannot be empty"
                newName == currentName -> "Name is the same as current"
                newName.length > 100 -> "Name is too long"
                else -> null
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Initially disable the rename button until valid input
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CURRENT_NAME = "current_name"

        fun newInstance(
            currentName: String,
            onRename: (String) -> Unit
        ): RenameSearchListDialog {
            return RenameSearchListDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_NAME, currentName)
                }
                this.onRename = onRename
            }
        }
    }
}