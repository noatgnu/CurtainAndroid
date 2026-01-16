package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.domain.model.AdvancedFilterParams
import info.proteo.curtain.domain.model.BatchSearchResultGroup
import info.proteo.curtain.domain.service.SearchType
import info.proteo.curtain.presentation.viewmodel.ProteinSearchViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProteinSearchDialog(
    onDismiss: () -> Unit,
    viewModel: ProteinSearchViewModel,
    onCreateSelection: (String, Set<String>) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectionTitle by viewModel.selectionTitle.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val significantOnly by viewModel.significantOnly.collectAsState()
    val batchSearchResults by viewModel.batchSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filterLists by viewModel.filterLists.collectAsState()

    LaunchedEffect(batchSearchResults, isSearching) {
        android.util.Log.d("ProteinSearchDialog", "LaunchedEffect triggered: isSearching=$isSearching, batchSearchResults.size=${batchSearchResults.size}")
        if (!isSearching && batchSearchResults.isNotEmpty()) {
            val allProteinIds = viewModel.getAllPrimaryIdsFromBatchResults()
            val selectionName = if (selectionTitle.isNotEmpty()) {
                selectionTitle
            } else {
                "Batch Search (${batchSearchResults.size} terms, ${allProteinIds.size} proteins)"
            }

            android.util.Log.d("ProteinSearchDialog", "Creating selection: name='$selectionName', proteinIds=${allProteinIds.size}")
            onCreateSelection(selectionName, allProteinIds.toSet())

            android.util.Log.d("ProteinSearchDialog", "Clearing results and dismissing")
            viewModel.clearResults()
            onDismiss()
        }
    }

    var regexError by remember { mutableStateOf("") }

    var enableAdvanced by remember { mutableStateOf(false) }
    var minP by remember { mutableStateOf("0.0") }
    var maxP by remember { mutableStateOf("1.0") }
    var minFCLeft by remember { mutableStateOf("0.0") }
    var maxFCLeft by remember { mutableStateOf("10.0") }
    var minFCRight by remember { mutableStateOf("0.0") }
    var maxFCRight by remember { mutableStateOf("10.0") }
    var searchLeft by remember { mutableStateOf(false) }
    var searchRight by remember { mutableStateOf(false) }

    var typeaheadQuery by remember { mutableStateOf("") }
    var typeaheadResults by remember { mutableStateOf<List<FilterListPreview>>(emptyList()) }
    var isSearchingTypeahead by remember { mutableStateOf(false) }
    var showTypeaheadDropdown by remember { mutableStateOf(false) }

    var selectedSubcategory by remember { mutableStateOf<DataFilterListEntity?>(null) }
    var subcategories by remember { mutableStateOf<List<DataFilterListEntity>>(emptyList()) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
    var searchTypeExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory != null) {
            subcategories = filterLists
        } else {
            subcategories = emptyList()
        }
    }

    LaunchedEffect(typeaheadQuery) {
        searchJob?.cancel()
        if (typeaheadQuery.length >= 2) {
            isSearchingTypeahead = true
            searchJob = scope.launch {
                delay(300)
                val results = searchFilterLists(typeaheadQuery, filterLists)
                typeaheadResults = results
                isSearchingTypeahead = false
                showTypeaheadDropdown = results.isNotEmpty()
            }
        } else {
            typeaheadResults = emptyList()
            showTypeaheadDropdown = false
            isSearchingTypeahead = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Protein Batch Search") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = selectionTitle,
                            onValueChange = { viewModel.updateSelectionTitle(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Selection Title (Optional)") },
                            placeholder = { Text("Enter custom name for selection group...") },
                            singleLine = true
                        )
                    }

                    item {
                        HorizontalDivider()
                    }

                    item {
                        Text(
                            text = "Load from filter lists",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    item {
                        Box {
                            OutlinedTextField(
                                value = typeaheadQuery,
                                onValueChange = {
                                    typeaheadQuery = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Search filter lists") },
                                placeholder = { Text("Type to search curated lists...") },
                                trailingIcon = {
                                    if (isSearchingTypeahead) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                },
                                singleLine = true
                            )

                            if (showTypeaheadDropdown) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 400.dp)
                                    ) {
                                        items(typeaheadResults) { result ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val filter = filterLists.find { it.id == result.id }
                                                        if (filter != null) {
                                                            val existingLines = searchQuery.split("\n").filter { it.trim().isNotEmpty() }
                                                            val newLines = filter.data.split("\n").filter { it.trim().isNotEmpty() }
                                                            val combined = (existingLines + newLines).distinct().joinToString("\n").uppercase()
                                                            viewModel.updateSearchQuery(combined)
                                                            if (selectionTitle.isEmpty()) {
                                                                viewModel.updateSelectionTitle(filter.name)
                                                            }
                                                        }
                                                        showTypeaheadDropdown = false
                                                        typeaheadQuery = ""
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = result.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (result.isDefault) {
                                                            Color(0xFF850000)
                                                        } else {
                                                            Color(0xFF9933FF)
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (result.isDefault) {
                                                        Text(
                                                            text = "Curated",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF850000)
                                                        )
                                                    }
                                                }
                                                if (result.preview.isNotEmpty()) {
                                                    Text(
                                                        text = "...${result.preview}...",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (result != typeaheadResults.last()) {
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "- Or -",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory ?: "",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text("Browse by category") },
                                placeholder = { Text("Select a category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                            )

                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All") },
                                    onClick = {
                                        viewModel.selectCategory(null)
                                        categoryExpanded = false
                                    }
                                )
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            viewModel.selectCategory(category)
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = subcategoryExpanded,
                            onExpandedChange = { if (subcategories.isNotEmpty()) subcategoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedSubcategory?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text("Select filter list") },
                                placeholder = { Text("Choose from ${subcategories.size} lists") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryExpanded) },
                                enabled = subcategories.isNotEmpty()
                            )

                            ExposedDropdownMenu(
                                expanded = subcategoryExpanded,
                                onDismissRequest = { subcategoryExpanded = false }
                            ) {
                                subcategories.forEach { subcategory ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(subcategory.name)
                                                if (subcategory.isDefault) {
                                                    Text(
                                                        "Curated",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF850000)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedSubcategory = subcategory
                                            val existingLines = searchQuery.split("\n").filter { it.trim().isNotEmpty() }
                                            val newLines = subcategory.data.split("\n").filter { it.trim().isNotEmpty() }
                                            val combined = (existingLines + newLines).distinct().joinToString("\n").uppercase()
                                            viewModel.updateSearchQuery(combined)
                                            if (selectionTitle.isEmpty()) {
                                                viewModel.updateSelectionTitle(subcategory.name)
                                            }
                                            subcategoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider()
                    }

                    item {
                        Text(
                            text = "Search parameters",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = searchTypeExpanded,
                            onExpandedChange = { searchTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = if (searchType == SearchType.GENE_NAMES) "Gene Names" else "Primary IDs",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text("Identifier type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchTypeExpanded) }
                            )

                            ExposedDropdownMenu(
                                expanded = searchTypeExpanded,
                                onDismissRequest = { searchTypeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Gene Names") },
                                    onClick = {
                                        viewModel.setSearchType(SearchType.GENE_NAMES)
                                        searchTypeExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Primary IDs") },
                                    onClick = {
                                        viewModel.setSearchType(SearchType.PRIMARY_IDS)
                                        searchTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            placeholder = {
                                Text(
                                    if (useRegex) {
                                        "Enter regex patterns (one per line):\n^ACTB\nKINASE\$\nP[0-9]+"
                                    } else {
                                        "Enter protein identifiers (one per line or semicolon separated):\nACTB\nTUBB\nGAPDH"
                                    }
                                )
                            },
                            enabled = !enableAdvanced,
                            singleLine = false,
                            maxLines = Int.MAX_VALUE
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useRegex,
                                onCheckedChange = { viewModel.toggleUseRegex() }
                            )
                            Text("Enable Regex Search", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (regexError.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = regexError,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = significantOnly,
                                onCheckedChange = { viewModel.toggleSignificantOnly() },
                                enabled = !enableAdvanced
                            )
                            Text("Significant only", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (error != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = error!!,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider()
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enableAdvanced,
                                onCheckedChange = { enableAdvanced = it }
                            )
                            Text(
                                text = "Advanced filtering (FC/P-value ranges)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (enableAdvanced) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = searchLeft, onCheckedChange = { searchLeft = it })
                                    Text("Left side (negative FC)")
                                }

                                if (searchLeft) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = minFCLeft,
                                            onValueChange = { minFCLeft = it },
                                            label = { Text("Min") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = maxFCLeft,
                                            onValueChange = { maxFCLeft = it },
                                            label = { Text("Max") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = searchRight, onCheckedChange = { searchRight = it })
                                    Text("Right side (positive FC)")
                                }

                                if (searchRight) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = minFCRight,
                                            onValueChange = { minFCRight = it },
                                            label = { Text("Min") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = maxFCRight,
                                            onValueChange = { maxFCRight = it },
                                            label = { Text("Max") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }

                                Text("P-value range", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                regexError = ""

                                val advancedParams = if (enableAdvanced) {
                                    try {
                                        AdvancedFilterParams(
                                            minP = minP.toDouble(),
                                            maxP = maxP.toDouble(),
                                            minFCLeft = minFCLeft.toDouble(),
                                            maxFCLeft = maxFCLeft.toDouble(),
                                            minFCRight = minFCRight.toDouble(),
                                            maxFCRight = maxFCRight.toDouble(),
                                            searchLeft = searchLeft,
                                            searchRight = searchRight
                                        )
                                    } catch (e: Exception) {
                                        regexError = "Invalid numeric values"
                                        null
                                    }
                                } else null

                                viewModel.setAdvancedFiltering(advancedParams)
                                viewModel.performBatchSearch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = searchQuery.isNotEmpty() && !isSearching
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit")
                        }
                    }

                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Searching...",
                                        style = MaterialTheme.typography.bodyMedium
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

@Composable
private fun BatchResultCard(group: BatchSearchResultGroup) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
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
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle"
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                group.results.take(10).forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (group.totalCount > 10) {
                    Text(
                        text = "... and ${group.totalCount - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private data class FilterListPreview(
    val id: Long,
    val name: String,
    val preview: String,
    val isDefault: Boolean
)

private fun searchFilterLists(
    query: String,
    filterLists: List<DataFilterListEntity>
): List<FilterListPreview> {
    val queryLower = query.lowercase()

    return filterLists.mapNotNull { filter ->
        val dataLines = filter.data.split("\n").filter { it.trim().isNotEmpty() }
        val matchingLines = dataLines.filter { it.lowercase().contains(queryLower) }

        if (filter.name.lowercase().contains(queryLower) || matchingLines.isNotEmpty()) {
            FilterListPreview(
                id = filter.id,
                name = filter.name,
                preview = matchingLines.firstOrNull() ?: "",
                isDefault = filter.isDefault
            )
        } else {
            null
        }
    }.take(30)
}
