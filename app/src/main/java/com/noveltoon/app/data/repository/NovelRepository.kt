package com.noveltoon.app.data.repository

import android.content.Context
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import com.noveltoon.app.data.parser.ChapterOrder
import com.noveltoon.app.data.parser.NovelAccess
import com.noveltoon.app.data.parser.NovelAccessKind
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
    ): NovelAccess<List<SearchResult>> {
        val sources = bookSourceDao.getEnabledSources()
        if (sources.isEmpty()) return NovelAccess.noSource(emptyList(), "没有启用的书源")
        return parser.searchNovelAllSources(sources, keyword, onBatch)
    }

    private suspend fun resolveSource(sourceName: String): BookSource? {
        if (sourceName.isBlank()) return null
        return bookSourceDao.findByName(sourceName)
            ?: bookSourceDao.getEnabledSources().find { it.name == sourceName }
            ?: bookSourceDao.getAllSourcesOnce().find { it.name == sourceName }
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

        val source = resolveSource(result.sourceName)
        if (source != null) {
            val chapters = parser.getNovelChapters(source, result.url)
            if (chapters.kind == NovelAccessKind.SUCCESS) {
                val entities = chapters.value.mapIndexed { index, info ->
                    NovelChapter(
                        novelId = novelId,
                        title = info.title.ifBlank { "第${index + 1}章" },
                        url = info.url,
                        index = index
                    )
                }
                if (entities.isNotEmpty()) {
                    chapterDao.insertAll(entities)
                    novelDao.update(novelDao.getNovelById(novelId)!!.copy(totalChapters = entities.size))
                }
            }
        }
        return novelId
    }

    /**
     * Catalogs saved newest-first are rewritten into reading order.
     * A saved index of 0 stays at the start. A later bookmark is kept by chapter title.
     */
    suspend fun normalizeStoredChapterOrder(novelId: Long) {
        val existing = chapterDao.getChaptersList(novelId)
        if (!ChapterOrder.needsReverse(existing.map { it.title })) return
        val novel = novelDao.getNovelById(novelId) ?: return
        val ordered = existing.asReversed().mapIndexed { index, chapter -> chapter.copy(index = index) }
        val start = ChapterOrder.restoredIndex(
            novel.lastReadChapterIndex,
            novel.lastChapterTitle,
            ordered.map { it.title }
        )
        chapterDao.deleteByNovelId(novelId)
        chapterDao.insertAll(ordered)
        novelDao.update(
            novel.copy(
                totalChapters = ordered.size,
                lastReadChapterIndex = start,
                lastChapterTitle = ordered.getOrNull(start)?.title ?: novel.lastChapterTitle
            )
        )
    }

    suspend fun loadChapterContent(novelId: Long, chapterIndex: Int): NovelAccess<String> {
        val novel = novelDao.getNovelById(novelId) ?: return NovelAccess.noResult("", "找不到这本书")
        var chapter = chapterDao.getChapterByIndex(novelId, chapterIndex)
        if (chapter == null) {
            if (novel.isLocal) return NovelAccess.noResult("", "本地章节没有正文")
            val refreshed = refreshChapters(novelId)
            if (refreshed.kind != NovelAccessKind.SUCCESS) {
                return NovelAccess(refreshed.kind, "", refreshed.detail)
            }
            chapter = chapterDao.getChapterByIndex(novelId, chapterIndex)
                ?: return NovelAccess.noResult("", "目录中没有这一章")
        }
        if (chapter.isCached && chapter.content.isNotBlank()) return NovelAccess.ok(chapter.content)
        if (novel.isLocal) {
            return if (chapter.content.isBlank()) NovelAccess.noResult("", "本地章节没有正文")
            else NovelAccess.ok(chapter.content)
        }

        val source = resolveSource(novel.sourceName)
            ?: return NovelAccess.rule("", "找不到书源「${novel.sourceName}」")
        val content = parser.getNovelContent(source, chapter.url)
        if (content.kind == NovelAccessKind.SUCCESS) {
            chapterDao.update(chapter.copy(content = content.value, isCached = true))
        }
        return content
    }

    suspend fun refreshChapters(novelId: Long): NovelAccess<Int> {
        val novel = novelDao.getNovelById(novelId) ?: return NovelAccess.noResult(0, "找不到这本书")
        if (novel.isLocal) return NovelAccess.noResult(0, "本地书没有网络目录")
        val source = resolveSource(novel.sourceName)
            ?: return NovelAccess.rule(0, "找不到书源「${novel.sourceName}」")
        val chapters = parser.getNovelChapters(source, novel.sourceUrl)
        if (chapters.kind != NovelAccessKind.SUCCESS) {
            return NovelAccess(chapters.kind, 0, chapters.detail)
        }
        val entities = chapters.value.mapIndexed { index, info ->
            NovelChapter(
                novelId = novelId,
                title = info.title.ifBlank { "第${index + 1}章" },
                url = info.url,
                index = index
            )
        }
        chapterDao.deleteByNovelId(novelId)
        chapterDao.insertAll(entities)
        novelDao.update(novel.copy(totalChapters = entities.size))
        return NovelAccess.ok(entities.size)
    }

    /** Find sources that have results for the given title; returns list of (sourceName, firstResultUrl) */
    suspend fun findSourcesForTitle(title: String): List<Pair<String, String>> {
        val sources = bookSourceDao.getEnabledSources()
        return sources.mapNotNull { source ->
            try {
                val results = withTimeoutOrNull(8_000L) {
                    parser.searchNovel(source, title)
                }
                val hits = results?.value.orEmpty().filter { it.title.contains(title, ignoreCase = true) }
                if (hits.isNotEmpty()) source.name to hits.first().url else null
            } catch (_: Exception) { null }
        }
    }

    suspend fun switchSource(novelId: Long, newSourceName: String): Boolean {
        val novel = novelDao.getNovelById(novelId) ?: return false
        val newSource = resolveSource(newSourceName) ?: return false
        val results = parser.searchNovel(newSource, novel.title)
        val match = results.value.firstOrNull { it.title.contains(novel.title, ignoreCase = true) } ?: return false
        novelDao.update(
            novel.copy(
                sourceUrl = match.url,
                sourceName = newSource.name,
                coverUrl = match.coverUrl.ifBlank { novel.coverUrl },
                author = match.author.ifBlank { novel.author },
                isLocal = false
            )
        )
        val chapters = parser.getNovelChapters(newSource, match.url)
        if (chapters.kind != NovelAccessKind.SUCCESS || chapters.value.isEmpty()) return false
        val entities = chapters.value.mapIndexed { index, info ->
            NovelChapter(
                novelId = novelId,
                title = info.title.ifBlank { "第${index + 1}章" },
                url = info.url,
                index = index
            )
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
