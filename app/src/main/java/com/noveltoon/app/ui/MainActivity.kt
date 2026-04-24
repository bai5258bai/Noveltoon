package com.noveltoon.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.ui.navigation.AppNavigation
import com.noveltoon.app.ui.navigation.Screen
import com.noveltoon.app.ui.theme.NovelToonTheme

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPreferences(applicationContext)

        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = 2)

            NovelToonTheme(themeMode = themeMode) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        BottomNavItem(
            label = stringResource(R.string.tab_novel),
            selectedIcon = Icons.Filled.AutoStories,
            unselectedIcon = Icons.Outlined.AutoStories,
            route = Screen.NovelBookshelf.route
        ),
        BottomNavItem(
            label = stringResource(R.string.tab_comic),
            selectedIcon = Icons.Filled.CollectionsBookmark,
            unselectedIcon = Icons.Outlined.CollectionsBookmark,
            route = Screen.ComicBookshelf.route
        ),
        BottomNavItem(
            label = stringResource(R.string.tab_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            route = Screen.Settings.route
        )
    )

    // Routes where the bottom bar must be hidden
    val hideBottomBarRoutes = setOf(
        Screen.NovelReader.route,
        Screen.NovelSearch.route,
        Screen.NovelSourceManage.route,
        Screen.ComicReader.route,
        Screen.ComicSearch.route,
        Screen.ComicSourceManage.route
    )
    val showBottomBar = currentDestination?.route != null &&
        currentDestination.hierarchy.none { dest ->
            hideBottomBarRoutes.any { hideRoute ->
                dest.route == hideRoute || (dest.route?.startsWith("novel_reader/") == true) ||
                    (dest.route?.startsWith("comic_reader/") == true)
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppNavigation(navController = navController)
        }
    }
}
