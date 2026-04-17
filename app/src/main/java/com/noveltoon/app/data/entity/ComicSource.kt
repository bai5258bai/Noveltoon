package com.noveltoon.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comic_sources")
data class ComicSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val searchUrl: String = "",
    val searchListRule: String = "",
    val searchNameRule: String = "",
    val searchAuthorRule: String = "",
    val searchCoverRule: String = "",
    val searchUrlRule: String = "",
    val searchStatusRule: String = "",
    val detailNameRule: String = "",
    val detailAuthorRule: String = "",
    val detailCoverRule: String = "",
    val detailDescRule: String = "",
    val chapterListRule: String = "",
    val chapterNameRule: String = "",
    val chapterUrlRule: String = "",
    val imageListRule: String = "",
    val imageUrlRule: String = ""
)
