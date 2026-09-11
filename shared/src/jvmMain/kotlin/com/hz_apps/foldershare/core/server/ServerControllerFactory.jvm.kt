package com.hz_apps.foldershare.core.server

actual fun createServerController(serverManager: ServerManager): ServerController {
    return JvmServerController(serverManager)
}
