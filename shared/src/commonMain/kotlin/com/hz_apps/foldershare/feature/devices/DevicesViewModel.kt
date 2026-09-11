package com.hz_apps.foldershare.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.core.discovery.DevicePlatformType
import com.hz_apps.foldershare.core.discovery.DeviceClassifier
import com.hz_apps.foldershare.core.discovery.ClassifiedDeviceMetadata
import com.hz_apps.foldershare.core.discovery.ConnectionTargetParser
import com.hz_apps.foldershare.core.discovery.DefaultDeviceConnector
import com.hz_apps.foldershare.core.discovery.DeviceConnectionResult
import com.hz_apps.foldershare.core.discovery.DeviceConnector
import com.hz_apps.foldershare.core.discovery.DeviceResolver
import com.hz_apps.foldershare.core.discovery.ParsedConnectionTarget
import com.hz_apps.foldershare.core.discovery.ServiceBrowser
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.server.ServerConstants
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.DeviceCredentialEntity
import com.hz_apps.foldershare.data.database.ServerConfigDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class DeviceItem(
    val id: String,
    val name: String,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val platformType: DevicePlatformType = DevicePlatformType.UNKNOWN,
    val platformDisplayName: String = "",
    val osDetails: String,
    val rawOs: String = "",
    val statusText: String,
    val hostAddress: String,
    val port: Int,
    val isAuthRequired: Boolean = false,
    val isHttps: Boolean = false,
    val isOnline: Boolean = true,
    val isThisDevice: Boolean = false,
    val networkLinksCount: Int = 1,
    val endpoints: List<String> = emptyList()
)

data class DevicesUiState(
    val devices: List<DeviceItem> = emptyList(),
    val isSearching: Boolean = true,
    val isManualIpDialogOpen: Boolean = false,
    val manualHostInput: String = "",
    val manualDeviceNameInput: String = "",
    val manualConnectionError: String? = null,
    val isConnectingManual: Boolean = false,
    val connectingDeviceId: String? = null,
    val connectingDevice: DeviceItem? = null,
    val isConnecting: Boolean = false,
    val connectionErrorMessage: String? = null,
    
    // Auth Dialog State
    val isAuthDialogOpen: Boolean = false,
    val authDevice: DeviceItem? = null,
    val authUsernameInput: String = "",
    val authPasswordInput: String = "",
    val isAuthPasswordVisible: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authErrorMessage: String? = null,

    // Error Dialog State
    val isErrorDialogOpen: Boolean = false,
    val errorDialogDevice: DeviceItem? = null,
    val errorDialogMessage: String? = null
)

typealias ParsedHostInput = ParsedConnectionTarget

