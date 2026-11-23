package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberStoragePermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): StoragePermissionLauncher {
    return remember {
        object : StoragePermissionLauncher {
            override fun launchPermissionRequest() {
                onPermissionResult(true)
            }
        }
    }
}
