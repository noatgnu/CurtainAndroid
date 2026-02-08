package info.proteo.curtain.presentation.ui.ptm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import info.proteo.curtain.domain.model.AlignedSequencePair
import info.proteo.curtain.domain.model.CustomPTMSite
import info.proteo.curtain.domain.model.ExperimentalPTMSite
import info.proteo.curtain.domain.model.ParsedModification
import info.proteo.curtain.domain.model.PTMViewerState
import info.proteo.curtain.domain.model.ProteinDomain
import info.proteo.curtain.presentation.utils.DeviceUtils
import info.proteo.curtain.presentation.viewmodel.PTMViewerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTMViewerScreen(
    linkId: String,
    accession: String,
    pCutoff: Double = 0.05,
    fcCutoff: Double = 0.6,
    customSequences: Map<String, Any> = emptyMap(),
    variantCorrection: Map<String, Any> = emptyMap(),
    customPTMData: Map<String, Any> = emptyMap(),
    onNavigateBack: () -> Unit,
    viewModel: PTMViewerViewModel = hiltViewModel()
) {
    val isTablet = DeviceUtils.isTablet()

    if (isTablet) {
        Dialog(
            onDismissRequest = onNavigateBack,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                PTMViewerContent(
                    linkId = linkId,
                    accession = accession,
                    pCutoff = pCutoff,
                    fcCutoff = fcCutoff,
                    customSequences = customSequences,
                    variantCorrection = variantCorrection,
                    customPTMData = customPTMData,
                    onNavigateBack = onNavigateBack,
                    viewModel = viewModel,
                    useCloseIcon = true
                )
            }
        }
    } else {
        PTMViewerContent(
            linkId = linkId,
            accession = accession,
            pCutoff = pCutoff,
            fcCutoff = fcCutoff,
            customSequences = customSequences,
            variantCorrection = variantCorrection,
            customPTMData = customPTMData,
            onNavigateBack = onNavigateBack,
            viewModel = viewModel,
            useCloseIcon = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PTMViewerContent(
    linkId: String,
    accession: String,
    pCutoff: Double,
    fcCutoff: Double,
    customSequences: Map<String, Any>,
    variantCorrection: Map<String, Any>,
    customPTMData: Map<String, Any>,
    onNavigateBack: () -> Unit,
    viewModel: PTMViewerViewModel,
    useCloseIcon: Boolean
) {
    val ptmViewerState by viewModel.ptmViewerState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedSite by viewModel.selectedSite.collectAsState()
    val selectedModTypes by viewModel.selectedModTypes.collectAsState()
    val selectedCustomDatabases by viewModel.selectedCustomDatabases.collectAsState()

    LaunchedEffect(linkId, accession) {
        viewModel.loadData(linkId, accession, pCutoff, fcCutoff, customSequences, variantCorrection, customPTMData)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ptmViewerState?.geneName ?: accession)
                        Text(
                            text = accession,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            if (useCloseIcon) Icons.Default.Close else Icons.Default.ArrowBack,
                            if (useCloseIcon) "Close" else "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading PTM data...")
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            ptmViewerState != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    SequenceTab(
                        state = ptmViewerState!!,
                        selectedModTypes = selectedModTypes,
                        onModTypesChange = { viewModel.updateSelectedModTypes(it) },
                        selectedCustomDatabases = selectedCustomDatabases,
                        onCustomDatabasesChange = { viewModel.updateSelectedCustomDatabases(it) },
                        onSiteClick = { viewModel.selectSite(it) }
                    )
                }
            }
        }
    }

    if (selectedSite != null) {
        SiteDetailDialog(
            site = selectedSite!!,
            onDismiss = { viewModel.selectSite(null) }
        )
    }
}

