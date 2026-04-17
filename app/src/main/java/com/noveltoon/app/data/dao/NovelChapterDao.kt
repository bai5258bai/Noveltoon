package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.NovelChapter
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelChapterDao {
    @Query("SELECT * FROM novel_chapters WHERE novelId = :novelId ORDER BY `index` ASC")
    fun getChapters(novelId: Long): Flow<List<NovelChapter>>

    @Query("SELECT * FROM novel_chapters WHERE novelId = :novelId ORDER BY `index` ASC")
    suspend fun getChaptersList(novelId: Long): List<NovelChapter>

    @Query("SELECT * FROM novel_chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): NovelChapter?

    @Query("SELECT * FROM novel_chapters WHERE novelId = :novelId AND `index` = :index LIMIT 1")
    suspend fun getChapterByIndex(novelId: Long, index: Int): NovelChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<NovelChapter>)

    @Update
    suspend fun update(chapter: NovelChapter)

    @Query("DELETE FROM novel_chapters WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)
}
