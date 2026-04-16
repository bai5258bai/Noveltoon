package com.noveltoon.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.flow.Flow

class SourceRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val bookSourceDao = db.bookSourceDao()
    private val comicSourceDao = db.comicSourceDao()
    private val gson = Gson()

    fun getAllBookSources(): Flow<List<BookSource>> = bookSourceDao.getAllSources()
    fun getAllComicSources(): Flow<List<ComicSource>> = comicSourceDao.getAllSources()

    suspend fun addBookSource(source: BookSource): Long = bookSourceDao.insert(source)
    suspend fun addComicSource(source: ComicSource): Long = comicSourceDao.insert(source)

    suspend fun updateBookSource(source: BookSource) = bookSourceDao.update(source)
    suspend fun updateComicSource(source: ComicSource) = comicSourceDao.update(source)

    suspend fun deleteBookSource(source: BookSource) = bookSourceDao.delete(source)
    suspend fun deleteComicSource(source: ComicSource) = comicSourceDao.delete(source)

    suspend fun importBookSources(json: String): Int {
        return try {
            val type = object : TypeToken<List<BookSource>>() {}.type
            val sources: List<BookSource> = gson.fromJson(json, type)
            bookSourceDao.insertAll(sources.map { it.copy(id = 0) })
            sources.size
        } catch (e: Exception) {
            0
        }
    }

    suspend fun importComicSources(json: String): Int {
        return try {
            val type = object : TypeToken<List<ComicSource>>() {}.type
            val sources: List<ComicSource> = gson.fromJson(json, type)
            comicSourceDao.insertAll(sources.map { it.copy(id = 0) })
            sources.size
        } catch (e: Exception) {
            0
        }
    }

    fun exportBookSources(sources: List<BookSource>): String = gson.toJson(sources)
    fun exportComicSources(sources: List<ComicSource>): String = gson.toJson(sources)
}
