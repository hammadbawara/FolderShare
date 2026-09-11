package com.hz_apps.foldershare.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider

class AndroidBackgroundPermissionHandler(
    private val context: Context
) : BackgroundPermissionHandler {

    override fun checkStatus(): BackgroundPermissionStatus {
        val isNotificationGranted = isNotificationPermissionGranted()
        val isBatteryOptimizationIgnored = isBatteryOptimizationIgnored()
        return BackgroundPermissionStatus(
            isNotificationGranted = isNotificationGranted,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored
        )
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasRuntimePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            hasRuntimePermission && NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    override fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun createBackgroundPermissionHandler(): BackgroundPermissionHandler {
    val context = requireNotNull(AndroidContextProvider.applicationContext) {
        "AndroidContextProvider.applicationContext must be initialized before creating BackgroundPermissionHandler"
    }
    return AndroidBackgroundPermissionHandler(context)
}
