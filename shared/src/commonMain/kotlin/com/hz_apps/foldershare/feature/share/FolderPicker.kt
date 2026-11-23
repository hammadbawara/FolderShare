package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable

interface FolderPickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFolderPickerLauncher(
    onFolderPicked: (path: String, name: String) -> Unit
): FolderPickerLauncher
