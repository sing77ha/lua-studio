package com.luastudio.ai.ui.editor

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.LuaFile
import com.luastudio.ai.domain.model.RuntimeSettings
import com.luastudio.ai.domain.model.ScriptLanguage
import com.luastudio.ai.services.FileOpResult
import com.luastudio.ai.services.FileService
import com.luastudio.ai.services.LuaRuntimeService
import com.luastudio.ai.utils.computeAutoIndent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class EditorViewModel(
    private val fileService: FileService,
    private val preferencesManager: PreferencesManager,
    private val luaRuntimeService: LuaRuntimeService
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val autoSaveJobs = mutableMapOf<String, Job>()

    // ---------- Opening / creating tabs ----------

    fun newUntitledFile(name: String, language: ScriptLanguage) {
        val file = LuaFile(
            id = UUID.randomUUID().toString(),
            name = name,
            uriString = null,
            language = language,
            lastModifiedEpochMillis = System.currentTimeMillis()
        )
        val tab = EditorTab(
            file = file,
            textFieldValue = TextFieldValue(""),
            savedContent = ""
        )
        addTabAndFocus(tab)
    }

    fun openFile(uri: Uri, name: String, language: ScriptLanguage) {
        viewModelScope.launch {
            val existingIndex = _uiState.value.tabs.indexOfFirst { it.file.uriString == uri.toString() }
            if (existingIndex >= 0) {
                _uiState.value = _uiState.value.copy(currentTabIndex = existingIndex)
                return@launch
            }
            when (val result = fileService.readText(uri)) {
                is FileOpResult.Success -> {
                    val file = LuaFile(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        uriString = uri.toString(),
                        language = language,
                        lastModifiedEpochMillis = System.currentTimeMillis()
                    )
                    val tab = EditorTab(
                        file = file,
                        textFieldValue = TextFieldValue(result.value),
                        savedContent = result.value
                    )
                    addTabAndFocus(tab)
                    preferencesManager.pushRecentFile(
                        id = file.id, name = file.name, uri = file.uriString,
                        language = file.language.extension, modified = file.lastModifiedEpochMillis
                    )
                }
                is FileOpResult.Failure -> emitSnackbar(result.message)
            }
        }
    }

    private fun addTabAndFocus(tab: EditorTab) {
        val tabs = _uiState.value.tabs + tab
        _uiState.value = _uiState.value.copy(tabs = tabs, currentTabIndex = tabs.lastIndex)
    }

    fun switchTab(index: Int) {
        if (index in _uiState.value.tabs.indices) {
            _uiState.value = _uiState.value.copy(currentTabIndex = index, findReplace = FindReplaceState())
        }
    }

    fun requestCloseTab(index: Int) {
        val tab = _uiState.value.tabs.getOrNull(index) ?: return
        if (tab.isDirty) {
            _uiState.value = _uiState.value.copy(pendingCloseTabIndex = index)
        } else {
            forceCloseTab(index)
        }
    }

    fun dismissCloseConfirmation() {
        _uiState.value = _uiState.value.copy(pendingCloseTabIndex = null)
    }

    fun forceCloseTab(index: Int) {
        val state = _uiState.value
        val tabId = state.tabs.getOrNull(index)?.file?.id
        autoSaveJobs.remove(tabId)?.cancel()
        val newTabs = state.tabs.filterIndexed { i, _ -> i != index }
        val newCurrent = when {
            newTabs.isEmpty() -> -1
            index <= state.currentTabIndex -> (state.currentTabIndex - 1).coerceAtLeast(0)
            else -> state.currentTabIndex
        }
        _uiState.value = state.copy(tabs = newTabs, currentTabIndex = newCurrent, pendingCloseTabIndex = null)
    }

    // ---------- Editing ----------

    fun updateContent(newValue: TextFieldValue, autoIndentEnabled: Boolean, tabSize: Int, autoSaveEnabled: Boolean) {
        val index = _uiState.value.currentTabIndex
        val tab = _uiState.value.currentTab ?: return

        var finalValue = newValue
        val insertedNewline = newValue.text.length == tab.textFieldValue.text.length + 1 &&
            newValue.selection.collapsed &&
            newValue.selection.start > 0 &&
            newValue.text.getOrNull(newValue.selection.start - 1) == '\n'

        if (autoIndentEnabled && insertedNewline) {
            val cursor = newValue.selection.start
            val indent = computeAutoIndent(newValue.text.substring(0, cursor), tabSize)
            if (indent.isNotEmpty()) {
                val withIndent = newValue.text.substring(0, cursor) + indent + newValue.text.substring(cursor)
                finalValue = TextFieldValue(withIndent, TextRange(cursor + indent.length))
            }
        }

        val pushUndo = finalValue.text != tab.textFieldValue.text
        val updatedTab = tab.copy(
            textFieldValue = finalValue,
            undoStack = if (pushUndo) (tab.undoStack + tab.textFieldValue).takeLast(MAX_HISTORY) else tab.undoStack,
            redoStack = if (pushUndo) emptyList() else tab.redoStack
        )
        replaceTab(index, updatedTab)

        if (autoSaveEnabled && updatedTab.file.uriString != null) {
            scheduleAutoSave(updatedTab)
        }
    }

    fun insertAtCursor(text: String) {
        val index = _uiState.value.currentTabIndex
        val tab = _uiState.value.currentTab ?: return
        val current = tab.textFieldValue
        val start = current.selection.min
        val end = current.selection.max
        val newText = current.text.substring(0, start) + text + current.text.substring(end)
        val newCursor = start + text.length
        val newValue = TextFieldValue(newText, TextRange(newCursor))
        val updatedTab = tab.copy(
            textFieldValue = newValue,
            undoStack = (tab.undoStack + current).takeLast(MAX_HISTORY),
            redoStack = emptyList()
        )
        replaceTab(index, updatedTab)
    }

    fun selectAll() {
        val tab = _uiState.value.currentTab ?: return
        replaceCurrentTextFieldValue(tab.textFieldValue.copy(selection = TextRange(0, tab.textFieldValue.text.length)))
    }

    fun undo() {
        val index = _uiState.value.currentTabIndex
        val tab = _uiState.value.currentTab ?: return
        val previous = tab.undoStack.lastOrNull() ?: return
        val updatedTab = tab.copy(
            textFieldValue = previous,
            undoStack = tab.undoStack.dropLast(1),
            redoStack = tab.redoStack + tab.textFieldValue
        )
        replaceTab(index, updatedTab)
    }

    fun redo() {
        val index = _uiState.value.currentTabIndex
        val tab = _uiState.value.currentTab ?: return
        val next = tab.redoStack.lastOrNull() ?: return
        val updatedTab = tab.copy(
            textFieldValue = next,
            redoStack = tab.redoStack.dropLast(1),
            undoStack = tab.undoStack + tab.textFieldValue
        )
        replaceTab(index, updatedTab)
    }

    private fun replaceCurrentTextFieldValue(value: TextFieldValue) {
        val index = _uiState.value.currentTabIndex
        val tab = _uiState.value.currentTab ?: return
        replaceTab(index, tab.copy(textFieldValue = value))
    }

    private fun replaceTab(index: Int, tab: EditorTab) {
        val tabs = _uiState.value.tabs.toMutableList()
        if (index !in tabs.indices) return
        tabs[index] = tab
        _uiState.value = _uiState.value.copy(tabs = tabs)
    }

    // ---------- Saving ----------

    fun save() {
        val tab = _uiState.value.currentTab ?: return
        val uriString = tab.file.uriString
        if (uriString == null) {
            _uiState.value = _uiState.value.copy(pendingSaveAsForTabIndex = _uiState.value.currentTabIndex)
            return
        }
        performSave(tab, Uri.parse(uriString))
    }

    fun consumeSaveAsRequest() {
        _uiState.value = _uiState.value.copy(pendingSaveAsForTabIndex = null)
    }

    fun completeSaveAs(uri: Uri, displayName: String) {
        val index = _uiState.value.pendingSaveAsForTabIndex ?: return
        val tab = _uiState.value.tabs.getOrNull(index) ?: return
        val updatedFile = tab.file.copy(uriString = uri.toString(), name = displayName)
        val updatedTab = tab.copy(file = updatedFile)
        replaceTab(index, updatedTab)
        _uiState.value = _uiState.value.copy(pendingSaveAsForTabIndex = null)
        performSave(updatedTab, uri)
    }

    private fun performSave(tab: EditorTab, uri: Uri) {
        viewModelScope.launch {
            when (val result = fileService.writeText(uri, tab.textFieldValue.text)) {
                is FileOpResult.Success -> {
                    val index = _uiState.value.tabs.indexOfFirst { it.file.id == tab.file.id }
                    if (index >= 0) {
                        val savedTab = _uiState.value.tabs[index].copy(
                            savedContent = tab.textFieldValue.text,
                            file = tab.file.copy(lastModifiedEpochMillis = System.currentTimeMillis())
                        )
                        replaceTab(index, savedTab)
                        preferencesManager.pushRecentFile(
                            id = savedTab.file.id, name = savedTab.file.name, uri = savedTab.file.uriString,
                            language = savedTab.file.language.extension, modified = savedTab.file.lastModifiedEpochMillis
                        )
                    }
                    emitSnackbar("Saved successfully")
                }
                is FileOpResult.Failure -> emitSnackbar(result.message)
            }
        }
    }

    private fun scheduleAutoSave(tab: EditorTab) {
        autoSaveJobs[tab.file.id]?.cancel()
        autoSaveJobs[tab.file.id] = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            val uriString = tab.file.uriString ?: return@launch
            val latest = _uiState.value.tabs.find { it.file.id == tab.file.id } ?: return@launch
            performSave(latest, Uri.parse(uriString))
        }
    }

    // ---------- Find & Replace ----------

    fun toggleFindReplace() {
        val visible = !_uiState.value.findReplace.visible
        _uiState.value = _uiState.value.copy(
            findReplace = if (visible) _uiState.value.findReplace.copy(visible = true) else FindReplaceState()
        )
    }

    fun updateSearchQuery(query: String) {
        val text = _uiState.value.currentTab?.textFieldValue?.text.orEmpty()
        val matches = if (query.isEmpty()) emptyList() else Regex(Regex.escape(query))
            .findAll(text)
            .map { SearchMatch(it.range.first, it.range.last + 1) }
            .toList()
        _uiState.value = _uiState.value.copy(
            findReplace = _uiState.value.findReplace.copy(
                query = query,
                matches = matches,
                currentMatch = if (matches.isEmpty()) -1 else 0
            )
        )
        selectMatch(0)
    }

    fun updateReplaceQuery(replacement: String) {
        _uiState.value = _uiState.value.copy(findReplace = _uiState.value.findReplace.copy(replacement = replacement))
    }

    fun findNext() {
        val fr = _uiState.value.findReplace
        if (fr.matches.isEmpty()) return
        val next = (fr.currentMatch + 1) % fr.matches.size
        _uiState.value = _uiState.value.copy(findReplace = fr.copy(currentMatch = next))
        selectMatch(next)
    }

    fun findPrevious() {
        val fr = _uiState.value.findReplace
        if (fr.matches.isEmpty()) return
        val prev = if (fr.currentMatch <= 0) fr.matches.lastIndex else fr.currentMatch - 1
        _uiState.value = _uiState.value.copy(findReplace = fr.copy(currentMatch = prev))
        selectMatch(prev)
    }

    private fun selectMatch(matchIndex: Int) {
        val fr = _uiState.value.findReplace
        val match = fr.matches.getOrNull(matchIndex) ?: return
        val tab = _uiState.value.currentTab ?: return
        replaceCurrentTextFieldValue(tab.textFieldValue.copy(selection = TextRange(match.start, match.end)))
    }

    fun replaceCurrentMatch() {
        val fr = _uiState.value.findReplace
        val match = fr.matches.getOrNull(fr.currentMatch) ?: return
        val tab = _uiState.value.currentTab ?: return
        val newText = tab.textFieldValue.text.substring(0, match.start) + fr.replacement +
            tab.textFieldValue.text.substring(match.end)
        replaceCurrentTextFieldValue(TextFieldValue(newText, TextRange(match.start + fr.replacement.length)))
        updateSearchQuery(fr.query)
    }

    fun replaceAllMatches() {
        val fr = _uiState.value.findReplace
        val tab = _uiState.value.currentTab ?: return
        if (fr.query.isEmpty()) return
        val newText = tab.textFieldValue.text.replace(fr.query, fr.replacement)
        replaceCurrentTextFieldValue(TextFieldValue(newText, TextRange(0)))
        updateSearchQuery(fr.query)
    }

    // ---------- Go To Line ----------

    fun goToLine(lineNumber: Int) {
        val tab = _uiState.value.currentTab ?: return
        val lines = tab.textFieldValue.text.split("\n")
        val targetIndex = (lineNumber - 1).coerceIn(0, lines.lastIndex)
        val offset = lines.take(targetIndex).sumOf { it.length + 1 }
        replaceCurrentTextFieldValue(tab.textFieldValue.copy(selection = TextRange(offset)))
    }

    // ---------- Run / Stop / Console (offline Lua execution) ----------

    fun runCode(runtimeSettings: RuntimeSettings) {
        val tab = _uiState.value.currentTab ?: return
        if (_uiState.value.console.isRunning) return

        val startingLines = if (runtimeSettings.autoClearConsoleBeforeRun) emptyList() else _uiState.value.console.lines
        _uiState.value = _uiState.value.copy(
            console = ConsoleState(lines = startingLines, isRunning = true, isVisible = true)
        )

        val code = tab.textFieldValue.text
        viewModelScope.launch {
            val result = withTimeoutOrNull(runtimeSettings.executionTimeoutMs + 1000) {
                luaRuntimeService.execute(
                    code = code,
                    timeoutMillis = runtimeSettings.executionTimeoutMs,
                    outputLimitChars = runtimeSettings.outputLimitChars,
                    onOutput = { line -> appendConsoleLine(line) },
                    onError = { message -> appendConsoleLine("ERROR: $message") }
                )
            }
            if (result == null) {
                appendConsoleLine("ERROR: Execution timed out.")
            }
            appendConsoleLine("Process finished.")
            _uiState.value = _uiState.value.copy(console = _uiState.value.console.copy(isRunning = false))
        }
    }

    fun stopCode() {
        luaRuntimeService.stop()
    }

    fun clearConsole() {
        _uiState.value = _uiState.value.copy(console = _uiState.value.console.copy(lines = emptyList()))
    }

    fun toggleConsoleVisible() {
        _uiState.value = _uiState.value.copy(console = _uiState.value.console.copy(isVisible = !_uiState.value.console.isVisible))
    }

    private fun appendConsoleLine(line: String) {
        _uiState.value = _uiState.value.copy(
            console = _uiState.value.console.copy(lines = _uiState.value.console.lines + line)
        )
    }

    // ---------- Snackbar ----------

    private fun emitSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun consumeSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    companion object {
        private const val MAX_HISTORY = 100
        private const val AUTO_SAVE_DEBOUNCE_MS = 900L

        fun factory(
            fileService: FileService,
            preferencesManager: PreferencesManager,
            luaRuntimeService: LuaRuntimeService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(fileService, preferencesManager, luaRuntimeService) as T
            }
        }
    }
}
