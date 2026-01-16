package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.proteo.curtain.domain.model.SelectionGroup
import kotlin.math.abs

data class ProteinInfo(
    val primaryId: String,
    val geneName: String?,
    val log2FC: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val selectionGroups: List<SelectionGroup>
)

@Composable
fun ProteinDetailsTab(
    proteins: List<ProteinInfo>,
    selectionGroups: List<SelectionGroup>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProteinClick: (ProteinInfo) -> Unit,
    onAddToGroup: (String, String) -> Unit,
    onRemoveFromGroup: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var showGroupFilter by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onFilterClick = { showGroupFilter = true },
            selectedFilter = selectedFilter,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        if (selectedFilter != null) {
            FilterChip(
                filterName = selectionGroups.find { it.id == selectedFilter }?.name ?: "Unknown",
                onClearFilter = { selectedFilter = null },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filteredProteins = if (selectedFilter != null) {
                proteins.filter { protein ->
                    protein.selectionGroups.any { it.id == selectedFilter }
                }
            } else {
                proteins
            }

            items(filteredProteins) { protein ->
                ProteinCard(
                    protein = protein,
                    onClick = { onProteinClick(protein) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredProteins.isEmpty()) {
                item {
                    EmptyProteinListState(
                        hasFilter = selectedFilter != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }
            }
        }
    }

    if (showGroupFilter) {
        GroupFilterDialog(
            groups = selectionGroups,
            selectedGroupId = selectedFilter,
            onSelectGroup = { groupId ->
                selectedFilter = groupId
                showGroupFilter = false
            },
            onDismiss = { showGroupFilter = false }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    selectedFilter: String?,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search proteins or genes...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
                IconButton(onClick = onFilterClick) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter by group",
                        tint = if (selectedFilter != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun FilterChip(
    filterName: String,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = true,
        onClick = onClearFilter,
        label = { Text(filterName) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear filter",
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProteinCard(
    protein: ProteinInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = protein.geneName ?: protein.primaryId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (protein.geneName != null) {
                    Text(
                        text = protein.primaryId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    protein.log2FC?.let { fc ->
                        MetricBadge(
                            label = "Log2FC",
                            value = String.format("%.2f", fc),
                            color = when {
                                fc > 0 -> Color(0xFFD32F2F)
                                fc < 0 -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    protein.pValue?.let { p ->
                        MetricBadge(
                            label = "p-value",
                            value = String.format("%.2e", p),
                            color = if (protein.isSignificant) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (protein.selectionGroups.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        protein.selectionGroups.take(3).forEach { group ->
                            SelectionGroupBadge(group = group)
                        }
                        if (protein.selectionGroups.size > 3) {
                            Text(
                                text = "+${protein.selectionGroups.size - 3}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            if (protein.isSignificant) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Significant",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun SelectionGroupBadge(
    group: SelectionGroup,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color(android.graphics.Color.parseColor(group.color)).copy(alpha = 0.2f)
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color(android.graphics.Color.parseColor(group.color)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyProteinListState(
    hasFilter: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (hasFilter) Icons.Default.FilterList else Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilter) "No proteins in this group" else "No proteins found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasFilter) {
            Text(
                text = "Try selecting a different group",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupFilterDialog(
    groups: List<SelectionGroup>,
    selectedGroupId: String?,
    onSelectGroup: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Selection Group") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("All Proteins") },
                        leadingContent = {
                            RadioButton(
                                selected = selectedGroupId == null,
                                onClick = { onSelectGroup(null) }
                            )
                        },
                        modifier = Modifier.clickable { onSelectGroup(null) }
                    )
                }

                items(groups) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedGroupId == group.id,
                                onClick = { onSelectGroup(group.id) }
                            )
                        },
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(group.color)))
                            )
                        },
                        modifier = Modifier.clickable { onSelectGroup(group.id) }
                    )
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
