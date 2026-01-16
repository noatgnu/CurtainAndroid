package info.proteo.curtain.presentation.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.NearbyProtein
import info.proteo.curtain.domain.model.PlotCoordinates
import info.proteo.curtain.domain.model.ProteinPoint
import info.proteo.curtain.domain.model.VolcanoPointClickData
import info.proteo.curtain.presentation.utils.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointInteractionDialog(
    clickData: VolcanoPointClickData,
    curtainData: CurtainData,
    onDismiss: () -> Unit,
    onCreateSelection: (name: String, proteinIds: Set<String>) -> Unit,
    onCreateAnnotations: (proteins: List<ProteinPoint>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var newSelectionName by remember { mutableStateOf("") }
    var selectedProteinIds by remember { mutableStateOf(setOf(clickData.clickedProtein.id)) }
    var includeClickedProtein by remember { mutableStateOf(true) }

    val finalSelectedIds = remember(selectedProteinIds, includeClickedProtein) {
        if (includeClickedProtein) {
            selectedProteinIds + clickData.clickedProtein.id
        } else {
            selectedProteinIds - clickData.clickedProtein.id
        }
    }

    val canPerformAction = remember(selectedTab, newSelectionName, finalSelectedIds) {
        when (selectedTab) {
            0 -> newSelectionName.isNotBlank() && finalSelectedIds.isNotEmpty()
            1 -> finalSelectedIds.isNotEmpty()
            else -> true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PointInteractionHeader(clickData = clickData)

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Select") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Annotate") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Details") }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> SelectionTab(
                            clickData = clickData,
                            curtainData = curtainData,
                            selectedProteinIds = selectedProteinIds,
                            onSelectedProteinIdsChange = { selectedProteinIds = it },
                            newSelectionName = newSelectionName,
                            onNewSelectionNameChange = { newSelectionName = it },
                            includeClickedProtein = includeClickedProtein,
                            onIncludeClickedProteinChange = { includeClickedProtein = it },
                            onAutoCreateFromNearby = {
                                val allIds = mutableSetOf<String>()
                                if (includeClickedProtein) allIds.add(clickData.clickedProtein.id)
                                clickData.nearbyProteins.forEach { allIds.add(it.protein.id) }
                                val clickedName = proteinDisplayName(clickData.clickedProtein)
                                val includedText = if (includeClickedProtein) "including" else "near"
                                val autoName = "Proteins $includedText $clickedName (${allIds.size} proteins)"
                                onCreateSelection(autoName, allIds)
                                onDismiss()
                            },
                            onAutoCreateFromSelected = {
                                val clickedName = proteinDisplayName(clickData.clickedProtein)
                                val autoName = "Selected near $clickedName (${finalSelectedIds.size} proteins)"
                                onCreateSelection(autoName, finalSelectedIds)
                                onDismiss()
                            }
                        )
                        1 -> AnnotationTab(
                            clickData = clickData,
                            selectedProteinIds = selectedProteinIds,
                            onSelectedProteinIdsChange = { selectedProteinIds = it },
                            includeClickedProtein = includeClickedProtein,
                            onIncludeClickedProteinChange = { includeClickedProtein = it }
                        )
                        2 -> DetailsTab(
                            clickData = clickData,
                            curtainData = curtainData
                        )
                    }
                }

                PointInteractionActions(
                    selectedTab = selectedTab,
                    canPerformAction = canPerformAction,
                    onDismiss = onDismiss,
                    onDone = {
                        when (selectedTab) {
                            0 -> {
                                if (newSelectionName.isNotBlank() && finalSelectedIds.isNotEmpty()) {
                                    onCreateSelection(newSelectionName.trim(), finalSelectedIds)
                                }
                            }
                            1 -> {
                                if (finalSelectedIds.isNotEmpty()) {
                                    android.util.Log.d("PointInteractionDialog", "Creating annotations for ${finalSelectedIds.size} proteins")
                                    val selectedProteins = mutableListOf<ProteinPoint>()
                                    finalSelectedIds.forEach { proteinId ->
                                        if (proteinId == clickData.clickedProtein.id) {
                                            selectedProteins.add(clickData.clickedProtein)
                                            android.util.Log.d("PointInteractionDialog", "Added clicked protein: ${clickData.clickedProtein.primaryID}")
                                        } else {
                                            clickData.nearbyProteins.find { it.protein.id == proteinId }?.let {
                                                selectedProteins.add(it.protein)
                                                android.util.Log.d("PointInteractionDialog", "Added nearby protein: ${it.protein.primaryID}")
                                            }
                                        }
                                    }
                                    android.util.Log.d("PointInteractionDialog", "Calling onCreateAnnotations with ${selectedProteins.size} proteins")
                                    onCreateAnnotations(selectedProteins)
                                }
                            }
                        }
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun PointInteractionHeader(clickData: VolcanoPointClickData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    proteinDisplayName(clickData.clickedProtein),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Clicked Protein",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "FC: ${String.format("%.3f", clickData.clickedProtein.log2FC)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "p: ${String.format("%.2e", clickData.clickedProtein.pValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (clickData.nearbyProteins.isNotEmpty()) {
            Text(
                "${clickData.nearbyProteins.size} nearby proteins found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PointInteractionActions(
    selectedTab: Int,
    canPerformAction: Boolean,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancel")
        }

        if (selectedTab != 2) {
            FilledTonalButton(
                onClick = onDone,
                enabled = canPerformAction,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (selectedTab == 0) "Create Selection" else "Create Annotations")
            }
        }
    }
}

@Composable
private fun SelectionTab(
    clickData: VolcanoPointClickData,
    curtainData: CurtainData,
    selectedProteinIds: Set<String>,
    onSelectedProteinIdsChange: (Set<String>) -> Unit,
    newSelectionName: String,
    onNewSelectionNameChange: (String) -> Unit,
    includeClickedProtein: Boolean,
    onIncludeClickedProteinChange: (Boolean) -> Unit,
    onAutoCreateFromNearby: () -> Unit,
    onAutoCreateFromSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = onAutoCreateFromNearby,
            enabled = clickData.nearbyProteins.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Auto-Create from Nearby Proteins")
                Text(
                    "Creates selection with ${clickData.nearbyProteins.size + 1} proteins",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (selectedProteinIds.isNotEmpty()) {
            Button(
                onClick = onAutoCreateFromSelected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto-Create from Selected")
                    Text(
                        "Creates selection with ${selectedProteinIds.size} proteins",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        HorizontalDivider()

        Text("Manual Selection", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Include clicked protein", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = includeClickedProtein,
                onCheckedChange = onIncludeClickedProteinChange
            )
        }

        OutlinedTextField(
            value = newSelectionName,
            onValueChange = onNewSelectionNameChange,
            label = { Text("Selection Name") },
            placeholder = { Text("Enter selection name...") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Select Proteins (${selectedProteinIds.size} selected)",
            style = MaterialTheme.typography.titleSmall
        )

        ProteinSelectionRow(
            protein = clickData.clickedProtein,
            isSelected = selectedProteinIds.contains(clickData.clickedProtein.id),
            onSelectionChange = {
                onSelectedProteinIdsChange(
                    if (it) selectedProteinIds + clickData.clickedProtein.id
                    else selectedProteinIds - clickData.clickedProtein.id
                )
            },
            isClickedProtein = true,
            curtainData = curtainData
        )

        clickData.nearbyProteins.forEach { nearbyProtein ->
            ProteinSelectionRow(
                protein = nearbyProtein.protein,
                isSelected = selectedProteinIds.contains(nearbyProtein.protein.id),
                onSelectionChange = {
                    onSelectedProteinIdsChange(
                        if (it) selectedProteinIds + nearbyProtein.protein.id
                        else selectedProteinIds - nearbyProtein.protein.id
                    )
                },
                nearbyProtein = nearbyProtein,
                curtainData = curtainData
            )
        }
    }
}

@Composable
private fun AnnotationTab(
    clickData: VolcanoPointClickData,
    selectedProteinIds: Set<String>,
    onSelectedProteinIdsChange: (Set<String>) -> Unit,
    includeClickedProtein: Boolean,
    onIncludeClickedProteinChange: (Boolean) -> Unit
) {
    val targetProteins = remember(selectedProteinIds, includeClickedProtein) {
        if (includeClickedProtein) {
            selectedProteinIds + clickData.clickedProtein.id
        } else {
            selectedProteinIds - clickData.clickedProtein.id
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Annotations will be created for all selected proteins",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include clicked protein", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = includeClickedProtein,
                        onCheckedChange = onIncludeClickedProteinChange
                    )
                }
            }
        }

        Text(
            "Select Proteins to Annotate (${selectedProteinIds.size} selected)",
            style = MaterialTheme.typography.titleSmall
        )

        ProteinAnnotationRow(
            protein = clickData.clickedProtein,
            isSelected = selectedProteinIds.contains(clickData.clickedProtein.id),
            onSelectionChange = {
                onSelectedProteinIdsChange(
                    if (it) selectedProteinIds + clickData.clickedProtein.id
                    else selectedProteinIds - clickData.clickedProtein.id
                )
            },
            isClickedProtein = true
        )

        clickData.nearbyProteins.forEach { nearbyProtein ->
            ProteinAnnotationRow(
                protein = nearbyProtein.protein,
                isSelected = selectedProteinIds.contains(nearbyProtein.protein.id),
                onSelectionChange = {
                    onSelectedProteinIdsChange(
                        if (it) selectedProteinIds + nearbyProtein.protein.id
                        else selectedProteinIds - nearbyProtein.protein.id
                    )
                },
                nearbyProtein = nearbyProtein
            )
        }

        Text("Annotation Preview", style = MaterialTheme.typography.titleSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Annotations will be automatically generated:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                targetProteins.forEach { proteinId ->
                    val protein = if (proteinId == clickData.clickedProtein.id) {
                        clickData.clickedProtein
                    } else {
                        clickData.nearbyProteins.find { it.protein.id == proteinId }?.protein
                    }
                    protein?.let {
                        Text(
                            "• ${generateAnnotationTitle(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTab(
    clickData: VolcanoPointClickData,
    curtainData: CurtainData
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Clicked Protein", style = MaterialTheme.typography.titleMedium)
            ProteinDetailCard(
                protein = clickData.clickedProtein,
                plotCoordinates = clickData.plotCoordinates,
                curtainData = curtainData
            )
        }

        if (clickData.nearbyProteins.isNotEmpty()) {
            item {
                Text(
                    "Nearby Proteins (${clickData.nearbyProteins.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(clickData.nearbyProteins.take(10)) { nearbyProtein ->
                NearbyProteinCard(nearbyProtein = nearbyProtein)
            }

            if (clickData.nearbyProteins.size > 10) {
                item {
                    Text(
                        "... and ${clickData.nearbyProteins.size - 10} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProteinSelectionRow(
    protein: ProteinPoint,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    isClickedProtein: Boolean = false,
    nearbyProtein: NearbyProtein? = null,
    curtainData: CurtainData
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        proteinDisplayName(protein),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isClickedProtein) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isClickedProtein) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "(Clicked)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "FC: ${String.format("%.3f", protein.log2FC)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (protein.log2FC > 0) Color.Red else Color.Blue
                    )
                    Text(
                        "p: ${String.format("%.2e", protein.pValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    nearbyProtein?.let {
                        Text(
                            "dist: ${String.format("%.3f", it.distance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(parseColor(protein.color) ?: Color.Gray)
                    .border(1.dp, Color.White, CircleShape)
            )

            if (protein.isSignificant) {
                Text("⭐", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProteinAnnotationRow(
    protein: ProteinPoint,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    isClickedProtein: Boolean = false,
    nearbyProtein: NearbyProtein? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        proteinDisplayName(protein),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isClickedProtein) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isClickedProtein) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "(Clicked)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "FC: ${String.format("%.3f", protein.log2FC)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (protein.log2FC > 0) Color.Red else Color.Blue
                    )
                    Text(
                        "p: ${String.format("%.2e", protein.pValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    nearbyProtein?.let {
                        Text(
                            "dist: ${String.format("%.3f", it.distance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }

                Text(
                    "→ ${generateAnnotationTitle(protein)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (protein.isSignificant) {
                Text("⭐", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProteinDetailCard(
    protein: ProteinPoint,
    plotCoordinates: PlotCoordinates,
    curtainData: CurtainData
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    proteinDisplayName(protein),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(parseColor(protein.color) ?: Color.Gray)
                        .border(1.dp, Color.White, CircleShape)
                )
            }

            if (protein.proteinName != null && protein.proteinName != protein.primaryID) {
                Text(
                    "Protein: ${protein.proteinName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Log2 FC: ${String.format("%.6f", protein.log2FC)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "p-value: ${String.format("%.6e", protein.pValue)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "-log10(p): ${String.format("%.3f", protein.negLog10PValue)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (protein.isSignificant) {
                        Text(
                            "⭐ Significant",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Text(
                        "(${String.format("%.3f", plotCoordinates.x)}, ${String.format("%.3f", plotCoordinates.y)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyProteinCard(nearbyProtein: NearbyProtein) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    proteinDisplayName(nearbyProtein.protein),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    "Distance: ${String.format("%.3f", nearbyProtein.distance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "FC: ${String.format("%.3f", nearbyProtein.protein.log2FC)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "p: ${String.format("%.2e", nearbyProtein.protein.pValue)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "ΔX: ${String.format("%.3f", nearbyProtein.deltaX)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Blue
                    )
                    Text(
                        "ΔY: ${String.format("%.3f", nearbyProtein.deltaY)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red
                    )
                }
            }
        }
    }
}

private fun proteinDisplayName(protein: ProteinPoint): String {
    return if (!protein.geneNames.isNullOrEmpty() && protein.geneNames != protein.primaryID) {
        "${protein.geneNames} (${protein.primaryID})"
    } else {
        protein.primaryID
    }
}

private fun generateAnnotationTitle(protein: ProteinPoint): String {
    return if (!protein.geneNames.isNullOrEmpty() && protein.geneNames != protein.primaryID) {
        "${protein.geneNames}(${protein.primaryID})"
    } else {
        protein.primaryID
    }
}
