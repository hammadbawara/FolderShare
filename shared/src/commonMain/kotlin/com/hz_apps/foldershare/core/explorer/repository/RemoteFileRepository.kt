package com.hz_apps.foldershare.core.explorer.repository

import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice

data class DirectoryListingResult(
    val isWriteAllowed: Boolean = true,
    val files: List<RemoteFile> = emptyList()
)

/**
 * Clean domain repository interface for remote device operations.
 * Operates primarily using persistent device IDs (UUIDs). All network resolution
 * and authentication credentials are encapsulated within the implementation.
 */
interface RemoteFileRepository {
    suspend fun listFiles(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<DirectoryListingResult>

    suspend fun createFolder(
        deviceId: String,
        parentPath: String,
        folderName: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<Unit>

    suspend fun createFile(
        deviceId: String,
        parentPath: String,
        fileName: String,
        content: ByteArray = byteArrayOf(),
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<Unit>

    suspend fun uploadFile(
        deviceId: String,
        parentPath: String,
        localFileSource: LocalFileSource,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)? = null,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<Unit>

    suspend fun downloadFile(
        deviceId: String,
        path: String,
        fileName: String,
        destinationPath: okio.Path? = null,
        destinationDirectory: okio.Path? = null,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)? = null,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<String>

    fun cleanupPartialDownload(destinationPath: okio.Path)

    suspend fun deleteFile(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<Unit>

    suspend fun renameFile(
        deviceId: String,
        oldPath: String,
        newName: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<Unit>

    suspend fun getDownloadUrl(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): String

    suspend fun authenticateDevice(
        deviceId: String,
        username: String,
        password: String,
        fallbackDevice: RemoteTargetDevice? = null
    ): Result<DirectoryListingResult>

    suspend fun probeDeviceUuid(
        hostAddress: String,
        port: Int,
        isHttps: Boolean = false
    ): String?
}
