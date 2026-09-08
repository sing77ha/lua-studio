package com.luastudio.ai.ui.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.FileEntry
import com.luastudio.ai.domain.model.LuaFile
import com.luastudio.ai.domain.model.ScriptLanguage
import com.luastudio.ai.services.FileManagerService
import com.luastudio.ai.services.FileService
import com.luastudio.ai.services.ZipService
import com.luastudio.ai.ui.components.NewFileDialog
import com.luastudio.ai.utils.formatRelativeTime
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    preferencesManager: PreferencesManager,
    onOpenFileInEditor: (LuaFile) -> Unit
) {
    val context = LocalContext.current
    val fileService = remember { FileService(context.applicationContext) }
    val fileManagerService = remember { FileManagerService(context.applicationContext) }
    val zipService = remember { ZipService(context.applicationContext, fileManagerService, fileService) }
    val viewModel: FilesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember { FilesViewModel.factory(fileManagerService, fileService, zipService, preferencesManager) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val savedRootUri by preferencesManager.currentRootFolderUri.collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var actionSheetTarget by remember { mutableStateOf<FileEntry?>(null) }
    var exportTarget by remember { mutableStateOf<FileEntry?>(null) }

    LaunchedEffect(savedRootUri) {
        savedRootUri?.let { viewModel.restoreRootIfNeeded(Uri.parse(it)) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setRoot(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importFiles(uris) }

    val exportProjectZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportProjectZip(it) } }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) viewModel.exportFile(target, uri)
        exportTarget = null
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { file ->
                showNewFileDialog = false
                viewModel.createFile(file.name)
            }
        )
    }

    if (showNewFolderDialog) {
        SimpleNameDialog(
            title = "New Folder",
            label = "Folder name",
            initialValue = "",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    renameTarget?.let { entry ->
        SimpleNameDialog(
            title = "Rename",
            label = "Name",
            initialValue = entry.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(entry, name)
            }
        )
    }

    uiState.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete \"${entry.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") } }
        )
    }

    actionSheetTarget?.let { entry ->
        FileActionSheet(
            entry = entry,
            onDismiss = { actionSheetTarget = null },
            onOpen = {
                actionSheetTarget = null
                onOpenFileInEditor(
                    LuaFile(
                        id = entry.uri.toString(),
                        name = entry.name,
                        uriString = entry.uri.toString(),
                        language = ScriptLanguage.fromExtension(entry.extension),
                        lastModifiedEpochMillis = entry.lastModifiedEpochMillis
                    )
                )
            },
            onRename = { actionSheetTarget = null; renameTarget = entry },
            onDuplicate = { actionSheetTarget = null; viewModel.duplicate(entry) },
            onDelete = { actionSheetTarget = null; viewModel.requestDelete(entry) },
            onShare = {
                actionSheetTarget = null
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, entry.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
            },
            onExport = {
                actionSheetTarget = null
                exportTarget = entry
                exportFileLauncher.launch(entry.name)
            },
            onMove = { actionSheetTarget = null; viewModel.startMove(entry) },
            onCopy = { actionSheetTarget = null; viewModel.startCopy(entry) }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.rootUri == null) {
                EmptyFilesState(onOpenFolder = { openTreeLauncher.launch(null) })
            } else {
                BreadcrumbBar(
                    pathSegments = uiState.pathSegments,
                    onNavigateUp = viewModel::navigateUp,
                    onNavigateToBreadcrumb = viewModel::navigateToBreadcrumb,
                    onNewFile = { showNewFileDialog = true },
                    onNewFolder = { showNewFolderDialog = true },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExportProjectZip = {
                        val suggested = uiState.pathSegments.lastOrNull() ?: "project"
                        exportProjectZipLauncher.launch("$suggested.zip")
                    }
                )

                if (uiState.entries.isEmpty()) {
                    EmptyFolderState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.entries, key = { it.uri.toString() }) { entry ->
                            FileRow(
                                entry = entry,
                                onClick = {
                                    if (entry.isDirectory) {
                                        viewModel.navigateInto(entry.name)
                                    } else {
                                        actionSheetTarget = entry
                                    }
                                },
                                onLongClick = { actionSheetTarget = entry }
                            )
                        }
                    }
                }

                uiState.moveCopy?.let { moveCopy ->
                    MoveCopyBar(
                        entryName = moveCopy.entryName,
                        isMove = moveCopy.isMove,
                        onConfirm = viewModel::confirmMoveCopyHere,
                        onCancel = viewModel::cancelMoveCopy
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFilesState(onOpenFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "No folder open",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Choose a folder to browse and manage your Lua files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        androidx.compose.material3.Button(onClick = onOpenFolder) { Text("Open Folder") }
    }
}

@Composable
private fun EmptyFolderState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No files yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BreadcrumbBar(
    pathSegments: List<String>,
    onNavigateUp: () -> Unit,
    onNavigateToBreadcrumb: (Int) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImport: () -> Unit,
    onExportProjectZip: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pathSegments.isNotEmpty()) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Up")
                    }
                }
                Text(
                    text = "Root" + pathSegments.joinToString("") { " / $it" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToBreadcrumb(0) },
                    maxLines = 1
                )
                IconButton(onClick = onNewFile) { Icon(Icons.Filled.Add, contentDescription = "New File") }
                IconButton(onClick = onNewFolder) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder") }
                IconButton(onClick = onImport) { Icon(Icons.Filled.UploadFile, contentDescription = "Import") }
                IconButton(onClick = onExportProjectZip) { Icon(Icons.Filled.FolderZip, contentDescription = "Export Project ZIP") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        (if (entry.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                            .copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = if (entry.isDirectory) {
                        "Folder"
                    } else {
                        "${formatFileSize(entry.sizeBytes)} · ${formatRelativeTime(entry.lastModifiedEpochMillis)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MoveCopyBar(entryName: String, isMove: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${if (isMove) "Move" else "Copy"} \"$entryName\" here?",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onConfirm) { Text(if (isMove) "Move Here" else "Copy Here") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionSheet(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (!entry.isDirectory) {
                SheetItem("Open", onOpen)
            }
            SheetItem("Rename", onRename)
            if (!entry.isDirectory) {
                SheetItem("Duplicate", onDuplicate)
                SheetItem("Move", onMove)
                SheetItem("Copy", onCopy)
            }
            SheetItem("Delete", onDelete)
            if (!entry.isDirectory) {
                SheetItem("Share", onShare)
                SheetItem("Export", onExport)
            }
        }
    }
}

@Composable
private fun SheetItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SimpleNameDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("#.#").format(kb)} KB"
    val mb = kb / 1024.0
    return "${DecimalFormat("#.#").format(mb)} MB"
}
