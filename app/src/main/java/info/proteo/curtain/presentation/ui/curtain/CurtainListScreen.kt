package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.presentation.ui.dialogs.AddCurtainDialog
import info.proteo.curtain.presentation.viewmodel.CurtainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var curtainToEdit by remember { mutableStateOf<CurtainEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var curtainToDelete by remember { mutableStateOf<CurtainEntity?>(null) }
    var showDeleteCollectionDialog by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<CurtainCollectionEntity?>(null) }
    var showAddCurtainDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }

    val tabTitles = listOf("Sessions", "Collections")

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search datasets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true
                )

                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> SessionsTab(
                        curtains = curtains,
                        isLoading = isLoading,
                        downloadProgress = downloadProgress,
                        onDownload = { viewModel.downloadCurtain(it) },
                        onTogglePin = { viewModel.togglePin(it) },
                        onEdit = { curtain ->
                            curtainToEdit = curtain
                            showEditDialog = true
                        },
                        onRedownload = { viewModel.downloadCurtain(it) },
                        onDelete = { curtain ->
                            curtainToDelete = curtain
                            showDeleteDialog = true
                        },
                        onClick = { curtain ->
                            if (curtain.file != null) {
                                navController.navigate("curtain_details/${curtain.linkId}")
                            }
                        },
                        onLoadExample = { viewModel.loadExampleCurtain() }
                    )
                    1 -> CollectionsTab(
                        collections = collections,
                        isLoading = isLoadingCollections,
                        expandedCollectionIds = expandedCollectionIds,
                        collectionSessions = collectionSessions,
                        onToggleExpand = { viewModel.toggleCollectionExpanded(it) },
                        onRefresh = { viewModel.refreshCollection(it) },
                        onDelete = { collection ->
                            collectionToDelete = collection
                            showDeleteCollectionDialog = true
                        },
                        onSessionClick = { session, collection ->
                            viewModel.loadSessionFromCollection(session, collection)
                        },
                        onLoadExample = { viewModel.loadExampleCollection() }
                    )
                }
            }
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
                curtainDescription = curtainToDelete!!.dataDescription,
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
private fun SessionsTab(
    curtains: List<CurtainEntity>,
    isLoading: Boolean,
    downloadProgress: Map<String, Int>,
    onDownload: (CurtainEntity) -> Unit,
    onTogglePin: (CurtainEntity) -> Unit,
    onEdit: (CurtainEntity) -> Unit,
    onRedownload: (CurtainEntity) -> Unit,
    onDelete: (CurtainEntity) -> Unit,
    onClick: (CurtainEntity) -> Unit,
    onLoadExample: () -> Unit
) {
    when {
        isLoading && curtains.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        curtains.isEmpty() -> {
            EmptyState(onLoadExample = onLoadExample)
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(curtains, key = { it.linkId }) { curtain ->
                    CurtainItem(
                        curtain = curtain,
                        downloadProgress = downloadProgress[curtain.linkId],
                        onDownload = { onDownload(curtain) },
                        onTogglePin = { onTogglePin(curtain) },
                        onEdit = { onEdit(curtain) },
                        onRedownload = { onRedownload(curtain) },
                        onDelete = { onDelete(curtain) },
                        onClick = { onClick(curtain) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsTab(
    collections: List<CurtainCollectionEntity>,
    isLoading: Boolean,
    expandedCollectionIds: Set<Long>,
    collectionSessions: Map<Long, List<CollectionSessionEntity>>,
    onToggleExpand: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (CurtainCollectionEntity) -> Unit,
    onSessionClick: (CollectionSessionEntity, CurtainCollectionEntity) -> Unit,
    onLoadExample: () -> Unit
) {
    when {
        isLoading && collections.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        collections.isEmpty() -> {
            CollectionsEmptyState(onLoadExample = onLoadExample)
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(collections, key = { it.localId }) { collection ->
                    CollectionItem(
                        collection = collection,
                        isExpanded = expandedCollectionIds.contains(collection.localId),
                        sessions = collectionSessions[collection.localId] ?: emptyList(),
                        onToggleExpand = { onToggleExpand(collection.localId) },
                        onRefresh = { onRefresh(collection.localId) },
                        onDelete = { onDelete(collection) },
                        onSessionClick = { session -> onSessionClick(session, collection) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionItem(
    collection: CurtainCollectionEntity,
    isExpanded: Boolean,
    sessions: List<CollectionSessionEntity>,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSessionClick: (CollectionSessionEntity) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onToggleExpand),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = collection.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (collection.description.isNotBlank()) {
                            Text(
                                text = collection.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${collection.curtainCount} sessions • ${collection.ownerUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = collection.sourceHostname.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    if (sessions.isEmpty()) {
                        Text(
                            text = "Loading sessions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        sessions.forEach { session ->
                            CollectionSessionItem(
                                session = session,
                                onClick = { onSessionClick(session) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSessionItem(
    session: CollectionSessionEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 36.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.description.ifBlank { session.linkId },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    text = "ID: ${session.linkId.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.curtainType?.let { type ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Load session",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CollectionsEmptyState(
    onLoadExample: () -> Unit
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
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No collections found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Load an example collection or add collections via QR code scanning",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onLoadExample,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Load Example Collection")
            }
        }
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
/**
 * Single curtain item in the list.
 *
 * @param curtain Curtain entity
 * @param downloadProgress Download progress percentage (null if not downloading)
 * @param onDownload Download action callback
 * @param onTogglePin Toggle pin action callback
 * @param onEdit Edit description callback
 * @param onRedownload Redownload data callback
 * @param onDelete Delete action callback
 * @param onClick Click action callback
 */
@Composable
private fun CurtainItem(
    curtain: CurtainEntity,
    downloadProgress: Int?,
    onDownload: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDownloading = downloadProgress != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = !isDownloading, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curtain.dataDescription,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "ID: ${curtain.linkId.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatDate(curtain.created),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = curtain.sourceHostname.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    if (curtain.isPinned) {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Unpin",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pin",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (curtain.file == null) {
                        IconButton(
                            onClick = onDownload,
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Description") },
                                    onClick = {
                                        onEdit()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (curtain.isPinned) "Unpin" else "Pin") },
                                    onClick = {
                                        onTogglePin()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PushPin, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Redownload") },
                                    onClick = {
                                        onRedownload()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        onDelete()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }
                }
            }

            downloadProgress?.let { progress ->
                if (progress < 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Text(
                        text = "Downloading: $progress%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Empty state when no curtains are available.
 *
 * @param onLoadExample Load example dataset callback
 */
@Composable
internal fun EmptyState(onLoadExample: () -> Unit) {
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
                onClick = onLoadExample,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Load Example Dataset")
            }
        }
    }
}

/**
 * Format timestamp to readable date string.
 *
 * @param timestamp Timestamp in milliseconds
 * @return Formatted date string
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
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
