package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.domain.model.SettingsVariant
import info.proteo.curtain.domain.service.SettingsVariantManager
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsVariantScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val variantManager = remember { SettingsVariantManager.getInstance(context) }
    val savedVariants by variantManager.savedVariants.collectAsState()
    val curtainData by viewModel.curtainData.collectAsState()
    val currentData = curtainData ?: return
    val scope = rememberCoroutineScope()

    var showingSaveDialog by remember { mutableStateOf(false) }
    var showingLoadDialog by remember { mutableStateOf(false) }
    var showingDeleteDialog by remember { mutableStateOf<SettingsVariant?>(null) }
    var isLoadingVariant by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var loadingMessage by remember { mutableStateOf("") }

    fun loadVariant(variant: SettingsVariant) {
        if (isLoadingVariant) return

        scope.launch {
            isLoadingVariant = true
            loadingProgress = 0f
            loadingMessage = "Preparing to load variant..."

            delay(100)
            loadingProgress = 0.3f
            loadingMessage = "Processing variant data..."

            val updatedSettings = variant.appliedTo(currentData.settings)
            val variantSelectedMap = variant.getStoredSelectedMap()
            val variantSelectionsName = variant.getStoredSelectionsName()

            loadingProgress = 0.7f
            loadingMessage = "Updating application state..."

            delay(300)
            loadingProgress = 1.0f
            loadingMessage = "Complete!"

            viewModel.loadSettingsVariant(updatedSettings, variantSelectedMap, variantSelectionsName)

            delay(500)
            isLoadingVariant = false
            loadingProgress = 0f
            loadingMessage = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings Variants") },
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
                    Text("Settings Variants", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Manage analysis parameter presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isLoadingVariant) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text(
                                    loadingMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { loadingProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showingSaveDialog = true },
                            enabled = !isLoadingVariant
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }

                        OutlinedButton(
                            onClick = { showingLoadDialog = true },
                            enabled = savedVariants.isNotEmpty() && !isLoadingVariant
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            "${savedVariants.size} saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Current Settings Preview", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("P-value Cutoff:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            String.format("%.3f", currentData.settings.pCutoff),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Log2FC Cutoff:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            String.format("%.2f", currentData.settings.log2FCCutoff),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selection Groups:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${currentData.selectionsName?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (savedVariants.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Saved Variants", style = MaterialTheme.typography.titleMedium)

                        variantManager.sortedVariants.forEach { variant ->
                            VariantRowView(
                                variant = variant,
                                onLoad = { loadVariant(variant) },
                                onDelete = { showingDeleteDialog = variant },
                                isLoading = isLoadingVariant
                            )

                            if (variant != variantManager.sortedVariants.last()) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showingSaveDialog) {
        SaveVariantDialog(
            currentSettings = currentData.settings,
            selectedMap = currentData.selectedMap,
            selectionsName = currentData.selectionsName,
            onDismiss = { showingSaveDialog = false },
            onSave = { variant ->
                variantManager.saveVariant(variant)
                showingSaveDialog = false
            }
        )
    }

    if (showingLoadDialog) {
        LoadVariantDialog(
            variants = savedVariants,
            onDismiss = { showingLoadDialog = false },
            onLoad = { variant ->
                loadVariant(variant)
                showingLoadDialog = false
            }
        )
    }

    showingDeleteDialog?.let { variant ->
        AlertDialog(
            onDismissRequest = { showingDeleteDialog = null },
            title = { Text("Delete Variant") },
            text = { Text("Are you sure you want to delete '${variant.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        variantManager.deleteVariant(variant)
                        showingDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VariantRowView(
    variant: SettingsVariant,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    isLoading: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(variant.name, style = MaterialTheme.typography.labelLarge)

            if (variant.description.isNotEmpty()) {
                Text(
                    variant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "p: ${String.format("%.3f", variant.pCutoff)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "FC: ${String.format("%.2f", variant.log2FCCutoff)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Modified: ${dateFormat.format(Date(variant.dateModified))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalButton(
                onClick = onLoad,
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load", style = MaterialTheme.typography.labelSmall)
            }

            IconButton(onClick = onDelete, enabled = !isLoading) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SaveVariantDialog(
    currentSettings: info.proteo.curtain.domain.model.CurtainSettings,
    selectedMap: Map<String, Map<String, Boolean>>?,
    selectionsName: List<String>?,
    onDismiss: () -> Unit,
    onSave: (SettingsVariant) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Settings Variant") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Divider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Current Settings Preview:", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "p: ${String.format("%.3f", currentSettings.pCutoff)}, FC: ${String.format("%.2f", currentSettings.log2FCCutoff)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Selection Groups: ${selectionsName?.size ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val variant = SettingsVariant(
                        name = name.trim(),
                        description = description.trim(),
                        settings = currentSettings,
                        selectedMap = selectedMap,
                        selectionsName = selectionsName
                    )
                    onSave(variant)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadVariantDialog(
    variants: List<SettingsVariant>,
    onDismiss: () -> Unit,
    onLoad: (SettingsVariant) -> Unit
) {
    var selectedVariant by remember { mutableStateOf<SettingsVariant?>(null) }
    val sortedVariants = remember(variants) { variants.sortedByDescending { it.dateModified } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Settings Variant") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sortedVariants.isEmpty()) {
                    Text("No saved variants available", style = MaterialTheme.typography.bodySmall)
                } else {
                    sortedVariants.forEach { variant ->
                        VariantLoadRow(
                            variant = variant,
                            isSelected = selectedVariant == variant,
                            onSelect = { selectedVariant = variant }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedVariant?.let { onLoad(it) }
                },
                enabled = selectedVariant != null
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VariantLoadRow(
    variant: SettingsVariant,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        onClick = onSelect,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(variant.name, style = MaterialTheme.typography.labelLarge)

            if (variant.description.isNotEmpty()) {
                Text(
                    variant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "p: ${String.format("%.3f", variant.pCutoff)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "FC: ${String.format("%.2f", variant.log2FCCutoff)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Modified: ${dateFormat.format(Date(variant.dateModified))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
