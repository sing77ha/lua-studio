package com.luastudio.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.AppearanceSettings
import com.luastudio.ai.domain.model.EditorSettings
import com.luastudio.ai.domain.model.RuntimeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val editor: EditorSettings = EditorSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val runtime: RuntimeSettings = RuntimeSettings(),
    val confirmDelete: Boolean = true
)

class SettingsViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferencesManager.editorSettings,
                preferencesManager.appearanceSettings,
                preferencesManager.runtimeSettings,
                preferencesManager.confirmDelete
            ) { editor, appearance, runtime, confirmDelete ->
                SettingsUiState(editor, appearance, runtime, confirmDelete)
            }.collect { _uiState.value = it }
        }
    }

    fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch {
            preferencesManager.updateEditorSettings(transform(_uiState.value.editor))
        }
    }

    fun updateAppearanceSettings(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch {
            preferencesManager.updateAppearanceSettings(transform(_uiState.value.appearance))
        }
    }

    fun updateRuntimeSettings(transform: (RuntimeSettings) -> RuntimeSettings) {
        viewModelScope.launch {
            preferencesManager.updateRuntimeSettings(transform(_uiState.value.runtime))
        }
    }

    fun setConfirmDelete(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setConfirmDelete(value)
        }
    }

    companion object {
        fun factory(preferencesManager: PreferencesManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(preferencesManager) as T
            }
        }
    }
}
