package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.VolcanoColorType
import info.proteo.curtain.domain.model.VolcanoGroupColorInfo
import info.proteo.curtain.presentation.ui.dialogs.VolcanoDetailedColorPicker
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoColorManagerScreen(
    navController: NavController,
    viewModel: CurtainDetailsViewModel = hiltViewModel()
) {
    val curtainData by viewModel.curtainData.collectAsState()

    var volcanoGroups by remember { mutableStateOf<List<VolcanoGroupColorInfo>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var showingDetailedPicker by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<VolcanoGroupColorInfo?>(null) }

    val filteredGroups = remember(volcanoGroups, searchText) {
        if (searchText.isEmpty()) {
            volcanoGroups
        } else {
            volcanoGroups.filter { it.name.contains(searchText, ignoreCase = true) }
        }
    }

    LaunchedEffect(curtainData) {
        curtainData?.let { data ->
            volcanoGroups = loadVolcanoGroups(data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volcano Colors") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            curtainData?.let { data ->
                                saveChanges(data, volcanoGroups, viewModel)
                            }
                            navController.navigateUp()
                        }
                    ) {
                        Text("Done")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = VolcanoColorType.VOLCANO_PLOT_COLORS.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = VolcanoColorType.VOLCANO_PLOT_COLORS.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (volcanoGroups.isNotEmpty()) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search groups...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (searchText.isEmpty()) Icons.Default.Search else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchText.isEmpty()) "No Volcano Plot Groups" else "No Results",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (searchText.isEmpty())
                                "No groups found in the current volcano plot"
                            else
                                "No groups match your search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredGroups) { groupInfo ->
                        VolcanoGroupRow(
                            groupInfo = groupInfo,
                            onColorChange = { updatedInfo ->
                                volcanoGroups = volcanoGroups.map {
                                    if (it.name == updatedInfo.name) updatedInfo else it
                                }
                            },
                            onClick = {
                                selectedGroup = groupInfo
                                showingDetailedPicker = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showingDetailedPicker && selectedGroup != null) {
        VolcanoDetailedColorPicker(
            groupInfo = selectedGroup!!,
            onDismiss = { showingDetailedPicker = false },
            onColorChange = { updatedInfo: VolcanoGroupColorInfo ->
                volcanoGroups = volcanoGroups.map { it: VolcanoGroupColorInfo ->
                    if (it.name == updatedInfo.name) updatedInfo else it
                }
                selectedGroup = updatedInfo
            }
        )
    }
}

@Composable
private fun VolcanoGroupRow(
    groupInfo: VolcanoGroupColorInfo,
    onColorChange: (VolcanoGroupColorInfo) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(groupInfo.displayColor, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = groupInfo.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = groupInfo.hexColor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (groupInfo.alpha < 1.0f) {
                    Text(
                        text = "α: %.2f".format(groupInfo.alpha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Divider()
}

private fun loadVolcanoGroups(curtainData: CurtainData): List<VolcanoGroupColorInfo> {
    val groups = mutableListOf<VolcanoGroupColorInfo>()
    val allTraceGroups = mutableSetOf<String>()

    val conditionSet = curtainData.settings.conditionOrder.toSet()

    curtainData.selectedMap?.forEach { (_, selections) ->
        selections.forEach { (selectionName, isSelected) ->
            if (isSelected && !conditionSet.contains(selectionName)) {
                allTraceGroups.add(selectionName)
            }
        }
    }

    val significanceGroups = generateSignificanceGroups(curtainData)
    allTraceGroups.addAll(significanceGroups)

    allTraceGroups.forEach { groupName ->
        val colorString = curtainData.settings.colorMap[groupName]
            ?: getDefaultColorForGroup(groupName, curtainData.settings.defaultColorList)

        val (hexColor, alpha) = parseColorString(colorString)

        groups.add(
            VolcanoGroupColorInfo(
                name = groupName,
                type = VolcanoColorType.VOLCANO_PLOT_COLORS,
                hexColor = hexColor,
                alpha = alpha
            )
        )
    }

    return groups.sortedBy { it.name }
}

private fun generateSignificanceGroups(curtainData: CurtainData): Set<String> {
    val significanceGroups = mutableSetOf<String>()
    val pCutoff = curtainData.settings.pCutoff
    val log2FCCutoff = curtainData.settings.log2FCCutoff

    val comparisons = getAvailableComparisons(curtainData)

    val pValueCategories = listOf(
        "P-value <= $pCutoff",
        "P-value > $pCutoff"
    )

    val fcCategories = listOf(
        "FC <= $log2FCCutoff",
        "FC > $log2FCCutoff"
    )

    for (comparison in comparisons) {
        for (pCategory in pValueCategories) {
            for (fcCategory in fcCategories) {
                val groupName = "$pCategory;$fcCategory ($comparison)"
                significanceGroups.add(groupName)
            }
        }
    }

    return significanceGroups
}

private fun getAvailableComparisons(curtainData: CurtainData): Set<String> {
    val comparisons = mutableSetOf<String>()
    val comparisonColumn = curtainData.differentialForm.comparison

    if (comparisonColumn.isEmpty()) {
        comparisons.add("1")
    } else {
        val dataMap = curtainData.extraData?.data?.dataMap as? Map<*, *>
        val processedData = dataMap?.get("processedDifferentialData") as? List<*>
        processedData?.forEach { row ->
            val rowMap = row as? Map<*, *>
            val comparison = rowMap?.get(comparisonColumn) as? String
            if (!comparison.isNullOrEmpty()) {
                comparisons.add(comparison)
            }
        }
        if (comparisons.isEmpty()) {
            comparisons.add("1")
        }
    }

    return comparisons
}

private fun getDefaultColorForGroup(groupName: String, defaultColorList: List<String>): String {
    val hash = groupName.hashCode().let { if (it < 0) -it else it }
    val colorIndex = hash % defaultColorList.size
    return defaultColorList[colorIndex]
}

private fun parseColorString(colorString: String): Pair<String, Float> {
    return if (colorString.startsWith("#") && colorString.length == 9) {
        val alphaHex = colorString.substring(1, 3)
        val hexColor = "#" + colorString.substring(3)
        val alpha = alphaHex.toIntOrNull(16)?.let { it / 255.0f } ?: 1.0f
        Pair(hexColor, alpha)
    } else {
        Pair(colorString, 1.0f)
    }
}

private fun saveChanges(
    curtainData: CurtainData,
    volcanoGroups: List<VolcanoGroupColorInfo>,
    viewModel: CurtainDetailsViewModel
) {
    val newColorMap = curtainData.settings.colorMap.toMutableMap()

    volcanoGroups.forEach { groupInfo ->
        val colorWithAlpha = if (groupInfo.alpha < 1.0f) {
            groupInfo.argbString
        } else {
            groupInfo.hexColor
        }
        newColorMap[groupInfo.name] = colorWithAlpha
    }

    val updatedSettings = curtainData.settings.copy(colorMap = newColorMap)
    viewModel.updateSettings(updatedSettings)
}
