package com.luastudio.ai.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luastudio.ai.data.files.PendingFileOpenHolder
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.AppearanceSettings
import com.luastudio.ai.domain.model.EditorSettings
import com.luastudio.ai.domain.model.EditorTheme
import com.luastudio.ai.domain.model.RuntimeSettings
import com.luastudio.ai.domain.model.ScriptLanguage
import com.luastudio.ai.services.FileService
import com.luastudio.ai.services.LuaRuntimeService
import com.luastudio.ai.ui.components.NewFileDialog
import com.luastudio.ai.ui.theme.EditorFontFamily
import com.luastudio.ai.utils.LuaVisualTransformation
import com.luastudio.ai.utils.paletteFor

private val LUA_TOOLBAR_TOKENS = listOf(
    "Tab", "local", "function", "if", "then", "else", "end", "for", "while", "do",
    "return", "true", "false", "nil", "=", ".", ":", "(", ")", "[", "]", "{", "}", "\"", "'", ","
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    val fileService = remember { FileService(context.applicationContext) }
    val luaRuntimeService = remember { LuaRuntimeService() }
    val viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember { EditorViewModel.factory(fileService, preferencesManager, luaRuntimeService) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val editorSettings by preferencesManager.editorSettings.collectAsState(initial = EditorSettings())
    val appearance by preferencesManager.appearanceSettings.collectAsState(initial = AppearanceSettings())
    val runtimeSettings by preferencesManager.runtimeSettings.collectAsState(initial = RuntimeSettings())
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Consume anything Home/Files queued up for us (a new file to create, or
    // an existing file to open) exactly once when this screen first appears.
    LaunchedEffect(Unit) {
        val pending = PendingFileOpenHolder.consume()
        when {
            pending != null && pending.uriString != null ->
                viewModel.openFile(Uri.parse(pending.uriString), pending.name, pending.language)
            pending != null ->
                viewModel.newUntitledFile(pending.name, pending.language)
            uiState.tabs.isEmpty() ->
                viewModel.newUntitledFile("untitled.lua", ScriptLanguage.LUA)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val displayName = fileService.displayName(uri)
                ?: uiState.tabs.getOrNull(uiState.pendingSaveAsForTabIndex ?: -1)?.file?.name
                ?: "untitled.lua"
            viewModel.completeSaveAs(uri, displayName)
        } else {
            viewModel.consumeSaveAsRequest()
        }
    }

    LaunchedEffect(uiState.pendingSaveAsForTabIndex) {
        val index = uiState.pendingSaveAsForTabIndex ?: return@LaunchedEffect
        val suggestedName = uiState.tabs.getOrNull(index)?.file?.name ?: "untitled.lua"
        saveAsLauncher.launch(suggestedName)
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { file ->
                showNewFileDialog = false
                viewModel.newUntitledFile(file.name, file.language)
            }
        )
    }

    if (showGoToLineDialog) {
        GoToLineDialog(
            onDismiss = { showGoToLineDialog = false },
            onGo = { line ->
                showGoToLineDialog = false
                viewModel.goToLine(line)
            }
        )
    }

    uiState.pendingCloseTabIndex?.let { index ->
        val tabName = uiState.tabs.getOrNull(index)?.file?.name.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissCloseConfirmation,
            title = { Text("Save changes?") },
            text = { Text("\"$tabName\" has unsaved changes.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.switchTab(index)
                    viewModel.save()
                    viewModel.forceCloseTab(index)
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.forceCloseTab(index) }) { Text("Don't Save") }
                    TextButton(onClick = viewModel::dismissCloseConfirmation) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.currentTab != null) {
                if (uiState.console.isRunning) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::stopCode,
                        icon = { Icon(Icons.Filled.Stop, contentDescription = null) },
                        text = { Text("Stop") },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.runCode(runtimeSettings) },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        text = { Text("Run") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabBar(
                uiState = uiState,
                onSelectTab = viewModel::switchTab,
                onCloseTab = viewModel::requestCloseTab,
                onAddTab = { showNewFileDialog = true }
            )

            val currentTab = uiState.currentTab
            if (currentTab == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                FileStatusBar(
                    fileName = currentTab.file.name,
                    isDirty = currentTab.isDirty,
                    canUndo = currentTab.undoStack.isNotEmpty(),
                    canRedo = currentTab.redoStack.isNotEmpty(),
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onToggleFind = viewModel::toggleFindReplace,
                    onSave = viewModel::save,
                    showOverflowMenu = showOverflowMenu,
                    onOverflowToggle = { showOverflowMenu = it },
                    onGoToLine = { showOverflowMenu = false; showGoToLineDialog = true },
                    onSaveAs = {
                        showOverflowMenu = false
                        viewModel.save()
                    },
                    onToggleConsole = { showOverflowMenu = false; viewModel.toggleConsoleVisible() }
                )

                if (uiState.findReplace.visible) {
                    FindReplaceBar(
                        state = uiState.findReplace,
                        onQueryChange = viewModel::updateSearchQuery,
                        onReplaceChange = viewModel::updateReplaceQuery,
                        onNext = viewModel::findNext,
                        onPrevious = viewModel::findPrevious,
                        onReplace = viewModel::replaceCurrentMatch,
                        onReplaceAll = viewModel::replaceAllMatches,
                        onClose = viewModel::toggleFindReplace
                    )
                }

                CodeEditorArea(
                    modifier = Modifier.weight(1f),
                    tab = currentTab,
                    editorSettings = editorSettings,
                    editorTheme = appearance.editorTheme,
                    onValueChange = {
                        viewModel.updateContent(
                            it,
                            autoIndentEnabled = editorSettings.autoIndent,
                            tabSize = editorSettings.tabSize,
                            autoSaveEnabled = editorSettings.autoSave
                        )
                    }
                )

                if (uiState.console.isVisible) {
                    ConsolePanel(
                        lines = uiState.console.lines,
                        isRunning = uiState.console.isRunning,
                        onClear = viewModel::clearConsole,
                        onClose = viewModel::toggleConsoleVisible
                    )
                }

                LuaToolbar(
                    onTokenSelected = { token ->
                        val insertion = when (token) {
                            "Tab" -> " ".repeat(editorSettings.tabSize)
                            else -> token
                        }
                        viewModel.insertAtCursor(insertion)
                    }
                )
            }
        }
    }
}

