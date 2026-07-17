package com.noveltoon.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import com.noveltoon.app.data.parser.SourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class SourceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val bookSourceDao = db.bookSourceDao()
    private val comicSourceDao = db.comicSourceDao()
    private val gson = Gson()
    private val parser = SourceParser()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ImportResult(
        val addedCount: Int,
        val errorMessage: String? = null,
        val updatedCount: Int = 0
    ) {
        val totalCount: Int get() = addedCount + updatedCount
    }

    fun getAllBookSources(): Flow<List<BookSource>> = bookSourceDao.getAllSources()
    fun getAllComicSources(): Flow<List<ComicSource>> = comicSourceDao.getAllSources()

    suspend fun addBookSource(source: BookSource): Long = bookSourceDao.insert(source)
    suspend fun addComicSource(source: ComicSource): Long = comicSourceDao.insert(source)

    suspend fun updateBookSource(source: BookSource) = bookSourceDao.update(source)
    suspend fun updateComicSource(source: ComicSource) = comicSourceDao.update(source)

    suspend fun deleteBookSource(source: BookSource) = bookSourceDao.delete(source)
    suspend fun deleteComicSource(source: ComicSource) = comicSourceDao.delete(source)

    /** Returns true if source search URL responds with non-empty HTML */
    suspend fun checkBookSourceValid(source: BookSource): Boolean = parser.checkBookSourceValid(source)

    /** Returns true if comic source search URL responds with non-empty HTML */
    suspend fun checkComicSourceValid(source: ComicSource): Boolean = parser.checkComicSourceValid(source)

    private suspend fun fetchText(url: String): Pair<String, String?> = withContext(Dispatchers.IO) {
        val fallbackUrls = buildFallbackUrls(url)
        var lastReason: String? = null
        for (candidate in fallbackUrls) {
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
                client.newCall(request).execute().use { response ->
                    var shouldUseNext = false
                    if (!response.isSuccessful) {
                        lastReason = "HTTP ${response.code}"
                        shouldUseNext = true
                    }
                    if (!shouldUseNext) {
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) {
                            lastReason = "empty response"
                            shouldUseNext = true
                        } else if (looksLikeHtml(body)) {
                            lastReason = "返回的是网页而不是 JSON（可能 404 或被拦截）"
                            shouldUseNext = true
                        } else {
                            return@withContext body to null
                        }
                    }
                }
            } catch (_: Exception) {
                lastReason = "network exception"
            }
        }
        "" to (lastReason ?: "network failed")
    }

    /**
     * Mainstream-style import entry (Legado-like):
     * - Network URL returning JSON
     * - Raw JSON / JSON array / NDJSON paste
     * - Base64 / gzip+base64 payloads
     * - legado:// scheme URLs
     * - Local file content (via [importBookSourcesFromUri])
     */
    suspend fun importBookSourcesFromText(input: String): ImportResult {
        val resolved = resolveImportPayload(input.trim())
        if (resolved.isFailure) {
            return ImportResult(0, resolved.exceptionOrNull()?.message ?: "输入为空")
        }
        val payload = resolved.getOrNull().orEmpty()
        if (payload.isBlank()) return ImportResult(0, "输入为空")
        return importBookSources(payload)
    }

    suspend fun importComicSourcesFromText(input: String): ImportResult {
        val resolved = resolveImportPayload(input.trim())
        if (resolved.isFailure) {
            return ImportResult(0, resolved.exceptionOrNull()?.message ?: "输入为空")
        }
        val payload = resolved.getOrNull().orEmpty()
        if (payload.isBlank()) return ImportResult(0, "输入为空")
        return importComicSources(payload)
    }

    suspend fun importBookSourcesFromUri(uri: Uri): ImportResult {
        val text = readUriText(uri) ?: return ImportResult(0, "无法读取文件")
        return importBookSourcesFromText(text)
    }

    suspend fun importComicSourcesFromUri(uri: Uri): ImportResult {
        val text = readUriText(uri) ?: return ImportResult(0, "无法读取文件")
        return importComicSourcesFromText(text)
    }

    private suspend fun readUriText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveImportPayload(raw: String): Result<String> {
        if (raw.isBlank()) return Result.failure(IllegalArgumentException("输入为空"))
        var text = raw.removePrefix("\uFEFF").trim()

        // legado://import/http... or legado://booksource/...
        if (text.startsWith("legado://", ignoreCase = true)) {
            val after = text.substringAfter("://")
            val embeddedUrl = Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(after)?.value
            text = embeddedUrl ?: after.substringAfterLast('/').ifBlank { after }
        }

        if (text.startsWith("http://") || text.startsWith("https://")) {
            val (body, err) = fetchText(text)
            return if (body.isBlank()) Result.failure(IllegalArgumentException(err ?: "network failed"))
            else Result.success(decodeIfNeeded(body))
        }

        return Result.success(decodeIfNeeded(text))
    }

    /** Decode base64 / gzip payloads commonly shared for source packs. */
    private fun decodeIfNeeded(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<")) {
            return trimmed
        }
        // data URI
        val dataUri = Regex("""^data:(?:application|text)/[^;]*;base64,(.+)$""", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val candidate = dataUri?.groupValues?.getOrNull(1)?.replace("\\s".toRegex(), "")
            ?: trimmed.replace("\\s".toRegex(), "")

        if (candidate.length < 16 || candidate.any { !isBase64Char(it) }) {
            return trimmed
        }
        return try {
            val decoded = Base64.decode(candidate, Base64.DEFAULT)
            val bytes = if (decoded.size >= 2 && decoded[0] == 0x1f.toByte() && decoded[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(decoded)).use { it.readBytes() }
            } else {
                decoded
            }
            val asText = String(bytes, Charsets.UTF_8).trim().removePrefix("\uFEFF")
            if (asText.startsWith("{") || asText.startsWith("[")) asText else trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun isBase64Char(c: Char): Boolean =
        c.isLetterOrDigit() || c == '+' || c == '/' || c == '=' || c == '-' || c == '_'

    suspend fun importBookSources(json: String): ImportResult {
        return try {
            val list = parseSourceObjects(json).mapNotNull { parseBookSource(it) }
            if (list.isEmpty()) ImportResult(0, "未识别到可用书源字段（可尝试阅读3.0 / CSS 规则 JSON）")
            else upsertBookSources(list)
        } catch (e: Exception) {
            ImportResult(0, "JSON 解析失败：${e.message ?: "unknown"}")
        }
    }

    suspend fun importComicSources(json: String): ImportResult {
        return try {
            val list = parseSourceObjects(json).mapNotNull { parseComicSource(it) }
            if (list.isEmpty()) ImportResult(0, "未识别到可用图源字段（可尝试漫画规则 JSON）")
            else upsertComicSources(list)
        } catch (e: Exception) {
            ImportResult(0, "JSON 解析失败：${e.message ?: "unknown"}")
        }
    }

    private suspend fun upsertBookSources(list: List<BookSource>): ImportResult {
        var added = 0
        var updated = 0
        for (src in list) {
            val existing = bookSourceDao.findByNameAndBaseUrl(src.name, src.baseUrl)
                ?: bookSourceDao.findByName(src.name)?.takeIf {
                    it.baseUrl.trimEnd('/') == src.baseUrl.trimEnd('/') || src.baseUrl.isBlank() || it.baseUrl.isBlank()
                }
            if (existing != null) {
                bookSourceDao.update(
                    src.copy(
                        id = existing.id,
                        isBuiltIn = existing.isBuiltIn,
                        enabled = existing.enabled,
                        sortOrder = existing.sortOrder
                    )
                )
                updated++
            } else {
                bookSourceDao.insert(src.copy(id = 0, isBuiltIn = false))
                added++
            }
        }
        return ImportResult(addedCount = added, updatedCount = updated)
    }

    private suspend fun upsertComicSources(list: List<ComicSource>): ImportResult {
        var added = 0
        var updated = 0
        for (src in list) {
            val existing = comicSourceDao.findByNameAndBaseUrl(src.name, src.baseUrl)
                ?: comicSourceDao.findByName(src.name)?.takeIf {
                    it.baseUrl.trimEnd('/') == src.baseUrl.trimEnd('/') || src.baseUrl.isBlank() || it.baseUrl.isBlank()
                }
            if (existing != null) {
                comicSourceDao.update(
                    src.copy(
                        id = existing.id,
                        isBuiltIn = existing.isBuiltIn,
                        enabled = existing.enabled,
                        sortOrder = existing.sortOrder
                    )
                )
                updated++
            } else {
                comicSourceDao.insert(src.copy(id = 0, isBuiltIn = false))
                added++
            }
        }
        return ImportResult(addedCount = added, updatedCount = updated)
    }

    private fun parseBookSource(o: JsonObject): BookSource? {
        return try {
            val ruleSearch = o.obj("ruleSearch")
            val ruleBookInfo = o.obj("ruleBookInfo")
            val ruleToc = o.obj("ruleToc")
            val ruleContent = o.obj("ruleContent")
            BookSource(
                name = o.str("name") ?: o.str("bookSourceName") ?: return null,
                baseUrl = normalizeBaseUrl(o.str("baseUrl") ?: o.str("bookSourceUrl") ?: ""),
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = normalizeSearchUrl(o.str("searchUrl") ?: ""),
                searchListRule = sanitizeRule(o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: ""),
                searchNameRule = sanitizeRule(o.str("searchNameRule") ?: ruleSearch?.str("name") ?: ""),
                searchAuthorRule = sanitizeRule(o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: ""),
                searchCoverRule = sanitizeRule(o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: ""),
                searchUrlRule = sanitizeRule(o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: ""),
                searchLatestChapterRule = sanitizeRule(
                    o.str("searchLatestChapterRule") ?: ruleSearch?.str("lastChapter") ?: ""
                ),
                detailNameRule = sanitizeRule(o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: ""),
                detailAuthorRule = sanitizeRule(o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: ""),
                detailCoverRule = sanitizeRule(o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: ""),
                detailDescRule = sanitizeRule(o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: ""),
                chapterListRule = sanitizeRule(o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: ""),
                chapterNameRule = sanitizeRule(o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: ""),
                chapterUrlRule = sanitizeRule(o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: ""),
                contentRule = sanitizeRule(o.str("contentRule") ?: ruleContent?.str("content") ?: ""),
                contentNextPageRule = sanitizeRule(
                    o.str("contentNextPageRule") ?: ruleContent?.str("nextContentUrl") ?: ""
                ),
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseComicSource(o: JsonObject): ComicSource? {
        return try {
            val ruleSearch = o.obj("ruleSearch")
            val ruleBookInfo = o.obj("ruleBookInfo")
            val ruleToc = o.obj("ruleToc")
            val ruleContent = o.obj("ruleContent")
            ComicSource(
                name = o.str("name") ?: o.str("bookSourceName") ?: o.str("sourceName") ?: return null,
                baseUrl = normalizeBaseUrl(o.str("baseUrl") ?: o.str("bookSourceUrl") ?: o.str("sourceUrl") ?: ""),
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = normalizeSearchUrl(o.str("searchUrl") ?: ""),
                searchListRule = sanitizeRule(o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: ""),
                searchNameRule = sanitizeRule(o.str("searchNameRule") ?: ruleSearch?.str("name") ?: ""),
                searchAuthorRule = sanitizeRule(o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: ""),
                searchCoverRule = sanitizeRule(o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: ""),
                searchUrlRule = sanitizeRule(o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: ""),
                searchStatusRule = sanitizeRule(o.str("searchStatusRule") ?: ruleSearch?.str("kind") ?: ""),
                detailNameRule = sanitizeRule(o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: ""),
                detailAuthorRule = sanitizeRule(o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: ""),
                detailCoverRule = sanitizeRule(o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: ""),
                detailDescRule = sanitizeRule(o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: ""),
                chapterListRule = sanitizeRule(o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: ""),
                chapterNameRule = sanitizeRule(o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: ""),
                chapterUrlRule = sanitizeRule(o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: ""),
                imageListRule = sanitizeRule(
                    o.str("imageListRule") ?: ruleContent?.str("imageList") ?: ruleContent?.str("content") ?: ""
                ),
                imageUrlRule = sanitizeRule(o.str("imageUrlRule") ?: ruleContent?.str("imageUrl") ?: ""),
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = false
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Strip Legado POST/options suffix: url,{"method":"POST",...} */
    private fun normalizeSearchUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val optionIdx = trimmed.indexOf(",{")
        val urlPart = if (optionIdx > 0) trimmed.substring(0, optionIdx).trim() else trimmed
        return urlPart
            .replace("{{key}}", "{{keyword}}")
            .replace("{{searchKey}}", "{{keyword}}")
    }

    private fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    /**
     * Drop unsupported JS rules; map Legado tag./class./id. CSS sugar.
     * Keep ##replace chains for the parser.
     */
    private fun sanitizeRule(raw: String): String {
        val rule = raw.trim()
        if (rule.isEmpty()) return ""
        if (rule.contains("@js", ignoreCase = true) || rule.contains("<js>", ignoreCase = true)) {
            return ""
        }
        // Only transform the selector portion before first @ or ##
        val splitAt = rule.indexOf('@').let { if (it < 0) Int.MAX_VALUE else it }
        val splitHash = rule.indexOf("##").let { if (it < 0) Int.MAX_VALUE else it }
        val cut = minOf(splitAt, splitHash)
        if (cut == Int.MAX_VALUE) {
            return normalizeLegadoCss(rule)
        }
        val selector = normalizeLegadoCss(rule.substring(0, cut))
        return selector + rule.substring(cut)
    }

    private fun normalizeLegadoCss(selector: String): String {
        return selector
            .replace(Regex("""\btag\."""), "")
            .replace(Regex("""\bclass\."""), ".")
            .replace(Regex("""\bid\."""), "#")
            .trim()
    }

    private fun JsonObject.str(key: String): String? {
        return try {
            val el = this.get(key)
            if (el == null || el.isJsonNull) null
            else if (el.isJsonPrimitive) el.asString
            else el.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        val el = this.get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private fun parseSourceObjects(text: String): List<JsonObject> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        try {
            val element = JsonParser.parseString(trimmed)
            val fromJson = extractSourceObjects(element)
            if (fromJson.isNotEmpty()) return fromJson
        } catch (_: Exception) {
            // fall through to NDJSON / multi-object recovery
        }
        // NDJSON: one JSON object per line
        val ndjson = trimmed.lines().mapNotNull { line ->
            val l = line.trim()
            if (l.isEmpty() || !(l.startsWith("{") || l.startsWith("["))) return@mapNotNull null
            try {
                val el = JsonParser.parseString(l)
                when {
                    el.isJsonObject -> listOf(el.asJsonObject)
                    el.isJsonArray -> el.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                    else -> emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }.flatten()
        if (ndjson.isNotEmpty()) return ndjson

        // Recover concatenated objects: {...}{...}
        val recovered = Regex("""\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""")
            .findAll(trimmed)
            .mapNotNull { match ->
                try {
                    val el = JsonParser.parseString(match.value)
                    if (el.isJsonObject) el.asJsonObject else null
                } catch (_: Exception) {
                    null
                }
            }.toList()
        return recovered
    }

    private fun extractSourceObjects(element: JsonElement): List<JsonObject> {
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
        }
        if (!element.isJsonObject) return emptyList()
        val obj = element.asJsonObject
        val wrappedKeys = listOf(
            "sources", "bookSources", "comicSources", "data", "list", "items",
            "sourceList", "result", "payload", "bookSource", "comicSource"
        )
        wrappedKeys.forEach { key ->
            val el = obj.get(key) ?: return@forEach
            when {
                el.isJsonArray -> {
                    val list = el.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                    if (list.isNotEmpty()) return list
                }
                el.isJsonObject -> return listOf(el.asJsonObject)
            }
        }
        // Single source object
        return listOf(obj)
    }

    private fun looksLikeHtml(text: String): Boolean {
        val t = text.trimStart().lowercase()
        return t.startsWith("<!doctype html") || t.startsWith("<html")
    }

    private fun buildFallbackUrls(url: String): List<String> {
        val result = linkedSetOf(url)
        val trimmed = url.trim()
        if (trimmed.contains("raw.githubusercontent.com/")) {
            val afterHost = trimmed.substringAfter("raw.githubusercontent.com/", "")
            val seg = afterHost.split("/").filter { it.isNotBlank() }
            if (seg.size >= 4) {
                val owner = seg[0]
                val repo = seg[1]
                val branch = seg[2]
                val path = seg.drop(3).joinToString("/")
                result += "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
                result += "https://gcore.jsdelivr.net/gh/$owner/$repo@$branch/$path"
                result += "https://gh-proxy.com/$trimmed"
                result += "https://ghfast.top/$trimmed"
            }
        }
        if (trimmed.contains("github.com/") && trimmed.contains("/blob/")) {
            val after = trimmed.substringAfter("github.com/", "")
            val seg = after.split("/").filter { it.isNotBlank() }
            if (seg.size >= 5 && seg[2] == "blob") {
                val owner = seg[0]
                val repo = seg[1]
                val branch = seg[3]
                val path = seg.drop(4).joinToString("/")
                result += "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
                result += "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
                result += "https://gcore.jsdelivr.net/gh/$owner/$repo@$branch/$path"
            }
        }
        if (trimmed.contains(".github.io/")) {
            val host = trimmed.substringAfter("://").substringBefore("/")
            val owner = host.substringBefore(".github.io")
            val path = trimmed.substringAfter(host).removePrefix("/")
            if (owner.isNotBlank() && path.isNotBlank()) {
                result += "https://cdn.jsdelivr.net/gh/$owner/$owner.github.io@main/$path"
                result += "https://cdn.jsdelivr.net/gh/$owner/$owner.github.io@master/$path"
                result += "https://gcore.jsdelivr.net/gh/$owner/$owner.github.io@main/$path"
                result += "https://gcore.jsdelivr.net/gh/$owner/$owner.github.io@master/$path"
            }
        }
        return result.toList()
    }

    fun exportBookSources(sources: List<BookSource>): String = gson.toJson(sources)
    fun exportComicSources(sources: List<ComicSource>): String = gson.toJson(sources)
}
