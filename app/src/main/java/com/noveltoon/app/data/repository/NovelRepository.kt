package com.noveltoon.app.data.repository

import android.content.Context
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.parser.SourceParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

class NovelRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val novelDao = db.novelDao()
    private val chapterDao = db.novelChapterDao()
    private val bookSourceDao = db.bookSourceDao()
    private val parser = SourceParser()

    fun getAllNovels(): Flow<List<Novel>> = novelDao.getAllNovels()

    suspend fun getNovelById(id: Long): Novel? = novelDao.getNovelById(id)

    suspend fun addNovel(novel: Novel): Long = novelDao.insert(novel)

    suspend fun updateNovel(novel: Novel) = novelDao.update(novel)

    suspend fun deleteNovel(id: Long) = novelDao.deleteById(id)

    suspend fun markCompleted(id: Long, completed: Boolean) = novelDao.markCompleted(id, completed)

    suspend fun updateReadProgress(id: Long, chapterIndex: Int, position: Int, chapterTitle: String) =
        novelDao.updateReadProgress(id, chapterIndex, position, chapterTitle)

    suspend fun addReadingTime(id: Long, ms: Long) = novelDao.addReadingTime(id, ms)

    fun getChapters(novelId: Long): Flow<List<NovelChapter>> = chapterDao.getChapters(novelId)

    suspend fun getChaptersList(novelId: Long): List<NovelChapter> = chapterDao.getChaptersList(novelId)

    suspend fun getChapterByIndex(novelId: Long, index: Int): NovelChapter? =
        chapterDao.getChapterByIndex(novelId, index)

    suspend fun saveChapters(chapters: List<NovelChapter>) = chapterDao.insertAll(chapters)

    suspend fun updateChapter(chapter: NovelChapter) = chapterDao.update(chapter)

    suspend fun search(
        keyword: String,
        onBatch: (suspend (List<SearchResult>) -> Unit)? = null
    ): List<SearchResult> {
        val sources = bookSourceDao.getEnabledSources()
        return parser.searchNovelAllSources(sources, keyword, onBatch)
    }

    suspend fun addFromSearchResult(result: SearchResult): Long {
        val existing = novelDao.findNovel(result.title, result.url)
        if (existing != null) return existing.id

        val novel = Novel(
            title = result.title,
            author = result.author,
            coverUrl = result.coverUrl,
            sourceUrl = result.url,
            sourceName = result.sourceName,
            lastChapterTitle = result.latestChapter
        )
        val novelId = novelDao.insert(novel)

        val source = bookSourceDao.getEnabledSources().find { it.name == result.sourceName }
        if (source != null) {
            try {
                val chapters = parser.getNovelChapters(source, result.url)
                val safeChapters = chapters.ifEmpty {
                    listOf(com.noveltoon.app.data.parser.ChapterInfo(result.latestChapter.ifBlank { "第 1 章" }, result.url))
                }
                val entities = safeChapters.mapIndexed { index, info ->
                    NovelChapter(
                        novelId = novelId,
                        title = info.title,
                        url = info.url,
                        index = index
                    )
                }
                chapterDao.insertAll(entities)
                novelDao.update(novelDao.getNovelById(novelId)!!.copy(totalChapters = entities.size))
            } catch (_: Exception) {}
        } else {
            chapterDao.insertAll(
                listOf(
                    NovelChapter(
                        novelId = novelId,
                        title = result.latestChapter.ifBlank { "第 1 章" },
                        url = result.url,
                        index = 0
                    )
                )
            )
            novelDao.update(novel.copy(id = novelId, totalChapters = 1))
        }
        return novelId
    }

    suspend fun loadChapterContent(novelId: Long, chapterIndex: Int): String {
        val novel = novelDao.getNovelById(novelId) ?: return ""
        val chapter = chapterDao.getChapterByIndex(novelId, chapterIndex)
            ?: NovelChapter(
                novelId = novelId,
                title = novel.lastChapterTitle.ifBlank { "第 1 章" },
                url = novel.sourceUrl,
                index = 0
            ).also { chapterDao.insertAll(listOf(it)) }
        if (chapter.isCached && chapter.content.isNotBlank()) return chapter.content

        val source = bookSourceDao.getEnabledSources().find { it.name == novel.sourceName } ?: return ""

        val content = parser.getNovelContent(source, chapter.url)
        if (content.isNotBlank()) {
            chapterDao.update(chapter.copy(content = content, isCached = true))
        }
        return content
    }

    suspend fun refreshChapters(novelId: Long) {
        val novel = novelDao.getNovelById(novelId) ?: return
        val source = bookSourceDao.getEnabledSources().find { it.name == novel.sourceName } ?: return
        val chapters = parser.getNovelChapters(source, novel.sourceUrl)
        val entities = chapters.mapIndexed { index, info ->
            NovelChapter(novelId = novelId, title = info.title, url = info.url, index = index)
        }
        chapterDao.deleteByNovelId(novelId)
        chapterDao.insertAll(entities)
        novelDao.update(novel.copy(totalChapters = entities.size))
    }

    /** Find sources that have results for the given title; returns list of (sourceName, firstResultUrl) */
    suspend fun findSourcesForTitle(title: String): List<Pair<String, String>> {
        val sources = bookSourceDao.getEnabledSources()
        return sources.mapNotNull { source ->
            try {
                val results = withTimeoutOrNull(8_000L) {
                    parser.searchNovel(source, title).filter { it.title.contains(title, ignoreCase = true) }
                }
                if (!results.isNullOrEmpty()) source.name to results.first().url else null
            } catch (_: Exception) { null }
        }
    }

    suspend fun switchSource(novelId: Long, newSourceName: String): Boolean {
        val novel = novelDao.getNovelById(novelId) ?: return false
        val newSource = bookSourceDao.getEnabledSources().find { it.name == newSourceName } ?: return false
        val results = parser.searchNovel(newSource, novel.title)
            .filter { it.title.contains(novel.title, ignoreCase = true) }
        val match = results.firstOrNull() ?: return false
        novelDao.update(
            novel.copy(
                sourceUrl = match.url,
                sourceName = newSource.name,
                coverUrl = match.coverUrl.ifBlank { novel.coverUrl },
                author = match.author.ifBlank { novel.author }
            )
        )
        val chapters = parser.getNovelChapters(newSource, match.url)
        val entities = chapters.mapIndexed { index, info ->
            NovelChapter(novelId = novelId, title = info.title, url = info.url, index = index)
        }
        chapterDao.deleteByNovelId(novelId)
        chapterDao.insertAll(entities)
        novelDao.update(novelDao.getNovelById(novelId)!!.copy(totalChapters = entities.size))
        return true
    }

    suspend fun importFromUrl(url: String): Long {
        val fetched = parser.fetchTextFromUrl(url)
        if (fetched.isBlank()) return -1
        val title = parser.guessTitleFromUrl(url)
        val novel = Novel(
            title = title,
            sourceUrl = url,
            sourceName = "URL Import",
            isLocal = true,
            localPath = url
        )
        val novelId = novelDao.insert(novel)
        val chapters = parser.splitTextIntoChapters(fetched).mapIndexed { index, pair ->
            NovelChapter(
                novelId = novelId,
                title = pair.first,
                url = "",
                index = index,
                content = pair.second,
                isCached = true
            )
        }
        chapterDao.insertAll(chapters)
        novelDao.update(novelDao.getNovelById(novelId)!!.copy(totalChapters = chapters.size))
        return novelId
    }
}
