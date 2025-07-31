package info.proteo.curtain.presentation.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import info.proteo.curtain.R
import info.proteo.curtain.databinding.ActivityQrScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQrScannerBinding
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isScanning = true
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Enable edge-to-edge display
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Handle window insets for proper spacing
        setupWindowInsets()
        
        setupToolbar()
        setupUI()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Request camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun setupWindowInsets() {
        val rootView = binding.root // Use the actual root view from binding
        val toolbar = binding.toolbar
        val controlsLayout = binding.controlsLayout
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset to toolbar to extend under status bar
            toolbar.setPadding(
                toolbar.paddingLeft,
                systemBars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )
            
            // Apply bottom inset to controls layout
            controlsLayout.setPadding(
                controlsLayout.paddingLeft,
                controlsLayout.paddingTop,
                controlsLayout.paddingRight,
                systemBars.bottom
            )
            
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Scan QR Code"
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupUI() {
        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        binding.btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        if (isScanning) {
                            handleScannedBarcode(barcode)
                        }
                    })
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                
                // Enable flashlight toggle if available
                binding.btnFlashlight.visibility = if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                // Observe torch state to update icon
                camera?.cameraInfo?.torchState?.observe(this) { torchState ->
                    // torchState is an Int (TorchState.OFF = 0, TorchState.ON = 1)
                    val isOn = torchState == androidx.camera.core.TorchState.ON
                    updateFlashlightIcon(isOn)
                }
                
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
                finish()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun toggleFlashlight() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                // Get current torch state (Int value: TorchState.OFF = 0, TorchState.ON = 1)
                val currentTorchState = cam.cameraInfo.torchState.value ?: androidx.camera.core.TorchState.OFF
                val isCurrentlyOn = currentTorchState == androidx.camera.core.TorchState.ON
                val newTorchState = !isCurrentlyOn
                
                Log.d(TAG, "Torch toggle: current=$currentTorchState (isOn=$isCurrentlyOn), setting to=$newTorchState")
                cam.cameraControl.enableTorch(newTorchState)
            }
        }
    }
    
    private fun updateFlashlightIcon(torchState: Boolean) {
        val iconRes = if (torchState) {
            R.drawable.ic_flashlight_on
        } else {
            R.drawable.ic_flashlight
        }
        
        Log.d(TAG, "Updating flashlight icon: torchState=$torchState, iconRes=$iconRes")
        binding.btnFlashlight.setIconResource(iconRes)
    }
    
    private fun handleScannedBarcode(barcode: String) {
        isScanning = false
        
        runOnUiThread {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SCAN_RESULT, barcode)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT,
                                Barcode.TYPE_URL -> {
                                    barcode.rawValue?.let { onBarcodeDetected(it) }
                                    return@addOnSuccessListener
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    companion object {
        private const val TAG = "QRScannerActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val EXTRA_SCAN_RESULT = "scan_result"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, QRScannerActivity::class.java)
        }
    }
}