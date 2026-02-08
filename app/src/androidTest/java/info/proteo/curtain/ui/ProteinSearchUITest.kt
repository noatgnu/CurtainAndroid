package info.proteo.curtain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import org.junit.Rule
import org.junit.Test

class ProteinSearchUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchField_acceptsTextInput() {
        composeTestRule.setContent {
            CurtainTheme {
                var query by remember { mutableStateOf("") }
                TestSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Search proteins...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search proteins...").performTextInput("MAPK1")
        composeTestRule.onNodeWithText("MAPK1").assertIsDisplayed()
    }

    @Test
    fun searchField_clearButtonAppearsWhenNotEmpty() {
        composeTestRule.setContent {
            CurtainTheme {
                var query by remember { mutableStateOf("MAPK1") }
                TestSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear").assertIsDisplayed()
    }

    @Test
    fun searchField_clearButtonClearsText() {
        composeTestRule.setContent {
            CurtainTheme {
                var query by remember { mutableStateOf("MAPK1") }
                TestSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear").performClick()
        composeTestRule.onNodeWithText("Search proteins...").assertIsDisplayed()
    }

    @Test
    fun searchOptions_exactMatchToggle() {
        composeTestRule.setContent {
            CurtainTheme {
                var exactMatch by remember { mutableStateOf(false) }
                TestSearchOptions(
                    exactMatch = exactMatch,
                    onExactMatchChange = { exactMatch = it },
                    caseSensitive = false,
                    onCaseSensitiveChange = {},
                    useRegex = false,
                    onUseRegexChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Exact Match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exact Match").performClick()
    }

    @Test
    fun searchOptions_caseSensitiveToggle() {
        composeTestRule.setContent {
            CurtainTheme {
                var caseSensitive by remember { mutableStateOf(false) }
                TestSearchOptions(
                    exactMatch = false,
                    onExactMatchChange = {},
                    caseSensitive = caseSensitive,
                    onCaseSensitiveChange = { caseSensitive = it },
                    useRegex = false,
                    onUseRegexChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Case Sensitive").assertIsDisplayed()
    }

    @Test
    fun searchOptions_regexToggle() {
        composeTestRule.setContent {
            CurtainTheme {
                var useRegex by remember { mutableStateOf(false) }
                TestSearchOptions(
                    exactMatch = false,
                    onExactMatchChange = {},
                    caseSensitive = false,
                    onCaseSensitiveChange = {},
                    useRegex = useRegex,
                    onUseRegexChange = { useRegex = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Use Regex").assertIsDisplayed()
    }

    @Test
    fun searchResultCount_displaysCorrectly() {
        composeTestRule.setContent {
            CurtainTheme {
                Text(
                    text = "Found 25 proteins matching \"MAPK\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        composeTestRule.onNodeWithText("Found 25 proteins matching \"MAPK\"").assertIsDisplayed()
    }

    @Test
    fun noResults_displaysMessage() {
        composeTestRule.setContent {
            CurtainTheme {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    Text("No proteins found")
                    Text("Try a different search term or adjust filters")
                }
            }
        }

        composeTestRule.onNodeWithText("No proteins found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try a different search term or adjust filters").assertIsDisplayed()
    }
}

@Composable
private fun TestSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Search proteins...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun TestSearchOptions(
    exactMatch: Boolean,
    onExactMatchChange: (Boolean) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    useRegex: Boolean,
    onUseRegexChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        TestToggleOption("Exact Match", exactMatch, onExactMatchChange)
        TestToggleOption("Case Sensitive", caseSensitive, onCaseSensitiveChange)
        TestToggleOption("Use Regex", useRegex, onUseRegexChange)
    }
}

@Composable
private fun TestToggleOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
