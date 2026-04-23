package com.noveltoon.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SourceRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val bookSourceDao = db.bookSourceDao()
    private val comicSourceDao = db.comicSourceDao()
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    data class ImportResult(
        val addedCount: Int,
        val errorMessage: String? = null
    )

    fun getAllBookSources(): Flow<List<BookSource>> = bookSourceDao.getAllSources()
    fun getAllComicSources(): Flow<List<ComicSource>> = comicSourceDao.getAllSources()

    suspend fun addBookSource(source: BookSource): Long = bookSourceDao.insert(source)
    suspend fun addComicSource(source: ComicSource): Long = comicSourceDao.insert(source)

    suspend fun updateBookSource(source: BookSource) = bookSourceDao.update(source)
    suspend fun updateComicSource(source: ComicSource) = comicSourceDao.update(source)

    suspend fun deleteBookSource(source: BookSource) = bookSourceDao.delete(source)
    suspend fun deleteComicSource(source: ComicSource) = comicSourceDao.delete(source)

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

    suspend fun importBookSourcesFromText(input: String): ImportResult {
        val trimmed = input.trim()
        val (json, fetchError) = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            fetchText(trimmed)
        } else trimmed to null
        if (json.isBlank()) return ImportResult(0, fetchError ?: "输入为空")
        return importBookSources(json)
    }

    suspend fun importComicSourcesFromText(input: String): ImportResult {
        val trimmed = input.trim()
        val (json, fetchError) = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            fetchText(trimmed)
        } else trimmed to null
        if (json.isBlank()) return ImportResult(0, fetchError ?: "输入为空")
        return importComicSources(json)
    }

    suspend fun importBookSources(json: String): ImportResult {
        return try {
            val element = JsonParser.parseString(json)
            val list = extractSourceObjects(element).mapNotNull { parseBookSource(it) }
            if (list.isEmpty()) ImportResult(0, "未识别到可用书源字段（可尝试阅读3.0规则）")
            else {
                bookSourceDao.insertAll(list.map { it.copy(id = 0, isBuiltIn = false) })
                ImportResult(list.size)
            }
        } catch (e: Exception) {
            ImportResult(0, "JSON 解析失败：${e.message ?: "unknown"}")
        }
    }

    suspend fun importComicSources(json: String): ImportResult {
        return try {
            val element = JsonParser.parseString(json)
            val list = extractSourceObjects(element).mapNotNull { parseComicSource(it) }
            if (list.isEmpty()) ImportResult(0, "未识别到可用图源字段（可尝试漫画规则 JSON）")
            else {
                comicSourceDao.insertAll(list.map { it.copy(id = 0, isBuiltIn = false) })
                ImportResult(list.size)
            }
        } catch (e: Exception) {
            ImportResult(0, "JSON 解析失败：${e.message ?: "unknown"}")
        }
    }

    private fun parseBookSource(o: JsonObject): BookSource? {
        return try {
            val ruleSearch = o.obj("ruleSearch")
            val ruleBookInfo = o.obj("ruleBookInfo")
            val ruleToc = o.obj("ruleToc")
            val ruleContent = o.obj("ruleContent")
            BookSource(
                name = o.str("name") ?: o.str("bookSourceName") ?: return null,
                baseUrl = o.str("baseUrl") ?: o.str("bookSourceUrl") ?: "",
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = o.str("searchUrl") ?: "",
                searchListRule = o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: "",
                searchNameRule = o.str("searchNameRule") ?: ruleSearch?.str("name") ?: "",
                searchAuthorRule = o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: "",
                searchCoverRule = o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: "",
                searchUrlRule = o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: "",
                searchLatestChapterRule = o.str("searchLatestChapterRule") ?: ruleSearch?.str("lastChapter") ?: "",
                detailNameRule = o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: "",
                detailAuthorRule = o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: "",
                detailCoverRule = o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: "",
                detailDescRule = o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: "",
                chapterListRule = o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: "",
                chapterNameRule = o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: "",
                chapterUrlRule = o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: "",
                contentRule = o.str("contentRule") ?: ruleContent?.str("content") ?: "",
                contentNextPageRule = o.str("contentNextPageRule") ?: "",
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = false
            )
        } catch (e: Exception) {
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
                name = o.str("name") ?: o.str("bookSourceName") ?: return null,
                baseUrl = o.str("baseUrl") ?: o.str("bookSourceUrl") ?: "",
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = o.str("searchUrl") ?: "",
                searchListRule = o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: "",
                searchNameRule = o.str("searchNameRule") ?: ruleSearch?.str("name") ?: "",
                searchAuthorRule = o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: "",
                searchCoverRule = o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: "",
                searchUrlRule = o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: "",
                searchStatusRule = o.str("searchStatusRule") ?: ruleSearch?.str("kind") ?: "",
                detailNameRule = o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: "",
                detailAuthorRule = o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: "",
                detailCoverRule = o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: "",
                detailDescRule = o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: "",
                chapterListRule = o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: "",
                chapterNameRule = o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: "",
                chapterUrlRule = o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: "",
                imageListRule = o.str("imageListRule") ?: ruleContent?.str("imageList") ?: "",
                imageUrlRule = o.str("imageUrlRule") ?: ruleContent?.str("imageUrl") ?: "",
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.str(key: String): String? {
        return try {
            val el = this.get(key)
            if (el == null || el.isJsonNull) null else el.asString
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        val el = this.get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private fun extractSourceObjects(element: JsonElement): List<JsonObject> {
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
        }
        if (!element.isJsonObject) return emptyList()
        val obj = element.asJsonObject
        val wrappedKeys = listOf("sources", "bookSources", "comicSources", "data", "list", "items")
        wrappedKeys.forEach { key ->
            val arr = obj.get(key)
            if (arr != null && arr.isJsonArray) {
                return arr.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
            }
        }
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
