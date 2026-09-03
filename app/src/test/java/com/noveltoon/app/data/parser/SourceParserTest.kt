package com.noveltoon.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceParserTest {
    private val parser = SourceParser()

    @Test
    fun cleanContent_removesHtmlAndKnownJunk() {
        val content = parser.cleanContent(
            "<p>第一段&nbsp;内容</p><p>第二段</p>" +
                "\n天才一秒记住本站地址：https://example.com\n" +
                "广告【广告推荐】"
        )

        assertTrue(content.contains("第一段 内容"))
        assertTrue(content.contains("第二段"))
        assertFalse(content.contains("本站地址"))
        assertFalse(content.contains("【广告推荐】"))
    }

    @Test
    fun splitTextIntoChapters_preservesChapterHeadingAndBody() {
        val chapters = parser.splitTextIntoChapters(
            "第一章 开始\n主角醒来\n\n第二章 继续\n新的旅程"
        )

        assertEquals(2, chapters.size)
        assertEquals("第一章 开始", chapters[0].first)
        assertTrue(chapters[0].second.contains("主角醒来"))
        assertEquals("第二章 继续", chapters[1].first)
        assertTrue(chapters[1].second.contains("新的旅程"))
    }

    @Test
    fun splitTextIntoChapters_withoutHeadings_usesStableChunks() {
        val content = "a".repeat(5_001)

        val chapters = parser.splitTextIntoChapters(content)

        assertEquals(2, chapters.size)
        assertEquals("第1部分", chapters[0].first)
        assertEquals(5_000, chapters[0].second.length)
        assertEquals("第2部分", chapters[1].first)
        assertEquals(1, chapters[1].second.length)
    }
}
