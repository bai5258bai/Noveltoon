package com.noveltoon.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
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

    fun getAllBookSources(): Flow<List<BookSource>> = bookSourceDao.getAllSources()
    fun getAllComicSources(): Flow<List<ComicSource>> = comicSourceDao.getAllSources()

    suspend fun addBookSource(source: BookSource): Long = bookSourceDao.insert(source)
    suspend fun addComicSource(source: ComicSource): Long = comicSourceDao.insert(source)

    suspend fun updateBookSource(source: BookSource) = bookSourceDao.update(source)
    suspend fun updateComicSource(source: ComicSource) = comicSourceDao.update(source)

    suspend fun deleteBookSource(source: BookSource) = bookSourceDao.delete(source)
    suspend fun deleteComicSource(source: ComicSource) = comicSourceDao.delete(source)

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            client.newCall(request).execute().body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun importBookSourcesFromText(input: String): Int {
        val trimmed = input.trim()
        val json = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            fetchText(trimmed)
        } else trimmed
        if (json.isBlank()) return 0
        return importBookSources(json)
    }

    suspend fun importComicSourcesFromText(input: String): Int {
        val trimmed = input.trim()
        val json = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            fetchText(trimmed)
        } else trimmed
        if (json.isBlank()) return 0
        return importComicSources(json)
    }

    suspend fun importBookSources(json: String): Int {
        return try {
            val element = JsonParser.parseString(json)
            val array: JsonArray = when {
                element.isJsonArray -> element.asJsonArray
                element.isJsonObject -> JsonArray().also { it.add(element.asJsonObject) }
                else -> return 0
            }
            val list = array.mapNotNull { parseBookSource(it.asJsonObject) }
            if (list.isEmpty()) 0
            else {
                bookSourceDao.insertAll(list.map { it.copy(id = 0, isBuiltIn = false) })
                list.size
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun importComicSources(json: String): Int {
        return try {
            val element = JsonParser.parseString(json)
            val array: JsonArray = when {
                element.isJsonArray -> element.asJsonArray
                element.isJsonObject -> JsonArray().also { it.add(element.asJsonObject) }
                else -> return 0
            }
            val list = array.mapNotNull { parseComicSource(it.asJsonObject) }
            if (list.isEmpty()) 0
            else {
                comicSourceDao.insertAll(list.map { it.copy(id = 0, isBuiltIn = false) })
                list.size
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun parseBookSource(o: JsonObject): BookSource? {
        return try {
            BookSource(
                name = o.str("name") ?: return null,
                baseUrl = o.str("baseUrl") ?: o.str("bookSourceUrl") ?: "",
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: 0,
                searchUrl = o.str("searchUrl") ?: "",
                searchListRule = o.str("searchListRule") ?: "",
                searchNameRule = o.str("searchNameRule") ?: "",
                searchAuthorRule = o.str("searchAuthorRule") ?: "",
                searchCoverRule = o.str("searchCoverRule") ?: "",
                searchUrlRule = o.str("searchUrlRule") ?: "",
                searchLatestChapterRule = o.str("searchLatestChapterRule") ?: "",
                detailNameRule = o.str("detailNameRule") ?: "",
                detailAuthorRule = o.str("detailAuthorRule") ?: "",
                detailCoverRule = o.str("detailCoverRule") ?: "",
                detailDescRule = o.str("detailDescRule") ?: "",
                chapterListRule = o.str("chapterListRule") ?: "",
                chapterNameRule = o.str("chapterNameRule") ?: "",
                chapterUrlRule = o.str("chapterUrlRule") ?: "",
                contentRule = o.str("contentRule") ?: "",
                contentNextPageRule = o.str("contentNextPageRule") ?: "",
                searchEncoding = o.str("searchEncoding") ?: "UTF-8",
                isBuiltIn = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseComicSource(o: JsonObject): ComicSource? {
        return try {
            ComicSource(
                name = o.str("name") ?: return null,
                baseUrl = o.str("baseUrl") ?: "",
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: 0,
                searchUrl = o.str("searchUrl") ?: "",
                searchListRule = o.str("searchListRule") ?: "",
                searchNameRule = o.str("searchNameRule") ?: "",
                searchAuthorRule = o.str("searchAuthorRule") ?: "",
                searchCoverRule = o.str("searchCoverRule") ?: "",
                searchUrlRule = o.str("searchUrlRule") ?: "",
                searchStatusRule = o.str("searchStatusRule") ?: "",
                detailNameRule = o.str("detailNameRule") ?: "",
                detailAuthorRule = o.str("detailAuthorRule") ?: "",
                detailCoverRule = o.str("detailCoverRule") ?: "",
                detailDescRule = o.str("detailDescRule") ?: "",
                chapterListRule = o.str("chapterListRule") ?: "",
                chapterNameRule = o.str("chapterNameRule") ?: "",
                chapterUrlRule = o.str("chapterUrlRule") ?: "",
                imageListRule = o.str("imageListRule") ?: "",
                imageUrlRule = o.str("imageUrlRule") ?: "",
                searchEncoding = o.str("searchEncoding") ?: "UTF-8",
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

    fun exportBookSources(sources: List<BookSource>): String = gson.toJson(sources)
    fun exportComicSources(sources: List<ComicSource>): String = gson.toJson(sources)
}
