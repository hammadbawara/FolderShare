package com.hz_apps.foldershare.feature.explorer

import com.hz_apps.foldershare.core.discovery.DeviceResolver
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import com.hz_apps.foldershare.core.transfer.FakeFileTransferController
import com.hz_apps.foldershare.core.transfer.FakeRemoteFileRepository
import com.hz_apps.foldershare.core.transfer.FileTransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingPlatformFileHandler : PlatformFileHandler {
    var openedUrl: String? = null
    var openedMimeType: String? = null
    var openedTitle: String? = null
    var openedLocalPath: Path? = null
    var openedLocalMimeType: String? = null
    var openedLocalTitle: String? = null

    override fun getDefaultDownloadDirectory(): Path = "/downloads".toPath()
    override fun getTemporaryDirectory(): Path = "/temp".toPath()
    override fun getDownloadDestinationPath(fileName: String, customDirectory: Path?): Path =
        (customDirectory ?: getDefaultDownloadDirectory()) / fileName

    override fun openFile(downloadUrl: String, mimeType: String?, title: String?, fileSize: Long?, rawUrl: String?) {
        openedUrl = downloadUrl
        openedMimeType = mimeType
        openedTitle = title
    }

    override fun openLocalFile(filePath: Path, mimeType: String?, title: String?) {
        openedLocalPath = filePath
        openedLocalMimeType = mimeType
        openedLocalTitle = title
    }

    override fun shareFile(downloadUrl: String, title: String) {}
    override fun copyToClipboard(text: String) {}
}

class TestDeviceResolver : DeviceResolver {
    private val _deviceFlow = MutableStateFlow<RemoteTargetDevice?>(null)
    override fun observeDeviceEndpoint(deviceId: String): StateFlow<RemoteTargetDevice?> = _deviceFlow.asStateFlow()
    override fun resolveEndpoint(deviceId: String, fallback: RemoteTargetDevice?): RemoteTargetDevice? = fallback
    override fun resolveDevice(deviceId: String): com.hz_apps.foldershare.core.discovery.DiscoveredDevice? = null
    override fun observeDevice(deviceId: String): kotlinx.coroutines.flow.Flow<com.hz_apps.foldershare.core.discovery.DiscoveredDevice?> = kotlinx.coroutines.flow.emptyFlow()
    override fun registerManualDevice(device: RemoteTargetDevice) {
        _deviceFlow.value = device
    }
    override fun refresh() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class FileExplorerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testDevice = RemoteTargetDevice(
        id = "dev-1",
        name = "Test Laptop",
        hostAddress = "192.168.1.100",
        port = 8080,
        isHttps = false
    )

    private val testFolder = RemoteFile(
        name = "Documents",
        path = "/Documents",
        isDirectory = true,
        size = 0L,
        lastModifiedTimestamp = 0L
    )

    private val testFile = RemoteFile(
        name = "report.pdf",
        path = "/Documents/report.pdf",
        isDirectory = false,
        size = 1024L,
        lastModifiedTimestamp = 0L
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOnFileClickOnDirectoryNavigatesToFolder() = runTest(testDispatcher) {
        val repo = FakeRemoteFileRepository()
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        viewModel.onFileClick(testFolder)
        advanceUntilIdle()

        assertEquals("/Documents", viewModel.uiState.value.currentPath)
        assertNull(viewModel.uiState.value.selectedFileForOpenOptions)
    }

    @Test
    fun testOnFileClickOnFileShowsOpenOptionsDialog() = runTest(testDispatcher) {
        val repo = FakeRemoteFileRepository()
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        viewModel.onFileClick(testFile)
        advanceUntilIdle()

        assertEquals(testFile, viewModel.uiState.value.selectedFileForOpenOptions)

        viewModel.dismissOpenFileOptionsDialog()
        assertNull(viewModel.uiState.value.selectedFileForOpenOptions)
    }

    @Test
    fun testDirectOpenFileExecutesPlatformOpen() = runTest(testDispatcher) {
        val repo = object : FakeRemoteFileRepository() {
            override suspend fun getDownloadUrl(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): String {
                return "http://192.168.1.100:8080/Documents/report.pdf"
            }
        }
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        viewModel.showOpenFileOptionsDialog(testFile)
        assertEquals(testFile, viewModel.uiState.value.selectedFileForOpenOptions)

        viewModel.directOpenFile(testFile)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedFileForOpenOptions)
        assertEquals("http://192.168.1.100:8080/Documents/report.pdf", platformHandler.openedUrl)
        assertEquals("report.pdf", platformHandler.openedTitle)
        assertEquals("application/pdf", platformHandler.openedMimeType)
    }

