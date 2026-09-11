package com.hz_apps.foldershare.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    DEVICES("Devices", Icons.Default.Devices),
    SHARE("Share", Icons.Default.FolderShared),
    SETTINGS("Settings", Icons.Default.Settings)
}
