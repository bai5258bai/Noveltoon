package com.noveltoon.app.ui.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NovelRepository(application)

    val novels: StateFlow<List<Novel>> = repository.getAllNovels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentNovel = MutableStateFlow<Novel?>(null)
    val currentNovel: StateFlow<Novel?> = _currentNovel.asStateFlow()

    private val _chapters = MutableStateFlow<List<NovelChapter>>(emptyList())
    val chapters: StateFlow<List<NovelChapter>> = _chapters.asStateFlow()

    private val _chapterContent = MutableStateFlow("")
    val chapterContent: StateFlow<String> = _chapterContent.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    fun search(keyword: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            try {
                _searchResults.value = repository.search(keyword)
            } catch (_: Exception) {}
            _isSearching.value = false
        }
    }

    fun addFromSearch(result: SearchResult, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = repository.addFromSearchResult(result)
                onAdded(id)
            } catch (_: Exception) {}
        }
    }

    fun deleteNovel(id: Long) {
        viewModelScope.launch { repository.deleteNovel(id) }
    }

    fun markCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { repository.markCompleted(id, completed) }
    }

    fun loadNovel(novelId: Long) {
        viewModelScope.launch {
            _currentNovel.value = repository.getNovelById(novelId)
            _chapters.value = repository.getChaptersList(novelId)
        }
    }

    fun loadChapter(novelId: Long, chapterIndex: Int) {
        viewModelScope.launch {
            _isLoadingContent.value = true
            try {
                val content = repository.loadChapterContent(novelId, chapterIndex)
                _chapterContent.value = content
                val chapter = _chapters.value.getOrNull(chapterIndex)
                if (chapter != null) {
                    repository.updateReadProgress(novelId, chapterIndex, 0, chapter.title)
                    _currentNovel.value = repository.getNovelById(novelId)
                }
            } catch (_: Exception) {}
            _isLoadingContent.value = false
        }
    }

    fun saveReadProgress(novelId: Long, chapterIndex: Int, position: Int) {
        viewModelScope.launch {
            val chapter = _chapters.value.getOrNull(chapterIndex) ?: return@launch
            repository.updateReadProgress(novelId, chapterIndex, position, chapter.title)
        }
    }

    fun refreshChapters(novelId: Long) {
        viewModelScope.launch {
            repository.refreshChapters(novelId)
            _chapters.value = repository.getChaptersList(novelId)
        }
    }

    fun addLocalNovel(novel: Novel, chapters: List<NovelChapter>) {
        viewModelScope.launch {
            val id = repository.addNovel(novel)
            repository.saveChapters(chapters.map { it.copy(novelId = id) })
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            try {
                repository.importFromUrl(url)
            } catch (_: Exception) {}
        }
    }
}
