package info.proteo.curtain.presentation.ui.doi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.proteo.curtain.presentation.viewmodel.DOILoadingState
import info.proteo.curtain.presentation.viewmodel.DOIViewModel

@Composable
fun DOILoaderScreen(
    doi: String,
    sessionId: String?,
    onSessionLoaded: (Map<String, Any>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: DOIViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(doi, sessionId) {
        viewModel.loadDOI(doi, sessionId)
    }

    when (val currentState = state) {
        is DOILoadingState.Idle -> {
            DOILoadingView(doi = doi, status = "Initializing...")
        }

        is DOILoadingState.Loading -> {
            DOILoadingView(doi = doi, status = currentState.status)
        }

        is DOILoadingState.Collection -> {
            DOICollectionScreen(
                doi = doi,
                metadata = currentState.metadata,
                parsedData = currentState.parsedData,
                onSessionSelected = { sessionUrl ->
                    viewModel.loadSessionFromCollection(sessionUrl)
                },
                onDismiss = onDismiss
            )
        }

        is DOILoadingState.LoadingSession -> {
            DOILoadingView(doi = doi, status = currentState.status)
        }

        is DOILoadingState.Completed -> {
            LaunchedEffect(Unit) {
                onSessionLoaded(currentState.sessionData)
            }
        }

        is DOILoadingState.Error -> {
            DOIErrorView(
                doi = doi,
                error = currentState.error,
                onRetry = { viewModel.retry() },
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DOILoadingView(doi: String, status: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loading DOI") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = doi,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DOIErrorView(
    doi: String,
    error: Exception,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DOI Error") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Failed to Load DOI",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error.message ?: "Unknown error occurred",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = doi,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }

                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}
