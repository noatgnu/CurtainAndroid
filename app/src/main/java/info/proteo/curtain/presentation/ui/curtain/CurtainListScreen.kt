package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.presentation.ui.components.CurtainListMode
import info.proteo.curtain.presentation.ui.components.CurtainListPanel
import info.proteo.curtain.presentation.ui.dialogs.AddCurtainDialog
import info.proteo.curtain.presentation.viewmodel.CurtainViewModel

/**
 * Curtain list screen with search and item actions.
 * Matches iOS CurtainListView functionality.
 *
 * Features:
 * - Display curtains added by user (from local database)
 * - Search by description or link ID
 * - Download curtain data with progress
 * - Pin/unpin curtains
 * - Delete curtains
 * - Add curtains via QR code scanner
 *
 * @param navController Navigation controller
 * @param viewModel CurtainViewModel instance (injected by Hilt)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainListScreen(
    navController: NavHostController,
    viewModel: CurtainViewModel = hiltViewModel()
) {
    val curtains by viewModel.curtains.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingCollections by viewModel.isLoadingCollections.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val expandedCollectionIds by viewModel.expandedCollectionIds.collectAsState()
    val collectionSessions by viewModel.collectionSessions.collectAsState()
    val selectedSessionIds by viewModel.selectedSessionIds.collectAsState()
    val selectionModeCollectionId by viewModel.selectionModeCollectionId.collectAsState()
    val curtainTypeFilter by viewModel.curtainTypeFilter.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showEditDialog by remember { mutableStateOf(false) }
    var curtainToEdit by remember { mutableStateOf<CurtainEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var curtainToDelete by remember { mutableStateOf<CurtainEntity?>(null) }
    var showDeleteCollectionDialog by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<CurtainCollectionEntity?>(null) }
    var showAddCurtainDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Curtain Datasets") },
                    actions = {
                        IconButton(onClick = { showThemeSettings = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "Theme Settings")
                        }
                    },
                )
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(
                        onClick = { showAddMenu = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Dataset")
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scan QR Code") },
                            onClick = {
                                showAddMenu = false
                                navController.navigate("qr_scanner")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manual Entry") },
                            onClick = {
                                showAddMenu = false
                                showAddCurtainDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            CurtainListPanel(
                mode = CurtainListMode.MANAGEMENT,
                datasets = curtains,
                collections = collections,
                expandedCollectionIds = expandedCollectionIds,
                collectionSessions = collectionSessions,
                isLoading = isLoading,
                isLoadingCollections = isLoadingCollections,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onDatasetClick = { curtain ->
                    if (curtain.file != null) {
                        navController.navigate("curtain_details/${curtain.linkId}")
                    }
                },
                downloadProgress = downloadProgress,
                onDownload = { viewModel.downloadCurtain(it) },
                onTogglePin = { viewModel.togglePin(it) },
                onEdit = { curtain ->
                    curtainToEdit = curtain
                    showEditDialog = true
                },
                onRedownload = { viewModel.downloadCurtain(it) },
                onDeleteDataset = { curtain ->
                    curtainToDelete = curtain
                    showDeleteDialog = true
                },
                onToggleCollectionExpanded = { viewModel.toggleCollectionExpanded(it) },
                onRefreshCollection = { viewModel.refreshCollection(it) },
                onDeleteCollection = { collection ->
                    collectionToDelete = collection
                    showDeleteCollectionDialog = true
                },
                onSessionClick = { session, collection ->
                    viewModel.loadSessionFromCollection(session, collection)
                },
                selectionModeCollectionId = selectionModeCollectionId,
                selectedSessionIdsInCollection = selectedSessionIds,
                onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
                onExitSelectionMode = { viewModel.exitSelectionMode() },
                onToggleSessionSelectionInCollection = { collectionId, linkId ->
                    viewModel.toggleSessionSelection(collectionId, linkId)
                },
                onSelectAllInCollection = { viewModel.selectAllSessions(it) },
                onDeselectAllInCollection = { viewModel.deselectAllSessions(it) },
                onDownloadSelectedInCollection = { viewModel.downloadSelectedSessions(it) },
                onLoadExample = { viewModel.loadExampleCurtain() },
                onLoadPTMExample = { viewModel.loadExamplePTMCurtain() },
                onLoadBothExamples = { viewModel.loadBothExampleCurtains() },
                onLoadExampleCollection = { viewModel.loadExampleCollection() },
                curtainTypeFilter = curtainTypeFilter,
                onCurtainTypeFilterChange = { viewModel.updateCurtainTypeFilter(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }

        if (showEditDialog && curtainToEdit != null) {
            EditDescriptionDialog(
                currentDescription = curtainToEdit!!.dataDescription,
                onDismiss = { showEditDialog = false },
                onSave = { newDescription ->
                    viewModel.updateDescription(curtainToEdit!!.linkId, newDescription)
                    showEditDialog = false
                }
            )
        }

        if (showDeleteDialog && curtainToDelete != null) {
            DeleteConfirmationDialog(
                curtainDescription = curtainToDelete!!.sessionName?.takeIf { it.isNotBlank() } ?: curtainToDelete!!.dataDescription,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    viewModel.deleteCurtain(curtainToDelete!!)
                    showDeleteDialog = false
                }
            )
        }

        if (showDeleteCollectionDialog && collectionToDelete != null) {
            DeleteCollectionConfirmationDialog(
                collectionName = collectionToDelete!!.name,
                onDismiss = { showDeleteCollectionDialog = false },
                onConfirm = {
                    viewModel.deleteCollection(collectionToDelete!!.localId)
                    showDeleteCollectionDialog = false
                }
            )
        }

        if (showAddCurtainDialog) {
            AddCurtainDialog(
                onDismiss = { showAddCurtainDialog = false },
                onAdd = { linkId, apiUrl, frontendUrl, description ->
                    viewModel.loadCurtain(
                        linkId = linkId,
                        apiUrl = apiUrl,
                        frontendUrl = frontendUrl
                    )
                    showAddCurtainDialog = false
                }
            )
        }

        if (showThemeSettings) {
            info.proteo.curtain.presentation.ui.settings.ThemeSettingsScreen(
                onDismiss = { showThemeSettings = false }
            )
        }
    }

@Composable
private fun DeleteCollectionConfirmationDialog(
    collectionName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Collection") },
        text = {
            Text(
                "Are you sure you want to delete \"$collectionName\"? This will remove the collection from your local database.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
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
internal fun EmptyState(
    onLoadExample: () -> Unit,
    onLoadPTMExample: () -> Unit = {},
    onLoadBothExamples: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "No datasets found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Load an example dataset or add datasets via QR code scanning",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onLoadBothExamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Load Both Example Datasets")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLoadExample,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("TP Only")
                }

                OutlinedButton(
                    onClick = onLoadPTMExample,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("PTM Only")
                }
            }
        }
    }
}

@Composable
internal fun EditDescriptionDialog(
    currentDescription: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var description by remember { mutableStateOf(currentDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Description") },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(description) },
                enabled = description.isNotBlank()
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
internal fun DeleteConfirmationDialog(
    curtainDescription: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Dataset") },
        text = {
            Text(
                "Are you sure you want to delete \"$curtainDescription\"? This will also delete the downloaded data file.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