@Composable
private fun SequenceTab(
    state: PTMViewerState,
    selectedModTypes: Set<String>,
    onModTypesChange: (Set<String>) -> Unit,
    selectedCustomDatabases: Set<String>,
    onCustomDatabasesChange: (Set<String>) -> Unit,
    onSiteClick: (ExperimentalPTMSite?) -> Unit
) {
    val experimentalPositions = state.experimentalSites.associateBy { it.position }
    val filteredModifications = state.parsedModifications.filter { it.modType in selectedModTypes }
    val modificationPositions = filteredModifications.associateBy { it.position }
    val filteredCustomPTMSites = state.customPTMSites
        .filterKeys { it in selectedCustomDatabases }
        .values
        .flatten()
    val customPTMPositions = filteredCustomPTMSites.associateBy { it.position }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ProteinInfoCard(state)
        }

        if (state.availableModTypes.isNotEmpty()) {
            item {
                Text(
                    text = "UniProt Modifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            item {
                ModificationTypeSelector(
                    availableTypes = state.availableModTypes,
                    selectedTypes = selectedModTypes,
                    onSelectionChange = onModTypesChange
                )
            }
        }

        if (state.availableCustomDatabases.isNotEmpty()) {
            item {
                Text(
                    text = "Custom PTM Databases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                CustomDatabaseSelector(
                    availableDatabases = state.availableCustomDatabases,
                    selectedDatabases = selectedCustomDatabases,
                    onSelectionChange = onCustomDatabasesChange
                )
            }
        }

        item {
            Text(
                text = "Sequence Alignment (${state.sequenceLength} aa)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        item {
            AlignmentLegend()
        }

        if (state.alignedSequencePair != null) {
            item {
                FullSequenceAlignmentCard(
                    alignedPair = state.alignedSequencePair,
                    experimentalPositions = experimentalPositions,
                    modificationPositions = modificationPositions,
                    customPTMPositions = customPTMPositions,
                    sourceLabel = state.experimentalSequenceSource ?: "Experimental"
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No experimental sequence available for alignment",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (state.domains.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Domains",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            item {
                DomainVisualization(
                    domains = state.domains,
                    sequenceLength = state.sequenceLength
                )
            }
        }
    }
}

@Composable
private fun ProteinInfoCard(state: PTMViewerState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.geneName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            state.proteinName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.accession,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${state.sequenceLength} aa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun DomainVisualization(
    domains: List<ProteinDomain>,
    sequenceLength: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
            ) {
                domains.forEach { domain ->
                    val startFraction = domain.startPosition.toFloat() / sequenceLength
                    val widthFraction = (domain.endPosition - domain.startPosition + 1).toFloat() / sequenceLength

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(widthFraction)
                            .offset(x = (startFraction * 300).dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            domains.forEach { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = domain.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${domain.startPosition}-${domain.endPosition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModificationTypeSelector(
    availableTypes: List<String>,
    selectedTypes: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableTypes.size) { index ->
                    val modType = availableTypes[index]
                    val isSelected = modType in selectedTypes
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newSelection = if (isSelected) {
                                selectedTypes - modType
                            } else {
                                selectedTypes + modType
                            }
                            onSelectionChange(newSelection)
                        },
                        label = { Text(modType, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2196F3).copy(alpha = 0.3f)
                        )
                    )
                }
            }
            Text(
                text = "${selectedTypes.size} of ${availableTypes.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CustomDatabaseSelector(
    availableDatabases: List<String>,
    selectedDatabases: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableDatabases.size) { index ->
                    val dbName = availableDatabases[index]
                    val isSelected = dbName in selectedDatabases
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newSelection = if (isSelected) {
                                selectedDatabases - dbName
                            } else {
                                selectedDatabases + dbName
                            }
                            onSelectionChange(newSelection)
                        },
                        label = { Text(dbName, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF9C27B0).copy(alpha = 0.3f)
                        )
                    )
                }
            }
            Text(
                text = "${selectedDatabases.size} of ${availableDatabases.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SiteDetailDialog(
    site: ExperimentalPTMSite,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${site.residue}${site.position}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Primary ID", site.primaryId)
                DetailRow("Position", site.position.toString())
                DetailRow("Residue", site.residue.toString())
                site.modification?.let { DetailRow("Modification", it) }
                site.peptideSequence?.let { DetailRow("Peptide", it) }
                site.foldChange?.let { DetailRow("Fold Change", String.format("%.4f", it)) }
                site.pValue?.let { DetailRow("P-value", String.format("%.4e", it)) }
                site.comparison?.let { DetailRow("Comparison", it) }
                DetailRow("Significant", if (site.isSignificant) "Yes" else "No")
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AlignmentLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AlignmentLegendItem(color = Color(0xFF4CAF50), label = "Match")
            AlignmentLegendItem(color = Color(0xFFF44336), label = "Mismatch")
            AlignmentLegendItem(color = Color(0xFF9E9E9E), label = "Gap")
            AlignmentLegendItem(color = Color(0xFFFF5722), label = "Experimental")
            AlignmentLegendItem(color = Color(0xFF2196F3), label = "UniProt")
            AlignmentLegendItem(color = Color(0xFF9C27B0), label = "Custom")
        }
    }
}

