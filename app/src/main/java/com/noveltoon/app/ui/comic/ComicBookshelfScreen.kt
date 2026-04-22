package com.noveltoon.app.ui.comic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    var showMenu by remember { mutableStateOf(false) }
    var selectedComic by remember { mutableStateOf<Comic?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
        contentWindowInsets = WindowInsets.statusBars,
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
            if (comics.isEmpty()) {
                ComicEmptyState(onSearch = onNavigateToSearch)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comics, key = { it.id }) { comic ->
                        ComicGridItem(
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
fun ComicGridItem(
    comic: Comic,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                shape = RoundedCornerShape(12.dp),
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
                            comic.title.take(4),
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
                        .padding(4.dp)
                ) {
                    Text("NEW")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            comic.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (comic.lastChapterTitle.isNotBlank()) {
            Text(
                comic.lastChapterTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
