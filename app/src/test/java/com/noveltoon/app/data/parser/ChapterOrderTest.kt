package com.noveltoon.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterOrderTest {
    @Test
    fun newestFirstCatalog_opensAtTheEarliestChapter() {
        val stored = listOf(
            ChapterInfo("第100章 终", "https://example/100"),
            ChapterInfo("第99章", "https://example/99"),
            ChapterInfo("第1章 起", "https://example/1")
        )
        val ordered = ChapterOrder.arrange(stored)
        assertEquals("第1章 起", ordered.first().title)
        assertEquals("第100章 终", ordered.last().title)
        assertEquals(0, ChapterOrder.restoredIndex(0, "第100章 终", ordered.map { it.title }))
        assertEquals(2, ChapterOrder.restoredIndex(1, "第100章 终", ordered.map { it.title }))
    }

    @Test
    fun ascendingVolumes_stayInPlace() {
        val titles = listOf("第一卷", "第二卷", "第十二卷")
        assertFalse(ChapterOrder.needsReverse(titles))
        assertEquals(1, ChapterOrder.chapterNumber("第一卷"))
        assertEquals(12, ChapterOrder.chapterNumber("第十二卷"))
        assertEquals(120, ChapterOrder.chapterNumber("第百二十回　终"))
        assertEquals(1, ChapterOrder.chapterNumber("第001回　起"))
    }

    @Test
    fun savedMiddleChapter_isFoundAfterReorder() {
        val titles = ChapterOrder.arrange(
            (10 downTo 1).map { ChapterInfo("第${it}章", "https://example/$it") }
        ).map { it.title }
        assertEquals("第1章", titles.first())
        assertTrue(ChapterOrder.restoredIndex(4, "第6章", titles) == titles.indexOf("第6章"))
    }
}
