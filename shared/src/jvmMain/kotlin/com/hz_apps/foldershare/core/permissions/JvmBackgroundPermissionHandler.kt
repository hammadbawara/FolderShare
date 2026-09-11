package com.hz_apps.foldershare.core.permissions

class JvmBackgroundPermissionHandler : BackgroundPermissionHandler {
    override fun checkStatus(): BackgroundPermissionStatus {
        return BackgroundPermissionStatus(
            isNotificationGranted = true,
            isBatteryOptimizationIgnored = true
        )
    }

    override fun openAppSettings() {
        // No-op on JVM/Desktop
    }
}

actual fun createBackgroundPermissionHandler(): BackgroundPermissionHandler {
    return JvmBackgroundPermissionHandler()
}
