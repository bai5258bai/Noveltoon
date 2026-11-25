package com.noveltoon.app.data.parser

import com.noveltoon.app.data.entity.BookSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URLEncoder

class NovelSourcePipelineTest {
    private val keyword = "聊齋"
    private val bookUrl = "https://zh.wikisource.org/wiki/Book"
    private val chapter1 = "https://fixture.example/book/v1"
    private val chapter2 = "https://fixture.example/book/v2"

    @Test
    fun fixture_searchAddCatalogReadAndSwitchChapter() = runBlocking {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchUrl =
            "https://zh.wikisource.org/w/index.php?title=Special:Search&fulltext=1&ns0=1&search=$encoded"
        val parser = SourceParser(MapPageSource(mapOf(
            searchUrl to fixture("search.html"),
            bookUrl to fixture("book.html"),
            chapter1 to fixture("v1.html"),
            chapter2 to fixture("v2.html")
        )))
        val source = wikisource()

        val found = parser.searchNovel(source, keyword)
        assertEquals(NovelAccessKind.SUCCESS, found.kind)
        assertEquals(1, found.value.size)
        assertEquals("聊齋志異", found.value[0].title)
        assertTrue(found.value[0].url.endsWith("/wiki/Book"))

        val chapters = parser.getNovelChapters(source, found.value[0].url)
        assertEquals(NovelAccessKind.SUCCESS, chapters.kind)
        assertEquals(listOf("第一卷", "第二卷"), chapters.value.map { it.title })
        assertEquals(chapter1, chapters.value[0].url)
        assertEquals(chapter2, chapters.value[1].url)

        val first = parser.getNovelContent(source, chapters.value[0].url)
        val second = parser.getNovelContent(source, chapters.value[1].url)
        assertEquals(NovelAccessKind.SUCCESS, first.kind)
        assertEquals(NovelAccessKind.SUCCESS, second.kind)
        assertTrue(first.value.contains("予姊夫之祖"))
        assertTrue(second.value.contains("第二卷正文"))
        assertNotEquals(first.value, second.value)
    }

    @Test
    fun fixture_distinguishesNoResultRuleErrorAndNetworkFailure() = runBlocking {
        val encoded = URLEncoder.encode("没有", "UTF-8")
        val searchUrl =
            "https://zh.wikisource.org/w/index.php?title=Special:Search&fulltext=1&ns0=1&search=$encoded"
        val empty = SourceParser(MapPageSource(mapOf(searchUrl to "<ul></ul>")))
        val noResult = empty.searchNovel(wikisource(), "没有")
        assertEquals(NovelAccessKind.NO_RESULT, noResult.kind)

        val badRule = wikisource().copy(searchListRule = "div..list")
        val rule = empty.searchNovel(badRule, "没有")
        assertEquals(NovelAccessKind.RULE_ERROR, rule.kind)

        val offline = SourceParser(object : PageSource {
            override suspend fun load(url: String): LoadedPage {
                throw java.io.IOException("offline")
            }
        })
        val network = offline.searchNovel(wikisource(), "没有")
        assertEquals(NovelAccessKind.NETWORK_ERROR, network.kind)

        val none = empty.searchNovelAllSources(emptyList(), "没有")
        assertEquals(NovelAccessKind.NO_SOURCE, none.kind)
    }

    @Test
    fun onlineWikisource_searchCatalogContentAndNextChapter() = runBlocking {
        assumeTrue("Set NOVELTOON_ONLINE=1 to run the live Wikisource check", System.getenv("NOVELTOON_ONLINE") == "1")
        val parser = SourceParser()
        val asset = listOf(
            java.io.File("src/main/assets/default_book_sources.json"),
            java.io.File("app/src/main/assets/default_book_sources.json")
        ).first { it.exists() }
        val sources = com.noveltoon.app.data.SourceJson.parseBookSources(asset.readText())
        val source = sources.first { it.name == "维基文库" }

        val found = parser.searchNovel(source, keyword)
        assertEquals(found.detail, NovelAccessKind.SUCCESS, found.kind)
        val book = found.value.firstOrNull { it.title == "聊齋志異" }
        assertTrue("titles=${found.value.map { it.title }}", book != null)

        val chapters = parser.getNovelChapters(source, book!!.url)
        assertEquals(chapters.detail, NovelAccessKind.SUCCESS, chapters.kind)
        assertTrue(chapters.value.size >= 2)
        assertTrue(chapters.value[0].title.contains("卷"))
        assertTrue(chapters.value[1].url.startsWith("https://"))
        assertNotEquals(chapters.value[0].url, chapters.value[1].url)

        val first = parser.getNovelContent(source, chapters.value[0].url)
        val second = parser.getNovelContent(source, chapters.value[1].url)
        assertEquals(first.detail, NovelAccessKind.SUCCESS, first.kind)
        assertEquals(second.detail, NovelAccessKind.SUCCESS, second.kind)
        assertTrue(first.value.length > 80)
        assertTrue(second.value.length > 80)
        assertNotEquals(first.value, second.value)
    }

    private fun wikisource(): BookSource {
        return BookSource(
            name = "维基文库",
            baseUrl = "https://zh.wikisource.org",
            searchUrl = "https://zh.wikisource.org/w/index.php?title=Special:Search&fulltext=1&ns0=1&search={{keyword}}",
            searchListRule = "li.mw-search-result",
            searchNameRule = "div.mw-search-result-heading a@text",
            searchUrlRule = "div.mw-search-result-heading a@href",
            chapterListRule = "table.article-table a:not([href*='#'])",
            chapterNameRule = "@text",
            chapterUrlRule = "@href",
            contentRule = "div.mw-parser-output@html"
        )
    }

    private fun fixture(name: String): String {
        val resource = javaClass.classLoader!!.getResource("fixtures/novel/$name")
            ?: error("missing fixture $name")
        return resource.readText()
    }
}

private class MapPageSource(private val pages: Map<String, String>) : PageSource {
    override suspend fun load(url: String): LoadedPage {
        val body = pages[url] ?: throw java.io.IOException("missing $url")
        return LoadedPage(finalUrl = url, body = body)
    }
}
