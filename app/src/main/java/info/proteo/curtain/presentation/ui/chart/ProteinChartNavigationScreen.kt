package info.proteo.curtain.presentation.ui.chart

import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import info.proteo.curtain.presentation.viewmodel.ProteinChartViewModel
import kotlinx.coroutines.launch

enum class ChartType {
    BAR_CHART,
    AVERAGE_BAR_CHART,
    VIOLIN_PLOT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProteinChartNavigationScreen(
    linkId: String,
    proteinId: String? = null,
    geneName: String? = null,
    curtainDetailsViewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel,
    viewModel: ProteinChartViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val curtainData by curtainDetailsViewModel.curtainData.collectAsState()
    val barChartHtml by viewModel.barChartHtml.collectAsState()
    val averageBarChartHtml by viewModel.averageBarChartHtml.collectAsState()
    val violinPlotHtml by viewModel.violinPlotHtml.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentProteinId by viewModel.proteinId.collectAsState()

    var selectedChartType by remember { mutableStateOf(ChartType.BAR_CHART) }
    var showProteinInfo by remember { mutableStateOf(false) }
    var showChartSettings by remember { mutableStateOf(false) }

    var enableImputation by remember { mutableStateOf(false) }
    var averageBarErrorType by remember { mutableStateOf("Standard Error") }
    var showAverageIndividualPoints by remember { mutableStateOf(true) }

    var barChartMin by remember { mutableStateOf("") }
    var barChartMax by remember { mutableStateOf("") }
    var avgBarChartMin by remember { mutableStateOf("") }
    var avgBarChartMax by remember { mutableStateOf("") }
    var violinPlotMin by remember { mutableStateOf("") }
    var violinPlotMax by remember { mutableStateOf("") }

    LaunchedEffect(curtainData, currentProteinId) {
        currentProteinId?.let { pid ->
            curtainData?.let { data ->
                val limits = data.settings.individualYAxisLimits[pid] as? Map<*, *>

                (limits?.get("barChart") as? Map<*, *>)?.let { barLimits ->
                    barChartMin = (barLimits["min"] as? Number)?.toString() ?: ""
                    barChartMax = (barLimits["max"] as? Number)?.toString() ?: ""
                }

                (limits?.get("averageBarChart") as? Map<*, *>)?.let { avgLimits ->
                    avgBarChartMin = (avgLimits["min"] as? Number)?.toString() ?: ""
                    avgBarChartMax = (avgLimits["max"] as? Number)?.toString() ?: ""
                }

                (limits?.get("violinPlot") as? Map<*, *>)?.let { violinLimits ->
                    violinPlotMin = (violinLimits["min"] as? Number)?.toString() ?: ""
                    violinPlotMax = (violinLimits["max"] as? Number)?.toString() ?: ""
                }
            }
        }
    }

    LaunchedEffect(
        linkId,
        proteinId,
        geneName,
        curtainData,
        enableImputation,
        averageBarErrorType,
        showAverageIndividualPoints
    ) {
        if (proteinId != null && curtainData != null) {
            viewModel.loadProteinChart(
                curtainData = curtainData!!,
                proteinId = proteinId,
                geneName = geneName,
                enableImputation = enableImputation,
                useStandardError = averageBarErrorType == "Standard Error",
                showAverageIndividualPoints = showAverageIndividualPoints
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Protein Charts")
                        currentProteinId?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showProteinInfo = true }) {
                        Icon(Icons.Default.Info, "Protein Info")
                    }
                    IconButton(onClick = { showChartSettings = true }) {
                        Icon(Icons.Default.Settings, "Chart Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Bar Chart") },
                    selected = selectedChartType == ChartType.BAR_CHART,
                    onClick = { selectedChartType = ChartType.BAR_CHART }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Average") },
                    selected = selectedChartType == ChartType.AVERAGE_BAR_CHART,
                    onClick = { selectedChartType = ChartType.AVERAGE_BAR_CHART }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text("Violin") },
                    selected = selectedChartType == ChartType.VIOLIN_PLOT,
                    onClick = { selectedChartType = ChartType.VIOLIN_PLOT }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Loading protein data...")
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = error ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                else -> {
                    when (selectedChartType) {
                        ChartType.BAR_CHART -> {
                            barChartHtml?.let { html ->
                                ChartWebView(html = html)
                            }
                        }
                        ChartType.AVERAGE_BAR_CHART -> {
                            averageBarChartHtml?.let { html ->
                                ChartWebView(html = html)
                            }
                        }
                        ChartType.VIOLIN_PLOT -> {
                            violinPlotHtml?.let { html ->
                                ChartWebView(html = html)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProteinInfo) {
        ProteinInfoDialog(
            proteinId = currentProteinId ?: "",
            geneName = geneName,
            curtainData = curtainData,
            viewModel = viewModel,
            onDismiss = { showProteinInfo = false }
        )
    }

    if (showChartSettings) {
        ChartSettingsDialog(
            selectedChartType = selectedChartType,
            proteinId = currentProteinId ?: "",
            enableImputation = enableImputation,
            onEnableImputationChange = { enableImputation = it },
            averageBarErrorType = averageBarErrorType,
            onAverageBarErrorTypeChange = { averageBarErrorType = it },
            showAverageIndividualPoints = showAverageIndividualPoints,
            onShowAverageIndividualPointsChange = { showAverageIndividualPoints = it },
            barChartMin = barChartMin,
            barChartMax = barChartMax,
            onBarChartMinChange = { barChartMin = it },
            onBarChartMaxChange = { barChartMax = it },
            avgBarChartMin = avgBarChartMin,
            avgBarChartMax = avgBarChartMax,
            onAvgBarChartMinChange = { avgBarChartMin = it },
            onAvgBarChartMaxChange = { avgBarChartMax = it },
            violinPlotMin = violinPlotMin,
            violinPlotMax = violinPlotMax,
            onViolinPlotMinChange = { violinPlotMin = it },
            onViolinPlotMaxChange = { violinPlotMax = it },
            onSaveLimits = {
                curtainDetailsViewModel.saveIndividualYAxisLimits(
                    proteinId = currentProteinId ?: "",
                    barChartMin = barChartMin.toDoubleOrNull(),
                    barChartMax = barChartMax.toDoubleOrNull(),
                    avgBarChartMin = avgBarChartMin.toDoubleOrNull(),
                    avgBarChartMax = avgBarChartMax.toDoubleOrNull(),
                    violinPlotMin = violinPlotMin.toDoubleOrNull(),
                    violinPlotMax = violinPlotMax.toDoubleOrNull()
                )
            },
            onClearLimits = {
                curtainDetailsViewModel.clearIndividualYAxisLimits(currentProteinId ?: "")
                barChartMin = ""
                barChartMax = ""
                avgBarChartMin = ""
                avgBarChartMax = ""
                violinPlotMin = ""
                violinPlotMax = ""
            },
            onDismiss = { showChartSettings = false }
        )
    }
}

@Composable
fun ChartWebView(html: String) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false

                setInitialScale(1)

                if (isDarkTheme) {
                    setBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"))
                } else {
                    setBackgroundColor(android.graphics.Color.WHITE)
                }

                android.util.Log.d("ChartWebView", "Loading HTML, length: ${html.length}")
            }
        },
        update = { webView ->
            if (isDarkTheme) {
                webView.setBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"))
            } else {
                webView.setBackgroundColor(android.graphics.Color.WHITE)
            }

            android.util.Log.d("ChartWebView", "Updating WebView with HTML")
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartSettingsDialog(
    selectedChartType: ChartType,
    proteinId: String,
    enableImputation: Boolean,
    onEnableImputationChange: (Boolean) -> Unit,
    averageBarErrorType: String,
    onAverageBarErrorTypeChange: (String) -> Unit,
    showAverageIndividualPoints: Boolean,
    onShowAverageIndividualPointsChange: (Boolean) -> Unit,
    barChartMin: String,
    barChartMax: String,
    onBarChartMinChange: (String) -> Unit,
    onBarChartMaxChange: (String) -> Unit,
    avgBarChartMin: String,
    avgBarChartMax: String,
    onAvgBarChartMinChange: (String) -> Unit,
    onAvgBarChartMaxChange: (String) -> Unit,
    violinPlotMin: String,
    violinPlotMax: String,
    onViolinPlotMinChange: (String) -> Unit,
    onViolinPlotMaxChange: (String) -> Unit,
    onSaveLimits: () -> Unit,
    onClearLimits: () -> Unit,
    onDismiss: () -> Unit
) {
    var showErrorTypeDropdown by remember { mutableStateOf(false) }
    var showYAxisSection by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedChartType) {
                    ChartType.BAR_CHART -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Imputation")
                            Switch(
                                checked = enableImputation,
                                onCheckedChange = onEnableImputationChange
                            )
                        }
                    }
                    ChartType.AVERAGE_BAR_CHART -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Error Display Type", style = MaterialTheme.typography.labelMedium)
                            ExposedDropdownMenuBox(
                                expanded = showErrorTypeDropdown,
                                onExpandedChange = { showErrorTypeDropdown = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = averageBarErrorType,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = showErrorTypeDropdown,
                                    onDismissRequest = { showErrorTypeDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Standard Error") },
                                        onClick = {
                                            onAverageBarErrorTypeChange("Standard Error")
                                            showErrorTypeDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Standard Deviation") },
                                        onClick = {
                                            onAverageBarErrorTypeChange("Standard Deviation")
                                            showErrorTypeDropdown = false
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show data points")
                                Switch(
                                    checked = showAverageIndividualPoints,
                                    onCheckedChange = onShowAverageIndividualPointsChange
                                )
                            }
                        }
                    }
                    ChartType.VIOLIN_PLOT -> {
                        Text(
                            text = "No quick settings available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showYAxisSection = !showYAxisSection }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Settings, "Y-Axis Limits")
                            Text("Y-Axis Limits (This Item)")
                        }
                        Icon(
                            if (showYAxisSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                if (showYAxisSection) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Set custom Y-axis limits for this protein only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text("Bar Chart", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = barChartMin,
                                    onValueChange = onBarChartMinChange,
                                    label = { Text("Min Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = barChartMax,
                                    onValueChange = onBarChartMaxChange,
                                    label = { Text("Max Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }

                            Text("Average Bar Chart", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = avgBarChartMin,
                                    onValueChange = onAvgBarChartMinChange,
                                    label = { Text("Min Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = avgBarChartMax,
                                    onValueChange = onAvgBarChartMaxChange,
                                    label = { Text("Max Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }

                            Text("Violin Plot", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = violinPlotMin,
                                    onValueChange = onViolinPlotMinChange,
                                    label = { Text("Min Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = violinPlotMax,
                                    onValueChange = onViolinPlotMaxChange,
                                    label = { Text("Max Y") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onSaveLimits()
                                        showYAxisSection = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Save, "Save")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onClearLimits()
                                        showYAxisSection = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Clear, "Clear")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ProteinInfoDialog(
    proteinId: String,
    geneName: String?,
    curtainData: info.proteo.curtain.domain.model.CurtainData?,
    viewModel: ProteinChartViewModel,
    onDismiss: () -> Unit
) {
    val foldChange by viewModel.foldChange.collectAsState()
    val pValue by viewModel.pValue.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protein Information") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (geneName != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Gene Name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = geneName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Protein ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = proteinId,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                curtainData?.settings?.let { settings ->
                    if (settings.volcanoConditionLabels.leftCondition.isNotEmpty() &&
                        settings.volcanoConditionLabels.rightCondition.isNotEmpty()) {
                        Divider()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Comparison",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${settings.volcanoConditionLabels.leftCondition} vs ${settings.volcanoConditionLabels.rightCondition}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                foldChange?.let { fc ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Fold Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.4f", fc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                pValue?.let { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "P-value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.4e", p),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

