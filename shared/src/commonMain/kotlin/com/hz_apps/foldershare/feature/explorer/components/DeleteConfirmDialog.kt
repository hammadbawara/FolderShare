package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.ui.components.AppAlertDialog

@Composable
fun DeleteConfirmDialog(
    file: RemoteFile,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${if (file.isDirectory) "Folder" else "File"}") },
        text = {
            Text("Are you sure you want to delete '${file.name}'? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
