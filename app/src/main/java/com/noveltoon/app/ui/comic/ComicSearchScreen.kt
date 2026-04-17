package com.noveltoon.app.ui.comic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.noveltoon.app.data.parser.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (Long, Int) -> Unit,
    viewModel: ComicViewModel = viewModel()
) {
    var searchText by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_comic)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    placeholder = { Text(stringResource(R.string.search_hint_comic)) },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (searchText.isNotBlank()) viewModel.search(searchText.trim())
                    }
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }

            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(searchResults) { result ->
                        ComicSearchResultItem(
                            result = result,
                            onClick = {
                                viewModel.addFromSearch(result) { comicId ->
                                    onNavigateToReader(comicId, 0)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComicSearchResultItem(result: SearchResult, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (result.author.isNotBlank()) {
                    Text(result.author, style = MaterialTheme.typography.bodySmall)
                }
                if (result.status.isNotBlank()) {
                    Text(
                        result.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            if (result.coverUrl.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .size(48.dp, 64.dp)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(result.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        trailingContent = {
            if (result.sourceName.isNotBlank()) {
                AssistChip(
                    onClick = {},
                    label = { Text(result.sourceName, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    )
    HorizontalDivider()
}