@Composable
private fun AlignmentLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun FullSequenceAlignmentCard(
    alignedPair: AlignedSequencePair,
    experimentalPositions: Map<Int, ExperimentalPTMSite>,
    modificationPositions: Map<Int, ParsedModification>,
    customPTMPositions: Map<Int, CustomPTMSite> = emptyMap(),
    sourceLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Source: $sourceLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val charWidthDp = 7.dp
                val labelWidthDp = 44.dp
                val availableWidth = maxWidth - labelWidthDp
                val chunkSize = maxOf(20, (availableWidth / charWidthDp).toInt())

                Column(modifier = Modifier.fillMaxWidth()) {
                    val alignedLength = alignedPair.experimentalAligned.length
                    val numChunks = (alignedLength + chunkSize - 1) / chunkSize

                    for (chunkIdx in 0 until numChunks) {
                        val start = chunkIdx * chunkSize
                        val end = minOf(start + chunkSize, alignedLength)

                        val expChunk = alignedPair.experimentalAligned.substring(start, end)
                        val canChunk = alignedPair.canonicalAligned.substring(start, end)

                        AlignmentChunk(
                            experimentalChunk = expChunk,
                            canonicalChunk = canChunk,
                            startIndex = start,
                            experimentalPositions = experimentalPositions,
                            modificationPositions = modificationPositions,
                            customPTMPositions = customPTMPositions,
                            expPositionMap = alignedPair.experimentalPositionMap,
                            canPositionMap = alignedPair.canonicalPositionMap
                        )

                        if (chunkIdx < numChunks - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignmentChunk(
    experimentalChunk: String,
    canonicalChunk: String,
    startIndex: Int,
    experimentalPositions: Map<Int, ExperimentalPTMSite>,
    modificationPositions: Map<Int, ParsedModification>,
    customPTMPositions: Map<Int, CustomPTMSite> = emptyMap(),
    expPositionMap: Map<Int, Int>,
    canPositionMap: Map<Int, Int>
) {
    val expPtmAlignedPositions = experimentalPositions.keys.mapNotNull { pos ->
        expPositionMap[pos]
    }.filter { it >= startIndex && it < startIndex + experimentalChunk.length }
        .map { it - startIndex }
        .toSet()

    val uniprotModAlignedPositions = modificationPositions.keys.mapNotNull { pos ->
        canPositionMap[pos]
    }.filter { it >= startIndex && it < startIndex + canonicalChunk.length }
        .map { it - startIndex }
        .toSet()

    val customPTMAlignedPositions = customPTMPositions.keys.mapNotNull { pos ->
        canPositionMap[pos]
    }.filter { it >= startIndex && it < startIndex + canonicalChunk.length }
        .map { it - startIndex }
        .toSet()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "    ",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp)
            )
            experimentalChunk.forEachIndexed { idx, _ ->
                val globalPos = startIndex + idx + 1
                val showNumber = globalPos % 10 == 0
                Text(
                    text = if (showNumber) "|" else if (globalPos % 5 == 0) "." else " ",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 0.5.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Exp",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp)
            )
            experimentalChunk.forEachIndexed { idx, char ->
                val canChar = canonicalChunk.getOrNull(idx) ?: '-'
                val isExpPTM = idx in expPtmAlignedPositions

                val backgroundColor = when {
                    char == '-' -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                    isExpPTM -> Color(0xFFFF5722).copy(alpha = 0.4f)
                    char == canChar -> Color.Transparent
                    else -> Color(0xFFF44336).copy(alpha = 0.3f)
                }

                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(backgroundColor)
                        .padding(horizontal = 0.5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "    ",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp)
            )
            experimentalChunk.forEachIndexed { idx, expChar ->
                val canChar = canonicalChunk.getOrNull(idx) ?: '-'
                val indicator = when {
                    expChar == '-' || canChar == '-' -> " "
                    expChar == canChar -> "|"
                    else -> "."
                }
                Text(
                    text = indicator,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when (indicator) {
                        "|" -> Color(0xFF4CAF50)
                        "." -> Color(0xFFF44336)
                        else -> Color.Transparent
                    },
                    modifier = Modifier.padding(horizontal = 0.5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ref",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(36.dp)
            )
            canonicalChunk.forEachIndexed { idx, char ->
                val expChar = experimentalChunk.getOrNull(idx) ?: '-'
                val isUniprotMod = idx in uniprotModAlignedPositions

                val backgroundColor = when {
                    char == '-' -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                    isUniprotMod -> Color(0xFF2196F3).copy(alpha = 0.4f)
                    char == expChar -> Color.Transparent
                    else -> Color(0xFFF44336).copy(alpha = 0.3f)
                }

                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(backgroundColor)
                        .padding(horizontal = 0.5.dp)
                )
            }
        }

        if (customPTMAlignedPositions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(1.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cust",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.width(36.dp)
                )
                canonicalChunk.forEachIndexed { idx, char ->
                    val isCustomPTM = idx in customPTMAlignedPositions

                    val backgroundColor = when {
                        char == '-' -> Color.Transparent
                        isCustomPTM -> Color(0xFF9C27B0).copy(alpha = 0.4f)
                        else -> Color.Transparent
                    }

                    Text(
                        text = if (isCustomPTM) char.toString() else " ",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(backgroundColor)
                            .padding(horizontal = 0.5.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format("%4d", startIndex + 1),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = (startIndex + experimentalChunk.length).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

