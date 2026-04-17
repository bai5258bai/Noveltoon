package com.noveltoon.app.ui.novel

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_novel)) },
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
                    placeholder = { Text(stringResource(R.string.search_hint_novel)) },
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
                        NovelSearchResultItem(
                            result = result,
                            onClick = {
                                viewModel.addFromSearch(result) { novelId ->
                                    onNavigateToReader(novelId)
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
fun NovelSearchResultItem(result: SearchResult, onClick: () -> Unit) {
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
                if (result.latestChapter.isNotBlank()) {
                    Text(
                        result.latestChapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
