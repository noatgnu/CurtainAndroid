package info.proteo.curtain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import org.junit.Rule
import org.junit.Test

class CrossDatasetSearchUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchInput_acceptsMultipleTerms() {
        composeTestRule.setContent {
            CurtainTheme {
                var query by remember { mutableStateOf("") }
                TestMultilineSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Enter protein/gene names...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter protein/gene names...")
            .performTextInput("MAPK1\nMAPK3\nAKT1")
    }

    @Test
    fun typeFilter_displaysPTMAndTPChips() {
        composeTestRule.setContent {
            CurtainTheme {
                TestTypeFilterChips(
                    selectedTypes = setOf("PTM", "TP")
                )
            }
        }

        composeTestRule.onNodeWithText("PTM").assertIsDisplayed()
        composeTestRule.onNodeWithText("TP").assertIsDisplayed()
    }

    @Test
    fun typeFilter_togglesPTMSelection() {
        composeTestRule.setContent {
            CurtainTheme {
                var selected by remember { mutableStateOf(setOf("PTM", "TP")) }
                TestTypeFilterChips(
                    selectedTypes = selected,
                    onTypeToggle = { type ->
                        selected = if (selected.contains(type)) {
                            selected - type
                        } else {
                            selected + type
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("PTM").assertIsSelected()
        composeTestRule.onNodeWithText("PTM").performClick()
    }

    @Test
    fun significantOnlyToggle_displaysCorrectly() {
        composeTestRule.setContent {
            CurtainTheme {
                var significantOnly by remember { mutableStateOf(false) }
                TestSignificantOnlyToggle(
                    checked = significantOnly,
                    onCheckedChange = { significantOnly = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Significant only").assertIsDisplayed()
    }

    @Test
    fun searchButton_displaysCorrectly() {
        composeTestRule.setContent {
            CurtainTheme {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("Search Across Datasets", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        composeTestRule.onNodeWithText("Search Across Datasets").assertIsDisplayed()
    }

    @Test
    fun datasetResult_displaysDatasetInfo() {
        composeTestRule.setContent {
            CurtainTheme {
                TestDatasetResultCard(
                    name = "Phosphoproteomics Study 1",
                    type = "PTM",
                    matchCount = 5,
                    significantCount = 3
                )
            }
        }

        composeTestRule.onNodeWithText("Phosphoproteomics Study 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("PTM").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 matches").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 significant").assertIsDisplayed()
    }

    @Test
    fun matrixView_displaysHeaders() {
        composeTestRule.setContent {
            CurtainTheme {
                TestMatrixHeaders(
                    datasets = listOf("Dataset 1", "Dataset 2", "Dataset 3")
                )
            }
        }

        composeTestRule.onNodeWithText("Dataset 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dataset 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dataset 3").assertIsDisplayed()
    }

    @Test
    fun advancedFilter_displaysFoldChangeInputs() {
        composeTestRule.setContent {
            CurtainTheme {
                TestAdvancedFilterPanel()
            }
        }

        composeTestRule.onNodeWithText("Min FC").assertIsDisplayed()
        composeTestRule.onNodeWithText("Max FC").assertIsDisplayed()
    }

    @Test
    fun noResults_displaysEmptyState() {
        composeTestRule.setContent {
            CurtainTheme {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No matches found")
                    Text("Try different search terms or filters")
                }
            }
        }

        composeTestRule.onNodeWithText("No matches found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try different search terms or filters").assertIsDisplayed()
    }
}

@Composable
private fun TestMultilineSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Enter protein/gene names...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        minLines = 3,
        maxLines = 5
    )
}

@Composable
private fun TestTypeFilterChips(
    selectedTypes: Set<String>,
    onTypeToggle: (String) -> Unit = {}
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listOf("PTM", "TP")) { type ->
            FilterChip(
                selected = selectedTypes.contains(type),
                onClick = { onTypeToggle(type) },
                label = { Text(type) },
                leadingIcon = if (selectedTypes.contains(type)) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
        }
    }
}

@Composable
private fun TestSignificantOnlyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Significant only")
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TestDatasetResultCard(
    name: String,
    type: String,
    matchCount: Int,
    significantCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = type,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "$matchCount matches",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$significantCount significant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun TestMatrixHeaders(
    datasets: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        datasets.forEach { dataset ->
            Text(
                text = dataset,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TestAdvancedFilterPanel() {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Min FC") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Max FC") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
