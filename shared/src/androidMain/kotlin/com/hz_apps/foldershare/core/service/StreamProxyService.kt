package com.hz_apps.foldershare.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hz_apps.foldershare.core.proxy.LocalStreamProxy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

/**
 * Android Foreground Service for LocalStreamProxy.
 * Ensures that HTTP stream proxying to external media players (e.g., VLC, MX Player)
 * continues uninterrupted when Folder Share moves to the background.
 *
 * Acquires a CPU WakeLock during active streaming and automatically shuts down
 * after an idle timeout when no active streams remain.
 */
class StreamProxyService : Service(), KoinComponent {

    private val localStreamProxy: LocalStreamProxy by inject()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        stopProxyAndShutdown()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob + exceptionHandler)

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var idleCheckJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundProxyInternal()
            ACTION_STOP -> stopProxyAndShutdown()
            else -> {
                if (!isServiceStarted) {
                    startForegroundProxyInternal()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundProxyInternal() {
        if (isServiceStarted) return
        isServiceStarted = true

        acquireWakeLock()

        val notification = buildNotification("Media proxy active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            // Ensure local proxy engine is started
            localStreamProxy.ensureStarted()

            // Observe active streams count for dynamic notification and automatic idle shutdown
            localStreamProxy.activeStreamsCount.collectLatest { activeCount ->
                idleCheckJob?.cancel()
                if (activeCount > 0) {
                    val text = "Streaming media to external player ($activeCount active)..."
                    updateNotification(text)
                } else {
                    updateNotification("Media proxy idle")
                    // Schedule auto-shutdown after 1 minute of zero activity
                    idleCheckJob = serviceScope.launch {
                        delay(1.minutes)
                        if (localStreamProxy.activeStreamsCount.value == 0) {
                            stopProxyAndShutdown()
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopProxyAndShutdown() {
        if (!isServiceStarted && wakeLock?.isHeld != true) return
        isServiceStarted = false

        idleCheckJob?.cancel()
        serviceScope.launch {
            localStreamProxy.stop()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(10 * 60 * 1000L /* 10 mins fallback timeout */)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Folder Share Stream Proxy Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification for external media player streaming"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingIntentFlags)
        }

        val stopIntent = Intent(this, StreamProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Folder Share Stream Proxy")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Proxy",
                stopPendingIntent
            )

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        stopProxyAndShutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.hz_apps.foldershare.ACTION_START_PROXY"
        const val ACTION_STOP = "com.hz_apps.foldershare.ACTION_STOP_PROXY"
        private const val CHANNEL_ID = "foldershare_stream_proxy_service"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TAG = "FolderShare:StreamProxyWakeLock"
    }
}
