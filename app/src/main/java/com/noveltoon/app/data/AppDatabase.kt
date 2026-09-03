package com.noveltoon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 4,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Preserve reading data when upgrading from the original schema.
         * v2 added reading-time counters and v3 added the built-in source flag.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE novels ADD COLUMN totalReadingTimeMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE comics ADD COLUMN totalReadingTimeMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_sources ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE comic_sources ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Speed up chapter list loading and chapter lookup in the readers. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_novel_chapters_novelId_index ON novel_chapters(novelId, `index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comic_chapters_comicId_index ON comic_chapters(comicId, `index`)")
            }
        }
    }
}
