package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAnnotationDialog(
    pointData: String,
    onDismiss: () -> Unit,
    onCreateAnnotation: (String, Double, Double) -> Unit
) {
    var annotationText by remember { mutableStateOf("") }

    val pointInfo = remember(pointData) {
        try {
            val json = JSONObject(pointData)
            val primaryId = json.optString("primaryID", json.optString("primaryId", json.optString("id", "Unknown")))
            Triple(
                primaryId,
                json.optDouble("x", 0.0),
                json.optDouble("y", 0.0)
            )
        } catch (e: Exception) {
            Triple("Unknown", 0.0, 0.0)
        }
    }

    val (proteinId, plotX, plotY) = pointInfo

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Annotation") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Selected Protein:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            proteinId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Position: (%.2f, %.2f)".format(plotX, plotY),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Annotation Text:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = annotationText,
                        onValueChange = { annotationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter annotation text") },
                        supportingText = {
                            Text("This will appear next to the data point with an arrow")
                        }
                    )

                    if (annotationText.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Preview:",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    annotationText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💡", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Tip: You can edit the annotation position later using Annotation Edit Mode",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (annotationText.isNotBlank()) {
                        onCreateAnnotation(annotationText.trim(), plotX, plotY)
                        onDismiss()
                    }
                },
                enabled = annotationText.isNotBlank()
            ) {
                Text("Create Annotation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
