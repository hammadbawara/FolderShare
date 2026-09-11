package com.hz_apps.foldershare.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.DeviceCredentialEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceAuthUiState(
    val device: DeviceItem? = null,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class DeviceAuthViewModel(
    private val repository: RemoteFileRepository,
    private val credentialDao: DeviceCredentialDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceAuthUiState())
    val uiState: StateFlow<DeviceAuthUiState> = _uiState.asStateFlow()

    fun setTargetDevice(device: DeviceItem, initialErrorMessage: String? = null) {
        _uiState.update {
            DeviceAuthUiState(
                device = device,
                errorMessage = initialErrorMessage
            )
        }
        viewModelScope.launch {
            var savedCredential = credentialDao.getCredentialForDevice(device.id)
            if (savedCredential == null) {
                val probedUuid = repository.probeDeviceUuid(device.hostAddress, device.port, device.isHttps)
                if (!probedUuid.isNullOrBlank()) {
                    savedCredential = credentialDao.getCredentialForDevice(probedUuid)
                }
            }
            if (savedCredential != null) {
                _uiState.update {
                    it.copy(
                        username = savedCredential.username,
                        password = savedCredential.password
                    )
                }
            }
        }
    }

    fun updateUsername(name: String) {
        _uiState.update { it.copy(username = name, errorMessage = null) }
    }

    fun updatePassword(pass: String) {
        _uiState.update { it.copy(password = pass, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun authenticateAndSave(onSuccess: (DeviceItem, String, String) -> Unit) {
        val currentDevice = _uiState.value.device ?: return
        val currentUsername = _uiState.value.username.trim()
        val currentPassword = _uiState.value.password

        if (currentUsername.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Username cannot be empty") }
            return
        }

        _uiState.update { it.copy(isAuthenticating = true, errorMessage = null) }

        viewModelScope.launch {
            var effectiveDeviceId = currentDevice.id
            if (effectiveDeviceId.startsWith("manual_")) {
                val probedUuid = repository.probeDeviceUuid(currentDevice.hostAddress, currentDevice.port, currentDevice.isHttps)
                if (!probedUuid.isNullOrBlank()) {
                    effectiveDeviceId = probedUuid
                }
            }

            val updatedDevice = currentDevice.copy(id = effectiveDeviceId)
            val targetDevice = RemoteTargetDevice(
                id = effectiveDeviceId,
                name = updatedDevice.name,
                hostAddress = updatedDevice.hostAddress,
                port = updatedDevice.port,
                isHttps = updatedDevice.isHttps
            )
            val result = repository.authenticateDevice(
                deviceId = effectiveDeviceId,
                username = currentUsername,
                password = currentPassword,
                fallbackDevice = targetDevice
            )

            result.fold(
                onSuccess = {
                    // Save configuration using persistent UUID
                    credentialDao.saveCredential(
                        DeviceCredentialEntity(
                            deviceId = effectiveDeviceId,
                            username = currentUsername,
                            password = currentPassword
                        )
                    )
                    _uiState.update { it.copy(isAuthenticating = false, isSuccess = true) }
                    onSuccess(updatedDevice, currentUsername, currentPassword)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            errorMessage = error.message ?: "Authentication failed. Please check username and password."
                        )
                    }
                }
            )
        }
    }

    fun clearState() {
        _uiState.value = DeviceAuthUiState()
    }
}
