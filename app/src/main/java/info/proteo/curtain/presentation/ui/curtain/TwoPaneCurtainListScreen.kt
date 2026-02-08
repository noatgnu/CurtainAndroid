package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.presentation.ui.components.CurtainListMode
import info.proteo.curtain.presentation.ui.components.CurtainListPanel
import info.proteo.curtain.presentation.ui.dialogs.AddCurtainDialog
import info.proteo.curtain.presentation.ui.settings.ThemeSettingsScreen
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel
import info.proteo.curtain.presentation.viewmodel.CurtainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoPaneCurtainListScreen(
    navController: NavController,
    onNavigateToQRScanner: () -> Unit,
    curtainViewModel: CurtainViewModel = hiltViewModel()
) {
    val curtains by curtainViewModel.curtains.collectAsState()
    val collections by curtainViewModel.collections.collectAsState()
    val isLoading by curtainViewModel.isLoading.collectAsState()
    val isLoadingCollections by curtainViewModel.isLoadingCollections.collectAsState()
    val error by curtainViewModel.error.collectAsState()
    val searchQuery by curtainViewModel.searchQuery.collectAsState()
    val downloadProgress by curtainViewModel.downloadProgress.collectAsState()
    val expandedCollectionIds by curtainViewModel.expandedCollectionIds.collectAsState()
    val collectionSessions by curtainViewModel.collectionSessions.collectAsState()
    val selectedSessionIds by curtainViewModel.selectedSessionIds.collectAsState()
    val selectionModeCollectionId by curtainViewModel.selectionModeCollectionId.collectAsState()
    val curtainTypeFilter by curtainViewModel.curtainTypeFilter.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCurtain by remember { mutableStateOf<CurtainEntity?>(null) }

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
            curtainViewModel.clearError()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Curtain Datasets") },
                    actions = {
                        IconButton(onClick = { showThemeSettings = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "Theme Settings")
                        }
                    }
                )

                CurtainListPanel(
                    mode = CurtainListMode.MANAGEMENT,
                    datasets = curtains,
                    collections = collections,
                    expandedCollectionIds = expandedCollectionIds,
                    collectionSessions = collectionSessions,
                    isLoading = isLoading,
                    isLoadingCollections = isLoadingCollections,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { curtainViewModel.updateSearchQuery(it) },
                    selectedDatasetLinkId = selectedCurtain?.linkId,
                    onDatasetClick = { curtain ->
                        if (curtain.file != null) {
                            selectedCurtain = curtain
                        } else {
                            curtainViewModel.downloadCurtain(curtain)
                        }
                    },
                    downloadProgress = downloadProgress,
                    onDownload = { curtainViewModel.downloadCurtain(it) },
                    onTogglePin = { curtainViewModel.togglePin(it) },
                    onEdit = { curtain ->
                        curtainToEdit = curtain
                        showEditDialog = true
                    },
                    onRedownload = { curtainViewModel.downloadCurtain(it) },
                    onDeleteDataset = { curtain ->
                        curtainToDelete = curtain
                        showDeleteDialog = true
                    },
                    onToggleCollectionExpanded = { curtainViewModel.toggleCollectionExpanded(it) },
                    onRefreshCollection = { curtainViewModel.refreshCollection(it) },
                    onDeleteCollection = { collection ->
                        collectionToDelete = collection
                        showDeleteCollectionDialog = true
                    },
                    onSessionClick = { session, collection ->
                        curtainViewModel.loadSessionFromCollection(session, collection)
                    },
                    selectionModeCollectionId = selectionModeCollectionId,
                    selectedSessionIdsInCollection = selectedSessionIds,
                    onEnterSelectionMode = { curtainViewModel.enterSelectionMode(it) },
                    onExitSelectionMode = { curtainViewModel.exitSelectionMode() },
                    onToggleSessionSelectionInCollection = { collectionId, linkId ->
                        curtainViewModel.toggleSessionSelection(collectionId, linkId)
                    },
                    onSelectAllInCollection = { curtainViewModel.selectAllSessions(it) },
                    onDeselectAllInCollection = { curtainViewModel.deselectAllSessions(it) },
                    onDownloadSelectedInCollection = { curtainViewModel.downloadSelectedSessions(it) },
                    onLoadExample = { curtainViewModel.loadExampleCurtain() },
                    onLoadPTMExample = { curtainViewModel.loadExamplePTMCurtain() },
                    onLoadBothExamples = { curtainViewModel.loadBothExampleCurtains() },
                    onLoadExampleCollection = { curtainViewModel.loadExampleCollection() },
                    curtainTypeFilter = curtainTypeFilter,
                    onCurtainTypeFilterChange = { curtainViewModel.updateCurtainTypeFilter(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
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
                            onNavigateToQRScanner()
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (selectedCurtain != null) {
                CurtainDetailsSharedContent(
                    linkId = selectedCurtain!!.linkId,
                    navController = navController,
                    onBack = { selectedCurtain = null },
                    useCloseIcon = true
                )
            } else {
                EmptyDetailView()
            }
        }
    }

    if (showEditDialog && curtainToEdit != null) {
        EditDescriptionDialog(
            currentDescription = curtainToEdit!!.dataDescription,
            onDismiss = { showEditDialog = false },
            onSave = { newDescription ->
                curtainViewModel.updateDescription(curtainToEdit!!.linkId, newDescription)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog && curtainToDelete != null) {
        DeleteConfirmationDialog(
            curtainDescription = curtainToDelete!!.sessionName?.takeIf { it.isNotBlank() } ?: curtainToDelete!!.dataDescription,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                curtainViewModel.deleteCurtain(curtainToDelete!!)
                if (selectedCurtain?.linkId == curtainToDelete!!.linkId) {
                    selectedCurtain = null
                }
                showDeleteDialog = false
            }
        )
    }

    if (showDeleteCollectionDialog && collectionToDelete != null) {
        TabletDeleteCollectionDialog(
            collectionName = collectionToDelete!!.name,
            onDismiss = { showDeleteCollectionDialog = false },
            onConfirm = {
                curtainViewModel.deleteCollection(collectionToDelete!!.localId)
                showDeleteCollectionDialog = false
            }
        )
    }

    if (showAddCurtainDialog) {
        AddCurtainDialog(
            onDismiss = { showAddCurtainDialog = false },
            onAdd = { linkId, apiUrl, frontendUrl, description ->
                curtainViewModel.loadCurtain(
                    linkId = linkId,
                    apiUrl = apiUrl,
                    frontendUrl = frontendUrl
                )
                showAddCurtainDialog = false
            }
        )
    }

    if (showThemeSettings) {
        ThemeSettingsScreen(onDismiss = { showThemeSettings = false })
    }
}

@Composable
private fun EmptyDetailView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Select a Dataset",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose a dataset from the list to view its details",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TabletDeleteCollectionDialog(
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
                "Are you sure you want to delete \"$collectionName\"?",
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
