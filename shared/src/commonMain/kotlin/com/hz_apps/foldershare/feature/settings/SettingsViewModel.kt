package com.hz_apps.foldershare.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.core.server.ServerConstants
import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigEntity
import com.hz_apps.foldershare.getPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val deviceName: String = "",
    val defaultDeviceName: String = getPlatform().name,
    val serverPort: Int = ServerConstants.DEFAULT_PORT,
    val isAuthRequired: Boolean = false,
    val username: String = ServerConstants.DEFAULT_USERNAME,
    val password: String = ServerConstants.DEFAULT_PASSWORD,
    val isHttpsEnabled: Boolean = false,
    val showHttpsWarningDialog: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val version: String = "1.0.0"
)

class SettingsViewModel(
    private val serverConfigDao: ServerConfigDao
) : ViewModel() {
    private val _showHttpsWarningDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        serverConfigDao.getServerConfigFlow(),
        _showHttpsWarningDialog
    ) { config, showDialog ->
        val effective = config ?: ServerConfigEntity()
        SettingsUiState(
            isLoaded = config != null,
            deviceName = effective.deviceName,
            serverPort = effective.port,
            isAuthRequired = effective.isAuthRequired,
            username = effective.username,
            password = effective.password,
            isHttpsEnabled = effective.isHttpsEnabled,
            showHttpsWarningDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoaded = false)
    )

    init {
        viewModelScope.launch {
            if (serverConfigDao.getServerConfig() == null) {
                serverConfigDao.insertOrUpdateServerConfig(ServerConfigEntity())
            }
        }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            val current = serverConfigDao.getServerConfig() ?: ServerConfigEntity()
            serverConfigDao.insertOrUpdateServerConfig(current.copy(deviceName = name.trim()))
        }
    }

    fun updateAuthRequired(isAuthRequired: Boolean) {
        viewModelScope.launch {
            val current = serverConfigDao.getServerConfig() ?: ServerConfigEntity()
            serverConfigDao.insertOrUpdateServerConfig(current.copy(isAuthRequired = isAuthRequired))
        }
    }

    fun onHttpsToggleRequested(enabled: Boolean) {
        if (enabled) {
            _showHttpsWarningDialog.value = true
        } else {
            updateHttpsEnabled(false)
        }
    }

    fun confirmEnableHttps() {
        _showHttpsWarningDialog.value = false
        updateHttpsEnabled(true)
    }

    fun dismissHttpsWarningDialog() {
        _showHttpsWarningDialog.value = false
    }

    private fun updateHttpsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = serverConfigDao.getServerConfig() ?: ServerConfigEntity()
            serverConfigDao.insertOrUpdateServerConfig(current.copy(isHttpsEnabled = enabled))
        }
    }

    fun updateCredentials(username: String, pass: String) {
        viewModelScope.launch {
            val current = serverConfigDao.getServerConfig() ?: ServerConfigEntity()
            serverConfigDao.insertOrUpdateServerConfig(
                current.copy(
                    username = username.trim(),
                    password = pass
                )
            )
        }
    }
}
