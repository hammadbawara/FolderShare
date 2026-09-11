package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hz_apps.foldershare.core.explorer.model.FileSortOption
import com.hz_apps.foldershare.core.explorer.model.SortDirection
import com.hz_apps.foldershare.core.explorer.model.SortField
import com.hz_apps.foldershare.ui.components.CascadingMenuScope

@Composable
fun CascadingMenuScope.ExplorerMenuContent(
    sortOption: FileSortOption,
    onRefresh: () -> Unit,
    onToggleShowHiddenFiles: () -> Unit,
    onUpdateSortOption: (FileSortOption) -> Unit,
    onUploadFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    isWriteAllowed: Boolean = true,
    isUploadEnabled: Boolean = true,
    isCreateEnabled: Boolean = true
) {
    // 1. Refresh
    MenuItem(
        text = { Text("Refresh") },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        onClick = onRefresh
    )

    // 2. Show Hidden Files & Folders
    MenuItem(
        text = {
            Text(if (sortOption.showHiddenFiles) "Hide Hidden Files" else "Show Hidden Files")
        },
        leadingIcon = {
            Icon(
                imageVector = if (sortOption.showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (sortOption.showHiddenFiles) {
                Icon(Icons.Default.Check, contentDescription = null)
            }
        },
        onClick = onToggleShowHiddenFiles
    )

    // 3. Sort By Options (Cascading Submenu!)
    MenuItem(
        text = { Text("Sort By") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
        subMenus = {
            // Folders on Top Option
            MenuItem(
                text = { Text("Folders on Top") },
                trailingIcon = {
                    if (sortOption.directoriesFirst) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onUpdateSortOption(sortOption.copy(directoriesFirst = !sortOption.directoriesFirst))
                }
            )

            MenuDivider()

            // Sort Field Options
            SortField.entries.forEach { field ->
                val fieldLabel = when (field) {
                    SortField.NAME -> "Name"
                    SortField.SIZE -> "Size"
                    SortField.DATE -> "Last Modified"
                    SortField.TYPE -> "Type / Extension"
                }
                MenuItem(
                    text = { Text(fieldLabel) },
                    trailingIcon = {
                        if (sortOption.field == field) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onUpdateSortOption(sortOption.copy(field = field))
                    }
                )
            }

            MenuDivider()

            // Sort Direction Options
            MenuItem(
                text = { Text("Ascending") },
                trailingIcon = {
                    if (sortOption.direction == SortDirection.ASCENDING) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onUpdateSortOption(sortOption.copy(direction = SortDirection.ASCENDING))
                }
            )

            MenuItem(
                text = { Text("Descending") },
                trailingIcon = {
                    if (sortOption.direction == SortDirection.DESCENDING) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onUpdateSortOption(sortOption.copy(direction = SortDirection.DESCENDING))
                }
            )
        }
    )

    MenuDivider()

    // 4. Creation & Upload Actions
    MenuItem(
        text = { Text("Upload File...") },
        leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
        enabled = isWriteAllowed && isUploadEnabled,
        onClick = onUploadFile
    )

    MenuItem(
        text = { Text("New Folder") },
        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        enabled = isWriteAllowed && isCreateEnabled,
        onClick = onCreateFolder
    )

    MenuItem(
        text = { Text("New File") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
        enabled = isWriteAllowed && isCreateEnabled,
        onClick = onCreateFile
    )
}

