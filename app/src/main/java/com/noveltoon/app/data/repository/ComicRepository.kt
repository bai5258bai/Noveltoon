package com.noveltoon.app.data.repository

import android.content.Context
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.data.entity.ComicChapter
import com.noveltoon.app.data.entity.ComicSource
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.parser.SourceParser
import kotlinx.coroutines.flow.Flow
import java.io.File

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

    private suspend fun resolveSource(sourceName: String): ComicSource? {
        if (sourceName.isBlank()) return null
        return comicSourceDao.findByName(sourceName)
            ?: comicSourceDao.getEnabledSources().find { it.name == sourceName }
            ?: comicSourceDao.getAllSourcesOnce().find { it.name == sourceName }
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

        val source = resolveSource(result.sourceName)
        if (source != null) {
            try {
                val chapters = parser.getComicChapters(source, result.url)
                val entities = chapters.mapIndexed { index, info ->
                    ComicChapter(
                        comicId = comicId,
                        title = info.title.ifBlank { result.title },
                        url = info.url,
                        index = index
                    )
                }
                if (entities.isNotEmpty()) {
                    chapterDao.insertAll(entities)
                    comicDao.update(comicDao.getComicById(comicId)!!.copy(totalChapters = entities.size))
                }
            } catch (_: Exception) {}
        }
        return comicId
    }

    suspend fun loadChapterImages(comicId: Long, chapterIndex: Int): List<String> {
        val comic = comicDao.getComicById(comicId) ?: return emptyList()
        var chapters = chapterDao.getChaptersList(comicId)
        if (chapters.isEmpty() && !comic.isLocal && comic.sourceName.isNotBlank()) {
            refreshChapters(comicId)
            chapters = chapterDao.getChaptersList(comicId)
        }
        val chapter = chapters.getOrNull(chapterIndex) ?: return emptyList()

        // Local CBZ/ZIP / URL-imported comics
        if (comic.isLocal ||
            comic.sourceName == "URL Import" ||
            comic.sourceName == "Local Import" ||
            comic.sourceName.isBlank()
        ) {
            val local = resolveLocalOrInlineImages(chapter.url, comic.localPath)
            if (local.isNotEmpty()) return local
            if (comic.isLocal ||
                comic.sourceName == "URL Import" ||
                comic.sourceName == "Local Import"
            ) {
                return emptyList()
            }
        }

        val source = resolveSource(comic.sourceName) ?: return emptyList()
        return parser.getComicImages(source, chapter.url)
    }

    /**
     * Local comic chapter.url is either:
     * - a directory path (FileImporter)
     * - newline-separated image URLs/paths (URL Import)
     * - a single image path
     */
    private fun resolveLocalOrInlineImages(chapterUrl: String, localPath: String): List<String> {
        if (chapterUrl.contains('\n')) {
            return chapterUrl.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        }
        val candidates = listOf(chapterUrl, localPath).filter { it.isNotBlank() }
        for (path in candidates) {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                continue
            }
            val dir = File(path)
            if (dir.isDirectory) {
                val images = dir.walkTopDown()
                    .filter { it.isFile && isImageFile(it.name) }
                    .sortedBy { it.absolutePath }
                    .map { it.absolutePath }
                    .toList()
                if (images.isNotEmpty()) return images
            }
            if (dir.isFile && isImageFile(dir.name)) {
                return listOf(dir.absolutePath)
            }
        }
        // Inline remote URLs without newlines (single image)
        if (chapterUrl.startsWith("http://") || chapterUrl.startsWith("https://")) {
            return listOf(chapterUrl)
        }
        return emptyList()
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".gif")
    }

    suspend fun refreshChapters(comicId: Long) {
        val comic = comicDao.getComicById(comicId) ?: return
        if (comic.isLocal) return
        val source = resolveSource(comic.sourceName) ?: return
        val chapters = parser.getComicChapters(source, comic.sourceUrl)
        val entities = chapters.mapIndexed { index, info ->
            ComicChapter(
                comicId = comicId,
                title = info.title.ifBlank { comic.title },
                url = info.url,
                index = index
            )
        }
        if (entities.isEmpty()) return
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
        val newSource = resolveSource(newSourceName) ?: return false
        val results = parser.searchComic(newSource, comic.title)
            .filter { it.title.contains(comic.title, ignoreCase = true) }
        val match = results.firstOrNull() ?: return false
        comicDao.update(
            comic.copy(
                sourceUrl = match.url,
                sourceName = newSource.name,
                coverUrl = match.coverUrl.ifBlank { comic.coverUrl },
                author = match.author.ifBlank { comic.author },
                isLocal = false
            )
        )
        val chapters = parser.getComicChapters(newSource, match.url)
        val entities = chapters.mapIndexed { index, info ->
            ComicChapter(
                comicId = comicId,
                title = info.title.ifBlank { comic.title },
                url = info.url,
                index = index
            )
        }
        if (entities.isEmpty()) return false
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
