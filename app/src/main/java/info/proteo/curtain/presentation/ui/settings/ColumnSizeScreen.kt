package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnSizeScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var barChartColumnSize by remember { mutableStateOf("0") }
    var averageBarChartColumnSize by remember { mutableStateOf("0") }
    var violinPlotColumnSize by remember { mutableStateOf("0") }

    LaunchedEffect(currentData) {
        barChartColumnSize = (currentData.settings.columnSize["barChart"] ?: 0).toString()
        averageBarChartColumnSize = (currentData.settings.columnSize["averageBarChart"] ?: 0).toString()
        violinPlotColumnSize = (currentData.settings.columnSize["violinPlot"] ?: 0).toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Column Size") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedColumnSize = mutableMapOf<String, Int>()

                        updatedColumnSize["barChart"] = barChartColumnSize.toIntOrNull() ?: 0
                        updatedColumnSize["averageBarChart"] = averageBarChartColumnSize.toIntOrNull() ?: 0
                        updatedColumnSize["violinPlot"] = violinPlotColumnSize.toIntOrNull() ?: 0

                        val updatedSettings = currentData.settings.copy(columnSize = updatedColumnSize)
                        viewModel.updateSettings(updatedSettings)
                        navController.navigateUp()
                    }) {
                        Text("Save")
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
                            "Column size controls the width of individual bars/columns in charts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Formula: width = marginLeft + marginRight + (columnSize × itemCount)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Set to 0 for auto width (default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Bar Chart", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Column Width", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Width per sample in pixels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = barChartColumnSize,
                            onValueChange = { barChartColumnSize = it },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End)
                        )
                    }

                    val barSize = barChartColumnSize.toIntOrNull() ?: 0
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Status:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (barSize > 0) "$barSize pixels per sample" else "Auto width",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (barSize > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Average Bar Chart", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Column Width", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Width per condition in pixels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = averageBarChartColumnSize,
                            onValueChange = { averageBarChartColumnSize = it },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End)
                        )
                    }

                    val avgSize = averageBarChartColumnSize.toIntOrNull() ?: 0
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Status:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (avgSize > 0) "$avgSize pixels per condition" else "Auto width",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (avgSize > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Violin Plot", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Plot Width", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Width per condition in pixels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = violinPlotColumnSize,
                            onValueChange = { violinPlotColumnSize = it },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End)
                        )
                    }

                    val violinSize = violinPlotColumnSize.toIntOrNull() ?: 0
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Status:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (violinSize > 0) "$violinSize pixels per condition" else "Auto width",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (violinSize > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    barChartColumnSize = "0"
                    averageBarChartColumnSize = "0"
                    violinPlotColumnSize = "0"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset All to Auto Width")
            }
        }
    }
}
