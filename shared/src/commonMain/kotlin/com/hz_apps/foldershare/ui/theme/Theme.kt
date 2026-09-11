package com.hz_apps.foldershare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BaseBlue = Color(0xFF2563EB)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
)

private val LightColorScheme = lightColorScheme(
    primary = BaseBlue,
)

@Composable
fun FolderShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = rememberPlatformColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        defaultDarkColorScheme = DarkColorScheme,
        defaultLightColorScheme = LightColorScheme
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
