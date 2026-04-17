package com.noveltoon.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.noveltoon.app.ui.comic.ComicBookshelfScreen
import com.noveltoon.app.ui.comic.ComicReaderScreen
import com.noveltoon.app.ui.comic.ComicSearchScreen
import com.noveltoon.app.ui.comic.ComicSourceManageScreen
import com.noveltoon.app.ui.novel.NovelBookshelfScreen
import com.noveltoon.app.ui.novel.NovelReaderScreen
import com.noveltoon.app.ui.novel.NovelSearchScreen
import com.noveltoon.app.ui.novel.NovelSourceManageScreen
import com.noveltoon.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.NovelBookshelf.route
    ) {
        composable(Screen.NovelBookshelf.route) {
            NovelBookshelfScreen(
                onNavigateToSearch = { navController.navigate(Screen.NovelSearch.route) },
                onNavigateToReader = { novelId ->
                    navController.navigate(Screen.NovelReader.createRoute(novelId))
                },
                onNavigateToSourceManage = { navController.navigate(Screen.NovelSourceManage.route) }
            )
        }

        composable(Screen.NovelSearch.route) {
            NovelSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { novelId ->
                    navController.navigate(Screen.NovelReader.createRoute(novelId)) {
                        popUpTo(Screen.NovelBookshelf.route)
                    }
                }
            )
        }

        composable(
            route = Screen.NovelReader.route,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType })
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            NovelReaderScreen(
                novelId = novelId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NovelSourceManage.route) {
            NovelSourceManageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ComicBookshelf.route) {
            ComicBookshelfScreen(
                onNavigateToSearch = { navController.navigate(Screen.ComicSearch.route) },
                onNavigateToReader = { comicId, chapterIndex ->
                    navController.navigate(Screen.ComicReader.createRoute(comicId, chapterIndex))
                },
                onNavigateToSourceManage = { navController.navigate(Screen.ComicSourceManage.route) }
            )
        }

        composable(Screen.ComicSearch.route) {
            ComicSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { comicId, chapterIndex ->
                    navController.navigate(Screen.ComicReader.createRoute(comicId, chapterIndex)) {
                        popUpTo(Screen.ComicBookshelf.route)
                    }
                }
            )
        }

        composable(
            route = Screen.ComicReader.route,
            arguments = listOf(
                navArgument("comicId") { type = NavType.LongType },
                navArgument("chapterIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getLong("comicId") ?: 0L
            val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            ComicReaderScreen(
                comicId = comicId,
                initialChapterIndex = chapterIndex,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ComicSourceManage.route) {
            ComicSourceManageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
