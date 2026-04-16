package com.noveltoon.app.ui.comic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noveltoon.app.R
import com.noveltoon.app.data.repository.SourceRepository
import com.noveltoon.app.ui.novel.SourceItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSourceManageScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SourceRepository(context) }
    val sources by repository.getAllComicSources().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_comic_sources)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.import_source))
                    }
                }
            )
        }
    ) { padding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.no_sources),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { showImportDialog = true }) {
                        Text(stringResource(R.string.import_source))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceItem(
                        name = source.name,
                        url = source.baseUrl,
                        enabled = source.enabled,
                        onToggle = {
                            scope.launch {
                                repository.updateComicSource(source.copy(enabled = !source.enabled))
                            }
                        },
                        onDelete = {
                            scope.launch { repository.deleteComicSource(source) }
                        }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_source)) },
            text = {
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text(stringResource(R.string.paste_json)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.importComicSources(importJson)
                        importJson = ""
                        showImportDialog = false
                    }
                }) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    importJson = ""
                    showImportDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
