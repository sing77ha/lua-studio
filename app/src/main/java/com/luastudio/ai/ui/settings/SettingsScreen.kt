package com.luastudio.ai.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luastudio.ai.data.storage.PreferencesManager
import com.luastudio.ai.domain.model.AccentColor
import com.luastudio.ai.domain.model.EditorTheme
import com.luastudio.ai.domain.model.ThemeMode
import kotlinx.coroutines.launch

private val THAI_LOCALE_TAG = "th"

@Composable
fun SettingsScreen(preferencesManager: PreferencesManager) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember { SettingsViewModel.factory(preferencesManager) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "EDITOR") {
                    StepperRow(
                        label = "Font Size",
                        valueLabel = "${uiState.editor.fontSize}sp",
                        onDecrement = {
                            viewModel.updateEditorSettings { it.copy(fontSize = (it.fontSize - 1).coerceIn(10, 28)) }
                        },
                        onIncrement = {
                            viewModel.updateEditorSettings { it.copy(fontSize = (it.fontSize + 1).coerceIn(10, 28)) }
                        }
                    )
                    StepperRow(
                        label = "Tab Size",
                        valueLabel = "${uiState.editor.tabSize} spaces",
                        onDecrement = {
                            viewModel.updateEditorSettings { it.copy(tabSize = (it.tabSize - 1).coerceIn(2, 8)) }
                        },
                        onIncrement = {
                            viewModel.updateEditorSettings { it.copy(tabSize = (it.tabSize + 1).coerceIn(2, 8)) }
                        }
                    )
                    SwitchRow("Word Wrap", uiState.editor.wordWrap) { checked ->
                        viewModel.updateEditorSettings { it.copy(wordWrap = checked) }
                    }
                    SwitchRow("Line Numbers", uiState.editor.showLineNumbers) { checked ->
                        viewModel.updateEditorSettings { it.copy(showLineNumbers = checked) }
                    }
                    SwitchRow("Auto Indent", uiState.editor.autoIndent) { checked ->
                        viewModel.updateEditorSettings { it.copy(autoIndent = checked) }
                    }
                    SwitchRow("Syntax Highlight", uiState.editor.syntaxHighlight) { checked ->
                        viewModel.updateEditorSettings { it.copy(syntaxHighlight = checked) }
                    }
                    SwitchRow("Auto Save", uiState.editor.autoSave) { checked ->
                        viewModel.updateEditorSettings { it.copy(autoSave = checked) }
                    }
                    SwitchRow("Format on Save", uiState.editor.formatOnSave, isLast = true) { checked ->
                        viewModel.updateEditorSettings { it.copy(formatOnSave = checked) }
                    }
                }
            }

            item {
                SettingsSection(title = "APPEARANCE") {
                    ChoiceRow(
                        label = "Theme",
                        options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        selectedIndex = ThemeMode.entries.indexOf(uiState.appearance.themeMode)
                    ) { index ->
                        viewModel.updateAppearanceSettings { it.copy(themeMode = ThemeMode.entries[index]) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Accent Color",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    AccentColorRow(
                        selected = uiState.appearance.accentColor,
                        onSelect = { color -> viewModel.updateAppearanceSettings { it.copy(accentColor = color) } }
                    )
                    Spacer(Modifier.height(12.dp))
                    ChoiceRow(
                        label = "Editor Theme",
                        options = EditorTheme.entries.map { it.label },
                        selectedIndex = EditorTheme.entries.indexOf(uiState.appearance.editorTheme),
                        isLast = true
                    ) { index ->
                        viewModel.updateAppearanceSettings { it.copy(editorTheme = EditorTheme.entries[index]) }
                    }
                }
            }

            item {
                SettingsSection(title = "RUNTIME") {
                    StepperRow(
                        label = "Execution Timeout",
                        valueLabel = "${uiState.runtime.executionTimeoutMs / 1000}s",
                        onDecrement = {
                            viewModel.updateRuntimeSettings {
                                it.copy(executionTimeoutMs = (it.executionTimeoutMs - 1000).coerceIn(1000, 30000))
                            }
                        },
                        onIncrement = {
                            viewModel.updateRuntimeSettings {
                                it.copy(executionTimeoutMs = (it.executionTimeoutMs + 1000).coerceIn(1000, 30000))
                            }
                        }
                    )
                    StepperRow(
                        label = "Output Limit",
                        valueLabel = "${uiState.runtime.outputLimitChars / 1000}k chars",
                        onDecrement = {
                            viewModel.updateRuntimeSettings {
                                it.copy(outputLimitChars = (it.outputLimitChars - 5000).coerceIn(5000, 100000))
                            }
                        },
                        onIncrement = {
                            viewModel.updateRuntimeSettings {
                                it.copy(outputLimitChars = (it.outputLimitChars + 5000).coerceIn(5000, 100000))
                            }
                        }
                    )
                    SwitchRow(
                        "Auto Clear Console Before Run",
                        uiState.runtime.autoClearConsoleBeforeRun,
                        isLast = true
                    ) { checked ->
                        viewModel.updateRuntimeSettings { it.copy(autoClearConsoleBeforeRun = checked) }
                    }
                }
            }

            item {
                SettingsSection(title = "FILES") {
                    SwitchRow("Confirm Delete", uiState.confirmDelete, isLast = true) { checked ->
                        viewModel.setConfirmDelete(checked)
                    }
                }
            }

            item {
                SettingsSection(title = "LANGUAGE") {
                    SettingsRow(
                        label = "App Language",
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        },
                        isLast = true,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Per-app language needs Android 13+. On this version, change the " +
                                            "device's system language to Thai ($THAI_LOCALE_TAG) in phone Settings instead."
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "ABOUT") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("LUA STUDIO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Version 0.2.0-offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            "Fully offline. No AI API, no account, no network calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, isLast: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(
        label = label,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        isLast = isLast,
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun StepperRow(label: String, valueLabel: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) { Text("–", style = MaterialTheme.typography.titleLarge) }
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = onIncrement) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    isLast: Boolean = false,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isLast) 4.dp else 0.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(if (isLast) 8.dp else 4.dp))
    }
}

private fun accentColorValue(accent: AccentColor): Color = when (accent) {
    AccentColor.BLUE -> Color(0xFF4C8DFF)
    AccentColor.PURPLE -> Color(0xFF9D6CFF)
    AccentColor.GREEN -> Color(0xFF3FC97F)
    AccentColor.ORANGE -> Color(0xFFFF9C4C)
    AccentColor.RED -> Color(0xFFFF5C6C)
}

@Composable
private fun AccentColorRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccentColor.entries.forEach { color ->
            val isSelected = color == selected
            Surface(
                onClick = { onSelect(color) },
                shape = CircleShape,
                color = accentColorValue(color),
                modifier = Modifier.size(36.dp)
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    trailing: @Composable () -> Unit,
    isLast: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}
