package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun RenameDialog(
    file: RemoteFile,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(file.name) }
    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${if (file.isDirectory) "Folder" else "File"}") },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                Text("Enter a new name for '${file.name}':")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank() && newName != file.name) {
                        onRename(newName)
                    }
                },
                enabled = newName.isNotBlank() && newName != file.name
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