    @Test
    fun testTempSaveAndOpenFileDownloadsToTempAndOpensLocalFile() = runTest(testDispatcher) {
        val repo = FakeRemoteFileRepository().apply {
            downloadResultToReturn = Result.success("/temp/report.pdf")
        }
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        viewModel.showOpenFileOptionsDialog(testFile)
        assertEquals(testFile, viewModel.uiState.value.selectedFileForOpenOptions)

        viewModel.tempSaveAndOpenFile(testFile)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedFileForOpenOptions)
        assertEquals("/temp/report.pdf".toPath(), platformHandler.openedLocalPath)
        assertEquals("report.pdf", platformHandler.openedLocalTitle)
        assertEquals("application/pdf", platformHandler.openedLocalMimeType)
    }

    @Test
    fun testUploadFilesRefreshesListAndSetsUserMessageOnSuccess() = runTest(testDispatcher) {
        var listFilesCallCount = 0
        val initialListing = com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult(
            files = emptyList(),
            isWriteAllowed = true
        )
        val refreshedListing = com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult(
            files = listOf(testFile),
            isWriteAllowed = true
        )

        val repo = object : FakeRemoteFileRepository() {
            override suspend fun listFiles(
                deviceId: String,
                path: String,
                fallbackDevice: RemoteTargetDevice?
            ): Result<com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult> {
                listFilesCallCount++
                return Result.success(if (listFilesCallCount == 1) initialListing else refreshedListing)
            }
        }
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        advanceUntilIdle()

        assertEquals(1, listFilesCallCount)
        assertEquals(emptyList(), viewModel.uiState.value.files)

        val localSource = object : com.hz_apps.foldershare.core.explorer.model.LocalFileSource {
            override val name: String = "report.pdf"
            override val size: Long = 1024L
            override fun openStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(1024))
        }

        viewModel.uploadFiles(listOf(localSource))
        advanceUntilIdle()

        assertEquals(2, listFilesCallCount)
        assertEquals(listOf(testFile), viewModel.uiState.value.files)
        assertEquals("Uploaded 'report.pdf'", viewModel.uiState.value.userMessage)
    }

    @Test
    fun testNavigateToFolderAndNavigateUpRestoresTargetFocusPath() = runTest(testDispatcher) {
        val repo = FakeRemoteFileRepository()
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        advanceUntilIdle()

        assertEquals("/", viewModel.uiState.value.currentPath)

        // Navigate into /Documents
        viewModel.navigateToFolder(testFolder)
        advanceUntilIdle()
        assertEquals("/Documents", viewModel.uiState.value.currentPath)

        // Navigate up back to "/"
        val canNavigateUp = viewModel.navigateUp()
        advanceUntilIdle()

        assertEquals(true, canNavigateUp)
        assertEquals("/", viewModel.uiState.value.currentPath)
        // Verify target focus path points to the subfolder we just left
        assertEquals("/Documents", viewModel.uiState.value.targetFocusPath)
    }

    @Test
    fun testSetFocusedPathUpdatesState() = runTest(testDispatcher) {
        val repo = FakeRemoteFileRepository()
        val platformHandler = RecordingPlatformFileHandler()
        val resolver = TestDeviceResolver()
        val transferController = FakeFileTransferController()
        val transferManager = FileTransferManager(repo, transferController, resolver, platformHandler, testDispatcher)

        val viewModel = FileExplorerViewModel(
            repository = repo,
            platformFileHandler = platformHandler,
            deviceResolver = resolver,
            fileTransferManager = transferManager
        )

        viewModel.setTargetDevice(testDevice)
        advanceUntilIdle()

        viewModel.setFocusedPath("/Documents/report.pdf")
        assertEquals("/Documents/report.pdf", viewModel.uiState.value.targetFocusPath)
    }
}
