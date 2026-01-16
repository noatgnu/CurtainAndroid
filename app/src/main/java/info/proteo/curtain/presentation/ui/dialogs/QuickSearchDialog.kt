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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.proteo.curtain.domain.model.SearchResult
import info.proteo.curtain.domain.service.SearchType
import info.proteo.curtain.presentation.viewmodel.ProteinSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSearchDialog(
    onDismiss: () -> Unit,
    viewModel: ProteinSearchViewModel,
    onCreateSelection: (String, Set<String>) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectionTitle by viewModel.selectionTitle.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val availableSuggestions by viewModel.availableSuggestions.collectAsState()

    var showSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(searchResults, isSearching) {
        if (!isSearching && searchResults.isNotEmpty()) {
            val proteinIds = searchResults.map { it.proteinId }.toSet()
            val selectionName = if (selectionTitle.isNotEmpty()) {
                selectionTitle
            } else {
                "Quick Search: $searchQuery (${proteinIds.size} proteins)"
            }

            onCreateSelection(selectionName, proteinIds)

            viewModel.clearResults()
            onDismiss()
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
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
            ) {
                TopAppBar(
                    title = { Text("Quick Search") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = selectionTitle,
                        onValueChange = { viewModel.updateSelectionTitle(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Selection Title (Optional)") },
                        placeholder = { Text("Enter custom name for selection group...") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = searchType == SearchType.GENE_NAMES,
                            onClick = {
                                viewModel.setSearchType(SearchType.GENE_NAMES)
                                viewModel.updateSearchQuery("")
                            },
                            label = { Text("Gene Names") },
                            leadingIcon = {
                                if (searchType == SearchType.GENE_NAMES) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        FilterChip(
                            selected = searchType == SearchType.PRIMARY_IDS,
                            onClick = {
                                viewModel.setSearchType(SearchType.PRIMARY_IDS)
                                viewModel.updateSearchQuery("")
                            },
                            label = { Text("Primary IDs") },
                            leadingIcon = {
                                if (searchType == SearchType.PRIMARY_IDS) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }

                    Box {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                viewModel.updateSearchQuery(it)
                                showSuggestions = it.isNotEmpty()
                                viewModel.filterSuggestions(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search ${if (searchType == SearchType.GENE_NAMES) "Gene Names" else "Primary IDs"}") },
                            placeholder = { Text("Type to search...") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.updateSearchQuery("")
                                        showSuggestions = false
                                    }) {
                                        Icon(Icons.Default.Clear, "Clear")
                                    }
                                }
                            },
                            singleLine = true
                        )

                        if (showSuggestions && availableSuggestions.isNotEmpty() && searchResults.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 250.dp)
                                ) {
                                    items(availableSuggestions.take(10)) { suggestion ->
                                        Text(
                                            text = suggestion,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateSearchQuery(suggestion)
                                                    showSuggestions = false
                                                    viewModel.performSearch()
                                                }
                                                .padding(12.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (suggestion != availableSuggestions.take(10).last()) {
                                            Divider()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showSuggestions = false
                            viewModel.performSearch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = searchQuery.isNotEmpty() && !isSearching
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search")
                    }

                    error?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (isSearching) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "${searchResults.size} results",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { result ->
                                QuickSearchResultCard(result)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSearchResultCard(result: SearchResult) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.proteinId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                result.geneName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.isSignificant) {
                Icon(Icons.Default.Check, "Significant", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
