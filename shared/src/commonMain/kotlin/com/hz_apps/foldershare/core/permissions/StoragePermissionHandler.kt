package com.hz_apps.foldershare.core.permissions

interface StoragePermissionHandler {
    fun hasStoragePermission(): Boolean
    fun openAppSettings()
}

expect fun createStoragePermissionHandler(): StoragePermissionHandler
