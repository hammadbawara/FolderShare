package com.hz_apps.foldershare.core.discovery

import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.DeviceCredentialEntity
import com.hz_apps.foldershare.feature.devices.DeviceItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestCredentialDao : DeviceCredentialDao {
    val credentials = mutableMapOf<String, DeviceCredentialEntity>()

    override suspend fun getCredentialForDevice(deviceId: String): DeviceCredentialEntity? = credentials[deviceId]
    override fun getCredentialFlowForDevice(deviceId: String): Flow<DeviceCredentialEntity?> = flowOf(credentials[deviceId])
    override suspend fun saveCredential(credential: DeviceCredentialEntity) {
        credentials[credential.deviceId] = credential
    }
    override suspend fun deleteCredentialForDevice(deviceId: String) {
        credentials.remove(deviceId)
    }
}

private class TestRemoteFileRepository(
    var probedUuid: String? = null,
    var listFilesResult: Result<DirectoryListingResult> = Result.success(DirectoryListingResult(files = emptyList())),
    var authResult: Result<DirectoryListingResult> = Result.success(DirectoryListingResult(files = emptyList())),
    var listFilesHandler: ((deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?) -> Result<DirectoryListingResult>)? = null
) : RemoteFileRepository {
    var probeUuidCalledWith: Triple<String, Int, Boolean>? = null
    var listFilesCalls = mutableListOf<Triple<String, String, RemoteTargetDevice?>>()
    var authCalls = mutableListOf<Triple<String, String, String>>()

    override suspend fun probeDeviceUuid(hostAddress: String, port: Int, isHttps: Boolean): String? {
        probeUuidCalledWith = Triple(hostAddress, port, isHttps)
        return probedUuid
    }

    override suspend fun listFiles(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<DirectoryListingResult> {
        listFilesCalls.add(Triple(deviceId, path, fallbackDevice))
        val handler = listFilesHandler
        return if (handler != null) handler(deviceId, path, fallbackDevice) else listFilesResult
    }

    override suspend fun authenticateDevice(
        deviceId: String,
        username: String,
        password: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<DirectoryListingResult> {
        authCalls.add(Triple(deviceId, username, password))
        return authResult
    }

    override suspend fun createFolder(deviceId: String, parentPath: String, folderName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.success(Unit)
    override suspend fun createFile(deviceId: String, parentPath: String, fileName: String, content: ByteArray, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.success(Unit)
    override suspend fun uploadFile(deviceId: String, parentPath: String, localFileSource: LocalFileSource, onProgress: ((Long, Long?) -> Unit)?, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.success(Unit)
    override suspend fun downloadFile(deviceId: String, path: String, fileName: String, destinationPath: okio.Path?, destinationDirectory: okio.Path?, onProgress: ((Long, Long?) -> Unit)?, fallbackDevice: RemoteTargetDevice?): Result<String> = Result.success("")
    override fun cleanupPartialDownload(destinationPath: okio.Path) {}
    override suspend fun deleteFile(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.success(Unit)
    override suspend fun renameFile(deviceId: String, oldPath: String, newName: String, fallbackDevice: RemoteTargetDevice?): Result<Unit> = Result.success(Unit)
    override suspend fun getDownloadUrl(deviceId: String, path: String, fallbackDevice: RemoteTargetDevice?): String = ""
}

class DeviceConnectorTest {

    @Test
    fun testConnectManualOpenServer() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = "remote-uuid-123",
            listFilesResult = Result.success(DirectoryListingResult(files = emptyList()))
        )
        val credDao = TestCredentialDao()
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("192.168.1.50:8080")!!
        val result = connector.connectManual(target, customName = "My Server")

        assertTrue(result is DeviceConnectionResult.Success)
        assertEquals("remote-uuid-123", result.targetDevice.id)
        assertEquals("My Server", result.targetDevice.name)
        assertEquals(8080, result.targetDevice.port)
        assertNull(result.username)
        assertNull(result.password)
    }

    @Test
    fun testConnectManualProtectedServerWithoutCredentialsPromptsAuth() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = "protected-uuid-456",
            listFilesResult = Result.failure(Exception("HTTP 401 Unauthorized"))
        )
        val credDao = TestCredentialDao()
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("192.168.1.50:8080")!!
        val result = connector.connectManual(target, customName = "Protected Server")

        assertTrue(result is DeviceConnectionResult.NeedsAuthentication)
        assertEquals("protected-uuid-456", result.deviceItem.id)
        assertTrue(result.deviceItem.isAuthRequired)
        assertFalse(result.isSavedCredentialInvalid)
    }

    @Test
    fun testConnectManualProtectedServerWithSavedCredentials() = runBlocking {
        var callCount = 0
        val repo = TestRemoteFileRepository(
            probedUuid = "protected-uuid-456",
            listFilesHandler = { _, _, _ ->
                callCount++
                if (callCount == 1) {
                    // First call is probe without credentials -> 401 Unauthorized
                    Result.failure(Exception("HTTP 401 Unauthorized"))
                } else {
                    // Subsequent call with saved credentials -> success
                    Result.success(DirectoryListingResult(files = emptyList()))
                }
            }
        )
        val credDao = TestCredentialDao()
        credDao.saveCredential(
            DeviceCredentialEntity(
                deviceId = "protected-uuid-456",
                username = "admin",
                password = "savedpassword"
            )
        )
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("192.168.1.50:8080")!!
        val result = connector.connectManual(target, customName = null)

        assertTrue(result is DeviceConnectionResult.Success)
        assertEquals("admin", result.username)
        assertEquals("savedpassword", result.password)
    }

    @Test
    fun testConnectManualProtectedServerWithInvalidSavedCredentials() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = "protected-uuid-456",
            listFilesResult = Result.failure(Exception("HTTP 401 Unauthorized"))
        )
        val credDao = TestCredentialDao()
        credDao.saveCredential(
            DeviceCredentialEntity(
                deviceId = "protected-uuid-456",
                username = "admin",
                password = "wrongpassword"
            )
        )
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("192.168.1.50:8080")!!
        val result = connector.connectManual(target, customName = null)

        assertTrue(result is DeviceConnectionResult.NeedsAuthentication)
        assertTrue(result.isSavedCredentialInvalid)
    }

    @Test
    fun testConnectManualWithUrlCredentials() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = "auth-uuid-789",
            listFilesResult = Result.failure(Exception("401 Unauthorized")),
            authResult = Result.success(DirectoryListingResult(files = emptyList()))
        )
        val credDao = TestCredentialDao()
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("http://alice:supersecret@192.168.1.50:8080")!!
        val result = connector.connectManual(target, customName = "Alice Server")

        assertTrue(result is DeviceConnectionResult.Success)
        assertEquals("alice", result.username)
        assertEquals("supersecret", result.password)

        // Verify credentials saved
        val saved = credDao.getCredentialForDevice("auth-uuid-789")
        assertNotNull(saved)
        assertEquals("alice", saved.username)
        assertEquals("supersecret", saved.password)
    }

    @Test
    fun testConnectManualUnreachableHostFails() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = null,
            listFilesResult = Result.failure(Exception("Connection refused"))
        )
        val credDao = TestCredentialDao()
        val connector = DefaultDeviceConnector(repo, credDao)

        val target = ConnectionTargetParser.parse("192.168.1.99:8080")!!
        val result = connector.connectManual(target, customName = "Dead Host")

        assertTrue(result is DeviceConnectionResult.Failure)
        assertTrue(result.errorMessage.contains("Connection refused"))
    }

    @Test
    fun testConnectDeviceDiscoveredProtected() = runBlocking {
        val repo = TestRemoteFileRepository(
            probedUuid = "disc-uuid-1",
            listFilesResult = Result.failure(Exception("401 Unauthorized"))
        )
        val credDao = TestCredentialDao()
        val connector = DefaultDeviceConnector(repo, credDao)

        val deviceItem = DeviceItem(
            id = "disc-uuid-1",
            name = "Discovered Phone",
            osDetails = "Android",
            statusText = "Protected",
            hostAddress = "192.168.1.20",
            port = 45678,
            isAuthRequired = true
        )

        val result = connector.connectDevice(deviceItem)
        assertTrue(result is DeviceConnectionResult.NeedsAuthentication)
        assertFalse(result.isSavedCredentialInvalid)
    }
}
