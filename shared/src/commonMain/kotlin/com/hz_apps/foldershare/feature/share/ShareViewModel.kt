package com.hz_apps.foldershare.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.core.discovery.ServiceAdvertiser
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionHandler
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionStatus
import com.hz_apps.foldershare.core.server.ServerController
import com.hz_apps.foldershare.core.server.ServerStatus
import com.hz_apps.foldershare.core.util.formatHostForUrl
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShareUiState(
    val serverStatus: ServerStatus = ServerStatus.STOPPED,
    val folders: List<FolderConfigEntity> = emptyList(),
    val serverUrls: List<String> = emptyList(),
    val connectedDeviceCount: Int = 0,
    val errorMessage: String? = null,
    val permissionStatus: BackgroundPermissionStatus = BackgroundPermissionStatus(),
    val showPermissionRationaleDialog: Boolean = false,
    val isBackgroundRestricted: Boolean = false,
    val isLoadingFolders: Boolean = true
) {
    val isSharing: Boolean get() = serverStatus == ServerStatus.RUNNING
    val hasActiveFolders: Boolean get() = folders.any { it.isShared }
    val activeFoldersCount: Int get() = folders.count { it.isShared }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModel(
    private val serverController: ServerController,
    private val folderDao: FolderConfigDao,
    private val serviceAdvertiser: ServiceAdvertiser,
    private val permissionHandler: BackgroundPermissionHandler
) : ViewModel() {

    private val _userErrorMessage = MutableStateFlow<String?>(null)
    private val _permissionStatus = MutableStateFlow(permissionHandler.checkStatus())
    private val _showPermissionDialog = MutableStateFlow(false)
    private val _isBackgroundRestricted = MutableStateFlow(false)

    private val serverUrlsFlow = serverController.serverState.flatMapLatest { state ->
        if (state.status == ServerStatus.RUNNING) {
            serviceAdvertiser.advertisedAddresses.map { ips ->
                val scheme = if (state.isHttps) "https" else "http"
                ips.ifEmpty { listOf("127.0.0.1") }.map { ip ->
                    val formattedIp = formatHostForUrl(ip)
                    "$scheme://$formattedIp:${state.port}"
                }
            }
        } else {
            flowOf(emptyList())
        }
    }

    private val baseShareState = combine(
        serverController.serverState,
        folderDao.getAllFolders(),
        serverUrlsFlow,
        _userErrorMessage
    ) { state, folders, urls, userError ->
        ShareUiState(
            serverStatus = state.status,
            folders = folders,
            serverUrls = urls,
            connectedDeviceCount = state.connectedDeviceCount,
            errorMessage = userError ?: state.errorMessage,
            isLoadingFolders = false
        )
    }

    val uiState: StateFlow<ShareUiState> = combine(
        baseShareState,
        _permissionStatus,
        _showPermissionDialog,
        _isBackgroundRestricted
    ) { base, permStatus, showDialog, isRestricted ->
        base.copy(
            permissionStatus = permStatus,
            showPermissionRationaleDialog = showDialog,
            isBackgroundRestricted = isRestricted && !permStatus.isAllGranted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = createInitialUiState()
    )

    private fun createInitialUiState(): ShareUiState {
        val serverState = serverController.serverState.value
        val permStatus = permissionHandler.checkStatus()

        return ShareUiState(
            serverStatus = serverState.status,
            folders = emptyList(),
            serverUrls = emptyList(),
            connectedDeviceCount = serverState.connectedDeviceCount,
            errorMessage = serverState.errorMessage,
            permissionStatus = permStatus,
            showPermissionRationaleDialog = false,
            isBackgroundRestricted = false,
            isLoadingFolders = true
        )
    }

    fun clearErrorMessage() {
        _userErrorMessage.value = null
    }

    fun addFolder(path: String, name: String) {
        viewModelScope.launch {
            val emoji = when {
                name.lowercase().contains("download") -> "📥"
                name.lowercase().contains("document") -> "📄"
                name.lowercase().contains("music") || name.lowercase().contains("audio") -> "🎵"
                name.lowercase().contains("video") || name.lowercase().contains("movie") -> "🎬"
                name.lowercase().contains("picture") || name.lowercase().contains("image") || name.lowercase().contains("photo") -> "🖼️"
                else -> "📁"
            }

            val newFolder = FolderConfigEntity(
                name = name,
                path = path,
                iconEmoji = emoji,
                isReadAllowed = true,
                isWriteAllowed = false,
                isShared = true
            )
            folderDao.insertFolder(newFolder)
        }
    }

    fun addFolderConfig(folder: FolderConfigEntity) {
        viewModelScope.launch {
            folderDao.insertFolder(folder)
        }
    }

    fun updateFolderConfig(folder: FolderConfigEntity) {
        viewModelScope.launch {
            folderDao.updateFolder(folder)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            folderDao.deleteFolderById(folderId)
        }
    }

    fun toggleSharing() {
        val currentStatus = uiState.value.serverStatus
        if (currentStatus == ServerStatus.STARTING || currentStatus == ServerStatus.STOPPING) return

        if (uiState.value.isSharing) {
            _userErrorMessage.value = null
            viewModelScope.launch {
                serverController.stopServer()
            }
            return
        }

        if (!uiState.value.hasActiveFolders) {
            _userErrorMessage.value = "Please add or enable at least one shared folder before starting sharing."
            return
        }

        val permStatus = permissionHandler.checkStatus()
        _permissionStatus.value = permStatus

        if (!permStatus.isAllGranted) {
            _showPermissionDialog.value = true
            return
        }

        _isBackgroundRestricted.value = false
        _userErrorMessage.value = null
        viewModelScope.launch {
            serverController.startServer()
        }
    }

    fun onPermissionsResult(status: BackgroundPermissionStatus) {
        _permissionStatus.value = status
        if (status.isAllGranted) {
            _isBackgroundRestricted.value = false
            if (_showPermissionDialog.value) {
                _showPermissionDialog.value = false
                if (!uiState.value.isSharing) {
                    viewModelScope.launch {
                        serverController.startServer()
                    }
                }
            }
        } else {
            if (uiState.value.isSharing) {
                _isBackgroundRestricted.value = true
            }
        }
    }

    fun onSkipPermissions() {
        _showPermissionDialog.value = false
        _isBackgroundRestricted.value = true
        if (!uiState.value.isSharing && uiState.value.hasActiveFolders) {
            _userErrorMessage.value = null
            viewModelScope.launch {
                serverController.startServer()
            }
        }
    }

    fun onDismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun onFixBackgroundRestrictions() {
        _permissionStatus.value = permissionHandler.checkStatus()
        _showPermissionDialog.value = true
    }
}
