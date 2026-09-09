package com.luastudio.ai.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luastudio.ai.data.files.PendingFileOpenHolder
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.ui.components.ComingSoonScreen
import com.luastudio.ai.ui.editor.EditorScreen
import com.luastudio.ai.ui.files.FilesScreen
import com.luastudio.ai.ui.home.HomeScreen
import com.luastudio.ai.ui.settings.SettingsScreen

/**
 * Screens that show the bottom navigation bar. Home is reachable from the
 * top-bar logo on any of these, rather than being a bottom-bar tab itself.
 *
 * Note: NavBackStackEntry.destination.route always reports the *declared*
 * route pattern (e.g. "editor?fileId={fileId}"), not the resolved value —
 * so comparing against `Destination.X.route` directly is safe and exact,
 * no prefix-matching needed.
 */
private val bottomBarRoutes = setOf(
    Destination.Editor.route,
    Destination.AiAssistant.route,
    Destination.Files.route,
    Destination.Settings.route
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LuaStudioNavGraph(preferencesManager: PreferencesManager) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomBarRoutes
    val showTopBar = currentRoute != Destination.Home.route

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("LUA STUDIO") },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.navigate(Destination.Home.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(Icons.Filled.Code, contentDescription = "Home")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.destination.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(routeBase(item.destination)) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    preferencesManager = preferencesManager,
                    onNewFile = { file ->
                        PendingFileOpenHolder.post(file)
                        navController.navigate(Destination.Editor.routeFor(file.id))
                    },
                    onOpenFile = { navController.navigate(Destination.Files.route) },
                    onImportProject = { navController.navigate(Destination.Files.route) },
                    onOpenRecentFile = { file ->
                        PendingFileOpenHolder.post(file)
                        navController.navigate(Destination.Editor.routeFor(file.id))
                    },
                    onOpenRecentProject = { _ ->
                        navController.navigate(Destination.Files.route)
                    },
                    onAiGenerate = { navController.navigate(Destination.AiAssistant.route) },
                    onAiDebug = { navController.navigate(Destination.AiAssistant.route) },
                    onAiExplain = { navController.navigate(Destination.AiAssistant.route) }
                )
            }
            composable(
                route = Destination.Editor.route,
                arguments = listOf(
                    navArgument(Destination.Editor.ARG_FILE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                EditorScreen(preferencesManager = preferencesManager)
            }
            composable(Destination.AiAssistant.route) {
                com.luastudio.ai.ui.ai.AiDisabledScreen()
            }
            composable(Destination.Files.route) {
                FilesScreen(
                    preferencesManager = preferencesManager,
                    onOpenFileInEditor = { file ->
                        PendingFileOpenHolder.post(file)
                        navController.navigate(Destination.Editor.routeFor(file.id))
                    }
                )
            }
            composable(Destination.Settings.route) {
                ComingSoonScreen(
                    title = "Settings",
                    note = "Editor, appearance, file, and runtime settings land in the next phase."
                )
            }
        }
    }
}

private fun routeBase(destination: Destination): String = when (destination) {
    is Destination.Editor -> Destination.Editor.BASE
    else -> destination.route
}
