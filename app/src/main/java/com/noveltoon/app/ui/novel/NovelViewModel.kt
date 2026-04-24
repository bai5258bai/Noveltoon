package com.noveltoon.app.ui.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NovelRepository(application)
    private val bookSourceDao = AppDatabase.getInstance(application).bookSourceDao()

    val bookSources: StateFlow<List<BookSource>> = bookSourceDao.getAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _contentLoadError = MutableStateFlow(false)
    val contentLoadError: StateFlow<Boolean> = _contentLoadError.asStateFlow()

    fun search(keyword: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            try {
                repository.search(keyword) { partial ->
                    _searchResults.value = partial
                }
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
            _contentLoadError.value = false
            try {
                val content = repository.loadChapterContent(novelId, chapterIndex)
                _chapterContent.value = content
                if (content.isBlank()) {
                    _contentLoadError.value = true
                } else {
                    val chapter = _chapters.value.getOrNull(chapterIndex)
                    if (chapter != null) {
                        repository.updateReadProgress(novelId, chapterIndex, 0, chapter.title)
                        _currentNovel.value = repository.getNovelById(novelId)
                    }
                }
            } catch (_: Exception) {
                _contentLoadError.value = true
            }
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

    private val _importState = MutableStateFlow<String?>(null)
    val importState: StateFlow<String?> = _importState.asStateFlow()

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _importState.value = "loading"
            try {
                val id = repository.importFromUrl(url)
                _importState.value = if (id > 0) "success" else "failed"
            } catch (e: Exception) {
                _importState.value = "failed"
            }
        }
    }

    fun clearImportState() {
        _importState.value = null
    }

    fun addReadingTime(id: Long, ms: Long) {
        if (ms <= 0) return
        viewModelScope.launch { repository.addReadingTime(id, ms) }
    }

    private val _availableSources = MutableStateFlow<List<String>>(emptyList())
    val availableSources: StateFlow<List<String>> = _availableSources.asStateFlow()

    private val _isFindingSources = MutableStateFlow(false)
    val isFindingSources: StateFlow<Boolean> = _isFindingSources.asStateFlow()

    fun findAvailableSources(novelId: Long) {
        viewModelScope.launch {
            val novel = repository.getNovelById(novelId) ?: return@launch
            _isFindingSources.value = true
            _availableSources.value = emptyList()
            try {
                val found = repository.findSourcesForTitle(novel.title)
                _availableSources.value = found.map { it.first }
            } catch (_: Exception) {}
            _isFindingSources.value = false
        }
    }

    fun switchSource(novelId: Long, newSourceName: String) {
        viewModelScope.launch {
            try {
                val ok = repository.switchSource(novelId, newSourceName)
                if (ok) {
                    _currentNovel.value = repository.getNovelById(novelId)
                    _chapters.value = repository.getChaptersList(novelId)
                }
            } catch (_: Exception) {}
        }
    }
}
