package com.noveltoon.app.data.parser

import android.util.Log
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.Charset
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

/** Mainland China GitHub proxy candidates, tried in order after the original URL fails */
private val GH_PROXY_PREFIXES = listOf(
    "https://gh-proxy.com/",
    "https://ghfast.top/",
    "https://gitproxy.click/",
    "https://hub.gitmirror.com/"
)

/** Ad / junk patterns that appear in scraped novel content */
private val CONTENT_JUNK_PATTERNS = listOf(
    Regex("天才一秒记住本站地址[：:][^\\n]*"),
    Regex("手机版阅读网址[：:][^\\n]*"),
    Regex("本站网址[：:][^\\n]*"),
    Regex("记住网址[：:][^\\n]*"),
    Regex("最新全本[：:][^\\n]*"),
    Regex("(?i)https?://[a-zA-Z0-9./-]{4,50}"),   // stray URLs
    Regex("[（(][\\d\\s]{1,6}(k|K|万)[字数）)][^\\n]{0,30}"),  // word-count noise
    Regex("\\(\\)\\s*;"),
    Regex("【[^】]{0,20}广告[^】]{0,20}】"),
    Regex("\\[[^\\]]{0,20}广告[^\\]]{0,20}\\]"),
    Regex("☆[^☆\n]{0,40}☆"),
    Regex("------[\\s\\S]{0,60}------"),
    Regex("……{3,}"),
    Regex("。{4,}"),
).also { check(it.isNotEmpty()) }

class SourceParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ─── network helpers ──────────────────────────────────────────────────────

    private fun buildProxyUrls(url: String): List<String> {
        val result = mutableListOf(url)
        val lower = url.lowercase()
        if (lower.contains("github.com/") || lower.contains("raw.githubusercontent.com/") ||
            lower.contains(".github.io/")
        ) {
            GH_PROXY_PREFIXES.forEach { prefix -> result += prefix + url }
        }
        return result
    }

    private suspend fun fetchBytesWithFallback(
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ByteArray = withContext(Dispatchers.IO) {
        val candidates = buildProxyUrls(url)
        var lastEx: Exception? = null
        for (candidate in candidates) {
            try {
                val reqBuilder = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                val response = client.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) {
                    return@withContext response.body?.bytes() ?: byteArrayOf()
                }
            } catch (e: Exception) {
                lastEx = e
                Log.w("SourceParser", "fetch failed for $candidate: ${e.message}")
            }
        }
        throw lastEx ?: Exception("All URLs failed for $url")
    }

    private suspend fun fetchDocument(url: String, encoding: String = ""): Document {
        val bytes = fetchBytesWithFallback(url)
        val html = decodeWithCharset(bytes, encoding, null)
        return Jsoup.parse(html, url)
    }

    private suspend fun fetchString(url: String, encoding: String = ""): String {
        return try {
            val bytes = fetchBytesWithFallback(url)
            decodeWithCharset(bytes, encoding, null)
        } catch (_: Exception) { "" }
    }

    private fun decodeWithCharset(bytes: ByteArray, hint: String, contentType: String?): String {
        val candidates = buildList {
            if (hint.isNotBlank()) add(hint)
            if (contentType != null) {
                val match = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(contentType)
                match?.groupValues?.getOrNull(1)?.let { add(it) }
            }
            val head = String(bytes, 0, minOf(1024, bytes.size), Charsets.ISO_8859_1)
            val metaMatch = Regex("charset=[\"']?([^\"'\\s>/]+)", RegexOption.IGNORE_CASE).find(head)
            metaMatch?.groupValues?.getOrNull(1)?.let { add(it) }
            add("UTF-8")
            add("GBK")
        }
        for (name in candidates) {
            try { return String(bytes, Charset.forName(name.trim())) } catch (_: Exception) {}
        }
        return String(bytes, Charsets.UTF_8)
    }

    // ─── rule engine ──────────────────────────────────────────────────────────

    private fun applyRule(doc: Document, rule: String, baseUrl: String = ""): List<String> {
        if (rule.isBlank()) return emptyList()
        return try {
            val parts = rule.split("@")
            val cssSelector = parts[0]
            val attr = if (parts.size > 1) parts[1] else "text"
            doc.select(cssSelector).map { element ->
                val value = when (attr) {
                    "text" -> element.text()
                    "html" -> element.html()
                    "src"  -> element.absUrl("src").ifEmpty { element.attr("src") }
                    "href" -> element.absUrl("href").ifEmpty { element.attr("href") }
                    else   -> element.attr(attr)
                }
                if (value.startsWith("/") && baseUrl.isNotEmpty()) "${baseUrl.trimEnd('/')}$value"
                else value
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun applySingleRule(doc: Document, rule: String, baseUrl: String = "") =
        applyRule(doc, rule, baseUrl).firstOrNull() ?: ""

    // ─── content cleaner ──────────────────────────────────────────────────────

    fun cleanContent(raw: String): String {
        var text = raw
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n")
            .replace("</p>", "")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")  // strip remaining HTML tags
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

        for (pattern in CONTENT_JUNK_PATTERNS) {
            text = text.replace(pattern, "")
        }

        // Collapse excessive blank lines
        text = text.lines()
            .map { it.trim() }
            .fold(mutableListOf<String>()) { acc, line ->
                if (line.isEmpty() && acc.lastOrNull()?.isEmpty() == true) acc
                else { acc += line; acc }
            }
            .joinToString("\n")

        return text.trim()
    }

    // ─── source validity check ────────────────────────────────────────────────

    suspend fun checkBookSourceValid(source: BookSource): Boolean = withContext(Dispatchers.IO) {
        if (source.searchUrl.isBlank()) return@withContext false
        withTimeoutOrNull(10_000L) {
            try {
                val encoded = URLEncoder.encode("斗罗大陆", source.searchEncoding.ifBlank { "UTF-8" })
                val url = source.searchUrl
                    .replace("{{keyword}}", encoded).replace("{{key}}", encoded)
                    .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/${ it.trimStart('/') }" else it }
                val bytes = fetchBytesWithFallback(url)
                val html = decodeWithCharset(bytes, source.searchEncoding, null)
                html.isNotBlank() && !html.trimStart().lowercase().startsWith("<!doctype html><html><head><title>error")
            } catch (_: Exception) { false }
        } ?: false
    }

    suspend fun checkComicSourceValid(source: ComicSource): Boolean = withContext(Dispatchers.IO) {
        if (source.searchUrl.isBlank()) return@withContext false
        withTimeoutOrNull(10_000L) {
            try {
                val encoded = URLEncoder.encode("斗罗大陆", source.searchEncoding.ifBlank { "UTF-8" })
                val url = source.searchUrl
                    .replace("{{keyword}}", encoded).replace("{{key}}", encoded)
                    .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/${ it.trimStart('/') }" else it }
                val bytes = fetchBytesWithFallback(url)
                val html = decodeWithCharset(bytes, source.searchEncoding, null)
                html.isNotBlank()
            } catch (_: Exception) { false }
        } ?: false
    }

    // ─── search (concurrent, per-source timeout) ──────────────────────────────

    /** Filter: keep results whose title contains the full keyword (case-insensitive) */
    private fun matchesKeyword(title: String, keyword: String): Boolean {
        if (keyword.isBlank()) return true
        return title.contains(keyword.trim(), ignoreCase = true)
    }

    /**
     * Concurrent search across all sources.
     * Emits batches via [onBatch] as each source returns, so UI can show results immediately.
     * Full results are also returned at the end.
     */
    suspend fun searchNovelAllSources(
        sources: List<BookSource>,
        keyword: String,
        onBatch: (suspend (List<SearchResult>) -> Unit)? = null
    ): List<SearchResult> = coroutineScope {
        val allResults = mutableListOf<SearchResult>()
        // chunk into batches of 10 to avoid hammering network all at once
        sources.chunked(10).forEach { chunk ->
            chunk.map { source ->
                async {
                    withTimeoutOrNull(8_000L) {
                        searchNovel(source, keyword)
                            .filter { matchesKeyword(it.title, keyword) }
                    } ?: emptyList()
                }
            }.awaitAll().forEach { batch ->
                if (batch.isNotEmpty()) {
                    allResults.addAll(batch)
                    onBatch?.invoke(allResults.toList())
                }
            }
        }
        allResults
    }

    suspend fun searchComicAllSources(
        sources: List<ComicSource>,
        keyword: String,
        onBatch: (suspend (List<SearchResult>) -> Unit)? = null
    ): List<SearchResult> = coroutineScope {
        val allResults = mutableListOf<SearchResult>()
        sources.chunked(10).forEach { chunk ->
            chunk.map { source ->
                async {
                    withTimeoutOrNull(8_000L) {
                        searchComic(source, keyword)
                            .filter { matchesKeyword(it.title, keyword) }
                    } ?: emptyList()
                }
            }.awaitAll().forEach { batch ->
                if (batch.isNotEmpty()) {
                    allResults.addAll(batch)
                    onBatch?.invoke(allResults.toList())
                }
            }
        }
        allResults
    }

    suspend fun searchNovel(source: BookSource, keyword: String): List<SearchResult> {
        return try {
            val enc = source.searchEncoding.ifBlank { "UTF-8" }
            val encodedKeyword = URLEncoder.encode(keyword, enc)
            val searchUrl = source.searchUrl
                .replace("{{keyword}}", encodedKeyword)
                .replace("{{key}}", encodedKeyword)
                .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/${it.trimStart('/')}" else it }
            val doc = fetchDocument(searchUrl, enc)
            if (source.searchListRule.isBlank()) return emptyList()
            doc.select(source.searchListRule).mapNotNull { element ->
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
                } catch (_: Exception) { null }
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getNovelChapters(source: BookSource, bookUrl: String): List<ChapterInfo> {
        return try {
            val url = if (bookUrl.startsWith("http")) bookUrl
            else source.baseUrl.trimEnd('/') + "/${bookUrl.trimStart('/')}"
            val doc = fetchDocument(url, source.searchEncoding.ifBlank { "UTF-8" })
            if (source.chapterListRule.isBlank()) return emptyList()
            doc.select(source.chapterListRule).map { element ->
                val itemDoc = Jsoup.parse(element.outerHtml(), url)
                ChapterInfo(
                    title = applySingleRule(itemDoc, source.chapterNameRule),
                    url = applySingleRule(itemDoc, source.chapterUrlRule, source.baseUrl)
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getNovelContent(source: BookSource, chapterUrl: String): String {
        return try {
            val url = if (chapterUrl.startsWith("http")) chapterUrl
            else source.baseUrl.trimEnd('/') + "/${chapterUrl.trimStart('/')}"
            val enc = source.searchEncoding.ifBlank { "UTF-8" }
            val doc = fetchDocument(url, enc)
            var raw = applySingleRule(doc, source.contentRule)

            if (source.contentNextPageRule.isNotBlank()) {
                var nextUrl = applySingleRule(doc, source.contentNextPageRule, source.baseUrl)
                var pages = 0
                while (nextUrl.isNotBlank() && pages < 10) {
                    val nextDoc = fetchDocument(nextUrl, enc)
                    raw += "\n" + applySingleRule(nextDoc, source.contentRule)
                    nextUrl = applySingleRule(nextDoc, source.contentNextPageRule, source.baseUrl)
                    pages++
                }
            }
            cleanContent(raw)
        } catch (_: Exception) { "" }
    }

    suspend fun searchComic(source: ComicSource, keyword: String): List<SearchResult> {
        return try {
            val enc = source.searchEncoding.ifBlank { "UTF-8" }
            val encodedKeyword = URLEncoder.encode(keyword, enc)
            val searchUrl = source.searchUrl
                .replace("{{keyword}}", encodedKeyword)
                .replace("{{key}}", encodedKeyword)
                .let { if (!it.startsWith("http")) source.baseUrl.trimEnd('/') + "/${it.trimStart('/')}" else it }
            val doc = fetchDocument(searchUrl, enc)
            if (source.searchListRule.isBlank()) return emptyList()
            doc.select(source.searchListRule).mapNotNull { element ->
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
                } catch (_: Exception) { null }
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getComicChapters(source: ComicSource, comicUrl: String): List<ChapterInfo> {
        return try {
            val url = if (comicUrl.startsWith("http")) comicUrl
            else source.baseUrl.trimEnd('/') + "/${comicUrl.trimStart('/')}"
            val doc = fetchDocument(url)
            if (source.chapterListRule.isBlank()) return emptyList()
            doc.select(source.chapterListRule).map { element ->
                val itemDoc = Jsoup.parse(element.outerHtml(), url)
                ChapterInfo(
                    title = applySingleRule(itemDoc, source.chapterNameRule),
                    url = applySingleRule(itemDoc, source.chapterUrlRule, source.baseUrl)
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getComicImages(source: ComicSource, chapterUrl: String): List<String> {
        return try {
            val url = if (chapterUrl.startsWith("http")) chapterUrl
            else source.baseUrl.trimEnd('/') + "/${chapterUrl.trimStart('/')}"
            val doc = fetchDocument(url)
            applyRule(doc, source.imageListRule, source.baseUrl).filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchTextFromUrl(url: String): String {
        val lower = url.lowercase()
        return if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")) {
            fetchString(url)
        } else {
            val doc = fetchDocument(url)
            val body = doc.body()
            body.select("script, style, nav, footer, header").remove()
            body.text()
        }
    }

    suspend fun fetchImagesFromUrl(url: String): List<String> {
        return try {
            val doc = fetchDocument(url)
            doc.select("img").mapNotNull { el ->
                val src = el.absUrl("src").ifEmpty { el.absUrl("data-src") }.ifEmpty { el.attr("src") }
                src.ifBlank { null }
            }.distinct()
        } catch (_: Exception) { emptyList() }
    }

    fun guessTitleFromUrl(url: String): String {
        return try {
            val path = url.substringAfterLast('/').substringBefore('?')
            if (path.isBlank()) url.substringAfter("://").substringBefore('/') else path
        } catch (_: Exception) { "URL Import" }
    }

    fun splitTextIntoChapters(content: String): List<Pair<String, String>> {
        val chapterPattern = Regex(
            "^\\s*(第[零一二三四五六七八九十百千万\\d]+[章节回卷]|Chapter\\s+\\d+|CHAPTER\\s+\\d+).*",
            RegexOption.MULTILINE
        )
        val matches = chapterPattern.findAll(content).toList()
        if (matches.isEmpty()) {
            return content.chunked(5000).mapIndexed { i, chunk -> "第${i + 1}部分" to chunk }
        }
        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            match.value.trim() to content.substring(start, end).trim()
        }
    }
}
