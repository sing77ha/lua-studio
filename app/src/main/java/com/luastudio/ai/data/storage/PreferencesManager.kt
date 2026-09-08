package com.luastudio.ai.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luastudio.ai.domain.model.AccentColor
import com.luastudio.ai.domain.model.AppearanceSettings
import com.luastudio.ai.domain.model.EditorSettings
import com.luastudio.ai.domain.model.EditorTheme
import com.luastudio.ai.domain.model.RuntimeSettings
import com.luastudio.ai.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "lua_studio_prefs")

/**
 * Non-secret app preferences. LUA STUDIO is fully offline — there is no API
 * key, no account, and nothing here is ever sent off the device.
 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val RECENT_FILES = stringPreferencesKey("recent_files_json")
        val RECENT_PROJECTS = stringPreferencesKey("recent_projects_json")

        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val TAB_SIZE = intPreferencesKey("editor_tab_size")
        val WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("editor_show_line_numbers")
        val AUTO_INDENT = booleanPreferencesKey("editor_auto_indent")
        val SYNTAX_HIGHLIGHT = booleanPreferencesKey("editor_syntax_highlight")
        val AUTO_SAVE = booleanPreferencesKey("editor_auto_save")
        val FORMAT_ON_SAVE = booleanPreferencesKey("editor_format_on_save")

        val THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("appearance_accent_color")
        val EDITOR_THEME = stringPreferencesKey("appearance_editor_theme")

        val ROOT_FOLDER_URI = stringPreferencesKey("files_root_folder_uri")
        val CONFIRM_DELETE = booleanPreferencesKey("files_confirm_delete")

        val RUNTIME_TIMEOUT_MS = longPreferencesKey("runtime_timeout_ms")
        val RUNTIME_OUTPUT_LIMIT = intPreferencesKey("runtime_output_limit_chars")
        val RUNTIME_AUTO_CLEAR = booleanPreferencesKey("runtime_auto_clear_console")
    }

    // ---------- Recent files (stored as a small JSON array: id/name/uri/lang/modified) ----------

    val recentFilesJson: Flow<String> = context.dataStore.data.map { it[Keys.RECENT_FILES] ?: "[]" }

    suspend fun pushRecentFile(id: String, name: String, uri: String?, language: String, modified: Long) {
        context.dataStore.edit { prefs ->
            val current = JSONArray(prefs[Keys.RECENT_FILES] ?: "[]")
            val updated = JSONArray()
            val entry = JSONObject().apply {
                put("id", id); put("name", name); put("uri", uri)
                put("language", language); put("modified", modified)
            }
            updated.put(entry)
            for (i in 0 until current.length()) {
                val obj = current.getJSONObject(i)
                if (obj.optString("id") != id && updated.length() < MAX_RECENT_ITEMS) {
                    updated.put(obj)
                }
            }
            prefs[Keys.RECENT_FILES] = updated.toString()
        }
    }

    // ---------- Recent projects (opened folders) ----------

    val recentProjectsJson: Flow<String> = context.dataStore.data.map { it[Keys.RECENT_PROJECTS] ?: "[]" }

    suspend fun pushRecentProject(id: String, name: String, uri: String, modified: Long) {
        context.dataStore.edit { prefs ->
            val current = JSONArray(prefs[Keys.RECENT_PROJECTS] ?: "[]")
            val updated = JSONArray()
            val entry = JSONObject().apply {
                put("id", id); put("name", name); put("uri", uri); put("modified", modified)
            }
            updated.put(entry)
            for (i in 0 until current.length()) {
                val obj = current.getJSONObject(i)
                if (obj.optString("uri") != uri && updated.length() < MAX_RECENT_ITEMS) {
                    updated.put(obj)
                }
            }
            prefs[Keys.RECENT_PROJECTS] = updated.toString()
        }
    }

    // ---------- Files screen root folder + settings ----------

    val currentRootFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.ROOT_FOLDER_URI] }

    suspend fun setCurrentRootFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.ROOT_FOLDER_URI] = uri else prefs.remove(Keys.ROOT_FOLDER_URI)
        }
    }

    val confirmDelete: Flow<Boolean> = context.dataStore.data.map { it[Keys.CONFIRM_DELETE] ?: true }

    suspend fun setConfirmDelete(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CONFIRM_DELETE] = value }
    }

    // ---------- Editor settings ----------

    val editorSettings: Flow<EditorSettings> = context.dataStore.data.map { prefs ->
        EditorSettings(
            fontSize = prefs[Keys.FONT_SIZE] ?: 14,
            tabSize = prefs[Keys.TAB_SIZE] ?: 4,
            wordWrap = prefs[Keys.WORD_WRAP] ?: false,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            autoIndent = prefs[Keys.AUTO_INDENT] ?: true,
            syntaxHighlight = prefs[Keys.SYNTAX_HIGHLIGHT] ?: true,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true,
            formatOnSave = prefs[Keys.FORMAT_ON_SAVE] ?: false
        )
    }

    suspend fun updateEditorSettings(settings: EditorSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = settings.fontSize
            prefs[Keys.TAB_SIZE] = settings.tabSize
            prefs[Keys.WORD_WRAP] = settings.wordWrap
            prefs[Keys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
            prefs[Keys.AUTO_INDENT] = settings.autoIndent
            prefs[Keys.SYNTAX_HIGHLIGHT] = settings.syntaxHighlight
            prefs[Keys.AUTO_SAVE] = settings.autoSave
            prefs[Keys.FORMAT_ON_SAVE] = settings.formatOnSave
        }
    }

    // ---------- Appearance settings ----------

    val appearanceSettings: Flow<AppearanceSettings> = context.dataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "") }
                .getOrDefault(ThemeMode.DARK),
            accentColor = runCatching { AccentColor.valueOf(prefs[Keys.ACCENT_COLOR] ?: "") }
                .getOrDefault(AccentColor.BLUE),
            editorTheme = runCatching { EditorTheme.valueOf(prefs[Keys.EDITOR_THEME] ?: "") }
                .getOrDefault(EditorTheme.DEFAULT_DARK)
        )
    }

    suspend fun updateAppearanceSettings(settings: AppearanceSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.ACCENT_COLOR] = settings.accentColor.name
            prefs[Keys.EDITOR_THEME] = settings.editorTheme.name
        }
    }

    // ---------- Runtime settings (offline Lua execution — no API/network fields) ----------

    val runtimeSettings: Flow<RuntimeSettings> = context.dataStore.data.map { prefs ->
        RuntimeSettings(
            executionTimeoutMs = prefs[Keys.RUNTIME_TIMEOUT_MS] ?: 5000L,
            outputLimitChars = prefs[Keys.RUNTIME_OUTPUT_LIMIT] ?: 20000,
            autoClearConsoleBeforeRun = prefs[Keys.RUNTIME_AUTO_CLEAR] ?: true
        )
    }

    suspend fun updateRuntimeSettings(settings: RuntimeSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RUNTIME_TIMEOUT_MS] = settings.executionTimeoutMs
            prefs[Keys.RUNTIME_OUTPUT_LIMIT] = settings.outputLimitChars
            prefs[Keys.RUNTIME_AUTO_CLEAR] = settings.autoClearConsoleBeforeRun
        }
    }

    companion object {
        private const val MAX_RECENT_ITEMS = 20
    }
}
