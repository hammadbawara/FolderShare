package com.hz_apps.foldershare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun rememberPlatformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    defaultDarkColorScheme: ColorScheme,
    defaultLightColorScheme: ColorScheme
): ColorScheme
