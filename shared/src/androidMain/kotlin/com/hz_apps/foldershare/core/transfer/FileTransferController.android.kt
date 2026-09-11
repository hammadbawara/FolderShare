package com.hz_apps.foldershare.core.transfer

import android.content.Intent
import androidx.core.content.ContextCompat
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.core.service.FileTransferService

/**
 * Android actual implementation of FileTransferController.
 * Delegates background execution and WakeLock management to FileTransferService.
 */
class AndroidFileTransferController : FileTransferController {

    override fun startTransferService() {
        val context = AndroidContextProvider.applicationContext ?: return
        try {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = FileTransferService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopTransferService() {
        val context = AndroidContextProvider.applicationContext ?: return
        try {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = FileTransferService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun createFileTransferController(): FileTransferController = AndroidFileTransferController()
