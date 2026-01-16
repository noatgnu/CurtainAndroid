package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInfoScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings Information") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Analysis Parameters", style = MaterialTheme.typography.titleMedium)

                    SettingsInfoRow(
                        label = "P-value Cutoff",
                        value = String.format("%.3f", currentData.settings.pCutoff)
                    )

                    SettingsInfoRow(
                        label = "Log2FC Cutoff",
                        value = String.format("%.2f", currentData.settings.log2FCCutoff)
                    )

                    SettingsInfoRow(
                        label = "Academic Mode",
                        value = if (currentData.settings.academic) "Enabled" else "Disabled"
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Data Processing", style = MaterialTheme.typography.titleMedium)

                    SettingsInfoRow(
                        label = "UniProt Integration",
                        value = if (currentData.settings.fetchUniprot) "Enabled" else "Disabled"
                    )

                    SettingsInfoRow(
                        label = "Imputation",
                        value = if (currentData.settings.enableImputation) "Enabled" else "Disabled"
                    )

                    SettingsInfoRow(
                        label = "View Peptide Count",
                        value = if (currentData.settings.viewPeptideCount) "Enabled" else "Disabled"
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Display Settings", style = MaterialTheme.typography.titleMedium)

                    SettingsInfoRow(
                        label = "Plot Font Family",
                        value = currentData.settings.plotFontFamily
                    )

                    SettingsInfoRow(
                        label = "Scatter Plot Marker Size",
                        value = currentData.settings.scatterPlotMarkerSize.toString()
                    )

                    SettingsInfoRow(
                        label = "Background Color Grey",
                        value = if (currentData.settings.backGroundColorGrey) "Enabled" else "Disabled"
                    )

                    SettingsInfoRow(
                        label = "Volcano Plot Title",
                        value = currentData.settings.volcanoPlotTitle.ifEmpty { "None" }
                    )

                    SettingsInfoRow(
                        label = "Volcano Plot Grid",
                        value = buildString {
                            val xGrid = currentData.settings.volcanoPlotGrid["x"] ?: false
                            val yGrid = currentData.settings.volcanoPlotGrid["y"] ?: false
                            when {
                                xGrid && yGrid -> append("X & Y Enabled")
                                xGrid -> append("X Only")
                                yGrid -> append("Y Only")
                                else -> append("Disabled")
                            }
                        }
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Chart Dimensions", style = MaterialTheme.typography.titleMedium)

                    SettingsInfoRow(
                        label = "Volcano Plot Width",
                        value = "${currentData.settings.volcanoPlotDimension.width}px"
                    )

                    SettingsInfoRow(
                        label = "Volcano Plot Height",
                        value = "${currentData.settings.volcanoPlotDimension.height}px"
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Selection Groups", style = MaterialTheme.typography.titleMedium)

                    SettingsInfoRow(
                        label = "Total Groups",
                        value = "${currentData.selectionsName?.size ?: 0}"
                    )

                    if (!currentData.selectionsName.isNullOrEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Groups:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            currentData.selectionsName.forEach { groupName ->
                                Text(
                                    "• $groupName",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
