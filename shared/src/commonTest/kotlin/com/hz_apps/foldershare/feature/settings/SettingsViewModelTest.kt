package com.hz_apps.foldershare.feature.settings

import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
import kotlin.test.assertTrue

private class FakeMutableServerConfigDao(
    initialConfig: ServerConfigEntity = ServerConfigEntity()
) : ServerConfigDao {
    private val _configFlow = MutableStateFlow<ServerConfigEntity?>(initialConfig)

    override fun getServerConfigFlow(): Flow<ServerConfigEntity?> = _configFlow.asStateFlow()

    override suspend fun getServerConfig(): ServerConfigEntity? = _configFlow.value

    override suspend fun insertOrUpdateServerConfig(config: ServerConfigEntity) {
        _configFlow.value = config
    }

    override suspend fun updateServerConfig(config: ServerConfigEntity) {
        _configFlow.value = config
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_hasAuthenticationDisabled() = runTest(testDispatcher) {
        val dao = FakeMutableServerConfigDao()
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAuthRequired)
        assertFalse(dao.getServerConfig()?.isAuthRequired == true)
    }

    @Test
    fun initialState_loadsDeviceNameAndConfigFromDao() = runTest(testDispatcher) {
        val initialConfig = ServerConfigEntity(
            deviceName = "Living Room TV",
            port = 9090,
            isAuthRequired = true,
            isHttpsEnabled = false
        )
        val dao = FakeMutableServerConfigDao(initialConfig)
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Living Room TV", state.deviceName)
        assertEquals(9090, state.serverPort)
        assertTrue(state.isAuthRequired)
        assertFalse(state.isHttpsEnabled)
    }

    @Test
    fun updateDeviceName_persistsTrimmedName() = runTest(testDispatcher) {
        val dao = FakeMutableServerConfigDao()
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        viewModel.updateDeviceName("  My Work Laptop  ")
        advanceUntilIdle()

        assertEquals("My Work Laptop", viewModel.uiState.value.deviceName)
        assertEquals("My Work Laptop", dao.getServerConfig()?.deviceName)
    }

    @Test
    fun updateAuthRequired_updatesStateAndDao() = runTest(testDispatcher) {
        val dao = FakeMutableServerConfigDao()
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        // By default, authentication is not enabled
        assertFalse(viewModel.uiState.value.isAuthRequired)

        // User enables it from settings
        viewModel.updateAuthRequired(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthRequired)
        assertTrue(dao.getServerConfig()?.isAuthRequired == true)

        // User disables it again
        viewModel.updateAuthRequired(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthRequired)
        assertFalse(dao.getServerConfig()?.isAuthRequired == true)
    }

    @Test
    fun updateCredentials_persistsCredentials() = runTest(testDispatcher) {
        val dao = FakeMutableServerConfigDao()
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        viewModel.updateCredentials(" adminUser ", "secret123")
        advanceUntilIdle()

        assertEquals("adminUser", viewModel.uiState.value.username)
        assertEquals("secret123", viewModel.uiState.value.password)
        assertEquals("adminUser", dao.getServerConfig()?.username)
        assertEquals("secret123", dao.getServerConfig()?.password)
    }

    @Test
    fun httpsToggle_showsDialogOnEnable_andDisablesDirectly() = runTest(testDispatcher) {
        val dao = FakeMutableServerConfigDao()
        val viewModel = SettingsViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        // Request enable -> shows dialog
        viewModel.onHttpsToggleRequested(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showHttpsWarningDialog)

        // Confirm -> enables HTTPS and dismisses dialog
        viewModel.confirmEnableHttps()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showHttpsWarningDialog)
        assertTrue(viewModel.uiState.value.isHttpsEnabled)
        assertTrue(dao.getServerConfig()?.isHttpsEnabled == true)

        // Request disable -> disables directly without dialog
        viewModel.onHttpsToggleRequested(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showHttpsWarningDialog)
        assertFalse(viewModel.uiState.value.isHttpsEnabled)
        assertFalse(dao.getServerConfig()?.isHttpsEnabled == true)
    }
}
