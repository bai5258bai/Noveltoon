package com.noveltoon.app.ui.comic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.data.entity.ComicChapter
import com.noveltoon.app.data.entity.ComicSource
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.repository.ComicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ComicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ComicRepository(application)
    private val comicSourceDao = AppDatabase.getInstance(application).comicSourceDao()

    val comicSources: StateFlow<List<ComicSource>> = comicSourceDao.getAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val comics: StateFlow<List<Comic>> = repository.getAllComics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private var searchJob: Job? = null

    private val _currentComic = MutableStateFlow<Comic?>(null)
    val currentComic: StateFlow<Comic?> = _currentComic.asStateFlow()

    private val _chapters = MutableStateFlow<List<ComicChapter>>(emptyList())
    val chapters: StateFlow<List<ComicChapter>> = _chapters.asStateFlow()

    private val _images = MutableStateFlow<List<String>>(emptyList())
    val images: StateFlow<List<String>> = _images.asStateFlow()

    private val _isLoadingImages = MutableStateFlow(false)
    val isLoadingImages: StateFlow<Boolean> = _isLoadingImages.asStateFlow()

    private val _imageLoadError = MutableStateFlow(false)
    val imageLoadError: StateFlow<Boolean> = _imageLoadError.asStateFlow()

    fun search(keyword: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            try {
                repository.search(keyword) { partial ->
                    _searchResults.value = partial.distinctBy { it.sourceName + "|" + it.url + "|" + it.title }
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

    fun deleteComic(id: Long) {
        viewModelScope.launch { repository.deleteComic(id) }
    }

    fun loadComic(comicId: Long) {
        viewModelScope.launch {
            _currentComic.value = repository.getComicById(comicId)
            _chapters.value = repository.getChaptersList(comicId)
        }
    }

    fun loadChapterImages(comicId: Long, chapterIndex: Int) {
        viewModelScope.launch {
            _isLoadingImages.value = true
            _imageLoadError.value = false
            _images.value = emptyList()
            try {
                val imgs = repository.loadChapterImages(comicId, chapterIndex)
                _images.value = imgs
                if (imgs.isEmpty()) {
                    _imageLoadError.value = true
                } else {
                    val chapter = _chapters.value.getOrNull(chapterIndex)
                    if (chapter != null) {
                        repository.updateReadProgress(comicId, chapterIndex, 0, chapter.title)
                        _currentComic.value = repository.getComicById(comicId)
                    }
                }
            } catch (_: Exception) {
                _imageLoadError.value = true
            }
            _isLoadingImages.value = false
        }
    }

    fun saveReadProgress(comicId: Long, chapterIndex: Int, pageIndex: Int) {
        viewModelScope.launch {
            val chapter = _chapters.value.getOrNull(chapterIndex) ?: return@launch
            repository.updateReadProgress(comicId, chapterIndex, pageIndex, chapter.title)
        }
    }

    fun addLocalComic(comic: Comic, chapters: List<ComicChapter>) {
        viewModelScope.launch {
            val id = repository.addComic(comic)
            val chaptersWithId = chapters.map { it.copy(comicId = id) }
            val db = com.noveltoon.app.data.AppDatabase.getInstance(getApplication())
            db.comicChapterDao().insertAll(chaptersWithId)
        }
    }

    fun refreshChapters(comicId: Long) {
        viewModelScope.launch {
            repository.refreshChapters(comicId)
            _chapters.value = repository.getChaptersList(comicId)
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

    fun findAvailableSources(comicId: Long) {
        viewModelScope.launch {
            val comic = repository.getComicById(comicId) ?: return@launch
            _isFindingSources.value = true
            _availableSources.value = emptyList()
            try {
                val found = repository.findSourcesForTitle(comic.title)
                _availableSources.value = found.map { it.first }
            } catch (_: Exception) {}
            _isFindingSources.value = false
        }
    }

    fun switchSource(comicId: Long, newSourceName: String) {
        viewModelScope.launch {
            try {
                val ok = repository.switchSource(comicId, newSourceName)
                if (ok) {
                    _currentComic.value = repository.getComicById(comicId)
                    _chapters.value = repository.getChaptersList(comicId)
                }
            } catch (_: Exception) {}
        }
    }
}
