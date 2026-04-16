package com.noveltoon.app.ui.novel

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.ui.theme.*
import kotlinx.coroutines.launch

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

    val fontSize by prefs.novelFontSize.collectAsState(initial = 18f)
    val lineSpacing by prefs.novelLineSpacing.collectAsState(initial = 1.5f)
    val pageMargin by prefs.novelPageMargin.collectAsState(initial = 16)
    val bgIndex by prefs.novelBackground.collectAsState(initial = 0)

    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val backgroundColor = when (bgIndex) {
        0 -> ReaderWhite
        1 -> ReaderParchment
        2 -> ReaderGray
        3 -> ReaderBlack
        4 -> ReaderGreen
        else -> ReaderWhite
    }
    val textColor = if (bgIndex == 3) Color.LightGray else Color(0xFF333333)

    LaunchedEffect(novelId) {
        viewModel.loadNovel(novelId)
    }

    LaunchedEffect(novel) {
        novel?.let {
            currentChapterIndex = it.lastReadChapterIndex
            if (chapters.isNotEmpty()) {
                viewModel.loadChapter(novelId, currentChapterIndex)
            }
        }
    }

    LaunchedEffect(chapters) {
        if (chapters.isNotEmpty() && content.isEmpty()) {
            viewModel.loadChapter(novelId, currentChapterIndex)
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showControls = !showControls }
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = pageMargin.dp, vertical = 16.dp)
                ) {
                    val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: ""
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
                            TextButton(onClick = {
                                currentChapterIndex--
                                viewModel.loadChapter(novelId, currentChapterIndex)
                            }) {
                                Text(stringResource(R.string.prev_chapter), color = textColor.copy(alpha = 0.7f))
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        if (currentChapterIndex < chapters.size - 1) {
                            TextButton(onClick = {
                                currentChapterIndex++
                                viewModel.loadChapter(novelId, currentChapterIndex)
                            }) {
                                Text(stringResource(R.string.next_chapter), color = textColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            novel?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                if (currentChapterIndex > 0) {
                                    currentChapterIndex--
                                    viewModel.loadChapter(novelId, currentChapterIndex)
                                    showControls = false
                                }
                            }) {
                                Icon(Icons.Default.SkipPrevious, stringResource(R.string.prev_chapter))
                            }

                            IconButton(onClick = {
                                showChapterList = true
                                showControls = false
                            }) {
                                Icon(Icons.Default.List, stringResource(R.string.chapter_list))
                            }

                            IconButton(onClick = {
                                showSettings = true
                                showControls = false
                            }) {
                                Icon(Icons.Default.Settings, stringResource(R.string.reader_settings))
                            }

                            IconButton(onClick = {
                                if (currentChapterIndex < chapters.size - 1) {
                                    currentChapterIndex++
                                    viewModel.loadChapter(novelId, currentChapterIndex)
                                    showControls = false
                                }
                            }) {
                                Icon(Icons.Default.SkipNext, stringResource(R.string.next_chapter))
                            }
                        }

                        if (chapters.isNotEmpty()) {
                            Slider(
                                value = currentChapterIndex.toFloat(),
                                onValueChange = { newValue ->
                                    currentChapterIndex = newValue.toInt()
                                },
                                onValueChangeFinished = {
                                    viewModel.loadChapter(novelId, currentChapterIndex)
                                    showControls = false
                                },
                                valueRange = 0f..(chapters.size - 1).coerceAtLeast(1).toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${currentChapterIndex + 1} / ${chapters.size}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChapterList) {
        ModalBottomSheet(onDismissRequest = { showChapterList = false }) {
            Text(
                stringResource(R.string.chapter_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
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
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsPanel(
                fontSize = fontSize,
                lineSpacing = lineSpacing,
                pageMargin = pageMargin,
                bgIndex = bgIndex,
                onFontSizeChange = { scope.launch { prefs.setNovelFontSize(it) } },
                onLineSpacingChange = { scope.launch { prefs.setNovelLineSpacing(it) } },
                onPageMarginChange = { scope.launch { prefs.setNovelPageMargin(it) } },
                onBgChange = { scope.launch { prefs.setNovelBackground(it) } }
            )
        }
    }
}

@Composable
fun ReaderSettingsPanel(
    fontSize: Float,
    lineSpacing: Float,
    pageMargin: Int,
    bgIndex: Int,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onPageMarginChange: (Int) -> Unit,
    onBgChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.font_size), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", fontSize = 12.sp)
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..32f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text("A", fontSize = 24.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.line_spacing), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = lineSpacing,
            onValueChange = onLineSpacingChange,
            valueRange = 1.0f..3.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
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
            modifier = Modifier.fillMaxWidth(),
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
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = color
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
