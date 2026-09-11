package com.hz_apps.foldershare.core.discovery

data class DiscoveredDevice(
    val id: String,
    val serviceName: String? = null,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val osDetails: String,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val platformType: DevicePlatformType = DevicePlatformType.UNKNOWN,
    val httpUrl: String,
    val webDavUrl: String,
    val isAuthRequired: Boolean = false,
    val isHttps: Boolean = false,
    val statusText: String = "Sharing Active",
    val isOnline: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
