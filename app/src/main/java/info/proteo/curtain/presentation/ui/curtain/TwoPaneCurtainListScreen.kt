package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.presentation.ui.dialogs.AddCurtainDialog
import info.proteo.curtain.presentation.ui.settings.ThemeSettingsScreen
import info.proteo.curtain.presentation.viewmodel.CurtainViewModel
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.presentation.ui.curtain.ProteinDetailsTab
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoPaneCurtainListScreen(
    navController: NavController,
    onNavigateToQRScanner: () -> Unit,
    curtainViewModel: CurtainViewModel = hiltViewModel()
) {
    val curtains by curtainViewModel.curtains.collectAsState()
    val isLoading by curtainViewModel.isLoading.collectAsState()
    val error by curtainViewModel.error.collectAsState()
    val searchQuery by curtainViewModel.searchQuery.collectAsState()
    val downloadProgress by curtainViewModel.downloadProgress.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCurtain by remember { mutableStateOf<CurtainEntity?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var curtainToEdit by remember { mutableStateOf<CurtainEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var curtainToDelete by remember { mutableStateOf<CurtainEntity?>(null) }
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

                TextField(
                    value = searchQuery,
                    onValueChange = { curtainViewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search datasets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true
                )

                when {
                    isLoading && curtains.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    curtains.isEmpty() -> {
                        Box(modifier = Modifier.weight(1f)) {
                            EmptyState(
                                onLoadExample = { curtainViewModel.loadExampleCurtain() }
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(curtains, key = { it.linkId }) { curtain ->
                                CurtainItemCompact(
                                    curtain = curtain,
                                    isSelected = selectedCurtain?.linkId == curtain.linkId,
                                    downloadProgress = downloadProgress[curtain.linkId],
                                    onDownload = { curtainViewModel.downloadCurtain(curtain) },
                                    onTogglePin = { curtainViewModel.togglePin(curtain) },
                                    onEdit = {
                                        curtainToEdit = curtain
                                        showEditDialog = true
                                    },
                                    onRedownload = { curtainViewModel.downloadCurtain(curtain) },
                                    onDelete = {
                                        curtainToDelete = curtain
                                        showDeleteDialog = true
                                    },
                                    onClick = {
                                        if (curtain.file != null) {
                                            selectedCurtain = curtain
                                        } else {
                                            curtainViewModel.downloadCurtain(curtain)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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
                CurtainDetailsContent(
                    linkId = selectedCurtain!!.linkId,
                    navController = navController,
                    onBack = { selectedCurtain = null }
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
            curtainDescription = curtainToDelete!!.dataDescription,
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
private fun CurtainItemCompact(
    curtain: CurtainEntity,
    isSelected: Boolean,
    downloadProgress: Int?,
    onDownload: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curtain.dataDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )

                    Text(
                        text = "ID: ${curtain.linkId.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(curtain.created),
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
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (curtain.isPinned) "Unpin" else "Pin",
                            tint = if (curtain.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (curtain.file == null) {
                        IconButton(onClick = onDownload) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    modifier = Modifier.size(20.dp)
                                )
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
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (curtain.isPinned) "Unpin" else "Pin") },
                                    onClick = {
                                        onTogglePin()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.PushPin, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Redownload") },
                                    onClick = {
                                        onRedownload()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Download, null) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurtainDetailsContent(
    linkId: String,
    navController: NavController,
    onBack: () -> Unit,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val proteinCount by viewModel.proteinCount.collectAsState()
    val mappingProgress by viewModel.mappingProgress.collectAsState()
    val loadingStatus by viewModel.loadingStatus.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Volcano Plot", "Protein List", "Settings")

    var showProteinChartDialog by remember { mutableStateOf(false) }
    var selectedProteinId by remember { mutableStateOf<String?>(null) }
    var selectedGeneName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(linkId) {
        viewModel.loadCurtainData(linkId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Dataset Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        mappingProgress?.let { (current, total) ->
                            if (current == -1 && total == -1) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Saving mappings to database...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "Building protein mappings...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                LinearProgressIndicator(
                                    progress = { current.toFloat() / total.toFloat() },
                                    modifier = Modifier.width(200.dp)
                                )
                                Text(
                                    text = "$current / $total proteins",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } ?: run {
                            CircularProgressIndicator()
                            loadingStatus?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            curtainData != null -> {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> OverviewTab(curtainData!!, proteinCount)
                        1 -> VolcanoPlotTabSimple(
                            curtainData = curtainData!!,
                            viewModel = viewModel
                        )
                        2 -> ProteinListTabForTablet(
                            curtainData = curtainData!!,
                            linkId = linkId,
                            onProteinClick = { proteinId, geneName ->
                                selectedProteinId = proteinId
                                selectedGeneName = geneName
                                showProteinChartDialog = true
                            }
                        )
                        3 -> SettingsTab(curtainData!!, navController)
                    }
                }
            }
        }
    }

    if (showProteinChartDialog && selectedProteinId != null && curtainData != null) {
        ProteinChartDialog(
            linkId = linkId,
            proteinId = selectedProteinId!!,
            geneName = selectedGeneName,
            curtainDetailsViewModel = viewModel,
            onDismiss = {
                showProteinChartDialog = false
                selectedProteinId = null
                selectedGeneName = null
            }
        )
    }
}

@Composable
private fun VolcanoPlotTabSimple(
    curtainData: CurtainData,
    viewModel: CurtainDetailsViewModel
) {
    val volcanoPlotHtml by viewModel.volcanoPlotHtml.collectAsState()

    LaunchedEffect(curtainData) {
        if (volcanoPlotHtml == null) {
            viewModel.generateVolcanoPlotPublic()
        }
    }

    if (volcanoPlotHtml != null) {
        VolcanoPlotView(
            htmlContent = volcanoPlotHtml!!,
            onPlotReady = { },
            onPlotError = { },
            onPointClicked = { }
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating volcano plot...")
            }
        }
    }
}

@Composable
private fun ProteinListTabForTablet(
    curtainData: CurtainData,
    linkId: String,
    onProteinClick: (proteinId: String, geneName: String?) -> Unit,
    proteinDetailsViewModel: info.proteo.curtain.presentation.viewmodel.ProteinDetailsViewModel = hiltViewModel()
) {
    val proteins by proteinDetailsViewModel.proteins.collectAsState()
    val selectionGroups by proteinDetailsViewModel.selectionGroups.collectAsState()
    val searchQuery by proteinDetailsViewModel.searchQuery.collectAsState()

    LaunchedEffect(linkId) {
        proteinDetailsViewModel.setLinkId(linkId)
    }

    LaunchedEffect(curtainData) {
        proteinDetailsViewModel.setCurtainData(curtainData)
    }

    ProteinDetailsTab(
        proteins = proteins,
        selectionGroups = selectionGroups,
        searchQuery = searchQuery,
        onSearchQueryChange = { query -> proteinDetailsViewModel.updateSearchQuery(query) },
        onProteinClick = { protein ->
            onProteinClick(protein.primaryId, protein.geneName)
        },
        onAddToGroup = { proteinId, groupId ->
            proteinDetailsViewModel.addProteinToGroup(proteinId, groupId)
        },
        onRemoveFromGroup = { proteinId, groupId ->
            proteinDetailsViewModel.removeProteinFromGroup(proteinId, groupId)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProteinChartDialog(
    linkId: String,
    proteinId: String,
    geneName: String?,
    curtainDetailsViewModel: CurtainDetailsViewModel,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = geneName ?: proteinId,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                info.proteo.curtain.presentation.ui.chart.ProteinChartNavigationScreen(
                    linkId = linkId,
                    proteinId = proteinId,
                    geneName = geneName,
                    curtainDetailsViewModel = curtainDetailsViewModel,
                    onNavigateBack = onDismiss
                )
            }
        }
    }
}

