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
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (folderName: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder") },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                Text("Enter the name for the new folder:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName)
                    }
                },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
