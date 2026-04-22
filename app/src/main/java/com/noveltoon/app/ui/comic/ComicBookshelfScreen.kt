package com.noveltoon.app.ui.comic

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
import androidx.compose.material.icons.filled.Search
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
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.ui.novel.BookshelfTopBar
import com.noveltoon.app.ui.novel.UrlImportDialog
import com.noveltoon.app.ui.theme.BrandBackground
import com.noveltoon.app.ui.theme.BrandBackgroundTop
import com.noveltoon.app.util.FileImporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicBookshelfScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToReader: (Long, Int) -> Unit,
    onNavigateToSourceManage: () -> Unit,
    viewModel: ComicViewModel = viewModel()
) {
    val comics by viewModel.comics.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var selectedComic by remember { mutableStateOf<Comic?>(null) }
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
                val result = FileImporter.importLocalComic(context, it)
                if (result != null) {
                    viewModel.addLocalComic(result.first, result.second)
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
                title = stringResource(R.string.tab_comic),
                subtitle = stringResource(R.string.comic_subtitle),
                onSearch = onNavigateToSearch,
                onRefresh = {
                    comics.forEach { comic ->
                        if (!comic.isLocal) viewModel.refreshChapters(comic.id)
                    }
                },
                onAdd = { showMenu = true },
                showMenu = showMenu,
                onMenuDismiss = { showMenu = false },
                onImportLocal = {
                    showMenu = false
                    filePickerLauncher.launch(arrayOf("application/zip", "application/x-cbz", "*/*"))
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
            } else if (comics.isEmpty()) {
                ComicEmptyState(onSearch = onNavigateToSearch)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comics, key = { it.id }) { comic ->
                        ComicListItem(
                            comic = comic,
                            onClick = { onNavigateToReader(comic.id, comic.lastReadChapterIndex) },
                            onLongClick = {
                                selectedComic = comic
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && selectedComic != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(selectedComic!!.title) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComic(selectedComic!!.id)
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showUrlImportDialog) {
        UrlImportDialog(
            title = stringResource(R.string.import_from_url_comic),
            onDismiss = { showUrlImportDialog = false },
            onConfirm = { url ->
                showUrlImportDialog = false
                viewModel.importFromUrl(url)
            }
        )
    }
}

@Composable
fun ComicEmptyState(onSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.illustration_comic_empty),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_comic_shelf),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_comic_shelf_desc),
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
                stringResource(R.string.search_comic),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicListItem(
    comic: Comic,
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
            Box {
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
                    if (comic.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(comic.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = comic.title,
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
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                comic.title.take(2),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (comic.hasUnread) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) { Text("NEW") }
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
                    comic.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (comic.author.isNotBlank()) comic.author else stringResource(R.string.author_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (comic.lastChapterTitle.isNotBlank())
                        stringResource(R.string.read_to, comic.lastChapterTitle)
                    else stringResource(R.string.unread),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.total_reading, com.noveltoon.app.util.TimeFormat.formatReadingTime(comic.totalReadingTimeMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
