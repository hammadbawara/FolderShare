package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.SharedFoldersVirtualFileSystem
import com.hz_apps.foldershare.core.discovery.ServiceAdvertiser
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigEntity
import com.hz_apps.foldershare.getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ServerStatus {
    STOPPED, STARTING, RUNNING, STOPPING, ERROR
}

data class ServerState(
    val status: ServerStatus = ServerStatus.STOPPED,
    val port: Int = ServerConstants.DEFAULT_PORT,
    val isHttps: Boolean = false,
    val connectedDeviceCount: Int = 0,
    val errorMessage: String? = null
) {
    val isSharing: Boolean get() = status == ServerStatus.RUNNING
}

class ServerManager(
    private val serviceAdvertiser: ServiceAdvertiser,
    private val folderDao: FolderConfigDao,
    private val serverConfigDao: ServerConfigDao
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private var webDavServer: WebDavServer? = null
    private var folderWatcherJob: Job? = null
    private val lock = Mutex()

    suspend fun startServer(): Result<ServerState> = lock.withLock {
        if (_serverState.value.status == ServerStatus.RUNNING && webDavServer != null) {
            return Result.success(_serverState.value)
        }

        _serverState.update { it.copy(status = ServerStatus.STARTING, errorMessage = null) }

        return try {
            val activeFolders = folderDao.getAllFolders().first().filter { it.isShared }
            if (activeFolders.isEmpty()) {
                val errorMsg = "Cannot start server: No active shared folders configured."
                _serverState.update {
                    it.copy(
                        status = ServerStatus.ERROR,
                        errorMessage = errorMsg
                    )
                }
                return Result.failure(IllegalStateException(errorMsg))
            }

            val config = serverConfigDao.getServerConfig() ?: ServerConfigEntity()
            val vfs = SharedFoldersVirtualFileSystem(folderDao = folderDao)

            val isHttps = config.isHttpsEnabled
            val authConfig = if (config.isAuthRequired) {
                ServerAuthConfig.Basic(config.username, config.password)
            } else {
                ServerAuthConfig.Disabled
            }

            val candidatePorts = (listOf(config.port) + ServerConstants.FALLBACK_PORTS).distinct()
            var boundPort: Int? = null
            var startedServer: WebDavServer? = null
            var lastError: Throwable? = null

            for (port in candidatePorts) {
                val serverConfig = WebDavServerConfig(
                    port = port,
                    authConfig = authConfig,
                    isHttpsEnabled = isHttps,
                    deviceUuid = config.deviceUuid
                )
                val server = WebDavServer(vfs)
                try {
                    server.start(serverConfig)
                    startedServer = server
                    boundPort = port
                    break
                } catch (e: Exception) {
                    lastError = e
                    try { server.stop() } catch (_: Exception) {}
                }
            }

            if (startedServer == null || boundPort == null) {
                throw lastError ?: Exception("Could not bind WebDAV server to port ${config.port} or any fallback ports.")
            }

            webDavServer = startedServer
            val actualPort = boundPort

            val defaultDeviceName = getPlatform().name
            val deviceName = config.deviceName.trim().ifEmpty { defaultDeviceName }
            serviceAdvertiser.registerService(
                deviceName = deviceName,
                port = actualPort,
                osDetails = defaultDeviceName,
                isAuthRequired = config.isAuthRequired,
                isHttpsEnabled = isHttps,
                deviceUuid = config.deviceUuid
            )

            val initialState = ServerState(
                status = ServerStatus.RUNNING,
                port = actualPort,
                isHttps = isHttps,
                connectedDeviceCount = 0,
                errorMessage = null
            )
            _serverState.value = initialState

            folderWatcherJob?.cancel()
            folderWatcherJob = serverScope.launch {
                folderDao.getAllFolders().collect { allFolders ->
                    val activeCount = allFolders.count { it.isShared }
                    if (activeCount == 0 && _serverState.value.status == ServerStatus.RUNNING) {
                        lock.withLock {
                            if (_serverState.value.status == ServerStatus.RUNNING) {
                                stopServerInternal()
                                _serverState.update {
                                    it.copy(
                                        status = ServerStatus.STOPPED,
                                        errorMessage = "Sharing stopped: No active shared folders remaining."
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Result.success(initialState)
        } catch (e: Exception) {
            e.printStackTrace()
            stopServerInternal()
            _serverState.update {
                it.copy(
                    status = ServerStatus.ERROR,
                    errorMessage = e.message ?: "Failed to start server"
                )
            }
            Result.failure(e)
        }
    }

    suspend fun stopServer() = lock.withLock {
        _serverState.update { it.copy(status = ServerStatus.STOPPING) }
        stopServerInternal()
    }

    private fun stopServerInternal() {
        folderWatcherJob?.cancel()
        folderWatcherJob = null

        try {
            serviceAdvertiser.unregisterService()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            webDavServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            webDavServer = null
        }

        _serverState.update {
            ServerState(
                status = ServerStatus.STOPPED,
                port = ServerConstants.DEFAULT_PORT,
                isHttps = false,
                connectedDeviceCount = 0,
                errorMessage = null
            )
        }
    }
}
