package com.hz_apps.foldershare.feature.devices

import com.hz_apps.foldershare.core.discovery.DefaultDeviceResolver
import com.hz_apps.foldershare.core.discovery.DiscoveredDevice
import com.hz_apps.foldershare.core.discovery.ServiceBrowser
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.server.ServerConstants
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.DeviceCredentialEntity
import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private class FakeCredentialDao : DeviceCredentialDao {
    override suspend fun getCredentialForDevice(deviceId: String): DeviceCredentialEntity? = null
    override fun getCredentialFlowForDevice(deviceId: String): Flow<DeviceCredentialEntity?> = flowOf(null)
    override suspend fun saveCredential(credential: DeviceCredentialEntity) {}
    override suspend fun deleteCredentialForDevice(deviceId: String) {}
}

private class FakeServerConfigDao(
    private val config: ServerConfigEntity
) : ServerConfigDao {
    override fun getServerConfigFlow(): Flow<ServerConfigEntity?> = flowOf(config)
    override suspend fun getServerConfig(): ServerConfigEntity? = config
    override suspend fun insertOrUpdateServerConfig(config: ServerConfigEntity) {}
    override suspend fun updateServerConfig(config: ServerConfigEntity) {}
}

private class FakeRemoteFileRepo : RemoteFileRepository {
    override suspend fun listFiles(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): Result<DirectoryListingResult> = Result.failure(NotImplementedError())
    override suspend fun createFolder(deviceId: String, parentPath: String, folderName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun createFile(deviceId: String, parentPath: String, fileName: String, content: ByteArray, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun uploadFile(deviceId: String, parentPath: String, localFileSource: LocalFileSource, onProgress: ((Long, Long?) -> Unit)?, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun downloadFile(deviceId: String, path: String, fileName: String, destinationPath: okio.Path?, destinationDirectory: okio.Path?, onProgress: ((Long, Long?) -> Unit)?, fallbackDevice: RemoteTargetDevice?): Result<String> = Result.failure(NotImplementedError())
    override fun cleanupPartialDownload(destinationPath: okio.Path) {}
    override suspend fun deleteFile(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(deviceId: String, oldPath: String, newName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getDownloadUrl(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): String = ""
    override suspend fun authenticateDevice(deviceId: String, username: String, password: String, fallbackDevice: RemoteTargetDevice?): Result<DirectoryListingResult> = Result.failure(NotImplementedError())
    override suspend fun probeDeviceUuid(hostAddress: String, port: Int, isHttps: Boolean): String? = null
}

private class FakeBrowser : ServiceBrowser {
    val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discovered
    var startDiscoveryCount = 0
    var stopDiscoveryCount = 0
    var refreshDiscoveryCount = 0

    override fun startDiscovery() {
        startDiscoveryCount++
    }
    override fun stopDiscovery() {
        stopDiscoveryCount++
    }
    override fun refreshDiscovery() {
        refreshDiscoveryCount++
    }
}

class DevicesViewModelTest {

    @Test
    fun testParseHostInputPlainIp() {
        val parsed = DevicesViewModel.parseHostInput("192.168.1.50")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertNull(parsed.explicitPort)
        assertNull(parsed.explicitHttps)
    }

    @Test
    fun testParseHostInputWithPort() {
        val parsed = DevicesViewModel.parseHostInput("192.168.1.50:9090")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(9090, parsed.explicitPort)
        assertNull(parsed.explicitHttps)
    }

    @Test
    fun testParseHostInputHttpUrlWithPortAndPath() {
        val parsed = DevicesViewModel.parseHostInput("http://192.168.1.50:8080/webdav/")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(8080, parsed.explicitPort)
        assertEquals(false, parsed.explicitHttps)
    }

    @Test
    fun testParseHostInputHttpsUrl() {
        val parsed = DevicesViewModel.parseHostInput("https://my-host.local:8443")
        assertNotNull(parsed)
        assertEquals("my-host.local", parsed.host)
        assertEquals(8443, parsed.explicitPort)
        assertEquals(true, parsed.explicitHttps)
    }

    @Test
    fun testParseHostInputProtocolWithoutSlashes() {
        // protocol:ip:port
        val parsedHttpPort = DevicesViewModel.parseHostInput("http:192.168.1.50:8080")
        assertNotNull(parsedHttpPort)
        assertEquals("192.168.1.50", parsedHttpPort.host)
        assertEquals(8080, parsedHttpPort.explicitPort)
        assertEquals(false, parsedHttpPort.explicitHttps)

        val parsedHttpsPort = DevicesViewModel.parseHostInput("https:192.168.1.50:8443")
        assertNotNull(parsedHttpsPort)
        assertEquals("192.168.1.50", parsedHttpsPort.host)
        assertEquals(8443, parsedHttpsPort.explicitPort)
        assertEquals(true, parsedHttpsPort.explicitHttps)

        // protocol:ip
        val parsedHttp = DevicesViewModel.parseHostInput("http:192.168.1.50")
        assertNotNull(parsedHttp)
        assertEquals("192.168.1.50", parsedHttp.host)
        assertNull(parsedHttp.explicitPort)
        assertEquals(false, parsedHttp.explicitHttps)

        val parsedHttps = DevicesViewModel.parseHostInput("https:192.168.1.50")
        assertNotNull(parsedHttps)
        assertEquals("192.168.1.50", parsedHttps.host)
        assertNull(parsedHttps.explicitPort)
        assertEquals(true, parsedHttps.explicitHttps)
    }

    @Test
    fun testParseHostInputVariousProtocols() {
        val webdav = DevicesViewModel.parseHostInput("webdav://192.168.1.50:8080/share")
        assertNotNull(webdav)
        assertEquals("192.168.1.50", webdav.host)
        assertEquals(8080, webdav.explicitPort)
        assertEquals(false, webdav.explicitHttps)

        val webdavs = DevicesViewModel.parseHostInput("webdavs://192.168.1.50:8443")
        assertNotNull(webdavs)
        assertEquals("192.168.1.50", webdavs.host)
        assertEquals(8443, webdavs.explicitPort)
        assertEquals(true, webdavs.explicitHttps)

        val foldershare = DevicesViewModel.parseHostInput("foldershare:192.168.1.50:8080")
        assertNotNull(foldershare)
        assertEquals("192.168.1.50", foldershare.host)
        assertEquals(8080, foldershare.explicitPort)
        assertEquals(false, foldershare.explicitHttps)
    }

    @Test
    fun testParseHostInputTrimmingAndSanitization() {
        // Spaces, quotes, brackets, query params, hash
        val messy = DevicesViewModel.parseHostInput("  \"<http://192.168.1.50:8080/path?foo=bar#section>\"  ")
        assertNotNull(messy)
        assertEquals("192.168.1.50", messy.host)
        assertEquals(8080, messy.explicitPort)
        assertEquals(false, messy.explicitHttps)

        // Space before and after port colon
        val spacedPort = DevicesViewModel.parseHostInput("  192.168.1.50 : 9000  ")
        assertNotNull(spacedPort)
        assertEquals("192.168.1.50", spacedPort.host)
        assertEquals(9000, spacedPort.explicitPort)

        // Userinfo in URL
        val userInfo = DevicesViewModel.parseHostInput("http://admin:secret@192.168.1.50:8080/")
        assertNotNull(userInfo)
        assertEquals("192.168.1.50", userInfo.host)
        assertEquals(8080, userInfo.explicitPort)

        // Trailing colon without port
        val trailingColon = DevicesViewModel.parseHostInput("192.168.1.50:")
        assertNotNull(trailingColon)
        assertEquals("192.168.1.50", trailingColon.host)
        assertNull(trailingColon.explicitPort)
    }

    @Test
    fun testParseHostInputIPv6WithPort() {
        val parsed = DevicesViewModel.parseHostInput("[::1]:8080")
        assertNotNull(parsed)
        assertEquals("::1", parsed.host)
        assertEquals(8080, parsed.explicitPort)

        val unbracketed = DevicesViewModel.parseHostInput("fe80::1")
        assertNotNull(unbracketed)
        assertEquals("fe80::1", unbracketed.host)
        assertNull(unbracketed.explicitPort)
    }

    @Test
    fun testCandidateGenerationPrefersDiscoveredDevice() {
        val parsed = DevicesViewModel.parseHostInput("192.168.1.50")!!
        val discovered = listOf(
            DiscoveredDevice(
                id = "dev1",
                name = "Discovered",
                hostAddress = "192.168.1.50",
                port = 34857,
                osDetails = "Linux",
                httpUrl = "http://192.168.1.50:34857",
                webDavUrl = "http://192.168.1.50:34857/",
                isHttps = false
            )
        )
        val candidates = DevicesViewModel.generateCandidateEndpoints(parsed, discovered)
        assertNotNull(candidates)
        assertEquals(Pair(34857, false), candidates.first())
    }

    @Test
    fun testCandidateGenerationProbesAllAppPortsBothProtocols() {
        val parsed = DevicesViewModel.parseHostInput("192.168.1.50")!!
        val candidates = DevicesViewModel.generateCandidateEndpoints(parsed)
        val expectedSize = ServerConstants.ALL_APP_PORTS.size * 2
        assertEquals(expectedSize, candidates.size)
        assertEquals(Pair(ServerConstants.DEFAULT_PORT, false), candidates[0])
        assertEquals(Pair(ServerConstants.DEFAULT_PORT, true), candidates[1])
    }

    @Test
    fun testDeviceItemGroupingAndSelfIdentification() = runBlocking {
        val browser = FakeBrowser()
        val resolver = DefaultDeviceResolver(browser)
        val serverConfig = ServerConfigEntity(deviceUuid = "own-uuid-1234")
        val configDao = FakeServerConfigDao(serverConfig)
        val repo = FakeRemoteFileRepo()
        val credDao = FakeCredentialDao()

        val viewModel = DevicesViewModel(
            serviceBrowser = browser,
            credentialDao = credDao,
            repository = repo,
            deviceResolver = resolver,
            serverConfigDao = configDao
        )

        // Add 2 endpoints for Pixel 7 (Dual Link) and 1 endpoint for Self Device
        val pixelWifi = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.1.2",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.2:8080",
            webDavUrl = "http://192.168.1.2:8080/"
        )
        val pixelEthernet = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.10.5",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.10.5:8080",
            webDavUrl = "http://192.168.10.5:8080/"
        )
        val selfDev = DiscoveredDevice(
            id = "own-uuid-1234",
            name = "My Desktop",
            hostAddress = "192.168.1.100",
            port = 8080,
            osDetails = "Linux",
            httpUrl = "http://192.168.1.100:8080",
            webDavUrl = "http://192.168.1.100:8080/"
        )

        browser._discovered.value = listOf(pixelWifi, pixelEthernet, selfDev)
        delay(100.milliseconds)

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.devices.size)

        val pixelItem = uiState.devices.firstOrNull { it.id == "pixel-7-uuid" }
        assertNotNull(pixelItem)
        assertEquals("Pixel 7", pixelItem.name)
        assertEquals(2, pixelItem.networkLinksCount)
        assertEquals(false, pixelItem.isThisDevice)

        val selfItem = uiState.devices.firstOrNull { it.id == "own-uuid-1234" }
        assertNotNull(selfItem)
        assertEquals("My Desktop", selfItem.name)
        assertEquals(1, selfItem.networkLinksCount)
        assertEquals(true, selfItem.isThisDevice)
    }

    @Test
    fun testStartAndStopScanningToggle() = runBlocking {
        val browser = FakeBrowser()
        val configDao = FakeServerConfigDao(ServerConfigEntity(deviceUuid = "own-uuid"))
        val repo = FakeRemoteFileRepo()
        val credDao = FakeCredentialDao()

        val viewModel = DevicesViewModel(
            serviceBrowser = browser,
            credentialDao = credDao,
            repository = repo,
            serverConfigDao = configDao
        )

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(1, browser.startDiscoveryCount)

        viewModel.stopScanning()
        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(1, browser.stopDiscoveryCount)

        viewModel.startScanning()
        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(2, browser.startDiscoveryCount)
        assertEquals(1, browser.refreshDiscoveryCount)
    }

    @Test
    fun testDiscoveredDevicesUpdatePreservesStoppedScanningState() = runBlocking {
        val browser = FakeBrowser()
        val configDao = FakeServerConfigDao(ServerConfigEntity(deviceUuid = "own-uuid"))
        val repo = FakeRemoteFileRepo()
        val credDao = FakeCredentialDao()

        val viewModel = DevicesViewModel(
            serviceBrowser = browser,
            credentialDao = credDao,
            repository = repo,
            serverConfigDao = configDao
        )

        viewModel.stopScanning()
        assertFalse(viewModel.uiState.value.isSearching)

        // New device emission arrives after scanning was stopped
        browser._discovered.value = listOf(
            DiscoveredDevice(
                id = "device-1",
                name = "Phone",
                hostAddress = "192.168.1.50",
                port = 8080,
                osDetails = "Android",
                httpUrl = "http://192.168.1.50:8080",
                webDavUrl = "http://192.168.1.50:8080/"
            )
        )
        delay(100.milliseconds)

        assertEquals(1, viewModel.uiState.value.devices.size)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun testConnectToManualIpOpensAuthDialogWhenAuthRequired() = runBlocking {
        val browser = FakeBrowser()
        val configDao = FakeServerConfigDao(ServerConfigEntity(deviceUuid = "own-uuid"))
        val repo = FakeRemoteFileRepo()
        val credDao = FakeCredentialDao()

        val mockConnector = object : com.hz_apps.foldershare.core.discovery.DeviceConnector {
            override suspend fun connectManual(
                target: com.hz_apps.foldershare.core.discovery.ParsedConnectionTarget,
                customName: String?,
                discoveredDevices: List<DiscoveredDevice>
            ): com.hz_apps.foldershare.core.discovery.DeviceConnectionResult {
                return com.hz_apps.foldershare.core.discovery.DeviceConnectionResult.NeedsAuthentication(
                    deviceItem = DeviceItem(
                        id = "protected_dev",
                        name = customName ?: "Manual Device",
                        osDetails = "Manual IP • ${target.host}",
                        statusText = "Protected",
                        hostAddress = target.host,
                        port = target.explicitPort ?: 45678,
                        isAuthRequired = true
                    ),
                    isSavedCredentialInvalid = false
                )
            }

            override suspend fun connectDevice(
                device: DeviceItem,
                discoveredDevices: List<DiscoveredDevice>
            ): com.hz_apps.foldershare.core.discovery.DeviceConnectionResult = com.hz_apps.foldershare.core.discovery.DeviceConnectionResult.NeedsAuthentication(device)

            override fun formatConnectionErrorMessage(
                deviceName: String,
                host: String,
                port: Int?,
                error: Throwable?
            ): String = "Error"
        }

        val viewModel = DevicesViewModel(
            serviceBrowser = browser,
            credentialDao = credDao,
            repository = repo,
            serverConfigDao = configDao,
            deviceConnector = mockConnector
        )

        viewModel.showManualIpDialog()
        viewModel.updateManualHost("192.168.1.50")

        var navigated = false
        viewModel.connectToManualIp { _, _, _ -> navigated = true }
        delay(100.milliseconds)

        assertFalse(navigated, "Should NOT navigate directly when authentication is required")
        assertTrue(viewModel.uiState.value.isAuthDialogOpen, "Auth dialog should be opened")
        assertEquals("protected_dev", viewModel.uiState.value.authDevice?.id)
        assertFalse(viewModel.uiState.value.isManualIpDialogOpen)
    }

    @Test
    fun testConnectToManualIpNavigatesWhenOpen() = runBlocking {
        val browser = FakeBrowser()
        val configDao = FakeServerConfigDao(ServerConfigEntity(deviceUuid = "own-uuid"))
        val repo = FakeRemoteFileRepo()
        val credDao = FakeCredentialDao()

        val mockConnector = object : com.hz_apps.foldershare.core.discovery.DeviceConnector {
            override suspend fun connectManual(
                target: com.hz_apps.foldershare.core.discovery.ParsedConnectionTarget,
                customName: String?,
                discoveredDevices: List<DiscoveredDevice>
            ): com.hz_apps.foldershare.core.discovery.DeviceConnectionResult {
                return com.hz_apps.foldershare.core.discovery.DeviceConnectionResult.Success(
                    targetDevice = RemoteTargetDevice(
                        id = "open_dev",
                        name = "Open Device",
                        hostAddress = target.host,
                        port = target.explicitPort ?: 45678
                    )
                )
            }

            override suspend fun connectDevice(
                device: DeviceItem,
                discoveredDevices: List<DiscoveredDevice>
            ): com.hz_apps.foldershare.core.discovery.DeviceConnectionResult = com.hz_apps.foldershare.core.discovery.DeviceConnectionResult.Success(
                RemoteTargetDevice(device.id, device.name, device.hostAddress, device.port)
            )

            override fun formatConnectionErrorMessage(
                deviceName: String,
                host: String,
                port: Int?,
                error: Throwable?
            ): String = "Error"
        }

        val viewModel = DevicesViewModel(
            serviceBrowser = browser,
            credentialDao = credDao,
            repository = repo,
            serverConfigDao = configDao,
            deviceConnector = mockConnector
        )

        viewModel.showManualIpDialog()
        viewModel.updateManualHost("192.168.1.50")

        var navigatedDevice: RemoteTargetDevice? = null
        viewModel.connectToManualIp { target, _, _ -> navigatedDevice = target }
        delay(100.milliseconds)

        assertNotNull(navigatedDevice)
        assertEquals("open_dev", navigatedDevice?.id)
        assertFalse(viewModel.uiState.value.isAuthDialogOpen)
    }
}

