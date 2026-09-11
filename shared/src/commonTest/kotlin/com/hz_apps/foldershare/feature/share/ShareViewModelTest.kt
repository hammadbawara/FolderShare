package com.hz_apps.foldershare.feature.share

import com.hz_apps.foldershare.core.discovery.ServiceAdvertiser
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionHandler
import com.hz_apps.foldershare.core.permissions.BackgroundPermissionStatus
import com.hz_apps.foldershare.core.server.ServerController
import com.hz_apps.foldershare.core.server.ServerState
import com.hz_apps.foldershare.core.server.ServerStatus
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeServerController : ServerController {
        val stateFlow = MutableStateFlow(ServerState())
        override val serverState = stateFlow.asStateFlow()
        var startServerCalled = false
        var stopServerCalled = false

        override fun startServer() {
            startServerCalled = true
            stateFlow.value = ServerState(status = ServerStatus.RUNNING, port = 28090)
        }

        override fun stopServer() {
            stopServerCalled = true
            stateFlow.value = ServerState(status = ServerStatus.STOPPED)
        }
    }

    private class FakeFolderDao(
        initial: List<FolderConfigEntity> = emptyList()
    ) : FolderConfigDao {
        val flow = MutableStateFlow(initial)
        override fun getAllFolders(): Flow<List<FolderConfigEntity>> = flow.asStateFlow()
        override fun getActiveFolders(): Flow<List<FolderConfigEntity>> = flow.map { l -> l.filter { it.isShared } }
        override suspend fun insertFolder(folder: FolderConfigEntity): Long {
            flow.value = flow.value + folder
            return folder.id
        }
        override suspend fun updateFolder(folder: FolderConfigEntity) {
            flow.value = flow.value.map { if (it.id == folder.id) folder else it }
        }
        override suspend fun deleteFolder(folder: FolderConfigEntity) {
            flow.value = flow.value.filter { it.id != folder.id }
        }
        override suspend fun deleteFolderById(id: Long) {
            flow.value = flow.value.filter { it.id != id }
        }
    }

    private class FakeAdvertiser : ServiceAdvertiser {
        val ips = MutableStateFlow(listOf("192.168.1.50"))
        override val advertisedAddresses: Flow<List<String>> = ips.asStateFlow()
        override fun registerService(
            deviceName: String,
            port: Int,
            osDetails: String,
            isAuthRequired: Boolean,
            isHttpsEnabled: Boolean,
            deviceUuid: String?
        ) {}
        override fun unregisterService() {}
    }

    private class FakeBackgroundPermissionHandler(
        var currentStatus: BackgroundPermissionStatus = BackgroundPermissionStatus(
            isNotificationGranted = true,
            isBatteryOptimizationIgnored = true
        )
    ) : BackgroundPermissionHandler {
        var openAppSettingsCalled = false

        override fun checkStatus(): BackgroundPermissionStatus = currentStatus

        override fun openAppSettings() {
            openAppSettingsCalled = true
        }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testToggleSharingWithoutActiveFoldersFails() = runTest(testDispatcher) {
        val serverController = FakeServerController()
        val folderDao = FakeFolderDao(emptyList())
        val advertiser = FakeAdvertiser()
        val permissionHandler = FakeBackgroundPermissionHandler()
        val viewModel = ShareViewModel(serverController, folderDao, advertiser, permissionHandler)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasActiveFolders)

        viewModel.toggleSharing()
        advanceUntilIdle()

        assertFalse(serverController.startServerCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Please add or enable at least one shared folder"))

        viewModel.clearErrorMessage()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testToggleSharingWithActiveFolderAndGrantedPermissionsStartsServer() = runTest(testDispatcher) {
        val serverController = FakeServerController()
        val initialFolder = FolderConfigEntity(id = 1, name = "Music", path = "/music", isShared = true)
        val folderDao = FakeFolderDao(listOf(initialFolder))
        val advertiser = FakeAdvertiser()
        val permissionHandler = FakeBackgroundPermissionHandler()
        val viewModel = ShareViewModel(serverController, folderDao, advertiser, permissionHandler)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasActiveFolders)

        viewModel.toggleSharing()
        advanceUntilIdle()

        assertTrue(serverController.startServerCalled)
        assertEquals(ServerStatus.RUNNING, viewModel.uiState.value.serverStatus)
        assertTrue(viewModel.uiState.value.isSharing)
        assertFalse(viewModel.uiState.value.showPermissionRationaleDialog)
        assertFalse(viewModel.uiState.value.isBackgroundRestricted)
    }

    @Test
    fun testToggleSharingWithMissingPermissionsShowsRationaleDialog() = runTest(testDispatcher) {
        val serverController = FakeServerController()
        val initialFolder = FolderConfigEntity(id = 1, name = "Music", path = "/music", isShared = true)
        val folderDao = FakeFolderDao(listOf(initialFolder))
        val advertiser = FakeAdvertiser()
        val permissionHandler = FakeBackgroundPermissionHandler(
            currentStatus = BackgroundPermissionStatus(
                isNotificationGranted = false,
                isBatteryOptimizationIgnored = false
            )
        )
        val viewModel = ShareViewModel(serverController, folderDao, advertiser, permissionHandler)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleSharing()
        advanceUntilIdle()

        assertFalse(serverController.startServerCalled)
        assertTrue(viewModel.uiState.value.showPermissionRationaleDialog)
    }

    @Test
    fun testGrantPermissionsStartsServerAndDismissesDialog() = runTest(testDispatcher) {
        val serverController = FakeServerController()
        val initialFolder = FolderConfigEntity(id = 1, name = "Music", path = "/music", isShared = true)
        val folderDao = FakeFolderDao(listOf(initialFolder))
        val advertiser = FakeAdvertiser()
        val permissionHandler = FakeBackgroundPermissionHandler(
            currentStatus = BackgroundPermissionStatus(
                isNotificationGranted = false,
                isBatteryOptimizationIgnored = false
            )
        )
        val viewModel = ShareViewModel(serverController, folderDao, advertiser, permissionHandler)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleSharing()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showPermissionRationaleDialog)

        // Simulate user granting all permissions via system prompt
        viewModel.onPermissionsResult(
            BackgroundPermissionStatus(
                isNotificationGranted = true,
                isBatteryOptimizationIgnored = true
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionRationaleDialog)
        assertTrue(serverController.startServerCalled)
        assertFalse(viewModel.uiState.value.isBackgroundRestricted)
    }

    @Test
    fun testSkipPermissionsStartsServerAndShowsBackgroundWarning() = runTest(testDispatcher) {
        val serverController = FakeServerController()
        val initialFolder = FolderConfigEntity(id = 1, name = "Music", path = "/music", isShared = true)
        val folderDao = FakeFolderDao(listOf(initialFolder))
        val advertiser = FakeAdvertiser()
        val permissionHandler = FakeBackgroundPermissionHandler(
            currentStatus = BackgroundPermissionStatus(
                isNotificationGranted = false,
                isBatteryOptimizationIgnored = true
            )
        )
        val viewModel = ShareViewModel(serverController, folderDao, advertiser, permissionHandler)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleSharing()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showPermissionRationaleDialog)

        // User clicks Skip / Continue Anyway
        viewModel.onSkipPermissions()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionRationaleDialog)
        assertTrue(serverController.startServerCalled)
        assertTrue(viewModel.uiState.value.isBackgroundRestricted)

        // Fix background restrictions
        viewModel.onFixBackgroundRestrictions()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showPermissionRationaleDialog)

        // Granting permissions clears the warning banner
        viewModel.onPermissionsResult(
            BackgroundPermissionStatus(
                isNotificationGranted = true,
                isBatteryOptimizationIgnored = true
            )
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isBackgroundRestricted)
    }
}
