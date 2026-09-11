package com.hz_apps.foldershare.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.core.discovery.DeviceResolver
import com.hz_apps.foldershare.core.explorer.model.FileSortOption
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.model.SortDirection
import com.hz_apps.foldershare.core.explorer.model.SortField
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.explorer.util.PathUtils
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import com.hz_apps.foldershare.core.transfer.FileTransferManager
import com.hz_apps.foldershare.core.transfer.FileTransferProgress
import com.hz_apps.foldershare.core.transfer.TransferDirection
import com.hz_apps.foldershare.core.transfer.TransferStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PathSegment(
    val name: String,
    val fullPath: String
)

typealias TransferDirection = com.hz_apps.foldershare.core.transfer.TransferDirection
typealias TransferStatus = com.hz_apps.foldershare.core.transfer.TransferStatus
typealias FileTransferProgress = com.hz_apps.foldershare.core.transfer.FileTransferProgress
typealias FileSaveProgress = com.hz_apps.foldershare.core.transfer.FileSaveProgress

data class FileExplorerUiState(
    val device: RemoteTargetDevice? = null,
    val currentPath: String = "/",
    val pathSegments: List<PathSegment> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val rawFiles: List<RemoteFile> = emptyList(),
    val targetFocusPath: String? = null,
    val isCurrentFolderWriteAllowed: Boolean = true,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val transferProgress: FileTransferProgress? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val sortOption: FileSortOption = FileSortOption(),
    val selectedFileForProperties: RemoteFile? = null,
    val propertiesDownloadUrl: String? = null,
    val selectedFileForRename: RemoteFile? = null,
    val selectedFileForDelete: RemoteFile? = null,
    val selectedFileForOpenOptions: RemoteFile? = null,
    val showCreateFolderDialog: Boolean = false,
    val showCreateFileDialog: Boolean = false,
    val showSortDialog: Boolean = false
) {
    val saveProgress: FileTransferProgress? get() = transferProgress
}

