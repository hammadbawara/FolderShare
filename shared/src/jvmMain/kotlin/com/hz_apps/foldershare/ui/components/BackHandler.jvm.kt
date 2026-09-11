package com.hz_apps.foldershare.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back button on Desktop JVM platform
}
