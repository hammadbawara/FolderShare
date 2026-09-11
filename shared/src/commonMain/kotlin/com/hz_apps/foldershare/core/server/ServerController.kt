package com.hz_apps.foldershare.core.server

import kotlinx.coroutines.flow.StateFlow

interface ServerController {
    val serverState: StateFlow<ServerState>
    fun startServer()
    fun stopServer()
}
