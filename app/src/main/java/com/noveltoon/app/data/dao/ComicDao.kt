package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.Comic
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY sortOrder ASC, lastReadTime DESC")
    fun getAllComics(): Flow<List<Comic>>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicById(id: Long): Comic?

    @Query("SELECT * FROM comics WHERE title = :title AND sourceUrl = :sourceUrl LIMIT 1")
    suspend fun findComic(title: String, sourceUrl: String): Comic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comic: Comic): Long

    @Update
    suspend fun update(comic: Comic)

    @Delete
    suspend fun delete(comic: Comic)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE comics SET lastReadChapterIndex = :chapterIndex, lastReadPageIndex = :pageIndex, lastChapterTitle = :chapterTitle, lastReadTime = :time WHERE id = :id")
    suspend fun updateReadProgress(id: Long, chapterIndex: Int, pageIndex: Int, chapterTitle: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE comics SET hasUnread = :hasUnread WHERE id = :id")
    suspend fun updateUnread(id: Long, hasUnread: Boolean)

    @Query("UPDATE comics SET totalReadingTimeMs = totalReadingTimeMs + :addMs, lastReadTime = :time WHERE id = :id")
    suspend fun addReadingTime(id: Long, addMs: Long, time: Long = System.currentTimeMillis())
}
