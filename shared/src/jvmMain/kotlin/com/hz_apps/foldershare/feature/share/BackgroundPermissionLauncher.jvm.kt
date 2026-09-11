package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionStatus

@Composable
actual fun rememberBackgroundPermissionLauncher(
    onPermissionsResult: (BackgroundPermissionStatus) -> Unit
): BackgroundPermissionLauncher {
    return remember {
        object : BackgroundPermissionLauncher {
            override fun launchPermissionRequest() {
                onPermissionsResult(
                    BackgroundPermissionStatus(
                        isNotificationGranted = true,
                        isBatteryOptimizationIgnored = true
                    )
                )
            }
        }
    }
}
