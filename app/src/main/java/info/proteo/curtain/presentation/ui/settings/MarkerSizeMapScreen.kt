package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
fun MarkerSizeMapScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var markerSizes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val availableGroups = remember(currentData) {
        currentData.selectionsName?.sorted() ?: emptyList()
    }

    LaunchedEffect(currentData) {
        val sizes = mutableMapOf<String, String>()
        availableGroups.forEach { groupName ->
            val value = currentData.settings.markerSizeMap[groupName]
            sizes[groupName] = when (value) {
                is Int -> value.toString()
                is Double -> value.toInt().toString()
                else -> ""
            }
        }
        markerSizes = sizes
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marker Sizes") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedMarkerSizeMap = mutableMapOf<String, Any>()

                        availableGroups.forEach { groupName ->
                            val sizeStr = markerSizes[groupName] ?: ""
                            val size = sizeStr.toIntOrNull()
                            if (size != null && size > 0) {
                                updatedMarkerSizeMap[groupName] = size
                            }
                        }

                        val updatedSettings = currentData.settings.copy(markerSizeMap = updatedMarkerSizeMap)
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
                            "Configure marker sizes for individual selection groups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Groups without custom sizes use the global marker size setting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Default Marker Size", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Global Default", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Used when no custom size is set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "${currentData.settings.scatterPlotMarkerSize.toInt()} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (availableGroups.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Selection Groups", style = MaterialTheme.typography.titleMedium)

                        availableGroups.forEach { groupName ->
                            val sizeStr = markerSizes[groupName] ?: ""
                            val size = sizeStr.toIntOrNull()

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(groupName, style = MaterialTheme.typography.labelLarge)

                                        if (size != null && size > 0) {
                                            Text(
                                                "Custom: $size px",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        } else {
                                            Text(
                                                "Using default (${currentData.settings.scatterPlotMarkerSize.toInt()} px)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = sizeStr,
                                            onValueChange = { newValue ->
                                                markerSizes = markerSizes.toMutableMap().apply {
                                                    this[groupName] = newValue
                                                }
                                            },
                                            placeholder = { Text("Default") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(100.dp),
                                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                                            singleLine = true
                                        )

                                        if (sizeStr.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    markerSizes = markerSizes.toMutableMap().apply {
                                                        this[groupName] = ""
                                                    }
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (groupName != availableGroups.last()) {
                                    Divider()
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        markerSizes = availableGroups.associateWith { "" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Custom Sizes")
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "No selection groups found in current data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}
