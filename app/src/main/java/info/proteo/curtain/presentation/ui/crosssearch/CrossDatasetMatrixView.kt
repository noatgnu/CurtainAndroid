package info.proteo.curtain.presentation.ui.crosssearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import info.proteo.curtain.domain.model.CrossDatasetMatrix
import info.proteo.curtain.domain.model.MatrixCell
import info.proteo.curtain.domain.model.MatrixRow
import kotlin.math.abs

@Composable
fun CrossDatasetMatrixView(
    matrix: CrossDatasetMatrix,
    isLoading: Boolean,
    selectedProteinId: String,
    onDatasetClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (matrix.rows.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No data to display",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val horizontalScrollState = rememberScrollState()
    var selectedDataset by remember { mutableStateOf<String?>(null) }
    var showComparisonInfo by remember { mutableStateOf<Pair<MatrixRow, MatrixCell?>?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var filterSignificantOnly by remember { mutableStateOf(false) }
    var filterMinFoldChange by remember { mutableStateOf("") }
    var filterMaxPValue by remember { mutableStateOf("") }
    var hideNotFound by remember { mutableStateOf(false) }

    val minFcValue = filterMinFoldChange.toDoubleOrNull()
    val maxPValueValue = filterMaxPValue.toDoubleOrNull()

    val filteredRows = remember(matrix, filterSignificantOnly, minFcValue, maxPValueValue, hideNotFound, selectedProteinId) {
        matrix.rows.filter { row ->
            val cell = row.cells[selectedProteinId]
            if (cell == null) {
                !hideNotFound
            } else if (!cell.found) {
                !hideNotFound
            } else {
                val passesSignificant = !filterSignificantOnly || cell.isSignificant
                val passesFc = minFcValue == null || (cell.foldChange != null && abs(cell.foldChange) >= minFcValue)
                val passesP = maxPValueValue == null || (cell.pValue != null && cell.pValue <= maxPValueValue)
                passesSignificant && passesFc && passesP
            }
        }
    }

    val uniqueComparisons = remember(filteredRows) {
        filteredRows.map { it.comparison }.distinct().sorted()
    }

    val datasetNameWidth = 160.dp
    val comparisonColumnWidth = 130.dp
    val headerHeight = 56.dp
    val rowHeight = 52.dp
    val hasActiveFilters = filterSignificantOnly || minFcValue != null || maxPValueValue != null || hideNotFound

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredRows.size}/${matrix.rows.size} datasets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Filter Options",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = filterSignificantOnly,
                                    onCheckedChange = { filterSignificantOnly = it }
                                )
                                Text("Significant only", style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hideNotFound,
                                    onCheckedChange = { hideNotFound = it }
                                )
                                Text("Hide not found", style = MaterialTheme.typography.bodyMedium)
                            }

                            OutlinedTextField(
                                value = filterMinFoldChange,
                                onValueChange = { filterMinFoldChange = it },
                                label = { Text("Min |FC|") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.widthIn(max = 120.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = filterMaxPValue,
                                onValueChange = { filterMaxPValue = it },
                                label = { Text("Max P-value") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.widthIn(max = 120.dp),
                                singleLine = true
                            )

                            if (hasActiveFilters) {
                                TextButton(
                                    onClick = {
                                        filterSignificantOnly = false
                                        filterMinFoldChange = ""
                                        filterMaxPValue = ""
                                        hideNotFound = false
                                    }
                                ) {
                                    Text("Clear Filters")
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
            ) {
                Box(
                    modifier = Modifier
                        .width(datasetNameWidth)
                        .height(headerHeight)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Dataset",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    uniqueComparisons.forEach { comparison ->
                        Box(
                            modifier = Modifier
                                .width(comparisonColumnWidth)
                                .height(headerHeight)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = comparison,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(filteredRows, key = { _, row -> row.datasetLinkId }) { index, row ->
                val isSelectedRow = selectedDataset == row.datasetLinkId
                val backgroundColor = when {
                    isSelectedRow -> MaterialTheme.colorScheme.primaryContainer
                    index % 2 == 0 -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    color = backgroundColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .width(datasetNameWidth)
                                .fillMaxHeight()
                                .clickable {
                                    selectedDataset = if (selectedDataset == row.datasetLinkId) null else row.datasetLinkId
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = row.datasetName.ifEmpty { "Untitled" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelectedRow) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (onDatasetClick != null) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open dataset",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onDatasetClick(row.datasetLinkId) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            uniqueComparisons.forEach { comparison ->
                                val isThisComparison = row.comparison == comparison
                                val cell = if (isThisComparison) {
                                    row.cells[selectedProteinId]
                                } else {
                                    null
                                }

                                ComparisonCellView(
                                    cell = cell,
                                    isActiveComparison = isThisComparison,
                                    width = comparisonColumnWidth,
                                    height = rowHeight,
                                    isHighlighted = isSelectedRow,
                                    onClick = if (isThisComparison) {
                                        { showComparisonInfo = Pair(row, cell) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                if (index < matrix.rows.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        MatrixLegend()
    }

    showComparisonInfo?.let { (row, cell) ->
        ComparisonInfoDialog(
            row = row,
            cell = cell,
            onDismiss = { showComparisonInfo = null }
        )
    }
}

@Composable
private fun ComparisonInfoDialog(
    row: MatrixRow,
    cell: MatrixCell?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = row.datasetName.ifEmpty { "Dataset Info" },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Comparison: ${row.comparison}",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (cell != null && cell.found) {
                    HorizontalDivider()
                    Text(
                        text = "Values",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    cell.foldChange?.let { fc ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Fold Change:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format("%.3f", fc),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (fc > 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                            )
                        }
                    }
                    cell.pValue?.let { p ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("P-value:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format("%.2e", p),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (cell.isSignificant) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (cell.isSignificant) {
                        Text(
                            text = "★ Significant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (row.conditionLeft != null || row.conditionRight != null) {
                    HorizontalDivider()
                    Text(
                        text = "Volcano Plot Conditions",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    row.conditionLeft?.let {
                        Text(
                            text = "Left (↓ FC): $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD32F2F)
                        )
                    }
                    row.conditionRight?.let {
                        Text(
                            text = "Right (↑ FC): $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF388E3C)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ComparisonCellView(
    cell: MatrixCell?,
    isActiveComparison: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isHighlighted: Boolean,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            !isActiveComparison -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                )
            }
            cell == null || !cell.found -> {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "N/F",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            cell.foldChange != null -> {
                val fcColor = getFoldChangeColor(cell.foldChange)
                val textColor = if (abs(cell.foldChange) > 1.5) Color.White else MaterialTheme.colorScheme.onSurface

                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = fcColor),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isHighlighted) 4.dp else 1.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format("%.2f", cell.foldChange),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (cell.isSignificant) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                            if (cell.isSignificant) {
                                Text(
                                    "★",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getFoldChangeColor(foldChange: Double): Color {
    val intensity = (minOf(abs(foldChange), 3.0) / 3.0).toFloat()
    return if (foldChange > 0) {
        Color(
            red = 0.2f + (0.1f * (1 - intensity)),
            green = 0.6f + (0.2f * intensity),
            blue = 0.3f + (0.1f * (1 - intensity)),
            alpha = 0.7f + (0.3f * intensity)
        )
    } else {
        Color(
            red = 0.8f + (0.15f * intensity),
            green = 0.3f + (0.1f * (1 - intensity)),
            blue = 0.3f + (0.1f * (1 - intensity)),
            alpha = 0.7f + (0.3f * intensity)
        )
    }
}

@Composable
private fun MatrixLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Legend:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(
                    color = Color(0xFFD32F2F),
                    label = "Down"
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "~0"
                )
                LegendItem(
                    color = Color(0xFF388E3C),
                    label = "Up"
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "N/F"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "★ = significant",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
