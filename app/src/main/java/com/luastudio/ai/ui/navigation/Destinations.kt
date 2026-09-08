package com.luastudio.ai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every screen the app can navigate to. Home is reachable from the top bar
 * logo/menu on any bottom-nav screen rather than living in the bottom bar
 * itself, per the spec ("Home สามารถเข้าถึงได้จาก Logo / Menu").
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Editor : Destination("editor?fileId={fileId}") {
        const val BASE = "editor"
        const val ARG_FILE_ID = "fileId"
        fun routeFor(fileId: String?) = "editor?fileId=${fileId ?: ""}"
    }
    data object AiAssistant : Destination("ai_assistant")
    data object Files : Destination("files")
    data object Settings : Destination("settings")
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Destination.Editor, "Editor", Icons.Filled.Code),
    BottomNavItem(Destination.AiAssistant, "AI", Icons.Filled.AutoAwesome),
    BottomNavItem(Destination.Files, "Files", Icons.Filled.Folder),
    BottomNavItem(Destination.Settings, "Settings", Icons.Filled.Settings)
)
