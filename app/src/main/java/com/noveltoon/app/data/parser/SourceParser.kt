package com.noveltoon.app.data.parser

import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val author: String,
    val coverUrl: String,
    val url: String,
    val latestChapter: String = "",
    val status: String = "",
    val sourceName: String = ""
)

data class ChapterInfo(
    val title: String,
    val url: String
)

class SourceParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private suspend fun fetchDocument(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        val response = client.newCall(request).execute()
        Jsoup.parse(response.body?.string() ?: "", url)
    }

    private suspend fun fetchString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        val response = client.newCall(request).execute()
        response.body?.string() ?: ""
    }

    private fun applyRule(doc: Document, rule: String, baseUrl: String = ""): List<String> {
        if (rule.isBlank()) return emptyList()
        return try {
            val parts = rule.split("@")
            val cssSelector = parts[0]
            val attr = if (parts.size > 1) parts[1] else "text"
            val elements = doc.select(cssSelector)
            elements.map { element ->
                val value = when (attr) {
                    "text" -> element.text()
                    "html" -> element.html()
                    "src" -> element.absUrl("src").ifEmpty { element.attr("src") }
                    "href" -> element.absUrl("href").ifEmpty { element.attr("href") }
                    else -> element.attr(attr).let { v ->
                        if (attr == "abs:href" || attr == "abs:src") element.absUrl(attr.removePrefix("abs:"))
                        else v
                    }
                }
                if (value.startsWith("/") && baseUrl.isNotEmpty()) {
                    val base = baseUrl.trimEnd('/')
                    "$base$value"
                } else value
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun applySingleRule(doc: Document, rule: String, baseUrl: String = ""): String {
        return applyRule(doc, rule, baseUrl).firstOrNull() ?: ""
    }

    suspend fun searchNovel(source: BookSource, keyword: String): List<SearchResult> {
        return try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val searchUrl = source.searchUrl
                .replace("{{keyword}}", encodedKeyword)
                .replace("{{key}}", encodedKeyword)
                .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/" + it.trimStart('/') else it }

            val doc = fetchDocument(searchUrl)
            val listElements = if (source.searchListRule.isNotBlank()) {
                doc.select(source.searchListRule)
            } else {
                return emptyList()
            }

            listElements.mapNotNull { element ->
                try {
                    val itemDoc = Jsoup.parse(element.outerHtml(), searchUrl)
                    SearchResult(
                        title = applySingleRule(itemDoc, source.searchNameRule),
                        author = applySingleRule(itemDoc, source.searchAuthorRule),
                        coverUrl = applySingleRule(itemDoc, source.searchCoverRule, source.baseUrl),
                        url = applySingleRule(itemDoc, source.searchUrlRule, source.baseUrl),
                        latestChapter = applySingleRule(itemDoc, source.searchLatestChapterRule),
                        sourceName = source.name
                    )
                } catch (e: Exception) {
                    null
                }
            }.filter { it.title.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNovelChapters(source: BookSource, bookUrl: String): List<ChapterInfo> {
        return try {
            val url = if (bookUrl.startsWith("http")) bookUrl
            else source.baseUrl.trimEnd('/') + "/" + bookUrl.trimStart('/')
            val doc = fetchDocument(url)
            val elements = if (source.chapterListRule.isNotBlank()) {
                doc.select(source.chapterListRule)
            } else return emptyList()

            elements.mapIndexed { _, element ->
                val itemDoc = Jsoup.parse(element.outerHtml(), url)
                ChapterInfo(
                    title = applySingleRule(itemDoc, source.chapterNameRule),
                    url = applySingleRule(itemDoc, source.chapterUrlRule, source.baseUrl)
                )
            }.filter { it.title.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNovelContent(source: BookSource, chapterUrl: String): String {
        return try {
            val url = if (chapterUrl.startsWith("http")) chapterUrl
            else source.baseUrl.trimEnd('/') + "/" + chapterUrl.trimStart('/')
            val doc = fetchDocument(url)
            var content = applySingleRule(doc, source.contentRule)

            if (source.contentNextPageRule.isNotBlank()) {
                var nextUrl = applySingleRule(doc, source.contentNextPageRule, source.baseUrl)
                var pages = 0
                while (nextUrl.isNotBlank() && pages < 10) {
                    val nextDoc = fetchDocument(nextUrl)
                    content += "\n" + applySingleRule(nextDoc, source.contentRule)
                    nextUrl = applySingleRule(nextDoc, source.contentNextPageRule, source.baseUrl)
                    pages++
                }
            }

            content.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<p>", "\n")
                .replace("</p>", "")
                .replace("&nbsp;", " ")
                .trim()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun searchComic(source: ComicSource, keyword: String): List<SearchResult> {
        return try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val searchUrl = source.searchUrl
                .replace("{{keyword}}", encodedKeyword)
                .replace("{{key}}", encodedKeyword)
                .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/" + it.trimStart('/') else it }

            val doc = fetchDocument(searchUrl)
            val listElements = if (source.searchListRule.isNotBlank()) {
                doc.select(source.searchListRule)
            } else {
                return emptyList()
            }

            listElements.mapNotNull { element ->
                try {
                    val itemDoc = Jsoup.parse(element.outerHtml(), searchUrl)
                    SearchResult(
                        title = applySingleRule(itemDoc, source.searchNameRule),
                        author = applySingleRule(itemDoc, source.searchAuthorRule),
                        coverUrl = applySingleRule(itemDoc, source.searchCoverRule, source.baseUrl),
                        url = applySingleRule(itemDoc, source.searchUrlRule, source.baseUrl),
                        status = applySingleRule(itemDoc, source.searchStatusRule),
                        sourceName = source.name
                    )
                } catch (e: Exception) {
                    null
                }
            }.filter { it.title.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getComicChapters(source: ComicSource, comicUrl: String): List<ChapterInfo> {
        return try {
            val url = if (comicUrl.startsWith("http")) comicUrl
            else source.baseUrl.trimEnd('/') + "/" + comicUrl.trimStart('/')
            val doc = fetchDocument(url)
            val elements = if (source.chapterListRule.isNotBlank()) {
                doc.select(source.chapterListRule)
            } else return emptyList()

            elements.map { element ->
                val itemDoc = Jsoup.parse(element.outerHtml(), url)
                ChapterInfo(
                    title = applySingleRule(itemDoc, source.chapterNameRule),
                    url = applySingleRule(itemDoc, source.chapterUrlRule, source.baseUrl)
                )
            }.filter { it.title.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getComicImages(source: ComicSource, chapterUrl: String): List<String> {
        return try {
            val url = if (chapterUrl.startsWith("http")) chapterUrl
            else source.baseUrl.trimEnd('/') + "/" + chapterUrl.trimStart('/')
            val doc = fetchDocument(url)
            applyRule(doc, source.imageListRule, source.baseUrl)
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