class FileExplorerViewModel(
    private val repository: RemoteFileRepository,
    private val platformFileHandler: PlatformFileHandler,
    private val deviceResolver: DeviceResolver,
    private val fileTransferManager: FileTransferManager,
    private val streamProxyController: com.hz_apps.foldershare.core.proxy.StreamProxyController? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileExplorerUiState())
    val uiState: StateFlow<FileExplorerUiState> = _uiState.asStateFlow()

    private val directoryFocusMap = mutableMapOf<String, String>()
    private var deviceObservationJob: Job? = null

    init {
        viewModelScope.launch {
            fileTransferManager.transferProgress.collect { progress ->
                _uiState.update { it.copy(transferProgress = progress) }
            }
        }
    }

    fun setTargetDevice(device: RemoteTargetDevice, username: String? = null, password: String? = null, initialPath: String = "/") {
        deviceResolver.registerManualDevice(device)

        _uiState.update { 
            FileExplorerUiState(
                device = device,
                currentPath = initialPath,
                pathSegments = PathUtils.buildPathSegments(initialPath)
            ) 
        }

        deviceObservationJob?.cancel()
        deviceObservationJob = viewModelScope.launch {
            deviceResolver.observeDeviceEndpoint(device.id).collect { updatedDev ->
                if (updatedDev != null) {
                    val activeState = _uiState.value
                    val currentDev = activeState.device
                    if (currentDev == null || currentDev.hostAddress != updatedDev.hostAddress || currentDev.port != updatedDev.port) {
                        val hadError = activeState.errorMessage != null
                        _uiState.update { it.copy(device = updatedDev) }
                        if (hadError) {
                            loadDirectory(_uiState.value.currentPath)
                        }
                    }
                }
            }
        }

        loadDirectory(initialPath)
    }

    fun setFocusedPath(path: String) {
        val current = _uiState.value.currentPath
        directoryFocusMap[current] = path
        if (_uiState.value.targetFocusPath != path) {
            _uiState.update { it.copy(targetFocusPath = path) }
        }
    }

    fun loadDirectory(path: String, targetFocus: String? = null) {
        val target = _uiState.value.device ?: return
        val normalizedPath = PathUtils.normalizePath(path)
        val resolvedFocus = targetFocus ?: directoryFocusMap[normalizedPath]

        _uiState.update { 
            it.copy(
                currentPath = normalizedPath,
                pathSegments = PathUtils.buildPathSegments(normalizedPath),
                targetFocusPath = resolvedFocus,
                isLoading = true,
                errorMessage = null
            ) 
        }

        viewModelScope.launch {
            val result = repository.listFiles(
                deviceId = target.id,
                path = normalizedPath,
                fallbackDevice = target
            )

            result.fold(
                onSuccess = { listingResult ->
                    _uiState.update { state ->
                        state.copy(
                            rawFiles = listingResult.files,
                            files = sortFiles(listingResult.files, state.sortOption),
                            isCurrentFolderWriteAllowed = listingResult.isWriteAllowed,
                            isLoading = false,
                            errorMessage = null
                        ) 
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            rawFiles = emptyList(),
                            files = emptyList(),
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to connect to device"
                        ) 
                    }
                }
            )
        }
    }

    fun navigateToFolder(file: RemoteFile) {
        if (file.isDirectory) {
            val current = _uiState.value.currentPath
            directoryFocusMap[current] = file.path
            loadDirectory(file.path, targetFocus = directoryFocusMap[file.path])
        }
    }

    fun navigateUp(): Boolean {
        val currentPath = _uiState.value.currentPath
        if (currentPath == "/" || currentPath.isEmpty()) {
            return false
        }
        val parentPath = PathUtils.getParentPath(currentPath)
        val focusForParent = directoryFocusMap[parentPath] ?: currentPath
        loadDirectory(parentPath, targetFocus = focusForParent)
        return true
    }

    fun refresh() {
        deviceResolver.refresh()
        loadDirectory(_uiState.value.currentPath)
    }

    // --- File Explorer Actions ---

    fun createFolder(folderName: String) {
        val target = _uiState.value.device ?: return
        val trimmedName = folderName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(userMessage = "Folder name cannot be empty") }
            return
        }

        _uiState.update { it.copy(isActionLoading = true, showCreateFolderDialog = false) }

        viewModelScope.launch {
            val result = repository.createFolder(
                deviceId = target.id,
                parentPath = _uiState.value.currentPath,
                folderName = trimmedName,
                fallbackDevice = target
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isActionLoading = false, userMessage = "Folder '$trimmedName' created") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isActionLoading = false, userMessage = error.message ?: "Failed to create folder") }
                }
            )
        }
    }

    fun createFile(fileName: String, content: String) {
        val target = _uiState.value.device ?: return
        val trimmedName = fileName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(userMessage = "File name cannot be empty") }
            return
        }

        _uiState.update { it.copy(isActionLoading = true, showCreateFileDialog = false) }

        viewModelScope.launch {
            val result = repository.createFile(
                deviceId = target.id,
                parentPath = _uiState.value.currentPath,
                fileName = trimmedName,
                content = content.toByteArray(),
                fallbackDevice = target
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isActionLoading = false, userMessage = "File '$trimmedName' created") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isActionLoading = false, userMessage = error.message ?: "Failed to create file") }
                }
            )
        }
    }

    fun deleteFile(file: RemoteFile) {
        val target = _uiState.value.device ?: return
        _uiState.update { it.copy(isActionLoading = true, selectedFileForDelete = null) }

        viewModelScope.launch {
            val result = repository.deleteFile(
                deviceId = target.id,
                path = file.path,
                fallbackDevice = target
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isActionLoading = false, userMessage = "'${file.name}' deleted") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isActionLoading = false, userMessage = error.message ?: "Failed to delete file") }
                }
            )
        }
    }

    fun renameFile(file: RemoteFile, newName: String) {
        val target = _uiState.value.device ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank() || trimmedName == file.name) {
            _uiState.update { it.copy(selectedFileForRename = null) }
            return
        }

        _uiState.update { it.copy(isActionLoading = true, selectedFileForRename = null) }

        viewModelScope.launch {
            val result = repository.renameFile(
                deviceId = target.id,
                oldPath = file.path,
                newName = trimmedName,
                fallbackDevice = target
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isActionLoading = false, userMessage = "Renamed to '$trimmedName'") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isActionLoading = false, userMessage = error.message ?: "Failed to rename resource") }
                }
            )
        }
    }

    fun saveToDevice(file: RemoteFile, customDestinationDirectory: okio.Path? = null) {
        val device = _uiState.value.device ?: return
        fileTransferManager.startDownload(device, file, destinationDirectory = customDestinationDirectory)
    }

    fun uploadFiles(localFileSources: List<LocalFileSource>) {
        if (localFileSources.isEmpty()) return
        val state = _uiState.value
        val device = state.device ?: return
        if (!state.isCurrentFolderWriteAllowed) {
            val permDeniedMsg = "Write access is disabled for this folder"
            _uiState.update { currentState ->
                currentState.copy(
                    transferProgress = FileTransferProgress(
                        fileName = localFileSources.firstOrNull()?.name ?: "",
                        direction = TransferDirection.UPLOAD,
                        status = TransferStatus.PAUSED_ERROR,
                        errorMessage = permDeniedMsg,
                        isAutoRetrying = false
                    ),
                    userMessage = permDeniedMsg
                )
            }
            return
        }
        fileTransferManager.startUploadBatch(
            device = device,
            parentPath = state.currentPath,
            sources = localFileSources,
            onSuccess = {
                val count = localFileSources.size
                val msg = if (count == 1) {
                    "Uploaded '${localFileSources.first().name}'"
                } else {
                    "Uploaded $count files"
                }
                _uiState.update { it.copy(userMessage = msg) }
                refresh()
            }
        )
    }

    fun retryTransfer() {
        fileTransferManager.retryTransfer()
    }

    fun cancelTransfer() {
        val direction = _uiState.value.transferProgress?.direction
        fileTransferManager.cancelTransfer()
        val cancelMsg = if (direction == TransferDirection.UPLOAD) "Upload cancelled" else "Save cancelled"
        _uiState.update { it.copy(userMessage = cancelMsg) }
    }

    fun cancelSaveToDevice() = cancelTransfer()

    fun onFileClick(file: RemoteFile) {
        if (file.isDirectory) {
            navigateToFolder(file)
        } else {
            showOpenFileOptionsDialog(file)
        }
    }

    fun showOpenFileOptionsDialog(file: RemoteFile) {
        if (!file.isDirectory) {
            _uiState.update { it.copy(selectedFileForOpenOptions = file) }
        }
    }

    fun dismissOpenFileOptionsDialog() {
        _uiState.update { it.copy(selectedFileForOpenOptions = null) }
    }

    fun directOpenFile(file: RemoteFile) {
        dismissOpenFileOptionsDialog()
        val device = _uiState.value.device ?: return
        if (file.isDirectory) {
            navigateToFolder(file)
            return
        }
        viewModelScope.launch {
            val rawUrl = repository.getDownloadUrl(deviceId = device.id, path = file.path, fallbackDevice = device)
            val mimeType = PathUtils.getMimeType(file.extension)
            val streamableUrl = streamProxyController?.getStreamableUrl(
                downloadUrl = rawUrl,
                fileName = file.name,
                deviceId = device.id,
                path = file.path
            ) ?: rawUrl
            platformFileHandler.openFile(
                downloadUrl = streamableUrl,
                mimeType = mimeType,
                title = file.name,
                fileSize = if (file.size > 0) file.size else null,
                rawUrl = rawUrl
            )
        }
    }

    fun tempSaveAndOpenFile(file: RemoteFile) {
        dismissOpenFileOptionsDialog()
        val device = _uiState.value.device ?: return
        if (file.isDirectory) {
            navigateToFolder(file)
            return
        }
        val tempDir = platformFileHandler.getTemporaryDirectory()
        val tempDestPath = PathUtils.generateUniqueDestinationPath(tempDir, file.name)
        val mimeType = PathUtils.getMimeType(file.extension)

        fileTransferManager.startDownload(
            device = device,
            file = file,
            destinationDirectory = tempDir,
            destinationPath = tempDestPath,
            onSuccess = { savedPath ->
                platformFileHandler.openLocalFile(
                    filePath = savedPath,
                    mimeType = mimeType,
                    title = file.name
                )
                _uiState.update { it.copy(userMessage = "Opened '${file.name}'") }
            }
        )
    }

    fun openFile(file: RemoteFile) {
        onFileClick(file)
    }

    fun shareFile(file: RemoteFile) {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            val url = repository.getDownloadUrl(deviceId = device.id, path = file.path, fallbackDevice = device)
            platformFileHandler.shareFile(url, file.name)
            _uiState.update { it.copy(userMessage = "Share link copied / dispatched") }
        }
    }

    suspend fun getDownloadUrl(file: RemoteFile): String {
        val device = _uiState.value.device ?: return ""
        return repository.getDownloadUrl(deviceId = device.id, path = file.path, fallbackDevice = device)
    }

    fun copyToClipboard(text: String) {
        platformFileHandler.copyToClipboard(text)
        _uiState.update { it.copy(userMessage = "Link copied to clipboard") }
    }

    // --- Dialog Visibility Triggers ---

    fun showCreateFolderDialog() { _uiState.update { it.copy(showCreateFolderDialog = true) } }
    fun dismissCreateFolderDialog() { _uiState.update { it.copy(showCreateFolderDialog = false) } }

    fun showCreateFileDialog() { _uiState.update { it.copy(showCreateFileDialog = true) } }
    fun dismissCreateFileDialog() { _uiState.update { it.copy(showCreateFileDialog = false) } }

    fun showSortDialog() { _uiState.update { it.copy(showSortDialog = true) } }
    fun dismissSortDialog() { _uiState.update { it.copy(showSortDialog = false) } }

    fun showRenameDialog(file: RemoteFile) { _uiState.update { it.copy(selectedFileForRename = file) } }
    fun dismissRenameDialog() { _uiState.update { it.copy(selectedFileForRename = null) } }

    fun showDeleteDialog(file: RemoteFile) { _uiState.update { it.copy(selectedFileForDelete = file) } }
    fun dismissDeleteDialog() { _uiState.update { it.copy(selectedFileForDelete = null) } }

    fun showPropertiesDialog(file: RemoteFile) {
        viewModelScope.launch {
            val url = getDownloadUrl(file)
            _uiState.update { it.copy(selectedFileForProperties = file, propertiesDownloadUrl = url) }
        }
    }
    fun dismissPropertiesDialog() { _uiState.update { it.copy(selectedFileForProperties = null, propertiesDownloadUrl = null) } }

    fun updateSortOption(sortOption: FileSortOption) {
        _uiState.update { state ->
            state.copy(
                sortOption = sortOption,
                files = sortFiles(state.rawFiles, sortOption),
                showSortDialog = false
            )
        }
    }

    fun toggleShowHiddenFiles() {
        val currentOption = _uiState.value.sortOption
        updateSortOption(currentOption.copy(showHiddenFiles = !currentOption.showHiddenFiles))
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // --- Helper Logic ---

    private fun sortFiles(files: List<RemoteFile>, option: FileSortOption): List<RemoteFile> {
        val filtered = if (option.showHiddenFiles) {
            files
        } else {
            files.filter { !it.name.startsWith(".") }
        }

        val comparator = Comparator<RemoteFile> { f1, f2 ->
            if (option.directoriesFirst && f1.isDirectory != f2.isDirectory) {
                return@Comparator if (f1.isDirectory) -1 else 1
            }

            val res = when (option.field) {
                SortField.NAME -> f1.name.compareTo(f2.name, ignoreCase = true)
                SortField.SIZE -> f1.size.compareTo(f2.size)
                SortField.DATE -> f1.lastModifiedTimestamp.compareTo(f2.lastModifiedTimestamp)
                SortField.TYPE -> f1.extension.compareTo(f2.extension, ignoreCase = true)
            }

            if (option.direction == SortDirection.DESCENDING) -res else res
        }

        return filtered.sortedWith(comparator)
    }
}
