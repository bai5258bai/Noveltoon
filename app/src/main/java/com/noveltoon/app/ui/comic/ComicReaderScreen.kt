package com.noveltoon.app.ui.comic

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.ui.novel.SourceSwitchDialog
import com.noveltoon.app.util.rememberBatteryLevel
import com.noveltoon.app.util.rememberCurrentTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    comicId: Long,
    initialChapterIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: ComicViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    val comic by viewModel.currentComic.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoadingImages.collectAsState()

    val readingDirection by prefs.comicReadingDirection.collectAsState(initial = 2)
    val sources by viewModel.comicSources.collectAsState()

    var currentChapterIndex by remember { mutableIntStateOf(initialChapterIndex) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSourceSwitch by remember { mutableStateOf(false) }

    // Reading time tracking
    var sessionStart by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        sessionStart = System.currentTimeMillis()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (sessionStart > 0) {
            val elapsed = System.currentTimeMillis() - sessionStart
            if (elapsed in 1_000..3_600_000) viewModel.addReadingTime(comicId, elapsed)
            sessionStart = 0L
        }
    }
    DisposableEffect(comicId) {
        onDispose {
            if (sessionStart > 0) {
                val elapsed = System.currentTimeMillis() - sessionStart
                if (elapsed in 1_000..3_600_000) viewModel.addReadingTime(comicId, elapsed)
            }
        }
    }

    LaunchedEffect(comicId) {
        viewModel.loadComic(comicId)
    }

    LaunchedEffect(chapters) {
        if (chapters.isNotEmpty()) {
            viewModel.loadChapterImages(comicId, currentChapterIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val battery by rememberBatteryLevel()
        val time by rememberCurrentTime()

        if (isLoading && images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_images), color = Color.White)
            }
        } else {
            when (readingDirection) {
                0, 1 -> HorizontalComicReader(
                    images = images,
                    isRtl = readingDirection == 1,
                    initialPage = comic?.lastReadPageIndex ?: 0,
                    targetPage = currentPageIndex,
                    onPageChanged = { page ->
                        currentPageIndex = page
                        viewModel.saveReadProgress(comicId, currentChapterIndex, page)
                    },
                    onTap = { showControls = !showControls }
                )
                2 -> VerticalComicReader(
                    images = images,
                    initialPage = comic?.lastReadPageIndex ?: 0,
                    onPageChanged = { page ->
                        currentPageIndex = page
                        viewModel.saveReadProgress(comicId, currentChapterIndex, page)
                    },
                    onTap = { showControls = !showControls }
                )
            }
        }

        // Top bar
        // Persistent battery/time overlay at bottom when controls are hidden
        if (!showControls && images.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${currentPageIndex + 1} / ${images.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Text(
                    "${battery}%  ·  $time",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ComicReaderTopBar(
                title = comic?.title ?: "",
                chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "",
                onBack = onNavigateBack,
                onRefresh = {
                    viewModel.refreshChapters(comicId)
                    viewModel.loadChapterImages(comicId, currentChapterIndex)
                },
                onSwitchSource = { showSourceSwitch = true }
            )
        }

        // Bottom bar - from bottom up, persists
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ComicReaderBottomBar(
                currentChapterIndex = currentChapterIndex,
                totalChapters = chapters.size,
                currentPageIndex = currentPageIndex,
                totalPages = images.size,
                readingDirection = readingDirection,
                onPrevChapter = {
                    if (currentChapterIndex > 0) {
                        currentChapterIndex--
                        viewModel.loadChapterImages(comicId, currentChapterIndex)
                    }
                },
                onNextChapter = {
                    if (currentChapterIndex < chapters.size - 1) {
                        currentChapterIndex++
                        viewModel.loadChapterImages(comicId, currentChapterIndex)
                    }
                },
                onShowChapters = { showChapterList = true },
                onToggleDirection = {
                    val next = (readingDirection + 1) % 3
                    scope.launch { prefs.setComicReadingDirection(next) }
                },
                onSeekPage = { newPage ->
                    currentPageIndex = newPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
                }
            )
        }
    }

    if (showSourceSwitch) {
        SourceSwitchDialog(
            currentSourceName = comic?.sourceName ?: "",
            sourceNames = sources.filter { it.enabled }.map { it.name },
            onDismiss = { showSourceSwitch = false },
            onSelect = { name ->
                showSourceSwitch = false
                viewModel.switchSource(comicId, name)
            }
        )
    }

    if (showChapterList) {
        ModalBottomSheet(
            onDismissRequest = { showChapterList = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Text(
                stringResource(R.string.chapter_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        modifier = Modifier.clickable {
                            currentChapterIndex = index
                            viewModel.loadChapterImages(comicId, index)
                            showChapterList = false
                        },
                        colors = if (index == currentChapterIndex)
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ListItemDefaults.colors()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderTopBar(
    title: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchSource: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh), tint = Color.White)
            }
            IconButton(onClick = onSwitchSource) {
                Icon(Icons.Default.Source, stringResource(R.string.switch_source), tint = Color.White)
            }
        }
    }
}

@Composable
fun ComicReaderBottomBar(
    currentChapterIndex: Int,
    totalChapters: Int,
    currentPageIndex: Int,
    totalPages: Int,
    readingDirection: Int,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowChapters: () -> Unit,
    onToggleDirection: () -> Unit,
    onSeekPage: (Int) -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.88f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (totalPages > 1) {
                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = { onSeekPage(it.roundToInt()) },
                    valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.8f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    "${currentPageIndex + 1} / ${totalPages.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComicBottomIconButton(
                    icon = Icons.Default.SkipPrevious,
                    label = stringResource(R.string.prev_chapter),
                    onClick = onPrevChapter,
                    enabled = currentChapterIndex > 0
                )
                ComicBottomIconButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.chapter_list),
                    onClick = onShowChapters
                )
                ComicBottomIconButton(
                    icon = when (readingDirection) {
                        0 -> Icons.Default.SwipeRight
                        1 -> Icons.Default.SwipeLeft
                        else -> Icons.Default.SwipeDown
                    },
                    label = stringResource(R.string.direction),
                    onClick = onToggleDirection
                )
                ComicBottomIconButton(
                    icon = Icons.Default.SkipNext,
                    label = stringResource(R.string.next_chapter),
                    onClick = onNextChapter,
                    enabled = currentChapterIndex < totalChapters - 1
                )
            }
        }
    }
}

@Composable
fun ComicBottomIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White.copy(alpha = 0.8f)
            else Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun HorizontalComicReader(
    images: List<String>,
    isRtl: Boolean,
    initialPage: Int,
    targetPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    LaunchedEffect(targetPage) {
        if (targetPage != pagerState.currentPage && targetPage in images.indices) {
            pagerState.scrollToPage(targetPage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = isRtl,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                imageUrl = images[page],
                onTap = onTap
            )
        }
    }
}

@Composable
fun VerticalComicReader(
    images: List<String>,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    )

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChanged(listState.firstVisibleItemIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(images) { _, imageUrl ->
                ComicPageImage(imageUrl = imageUrl)
            }
        }
    }
}

@Composable
fun ZoomableImage(
    imageUrl: String,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (scale > 1.1f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        onTap()
                    }
                }
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.FillWidth,
            loading = {
                Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun ComicPageImage(imageUrl: String) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    )
}
