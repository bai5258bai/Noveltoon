package com.noveltoon.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_chapters",
    foreignKeys = [ForeignKey(
        entity = Novel::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("novelId")]
)
data class NovelChapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val title: String,
    val url: String = "",
    val index: Int = 0,
    val content: String = "",
    val isCached: Boolean = false
)
