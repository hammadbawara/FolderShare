package com.hz_apps.foldershare.core.proxy

import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface for managing Local Stream Proxy lifecycle across Android & Linux desktop.
 * Follows the Dependency Inversion Principle so view models and UI layers depend on abstractions.
 */
interface StreamProxyController {
    val isProxyActive: StateFlow<Boolean>
    val activeStreamsCount: StateFlow<Int>

    suspend fun getStreamableUrl(
        downloadUrl: String,
        fileName: String,
        deviceId: String? = null,
        path: String? = null
    ): String
    fun startProxy()
    fun stopProxy()
}

expect fun createStreamProxyController(
    localStreamProxy: LocalStreamProxy
): StreamProxyController
