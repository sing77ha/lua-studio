package com.luastudio.ai.domain.model

/**
 * Supported script languages in the editor. Kept as an enum (not a raw string)
 * so syntax highlighting / toolbar / autocomplete can branch on it safely.
 */
enum class ScriptLanguage(val extension: String, val label: String) {
    LUA("lua", "Lua"),
    LUAU("luau", "Luau");

    companion object {
        fun fromExtension(ext: String): ScriptLanguage =
            entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) } ?: LUA
    }
}

/**
 * A file tracked by the app, either on-disk (via SAF Uri) or newly created
 * and not yet saved anywhere.
 *
 * @param uriString the persisted content:// / file:// Uri as a string, or null for an
 *                   in-memory file that has never been saved.
 */
data class LuaFile(
    val id: String,
    val name: String,
    val uriString: String?,
    val language: ScriptLanguage,
    val lastModifiedEpochMillis: Long,
    val sizeBytes: Long = 0L,
    val isModified: Boolean = false
)

/**
 * A folder-based project opened via the Storage Access Framework.
 */
data class LuaProject(
    val id: String,
    val name: String,
    val rootUriString: String,
    val lastOpenedEpochMillis: Long
)
