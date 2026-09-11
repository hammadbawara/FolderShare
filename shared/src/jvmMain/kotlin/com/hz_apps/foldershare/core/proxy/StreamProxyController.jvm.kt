package com.hz_apps.foldershare.core.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * JVM actual implementation of StreamProxyController.
 * Controls local stream proxy lifecycle on Linux desktop with auto-shutdown on idle.
 */
class JvmStreamProxyController(
    private val localStreamProxy: LocalStreamProxy
) : StreamProxyController {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var idleJob: Job? = null

    init {
        scope.launch {
            localStreamProxy.activeStreamsCount.collectLatest { activeCount ->
                idleJob?.cancel()
                if (activeCount == 0 && localStreamProxy.isProxyRunning.value) {
                    idleJob = scope.launch {
                        delay(20.seconds)
                        if (localStreamProxy.activeStreamsCount.value == 0) {
                            localStreamProxy.stop()
                        }
                    }
                }
            }
        }
    }

    override val isProxyActive: StateFlow<Boolean>
        get() = localStreamProxy.isProxyRunning

    override val activeStreamsCount: StateFlow<Int>
        get() = localStreamProxy.activeStreamsCount

    override suspend fun getStreamableUrl(
        downloadUrl: String,
        fileName: String,
        deviceId: String?,
        path: String?
    ): String {
        return localStreamProxy.getStreamableUrl(downloadUrl, fileName, deviceId, path)
    }

    override fun startProxy() {
        // Automatically handled on demand
    }

    override fun stopProxy() {
        localStreamProxy.stop()
    }
}

actual fun createStreamProxyController(
    localStreamProxy: LocalStreamProxy
): StreamProxyController = JvmStreamProxyController(localStreamProxy)
