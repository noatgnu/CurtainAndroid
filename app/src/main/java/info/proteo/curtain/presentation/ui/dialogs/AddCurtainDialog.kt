package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCurtainDialog(
    onDismiss: () -> Unit,
    onAdd: (linkId: String, apiUrl: String, frontendUrl: String?, description: String?) -> Unit
) {
    var linkId by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("https://celsus.muttsu.xyz") }
    var frontendUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var apiUrlExpanded by remember { mutableStateOf(false) }

    val commonApiUrls = listOf(
        "https://celsus.muttsu.xyz",
        "https://curtain-backend.omics.quest"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Curtain Dataset") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = linkId,
                    onValueChange = { linkId = it },
                    label = { Text("Unique ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = apiUrlExpanded,
                    onExpandedChange = { apiUrlExpanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text("API URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiUrlExpanded)
                        },
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = apiUrlExpanded,
                        onDismissRequest = { apiUrlExpanded = false }
                    ) {
                        commonApiUrls.forEach { url ->
                            DropdownMenuItem(
                                text = { Text(url) },
                                onClick = {
                                    apiUrl = url
                                    apiUrlExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = frontendUrl,
                    onValueChange = { frontendUrl = it },
                    label = { Text("Frontend URL (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        linkId.trim(),
                        apiUrl.trim(),
                        frontendUrl.trim().ifBlank { null },
                        description.trim().ifBlank { null }
                    )
                },
                enabled = linkId.isNotBlank() && apiUrl.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
