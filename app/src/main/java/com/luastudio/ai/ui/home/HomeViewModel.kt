package com.luastudio.ai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.LuaFile
import com.luastudio.ai.domain.model.LuaProject
import com.luastudio.ai.domain.model.ScriptLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray

data class HomeUiState(
    val recentFiles: List<LuaFile> = emptyList(),
    val recentProjects: List<LuaProject> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRecents()
    }

    private fun observeRecents() {
        viewModelScope.launch {
            preferencesManager.recentFilesJson.combine(preferencesManager.recentProjectsJson) { filesJson, projectsJson ->
                HomeUiState(
                    recentFiles = parseRecentFiles(filesJson),
                    recentProjects = parseRecentProjects(projectsJson),
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Sets this project's folder as the Files screen root, then the caller navigates there. */
    fun openProject(project: LuaProject) {
        viewModelScope.launch {
            preferencesManager.setCurrentRootFolderUri(project.rootUriString)
        }
    }

    private fun parseRecentFiles(json: String): List<LuaFile> {
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LuaFile(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    uriString = obj.optString("uri").takeIf { it.isNotBlank() && it != "null" },
                    language = ScriptLanguage.fromExtension(obj.optString("language", "lua")),
                    lastModifiedEpochMillis = obj.optLong("modified"),
                    isModified = false
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRecentProjects(json: String): List<LuaProject> {
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LuaProject(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    rootUriString = obj.optString("uri"),
                    lastOpenedEpochMillis = obj.optLong("modified")
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        fun factory(preferencesManager: PreferencesManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(preferencesManager) as T
            }
        }
    }
}
