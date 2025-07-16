package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
        "https://celsus.muttsu.xyz",
        "https://curtain-backend.omics.quest"
    )

    // QR code scanner activity result launcher
    private val qrCodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringExtra("SCAN_RESULT")
            if (data != null) {
                handleScannedUrl(data)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddCurtainBinding.inflate(layoutInflater)
        
        setupViews()
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Curtain")
            .setView(binding.root)
            .setPositiveButton("Add", null) // Set to null initially
            .setNegativeButton("Cancel", null)
            .create()
        
        // Override the positive button click to prevent automatic dismissal
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                addCurtain()
            }
        }
        
        return dialog
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
        setupQuickActions()
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
        binding.frontendURLInput.addTextChangedListener { textWatcher() }
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
        
        // Update dialog button state - only if dialog is created
        dialog?.let { d ->
            (d as? androidx.appcompat.app.AlertDialog)?.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            )?.isEnabled = isValid
        }
        
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
        android.util.Log.d("AddCurtainDialog", "addCurtain() called")
        
        if (!validateInputs()) {
            android.util.Log.d("AddCurtainDialog", "Validation failed")
            return
        }
        
        val uniqueId = binding.uniqueIdInput.text?.toString()?.trim() ?: return
        val apiUrl = binding.apiUrlInput.text?.toString()?.trim() ?: return
        val frontendURL = binding.frontendURLInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val description = binding.descriptionInput.text?.toString()?.trim() ?: "Manual import"
        
        android.util.Log.d("AddCurtainDialog", "Adding curtain: $uniqueId, $apiUrl")
        
        lifecycleScope.launch {
            try {
                _binding?.loadingIndicator?.visibility = View.VISIBLE
                
                // Create curtain entry locally without network request
                val result = curtainRepository.createCurtainEntry(uniqueId, apiUrl, frontendURL, description)
                
                onCurtainAdded?.invoke()
                
                Toast.makeText(requireContext(), "Curtain added successfully", Toast.LENGTH_SHORT).show()
                
                dismiss()
                
            } catch (e: Exception) {
                _binding?.loadingIndicator?.visibility = View.GONE
                
                val errorMessage = when {
                    e.message?.contains("UNIQUE constraint failed") == true -> 
                        "Curtain already exists with this ID."
                    e.message?.contains("FOREIGN KEY constraint failed") == true -> 
                        "Invalid API URL or database error."
                    else -> 
                        "Error adding curtain: ${e.message}"
                }
                
                _binding?.uniqueIdInputLayout?.error = errorMessage
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
        // Initially disable the add button until valid input, then validate
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = false
        
        // Run validation after dialog is created
        validateInputs()
    }

    private fun setupQuickActions() {
        // Set up paste URL button
        binding.btnPasteUrl.setOnClickListener {
            pasteFromClipboard()
        }
        
        // Set up QR code scanner button
        binding.btnScanQr.setOnClickListener {
            launchQrScanner()
        }
    }
    
    private fun pasteFromClipboard() {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val pastedText = clipData.getItemAt(0).text?.toString()
            if (!pastedText.isNullOrEmpty()) {
                handleScannedUrl(pastedText)
            } else {
                Toast.makeText(requireContext(), "No text found in clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun launchQrScanner() {
        try {
            // Use ZXing's IntentIntegrator for QR code scanning
            val intent = Intent("com.google.zxing.client.android.SCAN")
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
            intent.putExtra("SAVE_HISTORY", false)
            
            qrCodeScannerLauncher.launch(intent)
        } catch (e: Exception) {
            // If ZXing app is not installed, show a message
            Toast.makeText(
                requireContext(),
                "Please install a QR code scanner app (like ZXing Barcode Scanner)",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun handleScannedUrl(url: String) {
        if (url.startsWith("curtain://")) {
            parseCurtainUrl(url)
        } else if (url.startsWith("https://") && url.contains("curtain")) {
            // Handle web URL format
            parseWebUrl(url)
        } else {
            Toast.makeText(requireContext(), "Invalid URL format. Expected curtain:// or curtain web URL", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun parseCurtainUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            
            // Check if the URL follows the expected format: curtain://open?uniqueId=X&apiURL=Y&frontendURL=Z
            if (uri.scheme == "curtain" && uri.host == "open") {
                val uniqueId = uri.getQueryParameter("uniqueId")
                val apiURL = uri.getQueryParameter("apiURL")
                val frontendURL = uri.getQueryParameter("frontendURL")
                
                if (!uniqueId.isNullOrEmpty() && !apiURL.isNullOrEmpty()) {
                    // Populate the form fields
                    binding.uniqueIdInput.setText(uniqueId)
                    binding.apiUrlInput.setText(apiURL)
                    
                    if (!frontendURL.isNullOrEmpty()) {
                        binding.frontendURLInput.setText(frontendURL)
                    }
                    
                    // Set a default description indicating the source
                    binding.descriptionInput.setText("Added via QR code/URL")
                    
                    validateInputs()
                    
                    Toast.makeText(requireContext(), "Curtain URL parsed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid curtain URL: Missing required parameters (uniqueId, apiURL)", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Invalid curtain URL format. Expected: curtain://open?uniqueId=X&apiURL=Y&frontendURL=Z", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error parsing curtain URL: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun parseWebUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            
            // Check if it's the specific curtain.proteo.info format: https://curtain.proteo.info/#/linkid
            if (uri.host == "curtain.proteo.info" && uri.fragment != null) {
                val fragment = uri.fragment
                if (fragment?.startsWith("/") == true) {
                    val linkId = fragment.substring(1) // Remove the leading "/"
                    
                    if (linkId.isNotEmpty()) {
                        // Use predefined backend and frontend URLs for curtain.proteo.info
                        val backendUrl = "https://celsus.muttsu.xyz"
                        val frontendUrl = "https://curtain.proteo.info"
                        
                        // Populate the form fields
                        binding.uniqueIdInput.setText(linkId)
                        binding.apiUrlInput.setText(backendUrl)
                        binding.frontendURLInput.setText(frontendUrl)
                        
                        // Set a default description indicating the source
                        binding.descriptionInput.setText("Added from curtain.proteo.info")
                        
                        validateInputs()
                        
                        Toast.makeText(requireContext(), "Curtain.proteo.info URL parsed successfully", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
            
            // Fall back to general web URL parsing
            // Expected format: https://curtain.proteo.info/curtain/open/uniqueId?apiURL=...&frontendURL=...
            val pathSegments = uri.pathSegments
            
            var uniqueId: String? = null
            var apiURL: String? = null
            var frontendURL: String? = null
            
            // Try to extract uniqueId from path
            if (pathSegments.size >= 3 && pathSegments[1] == "open") {
                uniqueId = pathSegments[2]
            }
            
            // Extract other parameters from query
            apiURL = uri.getQueryParameter("apiURL")
            frontendURL = uri.getQueryParameter("frontendURL")
            
            if (!uniqueId.isNullOrEmpty() && !apiURL.isNullOrEmpty()) {
                // Populate the form fields
                binding.uniqueIdInput.setText(uniqueId)
                binding.apiUrlInput.setText(apiURL)
                
                if (!frontendURL.isNullOrEmpty()) {
                    binding.frontendURLInput.setText(frontendURL)
                }
                
                // Set a default description indicating the source
                binding.descriptionInput.setText("Added via web URL")
                
                validateInputs()
                
                Toast.makeText(requireContext(), "Web URL parsed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Invalid web URL: Missing required parameters or unsupported format", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error parsing web URL: ${e.message}", Toast.LENGTH_LONG).show()
        }
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