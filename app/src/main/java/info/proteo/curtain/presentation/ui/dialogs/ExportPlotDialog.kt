package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class ExportFormat(val displayName: String, val extension: String) {
    HTML("HTML (Interactive)", "html"),
    PNG("PNG (Image)", "png")
}

@Composable
fun ExportPlotDialog(
    defaultFileName: String = "volcano_plot",
    onDismiss: () -> Unit,
    onExport: (fileName: String, format: ExportFormat) -> Unit
) {
    var fileName by remember { mutableStateOf(defaultFileName) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.HTML) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Volcano Plot") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "Export Format:",
                    style = MaterialTheme.typography.labelMedium
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.values().forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (selectedFormat == format),
                                    onClick = { selectedFormat = format },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedFormat == format),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when (format) {
                                        ExportFormat.HTML -> "Save as interactive HTML file"
                                        ExportFormat.PNG -> "Save as static PNG image"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "File will be saved as: ${fileName}.${selectedFormat.extension}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onExport(fileName.trim(), selectedFormat)
                    }
                },
                enabled = fileName.isNotBlank()
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
