package com.hz_apps.foldershare.feature.share

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hz_apps.foldershare.core.permissions.AndroidBackgroundPermissionHandler
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionStatus

@Composable
actual fun rememberBackgroundPermissionLauncher(
    onPermissionsResult: (BackgroundPermissionStatus) -> Unit
): BackgroundPermissionLauncher {
    val context = LocalContext.current
    val handler = remember(context) { AndroidBackgroundPermissionHandler(context) }

    // Re-check permissions whenever the app resumes to pick up changes made in system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onPermissionsResult(handler.checkStatus())
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionsResult(handler.checkStatus())
    }

    fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        batteryLauncher.launch(fallbackIntent)
                        return
                    } catch (ex: Exception) {
                        handler.openAppSettings()
                    }
                }
            }
        }
        onPermissionsResult(handler.checkStatus())
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val status = handler.checkStatus()
        if (!status.isBatteryOptimizationIgnored) {
            requestBatteryOptimization()
        } else {
            onPermissionsResult(status)
        }
    }

    fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            notificationSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            handler.openAppSettings()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val status = handler.checkStatus()
        if (!status.isBatteryOptimizationIgnored) {
            requestBatteryOptimization()
        } else if (!status.isNotificationGranted) {
            openNotificationSettings()
        } else {
            onPermissionsResult(status)
        }
    }

    return remember(context, handler) {
        object : BackgroundPermissionLauncher {
            override fun launchPermissionRequest() {
                val status = handler.checkStatus()
                if (status.isAllGranted) {
                    onPermissionsResult(status)
                    return
                }

                if (!status.isNotificationGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings()
                    }
                } else if (!status.isBatteryOptimizationIgnored) {
                    requestBatteryOptimization()
                } else {
                    onPermissionsResult(handler.checkStatus())
                }
            }
        }
    }
}
