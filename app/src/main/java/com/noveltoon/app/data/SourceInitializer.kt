package com.noveltoon.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object SourceInitializer {
    private val gson = Gson()

    suspend fun initIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)

        // Load built-in book sources
        try {
            val bookSources = loadBuiltInBookSources(context)
            val existing = db.bookSourceDao().getAllSources().first()
            val existingBuiltIn = existing.filter { it.isBuiltIn }
            val userAdded = existing.filter { !it.isBuiltIn }

            // Remove old built-ins and replace with the bundled set (preserve user-added)
            existingBuiltIn.forEach { db.bookSourceDao().delete(it) }

            // Preserve enabled state from existing if a name match exists
            val preservedStates = existingBuiltIn.associate { it.name to it.enabled }
            val toInsert = bookSources.map {
                val enabled = preservedStates[it.name] ?: it.enabled
                it.copy(id = 0, enabled = enabled, isBuiltIn = true)
            }
            db.bookSourceDao().insertAll(toInsert)
            // Keep user added intact
            userAdded.forEach { /* no-op */ }
        } catch (_: Exception) {}

        // Load built-in comic sources
        try {
            val comicSources = loadBuiltInComicSources(context)
            val existing = db.comicSourceDao().getAllSources().first()
            val existingBuiltIn = existing.filter { it.isBuiltIn }

            existingBuiltIn.forEach { db.comicSourceDao().delete(it) }

            val preservedStates = existingBuiltIn.associate { it.name to it.enabled }
            val toInsert = comicSources.map {
                val enabled = preservedStates[it.name] ?: it.enabled
                it.copy(id = 0, enabled = enabled, isBuiltIn = true)
            }
            db.comicSourceDao().insertAll(toInsert)
        } catch (_: Exception) {}
    }

    fun loadBuiltInBookSources(context: Context): List<BookSource> {
        val json = context.assets.open("default_book_sources.json")
            .bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BookSource>>() {}.type
        return gson.fromJson(json, type)
    }

    fun loadBuiltInComicSources(context: Context): List<ComicSource> {
        val json = context.assets.open("default_comic_sources.json")
            .bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<ComicSource>>() {}.type
        return gson.fromJson(json, type)
    }
}
