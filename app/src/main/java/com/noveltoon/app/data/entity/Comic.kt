package com.noveltoon.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comics")
data class Comic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val sourceUrl: String = "",
    val sourceName: String = "",
    val status: String = "",
    val lastChapterTitle: String = "",
    val lastReadChapterIndex: Int = 0,
    val lastReadPageIndex: Int = 0,
    val totalChapters: Int = 0,
    val hasUnread: Boolean = false,
    val isLocal: Boolean = false,
    val localPath: String = "",
    val addedTime: Long = System.currentTimeMillis(),
    val lastReadTime: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val totalReadingTimeMs: Long = 0L
)
