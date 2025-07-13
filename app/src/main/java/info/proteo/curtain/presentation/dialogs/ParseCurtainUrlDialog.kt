package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.proteo.curtain.databinding.DialogParseCurtainUrlBinding

class ParseCurtainUrlDialog : DialogFragment() {

    private var _binding: DialogParseCurtainUrlBinding? = null
    private val binding get() = _binding!!

    private var onUrlParsed: ((String, String, String?) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogParseCurtainUrlBinding.inflate(layoutInflater)
        
        setupViews()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Parse Curtain URL")
            .setView(binding.root)
            .setPositiveButton("Extract") { _, _ ->
                extractParameters()
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
        // Set helper text with examples
        binding.urlInputLayout.helperText = "Paste a curtain URL to extract parameters automatically"
        
        // Try to auto-fill from clipboard
        tryAutoFillFromClipboard()
        
        // Set up validation
        binding.urlInput.addTextChangedListener {
            validateAndParseUrl()
        }
        
        // Set up paste button
        binding.pasteButton.setOnClickListener {
            pasteFromClipboard()
        }
    }

    private fun tryAutoFillFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()
                if (!clipText.isNullOrEmpty() && isLikelyCurtainUrl(clipText)) {
                    binding.urlInput.setText(clipText)
                }
            }
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    binding.urlInput.setText(clipText)
                }
            }
        } catch (e: Exception) {
            binding.urlInputLayout.error = "Could not access clipboard"
        }
    }

    private fun isLikelyCurtainUrl(url: String): Boolean {
        return url.contains("curtain", ignoreCase = true) || 
               url.contains("uniqueId", ignoreCase = true) ||
               url.contains("apiURL", ignoreCase = true)
    }

    private fun validateAndParseUrl(): Boolean {
        val urlText = binding.urlInput.text?.toString()?.trim()
        
        if (urlText.isNullOrEmpty()) {
            binding.urlInputLayout.error = null
            clearPreview()
            return false
        }
        
        try {
            val parsedData = parseUrl(urlText)
            if (parsedData != null) {
                binding.urlInputLayout.error = null
                showPreview(parsedData.first, parsedData.second, parsedData.third)
                
                // Enable extract button
                (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
                )?.isEnabled = true
                
                return true
            } else {
                binding.urlInputLayout.error = "Could not extract curtain parameters from this URL"
                clearPreview()
            }
        } catch (e: Exception) {
            binding.urlInputLayout.error = "Invalid URL format"
            clearPreview()
        }
        
        // Disable extract button
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = false
        
        return false
    }

    private fun parseUrl(urlText: String): Triple<String, String, String?>? {
        try {
            // Try parsing as URI directly
            val uri = Uri.parse(urlText)
            
            // Check for curtain:// scheme
            if (uri.scheme == "curtain" && uri.host == "open") {
                val uniqueId = uri.getQueryParameter("uniqueId")
                val apiURL = uri.getQueryParameter("apiURL")
                val frontendURL = uri.getQueryParameter("frontendURL") ?: uri.getQueryParameter("frontendURL")
                
                if (!uniqueId.isNullOrEmpty() && !apiURL.isNullOrEmpty()) {
                    return Triple(uniqueId, apiURL, frontendURL)
                }
            }
            
            // Check for web URLs with curtain parameters
            if (uri.scheme in listOf("http", "https")) {
                val uniqueId = uri.getQueryParameter("uniqueId") ?: 
                              uri.getQueryParameter("unique_id") ?: 
                              uri.getQueryParameter("id")
                
                val apiURL = uri.getQueryParameter("apiURL") ?: 
                            uri.getQueryParameter("api_url") ?: 
                            uri.getQueryParameter("api") ?: 
                            uri.getQueryParameter("host")
                
                val frontendURL = uri.getQueryParameter("frontendURL") ?: 
                                 uri.getQueryParameter("frontendURL") ?:
                                 uri.getQueryParameter("frontend_url")
                
                if (!uniqueId.isNullOrEmpty() && !apiURL.isNullOrEmpty()) {
                    return Triple(uniqueId, apiURL, frontendURL)
                }
                
                // Try to extract from path for certain URL patterns
                val path = uri.path
                if (path != null && path.contains("/curtain/") && path.contains("/open/")) {
                    // Pattern like: https://example.com/curtain/open/UNIQUE_ID
                    val pathParts = path.split("/")
                    val openIndex = pathParts.indexOf("open")
                    if (openIndex >= 0 && openIndex + 1 < pathParts.size) {
                        val extractedId = pathParts[openIndex + 1]
                        val baseUrl = "${uri.scheme}://${uri.authority}"
                        // For path-based URLs, the frontend URL is typically the original URL
                        val frontendURL = urlText
                        return Triple(extractedId, baseUrl, frontendURL)
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun showPreview(uniqueId: String, apiUrl: String, frontendURL: String?) {
        binding.previewLayout.visibility = View.VISIBLE
        binding.previewUniqueId.text = "Unique ID: $uniqueId"
        binding.previewApiUrl.text = "API URL: $apiUrl"
        
        if (!frontendURL.isNullOrEmpty()) {
            binding.previewfrontendURL.text = "Frontend URL: $frontendURL"
            binding.previewfrontendURL.visibility = View.VISIBLE
        } else {
            binding.previewfrontendURL.visibility = View.GONE
        }
    }

    private fun clearPreview() {
        binding.previewLayout.visibility = View.GONE
    }

    private fun extractParameters() {
        val urlText = binding.urlInput.text?.toString()?.trim()
        if (!urlText.isNullOrEmpty()) {
            val parsedData = parseUrl(urlText)
            if (parsedData != null) {
                onUrlParsed?.invoke(parsedData.first, parsedData.second, parsedData.third)
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Initially disable the extract button
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )?.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(onUrlParsed: (String, String, String?) -> Unit): ParseCurtainUrlDialog {
            return ParseCurtainUrlDialog().apply {
                this.onUrlParsed = onUrlParsed
            }
        }
    }
}