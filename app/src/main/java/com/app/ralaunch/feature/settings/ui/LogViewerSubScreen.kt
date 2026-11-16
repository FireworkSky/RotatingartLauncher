package com.app.ralaunch.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.R
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.feature.settings.vm.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LogViewerSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return
    val viewModel: SettingsViewModel = koinViewModel(viewModelStoreOwner = activity)
    val state by viewModel.logViewerState.collectAsStateWithLifecycle()
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { scope.launch { exportLogs(context, it) } }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLogs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) { viewModel.refreshLogs() }
    LaunchedEffect(state.query, state.issuesOnly, state.source) { listState.scrollToItem(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                        Text(stringResource(R.string.settings_logs_latest))
                    }
                    IconButton(onClick = viewModel::refreshLogs, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.main_refresh))
                    }
                    TextButton(onClick = { exportLauncher.launch(LogFilePolicy.appLogFileName()) }) {
                        Text(stringResource(R.string.export))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setLogQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_logs_search)) }
                )
                FilterChip(
                    selected = state.issuesOnly,
                    onClick = { viewModel.setLogIssuesOnly(!state.issuesOnly) },
                    label = { Text(stringResource(R.string.settings_logs_issues)) }
                )
                Box(Modifier.weight(0.6f)) {
                    TextButton(onClick = { sourceMenuOpen = true }) {
                        Text(
                            state.source ?: stringResource(R.string.settings_logs_all_sources),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_logs_all_sources)) },
                            onClick = { viewModel.setLogSource(null); sourceMenuOpen = false }
                        )
                        state.sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source) },
                                onClick = { viewModel.setLogSource(source); sourceMenuOpen = false }
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.settings_logs_recent, state.entries.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (!settings.loggingEnabled) {
                Text(stringResource(R.string.settings_logs_disabled), color = MaterialTheme.colorScheme.error)
            }
            state.error?.let { error ->
                Text(stringResource(R.string.settings_logs_read_failed, error), color = MaterialTheme.colorScheme.error)
            }
            if (state.loading && state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (state.query.isNotBlank() || state.issuesOnly || state.source != null) {
                            R.string.settings_logs_no_matches
                        } else R.string.settings_logs_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.entries, key = { it.id }) { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = if (entry.isIssue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    "${entry.source} · ${entry.eventLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.isIssue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entry.occurrences > 1) {
                                    Text(
                                        stringResource(R.string.settings_logs_occurrences, entry.occurrences, entry.lastTimestamp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                SelectionContainer {
                                    Text(
                                        entry.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (entry.isIssue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
