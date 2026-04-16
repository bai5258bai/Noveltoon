package com.noveltoon.app.ui.navigation

sealed class Screen(val route: String) {
    data object NovelBookshelf : Screen("novel_bookshelf")
    data object NovelSearch : Screen("novel_search")
    data object NovelReader : Screen("novel_reader/{novelId}") {
        fun createRoute(novelId: Long) = "novel_reader/$novelId"
    }
    data object NovelSourceManage : Screen("novel_source_manage")

    data object ComicBookshelf : Screen("comic_bookshelf")
    data object ComicSearch : Screen("comic_search")
    data object ComicReader : Screen("comic_reader/{comicId}/{chapterIndex}") {
        fun createRoute(comicId: Long, chapterIndex: Int) = "comic_reader/$comicId/$chapterIndex"
    }
    data object ComicSourceManage : Screen("comic_source_manage")

    data object Settings : Screen("settings")
}
