package com.hz_apps.foldershare.core.proxy

import android.content.Intent
import androidx.core.content.ContextCompat
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.core.service.StreamProxyService
import kotlinx.coroutines.flow.StateFlow

/**
 * Android actual implementation of StreamProxyController.
 * Delegates lifecycle control to StreamProxyService to ensure foreground service + WakeLock state.
 */
class AndroidStreamProxyController(
    private val localStreamProxy: LocalStreamProxy
) : StreamProxyController {

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
        startProxy()
        return localStreamProxy.getStreamableUrl(downloadUrl, fileName, deviceId, path)
    }

    override fun startProxy() {
        val context = AndroidContextProvider.applicationContext ?: return
        try {
            val intent = Intent(context, StreamProxyService::class.java).apply {
                action = StreamProxyService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopProxy() {
        val context = AndroidContextProvider.applicationContext ?: return
        try {
            val intent = Intent(context, StreamProxyService::class.java).apply {
                action = StreamProxyService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun createStreamProxyController(
    localStreamProxy: LocalStreamProxy
): StreamProxyController = AndroidStreamProxyController(localStreamProxy)
