package info.proteo.curtain.presentation.ui.qr

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import info.proteo.curtain.domain.service.DeepLinkHandler
import info.proteo.curtain.domain.service.DeepLinkResult
import info.proteo.curtain.presentation.viewmodel.CurtainViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    navController: NavController,
    onQRCodeScanned: (String) -> Unit,
    deepLinkHandler: DeepLinkHandler? = null,
    curtainViewModel: CurtainViewModel = hiltViewModel()
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var flashEnabled by remember { mutableStateOf(false) }
    var scannedCode by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var parsedData by remember { mutableStateOf<DeepLinkResult.ParsedQRData?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { flashEnabled = !flashEnabled }) {
                        Icon(
                            if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (flashEnabled) "Flash On" else "Flash Off"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !cameraPermissionState.status.isGranted -> {
                    CameraPermissionRequest(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                    )
                }
                scannedCode != null -> {
                    QRCodeResult(
                        code = scannedCode!!,
                        parsedLinkId = parsedData?.linkId,
                        onUseCode = {
                            if (parsedData != null) {
                                showAddDialog = true
                            } else {
                                onQRCodeScanned(scannedCode!!)
                                navController.navigateUp()
                            }
                        },
                        onScanAgain = {
                            scannedCode = null
                            parsedData = null
                        }
                    )
                }
                else -> {
                    CameraPreview(
                        flashEnabled = flashEnabled,
                        onQRCodeDetected = { code ->
                            scannedCode = code
                            parsedData = deepLinkHandler?.parseQRCodeForDialog(code)
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog && parsedData != null) {
        QRAddCurtainDialog(
            linkId = parsedData!!.linkId,
            apiUrl = parsedData!!.apiURL ?: "https://api.curtain.proteo.info",
            frontendUrl = parsedData!!.frontendURL ?: "",
            onDismiss = {
                showAddDialog = false
                scannedCode = null
                parsedData = null
            },
            onAdd = { linkId, apiUrl, frontendUrl ->
                curtainViewModel.loadCurtain(
                    linkId = linkId,
                    apiUrl = apiUrl,
                    frontendUrl = frontendUrl
                )
                showAddDialog = false
                navController.navigateUp()
            }
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Camera access is needed to scan QR codes for dataset import",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun CameraPreview(
    flashEnabled: Boolean,
    onQRCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(previewView, flashEnabled) {
            previewView?.let { preview ->
                val cameraProvider = cameraProviderFuture.get()

                val previewUseCase = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(preview.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            QRCodeAnalyzer { qrCode ->
                                onQRCodeDetected(qrCode)
                            }
                        )
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        previewUseCase,
                        imageAnalysis
                    )

                    camera.cameraControl.enableTorch(flashEnabled)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text(
                "Position the QR code within the frame",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun QRCodeResult(
    code: String,
    parsedLinkId: String?,
    onUseCode: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "QR Code Detected",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (parsedLinkId != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Detected Link ID:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        parsedLinkId,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onUseCode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (parsedLinkId != null) "Add Dataset" else "Use This Code")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Again")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRAddCurtainDialog(
    linkId: String,
    apiUrl: String,
    frontendUrl: String,
    onDismiss: () -> Unit,
    onAdd: (linkId: String, apiUrl: String, frontendUrl: String) -> Unit
) {
    var editedLinkId by remember { mutableStateOf(linkId) }
    var editedApiUrl by remember { mutableStateOf(apiUrl) }
    var editedFrontendUrl by remember { mutableStateOf(frontendUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Dataset from QR Code") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = editedLinkId,
                    onValueChange = { editedLinkId = it },
                    label = { Text("Link ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedApiUrl,
                    onValueChange = { editedApiUrl = it },
                    label = { Text("API URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedFrontendUrl,
                    onValueChange = { editedFrontendUrl = it },
                    label = { Text("Frontend URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editedLinkId.isNotBlank() && editedApiUrl.isNotBlank()) {
                        onAdd(editedLinkId.trim(), editedApiUrl.trim(), editedFrontendUrl.trim())
                    }
                },
                enabled = editedLinkId.isNotBlank() && editedApiUrl.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()
    private var lastScannedTime = 0L
    private val scanCooldown = 2000L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        when (barcode.valueType) {
                            Barcode.TYPE_URL,
                            Barcode.TYPE_TEXT -> {
                                barcode.rawValue?.let { value ->
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastScannedTime > scanCooldown) {
                                        lastScannedTime = currentTime
                                        onQRCodeDetected(value)
                                    }
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
