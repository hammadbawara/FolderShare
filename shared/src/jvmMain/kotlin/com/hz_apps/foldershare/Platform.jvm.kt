package com.hz_apps.foldershare

import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.core.discovery.DevicePlatformType

class JVMPlatform : Platform {
    private val osName = System.getProperty("os.name") ?: "Desktop"
    private val osLower = osName.lowercase()

    override val platformType: DevicePlatformType = when {
        osLower.contains("win") -> DevicePlatformType.WINDOWS
        osLower.contains("mac") || osLower.contains("darwin") -> DevicePlatformType.MACOS
        osLower.contains("linux") || osLower.contains("nix") || osLower.contains("nux") -> DevicePlatformType.LINUX
        else -> DevicePlatformType.UNKNOWN
    }

    override val category: DeviceCategory = DeviceCategory.COMPUTER

    override val name: String = when (platformType) {
        DevicePlatformType.WINDOWS -> "Windows"
        DevicePlatformType.MACOS -> "macOS"
        DevicePlatformType.LINUX -> "Linux"
        else -> osName
    }

    override val defaultDeviceName: String = run {
        val user = System.getProperty("user.name")?.trim()
        val pcType = when (platformType) {
            DevicePlatformType.WINDOWS -> "PC"
            DevicePlatformType.MACOS -> "Mac"
            DevicePlatformType.LINUX -> "Linux PC"
            else -> "Computer"
        }
        if (!user.isNullOrBlank()) "$user's $pcType" else name
    }
}

actual fun getPlatform(): Platform = JVMPlatform()