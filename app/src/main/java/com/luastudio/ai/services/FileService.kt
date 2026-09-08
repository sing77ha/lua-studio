package com.luastudio.ai.services

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class FileOpResult<out T> {
    data class Success<T>(val value: T) : FileOpResult<T>()
    data class Failure(val message: String) : FileOpResult<Nothing>()
}

/**
 * All real file I/O goes through here so Editor, Files, and the ZIP service
 * share one implementation. Uses Storage Access Framework Uris exclusively —
 * no raw filesystem paths — per the spec's ANDROID STORAGE section.
 */
class FileService(private val context: Context) {

    suspend fun readText(uri: Uri): FileOpResult<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } ?: return@withContext FileOpResult.Failure("Unable to open file")
            FileOpResult.Success(text)
        } catch (e: Exception) {
            FileOpResult.Failure("Unable to open file")
        }
    }

    suspend fun writeText(uri: Uri, content: String): FileOpResult<Unit> =
        writeBytes(uri, content.toByteArray(Charsets.UTF_8))

    suspend fun readBytes(uri: Uri): FileOpResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext FileOpResult.Failure("Unable to open file")
            FileOpResult.Success(bytes)
        } catch (e: Exception) {
            FileOpResult.Failure("Unable to open file")
        }
    }

    suspend fun writeBytes(uri: Uri, bytes: ByteArray): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: return@withContext FileOpResult.Failure("Unable to save file")
            FileOpResult.Success(Unit)
        } catch (e: Exception) {
            FileOpResult.Failure("Unable to save file")
        }
    }

    fun querySizeBytes(uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun displayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
