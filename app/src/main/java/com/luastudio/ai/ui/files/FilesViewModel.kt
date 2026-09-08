package com.luastudio.ai.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.FileEntry
import com.luastudio.ai.services.FileManagerService
import com.luastudio.ai.services.FileOpResult
import com.luastudio.ai.services.FileService
import com.luastudio.ai.services.ZipService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MoveCopyState(val entryName: String, val sourcePath: List<String>, val isMove: Boolean)

data class FilesUiState(
    val rootUri: Uri? = null,
    val pathSegments: List<String> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    val pendingDelete: FileEntry? = null,
    val moveCopy: MoveCopyState? = null
) {
    val breadcrumbLabel: String get() = if (pathSegments.isEmpty()) "Root" else pathSegments.last()
}

class FilesViewModel(
    private val fileManagerService: FileManagerService,
    private val fileService: FileService,
    private val zipService: ZipService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    // ---------- Root folder ----------

    fun setRoot(uri: Uri, persistAsProject: Boolean = true) {
        _uiState.value = _uiState.value.copy(rootUri = uri, pathSegments = emptyList())
        load()
        if (persistAsProject) {
            viewModelScope.launch {
                preferencesManager.setCurrentRootFolderUri(uri.toString())
                val name = fileService.displayName(uri) ?: uri.lastPathSegment ?: "Project"
                preferencesManager.pushRecentProject(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    uri = uri.toString(),
                    modified = System.currentTimeMillis()
                )
            }
        }
    }

    fun restoreRootIfNeeded(savedUri: Uri) {
        if (_uiState.value.rootUri == null) {
            setRoot(savedUri, persistAsProject = false)
        }
    }

    // ---------- Navigation ----------

    fun navigateInto(folderName: String) {
        _uiState.value = _uiState.value.copy(pathSegments = _uiState.value.pathSegments + folderName)
        load()
    }

    fun navigateUp() {
        _uiState.value = _uiState.value.copy(pathSegments = _uiState.value.pathSegments.dropLast(1))
        load()
    }

    fun navigateToBreadcrumb(depth: Int) {
        _uiState.value = _uiState.value.copy(pathSegments = _uiState.value.pathSegments.take(depth))
        load()
    }

    fun refresh() = load()

    private fun currentFolder() = _uiState.value.rootUri
        ?.let { fileManagerService.rootDocument(it) }
        ?.let { fileManagerService.resolveFolder(it, _uiState.value.pathSegments) }

    private fun load() {
        val folder = currentFolder() ?: run {
            _uiState.value = _uiState.value.copy(entries = emptyList())
            return
        }
        _uiState.value = _uiState.value.copy(entries = fileManagerService.listEntries(folder))
    }

    // ---------- Create ----------

    fun createFile(name: String) {
        val folder = currentFolder() ?: return
        val unique = fileManagerService.uniqueImportName(folder, name)
        if (fileManagerService.createFile(folder, unique) != null) {
            emitSnackbar("File created")
            load()
        } else {
            emitSnackbar("Unable to save file")
        }
    }

    fun createFolder(name: String) {
        val folder = currentFolder() ?: return
        if (fileManagerService.createFolder(folder, name) != null) {
            load()
        } else {
            emitSnackbar("Unable to create folder")
        }
    }

    // ---------- Delete ----------

    fun requestDelete(entry: FileEntry) {
        _uiState.value = _uiState.value.copy(pendingDelete = entry)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val entry = _uiState.value.pendingDelete ?: return
        val folder = currentFolder() ?: return
        val success = fileManagerService.delete(folder, entry.name)
        _uiState.value = _uiState.value.copy(pendingDelete = null)
        if (success) load() else emitSnackbar("Unable to delete file")
    }

    // ---------- Rename ----------

    fun rename(entry: FileEntry, newName: String) {
        val folder = currentFolder() ?: return
        if (fileManagerService.rename(folder, entry.name, newName)) {
            load()
        } else {
            emitSnackbar("Unable to save file")
        }
    }

    // ---------- Duplicate ----------

    fun duplicate(entry: FileEntry) {
        if (entry.isDirectory) {
            emitSnackbar("Duplicating folders isn't supported yet")
            return
        }
        val folder = currentFolder() ?: return
        viewModelScope.launch {
            when (val bytes = fileService.readBytes(entry.uri)) {
                is FileOpResult.Success -> {
                    val newName = fileManagerService.uniqueDuplicateName(folder, entry.name)
                    val newUri = fileManagerService.createFile(folder, newName)
                    if (newUri != null) {
                        fileService.writeBytes(newUri, bytes.value)
                        load()
                    } else {
                        emitSnackbar("Unable to save file")
                    }
                }
                is FileOpResult.Failure -> emitSnackbar(bytes.message)
            }
        }
    }

    // ---------- Move / Copy (files only, within the same root tree) ----------

    fun startMove(entry: FileEntry) {
        _uiState.value = _uiState.value.copy(
            moveCopy = MoveCopyState(entry.name, _uiState.value.pathSegments, isMove = true)
        )
    }

    fun startCopy(entry: FileEntry) {
        _uiState.value = _uiState.value.copy(
            moveCopy = MoveCopyState(entry.name, _uiState.value.pathSegments, isMove = false)
        )
    }

    fun cancelMoveCopy() {
        _uiState.value = _uiState.value.copy(moveCopy = null)
    }

    fun confirmMoveCopyHere() {
        val state = _uiState.value.moveCopy ?: return
        val root = _uiState.value.rootUri?.let { fileManagerService.rootDocument(it) } ?: return
        val destinationPath = _uiState.value.pathSegments

        viewModelScope.launch {
            val sourceFolder = fileManagerService.resolveFolder(root, state.sourcePath)
            val sourceDoc = sourceFolder?.let { fileManagerService.findChild(it, state.entryName) }
            val destinationFolder = fileManagerService.resolveFolder(root, destinationPath)

            if (sourceFolder == null || sourceDoc == null || destinationFolder == null) {
                emitSnackbar("Unable to open file")
                _uiState.value = _uiState.value.copy(moveCopy = null)
                return@launch
            }
            if (sourceDoc.isDirectory) {
                emitSnackbar("Moving/copying folders isn't supported yet")
                _uiState.value = _uiState.value.copy(moveCopy = null)
                return@launch
            }
            if (sourcePath(state, destinationPath)) {
                emitSnackbar("Choose a different destination")
                return@launch
            }

            when (val bytes = fileService.readBytes(sourceDoc.uri)) {
                is FileOpResult.Success -> {
                    val newName = fileManagerService.uniqueImportName(destinationFolder, state.entryName)
                    val newUri = fileManagerService.createFile(destinationFolder, newName)
                    if (newUri != null) {
                        fileService.writeBytes(newUri, bytes.value)
                        if (state.isMove) {
                            fileManagerService.delete(sourceFolder, state.entryName)
                        }
                        emitSnackbar(if (state.isMove) "Moved" else "Copied")
                    } else {
                        emitSnackbar("Unable to save file")
                    }
                }
                is FileOpResult.Failure -> emitSnackbar(bytes.message)
            }
            _uiState.value = _uiState.value.copy(moveCopy = null)
            load()
        }
    }

    private fun sourcePath(state: MoveCopyState, destination: List<String>) =
        state.sourcePath == destination

    // ---------- Import ----------

    fun importFiles(uris: List<Uri>) {
        val folder = currentFolder() ?: return
        viewModelScope.launch {
            var importedCount = 0
            for (uri in uris) {
                val name = fileService.displayName(uri) ?: continue
                if (name.endsWith(".zip", ignoreCase = true)) {
                    when (zipService.extractZip(uri, folder)) {
                        is FileOpResult.Success -> importedCount++
                        is FileOpResult.Failure -> emitSnackbar("Unable to open file")
                    }
                } else {
                    when (val bytes = fileService.readBytes(uri)) {
                        is FileOpResult.Success -> {
                            val uniqueName = fileManagerService.uniqueImportName(folder, name)
                            val newUri = fileManagerService.createFile(folder, uniqueName, mimeTypeFor(uniqueName))
                            if (newUri != null) {
                                fileService.writeBytes(newUri, bytes.value)
                                importedCount++
                            }
                        }
                        is FileOpResult.Failure -> emitSnackbar("Unable to open file")
                    }
                }
            }
            if (importedCount > 0) emitSnackbar("Imported $importedCount item(s)")
            load()
        }
    }

    // ---------- Export ----------

    fun exportFile(entry: FileEntry, destinationUri: Uri) {
        viewModelScope.launch {
            when (val bytes = fileService.readBytes(entry.uri)) {
                is FileOpResult.Success -> {
                    when (fileService.writeBytes(destinationUri, bytes.value)) {
                        is FileOpResult.Success -> emitSnackbar("Saved successfully")
                        is FileOpResult.Failure -> emitSnackbar("Unable to save file")
                    }
                }
                is FileOpResult.Failure -> emitSnackbar(bytes.message)
            }
        }
    }

    fun exportProjectZip(destinationUri: Uri) {
        val folder = currentFolder() ?: return
        viewModelScope.launch {
            when (zipService.createZipFromFolder(folder, destinationUri)) {
                is FileOpResult.Success -> emitSnackbar("Saved successfully")
                is FileOpResult.Failure -> emitSnackbar("Unable to save file")
            }
        }
    }

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "lua", "luau", "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    // ---------- Snackbar ----------

    private fun emitSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun consumeSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    companion object {
        fun factory(
            fileManagerService: FileManagerService,
            fileService: FileService,
            zipService: ZipService,
            preferencesManager: PreferencesManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FilesViewModel(fileManagerService, fileService, zipService, preferencesManager) as T
            }
        }
    }
}
