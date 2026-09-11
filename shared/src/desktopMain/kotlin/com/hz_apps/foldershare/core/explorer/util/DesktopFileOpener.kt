package com.hz_apps.foldershare.core.explorer.util

import java.awt.Desktop
import java.net.URI

interface DesktopFileOpener {
    fun openFile(
        downloadUrl: String,
        mimeType: String?,
        title: String?,
        fileSize: Long?,
        rawUrl: String?
    ): Boolean
}

class DefaultDesktopFileOpener : DesktopFileOpener {
    override fun openFile(
        downloadUrl: String,
        mimeType: String?,
        title: String?,
        fileSize: Long?,
        rawUrl: String?
    ): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(downloadUrl))
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
