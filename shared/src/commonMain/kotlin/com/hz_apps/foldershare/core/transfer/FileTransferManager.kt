package com.hz_apps.foldershare.core.transfer

import co.touchlab.kermit.Logger
import com.hz_apps.foldershare.core.discovery.DeviceResolver
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.model.WebDavException
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.explorer.util.PathUtils
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import com.hz_apps.foldershare.core.explorer.util.getPlatformFileHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Application-scoped Manager responsible for executing background file downloads and uploads.
 * Outlives UI ViewModel scope so transfers continue uninterrupted when app is minimized.
 */
class FileTransferManager(
    private val repository: RemoteFileRepository,
    private val fileTransferController: FileTransferController,
    private val deviceResolver: DeviceResolver? = null,
    private val platformFileHandler: PlatformFileHandler = getPlatformFileHandler(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val throttleDuration: Duration = 1.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private val managerScope = CoroutineScope(dispatcher + SupervisorJob())
    private val logger = Logger.withTag("FileTransferManager")
    private val mutex = Mutex()

    private val _transferProgress = MutableStateFlow<FileTransferProgress?>(null)
    val transferProgress: StateFlow<FileTransferProgress?> = _transferProgress.asStateFlow()

    private var transferJob: Job? = null
    private var autoRetryJob: Job? = null
    private var deviceObservationJob: Job? = null

    private var currentDownloadDevice: RemoteTargetDevice? = null
    private var currentDownloadFile: RemoteFile? = null
    private var currentDownloadDestinationDirectory: okio.Path? = null
    private var currentDownloadDestinationPath: okio.Path? = null
    private var currentDownloadOnSuccess: ((savedPath: okio.Path) -> Unit)? = null

    private var currentUploadDevice: RemoteTargetDevice? = null
    private var currentUploadParentPath: String = "/"
    private var currentUploadSources: List<LocalFileSource> = emptyList()
    private var currentUploadOnSuccess: (() -> Unit)? = null
    private var retryAttemptCount = 0

    private fun createThrottledProgressCallback(
        fallbackTotal: Long?,
        onEmit: (transferred: Long, total: Long?) -> Unit
    ): (bytes: Long, total: Long?) -> Unit {
        var lastMark: TimeMark? = null
        return { bytes, total ->
            val resolvedTotal = total ?: fallbackTotal
            val isComplete = resolvedTotal != null && bytes >= resolvedTotal
            val mark = lastMark
            if (isComplete || mark == null || mark.elapsedNow() >= throttleDuration) {
                lastMark = timeSource.markNow()
                onEmit(bytes, resolvedTotal)
            }
        }
    }

    private fun clearState() {
        retryAttemptCount = 0
        currentDownloadDevice = null
        currentDownloadFile = null
        currentDownloadDestinationDirectory = null
        currentDownloadDestinationPath = null
        currentDownloadOnSuccess = null
        currentUploadDevice = null
        currentUploadParentPath = "/"
        currentUploadSources = emptyList()
        currentUploadOnSuccess = null
    }

    private fun startDeviceObservation(deviceId: String) {
        deviceObservationJob?.cancel()
        deviceObservationJob = managerScope.launch {
            deviceResolver?.observeDeviceEndpoint(deviceId)?.collect { endpoint ->
                if (endpoint != null) {
                    val current = _transferProgress.value
                    if (current != null && current.status == TransferStatus.PAUSED_ERROR && current.isAutoRetrying) {
                        logger.d { "Device observation resolved endpoint, attempting retry..." }
                        retryTransfer(isAutoRetry = true)
                    }
                }
            }
        }
    }

    private fun computeInitialProgress(
        fileName: String,
        totalSize: Long?,
        direction: TransferDirection,
        isRetry: Boolean,
        currentFileIndex: Int = 1,
        totalFilesCount: Int = 1
    ): FileTransferProgress {
        val existing = _transferProgress.value
        val isSameFile = isRetry && existing != null && existing.fileName == fileName
        val initialBytes = if (isSameFile) existing.bytesTransferred else 0L
        val initialProgress = if (isSameFile) {
            existing.progress
        } else if (totalSize != null && totalSize > 0) {
            (initialBytes.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
        val initialStatus = if (isSameFile) TransferStatus.RECONNECTING else TransferStatus.IN_PROGRESS
        val initialError = if (isSameFile) {
            "Reconnecting to target device..."
        } else null

        return FileTransferProgress(
            fileName = fileName,
            bytesTransferred = initialBytes,
            totalBytes = totalSize,
            progress = initialProgress,
            direction = direction,
            status = initialStatus,
            errorMessage = initialError,
            isAutoRetrying = isSameFile,
            currentFileIndex = currentFileIndex,
            totalFilesCount = totalFilesCount
        )
    }

    private fun handleTransferFailure(error: Throwable?) {
        if (error is CancellationException) {
            logger.d { "handleTransferFailure: Ignoring CancellationException" }
            return
        }

        val webDavEx = error as? WebDavException
        val isRetryable = webDavEx?.isRetryable ?: (error?.message?.contains("Write access", ignoreCase = true) != true)
        val userFriendlyMsg = webDavEx?.message ?: error?.message ?: "Transfer interrupted"

        logger.e(error) { "handleTransferFailure: $userFriendlyMsg, isRetryable=$isRetryable" }

        _transferProgress.update { current ->
            current?.copy(
                status = TransferStatus.PAUSED_ERROR,
                errorMessage = userFriendlyMsg,
                isAutoRetrying = isRetryable
            )
        }

        if (isRetryable && retryAttemptCount < 5) {
            startAutoRetryLoop()
        }
    }

    fun startDownload(
        device: RemoteTargetDevice,
        file: RemoteFile,
        destinationDirectory: okio.Path? = null,
        destinationPath: okio.Path? = null,
        isRetry: Boolean = false,
        onSuccess: ((savedPath: okio.Path) -> Unit)? = null
    ) {
        managerScope.launch {
            mutex.withLock {
                logger.d { "startDownload: fileName=${file.name}, isRetry=$isRetry" }
                if (isRetry && _transferProgress.value == null) {
                    logger.w { "startDownload: retry aborted because transfer was cancelled" }
                    return@withLock
                }

                val oldJob = transferJob
                transferJob = null
                oldJob?.cancelAndJoin()
                logger.d { "startDownload: old job cancelled and joined" }

                if (!isRetry) {
                    clearState()
                    currentDownloadDestinationDirectory = destinationDirectory
                    currentDownloadDestinationPath = destinationPath ?: run {
                        val dir = destinationDirectory ?: platformFileHandler.getDefaultDownloadDirectory()
                        PathUtils.generateUniqueDestinationPath(dir, file.name)
                    }
                    currentDownloadOnSuccess = onSuccess
                }
                deviceResolver?.registerManualDevice(device)
                currentDownloadDevice = device
                currentDownloadFile = file
                
                autoRetryJob?.cancel()
                startDeviceObservation(device.id)

                fileTransferController.startTransferService()
                val initialTotal = if (file.size > 0) file.size else null
                _transferProgress.value = computeInitialProgress(
                    fileName = file.name,
                    totalSize = initialTotal,
                    direction = TransferDirection.DOWNLOAD,
                    isRetry = isRetry
                )

                transferJob = managerScope.launch {
                    try {
                        logger.d { "Executing repository.downloadFile for ${file.name}" }
                        val progressCallback = createThrottledProgressCallback(initialTotal) { downloaded, total ->
                            _transferProgress.update { current ->
                                if (current?.fileName != file.name) return@update current
                                current.copy(
                                    bytesTransferred = downloaded,
                                    totalBytes = total,
                                    progress = if (total != null && total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null,
                                    status = TransferStatus.IN_PROGRESS,
                                    errorMessage = null,
                                    isAutoRetrying = false
                                )
                            }
                        }

                        val result = repository.downloadFile(
                            deviceId = device.id,
                            path = file.path,
                            fileName = file.name,
                            destinationPath = currentDownloadDestinationPath,
                            destinationDirectory = currentDownloadDestinationDirectory,
                            onProgress = progressCallback,
                            fallbackDevice = device
                        )

                        result.fold(
                            onSuccess = { savedPathString ->
                                logger.d { "Download successful: $savedPathString" }
                                mutex.withLock {
                                    if (currentDownloadFile?.name != file.name) {
                                        logger.w { "Download finished for ${file.name} but active transfer is different. Ignoring." }
                                        return@withLock
                                    }
                                    val resolvedSavedPath = currentDownloadDestinationPath ?: savedPathString.toPath()
                                    val callback = currentDownloadOnSuccess
                                    clearState()
                                    _transferProgress.value = null
                                    fileTransferController.stopTransferService()
                                    callback?.invoke(resolvedSavedPath)
                                }
                            },
                            onFailure = { error ->
                                mutex.withLock {
                                    if (currentDownloadFile?.name != file.name) {
                                        logger.w { "Download failed for ${file.name} but active transfer is different. Ignoring." }
                                        return@withLock
                                    }
                                    handleTransferFailure(error)
                                }
                            }
                        )
                    } finally {
                        logger.d { "Download coroutine finished/cancelled for ${file.name}" }
                    }
                }
            }
        }
    }

    fun startUploadBatch(
        device: RemoteTargetDevice,
        parentPath: String,
        sources: List<LocalFileSource>,
        isRetry: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (sources.isEmpty()) return

        managerScope.launch {
            mutex.withLock {
                logger.d { "startUploadBatch: count=${sources.size}, isRetry=$isRetry" }
                if (isRetry && _transferProgress.value == null) {
                    logger.w { "startUploadBatch: retry aborted because transfer was cancelled" }
                    return@withLock
                }
                
                val oldJob = transferJob
                transferJob = null
                oldJob?.cancelAndJoin()

                if (!isRetry) {
                    clearState()
                    currentUploadOnSuccess = onSuccess
                }
                deviceResolver?.registerManualDevice(device)
                currentUploadDevice = device
                currentUploadParentPath = parentPath
                currentUploadSources = sources
                
                autoRetryJob?.cancel()
                startDeviceObservation(device.id)

                fileTransferController.startTransferService()
                val firstSource = sources.first()
                val initialTotal = if (firstSource.size > 0) firstSource.size else null
                _transferProgress.value = computeInitialProgress(
                    fileName = firstSource.name,
                    totalSize = initialTotal,
                    direction = TransferDirection.UPLOAD,
                    isRetry = isRetry,
                    currentFileIndex = 1,
                    totalFilesCount = sources.size
                )

                transferJob = managerScope.launch {
                    try {
                        val totalFiles = sources.size
                        for ((index, localFileSource) in sources.withIndex()) {
                            if (!isActive) break
                            logger.d { "Executing repository.uploadFile for ${localFileSource.name} (${index + 1}/$totalFiles)" }

                            val sourceTotal = if (localFileSource.size > 0) localFileSource.size else null
                            if (index > 0) {
                                mutex.withLock {
                                    _transferProgress.value = computeInitialProgress(
                                        fileName = localFileSource.name,
                                        totalSize = sourceTotal,
                                        direction = TransferDirection.UPLOAD,
                                        isRetry = isRetry,
                                        currentFileIndex = index + 1,
                                        totalFilesCount = totalFiles
                                    )
                                }
                            }

                            val progressCallback = createThrottledProgressCallback(sourceTotal) { bytesUploaded, totalBytes ->
                                _transferProgress.update { current ->
                                    if (current?.fileName != localFileSource.name) return@update current
                                    current.copy(
                                        bytesTransferred = bytesUploaded,
                                        totalBytes = totalBytes,
                                        progress = if (totalBytes != null && totalBytes > 0) (bytesUploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null,
                                        status = TransferStatus.IN_PROGRESS,
                                        errorMessage = null,
                                        isAutoRetrying = false
                                    )
                                }
                            }

                            val result = repository.uploadFile(
                                deviceId = device.id,
                                parentPath = parentPath,
                                localFileSource = localFileSource,
                                onProgress = progressCallback,
                                fallbackDevice = device
                            )

                            if (result.isFailure) {
                                mutex.withLock {
                                    if (currentUploadSources !== sources) {
                                        logger.w { "Upload failed but active transfer is different. Ignoring." }
                                        return@withLock
                                    }
                                    handleTransferFailure(result.exceptionOrNull())
                                }
                                return@launch
                            }
                        }

                        // Batch completed successfully
                        logger.d { "Upload batch completed successfully" }
                        mutex.withLock {
                            if (currentUploadSources !== sources) return@withLock
                            val callback = currentUploadOnSuccess
                            clearState()
                            _transferProgress.value = null
                            fileTransferController.stopTransferService()
                            callback?.invoke()
                        }
                    } finally {
                        logger.d { "Upload batch coroutine finished/cancelled" }
                    }
                }
            }
        }
    }

    fun cancelTransfer() {
        managerScope.launch {
            mutex.withLock {
                logger.d { "cancelTransfer initiated" }
                
                val oldJob = transferJob
                transferJob = null
                oldJob?.cancelAndJoin()
                logger.d { "cancelTransfer: old job cancelled and joined" }

                autoRetryJob?.cancel()
                deviceObservationJob?.cancel()
                autoRetryJob = null
                deviceObservationJob = null

                val destPath = currentDownloadDestinationPath
                if (destPath != null) {
                    logger.d { "Cleaning up partial download at $destPath" }
                    repository.cleanupPartialDownload(destPath)
                }

                clearState()

                _transferProgress.value = null
                fileTransferController.stopTransferService()
                logger.d { "cancelTransfer completed" }
            }
        }
    }

    fun retryTransfer(isAutoRetry: Boolean = false) {
        managerScope.launch {
            mutex.withLock {
                if (_transferProgress.value == null) return@withLock
                
                logger.d { "retryTransfer initiated, isAutoRetry=$isAutoRetry" }
                if (isAutoRetry) {
                    retryAttemptCount++
                } else {
                    retryAttemptCount = 0
                }
                executeRetry()
            }
        }
    }

    // Assumes mutex is locked
    private fun executeRetry() {
        autoRetryJob?.cancel()
        autoRetryJob = null

        val current = _transferProgress.value ?: return

        when (current.direction) {
            TransferDirection.DOWNLOAD -> {
                val dev = currentDownloadDevice
                val file = currentDownloadFile
                if (dev != null && file != null) {
                    val updatedDev = deviceResolver?.resolveEndpoint(dev.id, dev) ?: dev
                    currentDownloadDevice = updatedDev
                    // startDownload will acquire the lock again, so we need to call it without locking?
                    // wait, Kotlin Mutex is not reentrant! We must not call startDownload while holding the lock if startDownload acquires it.
                    // So we must release the lock or launch a coroutine to do it.
                    managerScope.launch {
                        startDownload(
                            device = updatedDev,
                            file = file,
                            destinationDirectory = currentDownloadDestinationDirectory,
                            destinationPath = currentDownloadDestinationPath,
                            isRetry = true
                        )
                    }
                } else {
                    managerScope.launch { cancelTransfer() }
                }
            }
            TransferDirection.UPLOAD -> {
                val dev = currentUploadDevice
                val path = currentUploadParentPath
                val sources = currentUploadSources
                if (dev != null && sources.isNotEmpty()) {
                    val updatedDev = deviceResolver?.resolveEndpoint(dev.id, dev) ?: dev
                    currentUploadDevice = updatedDev
                    managerScope.launch {
                        startUploadBatch(updatedDev, path, sources, isRetry = true)
                    }
                } else {
                    managerScope.launch { cancelTransfer() }
                }
            }
        }
    }

    private fun startAutoRetryLoop() {
        autoRetryJob?.cancel()
        autoRetryJob = managerScope.launch {
            val count = mutex.withLock { retryAttemptCount }
            val delayMs = (3000L * (1L shl count.coerceAtMost(4))).coerceAtMost(30000L)
            logger.d { "startAutoRetryLoop: waiting $delayMs ms before retry" }
            delay(delayMs.milliseconds)
            if (!isActive) return@launch
            deviceResolver?.refresh()
            retryTransfer(isAutoRetry = true)
        }
    }
}
