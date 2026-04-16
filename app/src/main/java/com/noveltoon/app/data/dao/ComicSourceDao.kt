package com.noveltoon.app.data.dao

import androidx.room.*
import com.noveltoon.app.data.entity.ComicSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicSourceDao {
    @Query("SELECT * FROM comic_sources ORDER BY sortOrder ASC, name ASC")
    fun getAllSources(): Flow<List<ComicSource>>

    @Query("SELECT * FROM comic_sources WHERE enabled = 1 ORDER BY sortOrder ASC")
    suspend fun getEnabledSources(): List<ComicSource>

    @Query("SELECT * FROM comic_sources WHERE id = :id")
    suspend fun getSourceById(id: Long): ComicSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: ComicSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<ComicSource>)

    @Update
    suspend fun update(source: ComicSource)

    @Delete
    suspend fun delete(source: ComicSource)

    @Query("DELETE FROM comic_sources")
    suspend fun deleteAll()
}
