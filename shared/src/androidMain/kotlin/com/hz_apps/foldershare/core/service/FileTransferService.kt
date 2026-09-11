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
import com.hz_apps.foldershare.core.transfer.FileTransferManager
import com.hz_apps.foldershare.core.transfer.FileTransferProgress
import com.hz_apps.foldershare.core.transfer.TransferDirection
import com.hz_apps.foldershare.core.transfer.TransferStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android Foreground Service for managing background file uploads and downloads.
 * Holds CPU WakeLock during transfers and posts progress notifications.
 */
class FileTransferService : Service(), KoinComponent {

    private val fileTransferManager: FileTransferManager by inject()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        stopTransferAndShutdown()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob + exceptionHandler)

    private var transferObservationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false

    private var lastNotificationTimeMs = 0L
    private var lastNotifiedStatus: TransferStatus? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundTransferInternal()
            ACTION_STOP -> {
                stopTransferAndShutdown()
            }
            ACTION_CANCEL_FROM_NOTIFICATION -> {
                fileTransferManager.cancelTransfer()
                stopTransferAndShutdown()
            }
            else -> {
                if (!isServiceStarted) {
                    startForegroundTransferInternal()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundTransferInternal() {
        if (isServiceStarted) return
        isServiceStarted = true

        acquireWakeLock()

        val initialNotification = buildNotification(
            title = "File Transfer In Progress",
            contentText = "Initializing transfer...",
            progressPercent = null
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Observe FileTransferManager state as a passive observer
        transferObservationJob?.cancel()
        transferObservationJob = serviceScope.launch {
            fileTransferManager.transferProgress.collect { progress ->
                if (progress == null || progress.status == TransferStatus.COMPLETED) {
                    if (progress == null) {
                        stopTransferAndShutdown()
                    }
                } else {
                    if (shouldUpdateNotification(progress)) {
                        val statusPrefix = when (progress.status) {
                            TransferStatus.RECONNECTING -> "Reconnecting "
                            TransferStatus.PAUSED_ERROR -> "Interrupted "
                            else -> ""
                        }
                        val directionText = if (progress.direction == TransferDirection.DOWNLOAD) "Downloading" else "Uploading"
                        val batchText = if (progress.totalFilesCount > 1) " (${progress.currentFileIndex}/${progress.totalFilesCount})" else ""
                        val title = "$statusPrefix$directionText file$batchText"
                        val progressFloat = progress.progress
                        val pctText = if (progressFloat != null) " - ${(progressFloat * 100).toInt()}%" else ""
                        val contentText = if (progress.status == TransferStatus.RECONNECTING) {
                            "${progress.fileName}$pctText (Reconnecting...)"
                        } else {
                            "${progress.fileName}$pctText"
                        }

                        val updatedNotification = buildNotification(
                            title = title,
                            contentText = contentText,
                            progressPercent = progressFloat?.let { (it * 100).toInt() }
                        )
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            }
        }
    }

    private fun shouldUpdateNotification(progress: FileTransferProgress): Boolean {
        val now = System.currentTimeMillis()
        val statusChanged = progress.status != lastNotifiedStatus
        if (statusChanged || (now - lastNotificationTimeMs) >= NOTIFICATION_THROTTLE_MS) {
            lastNotificationTimeMs = now
            lastNotifiedStatus = progress.status
            return true
        }
        return false
    }

    private fun stopTransferAndShutdown() {
        transferObservationJob?.cancel()
        transferObservationJob = null
        releaseWakeLock()
        isServiceStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FolderShare:FileTransferWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(60 * 60 * 1000L /* 1 hour fallback timeout */)
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
            "Folder Share File Transfer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification for background file uploads and downloads"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, contentText: String, progressPercent: Int?): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingIntentFlags)
        }

        val stopIntent = Intent(this, FileTransferService::class.java).apply {
            action = ACTION_CANCEL_FROM_NOTIFICATION
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel Transfer",
                stopPendingIntent
            )

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        stopTransferAndShutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.hz_apps.foldershare.ACTION_START_FILE_TRANSFER"
        const val ACTION_STOP = "com.hz_apps.foldershare.ACTION_STOP_FILE_TRANSFER"
        const val ACTION_CANCEL_FROM_NOTIFICATION = "com.hz_apps.foldershare.ACTION_CANCEL_FILE_TRANSFER_FROM_NOTIFICATION"

        private const val NOTIFICATION_THROTTLE_MS = 250L
        private const val NOTIFICATION_ID = 2003
        private const val CHANNEL_ID = "foldershare_file_transfer_channel"
    }
}
