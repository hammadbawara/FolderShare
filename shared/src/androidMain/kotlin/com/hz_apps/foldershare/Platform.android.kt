package com.hz_apps.foldershare

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.core.discovery.DevicePlatformType

class AndroidPlatform : Platform {
    override val isTv: Boolean by lazy {
        val context = AndroidContextProvider.applicationContext
        val uiModeManager = context?.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context?.packageManager?.let { pm ->
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                @Suppress("DEPRECATION")
                pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        } == true
        val devInfo = "${Build.BRAND} ${Build.MANUFACTURER} ${Build.MODEL} ${Build.DEVICE} ${Build.PRODUCT}".lowercase()
        val devMatches = devInfo.contains("tv") || devInfo.contains("box") || devInfo.contains("shield") ||
            devInfo.contains("bravia") || devInfo.contains("firetv") || devInfo.contains("googletv")
        isTelevisionMode || hasLeanback || devMatches
    }

    override val platformType: DevicePlatformType = if (isTv) DevicePlatformType.ANDROID_TV else DevicePlatformType.ANDROID

    override val category: DeviceCategory = if (isTv) DeviceCategory.TV else DeviceCategory.PHONE

    override val name: String = if (isTv) "Android TV" else "Android"

    override val defaultDeviceName: String = run {
        val model = Build.MODEL.trim()
        val manufacturer = Build.MANUFACTURER.trim()
        if (model.isNotBlank()) {
            if (manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true)) {
                "$manufacturer $model"
            } else {
                model
            }
        } else {
            if (isTv) "Android TV" else "Android Device"
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()