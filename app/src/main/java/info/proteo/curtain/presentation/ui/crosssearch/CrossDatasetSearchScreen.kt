package info.proteo.curtain.presentation.ui.crosssearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.data.local.entity.SavedCrossDatasetSearchEntity
import info.proteo.curtain.domain.model.CrossDatasetAdvancedFilterParams
import info.proteo.curtain.domain.model.DatasetComparisonResult
import info.proteo.curtain.domain.model.DatasetProcessingStatus
import info.proteo.curtain.domain.model.DatasetScope
import info.proteo.curtain.domain.model.ProcessingState
import info.proteo.curtain.domain.model.ProteinDetailedReport
import info.proteo.curtain.domain.model.ProteinSearchSummary
import info.proteo.curtain.domain.model.ProteinSortOption
import info.proteo.curtain.domain.service.SearchType
import info.proteo.curtain.presentation.ui.components.CurtainListMode
import info.proteo.curtain.presentation.ui.components.CurtainListPanel
import info.proteo.curtain.presentation.utils.DeviceUtils
import info.proteo.curtain.presentation.viewmodel.CrossDatasetSearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun CrossDatasetSearchScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel,
    onSearchComplete: () -> Unit = {}
) {
    val isTablet = DeviceUtils.isTablet()

    if (isTablet) {
        TabletCrossDatasetSearchScreen(
            navController = navController,
            viewModel = viewModel,
            onSearchComplete = onSearchComplete
        )
    } else {
        PhoneCrossDatasetSearchScreen(
            navController = navController,
            viewModel = viewModel,
            onSearchComplete = onSearchComplete
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletCrossDatasetSearchScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel,
    onSearchComplete: () -> Unit
) {
    val searchInput by viewModel.searchInput.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val significantOnly by viewModel.significantOnly.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val advancedFiltering by viewModel.advancedFiltering.collectAsState()
    val selectedDatasetIds by viewModel.selectedDatasetIds.collectAsState()
    val availableDatasets by viewModel.availableDatasets.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val currentSavedSearchId by viewModel.currentSavedSearchId.collectAsState()
    val filterLists by viewModel.filterLists.collectAsState()
    val isSyncingFilters by viewModel.isSyncingFilters.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val expandedCollectionIds by viewModel.expandedCollectionIds.collectAsState()
    val collectionSessions by viewModel.collectionSessions.collectAsState()
    val datasetStatuses by viewModel.datasetStatuses.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showFilterListPicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSavedSearches by remember { mutableStateOf(false) }
    var previousSearchResults by remember { mutableStateOf(searchResults) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(searchResults) {
        if (previousSearchResults == null && searchResults != null) {
            onSearchComplete()
        }
        previousSearchResults = searchResults
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

    if (showFilterListPicker) {
        FilterListPickerDialog(
            filterLists = filterLists,
            categories = categories,
            isSyncing = isSyncingFilters,
            onDismiss = { showFilterListPicker = false },
            onSelect = { filterList ->
                viewModel.loadFilterListData(filterList)
                showFilterListPicker = false
            },
            onSync = { viewModel.syncFilterLists() }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Select Datasets") }
                )

                CurtainListPanel(
                    mode = CurtainListMode.SELECTION,
                    datasets = availableDatasets,
                    collections = collections,
                    expandedCollectionIds = expandedCollectionIds,
                    collectionSessions = collectionSessions,
                    selectedDatasetIds = selectedDatasetIds,
                    onToggleDatasetSelection = { viewModel.toggleDatasetSelection(it) },
                    onSelectAllDatasets = { viewModel.selectAllDatasets() },
                    onDeselectAllDatasets = { viewModel.deselectAllDatasets() },
                    onToggleCollectionExpanded = { viewModel.toggleCollectionExpanded(it) },
                    onSelectAllInCollection = { viewModel.selectAllSessionsInCollection(it) },
                    onDeselectAllInCollection = { viewModel.deselectAllSessionsInCollection(it) },
                    onToggleSessionSelection = { viewModel.toggleSessionSelection(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Cross-Dataset Search") },
                    actions = {
                        IconButton(onClick = { showFilterListPicker = true }) {
                            BadgedBox(
                                badge = {
                                    if (filterLists.isNotEmpty()) {
                                        Badge { Text(filterLists.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FilterList, "Filter Lists")
                            }
                        }
                        IconButton(onClick = { showSavedSearches = !showSavedSearches }) {
                            Icon(
                                if (showSavedSearches) Icons.Default.Bookmark else Icons.Default.History,
                                contentDescription = "Saved searches"
                            )
                        }
                        if (searchResults != null) {
                            IconButton(onClick = { showSaveDialog = true }) {
                                Icon(
                                    if (currentSavedSearchId != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save search"
                                )
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = showSavedSearches,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    SavedSearchesSection(
                        savedSearches = savedSearches,
                        currentSearchId = currentSavedSearchId,
                        onLoadSearch = { id ->
                            viewModel.loadSavedSearch(id)
                            showSavedSearches = false
                        },
                        onDeleteSearch = { viewModel.deleteSavedSearch(it) },
                        onRenameSearch = { id, name -> viewModel.renameSavedSearch(id, name) }
                    )
                }

                SearchInputSection(
                    searchInput = searchInput,
                    onSearchInputChange = { viewModel.updateSearchInput(it) },
                    searchType = searchType,
                    onSearchTypeChange = { viewModel.setSearchType(it) },
                    significantOnly = significantOnly,
                    onSignificantOnlyChange = { viewModel.toggleSignificantOnly() },
                    useRegex = useRegex,
                    onUseRegexChange = { viewModel.toggleUseRegex() },
                    advancedFiltering = advancedFiltering,
                    onAdvancedFilteringChange = { viewModel.setAdvancedFiltering(it) },
                    selectedDatasetCount = selectedDatasetIds.size,
                    onSearch = { viewModel.performSearch() },
                    isSearching = isSearching
                )

                AnimatedVisibility(
                    visible = isSearching && datasetStatuses.isNotEmpty(),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    DatasetProcessingStatusPanel(
                        statuses = datasetStatuses.values.toList(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                EmptySearchState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneCrossDatasetSearchScreen(
    navController: NavController,
    viewModel: CrossDatasetSearchViewModel,
    onSearchComplete: () -> Unit
) {
    val searchInput by viewModel.searchInput.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val significantOnly by viewModel.significantOnly.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val advancedFiltering by viewModel.advancedFiltering.collectAsState()
    val selectedDatasetIds by viewModel.selectedDatasetIds.collectAsState()
    val availableDatasets by viewModel.availableDatasets.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val currentSavedSearchId by viewModel.currentSavedSearchId.collectAsState()
    val filterLists by viewModel.filterLists.collectAsState()
    val isSyncingFilters by viewModel.isSyncingFilters.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val expandedCollectionIds by viewModel.expandedCollectionIds.collectAsState()
    val collectionSessions by viewModel.collectionSessions.collectAsState()
    val datasetStatuses by viewModel.datasetStatuses.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSavedSearches by remember { mutableStateOf(false) }
    var showFilterListPicker by remember { mutableStateOf(false) }
    var previousSearchResults by remember { mutableStateOf(searchResults) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(searchResults) {
        if (previousSearchResults == null && searchResults != null) {
            onSearchComplete()
        }
        previousSearchResults = searchResults
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

    if (showFilterListPicker) {
        FilterListPickerDialog(
            filterLists = filterLists,
            categories = categories,
            isSyncing = isSyncingFilters,
            onDismiss = { showFilterListPicker = false },
            onSelect = { filterList ->
                viewModel.loadFilterListData(filterList)
                showFilterListPicker = false
            },
            onSync = { viewModel.syncFilterLists() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            currentStep == 0 -> "Select Datasets"
                            else -> "Cross-Dataset Search"
                        }
                    )
                },
                navigationIcon = {
                    if (currentStep > 0) {
                        IconButton(onClick = { currentStep = 0 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to datasets")
                        }
                    }
                },
                actions = {
                    if (currentStep == 1) {
                        IconButton(onClick = { showFilterListPicker = true }) {
                            BadgedBox(
                                badge = {
                                    if (filterLists.isNotEmpty()) {
                                        Badge { Text(filterLists.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FilterList, "Filter Lists")
                            }
                        }
                        IconButton(onClick = { showSavedSearches = !showSavedSearches }) {
                            Icon(
                                if (showSavedSearches) Icons.Default.Bookmark else Icons.Default.History,
                                contentDescription = "Saved searches"
                            )
                        }
                        if (searchResults != null) {
                            IconButton(onClick = { showSaveDialog = true }) {
                                Icon(
                                    if (currentSavedSearchId != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save search"
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            currentStep == 0 -> {
                PhoneDatasetSelectionStep(
                    datasets = availableDatasets,
                    collections = collections,
                    expandedCollectionIds = expandedCollectionIds,
                    collectionSessions = collectionSessions,
                    selectedIds = selectedDatasetIds,
                    onToggleDataset = { viewModel.toggleDatasetSelection(it) },
                    onSelectAll = { viewModel.selectAllDatasets() },
                    onDeselectAll = { viewModel.deselectAllDatasets() },
                    onToggleCollectionExpanded = { viewModel.toggleCollectionExpanded(it) },
                    onSelectAllInCollection = { viewModel.selectAllSessionsInCollection(it) },
                    onDeselectAllInCollection = { viewModel.deselectAllSessionsInCollection(it) },
                    onToggleSession = { viewModel.toggleSessionSelection(it) },
                    onNext = { currentStep = 1 },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AnimatedVisibility(
                        visible = showSavedSearches,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        SavedSearchesSection(
                            savedSearches = savedSearches,
                            currentSearchId = currentSavedSearchId,
                            onLoadSearch = { id ->
                                viewModel.loadSavedSearch(id)
                                showSavedSearches = false
                            },
                            onDeleteSearch = { viewModel.deleteSavedSearch(it) },
                            onRenameSearch = { id, name -> viewModel.renameSavedSearch(id, name) }
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable { currentStep = 0 },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                                "${selectedDatasetIds.size} datasets selected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change selection",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    SearchInputSection(
                        searchInput = searchInput,
                        onSearchInputChange = { viewModel.updateSearchInput(it) },
                        searchType = searchType,
                        onSearchTypeChange = { viewModel.setSearchType(it) },
                        significantOnly = significantOnly,
                        onSignificantOnlyChange = { viewModel.toggleSignificantOnly() },
                        useRegex = useRegex,
                        onUseRegexChange = { viewModel.toggleUseRegex() },
                        advancedFiltering = advancedFiltering,
                        onAdvancedFilteringChange = { viewModel.setAdvancedFiltering(it) },
                        selectedDatasetCount = selectedDatasetIds.size,
                        onSearch = { viewModel.performSearch() },
                        isSearching = isSearching
                    )

                    AnimatedVisibility(
                        visible = isSearching && datasetStatuses.isNotEmpty(),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        DatasetProcessingStatusPanel(
                            statuses = datasetStatuses.values.toList(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    EmptySearchState(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneDatasetSelectionStep(
    datasets: List<CurtainEntity>,
    collections: List<CurtainCollectionEntity>,
    expandedCollectionIds: Set<Long>,
    collectionSessions: Map<Long, List<CollectionSessionEntity>>,
    selectedIds: Set<String>,
    onToggleDataset: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggleCollectionExpanded: (Long) -> Unit,
    onSelectAllInCollection: (Long) -> Unit,
    onDeselectAllInCollection: (Long) -> Unit,
    onToggleSession: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        CurtainListPanel(
            mode = CurtainListMode.SELECTION,
            datasets = datasets,
            collections = collections,
            expandedCollectionIds = expandedCollectionIds,
            collectionSessions = collectionSessions,
            selectedDatasetIds = selectedIds,
            onToggleDatasetSelection = onToggleDataset,
            onSelectAllDatasets = onSelectAll,
            onDeselectAllDatasets = onDeselectAll,
            onToggleCollectionExpanded = onToggleCollectionExpanded,
            onSelectAllInCollection = onSelectAllInCollection,
            onDeselectAllInCollection = onDeselectAllInCollection,
            onToggleSessionSelection = onToggleSession,
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider()

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            enabled = selectedIds.isNotEmpty()
        ) {
            Text("Continue with ${selectedIds.size} datasets")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SearchInputSection(
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    searchType: SearchType,
    onSearchTypeChange: (SearchType) -> Unit,
    significantOnly: Boolean,
    onSignificantOnlyChange: () -> Unit,
    useRegex: Boolean,
    onUseRegexChange: () -> Unit,
    advancedFiltering: CrossDatasetAdvancedFilterParams?,
    onAdvancedFilteringChange: (CrossDatasetAdvancedFilterParams?) -> Unit,
    selectedDatasetCount: Int,
    onSearch: () -> Unit,
    isSearching: Boolean,
    onDatasetSelectorClick: (() -> Unit)? = null
) {
    var expandedOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            label = { Text("Proteins (one per line or semicolon-separated)") },
            placeholder = { Text("ACTB;GAPDH;TUBB") },
            singleLine = false,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = searchType == SearchType.GENE_NAMES,
                onClick = { onSearchTypeChange(SearchType.GENE_NAMES) },
                label = { Text("Gene Names", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (searchType == SearchType.GENE_NAMES) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = searchType == SearchType.PRIMARY_IDS,
                onClick = { onSearchTypeChange(SearchType.PRIMARY_IDS) },
                label = { Text("Primary IDs", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (searchType == SearchType.PRIMARY_IDS) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { expandedOptions = !expandedOptions }) {
                Icon(
                    if (expandedOptions) Icons.Default.KeyboardArrowUp else Icons.Default.Settings,
                    "Options"
                )
            }
        }

        AnimatedVisibility(
            visible = expandedOptions,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use Regex", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = useRegex, onCheckedChange = { onUseRegexChange() })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Significant Only", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = significantOnly, onCheckedChange = { onSignificantOnlyChange() })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDatasetSelectorClick != null) {
                TextButton(onClick = onDatasetSelectorClick) {
                    Icon(Icons.Default.FilterList, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$selectedDatasetCount datasets")
                }
            } else {
                Text(
                    "$selectedDatasetCount datasets selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onSearch,
                enabled = !isSearching && searchInput.isNotBlank() && selectedDatasetCount > 0
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search")
            }
        }
    }
}

@Composable
private fun FilterListPickerDialog(
    filterLists: List<DataFilterListEntity>,
    categories: List<String>,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSelect: (DataFilterListEntity) -> Unit,
    onSync: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val allCategories = remember(categories, filterLists) {
        listOf("All") + (categories.ifEmpty { filterLists.map { it.category }.distinct() }).sorted()
    }

    val filteredLists = remember(filterLists, searchQuery, selectedCategory) {
        filterLists.filter { filter ->
            val matchesSearch = searchQuery.isEmpty() ||
                    filter.name.contains(searchQuery, ignoreCase = true) ||
                    filter.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || filter.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Filter List") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search filter lists...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Clear", Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (allCategories.size > 2) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allCategories) { category ->
                            FilterChip(
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category },
                                label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (category == selectedCategory) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filteredLists.size} filter lists",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onSync,
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync from Server")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredLists.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No filter lists available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap 'Sync from Server' to load curated filter lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredLists, key = { it.id }) { filterList ->
                            val proteinCount = remember(filterList) {
                                filterList.data.split("\n").filter { it.trim().isNotEmpty() }.size
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(filterList) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = filterList.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (filterList.isDefault) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("Curated", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(20.dp)
                                                )
                                            }
                                        }
                                        if (filterList.category.isNotBlank()) {
                                            Text(
                                                text = filterList.category,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$proteinCount",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProteinResultList(
    summaries: List<ProteinSearchSummary>,
    selectedProtein: ProteinSearchSummary?,
    sortOption: ProteinSortOption,
    onSortChange: (ProteinSortOption) -> Unit,
    onProteinClick: (ProteinSearchSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${summaries.size} proteins found",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider()

        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No proteins found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(summaries, key = { it.searchTerm }) { summary ->
                    ProteinSummaryItem(
                        summary = summary,
                        isSelected = selectedProtein?.searchTerm == summary.searchTerm,
                        onClick = { onProteinClick(summary) }
                    )
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
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
                    text = summary.geneName ?: summary.primaryId ?: summary.searchTerm,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.geneName != null && summary.primaryId != null) {
                    Text(
                        text = summary.primaryId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${summary.datasetsFoundIn}/${summary.totalDatasetsSearched}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (summary.hasSignificantResult) {
                    Text(
                        text = "Significant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProteinReportPanel(
    report: ProteinDetailedReport?,
    isLoading: Boolean,
    onNavigateToDataset: (linkId: String, proteinId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            report == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a protein to view details",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Found in ${report.datasetsFoundIn} of ${report.totalDatasetsSearched} datasets",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Results by Dataset",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    report.results.groupBy { it.datasetInfo.linkId }.forEach { (linkId, results) ->
                        DatasetResultCard(
                            datasetName = results.first().datasetInfo.datasetDescription,
                            linkId = linkId,
                            results = results,
                            onNavigate = { onNavigateToDataset(linkId, report.primaryId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasetResultCard(
    datasetName: String,
    linkId: String,
    results: List<DatasetComparisonResult>,
    onNavigate: () -> Unit
) {
    val displayName = datasetName.ifBlank { "Untitled" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${linkId.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "View in dataset",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            results.forEach { result ->
                ComparisonResultRow(result = result)
            }
        }
    }
}

@Composable
private fun ComparisonResultRow(result: DatasetComparisonResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.datasetInfo.comparison,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (result.found) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                result.foldChange?.let { fc ->
                    val color = when {
                        fc > 0 -> Color(0xFF4CAF50)
                        fc < 0 -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = String.format("FC: %.2f", fc),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }

                result.pValue?.let { p ->
                    Text(
                        text = String.format("p: %.2e", p),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (result.isSignificant) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Significant",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Text(
                text = "Not found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Search for proteins across datasets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter gene names or protein IDs above",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SaveSearchDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var searchName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val defaultName = remember { "Search ${dateFormat.format(Date())}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Search") },
        text = {
            Column {
                Text(
                    "Save this search to quickly access results later.",
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
            Button(
                onClick = { onSave(searchName.ifBlank { defaultName }) }
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
private fun SavedSearchesSection(
    savedSearches: List<SavedCrossDatasetSearchEntity>,
    currentSearchId: Long?,
    onLoadSearch: (Long) -> Unit,
    onDeleteSearch: (Long) -> Unit,
    onRenameSearch: (Long, String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    var editingSearchId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Saved Searches",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${savedSearches.size} saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (savedSearches.isEmpty()) {
            Text(
                "No saved searches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(savedSearches, key = { it.id }) { search ->
                    SavedSearchItem(
                        search = search,
                        isSelected = currentSearchId == search.id,
                        isEditing = editingSearchId == search.id,
                        editingName = if (editingSearchId == search.id) editingName else search.name,
                        dateFormat = dateFormat,
                        onLoad = { onLoadSearch(search.id) },
                        onDelete = { onDeleteSearch(search.id) },
                        onStartEdit = {
                            editingSearchId = search.id
                            editingName = search.name
                        },
                        onEditNameChange = { editingName = it },
                        onSaveEdit = {
                            if (editingName.isNotBlank()) {
                                onRenameSearch(search.id, editingName)
                            }
                            editingSearchId = null
                        },
                        onCancelEdit = { editingSearchId = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSearchItem(
    search: SavedCrossDatasetSearchEntity,
    isSelected: Boolean,
    isEditing: Boolean,
    editingName: String,
    dateFormat: SimpleDateFormat,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onStartEdit: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isEditing) { onLoad() },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditNameChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = onSaveEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = search.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${search.proteinCount} proteins",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${search.datasetCount} datasets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormat.format(Date(search.lastOpened)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onStartEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DatasetProcessingStatusPanel(
    statuses: List<DatasetProcessingStatus>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val completedCount = statuses.count { it.status == ProcessingState.COMPLETED }
    val failedCount = statuses.count { it.status == ProcessingState.FAILED }
    val totalCount = statuses.size

    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Processing datasets...",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (failedCount > 0) {
                        Text(
                            text = "$failedCount failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    statuses.sortedBy { it.datasetName }.forEach { status ->
                        DatasetStatusRow(status = status)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasetStatusRow(status: DatasetProcessingStatus) {
    val (icon, iconTint, statusText) = when (status.status) {
        ProcessingState.PENDING -> Triple(
            Icons.Default.HourglassEmpty,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Pending"
        )
        ProcessingState.LOADING -> Triple(
            Icons.Default.CloudDownload,
            MaterialTheme.colorScheme.primary,
            "Loading..."
        )
        ProcessingState.BUILDING -> Triple(
            Icons.Default.Build,
            MaterialTheme.colorScheme.tertiary,
            "Building..."
        )
        ProcessingState.SEARCHING -> Triple(
            Icons.Default.Search,
            MaterialTheme.colorScheme.secondary,
            "Searching..."
        )
        ProcessingState.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
            "Done"
        )
        ProcessingState.FAILED -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            "Failed"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = statusText,
                modifier = Modifier.size(16.dp),
                tint = iconTint
            )
            Text(
                text = status.datasetName.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status.message ?: statusText,
                style = MaterialTheme.typography.labelSmall,
                color = iconTint,
                maxLines = 1
            )
            if (status.status == ProcessingState.LOADING ||
                status.status == ProcessingState.BUILDING ||
                status.status == ProcessingState.SEARCHING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = iconTint
                )
            }
        }
    }
}
