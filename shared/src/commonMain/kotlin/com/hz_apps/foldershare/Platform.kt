package com.hz_apps.foldershare

import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.core.discovery.DevicePlatformType

interface Platform {
    val name: String
    val category: DeviceCategory
    val platformType: DevicePlatformType
    val defaultDeviceName: String
    val isTv: Boolean
        get() = category == DeviceCategory.TV || platformType == DevicePlatformType.ANDROID_TV
}

expect fun getPlatform(): Platform