@Composable
private fun TabBar(
    uiState: EditorUiState,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(uiState.tabs.size) { index ->
                val tab = uiState.tabs[index]
                val selected = index == uiState.currentTabIndex
                Row(
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab.isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = tab.file.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close ${tab.file.name}",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCloseTab(index) }
                    )
                }
            }
            item {
                IconButton(onClick = onAddTab) {
                    Icon(Icons.Filled.Add, contentDescription = "New file")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileStatusBar(
    fileName: String,
    isDirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleFind: () -> Unit,
    onSave: () -> Unit,
    showOverflowMenu: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    onGoToLine: () -> Unit,
    onSaveAs: () -> Unit,
    onToggleConsole: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (if (isDirty) "\u25CF " else "") + fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isDirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.Filled.Redo, contentDescription = "Redo")
        }
        IconButton(onClick = onToggleFind) {
            Icon(Icons.Filled.Search, contentDescription = "Find & Replace")
        }
        TextButton(onClick = onSave) { Text("Save") }
        Box {
            IconButton(onClick = { onOverflowToggle(true) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { onOverflowToggle(false) }) {
                DropdownMenuItem(text = { Text("Go To Line") }, onClick = onGoToLine)
                DropdownMenuItem(text = { Text("Save As") }, onClick = onSaveAs)
                DropdownMenuItem(text = { Text("Toggle Console") }, onClick = onToggleConsole)
            }
        }
    }
}

@Composable
private fun FindReplaceBar(
    state: FindReplaceState,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Find") }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.matches.isEmpty()) "0/0" else "${state.currentMatch + 1}/${state.matches.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close find") }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.replacement,
                    onValueChange = onReplaceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Replace") }
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onPrevious) { Text("Previous") }
                TextButton(onClick = onNext) { Text("Next") }
                TextButton(onClick = onReplace) { Text("Replace") }
                TextButton(onClick = onReplaceAll) { Text("Replace All") }
            }
        }
    }
}

@Composable
private fun CodeEditorArea(
    modifier: Modifier,
    tab: EditorTab,
    editorSettings: EditorSettings,
    editorTheme: EditorTheme,
    onValueChange: (TextFieldValue) -> Unit
) {
    val palette = paletteFor(editorTheme)
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = tab.textFieldValue.text.count { it == '\n' } + 1
    val lineHeight = (editorSettings.fontSize + 8).sp
    val backgroundColor = if (editorTheme == EditorTheme.LIGHT) Color(0xFFFFFFFF) else Color(0xFF16161A)

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(verticalScroll)
    ) {
        if (editorSettings.showLineNumbers) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (line in 1..lineCount) {
                    Text(
                        text = line.toString(),
                        color = palette.comment,
                        fontFamily = EditorFontFamily,
                        fontSize = editorSettings.fontSize.sp,
                        lineHeight = lineHeight
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 24.dp, end = 16.dp)
                .let { if (!editorSettings.wordWrap) it.horizontalScroll(horizontalScroll) else it }
        ) {
            BasicTextField(
                value = tab.textFieldValue,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = palette.plain,
                    fontFamily = EditorFontFamily,
                    fontSize = editorSettings.fontSize.sp,
                    lineHeight = lineHeight
                ),
                visualTransformation = if (editorSettings.syntaxHighlight) {
                    LuaVisualTransformation(palette)
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(palette.plain),
                modifier = if (editorSettings.wordWrap) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(2000.dp)
                }
            )
        }
    }
}

@Composable
private fun ConsolePanel(
    lines: List<String>,
    isRunning: Boolean,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Surface(color = Color(0xFF0D0D10), tonalElevation = 3.dp) {
        Column(modifier = Modifier.heightIn(min = 120.dp, max = 220.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Console",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE6E6EA),
                    modifier = Modifier.weight(1f)
                )
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE6E6EA)
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear console", tint = Color(0xFFE6E6EA))
                }
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(lines.joinToString("\n")))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy console", tint = Color(0xFFE6E6EA))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close console", tint = Color(0xFFE6E6EA))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontFamily = EditorFontFamily,
                        fontSize = 13.sp,
                        color = if (line.startsWith("ERROR")) Color(0xFFFF6B6B) else Color(0xFFD8DEE9)
                    )
                }
            }
        }
    }
}

@Composable
private fun LuaToolbar(onTokenSelected: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LUA_TOOLBAR_TOKENS.forEach { token ->
                ToolbarChip(label = token, onClick = { onTokenSelected(token) })
            }
        }
    }
}

@Composable
private fun ToolbarChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(40.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontFamily = EditorFontFamily, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GoToLineDialog(onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var lineText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go To Line") },
        text = {
            OutlinedTextField(
                value = lineText,
                onValueChange = { input -> lineText = input.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Line number") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = lineText.toIntOrNull() != null,
                onClick = { lineText.toIntOrNull()?.let(onGo) }
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
