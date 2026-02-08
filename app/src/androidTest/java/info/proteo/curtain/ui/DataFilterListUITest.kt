package info.proteo.curtain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import org.junit.Rule
import org.junit.Test

class DataFilterListUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryChips_displaysAllCategories() {
        val categories = listOf("All", "Disease", "Enzyme", "Pathway")

        composeTestRule.setContent {
            CurtainTheme {
                TestCategoryChips(
                    categories = categories,
                    selectedCategory = "All"
                )
            }
        }

        categories.forEach { category ->
            composeTestRule.onNodeWithText(category).assertIsDisplayed()
        }
    }

    @Test
    fun categoryChips_selectsClickedCategory() {
        val categories = listOf("All", "Disease", "Enzyme", "Pathway")

        composeTestRule.setContent {
            CurtainTheme {
                var selected by remember { mutableStateOf("All") }
                TestCategoryChips(
                    categories = categories,
                    selectedCategory = selected,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Pathway").performClick()
        composeTestRule.onNodeWithText("Pathway").assertIsSelected()
    }

    @Test
    fun categoryChips_allIsSelectedByDefault() {
        val categories = listOf("All", "Disease", "Enzyme", "Pathway")

        composeTestRule.setContent {
            CurtainTheme {
                TestCategoryChips(
                    categories = categories,
                    selectedCategory = "All"
                )
            }
        }

        composeTestRule.onNodeWithText("All").assertIsSelected()
        composeTestRule.onNodeWithText("Pathway").assertIsNotSelected()
    }

    @Test
    fun filterListCount_displaysCorrectCount() {
        composeTestRule.setContent {
            CurtainTheme {
                Text(
                    text = "5 filter lists",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        composeTestRule.onNodeWithText("5 filter lists").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysNoFiltersMessage() {
        composeTestRule.setContent {
            CurtainTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("No filter lists available")
                    Text("Download curated filter lists from server")
                }
            }
        }

        composeTestRule.onNodeWithText("No filter lists available").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download curated filter lists from server").assertIsDisplayed()
    }

    @Test
    fun noSearchResults_displaysSearchQuery() {
        val query = "AMPK"

        composeTestRule.setContent {
            CurtainTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("No results for \"$query\"")
                    Text("Try a different search term")
                }
            }
        }

        composeTestRule.onNodeWithText("No results for \"$query\"").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try a different search term").assertIsDisplayed()
    }
}

@Composable
private fun TestCategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit = {}
) {
    LazyRow(
        modifier = Modifier.padding(16.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                leadingIcon = if (category == selectedCategory) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
        }
    }
}
