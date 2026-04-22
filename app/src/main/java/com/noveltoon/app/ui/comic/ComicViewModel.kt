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

    private val _currentComic = MutableStateFlow<Comic?>(null)
    val currentComic: StateFlow<Comic?> = _currentComic.asStateFlow()

    private val _chapters = MutableStateFlow<List<ComicChapter>>(emptyList())
    val chapters: StateFlow<List<ComicChapter>> = _chapters.asStateFlow()

    private val _images = MutableStateFlow<List<String>>(emptyList())
    val images: StateFlow<List<String>> = _images.asStateFlow()

    private val _isLoadingImages = MutableStateFlow(false)
    val isLoadingImages: StateFlow<Boolean> = _isLoadingImages.asStateFlow()

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
            _images.value = emptyList()
            try {
                _images.value = repository.loadChapterImages(comicId, chapterIndex)
                val chapter = _chapters.value.getOrNull(chapterIndex)
                if (chapter != null) {
                    repository.updateReadProgress(comicId, chapterIndex, 0, chapter.title)
                    _currentComic.value = repository.getComicById(comicId)
                }
            } catch (_: Exception) {}
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

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            try {
                repository.importFromUrl(url)
            } catch (_: Exception) {}
        }
    }

    fun addReadingTime(id: Long, ms: Long) {
        if (ms <= 0) return
        viewModelScope.launch { repository.addReadingTime(id, ms) }
    }

    fun switchSource(comicId: Long, newSourceName: String) {
        viewModelScope.launch {
            try {
                repository.switchSource(comicId, newSourceName)
                _currentComic.value = repository.getComicById(comicId)
                _chapters.value = repository.getChaptersList(comicId)
            } catch (_: Exception) {}
        }
    }
}
