package info.proteo.curtain.presentation.fragments.qrcode

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import info.proteo.curtain.databinding.FragmentAndroidShareBinding
import info.proteo.curtain.utils.EdgeToEdgeHelper
import java.io.File
import java.io.FileOutputStream

class AndroidShareFragment : Fragment() {
    
    private var _binding: FragmentAndroidShareBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var linkId: String
    private lateinit var frontendUrl: String
    private var qrCodeWebView: WebView? = null
    private var generatedQRImageData: String? = null
    
    companion object {
        private const val ARG_LINK_ID = "arg_link_id"
        private const val ARG_FRONTEND_URL = "arg_frontend_url"
        
        fun newInstance(linkId: String, frontendUrl: String): AndroidShareFragment {
            return AndroidShareFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LINK_ID, linkId)
                    putString(ARG_FRONTEND_URL, frontendUrl)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            linkId = it.getString(ARG_LINK_ID) ?: ""
            frontendUrl = it.getString(ARG_FRONTEND_URL) ?: ""
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAndroidShareBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup edge-to-edge for fragment content with horizontal padding
        EdgeToEdgeHelper.setupFragment(this, binding.root, addHorizontalPadding = true)
        
        setupUI()
        generateAndroidUrl()
        setupQRCodeWebView()
    }
    
    private fun setupUI() {
        binding.btnShare.setOnClickListener {
            shareQRCode()
        }
        
        binding.btnDownload.setOnClickListener {
            downloadQRCode()
        }
        
        binding.btnCopyUrl.setOnClickListener {
            copyUrlToClipboard()
        }
        
        binding.btnDirectShare.setOnClickListener {
            shareDirectly()
        }
    }
    
    private fun generateAndroidUrl() {
        // Generate curtain:// protocol URL for Android app
        val androidUrl = "curtain://open?uniqueId=$linkId&apiURL=${frontendUrl.trimEnd('/')}"
        
        binding.urlText.text = androidUrl
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupQRCodeWebView() {
        val androidUrl = "curtain://open?uniqueId=$linkId&apiURL=${frontendUrl.trimEnd('/')}"
        
        qrCodeWebView = WebView(requireContext()).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Generate QR code once the page is loaded
                    generateStyledQRCode(androidUrl)
                }
            }
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
            }
            
            addJavascriptInterface(QRCodeInterface(), "Android")
        }
        
        // Load QR code styling library
        val qrCodeHtml = createQRCodeHTML()
        qrCodeWebView?.loadDataWithBaseURL("file:///android_asset/", qrCodeHtml, "text/html", "UTF-8", null)
    }
    
    private fun createQRCodeHTML(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        background-color: #f5f5f5;
                    }
                    #qr-code {
                        background: white;
                        padding: 20px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                </style>
            </head>
            <body>
                <div id="qr-code"></div>
                <script src="qr-code-styling.min.js"></script>
                <script>
                    function generateQRCode(text) {
                        const qrCode = new QRCodeStyling({
                            width: 250,
                            height: 250,
                            data: text,
                            margin: 5,
                            dotsOptions: {
                                color: "#2E2D62",
                                type: "dots"
                            },
                            backgroundOptions: {
                                color: "#ffffff"
                            },
                            imageOptions: {
                                crossOrigin: "anonymous",
                                margin: 0
                            }
                        });
                        
                        const qrContainer = document.getElementById('qr-code');
                        qrContainer.innerHTML = '';
                        qrCode.append(qrContainer);
                        
                        // Convert to base64 and send to Android
                        setTimeout(() => {
                            qrCode.getRawData('png').then((blob) => {
                                const reader = new FileReader();
                                reader.onload = function() {
                                    const base64 = reader.result.split(',')[1];
                                    Android.onQRCodeGenerated(base64);
                                };
                                reader.readAsDataURL(blob);
                            });
                        }, 100);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun generateStyledQRCode(url: String) {
        qrCodeWebView?.evaluateJavascript("generateQRCode('$url')", null)
    }
    
    inner class QRCodeInterface {
        @JavascriptInterface
        fun onQRCodeGenerated(base64Data: String) {
            activity?.runOnUiThread {
                generatedQRImageData = base64Data
                // Convert base64 to bitmap and display
                try {
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.qrCodeImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error displaying QR code: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun shareQRCode() {
        generatedQRImageData?.let { base64Data ->
            try {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                
                val cachePath = File(requireContext().cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "qr_code_android_$linkId.png")
                
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()
                
                val contentUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "Open in Curtain Android app: ${binding.urlText.text}")
                    type = "image/png"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error sharing QR code: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), "QR code not ready yet", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun downloadQRCode() {
        // Implementation for downloading QR code to gallery
        Toast.makeText(requireContext(), "Download QR code functionality", Toast.LENGTH_SHORT).show()
    }
    
    private fun copyUrlToClipboard() {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Curtain Android URL", binding.urlText.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Android URL copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareDirectly() {
        val androidUrl = binding.urlText.text.toString()
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Open this Curtain data in the Android app: $androidUrl")
            type = "text/plain"
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Curtain Link"))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        qrCodeWebView?.destroy()
        qrCodeWebView = null
        _binding = null
    }
}