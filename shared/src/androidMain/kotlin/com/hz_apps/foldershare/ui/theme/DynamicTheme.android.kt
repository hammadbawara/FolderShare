package com.hz_apps.foldershare.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    defaultDarkColorScheme: ColorScheme,
    defaultLightColorScheme: ColorScheme
): ColorScheme {
    val context = LocalContext.current

    val isTv = remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        com.hz_apps.foldershare.getPlatform().isTv || isTelevisionMode || hasLeanback
    }

    // Android TV is strictly dark mode and does not use dynamic wallpaper colors
    if (isTv) {
        return defaultDarkColorScheme
    }

    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    return if (darkTheme) defaultDarkColorScheme else defaultLightColorScheme
}
