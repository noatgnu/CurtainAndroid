package info.proteo.curtain.presentation.ui.curtain

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.DistanceCalculator
import info.proteo.curtain.domain.model.PlotCoordinates
import info.proteo.curtain.domain.model.ProteinPoint
import info.proteo.curtain.domain.model.VolcanoPointClickData
import info.proteo.curtain.domain.service.VolcanoPlotDataService
import info.proteo.curtain.presentation.ui.dialogs.ExportFormat
import info.proteo.curtain.presentation.ui.dialogs.ExportPlotDialog
import info.proteo.curtain.presentation.ui.dialogs.PointInteractionDialog
import info.proteo.curtain.presentation.ui.dialogs.TraceOrderDialogCompose
import info.proteo.curtain.presentation.utils.FileExportUtils
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel
import info.proteo.curtain.presentation.viewmodel.ProteinDetailsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainDetailsScreen(
    linkId: String,
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val mappingProgress by viewModel.mappingProgress.collectAsState()
    val loadingStatus by viewModel.loadingStatus.collectAsState()
    val proteinCount by viewModel.proteinCount.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }
    var showTraceOrderDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showBatchSearchDialog by remember { mutableStateOf(false) }
    var showQuickSearchDialog by remember { mutableStateOf(false) }
    var showSettingsVariantDialog by remember { mutableStateOf(false) }
    var annotationEditMode by remember { mutableStateOf(false) }
    var showAnnotationEditDialog by remember { mutableStateOf(false) }
    var annotationCandidates by remember { mutableStateOf<List<info.proteo.curtain.domain.model.AnnotationEditCandidate>>(emptyList()) }
    var isInteractivePositioning by remember { mutableStateOf(false) }
    var positioningCandidate by remember { mutableStateOf<info.proteo.curtain.domain.model.AnnotationEditCandidate?>(null) }
    var showPointInteractionDialog by remember { mutableStateOf(false) }
    var pointClickData by remember { mutableStateOf<VolcanoPointClickData?>(null) }
    val tabs = listOf("Overview", "Volcano Plot", "Protein List", "Settings")
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(linkId) {
        viewModel.loadCurtainData(linkId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dataset Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSearchMenu = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Proteins")
                        }
                        DropdownMenu(
                            expanded = showSearchMenu,
                            onDismissRequest = { showSearchMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Quick Search") },
                                onClick = {
                                    showQuickSearchDialog = true
                                    showSearchMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Batch Search") },
                                onClick = {
                                    showBatchSearchDialog = true
                                    showSearchMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ViewList, null) }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings Presets") },
                                onClick = {
                                    showSettingsVariantDialog = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings Information") },
                                onClick = {
                                    navController.navigate("settings_info/$linkId")
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Force Rebuild Dataset") },
                                onClick = {
                                    showMenu = false
                                    viewModel.forceRebuildDataset(linkId)
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(500)
                                        viewModel.loadCurtainData(linkId)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )

                            if (selectedTab == 1) {
                                Divider()
                                Text(
                                    "Volcano Plot",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Annotation Edit Mode")
                                            Switch(
                                                checked = annotationEditMode,
                                                onCheckedChange = null
                                            )
                                        }
                                    },
                                    onClick = {
                                        annotationEditMode = !annotationEditMode
                                        if (!annotationEditMode) {
                                            isInteractivePositioning = false
                                            positioningCandidate = null
                                        }
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Plot") },
                                    onClick = {
                                        showExportDialog = true
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reorder Traces") },
                                    onClick = {
                                        showTraceOrderDialog = true
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.List, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Volcano Colors") },
                                    onClick = {
                                        navController.navigate("volcano_color_manager/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.ColorLens, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Condition Labels") },
                                    onClick = {
                                        navController.navigate("volcano_condition_labels/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Label, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Y-Axis Position") },
                                    onClick = {
                                        navController.navigate("volcano_yaxis_position/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.GridOn, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hover Text Column") },
                                    onClick = {
                                        navController.navigate("volcano_text_column/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.TextFields, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Marker Size Map") },
                                    onClick = {
                                        navController.navigate("marker_size_map/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.RadioButtonChecked, null) }
                                )
                            }

                            if (selectedTab == 3) {
                                Divider()
                                Text(
                                    "Chart Settings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Color Palette") },
                                    onClick = {
                                        navController.navigate("color_palette/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Palette, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sample Order & Visibility") },
                                    onClick = {
                                        navController.navigate("sample_order/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Reorder, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Global Y-Axis Limits") },
                                    onClick = {
                                        navController.navigate("global_yaxis_limits/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.BarChart, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Column Size") },
                                    onClick = {
                                        navController.navigate("column_size/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.ViewColumn, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Violin Point Position") },
                                    onClick = {
                                        navController.navigate("violin_point_position/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.DonutSmall, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bar Chart Settings") },
                                    onClick = {
                                        navController.navigate("bar_chart_bracket/$linkId")
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Architecture, null) }
                                )
                            }

                            Divider()
                            DropdownMenuItem(
                                text = { Text("Extra Data Storage") },
                                onClick = {
                                    navController.navigate("extra_data_storage/$linkId")
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Storage, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        mappingProgress?.let { (current, total) ->
                            if (current == -1 && total == -1) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Saving mappings to database...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "Building protein mappings...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                LinearProgressIndicator(
                                    progress = { current.toFloat() / total.toFloat() },
                                    modifier = Modifier
                                        .width(250.dp)
                                        .height(8.dp),
                                )
                                Text(
                                    text = "$current / $total proteins",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } ?: run {
                            CircularProgressIndicator()
                            Text(
                                text = loadingStatus ?: "Loading dataset...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                error != null -> {
                    ErrorView(
                        error = error!!,
                        onRetry = { viewModel.loadCurtainData(linkId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                curtainData != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> OverviewTab(curtainData!!, proteinCount)
                            1 -> VolcanoPlotTab(
                                curtainData = curtainData!!,
                                viewModel = viewModel,
                                showExportDialog = showExportDialog,
                                onExportDismiss = { showExportDialog = false },
                                annotationEditMode = annotationEditMode,
                                isInteractivePositioning = isInteractivePositioning,
                                positioningCandidate = positioningCandidate,
                                onInteractivePositioningChange = { isInteractivePositioning = it },
                                onPositioningCandidateChange = { positioningCandidate = it },
                                onAnnotationTapped = { offset, viewSize ->
                                    android.util.Log.d("AnnotationEdit", "onAnnotationTapped called - offset: $offset, viewSize: $viewSize")
                                    android.util.Log.d("AnnotationEdit", "textAnnotations count: ${curtainData!!.settings.textAnnotation.size}")
                                    android.util.Log.d("AnnotationEdit", "volcanoAxis: ${curtainData!!.settings.volcanoAxis}")
                                    val candidates = info.proteo.curtain.presentation.utils.AnnotationCoordinateCalculator.findAnnotationsNearPoint(
                                        tapPoint = offset,
                                        maxDistance = 100.0,
                                        textAnnotations = curtainData!!.settings.textAnnotation,
                                        volcanoAxis = curtainData!!.settings.volcanoAxis,
                                        viewSize = viewSize,
                                        plotMargin = curtainData!!.settings.volcanoPlotDimension.margin
                                    )
                                    android.util.Log.d("AnnotationEdit", "Found ${candidates.size} candidates")
                                    if (candidates.isNotEmpty()) {
                                        android.util.Log.d("AnnotationEdit", "Showing annotation edit dialog with ${candidates.size} candidates")
                                        annotationCandidates = candidates
                                        showAnnotationEditDialog = true
                                    } else {
                                        android.util.Log.d("AnnotationEdit", "No candidates found near tap point")
                                    }
                                },
                                onPointClicked = { pointData ->
                                    if (!annotationEditMode) {
                                        curtainData?.let { data ->
                                            coroutineScope.launch {
                                                pointClickData = parsePointClickData(pointData, data, viewModel)
                                                showPointInteractionDialog = true
                                            }
                                        }
                                    }
                                }
                            )
                            2 -> ProteinListTabNew(curtainData!!, linkId, navController)
                            3 -> SettingsTab(curtainData!!, navController)
                        }
                    }
                }
            }
        }
    }

    if (showTraceOrderDialog) {
        val traces = viewModel.getLastGeneratedTraces()
        if (traces.isNotEmpty()) {
            TraceOrderDialogCompose(
                traces = traces,
                onDismiss = { showTraceOrderDialog = false },
                onSave = { order ->
                    viewModel.saveTraceOrder(order)
                },
                onReset = {
                    viewModel.resetTraceOrder()
                }
            )
        } else {
            showTraceOrderDialog = false
        }
    }

    android.util.Log.d("AnnotationEdit", "Composition check - showAnnotationEditDialog: $showAnnotationEditDialog, curtainData: ${curtainData != null}, candidates: ${annotationCandidates.size}")

    if (showAnnotationEditDialog && curtainData != null) {
        info.proteo.curtain.presentation.ui.dialogs.AnnotationEditDialog(
            candidates = annotationCandidates,
            curtainData = curtainData!!,
            onDismiss = {
                showAnnotationEditDialog = false
                if (!isInteractivePositioning) {
                    positioningCandidate = null
                }
            },
            onSaveAnnotation = { key, newText, newOffset ->
                viewModel.updateAnnotation(key, newText, newOffset)
                showAnnotationEditDialog = false
            },
            onStartInteractiveMode = { candidate ->
                isInteractivePositioning = true
                positioningCandidate = candidate
                showAnnotationEditDialog = false
            }
        )
    }

    if (showPointInteractionDialog && pointClickData != null && curtainData != null) {
        PointInteractionDialog(
            clickData = pointClickData!!,
            curtainData = curtainData!!,
            onDismiss = {
                showPointInteractionDialog = false
                pointClickData = null
            },
            onCreateSelection = { selectionName, proteinIds ->
                viewModel.createSelectionFromProteinIds(selectionName, proteinIds)
            },
            onCreateAnnotations = { proteins ->
                viewModel.bulkCreateAnnotations(proteins)
            }
        )
    }

    if (showBatchSearchDialog && curtainData != null) {
        val searchViewModel: info.proteo.curtain.presentation.viewmodel.ProteinSearchViewModel = hiltViewModel()

        LaunchedEffect(curtainData) {
            searchViewModel.setCurtainData(curtainData!!)
        }

        info.proteo.curtain.presentation.ui.dialogs.ProteinSearchDialog(
            onDismiss = { showBatchSearchDialog = false },
            viewModel = searchViewModel,
            onCreateSelection = { selectionName, proteinIds ->
                viewModel.createSelectionFromProteinIds(selectionName, proteinIds)
                showBatchSearchDialog = false
            }
        )
    }

    if (showQuickSearchDialog && curtainData != null) {
        val quickSearchViewModel: info.proteo.curtain.presentation.viewmodel.ProteinSearchViewModel = hiltViewModel()

        LaunchedEffect(curtainData) {
            quickSearchViewModel.setCurtainData(curtainData!!)
        }

        info.proteo.curtain.presentation.ui.dialogs.QuickSearchDialog(
            onDismiss = { showQuickSearchDialog = false },
            viewModel = quickSearchViewModel,
            onCreateSelection = { selectionName, proteinIds ->
                viewModel.createSelectionFromProteinIds(selectionName, proteinIds)
                showQuickSearchDialog = false
            }
        )
    }

    if (showSettingsVariantDialog && curtainData != null) {
        info.proteo.curtain.presentation.ui.dialogs.SettingsVariantDialog(
            curtainData = curtainData!!,
            onDismiss = { showSettingsVariantDialog = false },
            onLoadVariant = { variant ->
                val updatedSettings = variant.appliedTo(curtainData!!.settings)
                val variantSelectedMap = variant.getStoredSelectedMap()
                val variantSelectionsName = variant.getStoredSelectionsName()
                viewModel.loadSettingsVariant(updatedSettings, variantSelectedMap, variantSelectionsName)
            }
        )
    }
}

@Composable
internal fun OverviewTab(curtainData: CurtainData, proteinCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dataset Information", style = MaterialTheme.typography.titleMedium)
                Divider()
                InfoRow("ID", curtainData.linkId)
                InfoRow("Description", curtainData.description)
                InfoRow("Type", curtainData.curtainType)
                InfoRow("Proteins", proteinCount.toString())
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Analysis Settings", style = MaterialTheme.typography.titleMedium)
                Divider()
                InfoRow("p-value cutoff", curtainData.settings.pCutoff.toString())
                InfoRow("Log2FC cutoff", curtainData.settings.log2FCCutoff.toString())
                InfoRow("Primary ID Column", curtainData.differentialForm.primaryIDs)
                InfoRow("Fold Change Column", curtainData.differentialForm.foldChange)
                InfoRow("Significance Column", curtainData.differentialForm.significant)
            }
        }
    }
}

@Composable
private fun VolcanoPlotTab(
    curtainData: CurtainData,
    viewModel: CurtainDetailsViewModel,
    showExportDialog: Boolean,
    onExportDismiss: () -> Unit,
    annotationEditMode: Boolean = false,
    isInteractivePositioning: Boolean = false,
    positioningCandidate: info.proteo.curtain.domain.model.AnnotationEditCandidate? = null,
    onInteractivePositioningChange: (Boolean) -> Unit = {},
    onPositioningCandidateChange: (info.proteo.curtain.domain.model.AnnotationEditCandidate?) -> Unit = {},
    onAnnotationTapped: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Size) -> Unit = { _, _ -> },
    onPointClicked: (String) -> Unit = {}
) {
    val volcanoPlotHtml by viewModel.volcanoPlotHtml.collectAsState()
    var isPlotReady by remember { mutableStateOf(false) }
    var plotError by remember { mutableStateOf<String?>(null) }
    var shouldInitializeWebView by remember { mutableStateOf(false) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }

    var isShowingDragPreview by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var currentDragPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var currentPreviewOffset by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(volcanoPlotHtml) {
        if (volcanoPlotHtml != null && !shouldInitializeWebView) {
            kotlinx.coroutines.delay(50)
            shouldInitializeWebView = true

            kotlinx.coroutines.delay(3000)
            if (!isPlotReady && plotError == null) {
                isPlotReady = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            volcanoPlotHtml == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(text = "Preparing volcano plot data...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            !shouldInitializeWebView -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(text = "Initializing plot renderer...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        VolcanoPlotView(
                            htmlContent = volcanoPlotHtml!!,
                            onPlotReady = { isPlotReady = true },
                            onPlotError = { error -> plotError = error },
                            onWebViewCreated = { webView -> currentWebView = webView },
                            onPointClicked = onPointClicked
                        )

                        if (annotationEditMode) {
                            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val viewSize = with(density) { androidx.compose.ui.geometry.Size(maxWidth.toPx(), maxHeight.toPx()) }

                                android.util.Log.d("OverlaySize", "BoxWithConstraints size: maxWidth=${maxWidth}, maxHeight=${maxHeight}")
                                android.util.Log.d("OverlaySize", "Converted to pixels: ${viewSize.width}x${viewSize.height}")

                                info.proteo.curtain.presentation.ui.components.AnnotationEditOverlay(
                                    curtainData = curtainData,
                                    isInteractivePositioning = isInteractivePositioning,
                                    positioningCandidate = positioningCandidate,
                                    isShowingDragPreview = isShowingDragPreview,
                                    dragStartPosition = dragStartPosition,
                                    currentDragPosition = currentDragPosition,
                                    viewSize = viewSize,
                                    onAnnotationTapped = { offset ->
                                        if (isInteractivePositioning) {
                                            currentDragPosition = offset
                                            isShowingDragPreview = true

                                            positioningCandidate?.let { candidate ->
                                                val volcanoAxis = curtainData.settings.volcanoAxis
                                                val plotMargin = curtainData.settings.volcanoPlotDimension.margin
                                                val marginLeft = (plotMargin.left ?: 70).toDouble()
                                                val marginRight = (plotMargin.right ?: 40).toDouble()
                                                val marginTop = (plotMargin.top ?: 60).toDouble()
                                                val marginBottom = (plotMargin.bottom ?: 120).toDouble()
                                                val plotAreaWidth = viewSize.width - marginLeft - marginRight
                                                val plotAreaHeight = viewSize.height - marginTop - marginBottom
                                                val xMin = volcanoAxis.minX ?: -3.0
                                                val xMax = volcanoAxis.maxX ?: 3.0
                                                val yMin = volcanoAxis.minY ?: 0.0
                                                val yMax = volcanoAxis.maxY ?: 6.0
                                                val x = candidate.arrowPosition.first
                                                val y = candidate.arrowPosition.second
                                                val viewX = marginLeft + ((x - xMin) / (xMax - xMin)) * plotAreaWidth
                                                val viewY = viewSize.height - marginBottom - ((y - yMin) / (yMax - yMin)) * plotAreaHeight

                                                dragStartPosition = Offset(viewX.toFloat(), viewY.toFloat())

                                                val offsetX = (offset.x - viewX).toDouble()
                                                val offsetY = (offset.y - viewY).toDouble()

                                                val jsCode = """
                                                    (function() {
                                                        var webViewWidth = window.innerWidth;
                                                        var webViewHeight = window.innerHeight;
                                                        var overlayWidth = ${viewSize.width};
                                                        var overlayHeight = ${viewSize.height};
                                                        var scaleX = webViewWidth / overlayWidth;
                                                        var scaleY = webViewHeight / overlayHeight;

                                                        var scaledAx = $offsetX * scaleX;
                                                        var scaledAy = $offsetY * scaleY;

                                                        console.log('[Android] Overlay offset: $offsetX, $offsetY');
                                                        console.log('[Android] WebView size:', webViewWidth, 'x', webViewHeight);
                                                        console.log('[Android] Overlay size:', overlayWidth, 'x', overlayHeight);
                                                        console.log('[Android] Scale factors:', scaleX, scaleY);
                                                        console.log('[Android] Scaled offset (ax, ay):', scaledAx, scaledAy);

                                                        if(window.CurtainVisualization) {
                                                            window.CurtainVisualization.updateAnnotationPosition('${candidate.title}', scaledAx, scaledAy);
                                                        }

                                                        return { ax: scaledAx, ay: scaledAy };
                                                    })();
                                                """.trimIndent()

                                                currentWebView?.evaluateJavascript(jsCode) { result ->
                                                    try {
                                                        val json = org.json.JSONObject(result)
                                                        val scaledAx = json.getDouble("ax")
                                                        val scaledAy = json.getDouble("ay")
                                                        currentPreviewOffset = Pair(scaledAx, scaledAy)
                                                        android.util.Log.d("AnnotationDrag", "Stored scaled offset for save: ($scaledAx, $scaledAy)")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("AnnotationDrag", "Failed to parse scaled offset: $e")
                                                        currentPreviewOffset = Pair(offsetX, offsetY)
                                                    }
                                                }
                                            }
                                        } else {
                                            onAnnotationTapped(offset, viewSize)
                                        }
                                    },
                                    onAnnotationDragged = { offset ->
                                        if (isInteractivePositioning) {
                                            currentDragPosition = offset
                                            isShowingDragPreview = true
                                            positioningCandidate?.let { candidate ->
                                                val volcanoAxis = curtainData.settings.volcanoAxis
                                                val plotMargin = curtainData.settings.volcanoPlotDimension.margin
                                                val marginLeft = (plotMargin.left ?: 70).toDouble()
                                                val marginRight = (plotMargin.right ?: 40).toDouble()
                                                val marginTop = (plotMargin.top ?: 60).toDouble()
                                                val marginBottom = (plotMargin.bottom ?: 120).toDouble()
                                                val plotAreaWidth = viewSize.width - marginLeft - marginRight
                                                val plotAreaHeight = viewSize.height - marginTop - marginBottom
                                                val xMin = volcanoAxis.minX ?: -3.0
                                                val xMax = volcanoAxis.maxX ?: 3.0
                                                val yMin = volcanoAxis.minY ?: 0.0
                                                val yMax = volcanoAxis.maxY ?: 6.0
                                                val x = candidate.arrowPosition.first
                                                val y = candidate.arrowPosition.second
                                                val viewX = marginLeft + ((x - xMin) / (xMax - xMin)) * plotAreaWidth
                                                val viewY = viewSize.height - marginBottom - ((y - yMin) / (yMax - yMin)) * plotAreaHeight

                                                dragStartPosition = Offset(viewX.toFloat(), viewY.toFloat())

                                                val offsetX = (offset.x - viewX).toDouble()
                                                val offsetY = (offset.y - viewY).toDouble()

                                                val jsCode = """
                                                    (function() {
                                                        var webViewWidth = window.innerWidth;
                                                        var webViewHeight = window.innerHeight;
                                                        var overlayWidth = ${viewSize.width};
                                                        var overlayHeight = ${viewSize.height};
                                                        var scaleX = webViewWidth / overlayWidth;
                                                        var scaleY = webViewHeight / overlayHeight;

                                                        var scaledAx = $offsetX * scaleX;
                                                        var scaledAy = $offsetY * scaleY;

                                                        console.log('[Android] Overlay offset: $offsetX, $offsetY');
                                                        console.log('[Android] WebView size:', webViewWidth, 'x', webViewHeight);
                                                        console.log('[Android] Overlay size:', overlayWidth, 'x', overlayHeight);
                                                        console.log('[Android] Scale factors:', scaleX, scaleY);
                                                        console.log('[Android] Scaled offset (ax, ay):', scaledAx, scaledAy);

                                                        if(window.CurtainVisualization) {
                                                            window.CurtainVisualization.updateAnnotationPosition('${candidate.title}', scaledAx, scaledAy);
                                                        }

                                                        return { ax: scaledAx, ay: scaledAy };
                                                    })();
                                                """.trimIndent()

                                                currentWebView?.evaluateJavascript(jsCode) { result ->
                                                    try {
                                                        val json = org.json.JSONObject(result)
                                                        val scaledAx = json.getDouble("ax")
                                                        val scaledAy = json.getDouble("ay")
                                                        currentPreviewOffset = Pair(scaledAx, scaledAy)
                                                        android.util.Log.d("AnnotationDrag", "Stored scaled offset for save: ($scaledAx, $scaledAy)")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("AnnotationDrag", "Failed to parse scaled offset: $e")
                                                        currentPreviewOffset = Pair(offsetX, offsetY)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )

                                if (isShowingDragPreview) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 100.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    isShowingDragPreview = false
                                                    onInteractivePositioningChange(false)
                                                    dragStartPosition = null
                                                    currentDragPosition = null
                                                    onPositioningCandidateChange(null)
                                                    currentPreviewOffset = null
                                                    viewModel.updateSettings(curtainData.settings)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Cancel")
                                            }
                                            Button(
                                                onClick = {
                                                    if (positioningCandidate != null && currentPreviewOffset != null) {
                                                        viewModel.updateAnnotation(positioningCandidate!!.key, null, currentPreviewOffset)
                                                    }
                                                    isShowingDragPreview = false
                                                    onInteractivePositioningChange(false)
                                                    dragStartPosition = null
                                                    currentDragPosition = null
                                                    onPositioningCandidateChange(null)
                                                    currentPreviewOffset = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Accept")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }

                if (annotationEditMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (isInteractivePositioning) {
                            if (isShowingDragPreview) {
                                info.proteo.curtain.presentation.ui.components.PreviewModeIndicator()
                            } else {
                                androidx.compose.material3.Surface(
                                    color = Color(0xFF2196F3).copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        "📍 Drag to move the annotation text",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        } else {
                            info.proteo.curtain.presentation.ui.components.AnnotationEditModeIndicator()
                        }
                    }
                }

                if (!isPlotReady && plotError == null) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(text = "Rendering volcano plot...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                plotError?.let { error ->
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Text(text = "Error loading plot", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    }

    if (showExportDialog && volcanoPlotHtml != null) {
        ExportPlotDialog(
            defaultFileName = "volcano_plot_${curtainData.linkId}",
            onDismiss = onExportDismiss,
            onExport = { fileName, format ->
                scope.launch {
                    try {
                        val result = when (format) {
                            ExportFormat.HTML -> FileExportUtils.exportHtmlToFile(context, fileName, volcanoPlotHtml!!)
                            ExportFormat.PNG -> {
                                currentWebView?.let { webView ->
                                    FileExportUtils.captureWebViewBitmap(webView) { bitmap ->
                                        scope.launch {
                                            val pngResult = FileExportUtils.exportPngToFile(context, fileName, bitmap)
                                            exportMessage = pngResult.getOrNull() ?: pngResult.exceptionOrNull()?.message
                                        }
                                    }
                                    Result.success("Capturing plot...")
                                } ?: Result.failure(Exception("Plot not ready for export"))
                            }
                        }
                        if (format == ExportFormat.HTML) exportMessage = result.getOrNull() ?: result.exceptionOrNull()?.message
                    } catch (e: Exception) {
                        exportMessage = "Export failed: ${e.message}"
                    }
                }
                onExportDismiss()
            }
        )
    }

    exportMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            exportMessage = null
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(message) }
    }
}

@Composable
internal fun VolcanoPlotView(
    htmlContent: String,
    onPlotReady: () -> Unit,
    onPlotError: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit = {},
    onPointClicked: (String) -> Unit = {}
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var lastLoadedHtml by remember { mutableStateOf("") }

    LaunchedEffect(htmlContent, webView) {
        if (htmlContent.isNotEmpty() && htmlContent != lastLoadedHtml && webView != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                webView?.let { view ->
                    android.util.Log.d("VolcanoPlotView", "Loading HTML, length: ${htmlContent.length}")
                    view.post {
                        view.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                    }
                    lastLoadedHtml = htmlContent
                }
            }
        }
    }

    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    AndroidView(
        factory = { context ->
            android.util.Log.d("VolcanoPlotView", "Creating WebView")
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.allowFileAccess = true
                settings.allowContentAccess = false

                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                if (isDarkTheme) {
                    setBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"))
                } else {
                    setBackgroundColor(android.graphics.Color.WHITE)
                }

                webViewClient = WebViewClient()

                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            android.util.Log.d("WebView", "Console: [${it.lineNumber()}] ${it.message()}")
                        }
                        return true
                    }
                }

                val bridge = info.proteo.curtain.domain.service.WebViewJavaScriptBridge(
                    onPlotReady = {
                        android.util.Log.d("VolcanoPlotView", "Plot ready callback")
                        onPlotReady()
                    },
                    onPlotError = { message ->
                        android.util.Log.e("VolcanoPlotView", "Plot error: $message")
                        onPlotError(message)
                    },
                    onPointClicked = { json ->
                        android.util.Log.d("VolcanoPlotView", "Point clicked: $json")
                        onPointClicked(json)
                    }
                )

                addJavascriptInterface(bridge, "AndroidBridge")
                webView = this
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            if (isDarkTheme) {
                webView.setBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"))
            } else {
                webView.setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
internal fun ProteinListTabNew(
    curtainData: CurtainData,
    linkId: String,
    navController: NavController,
    proteinDetailsViewModel: ProteinDetailsViewModel = hiltViewModel()
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
            val encodedGeneName = java.net.URLEncoder.encode(protein.geneName ?: "", "UTF-8")
            navController.navigate("protein_chart/$linkId/${protein.primaryId}/$encodedGeneName")
        },
        onAddToGroup = { proteinId, groupId ->
            proteinDetailsViewModel.addProteinToGroup(proteinId, groupId)
        },
        onRemoveFromGroup = { proteinId, groupId ->
            proteinDetailsViewModel.removeProteinFromGroup(proteinId, groupId)
        }
    )
}

private suspend fun parsePointClickData(
    jsonString: String,
    curtainData: CurtainData,
    viewModel: CurtainDetailsViewModel
): VolcanoPointClickData {
    val json = JSONObject(jsonString)

    val primaryId = json.optString("primaryID", json.optString("primaryId", json.optString("id", "")))
    val plotX = json.optDouble("x", 0.0)
    val plotY = json.optDouble("y", 0.0)

    android.util.Log.d("PointClick", "Clicked protein: $primaryId at ($plotX, $plotY)")
    android.util.Log.d("PointClick", "Raw JSON: $jsonString")

    val volcanoResult = viewModel.processVolcanoDataForPointClick(curtainData)

    android.util.Log.d("PointClick", "Processed volcano data has ${volcanoResult.jsonData.size} entries")

    val allProteins = volcanoResult.jsonData.mapNotNull { dataPoint ->
        val id = dataPoint["id"] as? String ?: return@mapNotNull null
        val gene = dataPoint["gene"] as? String ?: id
        val x = (dataPoint["x"] as? Number)?.toDouble() ?: return@mapNotNull null
        val y = (dataPoint["y"] as? Number)?.toDouble() ?: return@mapNotNull null

        val pValue = 10.0.pow(-y)
        val color = dataPoint["color"] as? String ?: "#808080"

        val isSignificant = abs(x) >= curtainData.settings.log2FCCutoff &&
                            pValue <= curtainData.settings.pCutoff

        ProteinPoint(
            id = id,
            primaryID = id,
            geneNames = gene,
            proteinName = gene,
            log2FC = x,
            pValue = pValue,
            negLog10PValue = y,
            color = color,
            isSignificant = isSignificant
        )
    }

    android.util.Log.d("PointClick", "Built ${allProteins.size} proteins from processed data")

    val clickedProtein = allProteins.firstOrNull { it.id == primaryId } ?: run {
        android.util.Log.e("PointClick", "Clicked protein $primaryId not found in processed data!")
        ProteinPoint(
            id = primaryId,
            primaryID = primaryId,
            geneNames = primaryId,
            proteinName = primaryId,
            log2FC = plotX,
            pValue = 10.0.pow(-plotY),
            negLog10PValue = plotY,
            color = "#808080",
            isSignificant = false
        )
    }

    android.util.Log.d("PointClick", "Clicked protein: ${clickedProtein.primaryID}, gene: ${clickedProtein.geneNames}, coords: (${clickedProtein.log2FC}, ${clickedProtein.negLog10PValue})")

    val nearbyProteins = DistanceCalculator.findNearbyProteins(
        centerProtein = clickedProtein,
        allProteins = allProteins,
        distanceCutoff = 1.0
    )

    return VolcanoPointClickData(
        clickedProtein = clickedProtein,
        nearbyProteins = nearbyProteins,
        clickPosition = android.graphics.PointF(plotX.toFloat(), plotY.toFloat()),
        plotCoordinates = PlotCoordinates(x = plotX, y = plotY)
    )
}

@Composable
private fun ProteinListTab(curtainData: CurtainData) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Protein List",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                "Protein list view - Coming soon",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun SettingsTab(curtainData: CurtainData, navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Analysis Parameters", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("P-value Cutoff", String.format("%.4f", curtainData.settings.pCutoff))
                    InfoRow("Log2FC Cutoff", String.format("%.2f", curtainData.settings.log2FCCutoff))
                    InfoRow("Current Comparison", curtainData.settings.currentComparison.ifEmpty { "None" })
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Volcano Plot", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Title", curtainData.settings.volcanoPlotTitle.ifEmpty { "None" })
                    InfoRow("Width", "${curtainData.settings.volcanoPlotDimension.width}px")
                    InfoRow("Height", "${curtainData.settings.volcanoPlotDimension.height}px")
                    InfoRow("Marker Size", String.format("%.1f", curtainData.settings.scatterPlotMarkerSize))
                    InfoRow("Font Family", curtainData.settings.plotFontFamily)
                    InfoRow("X-Axis", curtainData.settings.volcanoAxis.x)
                    InfoRow("Y-Axis", curtainData.settings.volcanoAxis.y)
                    InfoRow("Y-Axis Position", curtainData.settings.volcanoPlotYaxisPosition.joinToString(", "))
                    InfoRow("Text Column", curtainData.settings.customVolcanoTextCol.ifEmpty { "Default" })
                    InfoRow("Grid X", if (curtainData.settings.volcanoPlotGrid["x"] == true) "Yes" else "No")
                    InfoRow("Grid Y", if (curtainData.settings.volcanoPlotGrid["y"] == true) "Yes" else "No")
                    InfoRow("Annotations", "${curtainData.settings.textAnnotation.size}")
                    InfoRow("Additional Shapes", "${curtainData.settings.volcanoAdditionalShapes.size}")
                    InfoRow("Trace Order", if (curtainData.settings.volcanoTraceOrder.isNotEmpty()) "Custom (${curtainData.settings.volcanoTraceOrder.size})" else "Default")
                    curtainData.settings.volcanoPlotLegendX?.let { x ->
                        curtainData.settings.volcanoPlotLegendY?.let { y ->
                            InfoRow("Legend Position", "${String.format("%.2f", x)}, ${String.format("%.2f", y)}")
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Volcano Condition Labels", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Enabled", if (curtainData.settings.volcanoConditionLabels.enabled) "Yes" else "No")
                    if (curtainData.settings.volcanoConditionLabels.enabled) {
                        InfoRow("Left Condition", curtainData.settings.volcanoConditionLabels.leftCondition)
                        InfoRow("Right Condition", curtainData.settings.volcanoConditionLabels.rightCondition)
                        InfoRow("Font Size", "${curtainData.settings.volcanoConditionLabels.fontSize}pt")
                        InfoRow("Font Color", curtainData.settings.volcanoConditionLabels.fontColor)
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bar Chart Settings", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    val barChartSize = curtainData.settings.columnSize["barChart"] ?: 0
                    InfoRow("Bar Chart Column Size", if (barChartSize > 0) "${barChartSize}px" else "Auto")
                    val avgBarChartSize = curtainData.settings.columnSize["averageBarChart"] ?: 0
                    InfoRow("Avg Bar Chart Column Size", if (avgBarChartSize > 0) "${avgBarChartSize}px" else "Auto")
                    val violinSize = curtainData.settings.columnSize["violinPlot"] ?: 0
                    InfoRow("Violin Plot Column Size", if (violinSize > 0) "${violinSize}px" else "Auto")
                    InfoRow("Show Bracket", if (curtainData.settings.barChartConditionBracket.showBracket) "Yes" else "No")
                    if (curtainData.settings.barChartConditionBracket.showBracket) {
                        InfoRow("Bracket Color", curtainData.settings.barChartConditionBracket.bracketColor)
                        InfoRow("Bracket Width", "${curtainData.settings.barChartConditionBracket.bracketWidth}px")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chart Y-Axis Limits", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()

                    val barChartLimits = curtainData.settings.chartYAxisLimits["barChart"]
                    if (barChartLimits != null) {
                        val min = barChartLimits.min?.let { String.format("%.2f", it) } ?: "Auto"
                        val max = barChartLimits.max?.let { String.format("%.2f", it) } ?: "Auto"
                        InfoRow("Bar Chart Range", "$min to $max")
                    } else {
                        InfoRow("Bar Chart Range", "Auto")
                    }

                    val avgBarChartLimits = curtainData.settings.chartYAxisLimits["averageBarChart"]
                    if (avgBarChartLimits != null) {
                        val min = avgBarChartLimits.min?.let { String.format("%.2f", it) } ?: "Auto"
                        val max = avgBarChartLimits.max?.let { String.format("%.2f", it) } ?: "Auto"
                        InfoRow("Avg Bar Chart Range", "$min to $max")
                    } else {
                        InfoRow("Avg Bar Chart Range", "Auto")
                    }

                    val violinLimits = curtainData.settings.chartYAxisLimits["violinPlot"]
                    if (violinLimits != null) {
                        val min = violinLimits.min?.let { String.format("%.2f", it) } ?: "Auto"
                        val max = violinLimits.max?.let { String.format("%.2f", it) } ?: "Auto"
                        InfoRow("Violin Plot Range", "$min to $max")
                    } else {
                        InfoRow("Violin Plot Range", "Auto")
                    }

                    InfoRow("Individual Protein Limits", "${curtainData.settings.individualYAxisLimits.size} proteins")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Violin Plot", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Point Position", String.format("%.1f", curtainData.settings.violinPointPos))
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samples & Conditions", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Conditions", "${curtainData.settings.conditionOrder.size}")
                    InfoRow("Sample Groups", "${curtainData.settings.sampleOrder.size}")
                    val visibleCount = curtainData.settings.sampleVisible.count { it.value }
                    val totalSamples = curtainData.settings.sampleVisible.size
                    InfoRow("Visible Samples", "$visibleCount / $totalSamples")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color Settings", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Default Palette", "${curtainData.settings.defaultColorList.size} colors")
                    InfoRow("Condition Colors", "${curtainData.settings.colorMap.size}")
                    InfoRow("Bar Chart Colors", "${curtainData.settings.barchartColorMap.size}")
                    InfoRow("Marker Sizes", "${curtainData.settings.markerSizeMap.size} custom")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selection Groups", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Total Groups", "${curtainData.selectionsName?.size ?: 0}")
                    curtainData.selectionsName?.take(5)?.forEach { name ->
                        val count = curtainData.selectedMap?.count { it.value[name] == true } ?: 0
                        InfoRow("  $name", "$count proteins")
                    }
                    if ((curtainData.selectionsName?.size ?: 0) > 5) {
                        Text(
                            "... and ${(curtainData.selectionsName?.size ?: 0) - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extra Data", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    curtainData.settings.extraData.forEach { item ->
                        InfoRow(item.name, "${item.type}: ${item.content.take(50)}${if (item.content.length > 50) "..." else ""}")
                    }
                    if (curtainData.settings.extraData.isEmpty()) {
                        Text(
                            "No extra data stored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Advanced Features", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Enable Imputation", if (curtainData.settings.enableImputation) "Yes" else "No")
                    InfoRow("Imputation Maps", "${curtainData.settings.imputationMap.size}")
                    InfoRow("View Peptide Count", if (curtainData.settings.viewPeptideCount) "Yes" else "No")
                    InfoRow("Peptide Count Data", "${curtainData.settings.peptideCountData.size} proteins")
                    InfoRow("Enable Metabolomics", if (curtainData.settings.enableMetabolomics) "Yes" else "No")
                    InfoRow("Encrypted", if (curtainData.settings.encrypted) "Yes" else "No")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Network Analysis", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Network Interactions", "${curtainData.settings.networkInteractionData.size}")
                    InfoRow("Enrichr Gene Ranks", "${curtainData.settings.enrichrGeneRankMap.size}")
                    InfoRow("Enrichr Runs", "${curtainData.settings.enrichrRunList.size}")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project Information", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Title", curtainData.settings.project.title.ifEmpty { "Not set" })
                    InfoRow("Description", curtainData.settings.project.projectDescription.take(100).ifEmpty { "None" })
                    InfoRow("Accession", curtainData.settings.project.accession.ifEmpty { "None" })
                    InfoRow("Organisms", "${curtainData.settings.project.organisms.count { it.name.isNotEmpty() }}")
                    InfoRow("Cell Types", "${curtainData.settings.project.cellTypes.count { it.name.isNotEmpty() }}")
                    InfoRow("Diseases", "${curtainData.settings.project.diseases.count { it.name.isNotEmpty() }}")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Metadata", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Description", curtainData.settings.description.take(100).ifEmpty { "None" })
                    InfoRow("Data Analysis Contact", curtainData.settings.dataAnalysisContact.ifEmpty { "None" })
                    InfoRow("PRIDE Accession", curtainData.settings.prideAccession.ifEmpty { "None" })
                    InfoRow("FDR Curve Text", if (curtainData.settings.fdrCurveTextEnable) curtainData.settings.fdrCurveText else "Disabled")
                    InfoRow("Version", String.format("%.1f", curtainData.settings.version))
                    InfoRow("Academic", if (curtainData.settings.academic) "Yes" else "No")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Source", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    InfoRow("Dataset Type", curtainData.curtainType)
                    InfoRow("Link ID", curtainData.linkId)
                    InfoRow("Permanent", if (curtainData.permanent) "Yes" else "No")
                    InfoRow("Fetch UniProt", if (curtainData.fetchUniprot) "Yes" else "No")
                    InfoRow("Bypass UniProt", if (curtainData.bypassUniProt) "Yes" else "No")
                    curtainData.extraData?.uniprot?.organism?.let {
                        InfoRow("Organism", it)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            "Error loading data",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
