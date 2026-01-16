package info.proteo.curtain.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel

val COLOR_PALETTES = mapOf(
    "pastel" to listOf(
        "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
        "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7"
    ),
    "retro" to listOf(
        "#ea5545", "#f46a9b", "#ef9b20", "#edbf33", "#ede15b",
        "#bdcf32", "#87bc45", "#27aeef", "#b33dc6"
    ),
    "solid" to listOf(
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    ),
    "gradient_red_green" to listOf(
        "#ff0000", "#ff3300", "#ff6600", "#ff9900", "#ffcc00", "#ffff00",
        "#ccff00", "#99ff00", "#66ff00", "#33ff00", "#00ff00"
    ),
    "Tol_bright" to listOf(
        "#EE6677", "#228833", "#4477AA", "#CCBB44",
        "#66CCEE", "#AA3377", "#BBBBBB"
    ),
    "Tol_muted" to listOf(
        "#88CCEE", "#44AA99", "#117733", "#332288", "#DDCC77",
        "#999933", "#CC6677", "#882255", "#AA4499", "#DDDDDD"
    ),
    "Tol_light" to listOf(
        "#BBCC33", "#AAAA00", "#77AADD", "#EE8866", "#EEDD88",
        "#FFAABB", "#99DDFF", "#44BB99", "#DDDDDD"
    ),
    "Okabe_Ito" to listOf(
        "#E69F00", "#56B4E9", "#009E73", "#F0E442",
        "#0072B2", "#D55E00", "#CC79A7", "#000000"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteScreen(
    navController: NavHostController,
    viewModel: CurtainDetailsViewModel
) {
    val curtainData by viewModel.curtainData.collectAsState()

    val currentPalette = remember(curtainData) {
        curtainData?.settings?.defaultColorList?.toMutableStateList() ?: mutableStateListOf(
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
            "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
        )
    }

    var selectedPaletteName by remember { mutableStateOf<String?>(null) }
    var customPalette by remember { mutableStateOf<List<String>?>(null) }
    var resetVolcanoColors by remember { mutableStateOf(false) }
    var resetBarChartColors by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Palette") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, "Import/Export")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = resetVolcanoColors,
                                onCheckedChange = { resetVolcanoColors = it }
                            )
                            Text("Reset Volcano", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = resetBarChartColors,
                                onCheckedChange = { resetBarChartColors = it }
                            )
                            Text("Reset Bar Chart", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(onClick = {
                        val finalPalette = customPalette ?: currentPalette
                        curtainData?.let { data ->
                            val updatedSettings = data.settings.copy(
                                defaultColorList = finalPalette
                            )

                            if (resetBarChartColors) {
                                val newColorMap = mutableMapOf<String, String>()
                                val newBarChartColorMap = mutableMapOf<String, String>()
                                data.settings.conditionOrder.forEachIndexed { index, condition ->
                                    val colorIndex = index % finalPalette.size
                                    newColorMap[condition] = finalPalette[colorIndex]
                                    newBarChartColorMap[condition] = finalPalette[colorIndex]
                                }
                                viewModel.updateSettings(updatedSettings.copy(
                                    colorMap = newColorMap,
                                    barchartColorMap = newBarChartColorMap
                                ))
                            } else {
                                viewModel.updateSettings(updatedSettings)
                            }

                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.Check, "Apply", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Current Palette",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        ColorPaletteRow(currentPalette)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { customPalette = currentPalette.toList() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Customize Current")
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Built-in Palettes",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))

                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPaletteName ?: "-- Choose a palette --",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                COLOR_PALETTES.keys.forEach { paletteName ->
                                    DropdownMenuItem(
                                        text = { Text(paletteName) },
                                        onClick = {
                                            selectedPaletteName = paletteName
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        selectedPaletteName?.let { name ->
                            COLOR_PALETTES[name]?.let { palette ->
                                Spacer(Modifier.height(12.dp))
                                ColorPaletteRow(palette)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { customPalette = palette },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Customize")
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            currentPalette.clear()
                                            currentPalette.addAll(palette)
                                            customPalette = null
                                            selectedPaletteName = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Use")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            customPalette?.let { palette ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Custom Palette",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(onClick = { customPalette = null }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            palette.forEachIndexed { index, color ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(parseColor(color), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable { showColorPicker = index }
                                    )

                                    Text(
                                        color,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            customPalette = palette.toMutableList().apply { removeAt(index) }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        customPalette = palette.toMutableList().apply { add("#ffffff") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Color")
                                }
                                OutlinedButton(
                                    onClick = { customPalette = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportExportDialog(
            currentPalette = customPalette ?: currentPalette,
            onDismiss = { showImportDialog = false },
            onImport = { imported ->
                customPalette = imported
                showImportDialog = false
            }
        )
    }

    showColorPicker?.let { index ->
        customPalette?.let { palette ->
            SimpleColorPickerDialog(
                initialColor = parseColor(palette[index]),
                onDismiss = { showColorPicker = null },
                onColorSelected = { color ->
                    val hex = colorToHex(color)
                    customPalette = palette.toMutableList().apply { set(index, hex) }
                    showColorPicker = null
                }
            )
        }
    }
}

@Composable
private fun ColorPaletteRow(colors: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        colors.take(10).forEachIndexed { index, color ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(parseColor(color), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                )
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (colors.size > 10) {
        Spacer(Modifier.height(4.dp))
        Text(
            "+${colors.size - 10} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportExportDialog(
    currentPalette: List<String>,
    onDismiss: () -> Unit,
    onImport: (List<String>) -> Unit
) {
    var importText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val gson = remember { Gson() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import/Export Palette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current palette JSON:", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        gson.toJson(currentPalette),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("Import palette:", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = importText,
                    onValueChange = {
                        importText = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("[\"#FF0000\", \"#00FF00\", \"#0000FF\"]") },
                    minLines = 3,
                    maxLines = 5,
                    isError = errorMessage != null
                )

                errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val parsed = gson.fromJson(importText, Array<String>::class.java)
                    if (parsed != null && parsed.isNotEmpty()) {
                        onImport(parsed.toList())
                    } else {
                        errorMessage = "Invalid palette format"
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to parse JSON: ${e.message}"
                }
            }) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SimpleColorPickerDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(currentColor, RoundedCornerShape(4.dp))
                )

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

private fun parseColor(colorValue: String): Color {
    return try {
        val hex = colorValue.removePrefix("#")
        val colorInt = hex.toLong(16).toInt()
        Color(colorInt or 0xFF000000.toInt())
    } catch (e: Exception) {
        Color.Gray
    }
}

private fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}
