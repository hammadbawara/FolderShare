package com.hz_apps.foldershare.core.discovery

import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.server.ServerConstants
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.DeviceCredentialEntity
import com.hz_apps.foldershare.feature.devices.DeviceItem

/**
 * Result of a connection attempt to a target device.
 */
sealed interface DeviceConnectionResult {
    data class Success(
        val targetDevice: RemoteTargetDevice,
        val username: String? = null,
        val password: String? = null
    ) : DeviceConnectionResult

    data class NeedsAuthentication(
        val deviceItem: DeviceItem,
        val isSavedCredentialInvalid: Boolean = false
    ) : DeviceConnectionResult

    data class Failure(
        val deviceItem: DeviceItem,
        val errorMessage: String
    ) : DeviceConnectionResult
}

/**
 * Domain service encapsulating endpoint discovery, reachability probing,
 * authentication guard validation, and credential resolution.
 */
interface DeviceConnector {
    suspend fun connectManual(
        target: ParsedConnectionTarget,
        customName: String?,
        discoveredDevices: List<DiscoveredDevice> = emptyList()
    ): DeviceConnectionResult

    suspend fun connectDevice(
        device: DeviceItem,
        discoveredDevices: List<DiscoveredDevice> = emptyList()
    ): DeviceConnectionResult

    fun formatConnectionErrorMessage(
        deviceName: String,
        host: String,
        port: Int?,
        error: Throwable?
    ): String
}

