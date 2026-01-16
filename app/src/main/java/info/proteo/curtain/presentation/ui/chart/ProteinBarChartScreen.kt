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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.ProteinChartViewModel

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

    LaunchedEffect(linkId, proteinId, curtainData) {
        if (curtainData != null) {
            viewModel.loadProteinChart(curtainData!!, proteinId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protein Bar Chart") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportChart() }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
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
                    BarChartView(htmlContent = barChartHtml!!)
                }
            }
        }
    }
}

@Composable
private fun BarChartView(htmlContent: String) {
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
                    }
                )

                addJavascriptInterface(bridge, "AndroidBridge")
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
