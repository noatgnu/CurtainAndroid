package info.proteo.curtain.presentation.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.proteo.curtain.domain.model.AdvancedFilterParams
import info.proteo.curtain.domain.model.BatchSearchResultGroup
import info.proteo.curtain.domain.model.SearchResult
import info.proteo.curtain.domain.service.SearchType
import info.proteo.curtain.presentation.viewmodel.ProteinSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProteinSearchScreen(
    viewModel: ProteinSearchViewModel = hiltViewModel()
) {
    val batchMode by viewModel.batchMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val caseSensitive by viewModel.caseSensitive.collectAsState()
    val exactMatch by viewModel.exactMatch.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val searchInProteinIds by viewModel.searchInProteinIds.collectAsState()
    val searchInGeneNames by viewModel.searchInGeneNames.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val significantOnly by viewModel.significantOnly.collectAsState()
    val advancedFiltering by viewModel.advancedFiltering.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val batchSearchResults by viewModel.batchSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchLists by viewModel.searchLists.collectAsState()
    val selectedSearchListId by viewModel.selectedSearchListId.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSearchListsDialog by remember { mutableStateOf(false) }
    var expandedOptions by remember { mutableStateOf(false) }
    var expandedAdvanced by remember { mutableStateOf(false) }

    var minP by remember { mutableStateOf("0.0") }
    var maxP by remember { mutableStateOf("1.0") }
    var minFCLeft by remember { mutableStateOf("0.0") }
    var maxFCLeft by remember { mutableStateOf("10.0") }
    var minFCRight by remember { mutableStateOf("0.0") }
    var maxFCRight by remember { mutableStateOf("10.0") }
    var searchLeft by remember { mutableStateOf(false) }
    var searchRight by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (batchMode) "Batch Search" else "Protein Search") },
                actions = {
                    IconButton(onClick = { viewModel.toggleBatchMode() }) {
                        Icon(
                            if (batchMode) Icons.Default.ViewList else Icons.Default.Search,
                            "Toggle Batch Mode"
                        )
                    }
                    IconButton(onClick = { expandedOptions = !expandedOptions }) {
                        Icon(Icons.Default.Settings, "Search Options")
                    }
                    IconButton(
                        onClick = { showSearchListsDialog = true },
                        enabled = searchLists.isNotEmpty()
                    ) {
                        BadgedBox(
                            badge = {
                                if (searchLists.isNotEmpty()) {
                                    Badge { Text(searchLists.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.List, "Saved Searches")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (batchMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Batch Mode Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Multi-line with ID resolution",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = searchType == SearchType.GENE_NAMES,
                        onClick = { viewModel.setSearchType(SearchType.GENE_NAMES) },
                        label = { Text("Gene Names") },
                        leadingIcon = {
                            if (searchType == SearchType.GENE_NAMES) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                    FilterChip(
                        selected = searchType == SearchType.PRIMARY_IDS,
                        onClick = { viewModel.setSearchType(SearchType.PRIMARY_IDS) },
                        label = { Text("Primary IDs") },
                        leadingIcon = {
                            if (searchType == SearchType.PRIMARY_IDS) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (batchMode) Modifier.height(180.dp) else Modifier),
                label = {
                    Text(if (batchMode) "Batch Input (multi-line)" else "Search proteins")
                },
                placeholder = {
                    Text(
                        if (batchMode)
                            "Enter one ${if (searchType == SearchType.GENE_NAMES) "gene name" else "protein ID"} per line\nOr semicolons: ACTB;TUBB;GAPDH"
                        else
                            "Enter protein ID or gene name"
                    )
                },
                leadingIcon = if (!batchMode) {
                    { Icon(Icons.Default.Search, "Search") }
                } else null,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = if (batchMode) ImeAction.Default else ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (!batchMode) viewModel.performSearch() }
                ),
                singleLine = !batchMode,
                maxLines = if (batchMode) Int.MAX_VALUE else 1,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (expandedOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Search Options",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (batchMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Use Regex")
                                Switch(
                                    checked = useRegex,
                                    onCheckedChange = { viewModel.toggleUseRegex() }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Significant Only")
                                Switch(
                                    checked = significantOnly,
                                    onCheckedChange = { viewModel.toggleSignificantOnly() }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Case Sensitive")
                                Switch(
                                    checked = caseSensitive,
                                    onCheckedChange = { viewModel.toggleCaseSensitive() }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Exact Match")
                                Switch(
                                    checked = exactMatch,
                                    onCheckedChange = { viewModel.toggleExactMatch() }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Use Regex")
                                Switch(
                                    checked = useRegex,
                                    onCheckedChange = { viewModel.toggleUseRegex() }
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                text = "Search In",
                                style = MaterialTheme.typography.titleSmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Protein IDs")
                                Switch(
                                    checked = searchInProteinIds,
                                    onCheckedChange = { viewModel.toggleSearchInProteinIds() }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gene Names")
                                Switch(
                                    checked = searchInGeneNames,
                                    onCheckedChange = { viewModel.toggleSearchInGeneNames() }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (batchMode && !expandedOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advanced Filtering",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (advancedFiltering != null) {
                                    AssistChip(
                                        onClick = { viewModel.setAdvancedFiltering(null) },
                                        label = { Text("Clear") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { expandedAdvanced = !expandedAdvanced }
                                ) {
                                    Icon(
                                        if (expandedAdvanced) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        "Toggle"
                                    )
                                }
                            }
                        }

                        if (expandedAdvanced) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "P-value Range",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = minP,
                                    onValueChange = { minP = it },
                                    label = { Text("Min") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = maxP,
                                    onValueChange = { maxP = it },
                                    label = { Text("Max") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Search Left Side (negative FC)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Switch(
                                    checked = searchLeft,
                                    onCheckedChange = { searchLeft = it }
                                )
                            }

                            if (searchLeft) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = minFCLeft,
                                        onValueChange = { minFCLeft = it },
                                        label = { Text("Min FC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = maxFCLeft,
                                        onValueChange = { maxFCLeft = it },
                                        label = { Text("Max FC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Search Right Side (positive FC)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Switch(
                                    checked = searchRight,
                                    onCheckedChange = { searchRight = it }
                                )
                            }

                            if (searchRight) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = minFCRight,
                                        onValueChange = { minFCRight = it },
                                        label = { Text("Min FC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = maxFCRight,
                                        onValueChange = { maxFCRight = it },
                                        label = { Text("Max FC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    try {
                                        val params = AdvancedFilterParams(
                                            minP = minP.toDouble(),
                                            maxP = maxP.toDouble(),
                                            minFCLeft = minFCLeft.toDouble(),
                                            maxFCLeft = maxFCLeft.toDouble(),
                                            minFCRight = minFCRight.toDouble(),
                                            maxFCRight = maxFCRight.toDouble(),
                                            searchLeft = searchLeft,
                                            searchRight = searchRight
                                        )
                                        viewModel.setAdvancedFiltering(params)
                                        expandedAdvanced = false
                                    } catch (e: Exception) {
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Apply Advanced Filters")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (batchMode) viewModel.performBatchSearch()
                        else viewModel.performSearch()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = searchQuery.isNotEmpty() && !isSearching
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search")
                }

                if (searchResults.isNotEmpty() || batchSearchResults.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            error?.let { errorMessage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (batchMode && batchSearchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total: ${viewModel.getAllPrimaryIdsFromBatchResults().size} proteins",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${batchSearchResults.size} search terms matched",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    items(batchSearchResults) { group ->
                        BatchSearchResultGroupCard(group = group)
                    }
                }
            } else if (!batchMode && searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!batchMode && searchResults.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${searchResults.size} results",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedSearchListId != null) {
                            TextButton(
                                onClick = { viewModel.clearResults() }
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { result ->
                            SearchResultItem(result = result)
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveSearchListDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name, description ->
                viewModel.saveSearchList(name, description)
                showSaveDialog = false
            }
        )
    }

    if (showSearchListsDialog) {
        SavedSearchListsDialog(
            searchLists = searchLists,
            selectedId = selectedSearchListId,
            onDismiss = { showSearchListsDialog = false },
            onLoad = { searchListId ->
                viewModel.loadSearchList(searchListId)
                showSearchListsDialog = false
            },
            onDelete = { searchListId ->
                viewModel.deleteSearchList(searchListId)
            }
        )
    }
}

@Composable
fun SearchResultItem(result: SearchResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.proteinId,
                        style = MaterialTheme.typography.titleMedium
                    )
                    result.geneName?.let { geneName ->
                        Text(
                            text = geneName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (result.isSignificant) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Significant") },
                        leadingIcon = {
                            Icon(Icons.Default.Check, null)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                result.log2FC?.let { fc ->
                    Column {
                        Text(
                            text = "Log2FC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.2f", fc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                result.pValue?.let { p ->
                    Column {
                        Text(
                            text = "P-Value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.2e", p),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Column {
                    Text(
                        text = "Match Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.matchType.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SaveSearchListDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Search List") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description) },
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
fun SavedSearchListsDialog(
    searchLists: List<info.proteo.curtain.domain.model.ProteinSearchList>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved Search Lists") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchLists) { searchList ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoad(searchList.id) },
                        colors = if (searchList.id == selectedId) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = searchList.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (searchList.description.isNotBlank()) {
                                    Text(
                                        text = searchList.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${searchList.proteinIds.size} proteins",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(searchList.id) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BatchSearchResultGroupCard(group: BatchSearchResultGroup) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.searchTerm,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${group.totalCount} matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        "Toggle"
                    )
                }
            }

            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.results.take(10).forEach { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.proteinId,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                result.geneName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (result.isSignificant) {
                                Icon(
                                    Icons.Default.Check,
                                    "Significant",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (group.totalCount > 10) {
                        Text(
                            text = "... and ${group.totalCount - 10} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
