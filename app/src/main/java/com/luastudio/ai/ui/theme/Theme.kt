package com.luastudio.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.luastudio.ai.domain.model.AccentColor
import com.luastudio.ai.domain.model.ThemeMode

private fun accentColorValue(accent: AccentColor) = when (accent) {
    AccentColor.BLUE -> AccentBlue
    AccentColor.PURPLE -> AccentPurple
    AccentColor.GREEN -> AccentGreen
    AccentColor.ORANGE -> AccentOrange
    AccentColor.RED -> AccentRed
}

@Composable
fun LuaStudioTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = accentColorValue(accentColor)

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accent,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onBackground = DarkOnSurface,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceMuted,
            outline = DarkOutline,
            error = StatusError
        )
    } else {
        lightColorScheme(
            primary = accent,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = LightOnSurface,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceMuted,
            outline = LightOutline,
            error = StatusError
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
