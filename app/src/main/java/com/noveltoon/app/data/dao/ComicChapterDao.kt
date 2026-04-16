package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.ComicChapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicChapterDao {
    @Query("SELECT * FROM comic_chapters WHERE comicId = :comicId ORDER BY `index` ASC")
    fun getChapters(comicId: Long): Flow<List<ComicChapter>>

    @Query("SELECT * FROM comic_chapters WHERE comicId = :comicId ORDER BY `index` ASC")
    suspend fun getChaptersList(comicId: Long): List<ComicChapter>

    @Query("SELECT * FROM comic_chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ComicChapter?

    @Query("SELECT * FROM comic_chapters WHERE comicId = :comicId AND `index` = :index LIMIT 1")
    suspend fun getChapterByIndex(comicId: Long, index: Int): ComicChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ComicChapter>)

    @Query("DELETE FROM comic_chapters WHERE comicId = :comicId")
    suspend fun deleteByComicId(comicId: Long)
}
