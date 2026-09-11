package com.hz_apps.foldershare.core.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hz_apps.foldershare.core.service.FolderShareService
import kotlinx.coroutines.flow.StateFlow

class AndroidServerController(
    private val context: Context,
    private val serverManager: ServerManager
) : ServerController {

    override val serverState: StateFlow<ServerState>
        get() = serverManager.serverState

    override fun startServer() {
        val intent = Intent(context, FolderShareService::class.java).apply {
            action = FolderShareService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopServer() {
        val intent = Intent(context, FolderShareService::class.java).apply {
            action = FolderShareService.ACTION_STOP
        }
        context.startService(intent)
    }
}
