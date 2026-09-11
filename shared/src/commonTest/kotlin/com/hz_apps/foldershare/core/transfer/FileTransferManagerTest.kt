package com.hz_apps.foldershare.core.transfer

import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

open class FakeRemoteFileRepository : RemoteFileRepository {
    var downloadResultToReturn: Result<String> = Result.success("Saved")
    var downloadProgressSimulation: ((onProgress: ((Long, Long?) -> Unit)?) -> Unit)? = null
    var downloadGate: CompletableDeferred<Unit>? = null
    var lastDownloadDestinationPath: okio.Path? = null
    var lastCleanedUpPath: okio.Path? = null

    var uploadResultToReturn: Result<Unit> = Result.success(Unit)
    var uploadProgressSimulation: ((onProgress: ((Long, Long?) -> Unit)?) -> Unit)? = null
    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun downloadFile(
        deviceId: String,
        path: String,
        fileName: String,
        destinationPath: okio.Path?,
        destinationDirectory: okio.Path?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)?,
        fallbackDevice: RemoteTargetDevice?
    ): Result<String> {
        lastDownloadDestinationPath = destinationPath
        downloadProgressSimulation?.invoke(onProgress)
        downloadGate?.await()
        return downloadResultToReturn
    }

    override fun cleanupPartialDownload(destinationPath: okio.Path) {
        lastCleanedUpPath = destinationPath
    }

    override suspend fun uploadFile(
        deviceId: String,
        parentPath: String,
        localFileSource: LocalFileSource,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> {
        uploadProgressSimulation?.invoke(onProgress)
        uploadGate?.await()
        return uploadResultToReturn
    }

    override suspend fun listFiles(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): Result<DirectoryListingResult> = Result.failure(NotImplementedError())
    override suspend fun createFolder(deviceId: String, parentPath: String, folderName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun createFile(deviceId: String, parentPath: String, fileName: String, content: ByteArray, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(deviceId: String, oldPath: String, newName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getDownloadUrl(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): String = ""
    override suspend fun authenticateDevice(deviceId: String, username: String, password: String, fallbackDevice: RemoteTargetDevice?): Result<DirectoryListingResult> = Result.failure(NotImplementedError())
    override suspend fun probeDeviceUuid(hostAddress: String, port: Int, isHttps: Boolean): String? = null
}

class FakePlatformFileHandler : PlatformFileHandler {
    override fun getDefaultDownloadDirectory(): okio.Path = "/downloads".toPath()
    override fun getTemporaryDirectory(): okio.Path = "/temp".toPath()
    override fun getDownloadDestinationPath(fileName: String, customDirectory: okio.Path?): okio.Path =
        (customDirectory ?: getDefaultDownloadDirectory()) / fileName
    override fun openFile(downloadUrl: String, mimeType: String?, title: String?, fileSize: Long?, rawUrl: String?) {}
    override fun openLocalFile(filePath: okio.Path, mimeType: String?, title: String?) {}
    override fun shareFile(downloadUrl: String, title: String) {}
    override fun copyToClipboard(text: String) {}
}

class FakeFileTransferController : FileTransferController {
    var isServiceStarted = false

    override fun startTransferService() {
        isServiceStarted = true
    }

    override fun stopTransferService() {
        isServiceStarted = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FileTransferManagerTest {

    private val fakeDevice = RemoteTargetDevice(
        id = "dev-1",
        name = "Test Phone",
        hostAddress = "192.168.1.5",
        port = 8080,
        isHttps = false
    )

    private val fakeFile = RemoteFile(
        name = "movie.mp4",
        path = "/movie.mp4",
        isDirectory = false,
        size = 10_000_000L
    )

    private val fakeFile2 = RemoteFile(
        name = "photo.jpg",
        path = "/photo.jpg",
        isDirectory = false,
        size = 2_000_000L
    )

    @Test
    fun testStartDownloadInitializesProgressAtZero() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = FakeRemoteFileRepository().apply { downloadGate = gate }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        manager.startDownload(fakeDevice, fakeFile)
        testScheduler.runCurrent()

        val progress = manager.transferProgress.value
        assertNotNull(progress)
        assertEquals("movie.mp4", progress.fileName)
        assertEquals(0L, progress.bytesTransferred)
        assertEquals(10_000_000L, progress.totalBytes)
        assertEquals(0f, progress.progress)
        assertEquals(TransferStatus.IN_PROGRESS, progress.status)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testDownloadFailureSetsPausedErrorAndPreservesProgress() = runTest {
        val fakeRepo = FakeRemoteFileRepository().apply {
            downloadProgressSimulation = { onProgress ->
                onProgress?.invoke(5_000_000L, 10_000_000L)
            }
            downloadResultToReturn = Result.failure(Exception("Network socket timeout"))
        }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        manager.startDownload(fakeDevice, fakeFile)
        advanceUntilIdle()

        val progress = manager.transferProgress.value
        assertNotNull(progress)
        assertEquals(TransferStatus.PAUSED_ERROR, progress.status)
        assertEquals(5_000_000L, progress.bytesTransferred)
        assertEquals(0.5f, progress.progress)
        assertEquals("Network socket timeout", progress.errorMessage)
        assertTrue(progress.isAutoRetrying)
    }

    @Test
    fun testRetryDownloadPreservesProgressAndSetsReconnectingStatus() = runTest {
        val fakeRepo = FakeRemoteFileRepository().apply {
            downloadProgressSimulation = { onProgress ->
                onProgress?.invoke(4_000_000L, 10_000_000L)
            }
            downloadResultToReturn = Result.failure(Exception("Connection lost"))
        }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        manager.startDownload(fakeDevice, fakeFile)
        advanceUntilIdle()

        // First attempt failed at 4MB (40%)
        assertEquals(TransferStatus.PAUSED_ERROR, manager.transferProgress.value?.status)
        assertEquals(4_000_000L, manager.transferProgress.value?.bytesTransferred)

        // Now trigger retry
        val retryGate = CompletableDeferred<Unit>()
        fakeRepo.downloadGate = retryGate
        fakeRepo.downloadProgressSimulation = null // don't emit immediate progress
        manager.retryTransfer()
        testScheduler.runCurrent() // Allow the retry coroutine to start and reach downloadGate

        val reconnectingProgress = manager.transferProgress.value
        assertNotNull(reconnectingProgress)
        assertEquals(TransferStatus.RECONNECTING, reconnectingProgress.status)
        assertEquals(4_000_000L, reconnectingProgress.bytesTransferred)
        assertEquals(0.4f, reconnectingProgress.progress)
        assertEquals("Reconnecting to target device...", reconnectingProgress.errorMessage)

        retryGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun testCancelTransferClearsProgressAndStopsService() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = FakeRemoteFileRepository().apply { downloadGate = gate }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        manager.startDownload(fakeDevice, fakeFile)
        testScheduler.runCurrent()
        assertTrue(fakeController.isServiceStarted)
        assertNotNull(manager.transferProgress.value)

        manager.cancelTransfer()
        testScheduler.runCurrent()
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testConsecutiveDownloadsStartCleanly() = runTest {
        val fakeRepo = FakeRemoteFileRepository()
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        // First download
        manager.startDownload(fakeDevice, fakeFile)
        advanceUntilIdle()
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)

        // Second download starts cleanly without cancellation errors
        val gate = CompletableDeferred<Unit>()
        fakeRepo.downloadGate = gate
        manager.startDownload(fakeDevice, fakeFile2)
        testScheduler.runCurrent()
        val progress2 = manager.transferProgress.value
        assertNotNull(progress2)
        assertEquals("photo.jpg", progress2.fileName)
        assertEquals(0L, progress2.bytesTransferred)
        assertEquals(2_000_000L, progress2.totalBytes)
        assertEquals(true, fakeController.isServiceStarted)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testStartUploadBatchProgress() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = FakeRemoteFileRepository().apply { uploadGate = gate }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        val localSource = object : LocalFileSource {
            override val name: String = "upload_test.txt"
            override val size: Long = 5000L
            override fun openStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(5000))
        }

        manager.startUploadBatch(fakeDevice, "/uploads", listOf(localSource))
        testScheduler.runCurrent()

        val progress = manager.transferProgress.value
        assertNotNull(progress)
        assertEquals("upload_test.txt", progress.fileName)
        assertEquals(TransferDirection.UPLOAD, progress.direction)
        assertEquals(TransferStatus.IN_PROGRESS, progress.status)
        assertEquals(5000L, progress.totalBytes)
        assertEquals(true, fakeController.isServiceStarted)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testStartUploadBatchCallsOnSuccessCallback() = runTest {
        val fakeRepo = FakeRemoteFileRepository()
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        val localSource = object : LocalFileSource {
            override val name: String = "upload_test.txt"
            override val size: Long = 5000L
            override fun openStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(5000))
        }

        var callbackCalled = false
        manager.startUploadBatch(
            device = fakeDevice,
            parentPath = "/uploads",
            sources = listOf(localSource),
            onSuccess = {
                callbackCalled = true
            }
        )
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testCancelTransferCleansUpPartialDownload() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = FakeRemoteFileRepository().apply {
            downloadGate = gate
            downloadProgressSimulation = { onProgress ->
                onProgress?.invoke(2_000_000L, 10_000_000L)
            }
        }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        manager.startDownload(fakeDevice, fakeFile)
        testScheduler.runCurrent()
        manager.cancelTransfer()
        testScheduler.runCurrent()

        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
        assertNotNull(fakeRepo.lastCleanedUpPath)
        assertEquals("/downloads/movie.mp4", fakeRepo.lastCleanedUpPath.toString())
    }

    @Test
    fun testStartDownloadCustomDestinationDirectory() = runTest {
        val fakeRepo = FakeRemoteFileRepository()
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        val customDir = "/custom/downloads".toPath()
        manager.startDownload(fakeDevice, fakeFile, destinationDirectory = customDir)
        advanceUntilIdle()

        assertEquals("/custom/downloads/movie.mp4", fakeRepo.lastDownloadDestinationPath?.toString())
    }

    @Test
    fun testStartDownloadCallsOnSuccessCallback() = runTest {
        val fakeRepo = FakeRemoteFileRepository()
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(fakeRepo, fakeController, platformFileHandler = fakeHandler, dispatcher = StandardTestDispatcher(testScheduler))

        var callbackCalled = false
        var savedPathReceived: okio.Path? = null
        val tempDir = "/temp".toPath()

        manager.startDownload(
            device = fakeDevice,
            file = fakeFile,
            destinationDirectory = tempDir,
            onSuccess = { path ->
                callbackCalled = true
                savedPathReceived = path
            }
        )
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals("/temp/movie.mp4".toPath(), savedPathReceived)
        assertEquals(null, manager.transferProgress.value)
        assertEquals(false, fakeController.isServiceStarted)
    }

    @Test
    fun testProgressUpdatesAreThrottled() = runTest {
        val testTimeSource = kotlin.time.TestTimeSource()
        val gate = CompletableDeferred<Unit>()
        var progressCallback: ((Long, Long?) -> Unit)? = null
        val fakeRepo = FakeRemoteFileRepository().apply {
            downloadGate = gate
            downloadProgressSimulation = { callback ->
                progressCallback = callback
            }
        }
        val fakeHandler = FakePlatformFileHandler()
        val fakeController = FakeFileTransferController()
        val manager = FileTransferManager(
            fakeRepo,
            fakeController,
            platformFileHandler = fakeHandler,
            dispatcher = StandardTestDispatcher(testScheduler),
            throttleDuration = 1.seconds,
            timeSource = testTimeSource
        )

        manager.startDownload(fakeDevice, fakeFile)
        testScheduler.runCurrent()

        // 1st update at t=0s -> should emit
        progressCallback?.invoke(1_000_000L, 10_000_000L)
        testScheduler.runCurrent()
        assertEquals(1_000_000L, manager.transferProgress.value?.bytesTransferred)

        // 2nd update at t=500ms -> should be throttled/ignored
        testTimeSource += 500.milliseconds
        progressCallback?.invoke(2_000_000L, 10_000_000L)
        testScheduler.runCurrent()
        assertEquals(1_000_000L, manager.transferProgress.value?.bytesTransferred)

        // 3rd update at t=1.1s -> should emit
        testTimeSource += 600.milliseconds
        progressCallback?.invoke(3_000_000L, 10_000_000L)
        testScheduler.runCurrent()
        assertEquals(3_000_000L, manager.transferProgress.value?.bytesTransferred)

        // 4th update at 100% completion (even within throttle window) -> must emit immediately
        testTimeSource += 100.milliseconds
        progressCallback?.invoke(10_000_000L, 10_000_000L)
        testScheduler.runCurrent()
        assertEquals(10_000_000L, manager.transferProgress.value?.bytesTransferred)

        gate.complete(Unit)
        advanceUntilIdle()
    }
}
