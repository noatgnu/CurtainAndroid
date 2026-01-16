package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleOrderScreen(
    navController: NavHostController,
    viewModel: CurtainDetailsViewModel
) {
    val curtainData by viewModel.curtainData.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val conditionOrder = remember(curtainData) {
        curtainData?.settings?.conditionOrder?.toMutableStateList() ?: mutableStateListOf()
    }

    val sampleOrder = remember(curtainData) {
        curtainData?.settings?.sampleOrder?.toMutableMap() ?: mutableMapOf()
    }

    val sampleVisible = remember(curtainData) {
        curtainData?.settings?.sampleVisible?.toMutableMap() ?: mutableMapOf()
    }

    val conditionColors = remember(curtainData) {
        mutableStateMapOf<String, Color>().apply {
            curtainData?.settings?.conditionOrder?.forEach { condition ->
                val colorValue = curtainData?.settings?.barchartColorMap?.get(condition)
                    ?: curtainData?.settings?.colorMap?.get(condition)
                if (colorValue != null) {
                    put(condition, parseColor(colorValue))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sample Order & Visibility") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        curtainData?.let { data ->
                            val updatedBarchartColorMap = conditionColors.mapValues { (_, color) ->
                                colorToHex(color)
                            }

                            viewModel.updateSettings(
                                data.settings.copy(
                                    conditionOrder = conditionOrder.toList(),
                                    sampleOrder = sampleOrder,
                                    sampleVisible = sampleVisible,
                                    barchartColorMap = updatedBarchartColorMap
                                )
                            )
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.Check, "Save")
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Condition Order") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Sample Order") }
                )
            }

            when (selectedTab) {
                0 -> ConditionOrderTab(
                    conditionOrder = conditionOrder,
                    conditionColors = conditionColors,
                    onMoveUp = { index ->
                        if (index > 0) {
                            val temp = conditionOrder[index]
                            conditionOrder[index] = conditionOrder[index - 1]
                            conditionOrder[index - 1] = temp
                        }
                    },
                    onMoveDown = { index ->
                        if (index < conditionOrder.size - 1) {
                            val temp = conditionOrder[index]
                            conditionOrder[index] = conditionOrder[index + 1]
                            conditionOrder[index + 1] = temp
                        }
                    },
                    onColorChange = { condition, color ->
                        conditionColors[condition] = color
                    }
                )
                1 -> SampleOrderTab(
                    conditionOrder = conditionOrder,
                    sampleOrder = sampleOrder,
                    sampleVisible = sampleVisible,
                    onMoveUp = { condition, index ->
                        val samples = sampleOrder[condition]?.toMutableList() ?: return@SampleOrderTab
                        if (index > 0) {
                            val temp = samples[index]
                            samples[index] = samples[index - 1]
                            samples[index - 1] = temp
                            sampleOrder[condition] = samples
                        }
                    },
                    onMoveDown = { condition, index ->
                        val samples = sampleOrder[condition]?.toMutableList() ?: return@SampleOrderTab
                        if (index < samples.size - 1) {
                            val temp = samples[index]
                            samples[index] = samples[index + 1]
                            samples[index + 1] = temp
                            sampleOrder[condition] = samples
                        }
                    },
                    onToggleVisibility = { sample, visible ->
                        sampleVisible[sample] = visible
                    }
                )
            }
        }
    }
}

@Composable
private fun ConditionOrderTab(
    conditionOrder: List<String>,
    conditionColors: Map<String, Color>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onColorChange: (String, Color) -> Unit
) {
    var showColorPicker by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(conditionOrder) { index, condition ->
            ConditionOrderItem(
                condition = condition,
                color = conditionColors[condition] ?: Color.Gray,
                canMoveUp = index > 0,
                canMoveDown = index < conditionOrder.size - 1,
                onMoveUp = { onMoveUp(index) },
                onMoveDown = { onMoveDown(index) },
                onColorClick = { showColorPicker = condition }
            )
        }
    }

    showColorPicker?.let { condition ->
        ColorPickerDialog(
            initialColor = conditionColors[condition] ?: Color.Gray,
            onDismiss = { showColorPicker = null },
            onColorSelected = { color ->
                onColorChange(condition, color)
                showColorPicker = null
            }
        )
    }
}

@Composable
private fun ConditionOrderItem(
    condition: String,
    color: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onColorClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onColorClick)
                )
                Text(
                    text = condition,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move Up")
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move Down")
                }
            }
        }
    }
}

@Composable
private fun SampleOrderTab(
    conditionOrder: List<String>,
    sampleOrder: Map<String, List<String>>,
    sampleVisible: Map<String, Boolean>,
    onMoveUp: (String, Int) -> Unit,
    onMoveDown: (String, Int) -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit
) {
    var expandedCondition by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conditionOrder) { condition ->
            val samples = sampleOrder[condition] ?: emptyList()

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCondition = if (expandedCondition == condition) null else condition
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = condition,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${samples.size} samples",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (expandedCondition == condition)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expandedCondition == condition) "Collapse" else "Expand"
                        )
                    }

                    if (expandedCondition == condition) {
                        Divider()
                        samples.forEachIndexed { index, sample ->
                            SampleOrderItem(
                                sample = sample,
                                isVisible = sampleVisible[sample] ?: true,
                                canMoveUp = index > 0,
                                canMoveDown = index < samples.size - 1,
                                onMoveUp = { onMoveUp(condition, index) },
                                onMoveDown = { onMoveDown(condition, index) },
                                onToggleVisibility = { visible ->
                                    onToggleVisibility(sample, visible)
                                }
                            )
                            if (index < samples.size - 1) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleOrderItem(
    sample: String,
    isVisible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sample,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "Move Up")
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Move Down")
            }
            Checkbox(
                checked = isVisible,
                onCheckedChange = onToggleVisibility
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(currentColor)
                )

                Column {
                    Text("Red: ${(red * 255).toInt()}")
                    Slider(
                        value = red,
                        onValueChange = { red = it },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red
                        )
                    )

                    Text("Green: ${(green * 255).toInt()}")
                    Slider(
                        value = green,
                        onValueChange = { green = it },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Green,
                            activeTrackColor = Color.Green
                        )
                    )

                    Text("Blue: ${(blue * 255).toInt()}")
                    Slider(
                        value = blue,
                        onValueChange = { blue = it },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Blue,
                            activeTrackColor = Color.Blue
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentColor) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseColor(colorValue: Any): Color {
    return when (colorValue) {
        is String -> {
            try {
                val hex = colorValue.removePrefix("#")
                val colorInt = hex.toLong(16).toInt()
                Color(colorInt or 0xFF000000.toInt())
            } catch (e: Exception) {
                Color.Gray
            }
        }
        is Number -> Color(colorValue.toInt() or 0xFF000000.toInt())
        else -> Color.Gray
    }
}

private fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}
