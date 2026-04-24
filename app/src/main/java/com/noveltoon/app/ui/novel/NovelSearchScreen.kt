package com.noveltoon.app.ui.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noveltoon.app.R
import com.noveltoon.app.data.parser.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelSearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    viewModel: NovelViewModel = viewModel()
) {
    var searchText by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_novel)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.search_hint_novel))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (searchText.isNotBlank()) viewModel.search(searchText.trim()) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }

            // Streaming: show linear progress while searching, results appear as they come in
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (isSearching && searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.searching_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchResults.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.search_results_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isSearching) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    items(searchResults, key = { it.url + it.sourceName }) { result ->
                        NovelSearchResultCard(
                            result = result,
                            onClick = {
                                viewModel.addFromSearch(result) { novelId ->
                                    onNavigateToReader(novelId)
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
fun NovelSearchResultCard(result: SearchResult, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Cover
            Card(
                modifier = Modifier
                    .size(64.dp, 88.dp)
                    .clip(RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (result.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(result.coverUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        result.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.latestChapter.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        result.latestChapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (result.sourceName.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                result.sourceName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}
