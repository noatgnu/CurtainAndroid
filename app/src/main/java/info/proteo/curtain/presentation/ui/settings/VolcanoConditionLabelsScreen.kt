package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoConditionLabelsScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var enabled by remember { mutableStateOf(currentData.settings.volcanoConditionLabels.enabled) }
    var leftCondition by remember { mutableStateOf(currentData.settings.volcanoConditionLabels.leftCondition) }
    var rightCondition by remember { mutableStateOf(currentData.settings.volcanoConditionLabels.rightCondition) }
    var leftX by remember { mutableFloatStateOf(currentData.settings.volcanoConditionLabels.leftX.toFloat()) }
    var rightX by remember { mutableFloatStateOf(currentData.settings.volcanoConditionLabels.rightX.toFloat()) }
    var yPosition by remember { mutableFloatStateOf(currentData.settings.volcanoConditionLabels.yPosition.toFloat()) }
    var fontSize by remember { mutableIntStateOf(currentData.settings.volcanoConditionLabels.fontSize) }
    var fontColorHex by remember { mutableStateOf(currentData.settings.volcanoConditionLabels.fontColor) }

    val availableConditions = currentData.settings.conditionOrder

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Condition Labels") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedLabels = currentData.settings.volcanoConditionLabels.copy(
                            enabled = enabled,
                            leftCondition = leftCondition,
                            rightCondition = rightCondition,
                            leftX = leftX.toDouble(),
                            rightX = rightX.toDouble(),
                            yPosition = yPosition.toDouble(),
                            fontSize = fontSize,
                            fontColor = fontColorHex
                        )
                        val updatedSettings = currentData.settings.copy(volcanoConditionLabels = updatedLabels)
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
                        Text("Enable Condition Labels", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    if (enabled) {
                        Text(
                            "Show condition labels below the volcano plot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (enabled) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Conditions", style = MaterialTheme.typography.titleMedium)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Left Condition (Decrease)",
                                style = MaterialTheme.typography.labelLarge
                            )

                            if (availableConditions.isEmpty()) {
                                OutlinedTextField(
                                    value = leftCondition,
                                    onValueChange = { leftCondition = it },
                                    label = { Text("Enter condition name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                var expandedLeft by remember { mutableStateOf(false) }

                                ExposedDropdownMenuBox(
                                    expanded = expandedLeft,
                                    onExpandedChange = { expandedLeft = !expandedLeft }
                                ) {
                                    OutlinedTextField(
                                        value = leftCondition.ifEmpty { "None" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select condition") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLeft) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expandedLeft,
                                        onDismissRequest = { expandedLeft = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None") },
                                            onClick = {
                                                leftCondition = ""
                                                expandedLeft = false
                                            }
                                        )

                                        availableConditions.forEach { condition ->
                                            DropdownMenuItem(
                                                text = { Text(condition) },
                                                onClick = {
                                                    leftCondition = condition
                                                    expandedLeft = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = leftCondition,
                                    onValueChange = { leftCondition = it },
                                    label = { Text("Or enter custom name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Divider()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Right Condition (Increase)",
                                style = MaterialTheme.typography.labelLarge
                            )

                            if (availableConditions.isEmpty()) {
                                OutlinedTextField(
                                    value = rightCondition,
                                    onValueChange = { rightCondition = it },
                                    label = { Text("Enter condition name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                var expandedRight by remember { mutableStateOf(false) }

                                ExposedDropdownMenuBox(
                                    expanded = expandedRight,
                                    onExpandedChange = { expandedRight = !expandedRight }
                                ) {
                                    OutlinedTextField(
                                        value = rightCondition.ifEmpty { "None" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select condition") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRight) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expandedRight,
                                        onDismissRequest = { expandedRight = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None") },
                                            onClick = {
                                                rightCondition = ""
                                                expandedRight = false
                                            }
                                        )

                                        availableConditions.forEach { condition ->
                                            DropdownMenuItem(
                                                text = { Text(condition) },
                                                onClick = {
                                                    rightCondition = condition
                                                    expandedRight = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = rightCondition,
                                    onValueChange = { rightCondition = it },
                                    label = { Text("Or enter custom name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Label Positioning", style = MaterialTheme.typography.titleMedium)

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Left Label X Position")
                                Text("%.2f".format(leftX))
                            }
                            Slider(
                                value = leftX,
                                onValueChange = { leftX = it },
                                valueRange = 0f..1f,
                                steps = 19
                            )
                            Text(
                                "Horizontal position (0 = left edge, 1 = right edge)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Divider()

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Right Label X Position")
                                Text("%.2f".format(rightX))
                            }
                            Slider(
                                value = rightX,
                                onValueChange = { rightX = it },
                                valueRange = 0f..1f,
                                steps = 19
                            )
                            Text(
                                "Horizontal position (0 = left edge, 1 = right edge)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Divider()

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Vertical Position")
                                Text("%.2f".format(yPosition))
                            }
                            Slider(
                                value = yPosition,
                                onValueChange = { yPosition = it },
                                valueRange = -0.3f..0.3f,
                                steps = 59
                            )
                            Text(
                                "Vertical position (negative = below plot, positive = above plot)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    leftX = 0.25f
                                    rightX = 0.75f
                                    yPosition = -0.1f
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Reset to Defaults")
                            }

                            OutlinedButton(
                                onClick = {
                                    val legendY = currentData.settings.volcanoPlotLegendY ?: -0.1
                                    if (legendY < 0 && yPosition < 0 && kotlin.math.abs(legendY - yPosition) < 0.1) {
                                        yPosition = (legendY + 0.1).toFloat()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Auto-Adjust")
                            }
                        }
                    }
                }

                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Font Settings", style = MaterialTheme.typography.titleMedium)

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Font Size")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = { if (fontSize > 8) fontSize-- }) {
                                    Text("-")
                                }
                                Text("${fontSize}pt")
                                IconButton(onClick = { if (fontSize < 24) fontSize++ }) {
                                    Text("+")
                                }
                            }
                        }

                        Divider()

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Font Color")
                            OutlinedTextField(
                                value = fontColorHex,
                                onValueChange = { fontColorHex = it },
                                label = { Text("Hex Color") },
                                placeholder = { Text("#000000") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                            "The labels will appear below the volcano plot like this:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (leftCondition.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(leftX)
                                    ) {
                                        Text(
                                            leftCondition,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                if (rightCondition.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(1f - rightX)
                                    ) {
                                        Text(
                                            rightCondition,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
