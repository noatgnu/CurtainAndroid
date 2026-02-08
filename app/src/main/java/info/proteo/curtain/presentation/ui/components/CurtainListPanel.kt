package info.proteo.curtain.presentation.ui.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import java.text.SimpleDateFormat
import java.util.Locale

enum class CurtainListMode {
    MANAGEMENT,
    SELECTION
}

@Composable
fun CurtainListPanel(
    mode: CurtainListMode,
    datasets: List<CurtainEntity>,
    collections: List<CurtainCollectionEntity>,
    expandedCollectionIds: Set<Long>,
    collectionSessions: Map<Long, List<CollectionSessionEntity>>,
    isLoading: Boolean = false,
    isLoadingCollections: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedDatasetLinkId: String? = null,
    onDatasetClick: (CurtainEntity) -> Unit = {},
    downloadProgress: Map<String, Int> = emptyMap(),
    onDownload: (CurtainEntity) -> Unit = {},
    onTogglePin: (CurtainEntity) -> Unit = {},
    onEdit: (CurtainEntity) -> Unit = {},
    onRedownload: (CurtainEntity) -> Unit = {},
    onDeleteDataset: (CurtainEntity) -> Unit = {},
    onToggleCollectionExpanded: (Long) -> Unit = {},
    onRefreshCollection: (Long) -> Unit = {},
    onDeleteCollection: (CurtainCollectionEntity) -> Unit = {},
    onSessionClick: (CollectionSessionEntity, CurtainCollectionEntity) -> Unit = { _, _ -> },
    selectionModeCollectionId: Long? = null,
    selectedSessionIdsInCollection: Map<Long, Set<String>> = emptyMap(),
    onEnterSelectionMode: (Long) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onToggleSessionSelectionInCollection: (Long, String) -> Unit = { _, _ -> },
    onSelectAllInCollection: (Long) -> Unit = {},
    onDeselectAllInCollection: (Long) -> Unit = {},
    onDownloadSelectedInCollection: (Long) -> Unit = {},
    selectedDatasetIds: Set<String> = emptySet(),
    onToggleDatasetSelection: (String) -> Unit = {},
    onSelectAllDatasets: () -> Unit = {},
    onDeselectAllDatasets: () -> Unit = {},
    onToggleSessionSelection: (String) -> Unit = {},
    onLoadExample: () -> Unit = {},
    onLoadPTMExample: () -> Unit = {},
    onLoadBothExamples: () -> Unit = {},
    onLoadExampleCollection: () -> Unit = {},
    curtainTypeFilter: String = "all",
    onCurtainTypeFilterChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectionSearchQuery by rememberSaveable { mutableStateOf("") }

    val filteredDatasets = remember(datasets, curtainTypeFilter, searchQuery, selectionSearchQuery, mode) {
        val query = if (mode == CurtainListMode.SELECTION) selectionSearchQuery else searchQuery
        datasets.filter { dataset ->
            val matchesType = curtainTypeFilter == "all" || dataset.curtainType == curtainTypeFilter
            val matchesSearch = query.isBlank() ||
                dataset.dataDescription.contains(query, ignoreCase = true) ||
                dataset.linkId.contains(query, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    Column(modifier = modifier) {
        if (mode == CurtainListMode.MANAGEMENT) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search datasets...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all" to "All", "TP" to "TP", "PTM" to "PTM").forEach { (value, label) ->
                    FilterChip(
                        selected = curtainTypeFilter == value,
                        onClick = { onCurtainTypeFilterChange(value) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (mode == CurtainListMode.SELECTION) {
            TextField(
                value = selectionSearchQuery,
                onValueChange = { selectionSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search datasets...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("all" to "All", "TP" to "TP", "PTM" to "PTM").forEach { (value, label) ->
                    FilterChip(
                        selected = curtainTypeFilter == value,
                        onClick = { onCurtainTypeFilterChange(value) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedDatasetIds.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    TextButton(onClick = onSelectAllDatasets) {
                        Text("All", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onDeselectAllDatasets) {
                        Text("None", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Sessions") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Collections") }
            )
        }

        when (selectedTabIndex) {
            0 -> {
                when {
                    isLoading && datasets.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    datasets.isEmpty() -> {
                        DatasetEmptyState(
                            mode = mode,
                            onLoadExample = onLoadExample,
                            onLoadPTMExample = onLoadPTMExample,
                            onLoadBothExamples = onLoadBothExamples,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(filteredDatasets, key = { it.linkId }) { dataset ->
                                CurtainListItem(
                                    mode = mode,
                                    dataset = dataset,
                                    isSelected = when (mode) {
                                        CurtainListMode.MANAGEMENT -> selectedDatasetLinkId == dataset.linkId
                                        CurtainListMode.SELECTION -> selectedDatasetIds.contains(dataset.linkId)
                                    },
                                    downloadProgress = downloadProgress[dataset.linkId],
                                    onClick = {
                                        when (mode) {
                                            CurtainListMode.MANAGEMENT -> onDatasetClick(dataset)
                                            CurtainListMode.SELECTION -> onToggleDatasetSelection(dataset.linkId)
                                        }
                                    },
                                    onDownload = { onDownload(dataset) },
                                    onTogglePin = { onTogglePin(dataset) },
                                    onEdit = { onEdit(dataset) },
                                    onRedownload = { onRedownload(dataset) },
                                    onDelete = { onDeleteDataset(dataset) }
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                when {
                    isLoadingCollections && collections.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    collections.isEmpty() -> {
                        CollectionEmptyState(
                            mode = mode,
                            onLoadExample = onLoadExampleCollection,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(collections, key = { it.localId }) { collection ->
                                CollectionListItem(
                                    mode = mode,
                                    collection = collection,
                                    isExpanded = expandedCollectionIds.contains(collection.localId),
                                    sessions = collectionSessions[collection.localId] ?: emptyList(),
                                    onToggleExpand = { onToggleCollectionExpanded(collection.localId) },
                                    onRefresh = { onRefreshCollection(collection.localId) },
                                    onDelete = { onDeleteCollection(collection) },
                                    onSessionClick = { session -> onSessionClick(session, collection) },
                                    isInSelectionMode = selectionModeCollectionId == collection.localId,
                                    selectedSessionIds = when (mode) {
                                        CurtainListMode.MANAGEMENT -> selectedSessionIdsInCollection[collection.localId] ?: emptySet()
                                        CurtainListMode.SELECTION -> selectedDatasetIds
                                    },
                                    onEnterSelectionMode = { onEnterSelectionMode(collection.localId) },
                                    onExitSelectionMode = onExitSelectionMode,
                                    onToggleSessionSelection = { linkId ->
                                        when (mode) {
                                            CurtainListMode.MANAGEMENT -> onToggleSessionSelectionInCollection(collection.localId, linkId)
                                            CurtainListMode.SELECTION -> onToggleSessionSelection(linkId)
                                        }
                                    },
                                    onSelectAll = {
                                        when (mode) {
                                            CurtainListMode.MANAGEMENT -> onSelectAllInCollection(collection.localId)
                                            CurtainListMode.SELECTION -> {
                                                val sessions = collectionSessions[collection.localId] ?: emptyList()
                                                sessions.forEach { onToggleSessionSelection(it.linkId) }
                                            }
                                        }
                                    },
                                    onDeselectAll = {
                                        when (mode) {
                                            CurtainListMode.MANAGEMENT -> onDeselectAllInCollection(collection.localId)
                                            CurtainListMode.SELECTION -> {
                                                val sessions = collectionSessions[collection.localId] ?: emptyList()
                                                sessions.filter { selectedDatasetIds.contains(it.linkId) }
                                                    .forEach { onToggleSessionSelection(it.linkId) }
                                            }
                                        }
                                    },
                                    onDownloadSelected = { onDownloadSelectedInCollection(collection.localId) }
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
private fun CurtainListItem(
    mode: CurtainListMode,
    dataset: CurtainEntity,
    isSelected: Boolean,
    downloadProgress: Int?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDownloading = downloadProgress != null
    val displayName = dataset.sessionName?.takeIf { it.isNotBlank() } ?: dataset.dataDescription.ifBlank { "Untitled" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(enabled = !isDownloading, onClick = onClick),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = dataset.curtainType,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dataset.curtainType == "PTM") MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "ID: ${dataset.linkId.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(dataset.created),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = dataset.sourceHostname.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (mode) {
                    CurtainListMode.MANAGEMENT -> {
                        Row {
                            IconButton(onClick = onTogglePin) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = if (dataset.isPinned) "Unpin" else "Pin",
                                    tint = if (dataset.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (dataset.file == null) {
                                IconButton(
                                    onClick = onDownload,
                                    enabled = !isDownloading
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
                                            text = { Text(if (dataset.isPinned) "Unpin" else "Pin") },
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
                    CurtainListMode.SELECTION -> {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() }
                        )
                    }
                }
            }

            if (mode == CurtainListMode.MANAGEMENT) {
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
}

@Composable
private fun CollectionListItem(
    mode: CurtainListMode,
    collection: CurtainCollectionEntity,
    isExpanded: Boolean,
    sessions: List<CollectionSessionEntity>,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSessionClick: (CollectionSessionEntity) -> Unit,
    isInSelectionMode: Boolean,
    selectedSessionIds: Set<String>,
    onEnterSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = collection.name.ifBlank { "Untitled Collection" }
    val selectedInCollection = sessions.count { selectedSessionIds.contains(it.linkId) }
    val showSelectionControls = mode == CurtainListMode.SELECTION || isInSelectionMode

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onToggleExpand),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sessionCount = if (sessions.isNotEmpty()) sessions.size else collection.curtainCount
                        val sessionCountText = "$sessionCount session${if (sessionCount != 1) "s" else ""}" +
                            if (showSelectionControls && selectedInCollection > 0) " ($selectedInCollection selected)" else ""
                        Text(
                            text = sessionCountText,
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
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (mode == CurtainListMode.MANAGEMENT) {
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
                                    text = { Text("Download Multiple") },
                                    onClick = {
                                        onEnterSelectionMode()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.SelectAll, contentDescription = null)
                                    }
                                )
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
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    if (showSelectionControls) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$selectedInCollection/${sessions.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Row {
                                TextButton(onClick = onSelectAll) {
                                    Text("All", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = onDeselectAll) {
                                    Text("None", style = MaterialTheme.typography.labelSmall)
                                }
                                if (mode == CurtainListMode.MANAGEMENT && isInSelectionMode) {
                                    TextButton(
                                        onClick = onDownloadSelected,
                                        enabled = selectedSessionIds.isNotEmpty()
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = onExitSelectionMode,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Exit selection mode",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (sessions.isEmpty()) {
                        Text(
                            text = "Loading sessions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                        )
                    } else {
                        sessions.forEach { session ->
                            SessionListItem(
                                mode = mode,
                                session = session,
                                showCheckbox = showSelectionControls,
                                isSelected = selectedSessionIds.contains(session.linkId),
                                onClick = {
                                    if (showSelectionControls) {
                                        onToggleSessionSelection(session.linkId)
                                    } else {
                                        onSessionClick(session)
                                    }
                                },
                                onToggleSelection = { onToggleSessionSelection(session.linkId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionListItem(
    mode: CurtainListMode,
    session: CollectionSessionEntity,
    showCheckbox: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val displayName = session.sessionName?.takeIf { it.isNotBlank() } ?: session.description.ifBlank { "Untitled" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (showCheckbox) 8.dp else 28.dp,
                top = 6.dp,
                bottom = 6.dp,
                end = 8.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ID: ${session.linkId.take(12)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DatasetEmptyState(
    mode: CurtainListMode,
    onLoadExample: () -> Unit,
    onLoadPTMExample: () -> Unit = {},
    onLoadBothExamples: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (mode == CurtainListMode.SELECTION) "No Downloaded Datasets" else "No Datasets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (mode == CurtainListMode.SELECTION)
                    "Download datasets from the Datasets tab first"
                else
                    "Add a dataset to get started",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mode == CurtainListMode.MANAGEMENT) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onLoadBothExamples) {
                    Text("Load Both Example Datasets")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onLoadExample) {
                        Text("TP Only")
                    }
                    TextButton(onClick = onLoadPTMExample) {
                        Text("PTM Only")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEmptyState(
    mode: CurtainListMode,
    onLoadExample: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Collections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (mode == CurtainListMode.SELECTION)
                    "Add collections from the Datasets tab"
                else
                    "Add a collection to organize datasets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mode == CurtainListMode.MANAGEMENT) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onLoadExample) {
                    Text("Load Example Collection")
                }
            }
        }
    }
}
