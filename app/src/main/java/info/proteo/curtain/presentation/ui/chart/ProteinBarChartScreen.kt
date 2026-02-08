package info.proteo.curtain.presentation.ui.chart

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.ui.dialogs.ExportFormat
import info.proteo.curtain.presentation.ui.dialogs.ExportPlotDialog
import info.proteo.curtain.presentation.utils.FileExportUtils
import info.proteo.curtain.presentation.viewmodel.ProteinChartViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProteinBarChartScreen(
    linkId: String,
    proteinId: String,
    navController: NavController,
    curtainDetailsViewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel,
    viewModel: ProteinChartViewModel = hiltViewModel()
) {
    val curtainData by curtainDetailsViewModel.curtainData.collectAsState()
    val barChartHtml by viewModel.barChartHtml.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentProteinId by viewModel.proteinId.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(linkId, proteinId, curtainData) {
        if (curtainData != null) {
            viewModel.loadProteinChart(curtainData!!, proteinId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isPTM = curtainData?.differentialForm?.isPTM == true
                    Text(if (isPTM) "Site Bar Chart" else "Protein Bar Chart")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                    IconButton(onClick = {  }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    ErrorView(
                        error = error!!,
                        onRetry = {
                            curtainData?.let { viewModel.loadProteinChart(it, proteinId) }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                barChartHtml != null -> {
                    BarChartView(
                        htmlContent = barChartHtml!!,
                        onWebViewCreated = { currentWebView = it },
                        onImageExported = { json ->
                            scope.launch {
                                try {
                                    val jsonObj = JSONObject(json)
                                    val format = jsonObj.getString("format")
                                    val filename = jsonObj.getString("filename")
                                    val dataUrl = jsonObj.getString("dataUrl")
                                    val result = FileExportUtils.exportFromDataUrl(context, filename, dataUrl, format)
                                    exportMessage = result.getOrNull() ?: result.exceptionOrNull()?.message
                                } catch (e: Exception) {
                                    exportMessage = "Export failed: ${e.message}"
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportPlotDialog(
            defaultFileName = "bar_chart_${proteinId}",
            onDismiss = { showExportDialog = false },
            onExport = { fileName, format ->
                val formatStr = when (format) {
                    ExportFormat.SVG -> "svg"
                    ExportFormat.PNG -> "png"
                }
                currentWebView?.evaluateJavascript(
                    "window.BarChart.exportPlot('$formatStr', '$fileName')",
                    null
                ) ?: run { exportMessage = "Chart not ready for export" }
                showExportDialog = false
            }
        )
    }

    exportMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            exportMessage = null
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(message) }
    }
}

@Composable
private fun BarChartView(
    htmlContent: String,
    onWebViewCreated: (WebView) -> Unit = {},
    onImageExported: (String) -> Unit = {}
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                webViewClient = WebViewClient()
                webChromeClient = android.webkit.WebChromeClient()

                val bridge = info.proteo.curtain.domain.service.WebViewJavaScriptBridge(
                    onPlotReady = {
                        errorMessage = null
                    },
                    onPlotError = { message ->
                        errorMessage = message
                    },
                    onBarClicked = { data ->
                    },
                    onBarHover = { data ->
                    },
                    onImageExported = { json -> onImageExported(json) }
                )

                addJavascriptInterface(bridge, "AndroidBridge")
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )

    errorMessage?.let { error ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun ProteinNavigationBar(
    currentIndex: Int,
    totalProteins: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous protein")
            }

            Text(
                text = "${currentIndex + 1} / $totalProteins",
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(
                onClick = onNext,
                enabled = currentIndex < totalProteins - 1
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next protein")
            }
        }
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            "Error loading chart",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
