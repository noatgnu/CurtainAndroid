package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.CurtainRepository
import info.proteo.curtain.databinding.DialogAddCurtainBinding
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class AddCurtainDialog : DialogFragment() {

    private var _binding: DialogAddCurtainBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var curtainRepository: CurtainRepository

    private var onCurtainAdded: (() -> Unit)? = null

    // Common API URLs for quick selection
    private val commonApiUrls = listOf(
        "https://curtain.proteo.info",
        "https://curtain-dev.proteo.info",
        "https://localhost:8000",
        "https://curtain-staging.proteo.info"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddCurtainBinding.inflate(layoutInflater)
        
        setupViews()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Curtain")
            .setView(binding.root)
            .setPositiveButton("Add") { _, _ ->
                addCurtain()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("From URL") { _, _ ->
                showUrlParsingDialog()
            }
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
        setupApiUrlDropdown()
        setupValidation()
        setupHelperText()
    }

    private fun setupApiUrlDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            commonApiUrls
        )
        binding.apiUrlInput.setAdapter(adapter)
        
        // Set default API URL
        binding.apiUrlInput.setText(commonApiUrls.first(), false)
    }

    private fun setupValidation() {
        // Validate inputs and enable/disable add button
        val textWatcher = {
            validateInputs()
        }
        
        binding.uniqueIdInput.addTextChangedListener { textWatcher() }
        binding.apiUrlInput.addTextChangedListener { textWatcher() }
        binding.descriptionInput.addTextChangedListener { textWatcher() }
    }

    private fun setupHelperText() {
        binding.uniqueIdInputLayout.helperText = "The unique identifier from the curtain URL"
        binding.apiUrlInputLayout.helperText = "The base URL of the Curtain API server"
        binding.descriptionInputLayout.helperText = "Optional description for this curtain (e.g., project name)"
    }

    private fun validateInputs(): Boolean {
        val uniqueId = binding.uniqueIdInput.text?.toString()?.trim()
        val apiUrl = binding.apiUrlInput.text?.toString()?.trim()
        
        var isValid = true
        
        // Validate unique ID
        if (uniqueId.isNullOrEmpty()) {
            binding.uniqueIdInputLayout.error = "Unique ID is required"
            isValid = false
        } else if (uniqueId.length < 3) {
            binding.uniqueIdInputLayout.error = "Unique ID must be at least 3 characters"
            isValid = false
        } else {
            binding.uniqueIdInputLayout.error = null
        }
        
        // Validate API URL
        if (apiUrl.isNullOrEmpty()) {
            binding.apiUrlInputLayout.error = "API URL is required"
            isValid = false
        } else if (!isValidUrl(apiUrl)) {
            binding.apiUrlInputLayout.error = "Please enter a valid URL"
            isValid = false
        } else {
            binding.apiUrlInputLayout.error = null
        }
        
        // Update dialog button state
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = isValid
        
        return isValid
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            parsedUrl.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }

    private fun addCurtain() {
        if (!validateInputs()) return
        
        val uniqueId = binding.uniqueIdInput.text?.toString()?.trim() ?: return
        val apiUrl = binding.apiUrlInput.text?.toString()?.trim() ?: return
        val frontendURL = binding.frontendURLInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val description = binding.descriptionInput.text?.toString()?.trim() ?: "Manual import"
        
        lifecycleScope.launch {
            try {
                binding.loadingIndicator.visibility = View.VISIBLE
                
                // Use the same method as the deep link handler
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(uniqueId, apiUrl, frontendURL)
                
                if (result != null) {
                    // Update the description if provided
                    if (description.isNotEmpty() && description != "Manual import") {
                        // Update the curtain description in the database
                        curtainRepository.updateCurtainDescription(uniqueId, description)
                    }
                    
                    onCurtainAdded?.invoke()
                    
                    view?.let {
                        Snackbar.make(it, "Curtain added successfully", Snackbar.LENGTH_SHORT).show()
                    }
                    
                    dismiss()
                } else {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.uniqueIdInputLayout.error = "Curtain not found. Check the unique ID and API URL."
                }
                
            } catch (e: Exception) {
                binding.loadingIndicator.visibility = View.GONE
                
                val errorMessage = when {
                    e.message?.contains("network", ignoreCase = true) == true -> 
                        "Network error. Check your connection and API URL."
                    e.message?.contains("404") == true -> 
                        "Curtain not found. Check the unique ID."
                    e.message?.contains("401") == true || e.message?.contains("403") == true -> 
                        "Access denied. You may not have permission to access this curtain."
                    else -> 
                        "Error loading curtain: ${e.message}"
                }
                
                binding.apiUrlInputLayout.error = errorMessage
            }
        }
    }

    private fun showUrlParsingDialog() {
        val urlParsingDialog = ParseCurtainUrlDialog.newInstance { uniqueId, apiUrl, frontendURL ->
            binding.uniqueIdInput.setText(uniqueId)
            binding.apiUrlInput.setText(apiUrl)
            if (!frontendURL.isNullOrEmpty()) {
                binding.frontendURLInput.setText(frontendURL)
            }
            validateInputs()
        }
        
        urlParsingDialog.show(parentFragmentManager, "ParseCurtainUrlDialog")
    }

    override fun onStart() {
        super.onStart()
        // Initially disable the add button until valid input
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(onCurtainAdded: () -> Unit): AddCurtainDialog {
            return AddCurtainDialog().apply {
                this.onCurtainAdded = onCurtainAdded
            }
        }
    }
}