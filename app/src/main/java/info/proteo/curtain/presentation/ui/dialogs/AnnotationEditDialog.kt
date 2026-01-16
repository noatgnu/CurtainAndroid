package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.proteo.curtain.domain.model.AnnotationEditAction
import info.proteo.curtain.domain.model.AnnotationEditCandidate
import info.proteo.curtain.domain.model.CurtainData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationEditDialog(
    candidates: List<AnnotationEditCandidate>,
    curtainData: CurtainData,
    onDismiss: () -> Unit,
    onSaveAnnotation: (String, String?, Pair<Double, Double>?) -> Unit,
    onStartInteractiveMode: (AnnotationEditCandidate) -> Unit
) {
    var selectedCandidate by remember { mutableStateOf<AnnotationEditCandidate?>(
        if (candidates.size == 1) candidates.first() else null
    ) }
    var editAction by remember { mutableStateOf(AnnotationEditAction.EDIT_TEXT) }
    var editedText by remember { mutableStateOf("") }
    var textOffsetX by remember { mutableDoubleStateOf(-20.0) }
    var textOffsetY by remember { mutableDoubleStateOf(-20.0) }

    LaunchedEffect(selectedCandidate) {
        selectedCandidate?.let { candidate ->
            editedText = extractPlainText(candidate.currentText)

            val annotationData = curtainData.settings.textAnnotation[candidate.key] as? Map<String, Any>
            val dataSection = annotationData?.get("data") as? Map<String, Any>
            textOffsetX = dataSection?.get("ax") as? Double ?: -20.0
            textOffsetY = dataSection?.get("ay") as? Double ?: -20.0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedCandidate != null) "Edit Annotation"
                else "Select Annotation"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedCandidate == null && candidates.size > 1) {
                    AnnotationSelectionList(
                        candidates = candidates,
                        onSelect = { selectedCandidate = it }
                    )
                } else if (selectedCandidate != null) {
                    SingleAnnotationEditView(
                        candidate = selectedCandidate!!,
                        editAction = editAction,
                        onEditActionChange = { editAction = it },
                        editedText = editedText,
                        onEditedTextChange = { editedText = it },
                        textOffsetX = textOffsetX,
                        onTextOffsetXChange = { textOffsetX = it },
                        textOffsetY = textOffsetY,
                        onTextOffsetYChange = { textOffsetY = it }
                    )
                } else {
                     Text("No annotations selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (selectedCandidate != null) {
                Button(
                    onClick = {
                        if (editAction == AnnotationEditAction.MOVE_TEXT_INTERACTIVE) {
                            onStartInteractiveMode(selectedCandidate!!)
                        } else {
                            when (editAction) {
                                AnnotationEditAction.EDIT_TEXT -> {
                                    onSaveAnnotation(selectedCandidate!!.key, editedText, null)
                                }
                                AnnotationEditAction.MOVE_TEXT -> {
                                    onSaveAnnotation(
                                        selectedCandidate!!.key,
                                        null,
                                        Pair(textOffsetX, textOffsetY)
                                    )
                                }
                                else -> {}
                            }
                            onDismiss()
                        }
                    }
                ) {
                    Text(
                        if (editAction == AnnotationEditAction.MOVE_TEXT_INTERACTIVE)
                            "Start Interactive Mode"
                        else
                            "Done"
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedCandidate != null && candidates.size > 1) {
                    selectedCandidate = null
                } else {
                    onDismiss()
                }
            }) {
                Text(if (selectedCandidate != null && candidates.size > 1) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun AnnotationSelectionList(
    candidates: List<AnnotationEditCandidate>,
    onSelect: (AnnotationEditCandidate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Multiple annotations found:",
            style = MaterialTheme.typography.titleMedium
        )

        candidates.forEach { candidate ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(candidate) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        candidate.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        extractPlainText(candidate.currentText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Distance: %.1f px".format(candidate.distance),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleAnnotationEditView(
    candidate: AnnotationEditCandidate,
    editAction: AnnotationEditAction,
    onEditActionChange: (AnnotationEditAction) -> Unit,
    editedText: String,
    onEditedTextChange: (String) -> Unit,
    textOffsetX: Double,
    onTextOffsetXChange: (Double) -> Unit,
    textOffsetY: Double,
    onTextOffsetYChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Edit Annotation: ${candidate.title}",
            style = MaterialTheme.typography.titleMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Choose Action:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionOption(
                    selected = editAction == AnnotationEditAction.EDIT_TEXT,
                    text = "Edit Text",
                    onClick = { onEditActionChange(AnnotationEditAction.EDIT_TEXT) }
                )

                ActionOption(
                    selected = editAction == AnnotationEditAction.MOVE_TEXT,
                    text = "Adjust Position (Sliders)",
                    onClick = { onEditActionChange(AnnotationEditAction.MOVE_TEXT) }
                )

                ActionOption(
                    selected = editAction == AnnotationEditAction.MOVE_TEXT_INTERACTIVE,
                    text = "Move by Tapping on Plot",
                    onClick = { onEditActionChange(AnnotationEditAction.MOVE_TEXT_INTERACTIVE) }
                )
            }
        }

        when (editAction) {
            AnnotationEditAction.EDIT_TEXT -> {
                EditTextView(editedText, onEditedTextChange)
            }
            AnnotationEditAction.MOVE_TEXT -> {
                MoveTextView(
                    textOffsetX, onTextOffsetXChange,
                    textOffsetY, onTextOffsetYChange
                )
            }
            AnnotationEditAction.MOVE_TEXT_INTERACTIVE -> {
                InteractiveMoveView()
            }
        }
    }
}

@Composable
private fun ActionOption(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditTextView(
    editedText: String,
    onEditedTextChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Annotation Text:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        OutlinedTextField(
            value = editedText,
            onValueChange = onEditedTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter annotation text") }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Preview: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                editedText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MoveTextView(
    textOffsetX: Double,
    onTextOffsetXChange: (Double) -> Unit,
    textOffsetY: Double,
    onTextOffsetYChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Text Position Adjustment:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Text(
            "Adjust the position of the annotation text relative to the data point:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OffsetControl(
            label = "Horizontal Offset",
            value = textOffsetX,
            onValueChange = onTextOffsetXChange
        )

        OffsetControl(
            label = "Vertical Offset",
            value = textOffsetY,
            onValueChange = onTextOffsetYChange
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    onTextOffsetXChange(-20.0)
                    onTextOffsetYChange(-20.0)
                }
            ) {
                Text("Reset to Default")
            }

            Text(
                "Arrow stays at data point",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            )
        ) {
            Text(
                "Preview: Text will be positioned %.0f px horizontally and %.0f px vertically from the data point.".format(
                    textOffsetX,
                    textOffsetY
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun OffsetControl(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label: %.0f px".format(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallButton("-10") { onValueChange(value - 10) }
            SmallButton("-5") { onValueChange(value - 5) }

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = -100f..100f,
                steps = 39,
                modifier = Modifier.weight(1f)
            )

            SmallButton("+5") { onValueChange(value + 5) }
            SmallButton("+10") { onValueChange(value + 10) }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.height(32.dp).width(48.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InteractiveMoveView() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Interactive Position Movement:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            "Use the 'Start Interactive Mode' button below, then tap anywhere on the plot where you want the annotation text to appear. The arrow will stay connected to the data point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Tap 'Start Interactive Mode' button below to begin positioning",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
            )
        ) {
            Text(
                "💡 Tip: You can tap anywhere on the plot to position the text. The system will calculate the best offset from the data point automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private fun extractPlainText(htmlText: String): String {
    return htmlText
        .replace("<b>", "")
        .replace("</b>", "")
        .replace("<i>", "")
        .replace("</i>", "")
}
