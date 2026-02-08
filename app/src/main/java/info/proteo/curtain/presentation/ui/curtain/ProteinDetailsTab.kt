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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import info.proteo.curtain.domain.model.SelectionGroup
import kotlin.math.abs

data class ProteinInfo(
    val primaryId: String,
    val geneName: String?,
    val log2FC: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val selectionGroups: List<SelectionGroup>,
    val accession: String? = null,
    val position: String? = null,
    val positionPeptide: String? = null,
    val peptideSequence: String? = null,
    val score: Double? = null
)

data class AccessionGroup(
    val accession: String,
    val geneName: String?,
    val sites: List<ProteinInfo>,
    val significantCount: Int
)

class SequenceResolver(
    private val customSequences: Map<String, Any>,
    private val variantCorrection: Map<String, Any>,
    private val sequenceCache: Map<String, String?>
) {
    fun resolveSequence(accession: String?): String? {
        if (accession == null) return null

        val customSeq = customSequences[accession]
        if (customSeq is String && customSeq.isNotEmpty()) return customSeq

        val correctedId = variantCorrection[accession]
        if (correctedId is String && correctedId.isNotEmpty()) {
            val seq = sequenceCache[correctedId]
            if (!seq.isNullOrEmpty()) return seq
        }

        return sequenceCache[accession]
    }
}

