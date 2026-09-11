package com.hz_apps.foldershare.core.discovery

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceCategory(val displayName: String) {
    PHONE("Phone"),
    TABLET("Tablet"),
    COMPUTER("Computer"),
    TV("TV"),
    UNKNOWN("Device")
}

@Serializable
enum class DevicePlatformType(val displayName: String) {
    ANDROID("Android"),
    ANDROID_TV("Android TV"),
    WINDOWS("Windows"),
    LINUX("Linux"),
    MACOS("macOS"),
    IOS("iOS"),
    UNKNOWN("FolderShare")
}

data class ClassifiedDeviceMetadata(
    val category: DeviceCategory,
    val platformType: DevicePlatformType,
    val displaySubtitle: String
)

object DeviceClassifier {

    fun parseCategory(categoryRaw: String?): DeviceCategory? {
        if (categoryRaw.isNullOrBlank()) return null
        return try {
            DeviceCategory.valueOf(categoryRaw.trim().uppercase())
        } catch (_: Exception) {
            when (categoryRaw.trim().lowercase()) {
                "phone", "mobile", "smartphone" -> DeviceCategory.PHONE
                "tablet", "pad" -> DeviceCategory.TABLET
                "computer", "desktop", "laptop", "pc" -> DeviceCategory.COMPUTER
                "tv", "television", "android_tv" -> DeviceCategory.TV
                else -> null
            }
        }
    }

    fun parsePlatform(platformRaw: String?): DevicePlatformType? {
        if (platformRaw.isNullOrBlank()) return null
        return try {
            DevicePlatformType.valueOf(platformRaw.trim().uppercase())
        } catch (_: Exception) {
            when (platformRaw.trim().lowercase()) {
                "android" -> DevicePlatformType.ANDROID
                "android_tv", "androidtv", "google_tv", "googletv" -> DevicePlatformType.ANDROID_TV
                "windows", "win" -> DevicePlatformType.WINDOWS
                "linux" -> DevicePlatformType.LINUX
                "macos", "mac", "darwin" -> DevicePlatformType.MACOS
                "ios", "iphone", "ipad" -> DevicePlatformType.IOS
                else -> null
            }
        }
    }

    fun classify(
        categoryAttr: String? = null,
        platformAttr: String? = null,
        osDetailsAttr: String? = null,
        deviceName: String = ""
    ): ClassifiedDeviceMetadata {
        val explicitCategory = parseCategory(categoryAttr)
        val explicitPlatform = parsePlatform(platformAttr)

        val osStr = osDetailsAttr.orEmpty()
        val combined = "$deviceName $osStr".lowercase()

        val platformType = explicitPlatform ?: when {
            combined.contains("android tv") || combined.contains("google tv") -> DevicePlatformType.ANDROID_TV
            combined.contains("windows") || combined.contains("win32") || combined.contains("win64") -> DevicePlatformType.WINDOWS
            combined.contains("linux") || combined.contains("ubuntu") || combined.contains("fedora") || combined.contains("debian") || combined.contains("arch") -> DevicePlatformType.LINUX
            combined.contains("macos") || combined.contains("mac os") || combined.contains("darwin") -> DevicePlatformType.MACOS
            combined.contains("ios") || combined.contains("iphone") || combined.contains("ipad") -> DevicePlatformType.IOS
            combined.contains("android") -> DevicePlatformType.ANDROID
            else -> DevicePlatformType.UNKNOWN
        }

        val category = explicitCategory ?: when {
            platformType == DevicePlatformType.ANDROID_TV ||
                combined.contains("tv") || combined.contains("television") || combined.contains("box") ||
                combined.contains("shield") || combined.contains("firetv") || combined.contains("bravia") -> DeviceCategory.TV

            combined.contains("tablet") || combined.contains("pad") || combined.contains("tab") -> DeviceCategory.TABLET

            platformType == DevicePlatformType.WINDOWS || platformType == DevicePlatformType.LINUX ||
                platformType == DevicePlatformType.MACOS || combined.contains("laptop") ||
                combined.contains("desktop") || combined.contains("pc") || combined.contains("java") -> DeviceCategory.COMPUTER

            platformType == DevicePlatformType.ANDROID || platformType == DevicePlatformType.IOS ||
                combined.contains("phone") || combined.contains("mobile") || combined.contains("pixel") ||
                combined.contains("galaxy") || combined.contains("xiaomi") -> DeviceCategory.PHONE

            else -> DeviceCategory.UNKNOWN
        }

        val platformName = when {
            platformType != DevicePlatformType.UNKNOWN -> platformType.displayName
            osStr.isNotBlank() && !osStr.contains("http", ignoreCase = true) -> osStr.split("•").first().trim()
            else -> category.displayName
        }

        val displaySubtitle = "${category.displayName} • $platformName"

        return ClassifiedDeviceMetadata(
            category = category,
            platformType = platformType,
            displaySubtitle = displaySubtitle
        )
    }
}
