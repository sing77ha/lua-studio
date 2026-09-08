package com.luastudio.ai.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class AccentColor { BLUE, PURPLE, GREEN, ORANGE, RED }

enum class EditorTheme(val label: String) {
    DEFAULT_DARK("Default Dark"),
    MIDNIGHT("Midnight"),
    DRACULA("Dracula"),
    MONOKAI("Monokai"),
    LIGHT("Light")
}

data class EditorSettings(
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val autoIndent: Boolean = true,
    val syntaxHighlight: Boolean = true,
    val autoSave: Boolean = true,
    val formatOnSave: Boolean = false
)

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.BLUE,
    val editorTheme: EditorTheme = EditorTheme.DEFAULT_DARK
)

data class FileSettings(
    val defaultFolderUri: String? = null,
    val autoSave: Boolean = true,
    val confirmDelete: Boolean = true
)

/**
 * Settings for the offline Lua Runtime (Run/Stop/Console). No network or
 * API-related fields here by design — everything executes on-device.
 */
data class RuntimeSettings(
    val executionTimeoutMs: Long = 5000L,
    val outputLimitChars: Int = 20000,
    val autoClearConsoleBeforeRun: Boolean = true
)
