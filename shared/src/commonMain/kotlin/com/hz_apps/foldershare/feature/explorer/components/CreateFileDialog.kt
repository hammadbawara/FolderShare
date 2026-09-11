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
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New File") },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (e.g. note.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (Optional)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onCreate(fileName, content)
                    }
                },
                enabled = fileName.isNotBlank()
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
