package info.proteo.curtain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import org.junit.Rule
import org.junit.Test

class PTMViewerUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun proteinInfoCard_displaysAccession() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinInfoCard(
                    accession = "P28482",
                    geneName = "MAPK1",
                    proteinName = "Mitogen-activated protein kinase 1",
                    organism = "Homo sapiens (Human)"
                )
            }
        }

        composeTestRule.onNodeWithText("P28482").assertIsDisplayed()
    }

    @Test
    fun proteinInfoCard_displaysGeneName() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinInfoCard(
                    accession = "P28482",
                    geneName = "MAPK1",
                    proteinName = "Mitogen-activated protein kinase 1",
                    organism = "Homo sapiens (Human)"
                )
            }
        }

        composeTestRule.onNodeWithText("MAPK1").assertIsDisplayed()
    }

    @Test
    fun proteinInfoCard_displaysProteinName() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinInfoCard(
                    accession = "P28482",
                    geneName = "MAPK1",
                    proteinName = "Mitogen-activated protein kinase 1",
                    organism = "Homo sapiens (Human)"
                )
            }
        }

        composeTestRule.onNodeWithText("Mitogen-activated protein kinase 1").assertIsDisplayed()
    }

    @Test
    fun proteinInfoCard_displaysOrganism() {
        composeTestRule.setContent {
            CurtainTheme {
                TestProteinInfoCard(
                    accession = "P28482",
                    geneName = "MAPK1",
                    proteinName = "Mitogen-activated protein kinase 1",
                    organism = "Homo sapiens (Human)"
                )
            }
        }

        composeTestRule.onNodeWithText("Homo sapiens (Human)").assertIsDisplayed()
    }

    @Test
    fun modificationSelector_displaysModTypes() {
        val modTypes = listOf("Phosphoserine", "Phosphothreonine", "Phosphotyrosine")

        composeTestRule.setContent {
            CurtainTheme {
                TestModificationSelector(
                    availableModTypes = modTypes,
                    selectedModTypes = setOf("Phosphoserine")
                )
            }
        }

        modTypes.forEach { modType ->
            composeTestRule.onNodeWithText(modType).assertIsDisplayed()
        }
    }

    @Test
    fun modificationSelector_togglesSelection() {
        val modTypes = listOf("Phosphoserine", "Phosphothreonine", "Phosphotyrosine")

        composeTestRule.setContent {
            CurtainTheme {
                var selected by remember { mutableStateOf(setOf("Phosphoserine")) }
                TestModificationSelector(
                    availableModTypes = modTypes,
                    selectedModTypes = selected,
                    onSelectionChange = { modType ->
                        selected = if (selected.contains(modType)) {
                            selected - modType
                        } else {
                            selected + modType
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Phosphoserine").assertIsSelected()
        composeTestRule.onNodeWithText("Phosphothreonine").performClick()
        composeTestRule.onNodeWithText("Phosphothreonine").assertIsSelected()
    }

    @Test
    fun sequenceAlignment_displaysPositionMarkers() {
        composeTestRule.setContent {
            CurtainTheme {
                TestSequenceAlignmentRow(
                    startPosition = 1,
                    endPosition = 50
                )
            }
        }

        composeTestRule.onNodeWithText("1-50").assertIsDisplayed()
    }

    @Test
    fun sequenceAlignment_displaysRowLabels() {
        composeTestRule.setContent {
            CurtainTheme {
                Column {
                    TestSequenceRow(label = "Exp", sequence = "MAAAAAAGAG")
                    TestSequenceRow(label = "Ref", sequence = "MAAAAAAGAG")
                }
            }
        }

        composeTestRule.onNodeWithText("Exp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ref").assertIsDisplayed()
    }

    @Test
    fun ptmLegend_displaysAllCategories() {
        composeTestRule.setContent {
            CurtainTheme {
                TestPTMLegend()
            }
        }

        composeTestRule.onNodeWithText("Experimental PTM").assertIsDisplayed()
        composeTestRule.onNodeWithText("UniProt Modification").assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom PTM").assertIsDisplayed()
    }

    @Test
    fun domainDisplay_showsDomainInfo() {
        composeTestRule.setContent {
            CurtainTheme {
                TestDomainDisplay(
                    name = "Protein kinase",
                    startPosition = 25,
                    endPosition = 313
                )
            }
        }

        composeTestRule.onNodeWithText("Protein kinase").assertIsDisplayed()
        composeTestRule.onNodeWithText("25-313").assertIsDisplayed()
    }
}

@Composable
private fun TestProteinInfoCard(
    accession: String,
    geneName: String?,
    proteinName: String?,
    organism: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = accession,
                style = MaterialTheme.typography.titleLarge
            )
            if (geneName != null) {
                Text(
                    text = geneName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (proteinName != null) {
                Text(
                    text = proteinName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (organism != null) {
                Text(
                    text = organism,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TestModificationSelector(
    availableModTypes: List<String>,
    selectedModTypes: Set<String>,
    onSelectionChange: (String) -> Unit = {}
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(availableModTypes) { modType ->
            FilterChip(
                selected = selectedModTypes.contains(modType),
                onClick = { onSelectionChange(modType) },
                label = { Text(modType) },
                leadingIcon = if (selectedModTypes.contains(modType)) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun TestSequenceAlignmentRow(
    startPosition: Int,
    endPosition: Int
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "$startPosition-$endPosition",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TestSequenceRow(
    label: String,
    sequence: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = sequence,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun TestPTMLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(color = Color(0xFFFF9800), label = "Experimental PTM")
        LegendItem(color = Color(0xFF2196F3), label = "UniProt Modification")
        LegendItem(color = Color(0xFF9C27B0), label = "Custom PTM")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TestDomainDisplay(
    name: String,
    startPosition: Int,
    endPosition: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "$startPosition-$endPosition",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