class DevicesViewModel(
    private val serviceBrowser: ServiceBrowser,
    private val credentialDao: DeviceCredentialDao,
    private val repository: RemoteFileRepository,
    private val deviceResolver: DeviceResolver? = null,
    private val serverConfigDao: ServerConfigDao? = null,
    private val deviceConnector: DeviceConnector = DefaultDeviceConnector(repository, credentialDao, deviceResolver)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private var connectionJob: Job? = null

    init {
        serviceBrowser.startDiscovery()
        val serverConfigFlow = serverConfigDao?.getServerConfigFlow() ?: flowOf(null)
        viewModelScope.launch {
            combine(serviceBrowser.discoveredDevices, serverConfigFlow) { discoveredList, serverConfig ->
                val ownUuid = serverConfig?.deviceUuid
                val grouped = discoveredList.groupBy { it.id }
                val items = grouped.map { (deviceId, candidateList) ->
                    val optimal = if (deviceResolver != null) {
                        deviceResolver.resolveDevice(deviceId) ?: candidateList.first()
                    } else {
                        candidateList.first()
                    }
                    val isThisDevice = !ownUuid.isNullOrBlank() && (deviceId == ownUuid)
                    val endpointList = candidateList.map { "${it.hostAddress}:${it.port}" }.distinct()
                    val classified = if (optimal.category != com.hz_apps.foldershare.core.discovery.DeviceCategory.UNKNOWN && optimal.platformType != com.hz_apps.foldershare.core.discovery.DevicePlatformType.UNKNOWN) {
                        com.hz_apps.foldershare.core.discovery.ClassifiedDeviceMetadata(
                            category = optimal.category,
                            platformType = optimal.platformType,
                            displaySubtitle = "${optimal.category.displayName} • ${optimal.platformType.displayName}"
                        )
                    } else {
                        com.hz_apps.foldershare.core.discovery.DeviceClassifier.classify(
                            categoryAttr = optimal.category.name.takeIf { optimal.category != com.hz_apps.foldershare.core.discovery.DeviceCategory.UNKNOWN },
                            platformAttr = optimal.platformType.name.takeIf { optimal.platformType != com.hz_apps.foldershare.core.discovery.DevicePlatformType.UNKNOWN },
                            osDetailsAttr = optimal.osDetails,
                            deviceName = optimal.name
                        )
                    }

                    DeviceItem(
                        id = optimal.id,
                        name = optimal.name,
                        category = classified.category,
                        platformType = classified.platformType,
                        platformDisplayName = classified.displaySubtitle,
                        osDetails = "${optimal.osDetails} • ${optimal.httpUrl}",
                        rawOs = optimal.osDetails,
                        statusText = if (optimal.isAuthRequired) "Protected" else "Open",
                        hostAddress = optimal.hostAddress,
                        port = optimal.port,
                        isAuthRequired = optimal.isAuthRequired,
                        isHttps = optimal.isHttps,
                        isOnline = optimal.isOnline,
                        isThisDevice = isThisDevice,
                        networkLinksCount = candidateList.size,
                        endpoints = endpointList
                    )
                }
                _uiState.update { currentState ->
                    currentState.copy(
                        devices = items
                    )
                }
            }.collect()
        }
    }

    fun startScanning() {
        _uiState.update { it.copy(isSearching = true) }
        serviceBrowser.startDiscovery()
        serviceBrowser.refreshDiscovery()
    }

    fun stopScanning() {
        _uiState.update { it.copy(isSearching = false) }
        serviceBrowser.stopDiscovery()
    }

    fun refresh() {
        startScanning()
    }

    fun clearConnectionError() {
        _uiState.update { it.copy(connectionErrorMessage = null) }
    }

    fun showManualIpDialog() {
        _uiState.update {
            it.copy(
                isManualIpDialogOpen = true,
                manualConnectionError = null,
                isConnectingManual = false
            )
        }
    }

    fun hideManualIpDialog() {
        _uiState.update {
            it.copy(
                isManualIpDialogOpen = false,
                manualConnectionError = null,
                isConnectingManual = false
            )
        }
    }

    fun updateManualHost(host: String) {
        _uiState.update { it.copy(manualHostInput = host, manualConnectionError = null) }
    }

    fun updateManualDeviceName(name: String) {
        _uiState.update { it.copy(manualDeviceNameInput = name, manualConnectionError = null) }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        connectionJob = null
        _uiState.update {
            it.copy(
                isConnecting = false,
                connectingDeviceId = null,
                connectingDevice = null,
                isConnectingManual = false
            )
        }
    }

    fun showAuthDialog(device: DeviceItem, initialErrorMessage: String? = null) {
        _uiState.update {
            it.copy(
                isAuthDialogOpen = true,
                authDevice = device,
                authUsernameInput = "",
                authPasswordInput = "",
                isAuthPasswordVisible = false,
                isAuthenticating = false,
                authErrorMessage = initialErrorMessage
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
                        authUsernameInput = savedCredential.username,
                        authPasswordInput = savedCredential.password
                    )
                }
            }
        }
    }

    fun hideAuthDialog() {
        _uiState.update {
            it.copy(
                isAuthDialogOpen = false,
                authDevice = null,
                authErrorMessage = null,
                isAuthenticating = false
            )
        }
    }

    fun updateAuthUsername(name: String) {
        _uiState.update { it.copy(authUsernameInput = name, authErrorMessage = null) }
    }

    fun updateAuthPassword(pass: String) {
        _uiState.update { it.copy(authPasswordInput = pass, authErrorMessage = null) }
    }

    fun toggleAuthPasswordVisibility() {
        _uiState.update { it.copy(isAuthPasswordVisible = !it.isAuthPasswordVisible) }
    }

    fun submitAuthentication(onNavigateToFileExplorer: (RemoteTargetDevice, String?, String?) -> Unit) {
        val currentDevice = _uiState.value.authDevice ?: return
        val currentUsername = _uiState.value.authUsernameInput.trim()
        val currentPassword = _uiState.value.authPasswordInput

        if (currentUsername.isEmpty()) {
            _uiState.update { it.copy(authErrorMessage = "Username cannot be empty") }
            return
        }

        _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }

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
                    credentialDao.saveCredential(
                        DeviceCredentialEntity(
                            deviceId = effectiveDeviceId,
                            username = currentUsername,
                            password = currentPassword
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            isAuthDialogOpen = false,
                            authDevice = null
                        )
                    }
                    onNavigateToFileExplorer(targetDevice, currentUsername, currentPassword)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = error.message ?: "Authentication failed. Please check username and password."
                        )
                    }
                }
            )
        }
    }

    fun showErrorDialog(device: DeviceItem?, message: String) {
        _uiState.update {
            it.copy(
                isErrorDialogOpen = true,
                errorDialogDevice = device,
                errorDialogMessage = message
            )
        }
    }

    fun hideErrorDialog() {
        _uiState.update {
            it.copy(
                isErrorDialogOpen = false,
                errorDialogDevice = null,
                errorDialogMessage = null
            )
        }
    }

    fun connectToManualIp(
        onNavigateToFileExplorer: (RemoteTargetDevice, String?, String?) -> Unit
    ) {
        val rawInput = _uiState.value.manualHostInput.trim()
        val customName = _uiState.value.manualDeviceNameInput.trim()

        val parsedInput = parseHostInput(rawInput)
        if (parsedInput == null) {
            _uiState.update { it.copy(manualConnectionError = "Please enter a valid IP address or hostname.") }
            return
        }

        val explicitOrDefPort = parsedInput.explicitPort ?: ServerConstants.DEFAULT_PORT
        val dummyDevice = DeviceItem(
            id = "manual_${parsedInput.host}",
            name = if (customName.isNotEmpty()) customName else "Manual Device (${parsedInput.host})",
            category = com.hz_apps.foldershare.core.discovery.DeviceCategory.UNKNOWN,
            platformType = com.hz_apps.foldershare.core.discovery.DevicePlatformType.UNKNOWN,
            platformDisplayName = "Manual IP",
            osDetails = "Manual IP • ${parsedInput.host}",
            rawOs = "Manual IP",
            statusText = "Connecting",
            hostAddress = parsedInput.host,
            port = explicitOrDefPort,
            isOnline = true,
            endpoints = listOf("${parsedInput.host}:$explicitOrDefPort")
        )

        _uiState.update { 
            it.copy(
                isConnectingManual = true,
                isConnecting = true,
                connectingDevice = dummyDevice,
                manualConnectionError = null
            ) 
        }

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val result = deviceConnector.connectManual(
                target = parsedInput,
                customName = customName.takeIf { it.isNotEmpty() },
                discoveredDevices = serviceBrowser.discoveredDevices.value
            )

            hideManualIpDialog()
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    connectingDevice = null,
                    isConnectingManual = false
                )
            }

            when (result) {
                is DeviceConnectionResult.Success -> {
                    onNavigateToFileExplorer(result.targetDevice, result.username, result.password)
                }
                is DeviceConnectionResult.NeedsAuthentication -> {
                    val initialError = if (result.isSavedCredentialInvalid) {
                        "Authentication failed with saved credentials. Please enter credentials."
                    } else null
                    showAuthDialog(result.deviceItem, initialError)
                }
                is DeviceConnectionResult.Failure -> {
                    _uiState.update { it.copy(manualConnectionError = result.errorMessage) }
                    showErrorDialog(result.deviceItem, result.errorMessage)
                }
            }
        }
    }

    fun connectToDevice(
        device: DeviceItem,
        onNavigateToFileExplorer: (RemoteTargetDevice, String?, String?) -> Unit
    ) {
        if (_uiState.value.isConnecting) return

        _uiState.update { 
            it.copy(
                isConnecting = true,
                connectingDeviceId = device.id,
                connectingDevice = device,
                connectionErrorMessage = null
            ) 
        }

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val result = deviceConnector.connectDevice(
                device = device,
                discoveredDevices = serviceBrowser.discoveredDevices.value
            )

            _uiState.update {
                it.copy(
                    isConnecting = false,
                    connectingDeviceId = null,
                    connectingDevice = null
                )
            }

            when (result) {
                is DeviceConnectionResult.Success -> {
                    onNavigateToFileExplorer(result.targetDevice, result.username, result.password)
                }
                is DeviceConnectionResult.NeedsAuthentication -> {
                    val initialError = if (result.isSavedCredentialInvalid) {
                        "Authentication failed with saved credentials. Please enter credentials."
                    } else null
                    showAuthDialog(result.deviceItem, initialError)
                }
                is DeviceConnectionResult.Failure -> {
                    _uiState.update { it.copy(connectionErrorMessage = result.errorMessage) }
                    showErrorDialog(result.deviceItem, result.errorMessage)
                }
            }
        }
    }

    override fun onCleared() {
        serviceBrowser.stopDiscovery()
    }

    companion object {
        fun parseHostInput(input: String): ParsedHostInput? {
            return ConnectionTargetParser.parse(input)
        }

        fun generateCandidateEndpoints(
            parsed: ParsedHostInput,
            discoveredDevices: List<com.hz_apps.foldershare.core.discovery.DiscoveredDevice> = emptyList()
        ): List<Pair<Int, Boolean>> {
            return ConnectionTargetParser.generateCandidates(parsed, discoveredDevices)
                .map { Pair(it.port, it.isHttps) }
        }
    }
}

