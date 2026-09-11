package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.discovery.AndroidContextProvider

actual fun createServerController(serverManager: ServerManager): ServerController {
    val context = requireNotNull(AndroidContextProvider.applicationContext) {
        "AndroidContextProvider.applicationContext must be initialized before creating ServerController"
    }
    return AndroidServerController(context, serverManager)
}
