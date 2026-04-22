package com.noveltoon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.noveltoon.app.data.dao.*
import com.noveltoon.app.data.entity.*

@Database(
    entities = [
        Novel::class,
        NovelChapter::class,
        Comic::class,
        ComicChapter::class,
        BookSource::class,
        ComicSource::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun novelChapterDao(): NovelChapterDao
    abstract fun comicDao(): ComicDao
    abstract fun comicChapterDao(): ComicChapterDao
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun comicSourceDao(): ComicSourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "noveltoon.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