class DefaultDeviceConnector(
    private val repository: RemoteFileRepository,
    private val credentialDao: DeviceCredentialDao,
    private val deviceResolver: DeviceResolver? = null
) : DeviceConnector {

    override suspend fun connectManual(
        target: ParsedConnectionTarget,
        customName: String?,
        discoveredDevices: List<DiscoveredDevice>
    ): DeviceConnectionResult {
        val candidates = ConnectionTargetParser.generateCandidates(target, discoveredDevices)

        var workingPort: Int? = null
        var workingIsHttps: Boolean = false
        var probedUuid: String? = null
        var isAuthRequired = false
        var lastError: Throwable? = null

        for (candidate in candidates) {
            val uuid = repository.probeDeviceUuid(target.host, candidate.port, candidate.isHttps)
            if (!uuid.isNullOrBlank()) {
                workingPort = candidate.port
                workingIsHttps = candidate.isHttps
                probedUuid = uuid

                // Guard: Probing UUID via OPTIONS does NOT mean auth is disabled.
                // Test file listing without credentials to check if server requires authentication.
                val testDevice = RemoteTargetDevice(
                    id = uuid,
                    name = customName ?: "Probe Device",
                    hostAddress = target.host,
                    port = candidate.port,
                    isHttps = candidate.isHttps
                )
                val testResult = repository.listFiles(
                    deviceId = testDevice.id,
                    path = "/",
                    fallbackDevice = testDevice
                )
                if (testResult.isSuccess) {
                    isAuthRequired = false
                } else {
                    val err = testResult.exceptionOrNull()
                    val errMsg = err?.message.orEmpty()
                    val isAuth = isAuthError(errMsg)
                    if (isAuth) {
                        isAuthRequired = true
                    } else {
                        val discoveredMatch = discoveredDevices.firstOrNull {
                            it.hostAddress.equals(target.host, ignoreCase = true) && it.port == candidate.port
                        }
                        isAuthRequired = discoveredMatch?.isAuthRequired ?: false
                    }
                }
                break
            }

            // Fallback: If probeDeviceUuid returned null (e.g. standard WebDAV server), test listFiles directly
            val tempDevice = RemoteTargetDevice(
                id = "temp_probe_${target.host}_${candidate.port}",
                name = customName ?: "Probe Device",
                hostAddress = target.host,
                port = candidate.port,
                isHttps = candidate.isHttps
            )
            val testResult = repository.listFiles(
                deviceId = tempDevice.id,
                path = "/",
                fallbackDevice = tempDevice
            )
            if (testResult.isSuccess) {
                workingPort = candidate.port
                workingIsHttps = candidate.isHttps
                isAuthRequired = false
                break
            } else {
                val err = testResult.exceptionOrNull()
                val errMsg = err?.message.orEmpty()
                val isAuth = isAuthError(errMsg)
                if (isAuth) {
                    workingPort = candidate.port
                    workingIsHttps = candidate.isHttps
                    isAuthRequired = true
                    break
                } else {
                    lastError = err
                }
            }
        }

        if (workingPort == null) {
            val fallbackPort = target.explicitPort ?: ServerConstants.DEFAULT_PORT
            val dummyDevice = DeviceItem(
                id = "manual_${target.host}",
                name = if (!customName.isNullOrBlank()) customName else "Manual Device (${target.host})",
                osDetails = "Manual IP • ${target.host}",
                statusText = "Offline",
                hostAddress = target.host,
                port = fallbackPort,
                isOnline = false
            )
            val readableError = formatConnectionErrorMessage(dummyDevice.name, target.host, fallbackPort, lastError)
            return DeviceConnectionResult.Failure(dummyDevice, readableError)
        }

        val finalPort = workingPort
        val finalIsHttps = workingIsHttps

        val discoveredMatch = discoveredDevices.firstOrNull {
            it.hostAddress.equals(target.host, ignoreCase = true) && it.port == finalPort
        }
        val deviceId = probedUuid ?: discoveredMatch?.id ?: "manual_${target.host.lowercase()}_$finalPort"
        val deviceName = if (!customName.isNullOrBlank()) customName else (discoveredMatch?.name ?: "Manual Device (${target.host})")

        val manualTargetDevice = RemoteTargetDevice(
            id = deviceId,
            name = deviceName,
            hostAddress = target.host,
            port = finalPort,
            isHttps = finalIsHttps
        )
        deviceResolver?.registerManualDevice(manualTargetDevice)

        val manualDeviceItem = DeviceItem(
            id = deviceId,
            name = deviceName,
            osDetails = "Manual IP • ${target.host}:$finalPort",
            statusText = if (isAuthRequired) "Protected" else "Open",
            hostAddress = target.host,
            port = finalPort,
            isAuthRequired = isAuthRequired,
            isHttps = finalIsHttps,
            isOnline = true
        )

        if (isAuthRequired) {
            // Case A: User entered credentials in connection string (e.g. user:pass@host)
            if (!target.username.isNullOrBlank() && target.password != null) {
                val authResult = repository.authenticateDevice(
                    deviceId = deviceId,
                    username = target.username,
                    password = target.password,
                    fallbackDevice = manualTargetDevice
                )
                if (authResult.isSuccess) {
                    credentialDao.saveCredential(
                        DeviceCredentialEntity(
                            deviceId = deviceId,
                            username = target.username,
                            password = target.password
                        )
                    )
                    return DeviceConnectionResult.Success(manualTargetDevice, target.username, target.password)
                } else {
                    return DeviceConnectionResult.NeedsAuthentication(manualDeviceItem, isSavedCredentialInvalid = true)
                }
            }

            // Case B: Check saved credentials in database
            val savedCredential = credentialDao.getCredentialForDevice(deviceId)
            if (savedCredential != null) {
                val savedTestResult = repository.listFiles(
                    deviceId = deviceId,
                    path = "/",
                    fallbackDevice = manualTargetDevice
                )
                if (savedTestResult.isSuccess) {
                    return DeviceConnectionResult.Success(manualTargetDevice, savedCredential.username, savedCredential.password)
                } else {
                    return DeviceConnectionResult.NeedsAuthentication(manualDeviceItem, isSavedCredentialInvalid = true)
                }
            }

            // Case C: No credentials available -> prompt user
            return DeviceConnectionResult.NeedsAuthentication(manualDeviceItem, isSavedCredentialInvalid = false)
        } else {
            return DeviceConnectionResult.Success(manualTargetDevice, null, null)
        }
    }

    override suspend fun connectDevice(
        device: DeviceItem,
        discoveredDevices: List<DiscoveredDevice>
    ): DeviceConnectionResult {
        var effectiveDeviceId = device.id
        var savedCredential = credentialDao.getCredentialForDevice(effectiveDeviceId)

        if (savedCredential == null && device.isAuthRequired) {
            val probedUuid = repository.probeDeviceUuid(device.hostAddress, device.port, device.isHttps)
            if (!probedUuid.isNullOrBlank() && probedUuid != effectiveDeviceId) {
                effectiveDeviceId = probedUuid
                savedCredential = credentialDao.getCredentialForDevice(effectiveDeviceId)
            }
        }

        val targetDevice = RemoteTargetDevice(
            id = effectiveDeviceId,
            name = device.name,
            hostAddress = device.hostAddress,
            port = device.port,
            isHttps = device.isHttps
        )
        deviceResolver?.registerManualDevice(targetDevice)
        val targetDeviceItem = device.copy(id = effectiveDeviceId)

        if (!device.isAuthRequired) {
            val testResult = repository.listFiles(
                deviceId = effectiveDeviceId,
                path = "/",
                fallbackDevice = targetDevice
            )
            return if (testResult.isSuccess) {
                DeviceConnectionResult.Success(targetDevice, null, null)
            } else {
                val error = testResult.exceptionOrNull()
                val errMsg = error?.message.orEmpty()
                if (isAuthError(errMsg)) {
                    DeviceConnectionResult.NeedsAuthentication(targetDeviceItem, isSavedCredentialInvalid = false)
                } else {
                    val readableMsg = formatConnectionErrorMessage(device.name, device.hostAddress, device.port, error)
                    DeviceConnectionResult.Failure(targetDeviceItem, readableMsg)
                }
            }
        } else {
            if (savedCredential != null) {
                val testResult = repository.listFiles(
                    deviceId = effectiveDeviceId,
                    path = "/",
                    fallbackDevice = targetDevice
                )
                return if (testResult.isSuccess) {
                    DeviceConnectionResult.Success(targetDevice, savedCredential.username, savedCredential.password)
                } else {
                    val error = testResult.exceptionOrNull()
                    val errMsg = error?.message.orEmpty()
                    if (isAuthError(errMsg)) {
                        DeviceConnectionResult.NeedsAuthentication(targetDeviceItem, isSavedCredentialInvalid = true)
                    } else {
                        val readableMsg = formatConnectionErrorMessage(device.name, device.hostAddress, device.port, error)
                        DeviceConnectionResult.Failure(targetDeviceItem, readableMsg)
                    }
                }
            } else {
                return DeviceConnectionResult.NeedsAuthentication(targetDeviceItem, isSavedCredentialInvalid = false)
            }
        }
    }

    override fun formatConnectionErrorMessage(
        deviceName: String,
        host: String,
        port: Int?,
        error: Throwable?
    ): String {
        val rawMsg = error?.message.orEmpty()
        val portSuffix = if (port != null) ":$port" else ""
        return when {
            rawMsg.contains("UnknownHostException", ignoreCase = true) ||
            rawMsg.contains("Could not connect to target device", ignoreCase = true) ||
            rawMsg.contains("unresolved", ignoreCase = true) ->
                "Could not resolve host '$host'. Verify target device is on the same local Wi-Fi or Mobile Hotspot."
            rawMsg.contains("ConnectException", ignoreCase = true) || rawMsg.contains("Connection refused", ignoreCase = true) ->
                "Connection refused by $deviceName ($host$portSuffix). Check if Folder Share is active on target device and both devices are on the same Wi-Fi/Hotspot."
            rawMsg.contains("SocketTimeoutException", ignoreCase = true) || rawMsg.contains("timed out", ignoreCase = true) ->
                "Connection to $deviceName ($host$portSuffix) timed out. The device may be unreachable or offline."
            rawMsg.isNotBlank() && !rawMsg.contains("Exception") ->
                "Could not connect to $deviceName: $rawMsg"
            else ->
                "Could not connect to $deviceName ($host$portSuffix). Verify device is online, Folder Share is active, and both devices are on the same Wi-Fi or Mobile Hotspot."
        }
    }

    private fun isAuthError(message: String): Boolean {
        return message.contains("401") ||
                message.contains("Unauthorized", ignoreCase = true) ||
                message.contains("auth", ignoreCase = true)
    }
}
