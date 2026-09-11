package com.hz_apps.foldershare.core.permissions

data class BackgroundPermissionStatus(
    val isNotificationGranted: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = true
) {
    val isAllGranted: Boolean get() = isNotificationGranted && isBatteryOptimizationIgnored
}

interface BackgroundPermissionHandler {
    fun checkStatus(): BackgroundPermissionStatus
    fun openAppSettings()
}

expect fun createBackgroundPermissionHandler(): BackgroundPermissionHandler
