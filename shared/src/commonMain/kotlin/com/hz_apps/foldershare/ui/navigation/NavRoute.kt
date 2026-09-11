package com.hz_apps.foldershare.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.feature.devices.DeviceItem
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRoute : NavKey

@Serializable
data object ShareRoute : NavRoute

@Serializable
data object DevicesRoute : NavRoute

@Serializable
data object SettingsRoute : NavRoute

@Serializable
data class DeviceAuthRoute(
    val device: DeviceItem,
    val initialErrorMessage: String? = null
) : NavRoute

@Serializable
data class FileExplorerRoute(
    val device: RemoteTargetDevice,
    val username: String? = null,
    val password: String? = null,
    val path: String = "/"
) : NavRoute
