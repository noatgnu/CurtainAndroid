package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarChartConditionBracketScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var enabled by remember { mutableStateOf(currentData.settings.barChartConditionBracket.showBracket) }
    var bracketColorHex by remember { mutableStateOf("#000000") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Condition Bracket") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedBracket = currentData.settings.barChartConditionBracket.copy(
                            showBracket = enabled
                        )
                        val updatedSettings = currentData.settings.copy(barChartConditionBracket = updatedBracket)
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
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Condition Bracket", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    if (enabled) {
                        Text(
                            "Draws a bracket above bar charts connecting the two conditions selected in volcano plot labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (enabled) {
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
                        Text(
                            "The bracket connects the left and right conditions from the Volcano Condition Labels settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Preview", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Bracket Preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val leftX = width * 0.25f
                                val rightX = width * 0.75f
                                val baseY = size.height - 60f
                                val topY = baseY - 40f

                                drawLine(
                                    color = Color.Black,
                                    start = Offset(leftX, baseY),
                                    end = Offset(leftX, topY),
                                    strokeWidth = 2f
                                )

                                drawLine(
                                    color = Color.Black,
                                    start = Offset(leftX, topY),
                                    end = Offset(rightX, topY),
                                    strokeWidth = 2f
                                )

                                drawLine(
                                    color = Color.Black,
                                    start = Offset(rightX, topY),
                                    end = Offset(rightX, baseY),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
                }

                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Current Volcano Conditions", style = MaterialTheme.typography.titleMedium)

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Left Condition:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currentData.settings.volcanoConditionLabels.leftCondition.ifEmpty { "Not set" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentData.settings.volcanoConditionLabels.leftCondition.isEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Right Condition:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currentData.settings.volcanoConditionLabels.rightCondition.ifEmpty { "Not set" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentData.settings.volcanoConditionLabels.rightCondition.isEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentData.settings.volcanoConditionLabels.leftCondition.isEmpty() ||
                            currentData.settings.volcanoConditionLabels.rightCondition.isEmpty()) {
                            Text(
                                "Set conditions in Volcano Condition Labels first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
