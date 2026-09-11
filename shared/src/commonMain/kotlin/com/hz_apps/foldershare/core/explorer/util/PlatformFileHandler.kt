package com.hz_apps.foldershare.core.explorer.util

/**
 * Interface defining OS platform operations for saving files, opening URLs, sharing, and clipboard.
 */
interface PlatformFileHandler {
    fun getDefaultDownloadDirectory(): okio.Path
    fun getTemporaryDirectory(): okio.Path
    fun getDownloadDestinationPath(fileName: String, customDirectory: okio.Path? = null): okio.Path
    fun openFile(
        downloadUrl: String,
        mimeType: String? = null,
        title: String? = null,
        fileSize: Long? = null,
        rawUrl: String? = null
    )
    fun openLocalFile(
        filePath: okio.Path,
        mimeType: String? = null,
        title: String? = null
    )
    fun shareFile(downloadUrl: String, title: String)
    fun copyToClipboard(text: String)
}

expect fun getPlatformFileHandler(): PlatformFileHandler
