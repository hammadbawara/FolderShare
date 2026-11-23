package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable

interface StoragePermissionLauncher {
    fun launchPermissionRequest()
}

@Composable
expect fun rememberStoragePermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): StoragePermissionLauncher
