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
import com.hz_apps.foldershare.core.server.ServerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FolderShareService : Service(), KoinComponent {

    private val serverManager: ServerManager by inject()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        stopServerAndShutdown()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob + exceptionHandler)

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                startForegroundServiceInternal()
            }
            ACTION_STOP -> {
                stopServerAndShutdown()
            }
            else -> {
                if (!isServiceStarted) {
                    startForegroundServiceInternal()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceInternal() {
        if (isServiceStarted) return
        isServiceStarted = true

        acquireWakeLock()

        val notification = buildNotification("Starting HTTP / WebDAV Server...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            val result = serverManager.startServer()
            if (result.isFailure) {
                stopServerAndShutdown()
                return@launch
            }

            // Observe server state to update notification dynamically
            serverManager.serverState.collect { state ->
                if (state.isSharing) {
                    val ips = com.hz_apps.foldershare.core.util.getAllLocalIpAddresses()
                    val scheme = if (state.isHttps) "https" else "http"
                    val addressStr = if (ips.size > 1) {
                        "$scheme://${com.hz_apps.foldershare.core.util.formatHostForUrl(ips.first())}:${state.port} (+${ips.size - 1} more)"
                    } else {
                        ips.firstOrNull()?.let { "$scheme://${com.hz_apps.foldershare.core.util.formatHostForUrl(it)}:${state.port}" } ?: "localhost:${state.port}"
                    }
                    val updateNotification = buildNotification("Sharing active at $addressStr")
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updateNotification)
                } else if (isServiceStarted) {
                    stopServerAndShutdown()
                }
            }
        }
    }

    private fun stopServerAndShutdown() {
        if (!isServiceStarted && wakeLock?.isHeld != true) return
        isServiceStarted = false

        serviceScope.launch {
            serverManager.stopServer()
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
                it.acquire(10 * 60 * 1000L /* 10 minutes fallback timeout */)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Folder Share Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for local file sharing server"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingIntentFlags)
        }

        val stopIntent = Intent(this, FolderShareService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Folder Share Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Sharing",
                stopPendingIntent
            )

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        if (isServiceStarted) {
            isServiceStarted = false
            runBlocking {
                runCatching { serverManager.stopServer() }
            }
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isServiceStarted) {
            isServiceStarted = false
            runBlocking {
                runCatching { serverManager.stopServer() }
            }
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.hz_apps.foldershare.ACTION_START_SERVER"
        const val ACTION_STOP = "com.hz_apps.foldershare.ACTION_STOP_SERVER"
        private const val CHANNEL_ID = "foldershare_foreground_service"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "FolderShare:ServerWakeLock"
    }
}
