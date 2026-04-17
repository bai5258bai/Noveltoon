package com.noveltoon.app.data.repository

import android.content.Context
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import com.noveltoon.app.data.parser.SearchResult
import com.noveltoon.app.data.parser.SourceParser
import kotlinx.coroutines.flow.Flow

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

    fun getChapters(novelId: Long): Flow<List<NovelChapter>> = chapterDao.getChapters(novelId)

    suspend fun getChaptersList(novelId: Long): List<NovelChapter> = chapterDao.getChaptersList(novelId)

    suspend fun getChapterByIndex(novelId: Long, index: Int): NovelChapter? =
        chapterDao.getChapterByIndex(novelId, index)

    suspend fun saveChapters(chapters: List<NovelChapter>) = chapterDao.insertAll(chapters)

    suspend fun updateChapter(chapter: NovelChapter) = chapterDao.update(chapter)

    suspend fun search(keyword: String): List<SearchResult> {
        val sources = bookSourceDao.getEnabledSources()
        val results = mutableListOf<SearchResult>()
        for (source in sources) {
            try {
                results.addAll(parser.searchNovel(source, keyword))
            } catch (_: Exception) {}
        }
        return results
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
                val entities = chapters.mapIndexed { index, info ->
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
        }
        return novelId
    }

    suspend fun loadChapterContent(novelId: Long, chapterIndex: Int): String {
        val chapter = chapterDao.getChapterByIndex(novelId, chapterIndex) ?: return ""
        if (chapter.isCached && chapter.content.isNotBlank()) return chapter.content

        val novel = novelDao.getNovelById(novelId) ?: return ""
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
}
