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

        try {
            if (db.bookSourceDao().getAllSources().first().isEmpty()) {
                val json = context.assets.open("default_book_sources.json")
                    .bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<BookSource>>() {}.type
                val sources: List<BookSource> = gson.fromJson(json, type)
                db.bookSourceDao().insertAll(sources.map { it.copy(id = 0) })
            }
        } catch (_: Exception) {}

        try {
            if (db.comicSourceDao().getAllSources().first().isEmpty()) {
                val json = context.assets.open("default_comic_sources.json")
                    .bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<ComicSource>>() {}.type
                val sources: List<ComicSource> = gson.fromJson(json, type)
                db.comicSourceDao().insertAll(sources.map { it.copy(id = 0) })
            }
        } catch (_: Exception) {}
    }
}
