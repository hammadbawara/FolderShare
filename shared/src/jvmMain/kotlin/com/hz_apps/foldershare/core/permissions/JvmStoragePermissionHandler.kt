package com.hz_apps.foldershare.core.permissions

class JvmStoragePermissionHandler : StoragePermissionHandler {
    override fun hasStoragePermission(): Boolean = true

    override fun openAppSettings() {
        // No-op on JVM/Desktop
    }
}

actual fun createStoragePermissionHandler(): StoragePermissionHandler = JvmStoragePermissionHandler()
