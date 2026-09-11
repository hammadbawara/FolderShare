package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.FileSortOption
import com.hz_apps.foldershare.ui.components.CascadingDropdownMenu

@Composable
fun BackgroundContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sortOption: FileSortOption,
    onRefresh: () -> Unit,
    onToggleShowHiddenFiles: () -> Unit,
    onUpdateSortOption: (FileSortOption) -> Unit,
    onUploadFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    isWriteAllowed: Boolean = true,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    CascadingDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset
    ) {
        ExplorerMenuContent(
            sortOption = sortOption,
            onRefresh = onRefresh,
            onToggleShowHiddenFiles = onToggleShowHiddenFiles,
            onUpdateSortOption = onUpdateSortOption,
            onUploadFile = onUploadFile,
            onCreateFolder = onCreateFolder,
            onCreateFile = onCreateFile,
            isWriteAllowed = isWriteAllowed
        )
    }
}


