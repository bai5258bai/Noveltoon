package com.noveltoon.app.ui.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noveltoon.app.R
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import com.noveltoon.app.data.repository.SourceRepository
import kotlinx.coroutines.launch

private const val BUILTIN_PASSWORD = "Bai."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInSourcePasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_required)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = {
                        pwd = it
                        error = false
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    supportingText = {
                        if (error) Text(
                            stringResource(R.string.password_wrong),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pwd == BUILTIN_PASSWORD) onSuccess() else error = true
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInSourceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SourceRepository(context) }
    val scope = rememberCoroutineScope()

    val bookSources by repo.getAllBookSources().collectAsState(initial = emptyList())
    val comicSources by repo.getAllComicSources().collectAsState(initial = emptyList())

    val builtInBook = bookSources.filter { it.isBuiltIn }
    val builtInComic = comicSources.filter { it.isBuiltIn }

    var tabIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.builtin_source_manage)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text(stringResource(R.string.builtin_book_sources)) }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text(stringResource(R.string.builtin_comic_sources)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                when (tabIndex) {
                    0 -> BuiltInBookList(builtInBook) { s, enabled ->
                        scope.launch { repo.updateBookSource(s.copy(enabled = enabled)) }
                    }
                    1 -> BuiltInComicList(builtInComic) { s, enabled ->
                        scope.launch { repo.updateComicSource(s.copy(enabled = enabled)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun BuiltInBookList(
    sources: List<BookSource>,
    onToggle: (BookSource, Boolean) -> Unit
) {
    if (sources.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_sources),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(sources, key = { it.id }) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        source.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = { onToggle(source, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInComicList(
    sources: List<ComicSource>,
    onToggle: (ComicSource, Boolean) -> Unit
) {
    if (sources.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_sources),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(sources, key = { it.id }) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        source.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = { onToggle(source, it) }
                    )
                }
            }
        }
    }
}
