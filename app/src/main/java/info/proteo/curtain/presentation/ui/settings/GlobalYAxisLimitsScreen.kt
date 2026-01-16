package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalYAxisLimitsScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var barChartMinEnabled by remember { mutableStateOf(false) }
    var barChartMaxEnabled by remember { mutableStateOf(false) }
    var barChartMin by remember { mutableStateOf("") }
    var barChartMax by remember { mutableStateOf("") }

    var avgBarChartMinEnabled by remember { mutableStateOf(false) }
    var avgBarChartMaxEnabled by remember { mutableStateOf(false) }
    var avgBarChartMin by remember { mutableStateOf("") }
    var avgBarChartMax by remember { mutableStateOf("") }

    var violinPlotMinEnabled by remember { mutableStateOf(false) }
    var violinPlotMaxEnabled by remember { mutableStateOf(false) }
    var violinPlotMin by remember { mutableStateOf("") }
    var violinPlotMax by remember { mutableStateOf("") }

    LaunchedEffect(currentData) {
        currentData.settings.chartYAxisLimits["barChart"]?.let { limits ->
            limits.min?.let {
                barChartMinEnabled = true
                barChartMin = it.toString()
            }
            limits.max?.let {
                barChartMaxEnabled = true
                barChartMax = it.toString()
            }
        }

        currentData.settings.chartYAxisLimits["averageBarChart"]?.let { limits ->
            limits.min?.let {
                avgBarChartMinEnabled = true
                avgBarChartMin = it.toString()
            }
            limits.max?.let {
                avgBarChartMaxEnabled = true
                avgBarChartMax = it.toString()
            }
        }

        currentData.settings.chartYAxisLimits["violinPlot"]?.let { limits ->
            limits.min?.let {
                violinPlotMinEnabled = true
                violinPlotMin = it.toString()
            }
            limits.max?.let {
                violinPlotMaxEnabled = true
                violinPlotMax = it.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Y-Axis Limits") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedLimits = mutableMapOf<String, info.proteo.curtain.domain.model.ChartYAxisLimits>()

                        updatedLimits["barChart"] = info.proteo.curtain.domain.model.ChartYAxisLimits(
                            min = if (barChartMinEnabled) barChartMin.toDoubleOrNull() else null,
                            max = if (barChartMaxEnabled) barChartMax.toDoubleOrNull() else null
                        )

                        updatedLimits["averageBarChart"] = info.proteo.curtain.domain.model.ChartYAxisLimits(
                            min = if (avgBarChartMinEnabled) avgBarChartMin.toDoubleOrNull() else null,
                            max = if (avgBarChartMaxEnabled) avgBarChartMax.toDoubleOrNull() else null
                        )

                        updatedLimits["violinPlot"] = info.proteo.curtain.domain.model.ChartYAxisLimits(
                            min = if (violinPlotMinEnabled) violinPlotMin.toDoubleOrNull() else null,
                            max = if (violinPlotMaxEnabled) violinPlotMax.toDoubleOrNull() else null
                        )

                        val updatedSettings = currentData.settings.copy(chartYAxisLimits = updatedLimits)
                        viewModel.updateSettings(updatedSettings)
                        navController.navigateUp()
                    }) {
                        Text("Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Global Y-Axis Limits",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Set consistent Y-axis ranges for all protein charts of each type. Leave fields disabled for automatic scaling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Bar Chart", style = MaterialTheme.typography.titleMedium)
                    ChartLimitInputs(
                        minEnabled = barChartMinEnabled,
                        onMinEnabledChange = { barChartMinEnabled = it },
                        maxEnabled = barChartMaxEnabled,
                        onMaxEnabledChange = { barChartMaxEnabled = it },
                        minValue = barChartMin,
                        onMinValueChange = { barChartMin = it },
                        maxValue = barChartMax,
                        onMaxValueChange = { barChartMax = it },
                        chartTypeName = "Bar Chart"
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Average Bar Chart", style = MaterialTheme.typography.titleMedium)
                    ChartLimitInputs(
                        minEnabled = avgBarChartMinEnabled,
                        onMinEnabledChange = { avgBarChartMinEnabled = it },
                        maxEnabled = avgBarChartMaxEnabled,
                        onMaxEnabledChange = { avgBarChartMaxEnabled = it },
                        minValue = avgBarChartMin,
                        onMinValueChange = { avgBarChartMin = it },
                        maxValue = avgBarChartMax,
                        onMaxValueChange = { avgBarChartMax = it },
                        chartTypeName = "Average Bar Chart"
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Violin Plot", style = MaterialTheme.typography.titleMedium)
                    ChartLimitInputs(
                        minEnabled = violinPlotMinEnabled,
                        onMinEnabledChange = { violinPlotMinEnabled = it },
                        maxEnabled = violinPlotMaxEnabled,
                        onMaxEnabledChange = { violinPlotMaxEnabled = it },
                        minValue = violinPlotMin,
                        onMinValueChange = { violinPlotMin = it },
                        maxValue = violinPlotMax,
                        onMaxValueChange = { violinPlotMax = it },
                        chartTypeName = "Violin Plot"
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    barChartMinEnabled = false
                    barChartMaxEnabled = false
                    barChartMin = ""
                    barChartMax = ""
                    avgBarChartMinEnabled = false
                    avgBarChartMaxEnabled = false
                    avgBarChartMin = ""
                    avgBarChartMax = ""
                    violinPlotMinEnabled = false
                    violinPlotMaxEnabled = false
                    violinPlotMin = ""
                    violinPlotMax = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset All to Auto")
            }
        }
    }
}

@Composable
private fun ChartLimitInputs(
    minEnabled: Boolean,
    onMinEnabledChange: (Boolean) -> Unit,
    maxEnabled: Boolean,
    onMaxEnabledChange: (Boolean) -> Unit,
    minValue: String,
    onMinValueChange: (String) -> Unit,
    maxValue: String,
    onMaxValueChange: (String) -> Unit,
    chartTypeName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Minimum", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = minEnabled,
                    onCheckedChange = onMinEnabledChange
                )
            }

            if (minEnabled) {
                OutlinedTextField(
                    value = minValue,
                    onValueChange = onMinValueChange,
                    label = { Text("Minimum Y value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Maximum", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = maxEnabled,
                    onCheckedChange = onMaxEnabledChange
                )
            }

            if (maxEnabled) {
                OutlinedTextField(
                    value = maxValue,
                    onValueChange = onMaxValueChange,
                    label = { Text("Maximum Y value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (minEnabled || maxEnabled) Icons.Default.Info else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (minEnabled || maxEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (minEnabled || maxEnabled) "All ${chartTypeName}s will use these limits" else "Using automatic scaling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
