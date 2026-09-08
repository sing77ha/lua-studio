package com.luastudio.ai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.luastudio.ai.domain.model.LuaFile
import com.luastudio.ai.domain.model.ScriptLanguage
import com.luastudio.ai.utils.sanitizeFileBaseName
import java.util.UUID

/**
 * "New Lua File" dialog per spec: filename field, Lua/Luau type choice,
 * Cancel/Create. Filenames are sanitized and always end in the extension
 * for the selected language, regardless of what the user typed.
 */
@Composable
fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (LuaFile) -> Unit
) {
    var rawName by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(ScriptLanguage.LUA) }
    val cleanedBaseName = sanitizeFileBaseName(rawName)
    val isValid = cleanedBaseName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Lua File") },
        text = {
            Column {
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = language == ScriptLanguage.LUA, onClick = { language = ScriptLanguage.LUA })
                    Text("Lua (.lua)", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(selected = language == ScriptLanguage.LUAU, onClick = { language = ScriptLanguage.LUAU })
                    Text("Luau (.luau)")
                }
                if (rawName.isNotBlank() && !isValid) {
                    Text(
                        text = "Enter a valid file name",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val file = LuaFile(
                        id = UUID.randomUUID().toString(),
                        name = "$cleanedBaseName.${language.extension}",
                        uriString = null,
                        language = language,
                        lastModifiedEpochMillis = System.currentTimeMillis()
                    )
                    onCreate(file)
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
