package com.noveltoon.app.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noveltoon.app.R
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import com.noveltoon.app.data.repository.SourceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceManageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SourceRepository(context) }
    val sources by repo.getAllBookSources().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    SourceManageScaffold(
        title = stringResource(R.string.manage_book_sources),
        onNavigateBack = onNavigateBack,
        onAdd = { showImportDialog = true },
        sources = sources.map { SourceListEntry(it.id, it.name, it.baseUrl, it.enabled) },
        selectedIds = selectedIds,
        onToggleSelect = { id ->
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        },
        onToggleEnabled = { id, enabled ->
            sources.find { it.id == id }?.let {
                scope.launch { repo.updateBookSource(it.copy(enabled = enabled)) }
            }
        },
        onSelectAll = { selectedIds = sources.map { it.id }.toSet() },
        onInvertSelect = {
            val all = sources.map { it.id }.toSet()
            selectedIds = all - selectedIds
        },
        onDeleteSelected = {
            scope.launch {
                sources.filter { it.id in selectedIds }.forEach { repo.deleteBookSource(it) }
                selectedIds = emptySet()
            }
        }
    )

    if (showImportDialog) {
        ImportSourceJsonDialog(
            onDismiss = {
                showImportDialog = false
                importJson = ""
            },
            json = importJson,
            onJsonChange = { importJson = it },
            onImport = {
                scope.launch {
                    repo.importBookSources(importJson)
                    importJson = ""
                    showImportDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSourceManageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SourceRepository(context) }
    val sources by repo.getAllComicSources().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    SourceManageScaffold(
        title = stringResource(R.string.manage_comic_sources),
        onNavigateBack = onNavigateBack,
        onAdd = { showImportDialog = true },
        sources = sources.map { SourceListEntry(it.id, it.name, it.baseUrl, it.enabled) },
        selectedIds = selectedIds,
        onToggleSelect = { id ->
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        },
        onToggleEnabled = { id, enabled ->
            sources.find { it.id == id }?.let {
                scope.launch { repo.updateComicSource(it.copy(enabled = enabled)) }
            }
        },
        onSelectAll = { selectedIds = sources.map { it.id }.toSet() },
        onInvertSelect = {
            val all = sources.map { it.id }.toSet()
            selectedIds = all - selectedIds
        },
        onDeleteSelected = {
            scope.launch {
                sources.filter { it.id in selectedIds }.forEach { repo.deleteComicSource(it) }
                selectedIds = emptySet()
            }
        }
    )

    if (showImportDialog) {
        ImportSourceJsonDialog(
            onDismiss = {
                showImportDialog = false
                importJson = ""
            },
            json = importJson,
            onJsonChange = { importJson = it },
            onImport = {
                scope.launch {
                    repo.importComicSources(importJson)
                    importJson = ""
                    showImportDialog = false
                }
            }
        )
    }
}

data class SourceListEntry(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceManageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    sources: List<SourceListEntry>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, stringResource(R.string.import_source))
                    }
                }
            )
        },
        bottomBar = {
            SourceSelectionBottomBar(
                selectedCount = selectedIds.size,
                totalCount = sources.size,
                onSelectAll = onSelectAll,
                onInvertSelect = onInvertSelect,
                onDelete = onDeleteSelected
            )
        }
    ) { padding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_sources),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceCheckListItem(
                        entry = source,
                        checked = source.id in selectedIds,
                        onCheckedChange = { onToggleSelect(source.id) },
                        onToggleEnabled = { onToggleEnabled(source.id, it) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun SourceCheckListItem(
    entry: SourceListEntry,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.url.isNotBlank()) {
                Text(
                    entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
private fun SourceSelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onSelectAll)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = totalCount > 0 && selectedCount == totalCount,
                    onCheckedChange = { onSelectAll() }
                )
                Text(
                    "${stringResource(R.string.select_all)} ($selectedCount/$totalCount)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onInvertSelect, enabled = totalCount > 0) {
                Text(stringResource(R.string.invert_selection))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun ImportSourceJsonDialog(
    onDismiss: () -> Unit,
    json: String,
    onJsonChange: (String) -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_source)) },
        text = {
            OutlinedTextField(
                value = json,
                onValueChange = onJsonChange,
                label = { Text(stringResource(R.string.paste_json)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = onImport) { Text(stringResource(R.string.import_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
