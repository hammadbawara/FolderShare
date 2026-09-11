package com.hz_apps.foldershare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    defaultDarkColorScheme: ColorScheme,
    defaultLightColorScheme: ColorScheme
): ColorScheme {
    return if (darkTheme) defaultDarkColorScheme else defaultLightColorScheme
}
