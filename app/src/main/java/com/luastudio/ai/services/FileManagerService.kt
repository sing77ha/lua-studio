package com.luastudio.ai.services

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.luastudio.ai.domain.model.FileEntry

/**
 * Thin wrapper over androidx.documentfile.provider.DocumentFile so the Files
 * screen never touches raw filesystem paths — everything is resolved from
 * the persisted root tree Uri the user granted via ACTION_OPEN_DOCUMENT_TREE.
 *
 * Folders are addressed by a path of segment names resolved from the root
 * each time (rather than caching intermediate Uris), since renameTo() on a
 * DocumentFile can change its underlying Uri.
 */
class FileManagerService(private val context: Context) {

    fun rootDocument(treeUri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    fun resolveFolder(root: DocumentFile, path: List<String>): DocumentFile? {
        var current = root
        for (segment in path) {
            current = current.findFile(segment)?.takeIf { it.isDirectory } ?: return null
        }
        return current
    }

    fun findChild(folder: DocumentFile, name: String): DocumentFile? = folder.findFile(name)

    fun listEntries(folder: DocumentFile): List<FileEntry> {
        return folder.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            FileEntry(
                uri = doc.uri,
                name = name,
                isDirectory = doc.isDirectory,
                sizeBytes = if (doc.isDirectory) 0L else doc.length(),
                lastModifiedEpochMillis = doc.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun createFile(folder: DocumentFile, name: String, mimeType: String = "text/plain"): Uri? =
        folder.createFile(mimeType, name)?.uri

    fun createFolder(folder: DocumentFile, name: String): Uri? =
        folder.createDirectory(name)?.uri

    fun delete(folder: DocumentFile, name: String): Boolean =
        folder.findFile(name)?.delete() ?: false

    fun rename(folder: DocumentFile, oldName: String, newName: String): Boolean =
        folder.findFile(oldName)?.renameTo(newName) ?: false

    /** Returns a name like "script (copy).lua" that doesn't collide with an existing child. */
    fun uniqueDuplicateName(folder: DocumentFile, originalName: String): String {
        val dot = originalName.lastIndexOf('.')
        val base = if (dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (dot > 0) originalName.substring(dot) else ""
        var candidate = "$base (copy)$ext"
        var counter = 2
        while (folder.findFile(candidate) != null) {
            candidate = "$base (copy $counter)$ext"
            counter++
        }
        return candidate
    }

    /** Returns a name that doesn't collide with an existing child, for imports/exports. */
    fun uniqueImportName(folder: DocumentFile, originalName: String): String {
        if (folder.findFile(originalName) == null) return originalName
        return uniqueDuplicateName(folder, originalName)
    }
}
