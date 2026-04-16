package com.noveltoon.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comic_chapters",
    foreignKeys = [ForeignKey(
        entity = Comic::class,
        parentColumns = ["id"],
        childColumns = ["comicId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("comicId")]
)
data class ComicChapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicId: Long,
    val title: String,
    val url: String = "",
    val index: Int = 0
)
