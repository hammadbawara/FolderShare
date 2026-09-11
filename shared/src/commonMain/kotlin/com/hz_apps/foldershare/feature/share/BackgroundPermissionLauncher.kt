package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionStatus

interface BackgroundPermissionLauncher {
    fun launchPermissionRequest()
}

@Composable
expect fun rememberBackgroundPermissionLauncher(
    onPermissionsResult: (BackgroundPermissionStatus) -> Unit
): BackgroundPermissionLauncher
