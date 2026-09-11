package com.hz_apps.foldershare.core.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JvmServerController(
    private val serverManager: ServerManager
) : ServerController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val serverState: StateFlow<ServerState>
        get() = serverManager.serverState

    override fun startServer() {
        scope.launch {
            serverManager.startServer()
        }
    }

    override fun stopServer() {
        scope.launch {
            serverManager.stopServer()
        }
    }
}
