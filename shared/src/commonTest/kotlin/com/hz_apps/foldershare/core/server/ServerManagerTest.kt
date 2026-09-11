package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.discovery.ServiceAdvertiser
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerManagerTest {

    private class FakeServiceAdvertiser : ServiceAdvertiser {
        val registeredServices = mutableListOf<String>()
        private val _advertisedAddresses = MutableStateFlow<List<String>>(emptyList())
        override val advertisedAddresses: Flow<List<String>> = _advertisedAddresses.asStateFlow()

        override fun registerService(
            deviceName: String,
            port: Int,
            osDetails: String,
            isAuthRequired: Boolean,
            isHttpsEnabled: Boolean,
            deviceUuid: String?
        ) {
            registeredServices.add(deviceName)
        }

        override fun unregisterService() {
            registeredServices.clear()
        }
    }

    private class FakeFolderConfigDao(
        initialFolders: List<FolderConfigEntity> = emptyList()
    ) : FolderConfigDao {
        val foldersFlow = MutableStateFlow(initialFolders)

        override fun getAllFolders(): Flow<List<FolderConfigEntity>> = foldersFlow.asStateFlow()

        override fun getActiveFolders(): Flow<List<FolderConfigEntity>> =
            foldersFlow.map { list -> list.filter { it.isShared } }

        override suspend fun insertFolder(folder: FolderConfigEntity): Long {
            foldersFlow.value = foldersFlow.value + folder
            return folder.id
        }

        override suspend fun updateFolder(folder: FolderConfigEntity) {
            foldersFlow.value = foldersFlow.value.map { if (it.id == folder.id) folder else it }
        }

        override suspend fun deleteFolder(folder: FolderConfigEntity) {
            foldersFlow.value = foldersFlow.value.filter { it.id != folder.id }
        }

        override suspend fun deleteFolderById(id: Long) {
            foldersFlow.value = foldersFlow.value.filter { it.id != id }
        }
    }

    private class FakeServerConfigDao : ServerConfigDao {
        val configFlow = MutableStateFlow<ServerConfigEntity?>(ServerConfigEntity(port = 28090, isHttpsEnabled = false))
        override fun getServerConfigFlow(): Flow<ServerConfigEntity?> = configFlow.asStateFlow()
        override suspend fun getServerConfig(): ServerConfigEntity? = configFlow.value
        override suspend fun insertOrUpdateServerConfig(config: ServerConfigEntity) { configFlow.value = config }
        override suspend fun updateServerConfig(config: ServerConfigEntity) { configFlow.value = config }
    }

    @Test
    fun testStartServerFailsWhenNoFoldersExist() = runBlocking {
        val advertiser = FakeServiceAdvertiser()
        val folderDao = FakeFolderConfigDao(emptyList())
        val serverConfigDao = FakeServerConfigDao()
        val manager = ServerManager(advertiser, folderDao, serverConfigDao)

        val result = manager.startServer()
        assertTrue(result.isFailure)
        assertEquals(ServerStatus.ERROR, manager.serverState.value.status)
        assertTrue(manager.serverState.value.errorMessage?.contains("No active shared folders") == true)
        assertEquals(0, advertiser.registeredServices.size)
    }

    @Test
    fun testStartServerFailsWhenFoldersAreNotShared() = runBlocking {
        val advertiser = FakeServiceAdvertiser()
        val folderDao = FakeFolderConfigDao(
            listOf(
                FolderConfigEntity(id = 1, name = "Downloads", path = "/downloads", isShared = false)
            )
        )
        val serverConfigDao = FakeServerConfigDao()
        val manager = ServerManager(advertiser, folderDao, serverConfigDao)

        val result = manager.startServer()
        assertTrue(result.isFailure)
        assertEquals(ServerStatus.ERROR, manager.serverState.value.status)
        assertFalse(manager.serverState.value.isSharing)
    }

    @Test
    fun testStartServerSucceedsWithActiveFolderAndAutoStopsWhenDeactivated() = runBlocking {
        val advertiser = FakeServiceAdvertiser()
        val initialFolder = FolderConfigEntity(id = 1, name = "SharedDocs", path = "/docs", isShared = true)
        val folderDao = FakeFolderConfigDao(listOf(initialFolder))
        val serverConfigDao = FakeServerConfigDao()
        val manager = ServerManager(advertiser, folderDao, serverConfigDao)

        val result = manager.startServer()
        try {
            assertTrue(result.isSuccess)
            assertEquals(ServerStatus.RUNNING, manager.serverState.value.status)
            assertTrue(manager.serverState.value.isSharing)

            // Deactivate folder while running
            folderDao.updateFolder(initialFolder.copy(isShared = false))

            // Allow coroutine watcher to react and await stop status
            kotlinx.coroutines.withTimeout(5000) {
                manager.serverState.first { it.status == ServerStatus.STOPPED }
            }

            // Server should automatically stop
            assertEquals(ServerStatus.STOPPED, manager.serverState.value.status)
            assertFalse(manager.serverState.value.isSharing)
        } finally {
            manager.stopServer()
        }
    }
}
