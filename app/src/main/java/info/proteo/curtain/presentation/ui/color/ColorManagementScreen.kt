package info.proteo.curtain.presentation.ui.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

data class ColorItem(
    val id: String,
    val name: String,
    val color: Color,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorManagementScreen(
    conditions: List<String>,
    proteinGroups: List<String>,
    currentColors: Map<String, Color>,
    onColorChange: (String, Color) -> Unit,
    onResetColors: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showColorPicker by remember { mutableStateOf<String?>(null) }

    val conditionColors = conditions.map { condition ->
        ColorItem(
            id = condition,
            name = condition,
            color = currentColors[condition] ?: Color.Gray,
            category = "Condition"
        )
    }

    val proteinGroupColors = proteinGroups.map { group ->
        ColorItem(
            id = group,
            name = group,
            color = currentColors[group] ?: Color.Blue,
            category = "Protein Group"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onResetColors) {
                        Icon(Icons.Default.Refresh, "Reset to Defaults")
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
                    text = { Text("Conditions") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Protein Groups") }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (conditionColors.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No conditions available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(conditionColors) { colorItem ->
                                ColorItemCard(
                                    colorItem = colorItem,
                                    onClick = { showColorPicker = colorItem.id }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (proteinGroupColors.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No protein groups available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(proteinGroupColors) { colorItem ->
                                ColorItemCard(
                                    colorItem = colorItem,
                                    onClick = { showColorPicker = colorItem.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showColorPicker?.let { itemId ->
        val currentColor = currentColors[itemId] ?: Color.Gray
        ColorPickerDialog(
            initialColor = currentColor,
            onDismiss = { showColorPicker = null },
            onColorSelected = { newColor ->
                onColorChange(itemId, newColor)
                showColorPicker = null
            }
        )
    }
}

@Composable
fun ColorItemCard(
    colorItem: ColorItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colorItem.color, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )

                Column {
                    Text(
                        text = colorItem.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = colorItem.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(Icons.Default.Edit, "Edit Color")
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableStateOf(initialColor.red) }
    var green by remember { mutableStateOf(initialColor.green) }
    var blue by remember { mutableStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)

    val presetColors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFDCE775),
        Color(0xFFFFD54F), Color(0xFFFFB74D), Color(0xFFFF8A65),
        Color(0xFFA1887F), Color(0xFF90A4AE), Color(0xFFBDBDBD)
    )

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
                        .border(1.dp, MaterialTheme.colorScheme.outline)
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

                Text(
                    text = "Preset Colors",
                    style = MaterialTheme.typography.titleSmall
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.chunked(6).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { presetColor ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(presetColor, CircleShape)
                                        .border(
                                            width = if (presetColor == currentColor) 3.dp else 1.dp,
                                            color = if (presetColor == currentColor)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            red = presetColor.red
                                            green = presetColor.green
                                            blue = presetColor.blue
                                        }
                                )
                            }
                        }
                    }
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
