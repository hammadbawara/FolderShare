package com.hz_apps.foldershare.core.discovery

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for discovering remote Folder Share devices on local network (mDNS/NSD).
 * Used by Devices module/ViewModel to search for online hosts.
 */
interface ServiceBrowser {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    fun startDiscovery()
    fun stopDiscovery()
    fun refreshDiscovery()
}
