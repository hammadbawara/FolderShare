package com.hz_apps.foldershare.core.explorer.client

import com.hz_apps.foldershare.core.explorer.model.LocalFileSource

/**
 * Interface encapsulating low-level WebDAV operations and HTTP mechanics.
 * Follows Single Responsibility Principle (SRP) by handling WebDAV communications separately from repository logic.
 */
interface WebDavClient {

    /**
     * Executes PROPFIND request to retrieve raw XML directory listing.
     */
    suspend fun propfind(
        targetUrl: String,
        username: String?,
        password: String?,
        depth: String = "1"
    ): String

    /**
     * Executes MKCOL request to create a directory.
     */
    suspend fun mkcol(
        targetUrl: String,
        username: String?,
        password: String?
    )

    /**
     * Executes PUT request to upload raw ByteArray content (for small files).
     */
    suspend fun putContent(
        targetUrl: String,
        content: ByteArray,
        username: String?,
        password: String?
    )

    /**
     * Executes PUT request to stream a LocalFileSource with progress callback, resume support, and dynamic URL switching.
     */
    suspend fun putFile(
        targetUrl: String,
        localFileSource: LocalFileSource,
        username: String?,
        password: String?,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)? = null
    )

    /**
     * Executes GET request to stream a remote file to a local destination path with progress callback,
     * resume support, and seamless mid-transfer connection migration via activeUrlProvider.
     */
    suspend fun downloadFile(
        targetUrl: String,
        destinationPath: okio.Path,
        username: String?,
        password: String?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)? = null
    ): String

    /**
     * Cleans up any incomplete .part download file associated with the destination path.
     */
    fun cleanupPartialDownload(destinationPath: okio.Path)

    /**
     * Executes DELETE request to delete a resource.
     */
    suspend fun delete(
        targetUrl: String,
        username: String?,
        password: String?
    )

    /**
     * Executes MOVE request to rename or move a resource.
     */
    suspend fun move(
        targetUrl: String,
        destinationPath: String,
        username: String?,
        password: String?
    )

    /**
     * Executes HEAD request to check resource existence, content length, or permission capabilities.
     */
    suspend fun head(
        targetUrl: String,
        username: String?,
        password: String?
    ): Map<String, List<String>>

    /**
     * Lightly probes target endpoint to extract X-Device-UUID header.
     */
    suspend fun probeDeviceUuid(targetUrl: String): String?

    /**
     * Lightly probes target endpoint to measure network round-trip latency in milliseconds.
     * Returns null if target is unreachable or timed out.
     */
    suspend fun probeLatency(targetUrl: String): Long?
}


