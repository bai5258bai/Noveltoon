package com.noveltoon.app.ui.novel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noveltoon.app.R
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.util.FileImporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovelBookshelfScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToSourceManage: () -> Unit,
    viewModel: NovelViewModel = viewModel()
) {
    val novels by viewModel.novels.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var selectedNovel by remember { mutableStateOf<Novel?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val result = FileImporter.importTxtNovel(context, it)
                if (result != null) {
                    viewModel.addLocalNovel(result.first, result.second)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_novel)) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_local_file)) },
                            onClick = {
                                showMenu = false
                                filePickerLauncher.launch(arrayOf("text/plain", "application/epub+zip"))
                            },
                            leadingIcon = { Icon(Icons.Default.FileOpen, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_sources)) },
                            onClick = {
                                showMenu = false
                                onNavigateToSourceManage()
                            },
                            leadingIcon = { Icon(Icons.Default.Source, null) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (novels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.empty_bookshelf),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_novel))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(novels, key = { it.id }) { novel ->
                    NovelGridItem(
                        novel = novel,
                        onClick = { onNavigateToReader(novel.id) },
                        onLongClick = {
                            selectedNovel = novel
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog && selectedNovel != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(selectedNovel!!.title) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNovel(selectedNovel!!.id)
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.markCompleted(selectedNovel!!.id, !selectedNovel!!.isCompleted)
                    showDeleteDialog = false
                }) {
                    Text(
                        if (selectedNovel!!.isCompleted) stringResource(R.string.mark_reading)
                        else stringResource(R.string.mark_completed)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelGridItem(
    novel: Novel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (novel.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(novel.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = novel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        novel.title.take(4),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            novel.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (novel.lastChapterTitle.isNotBlank()) {
            Text(
                novel.lastChapterTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
