package info.proteo.curtain.presentation.ui.crosssearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import info.proteo.curtain.data.local.entity.SavedCrossDatasetSearchEntity
import info.proteo.curtain.domain.model.DatasetComparisonResult
import info.proteo.curtain.domain.model.ProteinDetailedReport
import info.proteo.curtain.domain.model.ProteinSearchSummary
import info.proteo.curtain.presentation.utils.DeviceUtils
import info.proteo.curtain.presentation.viewmodel.CrossDatasetSearchViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun CrossDatasetResultsScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel
) {
    val isTablet = DeviceUtils.isTablet()

    if (isTablet) {
        TabletResultsScreen(navController = navController, viewModel = viewModel)
    } else {
        PhoneResultsScreen(navController = navController, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletResultsScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val currentSavedSearchId by viewModel.currentSavedSearchId.collectAsState()
    val selectedProtein by viewModel.selectedProtein.collectAsState()
    val error by viewModel.error.collectAsState()
    val matrixData by viewModel.matrixData.collectAsState()
    val isLoadingMatrix by viewModel.isLoadingMatrix.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(searchResults) {
        if (searchResults != null && matrixData == null) {
            viewModel.buildMatrix()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showSaveDialog) {
        SaveSearchDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCurrentSearch(name)
                showSaveDialog = false
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            TopAppBar(
                title = { Text("Searches", style = MaterialTheme.typography.titleSmall) },
                actions = {
                    if (searchResults != null && currentSavedSearchId == null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save search")
                        }
                    }
                }
            )

            SavedSearchesList(
                savedSearches = savedSearches,
                currentSearchId = currentSavedSearchId,
                hasUnsavedResults = searchResults != null && currentSavedSearchId == null,
                onLoadSearch = { viewModel.loadSavedSearch(it) },
                onDeleteSearch = { viewModel.deleteSavedSearch(it) },
                onSaveCurrentSearch = { showSaveDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            TopAppBar(
                title = {
                    Text(
                        "Proteins Found (${searchResults?.proteinSummaries?.size ?: 0})",
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                actions = {
                    if (searchResults != null && currentSavedSearchId == null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save search")
                        }
                    }
                }
            )

            if (searchResults != null) {
                ProteinSummaryList(
                    summaries = searchResults!!.proteinSummaries,
                    selectedProtein = selectedProtein,
                    onProteinSelected = { viewModel.selectProtein(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                EmptyStateMessage(
                    message = "Select a search to view proteins",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val selectedSummary = searchResults?.proteinSummaries?.find {
                    (it.primaryId ?: it.searchTerm) == (selectedProtein?.primaryId ?: selectedProtein?.searchTerm)
                }

                TopAppBar(
                    title = {
                        Text(
                            if (selectedProtein != null) {
                                selectedSummary?.geneName ?: selectedSummary?.primaryId ?: "Dataset vs Comparison"
                            } else {
                                "Dataset vs Comparison"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )

                when {
                    selectedProtein == null -> {
                        EmptyStateMessage(
                            message = "Select a protein to view matrix",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    isLoadingMatrix -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    matrixData != null && selectedProtein != null -> {
                        CrossDatasetMatrixView(
                            matrix = matrixData!!,
                            isLoading = isLoadingMatrix,
                            selectedProteinId = selectedProtein!!.primaryId ?: selectedProtein!!.searchTerm,
                            onDatasetClick = { linkId ->
                                navController.navigate("curtain_details/$linkId")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        EmptyStateMessage(
                            message = "Building matrix...",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneResultsScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val currentSavedSearchId by viewModel.currentSavedSearchId.collectAsState()
    val selectedProtein by viewModel.selectedProtein.collectAsState()
    val error by viewModel.error.collectAsState()
    val matrixData by viewModel.matrixData.collectAsState()
    val isLoadingMatrix by viewModel.isLoadingMatrix.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentPanel by rememberSaveable { mutableStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(searchResults) {
        if (searchResults != null && matrixData == null) {
            viewModel.buildMatrix()
        }
    }

    if (showSaveDialog) {
        SaveSearchDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCurrentSearch(name)
                showSaveDialog = false
            }
        )
    }

    val selectedSummary = searchResults?.proteinSummaries?.find {
        (it.primaryId ?: it.searchTerm) == (selectedProtein?.primaryId ?: selectedProtein?.searchTerm)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentPanel) {
                            0 -> "Saved Searches"
                            1 -> "Proteins Found (${searchResults?.proteinSummaries?.size ?: 0})"
                            else -> selectedSummary?.geneName ?: selectedSummary?.primaryId ?: "Dataset vs Comparison"
                        }
                    )
                },
                navigationIcon = {
                    if (currentPanel > 0) {
                        IconButton(onClick = { currentPanel-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentPanel == 1 && searchResults != null && currentSavedSearchId == null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save search")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentPanel) {
                0 -> {
                    SavedSearchesList(
                        savedSearches = savedSearches,
                        currentSearchId = currentSavedSearchId,
                        hasUnsavedResults = searchResults != null && currentSavedSearchId == null,
                        onLoadSearch = {
                            viewModel.loadSavedSearch(it)
                            currentPanel = 1
                        },
                        onDeleteSearch = { viewModel.deleteSavedSearch(it) },
                        onSaveCurrentSearch = { showSaveDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (searchResults != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable { currentPanel = 1 },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Current Search",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${searchResults!!.proteinSummaries.size} proteins found",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "View"
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (searchResults != null) {
                        ProteinSummaryList(
                            summaries = searchResults!!.proteinSummaries,
                            selectedProtein = selectedProtein,
                            onProteinSelected = {
                                viewModel.selectProtein(it)
                                currentPanel = 2
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        EmptyStateMessage(
                            message = "No search results",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                2 -> {
                    when {
                        isLoadingMatrix -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        matrixData != null && selectedProtein != null -> {
                            CrossDatasetMatrixView(
                                matrix = matrixData!!,
                                isLoading = isLoadingMatrix,
                                selectedProteinId = selectedProtein!!.primaryId ?: selectedProtein!!.searchTerm,
                                onDatasetClick = { linkId ->
                                    navController.navigate("curtain_details/$linkId")
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            EmptyStateMessage(
                                message = "Building matrix...",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSearchesList(
    savedSearches: List<SavedCrossDatasetSearchEntity>,
    currentSearchId: Long?,
    hasUnsavedResults: Boolean,
    onLoadSearch: (Long) -> Unit,
    onDeleteSearch: (Long) -> Unit,
    onSaveCurrentSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (hasUnsavedResults) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Current (unsaved)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onSaveCurrentSearch != null) {
                                IconButton(
                                    onClick = onSaveCurrentSearch,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Save search",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Active",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (savedSearches.isEmpty() && !hasUnsavedResults) {
            item {
                Text(
                    "No saved searches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(savedSearches, key = { it.id }) { search ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable { onLoadSearch(search.id) },
                colors = if (currentSearchId == search.id) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                }
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
                            text = search.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${search.proteinCount} proteins • ${dateFormat.format(java.util.Date(search.created))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        if (currentSearchId == search.id) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Active",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onDeleteSearch(search.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProteinSummaryList(
    summaries: List<ProteinSearchSummary>,
    selectedProtein: ProteinSearchSummary?,
    onProteinSelected: (ProteinSearchSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSummaries = remember(summaries, searchQuery) {
        if (searchQuery.isEmpty()) {
            summaries
        } else {
            summaries.filter { summary ->
                summary.primaryId?.contains(searchQuery, ignoreCase = true) == true ||
                        summary.geneName?.contains(searchQuery, ignoreCase = true) == true ||
                        summary.searchTerm.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text("Filter proteins...") },
            singleLine = true
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(filteredSummaries, key = { it.primaryId ?: it.searchTerm }) { summary ->
                val isSelected = (selectedProtein?.primaryId ?: selectedProtein?.searchTerm) ==
                        (summary.primaryId ?: summary.searchTerm)
                ProteinSummaryItem(
                    summary = summary,
                    isSelected = isSelected,
                    onClick = { onProteinSelected(summary) }
                )
            }
        }
    }
}

@Composable
private fun ProteinSummaryItem(
    summary: ProteinSearchSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val displayName = summary.geneName ?: summary.primaryId ?: summary.searchTerm
    val hasGeneName = summary.geneName != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (summary.hasSignificantResult) {
                        Text(
                            "★",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (hasGeneName && summary.primaryId != null) {
                    Text(
                        text = summary.primaryId,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!hasGeneName && summary.primaryId != null && summary.primaryId != displayName) {
                    Text(
                        text = "(${summary.searchTerm})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${summary.datasetsFoundIn}/${summary.totalDatasetsSearched} datasets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                Icons.Default.OpenInNew,
                contentDescription = "View details",
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ProteinDetailsPanel(
    report: ProteinDetailedReport,
    onNavigateToDataset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = report.geneName ?: report.primaryId ?: report.searchTerm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (report.geneName != null && report.primaryId != null) {
                    Text(
                        text = report.primaryId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Found in ${report.datasetsFoundIn} of ${report.totalDatasetsSearched} datasets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            "Dataset Comparisons",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        report.results.forEach { result ->
            DatasetComparisonCard(
                result = result,
                onNavigate = { onNavigateToDataset(result.datasetInfo.linkId) }
            )
        }
    }
}

@Composable
private fun DatasetComparisonCard(
    result: DatasetComparisonResult,
    onNavigate: () -> Unit
) {
    val backgroundColor = when {
        !result.found -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        result.foldChange == null -> MaterialTheme.colorScheme.surface
        result.foldChange > 0 -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        result.foldChange < 0 -> Color(0xFFF44336).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onNavigate),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
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
                    text = result.datasetInfo.datasetDescription.ifEmpty { "Untitled Dataset" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.datasetInfo.comparison,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (result.found) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = result.foldChange?.let { String.format("%.2f", it) } ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    result.foldChange == null -> MaterialTheme.colorScheme.onSurface
                                    result.foldChange > 0 -> Color(0xFF2E7D32)
                                    else -> Color(0xFFC62828)
                                }
                            )
                            if (result.isSignificant) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Significant",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        result.pValue?.let { p ->
                            Text(
                                text = String.format("p=%.2e", p),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Open dataset",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Not found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaveSearchDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var searchName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val defaultName = remember { "Search ${dateFormat.format(System.currentTimeMillis())}" }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Search") },
        text = {
            Column {
                Text(
                    "Save this search to access results later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchName,
                    onValueChange = { searchName = it },
                    label = { Text("Search name") },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onSave(searchName.ifBlank { defaultName }) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
