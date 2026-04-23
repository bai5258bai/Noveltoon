package com.noveltoon.app.ui.novel

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.ui.theme.ReaderBlack
import com.noveltoon.app.ui.theme.ReaderGray
import com.noveltoon.app.ui.theme.ReaderGreen
import com.noveltoon.app.ui.theme.ReaderParchment
import com.noveltoon.app.ui.theme.ReaderWhite
import com.noveltoon.app.util.rememberBatteryLevel
import com.noveltoon.app.util.rememberCurrentTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
    novelId: Long,
    onNavigateBack: () -> Unit,
    viewModel: NovelViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    val novel by viewModel.currentNovel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val content by viewModel.chapterContent.collectAsState()
    val isLoading by viewModel.isLoadingContent.collectAsState()
    val sources by viewModel.bookSources.collectAsState()

    val fontSize by prefs.novelFontSize.collectAsState(initial = 18f)
    val lineSpacing by prefs.novelLineSpacing.collectAsState(initial = 1.5f)
    val pageMargin by prefs.novelPageMargin.collectAsState(initial = 16)
    val bgIndex by prefs.novelBackground.collectAsState(initial = 0)
    val readingMode by prefs.novelReadingMode.collectAsState(initial = 1)

    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPagesInChapter by remember { mutableIntStateOf(1) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSourceSwitch by remember { mutableStateOf(false) }
    var chapterLoaded by remember { mutableStateOf(false) }

    // Reading time tracking
    var sessionStart by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        sessionStart = System.currentTimeMillis()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (sessionStart > 0) {
            val elapsed = System.currentTimeMillis() - sessionStart
            if (elapsed in 1_000..3_600_000) viewModel.addReadingTime(novelId, elapsed)
            sessionStart = 0L
        }
    }
    DisposableEffect(novelId) {
        onDispose {
            if (sessionStart > 0) {
                val elapsed = System.currentTimeMillis() - sessionStart
                if (elapsed in 1_000..3_600_000) viewModel.addReadingTime(novelId, elapsed)
            }
        }
    }

    val backgroundColor = when (bgIndex) {
        0 -> ReaderWhite
        1 -> ReaderParchment
        2 -> ReaderGray
        3 -> ReaderBlack
        4 -> ReaderGreen
        else -> ReaderWhite
    }
    val textColor = if (bgIndex == 3) Color(0xFFDCDCDC) else Color(0xFF2A2A2A)

    LaunchedEffect(novelId) {
        viewModel.loadNovel(novelId)
    }

    LaunchedEffect(novel, chapters) {
        if (!chapterLoaded && chapters.isNotEmpty() && novel != null) {
            currentChapterIndex = novel!!.lastReadChapterIndex.coerceIn(0, chapters.size - 1)
            viewModel.loadChapter(novelId, currentChapterIndex)
            chapterLoaded = true
        }
    }

    LaunchedEffect(currentChapterIndex) {
        currentPageIndex = 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading && content.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: ""

            val onPageBack: () -> Unit = {
                if (readingMode == 1) {
                    if (currentPageIndex > 0) {
                        currentPageIndex--
                    } else if (currentChapterIndex > 0) {
                        currentChapterIndex--
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                } else {
                    if (currentChapterIndex > 0) {
                        currentChapterIndex--
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                }
            }
            val onPageForward: () -> Unit = {
                if (readingMode == 1) {
                    if (currentPageIndex < totalPagesInChapter - 1) {
                        currentPageIndex++
                    } else if (currentChapterIndex < chapters.size - 1) {
                        currentChapterIndex++
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                } else {
                    if (currentChapterIndex < chapters.size - 1) {
                        currentChapterIndex++
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                }
            }

            if (readingMode == 0) {
                ScrollReader(
                    content = content,
                    chapterTitle = chapterTitle,
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    pageMargin = pageMargin,
                    textColor = textColor,
                    currentChapterIndex = currentChapterIndex,
                    totalChapters = chapters.size,
                    onPrev = {
                        if (currentChapterIndex > 0) {
                            currentChapterIndex--
                            viewModel.loadChapter(novelId, currentChapterIndex)
                        }
                    },
                    onNext = {
                        if (currentChapterIndex < chapters.size - 1) {
                            currentChapterIndex++
                            viewModel.loadChapter(novelId, currentChapterIndex)
                        }
                    }
                )
                LaunchedEffect(content) {
                    totalPagesInChapter = 1
                    currentPageIndex = 0
                }
            } else {
                PageReader(
                    content = content,
                    chapterTitle = chapterTitle,
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    pageMargin = pageMargin,
                    textColor = textColor,
                    currentChapterIndex = currentChapterIndex,
                    totalChapters = chapters.size,
                    targetPage = currentPageIndex,
                    onPageInfoChanged = { pageIndex, total ->
                        currentPageIndex = pageIndex
                        totalPagesInChapter = total
                    },
                    battery = rememberBatteryLevel().value,
                    time = rememberCurrentTime().value
                )
            }

            // Three-zone click overlay: left = back, center = toggle controls, right = forward
            ThreeZoneTapOverlay(
                onLeft = onPageBack,
                onCenter = { showControls = !showControls },
                onRight = onPageForward
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = novel?.title ?: "",
                onBack = onNavigateBack,
                onRefresh = {
                    viewModel.refreshChapters(novelId)
                    viewModel.loadChapter(novelId, currentChapterIndex)
                },
                onSwitchSource = { showSourceSwitch = true },
                onSettings = { showSettings = true }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NovelReaderBottomBar(
                currentChapterIndex = currentChapterIndex,
                totalChapters = chapters.size,
                currentPageIndex = currentPageIndex,
                totalPagesInChapter = totalPagesInChapter,
                onPrevChapter = {
                    if (currentChapterIndex > 0) {
                        currentChapterIndex--
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                },
                onNextChapter = {
                    if (currentChapterIndex < chapters.size - 1) {
                        currentChapterIndex++
                        viewModel.loadChapter(novelId, currentChapterIndex)
                    }
                },
                onShowChapters = { showChapterList = true },
                onShowSettings = { showSettings = true },
                onSeekPage = { newPageIndex ->
                    currentPageIndex = newPageIndex.coerceIn(0, (totalPagesInChapter - 1).coerceAtLeast(0))
                }
            )
        }
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
                items(chapters) { chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        modifier = Modifier.clickable {
                            currentChapterIndex = chapter.index
                            viewModel.loadChapter(novelId, chapter.index)
                            showChapterList = false
                        },
                        colors = if (chapter.index == currentChapterIndex)
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ListItemDefaults.colors()
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            ReaderSettingsPanel(
                fontSize = fontSize,
                lineSpacing = lineSpacing,
                pageMargin = pageMargin,
                bgIndex = bgIndex,
                readingMode = readingMode,
                onFontSizeChange = { scope.launch { prefs.setNovelFontSize(it) } },
                onLineSpacingChange = { scope.launch { prefs.setNovelLineSpacing(it) } },
                onPageMarginChange = { scope.launch { prefs.setNovelPageMargin(it) } },
                onBgChange = { scope.launch { prefs.setNovelBackground(it) } },
                onReadingModeChange = { scope.launch { prefs.setNovelReadingMode(it) } }
            )
        }
    }

    if (showSourceSwitch) {
        SourceSwitchDialog(
            currentSourceName = novel?.sourceName ?: "",
            sourceNames = sources.filter { it.enabled }.map { it.name },
            onDismiss = { showSourceSwitch = false },
            onSelect = { name ->
                showSourceSwitch = false
                viewModel.switchSource(novelId, name)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchSource: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 2.dp,
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            }
            IconButton(onClick = onSwitchSource) {
                Icon(Icons.Default.Source, stringResource(R.string.switch_source))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.reader_settings))
            }
        }
    }
}

@Composable
fun NovelReaderBottomBar(
    currentChapterIndex: Int,
    totalChapters: Int,
    currentPageIndex: Int,
    totalPagesInChapter: Int,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowChapters: () -> Unit,
    onShowSettings: () -> Unit,
    onSeekPage: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (totalPagesInChapter > 1) {
                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = { onSeekPage(it.roundToInt()) },
                    valueRange = 0f..(totalPagesInChapter - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${currentPageIndex + 1} / ${totalPagesInChapter.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
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
                ReaderBottomIconButton(
                    icon = Icons.Default.SkipPrevious,
                    label = stringResource(R.string.prev_chapter),
                    onClick = onPrevChapter,
                    enabled = currentChapterIndex > 0
                )
                ReaderBottomIconButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.chapter_list),
                    onClick = onShowChapters
                )
                ReaderBottomIconButton(
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.reader_settings),
                    onClick = onShowSettings
                )
                ReaderBottomIconButton(
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
fun ReaderBottomIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            maxLines = 1
        )
    }
}

@Composable
fun ScrollReader(
    content: String,
    chapterTitle: String,
    fontSize: Float,
    lineSpacing: Float,
    pageMargin: Int,
    textColor: Color,
    currentChapterIndex: Int,
    totalChapters: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = pageMargin.dp, vertical = 16.dp)
    ) {
        if (chapterTitle.isNotBlank()) {
            Text(
                text = chapterTitle,
                style = TextStyle(
                    fontSize = (fontSize + 4).sp,
                    color = textColor,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
        Text(
            text = content,
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineSpacing).sp,
                color = textColor
            )
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentChapterIndex > 0) {
                TextButton(onClick = onPrev) {
                    Text(stringResource(R.string.prev_chapter), color = textColor.copy(alpha = 0.7f))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (currentChapterIndex < totalChapters - 1) {
                TextButton(onClick = onNext) {
                    Text(stringResource(R.string.next_chapter), color = textColor.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageReader(
    content: String,
    chapterTitle: String,
    fontSize: Float,
    lineSpacing: Float,
    pageMargin: Int,
    textColor: Color,
    currentChapterIndex: Int,
    totalChapters: Int,
    targetPage: Int,
    onPageInfoChanged: (Int, Int) -> Unit,
    battery: Int,
    time: String
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val availableWidthPx = with(density) { (maxWidth - (pageMargin * 2).dp).toPx() }
        val availableHeightPx = with(density) { (maxHeight - 80.dp).toPx() }

        val textStyle = TextStyle(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineSpacing).sp,
            color = textColor
        )

        val pages = remember(content, fontSize, lineSpacing, pageMargin, availableWidthPx, availableHeightPx) {
            splitIntoPages(
                content = content,
                textMeasurer = textMeasurer,
                style = textStyle,
                widthPx = availableWidthPx.roundToInt(),
                heightPx = availableHeightPx.roundToInt()
            )
        }

        val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

        LaunchedEffect(content, pages.size) {
            pagerState.scrollToPage(0)
            onPageInfoChanged(0, pages.size.coerceAtLeast(1))
        }

        LaunchedEffect(pagerState.currentPage) {
            onPageInfoChanged(pagerState.currentPage, pages.size.coerceAtLeast(1))
        }

        LaunchedEffect(targetPage) {
            if (targetPage != pagerState.currentPage && targetPage in pages.indices) {
                pagerState.scrollToPage(targetPage)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            if (chapterTitle.isNotBlank()) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelMedium.copy(color = textColor.copy(alpha = 0.6f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = pageMargin.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val pageContent = pages.getOrNull(pageIndex) ?: ""
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = pageMargin.dp)
                ) {
                    Text(text = pageContent, style = textStyle)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = pageMargin.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "第 ${currentChapterIndex + 1} 章",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${battery}%  ·  $time",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    "${pagerState.currentPage + 1} / ${pages.size.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ThreeZoneTapOverlay(
    onLeft: () -> Unit,
    onCenter: () -> Unit,
    onRight: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLeft
                )
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCenter
                )
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRight
                )
        )
    }
}

private fun splitIntoPages(
    content: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<String> {
    if (content.isBlank() || widthPx <= 0 || heightPx <= 0) return listOf(content)

    val pages = mutableListOf<String>()
    val constraints = Constraints(maxWidth = widthPx, maxHeight = Int.MAX_VALUE)
    val layout = textMeasurer.measure(text = content, style = style, constraints = constraints)

    val totalHeight = layout.size.height
    if (totalHeight <= heightPx) {
        return listOf(content)
    }

    val lineCount = layout.lineCount
    var pageStartLine = 0
    var pageStartY = layout.getLineTop(0)

    for (lineIndex in 0 until lineCount) {
        val lineBottom = layout.getLineBottom(lineIndex)
        if (lineBottom - pageStartY > heightPx && lineIndex > pageStartLine) {
            val startOffset = layout.getLineStart(pageStartLine)
            val endOffset = layout.getLineStart(lineIndex)
            pages.add(content.substring(startOffset, endOffset))
            pageStartLine = lineIndex
            pageStartY = layout.getLineTop(lineIndex)
        }
    }
    if (pageStartLine < lineCount) {
        val startOffset = layout.getLineStart(pageStartLine)
        pages.add(content.substring(startOffset))
    }
    return if (pages.isEmpty()) listOf(content) else pages
}

@Composable
fun ReaderSettingsPanel(
    fontSize: Float,
    lineSpacing: Float,
    pageMargin: Int,
    bgIndex: Int,
    readingMode: Int,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onPageMarginChange: (Int) -> Unit,
    onBgChange: (Int) -> Unit,
    onReadingModeChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.reading_mode), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = readingMode == 1,
                onClick = { onReadingModeChange(1) },
                label = { Text(stringResource(R.string.reading_mode_horizontal)) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = readingMode == 0,
                onClick = { onReadingModeChange(0) },
                label = { Text(stringResource(R.string.reading_mode_vertical)) },
                leadingIcon = { Icon(Icons.Default.SwapVert, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.font_size), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", fontSize = 12.sp)
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..32f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("A", fontSize = 24.sp)
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.line_spacing), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = lineSpacing,
            onValueChange = onLineSpacingChange,
            valueRange = 1.0f..3.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.page_margin), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = pageMargin.toFloat(),
            onValueChange = { onPageMarginChange(it.toInt()) },
            valueRange = 0f..48f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.background_color), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val bgColors = listOf(
                ReaderWhite to stringResource(R.string.bg_white),
                ReaderParchment to stringResource(R.string.bg_parchment),
                ReaderGray to stringResource(R.string.bg_gray),
                ReaderBlack to stringResource(R.string.bg_black),
                ReaderGreen to stringResource(R.string.bg_green)
            )
            bgColors.forEachIndexed { index, (color, name) ->
                FilterChip(
                    selected = bgIndex == index,
                    onClick = { onBgChange(index) },
                    label = { Text(name, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = color)
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * @param sourceEntries list of (sourceName, isSearching, hasResult)
 *   isSearching = currently probing; hasResult = probe found the title in this source
 */
data class SourceSwitchEntry(
    val name: String,
    val isSearching: Boolean = false,
    val hasResult: Boolean? = null   // null=unchecked
)

@Composable
fun SourceSwitchDialog(
    currentSourceName: String,
    sourceNames: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.switch_source)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (sourceNames.isEmpty()) {
                    Text(stringResource(R.string.no_sources))
                } else {
                    Text(
                        stringResource(R.string.switch_source_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn {
                        items(sourceNames) { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(name) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = name == currentSourceName,
                                    onClick = { onSelect(name) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (name == currentSourceName) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(stringResource(R.string.source_current), style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