@Composable
fun ProteinDetailsTab(
    proteins: List<ProteinInfo>,
    selectionGroups: List<SelectionGroup>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProteinClick: (ProteinInfo) -> Unit,
    onAddToGroup: (String, String) -> Unit,
    onRemoveFromGroup: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    isPTM: Boolean = false,
    accessionGroups: List<AccessionGroup> = emptyList(),
    sequenceResolver: SequenceResolver? = null,
    onViewPTMViewer: ((String) -> Unit)? = null,
    getVariantSourceLabel: ((String) -> String)? = null,
    getAvailableIsoforms: (suspend (String) -> List<String>)? = null,
    onSetVariant: ((String, String?) -> Unit)? = null,
    onSetCustomSequence: ((String, String?) -> Unit)? = null,
    onClearVariant: ((String) -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var showGroupFilter by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onFilterClick = { showGroupFilter = true },
            selectedFilter = selectedFilter,
            isPTM = isPTM,
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

        if (isPTM && accessionGroups.isNotEmpty()) {
            PTMGroupedList(
                accessionGroups = accessionGroups,
                selectedFilter = selectedFilter,
                onSiteClick = onProteinClick,
                sequenceResolver = sequenceResolver,
                onViewPTMViewer = onViewPTMViewer,
                getVariantSourceLabel = getVariantSourceLabel,
                getAvailableIsoforms = getAvailableIsoforms,
                onSetVariant = onSetVariant,
                onSetCustomSequence = onSetCustomSequence,
                onClearVariant = onClearVariant,
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
                            isPTM = isPTM,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                }
            }
        }
    }

    if (showGroupFilter) {
        GroupFilterDialog(
            groups = selectionGroups,
            selectedGroupId = selectedFilter,
            isPTM = isPTM,
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
    isPTM: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(if (isPTM) "Search sites or accessions..." else "Search proteins or genes...") },
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

                if (protein.accession != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        MetricBadge(
                            label = "Accession",
                            value = protein.accession,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        protein.position?.let { pos ->
                            MetricBadge(
                                label = "Pos",
                                value = pos,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        protein.score?.let { s ->
                            MetricBadge(
                                label = "Score",
                                value = String.format("%.2f", s),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
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

private fun parseColorSafe(colorString: String): Color {
    return try {
        val normalizedColor = when {
            colorString.startsWith("rgb(") || colorString.startsWith("rgba(") -> {
                val values = colorString
                    .removePrefix("rgba(").removePrefix("rgb(")
                    .removeSuffix(")")
                    .split(",")
                    .map { it.trim().toIntOrNull() ?: 0 }
                if (values.size >= 3) {
                    String.format("#%02X%02X%02X", values[0], values[1], values[2])
                } else colorString
            }
            colorString.matches(Regex("^[0-9A-Fa-f]{6}$")) -> "#$colorString"
            colorString.matches(Regex("^[0-9A-Fa-f]{3}$")) -> {
                "#${colorString[0]}${colorString[0]}${colorString[1]}${colorString[1]}${colorString[2]}${colorString[2]}"
            }
            else -> colorString
        }
        Color(android.graphics.Color.parseColor(normalizedColor))
    } catch (e: Exception) {
        Color.Gray
    }
}

@Composable
private fun SelectionGroupBadge(
    group: SelectionGroup,
    modifier: Modifier = Modifier
) {
    val parsedColor = parseColorSafe(group.color)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = parsedColor.copy(alpha = 0.2f)
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.labelSmall,
            color = parsedColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HighlightedPeptideSequence(
    peptideSequence: String,
    positionPeptide: String?,
    isFullProteinSequence: Boolean = false,
    modifier: Modifier = Modifier
) {
    val positions = positionPeptide
        ?.split(";")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.sorted()
        ?: emptyList()

    val contextWindow = 7

    val displayData = if (isFullProteinSequence && positions.isNotEmpty()) {
        val firstPos = positions.first() - 1
        val windowStart = maxOf(0, firstPos - contextWindow)
        val windowEnd = minOf(peptideSequence.length, firstPos + contextWindow + 1)
        val windowSeq = peptideSequence.substring(windowStart, windowEnd)
        val adjustedPositions = positions.map { it - windowStart }
        val prefix = if (windowStart > 0) "..." else ""
        val suffix = if (windowEnd < peptideSequence.length) "..." else ""
        Triple(windowSeq, adjustedPositions, prefix to suffix)
    } else {
        val cleanedPeptide = peptideSequence
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\{[^}]*}"), "")
            .replace("_", "")
        Triple(cleanedPeptide, positions, "" to "")
    }

    val (sequence, adjustedPositions, ellipsis) = displayData

    val annotatedText = buildAnnotatedString {
        if (adjustedPositions.isEmpty() || sequence.isEmpty()) {
            append(sequence.ifEmpty { peptideSequence })
            return@buildAnnotatedString
        }

        if (ellipsis.first.isNotEmpty()) {
            withStyle(SpanStyle(color = Color.Gray)) {
                append(ellipsis.first)
            }
        }

        var currentIndex = 0
        for (pos in adjustedPositions) {
            val adjustedPos = pos - 1
            if (adjustedPos < 0 || adjustedPos >= sequence.length) continue

            if (currentIndex < adjustedPos) {
                append(sequence.substring(currentIndex, adjustedPos))
            }

            withStyle(SpanStyle(color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)) {
                append(sequence[adjustedPos].toString())
            }
            currentIndex = adjustedPos + 1
        }

        if (currentIndex < sequence.length) {
            append(sequence.substring(currentIndex))
        }

        if (ellipsis.second.isNotEmpty()) {
            withStyle(SpanStyle(color = Color.Gray)) {
                append(ellipsis.second)
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun EmptyProteinListState(
    hasFilter: Boolean,
    isPTM: Boolean = false,
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
            text = if (hasFilter) {
                if (isPTM) "No sites in this group" else "No proteins in this group"
            } else {
                if (isPTM) "No sites found" else "No proteins found"
            },
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
    isPTM: Boolean = false,
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
                        headlineContent = { Text(if (isPTM) "All Sites" else "All Proteins") },
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

@Composable
private fun PTMGroupedList(
    accessionGroups: List<AccessionGroup>,
    selectedFilter: String?,
    onSiteClick: (ProteinInfo) -> Unit,
    sequenceResolver: SequenceResolver? = null,
    onViewPTMViewer: ((String) -> Unit)? = null,
    getVariantSourceLabel: ((String) -> String)? = null,
    getAvailableIsoforms: (suspend (String) -> List<String>)? = null,
    onSetVariant: ((String, String?) -> Unit)? = null,
    onSetCustomSequence: ((String, String?) -> Unit)? = null,
    onClearVariant: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filteredGroups = if (selectedFilter != null) {
            accessionGroups.map { group ->
                group.copy(sites = group.sites.filter { site ->
                    site.selectionGroups.any { it.id == selectedFilter }
                })
            }.filter { it.sites.isNotEmpty() }
        } else {
            accessionGroups
        }

        filteredGroups.forEach { group ->
            val isExpanded = expandedGroups[group.accession] ?: false

            item(key = "header_${group.accession}") {
                AccessionGroupHeader(
                    group = group,
                    isExpanded = isExpanded,
                    onToggle = { expandedGroups[group.accession] = !isExpanded },
                    onViewPTMViewer = onViewPTMViewer,
                    variantSourceLabel = getVariantSourceLabel?.invoke(group.accession),
                    getAvailableIsoforms = getAvailableIsoforms,
                    onSetVariant = onSetVariant,
                    onSetCustomSequence = onSetCustomSequence,
                    onClearVariant = onClearVariant
                )
            }

            if (isExpanded) {
                items(
                    items = group.sites,
                    key = { "site_${it.primaryId}" }
                ) { site ->
                    SiteCard(
                        site = site,
                        onClick = { onSiteClick(site) },
                        sequenceResolver = sequenceResolver,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                    )
                }
            }
        }

        if (filteredGroups.isEmpty()) {
            item {
                EmptyProteinListState(
                    hasFilter = selectedFilter != null,
                    isPTM = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun AccessionGroupHeader(
    group: AccessionGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onViewPTMViewer: ((String) -> Unit)? = null,
    variantSourceLabel: String? = null,
    getAvailableIsoforms: (suspend (String) -> List<String>)? = null,
    onSetVariant: ((String, String?) -> Unit)? = null,
    onSetCustomSequence: ((String, String?) -> Unit)? = null,
    onClearVariant: ((String) -> Unit)? = null
) {
    var showVariantDialog by remember { mutableStateOf(false) }
    val groupSelections = remember(group.sites) {
        group.sites
            .flatMap { it.selectionGroups }
            .distinctBy { it.id }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.geneName ?: group.accession,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.geneName != null) {
                    Text(
                        text = group.accession,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (getAvailableIsoforms != null) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { showVariantDialog = true },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = variantSourceLabel ?: group.accession,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (groupSelections.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        groupSelections.take(3).forEach { selGroup ->
                            SelectionGroupBadge(group = selGroup)
                        }
                        if (groupSelections.size > 3) {
                            Text(
                                text = "+${groupSelections.size - 3}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.sites.size} sites",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (group.significantCount > 0) {
                    Text(
                        text = "${group.significantCount} sig.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
                if (onViewPTMViewer != null) {
                    IconButton(
                        onClick = { onViewPTMViewer(group.accession) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View PTM Viewer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showVariantDialog && getAvailableIsoforms != null) {
        VariantSelectionDialog(
            accession = group.accession,
            currentVariant = variantSourceLabel,
            getAvailableIsoforms = getAvailableIsoforms,
            onSelectVariant = { variant ->
                onSetVariant?.invoke(group.accession, variant)
                showVariantDialog = false
            },
            onSetCustomSequence = { sequence ->
                onSetCustomSequence?.invoke(group.accession, sequence)
                showVariantDialog = false
            },
            onClearVariant = {
                onClearVariant?.invoke(group.accession)
                showVariantDialog = false
            },
            onDismiss = { showVariantDialog = false }
        )
    }
}

@Composable
private fun VariantSelectionDialog(
    accession: String,
    currentVariant: String?,
    getAvailableIsoforms: suspend (String) -> List<String>,
    onSelectVariant: (String) -> Unit,
    onSetCustomSequence: (String) -> Unit,
    onClearVariant: () -> Unit,
    onDismiss: () -> Unit
) {
    var isoforms by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedIsoform by remember { mutableStateOf<String?>(null) }
    var customSequence by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(accession) {
        isLoading = true
        isoforms = getAvailableIsoforms(accession)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Variant Assignment") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Loading isoforms...")
                    }
                } else {
                    Text(
                        text = "Isoforms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedIsoform ?: "Select isoform...",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            if (isoforms.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No isoforms available") },
                                    onClick = { showDropdown = false },
                                    enabled = false
                                )
                            } else {
                                isoforms.forEach { isoform ->
                                    DropdownMenuItem(
                                        text = { Text(isoform) },
                                        onClick = {
                                            selectedIsoform = isoform
                                            customSequence = ""
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Or enter custom sequence",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = customSequence,
                        onValueChange = {
                            customSequence = it
                            if (it.isNotEmpty()) selectedIsoform = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste protein sequence...") },
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClearVariant) {
                    Text("Remove")
                }
                Button(
                    onClick = {
                        when {
                            customSequence.isNotEmpty() -> onSetCustomSequence(customSequence)
                            selectedIsoform != null -> onSelectVariant(selectedIsoform!!)
                        }
                    },
                    enabled = selectedIsoform != null || customSequence.isNotEmpty()
                ) {
                    Text("Set")
                }
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
private fun SiteCard(
    site: ProteinInfo,
    onClick: () -> Unit,
    sequenceResolver: SequenceResolver? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    site.position?.let { pos ->
                        Text(
                            text = pos,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = site.primaryId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                site.peptideSequence?.let { seq ->
                    val resolvedSeq = sequenceResolver?.resolveSequence(site.accession)
                    if (resolvedSeq != null && site.position != null) {
                        HighlightedPeptideSequence(
                            peptideSequence = resolvedSeq,
                            positionPeptide = site.position,
                            isFullProteinSequence = true
                        )
                    } else {
                        HighlightedPeptideSequence(
                            peptideSequence = seq,
                            positionPeptide = site.positionPeptide
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    site.log2FC?.let { fc ->
                        MetricBadge(
                            label = "FC",
                            value = String.format("%.2f", fc),
                            color = when {
                                fc > 0 -> Color(0xFFD32F2F)
                                fc < 0 -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    site.pValue?.let { p ->
                        MetricBadge(
                            label = "p",
                            value = String.format("%.1e", p),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    site.score?.let { s ->
                        MetricBadge(
                            label = "Score",
                            value = String.format("%.1f", s),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (site.selectionGroups.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        site.selectionGroups.take(3).forEach { group ->
                            SelectionGroupBadge(group = group)
                        }
                        if (site.selectionGroups.size > 3) {
                            Text(
                                text = "+${site.selectionGroups.size - 3}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            if (site.isSignificant) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
    }
}
