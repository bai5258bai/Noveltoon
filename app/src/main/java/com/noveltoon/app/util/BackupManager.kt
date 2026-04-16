package com.noveltoon.app.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noveltoon.app.data.AppDatabase
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.data.entity.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BackupData(
    val novels: List<Novel>,
    val comics: List<Comic>,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

object BackupManager {
    private val gson = Gson()

    suspend fun exportBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val novels = db.novelDao().getAllNovels().first()
            val comics = db.comicDao().getAllComics().first()
            val data = BackupData(novels = novels, comics = comics)
            val json = gson.toJson(data)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@withContext false

            val type = object : TypeToken<BackupData>() {}.type
            val data: BackupData = gson.fromJson(json, type)
            val db = AppDatabase.getInstance(context)

            data.novels.forEach { novel ->
                db.novelDao().insert(novel.copy(id = 0))
            }
            data.comics.forEach { comic ->
                db.comicDao().insert(comic.copy(id = 0))
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
