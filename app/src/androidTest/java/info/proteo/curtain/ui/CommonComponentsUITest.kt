package info.proteo.curtain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import org.junit.Rule
import org.junit.Test

class CommonComponentsUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingIndicator_displaysSpinner() {
        composeTestRule.setContent {
            CurtainTheme {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        composeTestRule.onNode(
            androidx.compose.ui.test.hasProgressBarRangeInfo(
                androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
            )
        ).assertIsDisplayed()
    }

    @Test
    fun progressBar_displaysProgress() {
        composeTestRule.setContent {
            CurtainTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Loading data...")
                    LinearProgressIndicator(
                        progress = { 0.5f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("50% complete")
                }
            }
        }

        composeTestRule.onNodeWithText("Loading data...").assertIsDisplayed()
        composeTestRule.onNodeWithText("50% complete").assertIsDisplayed()
    }

    @Test
    fun errorBanner_displaysErrorMessage() {
        composeTestRule.setContent {
            CurtainTheme {
                TestErrorBanner(
                    message = "Failed to load data. Please try again."
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load data. Please try again.").assertIsDisplayed()
    }

    @Test
    fun successBanner_displaysSuccessMessage() {
        composeTestRule.setContent {
            CurtainTheme {
                TestSuccessBanner(
                    message = "Data saved successfully!"
                )
            }
        }

        composeTestRule.onNodeWithText("Data saved successfully!").assertIsDisplayed()
    }

    @Test
    fun confirmDialog_displaysTitle() {
        composeTestRule.setContent {
            CurtainTheme {
                TestConfirmDialog(
                    title = "Delete Item",
                    message = "Are you sure you want to delete this item?"
                )
            }
        }

        composeTestRule.onNodeWithText("Delete Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete this item?").assertIsDisplayed()
    }

    @Test
    fun confirmDialog_hasConfirmAndCancelButtons() {
        composeTestRule.setContent {
            CurtainTheme {
                TestConfirmDialog(
                    title = "Delete Item",
                    message = "Are you sure?"
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun fab_displaysAddIcon() {
        composeTestRule.setContent {
            CurtainTheme {
                FloatingActionButton(
                    onClick = {}
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

    @Test
    fun proteinCard_displaysProteinInfo() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinCard(
                    proteinId = "P28482",
                    geneName = "MAPK1",
                    foldChange = 2.5,
                    pValue = 0.001,
                    isSignificant = true
                )
            }
        }

        composeTestRule.onNodeWithText("P28482").assertIsDisplayed()
        composeTestRule.onNodeWithText("MAPK1").assertIsDisplayed()
        composeTestRule.onNodeWithText("FC: 2.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("p: 0.001").assertIsDisplayed()
    }

    @Test
    fun proteinCard_showsSignificanceIndicator() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinCard(
                    proteinId = "P28482",
                    geneName = "MAPK1",
                    foldChange = 2.5,
                    pValue = 0.001,
                    isSignificant = true
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Significant").assertIsDisplayed()
    }

    @Test
    fun selectionGroupCard_displaysGroupInfo() {
        composeTestRule.setContent {
            CurtainTheme {
                TestSelectionGroupCard(
                    name = "Upregulated",
                    color = "#FF0000",
                    count = 25
                )
            }
        }

        composeTestRule.onNodeWithText("Upregulated").assertIsDisplayed()
        composeTestRule.onNodeWithText("25 proteins").assertIsDisplayed()
    }

    @Test
    fun editDeleteButtons_displayCorrectly() {
        composeTestRule.setContent {
            CurtainTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysMessageAndAction() {
        composeTestRule.setContent {
            CurtainTheme {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No datasets found")
                    Text("Add a dataset to get started")
                    Button(onClick = {}) {
                        Text("Add Dataset")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("No datasets found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a dataset to get started").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Dataset").assertIsDisplayed()
    }

    @Test
    fun clickableCard_respondsToClick() {
        var clicked = false

        composeTestRule.setContent {
            CurtainTheme {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { clicked = true }
                ) {
                    Text("Click me", modifier = Modifier.padding(16.dp))
                }
            }
        }

        composeTestRule.onNodeWithText("Click me").performClick()
        assert(clicked)
    }
}

@Composable
private fun TestErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun TestSuccessBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TestConfirmDialog(
    title: String,
    message: String
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {}) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = {}) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TestProteinCard(
    proteinId: String,
    geneName: String?,
    foldChange: Double,
    pValue: Double,
    isSignificant: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = proteinId,
                    style = MaterialTheme.typography.titleMedium
                )
                if (geneName != null) {
                    Text(
                        text = geneName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "FC: $foldChange",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "p: $pValue",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (isSignificant) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Significant",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TestSelectionGroupCard(
    name: String,
    color: String,
    count: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$count proteins",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
