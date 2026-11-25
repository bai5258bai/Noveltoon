package com.noveltoon.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceInitReport(
    val bookCount: Int,
    val comicCount: Int,
    val error: String? = null
)

object SourceInitializer {
    suspend fun initIfNeeded(context: Context): SourceInitReport = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val book = replaceBuiltInBooks(context, db)
        val comic = replaceBuiltInComics(context, db)
        val error = listOfNotNull(book.error, comic.error).joinToString("\n").ifBlank { null }
        SourceInitReport(
            bookCount = book.count,
            comicCount = comic.count,
            error = error
        )
    }

    private data class ReplaceResult(val count: Int, val error: String?)

    private suspend fun replaceBuiltInBooks(context: Context, db: AppDatabase): ReplaceResult {
        val parsed = try {
            val json = context.assets.open("default_book_sources.json").bufferedReader().use { it.readText() }
            SourceJson.parseBookSources(json)
        } catch (e: Exception) {
            return ReplaceResult(0, "内置书源：${e.message ?: e.javaClass.simpleName}")
        }
        return try {
            val existingBuiltIn = db.bookSourceDao().getAllSourcesOnce().filter { it.isBuiltIn }
            val preserved = existingBuiltIn.associate { it.name to it.enabled }
            val toInsert = parsed.map { source ->
                source.copy(
                    id = 0,
                    enabled = preserved[source.name] ?: source.enabled,
                    isBuiltIn = true
                )
            }
            db.withTransaction {
                existingBuiltIn.forEach { db.bookSourceDao().delete(it) }
                db.bookSourceDao().insertAll(toInsert)
            }
            ReplaceResult(toInsert.size, null)
        } catch (e: Exception) {
            ReplaceResult(0, "内置书源写入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun replaceBuiltInComics(context: Context, db: AppDatabase): ReplaceResult {
        val parsed = try {
            val json = context.assets.open("default_comic_sources.json").bufferedReader().use { it.readText() }
            SourceJson.parseComicSources(json)
        } catch (e: Exception) {
            return ReplaceResult(0, "内置图源：${e.message ?: e.javaClass.simpleName}")
        }
        return try {
            val existingBuiltIn = db.comicSourceDao().getAllSourcesOnce().filter { it.isBuiltIn }
            val preserved = existingBuiltIn.associate { it.name to it.enabled }
            val toInsert = parsed.map { source ->
                source.copy(
                    id = 0,
                    enabled = preserved[source.name] ?: source.enabled,
                    isBuiltIn = true
                )
            }
            db.withTransaction {
                existingBuiltIn.forEach { db.comicSourceDao().delete(it) }
                db.comicSourceDao().insertAll(toInsert)
            }
            ReplaceResult(toInsert.size, null)
        } catch (e: Exception) {
            ReplaceResult(0, "内置图源写入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
