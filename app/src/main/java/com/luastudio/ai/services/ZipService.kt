package com.luastudio.ai.services

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Preserves folder structure on both ends: IMPORT ZIP recreates the
 * directories inside the zip as real SAF folders/files under the
 * destination; EXPORT PROJECT ZIP walks a SAF folder recursively and
 * writes matching zip entries, per the spec's PROJECT ZIP section.
 */
class ZipService(
    private val context: Context,
    private val fileManagerService: FileManagerService,
    private val fileService: FileService
) {

    suspend fun extractZip(zipUri: Uri, destination: DocumentFile): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val opened = context.contentResolver.openInputStream(zipUri)
                ?: return@withContext FileOpResult.Failure("Unable to open file")

            opened.use { input ->
                ZipInputStream(input).use { zipIn ->
                    val dirCache = mutableMapOf<String, DocumentFile>("" to destination)
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val cleanPath = entry.name.trim('/')
                        if (cleanPath.isBlank()) {
                            entry = zipIn.nextEntry
                            continue
                        }
                        val parts = cleanPath.split("/")
                        val fileName = parts.last()
                        val dirParts = parts.dropLast(1)

                        var parentDir = destination
                        var pathKey = ""
                        for (part in dirParts) {
                            pathKey = if (pathKey.isEmpty()) part else "$pathKey/$part"
                            parentDir = dirCache[pathKey] ?: run {
                                val existing = parentDir.findFile(part)?.takeIf { it.isDirectory }
                                val created = existing ?: parentDir.createDirectory(part)
                                created ?: return@withContext FileOpResult.Failure("Unable to save file")
                            }.also { dirCache[pathKey] = it }
                        }

                        if (!entry.isDirectory && fileName.isNotBlank()) {
                            val bytes = zipIn.readBytes()
                            val existingFile = parentDir.findFile(fileName)
                            val targetUri = existingFile?.uri
                                ?: parentDir.createFile(mimeTypeFor(fileName), fileName)?.uri
                            if (targetUri != null) {
                                fileService.writeBytes(targetUri, bytes)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            FileOpResult.Success(Unit)
        } catch (e: Exception) {
            FileOpResult.Failure("Unable to open file")
        }
    }

    suspend fun createZipFromFolder(folder: DocumentFile, outputUri: Uri): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val opened = context.contentResolver.openOutputStream(outputUri, "wt")
                ?: return@withContext FileOpResult.Failure("Unable to save file")
            opened.use { out ->
                ZipOutputStream(out).use { zipOut ->
                    addFolderToZip(folder, "", zipOut)
                }
            }
            FileOpResult.Success(Unit)
        } catch (e: Exception) {
            FileOpResult.Failure("Unable to save file")
        }
    }

    private fun addFolderToZip(folder: DocumentFile, basePath: String, zipOut: ZipOutputStream) {
        for (child in folder.listFiles()) {
            val name = child.name ?: continue
            val entryPath = if (basePath.isEmpty()) name else "$basePath/$name"
            if (child.isDirectory) {
                addFolderToZip(child, entryPath, zipOut)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    zipOut.putNextEntry(ZipEntry(entryPath))
                    input.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "lua", "luau", "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
