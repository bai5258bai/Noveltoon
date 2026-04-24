package com.noveltoon.app.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var importing by remember { mutableStateOf(false) }
    var snackbarText by remember { mutableStateOf("") }
    // validity: id -> true/false/null(unchecked)
    var validity by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var checking by remember { mutableStateOf(false) }

    SourceManageScaffold(
        title = stringResource(R.string.manage_book_sources),
        onNavigateBack = onNavigateBack,
        onAdd = { showImportDialog = true },
        onCheckValidity = {
            scope.launch {
                checking = true
                snackbarText = context.getString(R.string.source_checking)
                val result = mutableMapOf<Long, Boolean>()
                sources.forEach { s ->
                    result[s.id] = repo.checkBookSourceValid(s)
                }
                validity = result
                checking = false
                val ok = result.values.count { it }
                val fail = result.values.count { !it }
                snackbarText = context.getString(R.string.source_check_done, ok, fail)
            }
        },
        sources = sources.map { SourceListEntry(it.id, it.name, it.baseUrl, it.enabled, it.isBuiltIn, validity[it.id]) },
        selectedIds = selectedIds,
        snackbarText = snackbarText,
        onSnackbarDismiss = { snackbarText = "" },
        onToggleSelect = { id ->
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        },
        onToggleEnabled = { id, enabled ->
            sources.find { it.id == id }?.let {
                scope.launch { repo.updateBookSource(it.copy(enabled = enabled)) }
            }
        },
        onSelectAll = { selectedIds = sources.filter { !it.isBuiltIn }.map { it.id }.toSet() },
        onInvertSelect = {
            val all = sources.filter { !it.isBuiltIn }.map { it.id }.toSet()
            selectedIds = all - selectedIds
        },
        onDeleteSelected = {
            scope.launch {
                val toDelete = sources.filter { it.id in selectedIds && !it.isBuiltIn }
                toDelete.forEach { repo.deleteBookSource(it) }
                selectedIds = emptySet()
                snackbarText = context.getString(R.string.deleted_count, toDelete.size)
            }
        }
    )

    if (showImportDialog) {
        ImportSourceJsonDialog(
            onDismiss = {
                if (!importing) {
                    showImportDialog = false
                    importJson = ""
                }
            },
            json = importJson,
            onJsonChange = { importJson = it },
            importing = importing,
            onImport = {
                scope.launch {
                    importing = true
                    val result = repo.importBookSourcesFromText(importJson)
                    importing = false
                    showImportDialog = false
                    importJson = ""
                    snackbarText = if (result.addedCount > 0)
                        context.getString(R.string.import_success, result.addedCount)
                    else
                        context.getString(
                            R.string.import_failed_detail,
                            result.errorMessage ?: context.getString(R.string.import_failed)
                        )
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
    var importing by remember { mutableStateOf(false) }
    var snackbarText by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var checking by remember { mutableStateOf(false) }

    SourceManageScaffold(
        title = stringResource(R.string.manage_comic_sources),
        onNavigateBack = onNavigateBack,
        onAdd = { showImportDialog = true },
        onCheckValidity = {
            scope.launch {
                checking = true
                snackbarText = context.getString(R.string.source_checking)
                val result = mutableMapOf<Long, Boolean>()
                sources.forEach { s ->
                    result[s.id] = repo.checkComicSourceValid(s)
                }
                validity = result
                checking = false
                val ok = result.values.count { it }
                val fail = result.values.count { !it }
                snackbarText = context.getString(R.string.source_check_done, ok, fail)
            }
        },
        sources = sources.map { SourceListEntry(it.id, it.name, it.baseUrl, it.enabled, it.isBuiltIn, validity[it.id]) },
        selectedIds = selectedIds,
        snackbarText = snackbarText,
        onSnackbarDismiss = { snackbarText = "" },
        onToggleSelect = { id ->
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        },
        onToggleEnabled = { id, enabled ->
            sources.find { it.id == id }?.let {
                scope.launch { repo.updateComicSource(it.copy(enabled = enabled)) }
            }
        },
        onSelectAll = { selectedIds = sources.filter { !it.isBuiltIn }.map { it.id }.toSet() },
        onInvertSelect = {
            val all = sources.filter { !it.isBuiltIn }.map { it.id }.toSet()
            selectedIds = all - selectedIds
        },
        onDeleteSelected = {
            scope.launch {
                val toDelete = sources.filter { it.id in selectedIds && !it.isBuiltIn }
                toDelete.forEach { repo.deleteComicSource(it) }
                selectedIds = emptySet()
                snackbarText = context.getString(R.string.deleted_count, toDelete.size)
            }
        }
    )

    if (showImportDialog) {
        ImportSourceJsonDialog(
            onDismiss = {
                if (!importing) {
                    showImportDialog = false
                    importJson = ""
                }
            },
            json = importJson,
            onJsonChange = { importJson = it },
            importing = importing,
            onImport = {
                scope.launch {
                    importing = true
                    val result = repo.importComicSourcesFromText(importJson)
                    importing = false
                    showImportDialog = false
                    importJson = ""
                    snackbarText = if (result.addedCount > 0)
                        context.getString(R.string.import_success, result.addedCount)
                    else
                        context.getString(
                            R.string.import_failed_detail,
                            result.errorMessage ?: context.getString(R.string.import_failed)
                        )
                }
            }
        )
    }
}

data class SourceListEntry(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean = false,
    val isValid: Boolean? = null   // null=unchecked, true=ok, false=invalid
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceManageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    onCheckValidity: () -> Unit,
    sources: List<SourceListEntry>,
    selectedIds: Set<Long>,
    snackbarText: String,
    onSnackbarDismiss: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val nonBuiltIn = sources.count { !it.isBuiltIn }
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
                    IconButton(onClick = onCheckValidity) {
                        Icon(Icons.Default.NetworkCheck, stringResource(R.string.source_check))
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, stringResource(R.string.import_source))
                    }
                }
            )
        },
        bottomBar = {
            SourceSelectionBottomBar(
                selectedCount = selectedIds.size,
                totalCount = nonBuiltIn,
                onSelectAll = onSelectAll,
                onInvertSelect = onInvertSelect,
                onDelete = onDeleteSelected
            )
        },
        snackbarHost = {
            if (snackbarText.isNotEmpty()) {
                Snackbar(
                    action = {
                        TextButton(onClick = onSnackbarDismiss) { Text(stringResource(R.string.ok)) }
                    }
                ) { Text(snackbarText) }
            }
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
                        onCheckedChange = {
                            if (!source.isBuiltIn) onToggleSelect(source.id)
                        },
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
            .clickable(enabled = !entry.isBuiltIn) { onCheckedChange() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (!entry.isBuiltIn) onCheckedChange() },
            enabled = !entry.isBuiltIn
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // validity icon
                when (entry.isValid) {
                    true -> Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    false -> Icon(Icons.Default.Error, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    null -> {}
                }
                if (entry.isValid != null) Spacer(Modifier.width(4.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isBuiltIn) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.builtin_badge),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
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
    importing: Boolean,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_source)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.import_source_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = json,
                    onValueChange = onJsonChange,
                    label = { Text(stringResource(R.string.paste_json_or_url)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10,
                    enabled = !importing
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = !importing && json.isNotBlank()) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
