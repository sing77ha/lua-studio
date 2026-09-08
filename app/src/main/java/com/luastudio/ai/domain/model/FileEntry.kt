package com.luastudio.ai.domain.model

import android.net.Uri

/**
 * A single entry (file or folder) as listed from a DocumentFile directory.
 * This is transient UI/data-layer state, not something persisted directly —
 * recent files/projects are what get remembered across sessions.
 */
data class FileEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
) {
    val extension: String get() = name.substringAfterLast('.', "")
    val isLuaFile: Boolean get() = extension.equals("lua", ignoreCase = true) || extension.equals("luau", ignoreCase = true)
}
