package com.hz_apps.foldershare.feature.share.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun FolderConfigDialog(
    folder: FolderConfigEntity,
    isNewFolder: Boolean = false,
    onDismissRequest: () -> Unit,
    onSaveConfig: (FolderConfigEntity) -> Unit,
    onDeleteFolder: ((Long) -> Unit)? = null
) {
    var name by remember(folder) { mutableStateOf(folder.name) }
    var isShared by remember(folder) { mutableStateOf(folder.isShared) }
    var isWriteAllowed by remember(folder) { mutableStateOf(folder.isWriteAllowed) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val vectorIcon = getFolderVectorIcon(name, folder.iconEmoji)

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = vectorIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isNewFolder) "Add Shared Folder" else "Configure Folder",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                // Folder Display Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Path info card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = folder.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                // Toggle: Enable Sharing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isShared = !isShared }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Sharing",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Make this folder accessible over local network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isShared,
                        onCheckedChange = { isShared = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Access Level: Read Only vs Read & Write Segmented Chips
                Text(
                    text = "Access Level",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isWriteAllowed,
                        onClick = { isWriteAllowed = false },
                        label = {
                            Text(
                                text = "Read Only",
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    FilterChip(
                        selected = isWriteAllowed,
                        onClick = { isWriteAllowed = true },
                        label = {
                            Text(
                                text = "Read & Write",
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text(
                    text = if (isWriteAllowed) {
                        "Devices on your network can view, download, upload, and modify files."
                    } else {
                        "Devices on your network can only view and download files."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = folder.copy(
                        name = name.ifBlank { folder.name },
                        isReadAllowed = true,
                        isWriteAllowed = isWriteAllowed,
                        isShared = isShared
                    )
                    onSaveConfig(updated)
                    onDismissRequest()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isNewFolder) "Add Folder" else "Save")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isNewFolder && onDeleteFolder != null) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )

    // Separate confirmation dialog for safety before removing folder
    if (showDeleteConfirmDialog && onDeleteFolder != null) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Remove Shared Folder?") },
            text = {
                Text("This will unshare \"${folder.name}\" from Folder Share. The files on your device will remain untouched.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteFolder(folder.id)
                        onDismissRequest()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
