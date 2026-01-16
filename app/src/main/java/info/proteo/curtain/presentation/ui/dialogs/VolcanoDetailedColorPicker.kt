package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.proteo.curtain.domain.model.VolcanoGroupColorInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoDetailedColorPicker(
    groupInfo: VolcanoGroupColorInfo,
    onDismiss: () -> Unit,
    onColorChange: (VolcanoGroupColorInfo) -> Unit
) {
    var currentGroupInfo by remember { mutableStateOf(groupInfo) }
    var hexInput by remember { mutableStateOf(groupInfo.hexColor) }
    var argbInput by remember { mutableStateOf(groupInfo.argbString) }
    var alphaSlider by remember { mutableFloatStateOf(groupInfo.alpha) }
    var showingInvalidHex by remember { mutableStateOf(false) }
    var showingInvalidARGB by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    fun updateInputFields() {
        hexInput = currentGroupInfo.hexColor
        argbInput = currentGroupInfo.argbString
    }

    fun applyHexColor() {
        showingInvalidHex = false
        if (isValidHexColor(hexInput)) {
            currentGroupInfo = currentGroupInfo.copy(hexColor = hexInput)
            onColorChange(currentGroupInfo)
            updateInputFields()
        } else {
            showingInvalidHex = true
        }
    }

    fun applyARGBColor() {
        showingInvalidARGB = false
        if (isValidARGBColor(argbInput)) {
            val updatedInfo = currentGroupInfo.copy()
            updatedInfo.updateFromARGB(argbInput)
            currentGroupInfo = updatedInfo
            onColorChange(currentGroupInfo)
            hexInput = currentGroupInfo.hexColor
            alphaSlider = currentGroupInfo.alpha
        } else {
            showingInvalidARGB = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Edit Color") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Group Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentGroupInfo.type.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentGroupInfo.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Color Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(
                                        currentGroupInfo.displayColor,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Transparent, RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Color Picker",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Button(
                                onClick = { showColorPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Color")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Transparency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Opacity", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${(alphaSlider * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Slider(
                                value = alphaSlider,
                                onValueChange = { alphaSlider = it },
                                onValueChangeFinished = {
                                    currentGroupInfo = currentGroupInfo.copy(alpha = alphaSlider)
                                    onColorChange(currentGroupInfo)
                                    updateInputFields()
                                },
                                valueRange = 0f..1f,
                                steps = 99
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Manual Input",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Hex Color",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = hexInput,
                                        onValueChange = {
                                            hexInput = it.uppercase()
                                            showingInvalidHex = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("#RRGGBB") },
                                        isError = showingInvalidHex,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters
                                        )
                                    )

                                    Button(
                                        onClick = { applyHexColor() },
                                        enabled = hexInput.isNotEmpty()
                                    ) {
                                        Text("Apply")
                                    }
                                }

                                if (showingInvalidHex) {
                                    Text(
                                        text = "Invalid hex format. Use #RRGGBB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ARGB Color (with transparency)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = argbInput,
                                        onValueChange = {
                                            argbInput = it.uppercase()
                                            showingInvalidARGB = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("#AARRGGBB") },
                                        isError = showingInvalidARGB,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters
                                        )
                                    )

                                    Button(
                                        onClick = { applyARGBColor() },
                                        enabled = argbInput.isNotEmpty()
                                    ) {
                                        Text("Apply")
                                    }
                                }

                                if (showingInvalidARGB) {
                                    Text(
                                        text = "Invalid ARGB format. Use #AARRGGBB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Current Values",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Hex:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    currentGroupInfo.hexColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "ARGB:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    currentGroupInfo.argbString,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Opacity:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${(currentGroupInfo.alpha * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        SimpleColorPickerDialog(
            initialColor = currentGroupInfo.color,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                val hexColor = String.format("#%06X", 0xFFFFFF and color.toArgb())
                currentGroupInfo = currentGroupInfo.copy(hexColor = hexColor)
                onColorChange(currentGroupInfo)
                updateInputFields()
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun SimpleColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(selectedColor, RoundedCornerShape(8.dp))
                )

                val presetColors = listOf(
                    Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1),
                    Color(0xFF96CEB4), Color(0xFFFEEAA7), Color(0xFFDDA0DD),
                    Color(0xFF98D8C8), Color(0xFFF7DC6F), Color(0xFFE74C3C),
                    Color(0xFF3498DB), Color(0xFF2ECC71), Color(0xFFF39C12),
                    Color(0xFF9B59B6), Color(0xFF1ABC9C), Color(0xFFE67E22),
                    Color(0xFFECF0F1), Color(0xFF95A5A6), Color(0xFF34495E)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.chunked(6).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(color, RoundedCornerShape(8.dp))
                                        .then(
                                            if (selectedColor == color) {
                                                Modifier.padding(4.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
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

private fun isValidHexColor(hex: String): Boolean {
    val pattern = "^#[0-9A-Fa-f]{6}$".toRegex()
    return pattern.matches(hex)
}

private fun isValidARGBColor(argb: String): Boolean {
    val pattern = "^#[0-9A-Fa-f]{8}$".toRegex()
    return pattern.matches(argb)
}
