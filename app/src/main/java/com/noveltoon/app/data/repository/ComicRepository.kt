package com.noveltoon.app.data.repository

import android.content.Context
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.data.entity.ComicChapter
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.parser.SourceParser
import kotlinx.coroutines.flow.Flow

class ComicRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val comicDao = db.comicDao()
    private val chapterDao = db.comicChapterDao()
    private val comicSourceDao = db.comicSourceDao()
    private val parser = SourceParser()

    fun getAllComics(): Flow<List<Comic>> = comicDao.getAllComics()

    suspend fun getComicById(id: Long): Comic? = comicDao.getComicById(id)

    suspend fun addComic(comic: Comic): Long = comicDao.insert(comic)

    suspend fun updateComic(comic: Comic) = comicDao.update(comic)

    suspend fun deleteComic(id: Long) = comicDao.deleteById(id)

    suspend fun updateReadProgress(id: Long, chapterIndex: Int, pageIndex: Int, chapterTitle: String) =
        comicDao.updateReadProgress(id, chapterIndex, pageIndex, chapterTitle)

    suspend fun addReadingTime(id: Long, ms: Long) = comicDao.addReadingTime(id, ms)

    fun getChapters(comicId: Long): Flow<List<ComicChapter>> = chapterDao.getChapters(comicId)

    suspend fun getChaptersList(comicId: Long): List<ComicChapter> = chapterDao.getChaptersList(comicId)

    suspend fun search(
        keyword: String,
        onBatch: (suspend (List<SearchResult>) -> Unit)? = null
    ): List<SearchResult> {
        val sources = comicSourceDao.getEnabledSources()
        return parser.searchComicAllSources(sources, keyword, onBatch)
    }

    suspend fun addFromSearchResult(result: SearchResult): Long {
        val existing = comicDao.findComic(result.title, result.url)
        if (existing != null) return existing.id

        val comic = Comic(
            title = result.title,
            author = result.author,
            coverUrl = result.coverUrl,
            sourceUrl = result.url,
            sourceName = result.sourceName,
            status = result.status
        )
        val comicId = comicDao.insert(comic)

        val source = comicSourceDao.getEnabledSources().find { it.name == result.sourceName }
        if (source != null) {
            try {
                val chapters = parser.getComicChapters(source, result.url)
                val safeChapters = chapters.ifEmpty {
                    listOf(com.noveltoon.app.data.parser.ChapterInfo(result.title, result.url))
                }
                val entities = safeChapters.mapIndexed { index, info ->
                    ComicChapter(comicId = comicId, title = info.title.ifBlank { result.title }, url = info.url, index = index)
                }
                chapterDao.insertAll(entities)
                comicDao.update(comicDao.getComicById(comicId)!!.copy(totalChapters = entities.size))
            } catch (_: Exception) {}
        }
        return comicId
    }

    suspend fun loadChapterImages(comicId: Long, chapterIndex: Int): List<String> {
        val comic = comicDao.getComicById(comicId) ?: return emptyList()
        val chapters = chapterDao.getChaptersList(comicId)
        val chapter = chapters.getOrNull(chapterIndex) ?: return emptyList()

        // URL-imported comic stores images as newline-separated list in url
        if (comic.sourceName == "URL Import") {
            return chapter.url.split("\n").filter { it.isNotBlank() }
        }

        val source = comicSourceDao.getEnabledSources().find { it.name == comic.sourceName }
            ?: return emptyList()
        return parser.getComicImages(source, chapter.url)
    }

    suspend fun refreshChapters(comicId: Long) {
        val comic = comicDao.getComicById(comicId) ?: return
        val source = comicSourceDao.getEnabledSources().find { it.name == comic.sourceName } ?: return
        val chapters = parser.getComicChapters(source, comic.sourceUrl)
        val safeChapters = chapters.ifEmpty {
            listOf(com.noveltoon.app.data.parser.ChapterInfo(comic.title, comic.sourceUrl))
        }
        val entities = safeChapters.mapIndexed { index, info ->
            ComicChapter(comicId = comicId, title = info.title.ifBlank { comic.title }, url = info.url, index = index)
        }
        chapterDao.deleteByComicId(comicId)
        chapterDao.insertAll(entities)
        comicDao.update(comic.copy(totalChapters = entities.size))
    }

    suspend fun findSourcesForTitle(title: String): List<Pair<String, String>> {
        val sources = comicSourceDao.getEnabledSources()
        return sources.mapNotNull { source ->
            try {
                val results = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    parser.searchComic(source, title).filter { it.title.contains(title, ignoreCase = true) }
                }
                if (!results.isNullOrEmpty()) source.name to results.first().url else null
            } catch (_: Exception) { null }
        }
    }

    suspend fun switchSource(comicId: Long, newSourceName: String): Boolean {
        val comic = comicDao.getComicById(comicId) ?: return false
        val newSource = comicSourceDao.getEnabledSources().find { it.name == newSourceName } ?: return false
        val results = parser.searchComic(newSource, comic.title)
            .filter { it.title.contains(comic.title, ignoreCase = true) }
        val match = results.firstOrNull() ?: return false
        comicDao.update(
            comic.copy(
                sourceUrl = match.url,
                sourceName = newSource.name,
                coverUrl = match.coverUrl.ifBlank { comic.coverUrl },
                author = match.author.ifBlank { comic.author }
            )
        )
        val chapters = parser.getComicChapters(newSource, match.url)
        val safeChapters = chapters.ifEmpty {
            listOf(com.noveltoon.app.data.parser.ChapterInfo(match.title.ifBlank { comic.title }, match.url))
        }
        val entities = safeChapters.mapIndexed { index, info ->
            ComicChapter(comicId = comicId, title = info.title.ifBlank { comic.title }, url = info.url, index = index)
        }
        chapterDao.deleteByComicId(comicId)
        chapterDao.insertAll(entities)
        comicDao.update(comicDao.getComicById(comicId)!!.copy(totalChapters = entities.size))
        return true
    }

    suspend fun importFromUrl(url: String): Long {
        val images = parser.fetchImagesFromUrl(url)
        if (images.isEmpty()) return -1
        val title = parser.guessTitleFromUrl(url)
        val comic = Comic(
            title = title,
            sourceUrl = url,
            sourceName = "URL Import",
            isLocal = true,
            localPath = url,
            totalChapters = 1
        )
        val comicId = comicDao.insert(comic)
        val chapter = ComicChapter(
            comicId = comicId,
            title = title,
            url = images.joinToString("\n"),
            index = 0
        )
        chapterDao.insertAll(listOf(chapter))
        return comicId
    }
}
