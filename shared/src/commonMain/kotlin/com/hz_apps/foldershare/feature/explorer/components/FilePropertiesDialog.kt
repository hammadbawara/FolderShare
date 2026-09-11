package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun FilePropertiesDialog(
    file: RemoteFile,
    downloadUrl: String,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Properties") },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                PropertyRow(label = "Name", value = file.name)
                PropertyRow(label = "Type", value = if (file.isDirectory) "Directory / Folder" else "File (${file.extension.ifEmpty { "unknown" }})")
                PropertyRow(label = "Path", value = file.path)
                if (!file.isDirectory) {
                    PropertyRow(label = "Size", value = file.formattedSize)
                }
                if (file.lastModified.isNotEmpty()) {
                    PropertyRow(label = "Last Modified", value = file.lastModified)
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text(
                    text = "WebDAV URL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = downloadUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCopyLink(downloadUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy WebDAV Link")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
