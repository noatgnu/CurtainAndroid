package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoTextColumnScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return

    var selectedColumn by remember { mutableStateOf(currentData.settings.customVolcanoTextCol) }
    var availableColumns by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(currentData) {
        val raw = currentData.raw
        if (!raw.isNullOrEmpty()) {
            val lines = raw.lines().filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                availableColumns = lines[0]
                    .split("\t")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .sorted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hover Text Column") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val updatedSettings = currentData.settings.copy(customVolcanoTextCol = selectedColumn)
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
                    Text("Customize Hover Text", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "By default, volcano plot points show gene name and ID on hover. You can override this with any column from your data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Text Column", style = MaterialTheme.typography.titleMedium)

                    if (availableColumns.isEmpty()) {
                        Text(
                            "No columns available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedColumn.ifEmpty { "Default (Gene Name + ID)" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Column") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (Gene Name + ID)") },
                                    onClick = {
                                        selectedColumn = ""
                                        expanded = false
                                    }
                                )

                                availableColumns.forEach { column ->
                                    DropdownMenuItem(
                                        text = { Text(column) },
                                        onClick = {
                                            selectedColumn = column
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedColumn.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Hover text will show values from '$selectedColumn'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                    Text("Examples", style = MaterialTheme.typography.titleMedium)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExampleRow(
                            title = "Default",
                            description = "Shows: GeneSymbol(P12345)"
                        )

                        if (selectedColumn.isNotEmpty()) {
                            ExampleRow(
                                title = "Custom ($selectedColumn)",
                                description = "Shows: Value from '$selectedColumn' column"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
