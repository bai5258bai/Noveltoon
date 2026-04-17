package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.BookSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSourceDao {
    @Query("SELECT * FROM book_sources ORDER BY sortOrder ASC, name ASC")
    fun getAllSources(): Flow<List<BookSource>>

    @Query("SELECT * FROM book_sources WHERE enabled = 1 ORDER BY sortOrder ASC")
    suspend fun getEnabledSources(): List<BookSource>

    @Query("SELECT * FROM book_sources WHERE id = :id")
    suspend fun getSourceById(id: Long): BookSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: BookSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<BookSource>)

    @Update
    suspend fun update(source: BookSource)

    @Delete
    suspend fun delete(source: BookSource)

    @Query("DELETE FROM book_sources")
    suspend fun deleteAll()
}
