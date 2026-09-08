package com.luastudio.ai.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import com.luastudio.ai.domain.model.LuaFile

data class EditorTab(
    val file: LuaFile,
    val textFieldValue: TextFieldValue,
    val savedContent: String,
    val undoStack: List<TextFieldValue> = emptyList(),
    val redoStack: List<TextFieldValue> = emptyList()
) {
    val isDirty: Boolean get() = textFieldValue.text != savedContent
}

data class SearchMatch(val start: Int, val end: Int)

data class FindReplaceState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val currentMatch: Int = -1
)

data class ConsoleState(
    val lines: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val isVisible: Boolean = false
)

data class EditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val currentTabIndex: Int = -1,
    val findReplace: FindReplaceState = FindReplaceState(),
    val pendingCloseTabIndex: Int? = null,
    val pendingSaveAsForTabIndex: Int? = null,
    val snackbarMessage: String? = null,
    val console: ConsoleState = ConsoleState()
) {
    val currentTab: EditorTab? get() = tabs.getOrNull(currentTabIndex)
}
