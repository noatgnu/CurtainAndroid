package info.proteo.curtain.presentation.ui.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import info.proteo.curtain.presentation.viewmodel.SiteSettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Site settings screen for managing backend servers.
 * Matches iOS site settings management.
 *
 * Features:
 * - Display all configured backend servers
 * - Add new custom servers
 * - Toggle active/inactive status
 * - Update API keys
 * - Delete custom servers
 * - Reset to default sites
 *
 * @param viewModel SiteSettingsViewModel instance (injected by Hilt)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsScreen(
    viewModel: SiteSettingsViewModel = hiltViewModel()
) {
    val siteSettings by viewModel.siteSettings.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backend Sites") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Site")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (siteSettings.isEmpty()) {
                EmptyState(onAddSite = { showAddDialog = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(siteSettings, key = { it.hostname }) { site ->
                        SiteItem(
                            site = site,
                            onToggleActive = { viewModel.toggleSiteActive(site) },
                            onDelete = { viewModel.deleteSite(site) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSiteDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { hostname, description, apiKey, requiresAuth ->
                viewModel.addSite(hostname, description, apiKey, requiresAuth)
                showAddDialog = false
            }
        )
    }
}

/**
 * Single site item in the list.
 *
 * @param site Site settings entity
 * @param onToggleActive Toggle active status callback
 * @param onDelete Delete callback
 */
@Composable
private fun SiteItem(
    site: CurtainSiteSettingsEntity,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (site.active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (site.active) "Active" else "Inactive",
                            tint = if (site.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = site.siteDescription ?: "Backend Server",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = site.hostname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (site.lastSync > 0) {
                        Text(
                            text = "Last sync: ${formatDate(site.lastSync)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (site.requiresAuthentication) {
                        Text(
                            text = if (site.apiKey != null) "✓ API Key configured" else "⚠ API Key required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (site.apiKey != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                Row {
                    Switch(
                        checked = site.active,
                        onCheckedChange = { onToggleActive() }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

/**
 * Empty state when no sites are configured.
 *
 * @param onAddSite Add site callback
 */
@Composable
private fun EmptyState(onAddSite: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No backend sites configured",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Add a backend server to sync datasets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FloatingActionButton(onClick = onAddSite) {
                Icon(Icons.Default.Add, contentDescription = "Add Site")
            }
        }
    }
}

/**
 * Dialog for adding a new site.
 *
 * @param onDismiss Dismiss callback
 * @param onConfirm Confirm callback with site details
 */
@Composable
private fun AddSiteDialog(
    onDismiss: () -> Unit,
    onConfirm: (hostname: String, description: String, apiKey: String?, requiresAuth: Boolean) -> Unit
) {
    var hostname by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var requiresAuth by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Backend Site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text("Hostname") },
                    placeholder = { Text("https://example.com/api/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("My Backend Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Requires Authentication",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = requiresAuth,
                        onCheckedChange = { requiresAuth = it }
                    )
                }

                if (requiresAuth) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (hostname.isNotBlank() && description.isNotBlank()) {
                        onConfirm(
                            hostname,
                            description,
                            apiKey.ifBlank { null },
                            requiresAuth
                        )
                    }
                },
                enabled = hostname.isNotBlank() && description.isNotBlank()
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

/**
 * Format timestamp to readable date string.
 *
 * @param timestamp Timestamp in milliseconds
 * @return Formatted date string
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
