package com.hz_apps.foldershare.feature.share

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hz_apps.foldershare.core.permissions.AndroidStoragePermissionHandler

@Composable
actual fun rememberStoragePermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): StoragePermissionLauncher {
    val context = LocalContext.current
    val handler = remember(context) { AndroidStoragePermissionHandler(context) }
    val currentOnResult by rememberUpdatedState(onPermissionResult)
    var hasRequestedLegacyOnce by remember { mutableStateOf(false) }

    // Re-check storage permission on resume so changes made in Android Settings take effect immediately
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentOnResult(handler.hasStoragePermission())
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        currentOnResult(handler.hasStoragePermission())
    }

    val legacyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        currentOnResult(handler.hasStoragePermission())
    }

    return remember(context, handler) {
        object : StoragePermissionLauncher {
            override fun launchPermissionRequest() {
                if (handler.hasStoragePermission()) {
                    currentOnResult(true)
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        allFilesAccessLauncher.launch(intent)
                    } catch (_: Exception) {
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            allFilesAccessLauncher.launch(fallbackIntent)
                        } catch (_: Exception) {
                            handler.openAppSettings()
                        }
                    }
                } else {
                    val activity = context.findActivity()
                    val shouldShowRationale = activity != null && (
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) || ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                    if (hasRequestedLegacyOnce && activity != null && !shouldShowRationale) {
                        handler.openAppSettings()
                    } else {
                        hasRequestedLegacyOnce = true
                        legacyPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
