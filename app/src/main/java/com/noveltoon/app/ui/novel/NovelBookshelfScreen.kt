package com.noveltoon.app.ui.novel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noveltoon.app.R
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.ui.theme.BrandBackground
import com.noveltoon.app.ui.theme.BrandBackgroundTop
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
    val importState by viewModel.importState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var selectedNovel by remember { mutableStateOf<Novel?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var snackbarText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(importState) {
        when (importState) {
            "success" -> {
                snackbarText = context.getString(R.string.import_url_success)
                viewModel.clearImportState()
            }
            "failed" -> {
                snackbarText = context.getString(R.string.import_url_failed)
                viewModel.clearImportState()
            }
        }
    }

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

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(BrandBackgroundTop, BrandBackground)
    )

    Scaffold(
        modifier = Modifier.background(gradientBrush),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            if (snackbarText.isNotEmpty()) {
                Snackbar(
                    action = {
                        TextButton(onClick = { snackbarText = "" }) { Text(stringResource(R.string.ok)) }
                    }
                ) { Text(snackbarText) }
            }
        },
        topBar = {
            BookshelfTopBar(
                title = stringResource(R.string.tab_novel),
                subtitle = stringResource(R.string.novel_subtitle),
                onSearch = onNavigateToSearch,
                onRefresh = {
                    novels.forEach { novel ->
                        if (!novel.isLocal) viewModel.refreshChapters(novel.id)
                    }
                },
                onAdd = { showMenu = true },
                showMenu = showMenu,
                onMenuDismiss = { showMenu = false },
                onImportLocal = {
                    showMenu = false
                    filePickerLauncher.launch(arrayOf("text/plain", "application/epub+zip", "*/*"))
                },
                onImportUrl = {
                    showMenu = false
                    showUrlImportDialog = true
                },
                onManageSources = {
                    showMenu = false
                    onNavigateToSourceManage()
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(padding)
        ) {
            if (importState == "loading") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.importing))
                    }
                }
            } else if (novels.isEmpty()) {
                NovelEmptyState(onSearch = onNavigateToSearch)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(novels, key = { it.id }) { novel ->
                        NovelListItem(
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
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showUrlImportDialog) {
        UrlImportDialog(
            title = stringResource(R.string.import_from_url_novel),
            onDismiss = { showUrlImportDialog = false },
            onConfirm = { url ->
                showUrlImportDialog = false
                viewModel.importFromUrl(url)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfTopBar(
    title: String,
    subtitle: String,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onImportLocal: () -> Unit,
    onImportUrl: () -> Unit,
    onManageSources: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RoundIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = onRefresh
            )
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                icon = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                onClick = onSearch
            )
            Spacer(Modifier.width(8.dp))
            Box {
                RoundIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add),
                    onClick = onAdd
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_local_file)) },
                        onClick = onImportLocal,
                        leadingIcon = { Icon(Icons.Default.FileOpen, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_from_url)) },
                        onClick = onImportUrl,
                        leadingIcon = { Icon(Icons.Default.Link, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_sources)) },
                        onClick = onManageSources,
                        leadingIcon = { Icon(Icons.Default.Source, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun NovelEmptyState(onSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.illustration_novel_empty),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_bookshelf),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_bookshelf_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSearch,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.search_novel),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelListItem(
    novel: Novel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Card(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(3f / 4f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            novel.title.take(2),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 80.dp * 4 / 3),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    novel.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (novel.author.isNotBlank()) novel.author else stringResource(R.string.author_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (novel.lastChapterTitle.isNotBlank())
                        stringResource(R.string.read_to, novel.lastChapterTitle)
                    else stringResource(R.string.unread),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.total_reading, com.noveltoon.app.util.TimeFormat.formatReadingTime(novel.totalReadingTimeMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun UrlImportDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    stringResource(R.string.url_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text("https://...") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) onConfirm(url.trim())
            }) {
                Text(stringResource(R.string.import_action))
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
