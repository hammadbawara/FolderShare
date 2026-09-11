package com.hz_apps.foldershare.core.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Contract for host device service advertising over local network (mDNS/NSD).
 * Used by Share module/ViewModel to broadcast server availability.
 */
interface ServiceAdvertiser {
    /**
     * A flow of IP addresses on which the service is actively and successfully registered.
     */
    val advertisedAddresses: Flow<List<String>>

    fun registerService(
        deviceName: String,
        port: Int,
        osDetails: String,
        isAuthRequired: Boolean = false,
        isHttpsEnabled: Boolean = false,
        deviceUuid: String? = null
    )
    fun unregisterService()
}
