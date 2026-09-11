package com.hz_apps.foldershare

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.hz_apps.foldershare.core.discovery.DeviceDiscoveryEngine
import com.hz_apps.foldershare.core.discovery.JvmDeviceDiscoveryEngine
import com.hz_apps.foldershare.core.server.ServerManager
import com.hz_apps.foldershare.di.initKoin
import com.hz_apps.foldershare.ui.components.ActionProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() {
    val koinApp = initKoin()
    val serverManager = koinApp.koin.get<ServerManager>()
    val discoveryEngine = koinApp.koin.get<DeviceDiscoveryEngine>()

    application {
        var isShuttingDown by remember { mutableStateOf(false) }
        var shutdownProgressText by remember { mutableStateOf("Preparing to shut down...") }
        val scope = rememberCoroutineScope()

        Window(
            onCloseRequest = {
                if (!isShuttingDown) {
                    isShuttingDown = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            shutdownProgressText = "Stopping server..."
                            runCatching { serverManager.stopServer() }
                            shutdownProgressText = "Unregistering services..."
                            runCatching { discoveryEngine.unregisterService() }
                            if (discoveryEngine is JvmDeviceDiscoveryEngine) {
                                shutdownProgressText = "Disposing discovery engine..."
                                runCatching { discoveryEngine.dispose() }
                            }
                        }
                        exitApplication()
                    }
                }
            },
            title = "Folder Share",
            icon = painterResource("icons/icon.png"),
        ) {
            App()
            
            if (isShuttingDown) {
                ActionProgressDialog(
                    title = "Shutting Down",
                    message = shutdownProgressText,
                    onCancel = { },
                    isCancellable = false
                )
            }
        }
    }
}