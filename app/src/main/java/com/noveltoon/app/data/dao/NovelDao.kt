package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.Novel
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY sortOrder ASC, lastReadTime DESC")
    fun getAllNovels(): Flow<List<Novel>>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getNovelById(id: Long): Novel?

    @Query("SELECT * FROM novels WHERE title = :title AND sourceUrl = :sourceUrl LIMIT 1")
    suspend fun findNovel(title: String, sourceUrl: String): Novel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: Novel): Long

    @Update
    suspend fun update(novel: Novel)

    @Delete
    suspend fun delete(novel: Novel)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE novels SET lastReadChapterIndex = :chapterIndex, lastReadPosition = :position, lastChapterTitle = :chapterTitle, lastReadTime = :time WHERE id = :id")
    suspend fun updateReadProgress(id: Long, chapterIndex: Int, position: Int, chapterTitle: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET isCompleted = :completed WHERE id = :id")
    suspend fun markCompleted(id: Long, completed: Boolean)
}
