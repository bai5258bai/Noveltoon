package com.noveltoon.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noveltoon.app.data.parser.NovelAccessKind
import com.noveltoon.app.data.repository.NovelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator. One public-domain Wikisource search, then the
 * bookshelf insert, catalog, chapter text, and the next chapter.
 */
@RunWith(AndroidJUnit4::class)
class NovelInstallLoopTest {
    @Test
    fun freshInstall_searchesWikisource_addsBook_readsTwoChapters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = SourceInitializer.initIfNeeded(context)
        assertNull(report.error)
        assertTrue(report.bookCount >= 1)

        val repository = NovelRepository(context)
        val found = repository.search("聊齋")
        assertEquals(found.detail, NovelAccessKind.SUCCESS, found.kind)
        val book = found.value.firstOrNull { it.title == "聊齋志異" }
        assertTrue("titles=${found.value.map { it.title }}", book != null)

        val novelId = repository.addFromSearchResult(book!!)
        assertTrue(novelId > 0)
        val chapters = repository.getChaptersList(novelId)
        assertTrue("chapters=${chapters.map { it.title }}", chapters.size >= 2)

        val first = repository.loadChapterContent(novelId, 0)
        val second = repository.loadChapterContent(novelId, 1)
        assertEquals(first.detail, NovelAccessKind.SUCCESS, first.kind)
        assertEquals(second.detail, NovelAccessKind.SUCCESS, second.kind)
        assertTrue(first.value.length > 80)
        assertTrue(second.value.length > 80)
        assertNotEquals(first.value, second.value)
    }
}
