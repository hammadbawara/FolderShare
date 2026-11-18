package com.hz_apps.foldershare.core.explorer.client

import co.touchlab.kermit.Logger
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.WebDavException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("KtorWebDavClient")

private class UploadProgressChannelContent(
    private val localFileSource: LocalFileSource,
    private val startByte: Long = 0L,
    private val onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?
) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long? get() = localFileSource.size.takeIf { it > 0 }?.minus(startByte)?.coerceAtLeast(0L)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val inputStream = try {
            localFileSource.openStream()
        } catch (e: Exception) {
            logger.d(e) { "Failed to open local file stream for upload: ${localFileSource.name}" }
            throw WebDavException.StorageError(
                isOutOfSpace = false,
                message = "Could not open '${localFileSource.name}' for upload",
                cause = e
            )
        }
        val buffer = ByteArray(64 * 1024)
        var totalUploaded = startByte
        val totalSize = localFileSource.size.takeIf { it > 0 }

        inputStream.use { stream ->
            if (startByte > 0) {
                var skipped = 0L
                while (skipped < startByte) {
                    val toSkip = startByte - skipped
                    val actualSkipped = try {
                        stream.skip(toSkip)
                    } catch (e: Exception) {
                        logger.d(e) { "Failed to seek local file for upload: ${localFileSource.name}" }
                        throw WebDavException.StorageError(
                            isOutOfSpace = false,
                            message = "Could not read '${localFileSource.name}'",
                            cause = e
                        )
                    }
                    if (actualSkipped <= 0) {
                        val r = try {
                            stream.read(buffer, 0, minOf(buffer.size.toLong(), toSkip).toInt())
                        } catch (e: Exception) {
                            logger.d(e) { "Failed to read local file during seek: ${localFileSource.name}" }
                            throw WebDavException.StorageError(
                                isOutOfSpace = false,
                                message = "Could not read '${localFileSource.name}'",
                                cause = e
                            )
                        }
                        if (r <= 0) break
                        skipped += r
                    } else {
                        skipped += actualSkipped
                    }
                }
            }

            onProgress?.invoke(totalUploaded, totalSize)

            while (true) {
                val read = try {
                    stream.read(buffer)
                } catch (e: Exception) {
                    logger.d(e) { "Failed to read local file during upload: ${localFileSource.name}" }
                    throw WebDavException.StorageError(
                        isOutOfSpace = false,
                        message = "Could not read '${localFileSource.name}'",
                        cause = e
                    )
                }
                if (read <= 0) break
                channel.writeFully(buffer, 0, read)
                channel.flush()
                totalUploaded += read
                onProgress?.invoke(totalUploaded, totalSize)
            }
        }
    }
}

/**
 * Concrete Implementation of WebDavClient using Ktor HttpClient.
 * Encapsulates low-level HTTP calls, header generation, technical log output, and exception mapping.
 */
class KtorWebDavClient(
    private val client: HttpClient,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : WebDavClient {

    private fun HttpRequestBuilder.addAuthHeader(username: String?, password: String?) {
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val credentials = Base64.encode("$username:$password".encodeToByteArray())
            header(HttpHeaders.Authorization, "Basic $credentials")
        }
    }

    private fun extractPlainServerReason(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("<", ignoreCase = true)) return null
        return trimmed.lineSequence().firstOrNull()?.trim()?.take(160)?.ifEmpty { null }
    }

    private fun isOutOfSpaceThrowable(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            val msg = current.message.orEmpty()
            if (msg.contains("No space left on device", ignoreCase = true) ||
                msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("disk full", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private inline fun <T> runLocalStorageIo(actionDescription: String, block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavException) {
            throw e
        } catch (e: Exception) {
            val outOfSpace = isOutOfSpaceThrowable(e)
            logger.d(e) { "Local storage error during $actionDescription" }
            throw WebDavException.StorageError(isOutOfSpace = outOfSpace, cause = e)
        }
    }

    private suspend fun handleResponseStatus(
        response: HttpResponse,
        targetUrl: String,
        isReadOperation: Boolean = false,
        logError: Boolean = true
    ) {
        val status = response.status.value
        if (response.status.isSuccess() || status == 207 || status == 201 || status == 204) {
            return
        }

        val body = try { response.bodyAsText() } catch (_: Exception) { "" }
        if (logError) {
            logger.d { "[HTTP $status] $targetUrl | Response: ${body.take(200)}" }
        }
        val serverReason = extractPlainServerReason(body)

        val exception = when (status) {
            401 -> WebDavException.Unauthorized()
            403 -> WebDavException.PermissionDenied(
                path = targetUrl,
                isReadOperation = isReadOperation,
                serverReason = serverReason
            )
            404 -> WebDavException.NotFound(path = targetUrl)
            405 -> WebDavException.MethodNotAllowed(
                serverReason = serverReason,
                message = when {
                    serverReason?.contains("already exists", ignoreCase = true) == true ->
                        "A file or folder with that name already exists"
                    serverReason != null -> serverReason
                    else -> "This action is not allowed here"
                }
            )
            409 -> WebDavException.Conflict()
            in 500..599 -> WebDavException.ServerError(status, serverReason = serverReason)
            else -> WebDavException.Unknown(status, serverReason = serverReason)
        }
        throw exception
    }

    private fun handleException(e: Throwable, targetUrl: String): WebDavException {
        if (e is CancellationException) throw e
        if (e is WebDavException) return e

        logger.d(e) { "WebDAV request failed for $targetUrl: ${e.message}" }

        if (isOutOfSpaceThrowable(e)) {
            return WebDavException.StorageError(isOutOfSpace = true, cause = e)
        }

        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            val simpleName = current::class.simpleName.orEmpty()
            val msg = current.message.orEmpty()
            when {
                current is HttpRequestTimeoutException ||
                    simpleName.contains("SocketTimeout", ignoreCase = true) ||
                    simpleName.contains("ConnectTimeout", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) -> {
                    return WebDavException.Timeout(cause = e)
                }
                simpleName.contains("UnknownHost", ignoreCase = true) ||
                    msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Device could not be found on your network. Check your Wi-Fi connection.",
                        cause = e
                    )
                }
                simpleName.contains("ConnectException", ignoreCase = true) ||
                    msg.contains("ECONNREFUSED", ignoreCase = true) ||
                    msg.contains("Connection refused", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Connection refused. Make sure Folder Share is running on the device.",
                        cause = e
                    )
                }
                simpleName.contains("NoRouteToHost", ignoreCase = true) ||
                    msg.contains("No route to host", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) ||
                    msg.contains("ENETUNREACH", ignoreCase = true) ||
                    msg.contains("EHOSTUNREACH", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Device is unreachable. Check that both devices are on the same Wi-Fi or Hotspot.",
                        cause = e
                    )
                }
                msg.contains("Cleartext HTTP traffic", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Local HTTP connection was blocked by the system.",
                        cause = e
                    )
                }
                simpleName.contains("SSL", ignoreCase = true) ||
                    simpleName.contains("Certificate", ignoreCase = true) ||
                    msg.contains("Trust anchor", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Secure connection to the device failed.",
                        cause = e
                    )
                }
                simpleName.contains("SocketException", ignoreCase = true) ||
                    simpleName.contains("EOFException", ignoreCase = true) ||
                    simpleName.contains("StreamResetException", ignoreCase = true) ||
                    msg.contains("Connection reset", ignoreCase = true) ||
                    msg.contains("Broken pipe", ignoreCase = true) ||
                    msg.contains("Software caused connection abort", ignoreCase = true) ||
                    msg.contains("unexpected end of stream", ignoreCase = true) ||
                    msg.contains("Socket closed", ignoreCase = true) -> {
                    return WebDavException.NetworkError(
                        message = "Connection to the device was lost.",
                        cause = e
                    )
                }
            }
            current = current.cause
            depth++
        }

        return WebDavException.NetworkError(cause = e)
    }

    override suspend fun propfind(
        targetUrl: String,
        username: String?,
        password: String?,
        depth: String
    ): String {
        return try {
            val response = client.request(targetUrl) {
                method = HttpMethod("PROPFIND")
                header("Depth", depth)
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl, isReadOperation = true)
            response.bodyAsText()
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

    override suspend fun mkcol(
        targetUrl: String,
        username: String?,
        password: String?
    ) {
        try {
            val response = client.request(targetUrl) {
                method = HttpMethod("MKCOL")
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl)
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

    override suspend fun putContent(
        targetUrl: String,
        content: ByteArray,
        username: String?,
        password: String?
    ) {
        try {
            val response = client.request(targetUrl) {
                method = HttpMethod.Put
                setBody(content)
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl)
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

    override suspend fun putFile(
        targetUrl: String,
        localFileSource: LocalFileSource,
        username: String?,
        password: String?,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ) {
        var currentTargetUrl = activeUrlProvider?.invoke() ?: targetUrl
        try {
            // Pre-flight check: verify parent directory/resource permissions before streaming body
            val parentUrl = currentTargetUrl.substringBeforeLast('/')
            if (parentUrl.isNotEmpty()) {
                try {
                    val parentHeadHeaders = headInternal(parentUrl, username, password, logError = false)
                    val writeAllowedHeader = parentHeadHeaders.entries.firstOrNull { it.key.equals("x-write-allowed", ignoreCase = true) }?.value?.firstOrNull()
                    if (writeAllowedHeader.equals("false", ignoreCase = true)) {
                        logger.d { "Pre-flight upload check: write access disabled on $parentUrl" }
                        throw WebDavException.PermissionDenied(parentUrl)
                    }
                    val allowHeader = parentHeadHeaders.entries.firstOrNull { it.key.equals("allow", ignoreCase = true) }?.value?.firstOrNull()
                    if (allowHeader != null && !allowHeader.contains("PUT", ignoreCase = true) && !allowHeader.contains("MKCOL", ignoreCase = true)) {
                        logger.d { "Pre-flight upload check: PUT/MKCOL disallowed on $parentUrl ($allowHeader)" }
                        throw WebDavException.PermissionDenied(parentUrl)
                    }
                } catch (e: WebDavException) {
                    if (e is WebDavException.PermissionDenied || e is WebDavException.Unauthorized) {
                        throw e
                    }
                    // Ignore non-permission probe errors (e.g., 404/405 on non-standard servers)
                } catch (_: Exception) {
                    // Fallback if parent HEAD is not supported
                }
            }

            // Check resume start byte if file exists on remote
            var resumeStartByte = 0L
            try {
                val headHeaders = headInternal(currentTargetUrl, username, password, logError = false)
                val contentLengthStr = headHeaders.entries.firstOrNull { it.key.equals(HttpHeaders.ContentLength, ignoreCase = true) }?.value?.firstOrNull()
                val existingLength = contentLengthStr?.toLongOrNull() ?: 0L
                if (existingLength > 0 && existingLength < localFileSource.size) {
                    resumeStartByte = existingLength
                }
            } catch (e: WebDavException) {
                if (e is WebDavException.PermissionDenied) {
                    logger.d { "Resume HEAD check denied write permission on $currentTargetUrl" }
                    throw e
                }
            } catch (_: Exception) {
                // If HEAD fails or gives non-permission error (e.g. 404 for new file), proceed normally
            }

            val bodyContent = UploadProgressChannelContent(localFileSource, resumeStartByte, onProgress)

            logger.d { "Starting upload to $currentTargetUrl (resumeStartByte=$resumeStartByte, totalSize=${localFileSource.size})" }
            val response = client.request(currentTargetUrl) {
                method = HttpMethod.Put
                if (resumeStartByte > 0) {
                    header(HttpHeaders.ContentRange, "bytes $resumeStartByte-${localFileSource.size - 1}/${localFileSource.size}")
                }
                setBody(bodyContent)
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, currentTargetUrl)
            logger.d { "Upload to $currentTargetUrl completed successfully" }
        } catch (e: Exception) {
            throw handleException(e, currentTargetUrl)
        }
    }

    private fun safeMove(source: Path, destination: Path) {
        if (fileSystem.exists(destination)) {
            fileSystem.delete(destination)
        }
        try {
            fileSystem.atomicMove(source, destination)
        } catch (_: Exception) {
            fileSystem.copy(source, destination)
            fileSystem.delete(source)
        }
    }

    override fun cleanupPartialDownload(destinationPath: Path) {
        try {
            val partPath = destinationPath.parent?.let { it / "${destinationPath.name}.part" }
                ?: "${destinationPath}.part".toPath()
            if (fileSystem.exists(partPath)) {
                fileSystem.delete(partPath)
            }
        } catch (_: Exception) {
        }
    }

    override suspend fun downloadFile(
        targetUrl: String,
        destinationPath: Path,
        username: String?,
        password: String?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ): String {
        var currentTargetUrl = activeUrlProvider?.invoke() ?: targetUrl
        try {
            val parentDir = destinationPath.parent
            if (parentDir != null && !fileSystem.exists(parentDir)) {
                runLocalStorageIo("creating download directory $parentDir") {
                    fileSystem.createDirectories(parentDir)
                }
            }

            val partPath = destinationPath.parent?.let { it / "${destinationPath.name}.part" }
                ?: "${destinationPath}.part".toPath()

            logger.d { "Starting download from $currentTargetUrl (partPath=$partPath)" }

            while (true) {
                currentCoroutineContext().ensureActive()
                val currentPartSize = if (fileSystem.exists(partPath)) {
                    fileSystem.metadataOrNull(partPath)?.size ?: 0L
                } else {
                    0L
                }

                var connectionSwitched = false
                var streamCompleted = false
                var rangeSatisfiable = true

                client.prepareGet(currentTargetUrl) {
                    if (currentPartSize > 0) {
                        header(HttpHeaders.Range, "bytes=$currentPartSize-")
                    }
                    addAuthHeader(username, password)
                    timeout {
                        requestTimeoutMillis = null
                        socketTimeoutMillis = 15_000L
                        connectTimeoutMillis = 5_000L
                    }
                }.execute { response ->
                    if (response.status.value == 416) {
                        logger.d { "Download from $currentTargetUrl: HTTP 416 Range Not Satisfiable. Checking if file is complete." }
                        rangeSatisfiable = false
                        val contentRange = response.headers[HttpHeaders.ContentRange]
                        val serverLength = contentRange?.substringAfter("/", "")?.trim()?.toLongOrNull()
                        if (serverLength != null && currentPartSize == serverLength && currentPartSize > 0) {
                            logger.d { "File already fully downloaded: $currentTargetUrl" }
                            runLocalStorageIo("moving completed download to $destinationPath") {
                                safeMove(partPath, destinationPath)
                            }
                            onProgress?.invoke(currentPartSize, serverLength)
                            streamCompleted = true
                        }
                        return@execute
                    }

                    handleResponseStatus(response, currentTargetUrl, isReadOperation = true)

                    val isPartial = response.status.value == 206
                    val responseLength = response.contentLength()?.takeIf { it > 0 }
                    val totalBytes = if (isPartial && responseLength != null) currentPartSize + responseLength else responseLength

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var bytesDownloaded = if (isPartial) currentPartSize else 0L

                    val append = isPartial && currentPartSize > 0
                    val sink = runLocalStorageIo("opening download sink $partPath") {
                        if (append) fileSystem.appendingSink(partPath) else fileSystem.sink(partPath)
                    }
                    sink.buffer().use { bufferedSink ->
                        while (!channel.isClosedForRead) {
                            currentCoroutineContext().ensureActive()

                            // Check if a better connection was chosen dynamically
                            val latestUrl = activeUrlProvider?.invoke()
                            if (latestUrl != null && latestUrl.isNotBlank() && latestUrl != currentTargetUrl) {
                                logger.d { "Active link changed mid-download from $currentTargetUrl to $latestUrl. Migrating stream..." }
                                currentTargetUrl = latestUrl
                                connectionSwitched = true
                                runLocalStorageIo("flushing partial download $partPath") {
                                    bufferedSink.flush()
                                }
                                break
                            }

                            val read = withTimeoutOrNull(5.seconds) {
                                channel.readAvailable(buffer, 0, buffer.size)
                            } ?: run {
                                logger.d { "Download stalled (no data for 5s) from $currentTargetUrl" }
                                throw WebDavException.Timeout("Transfer stalled because the device stopped responding")
                            }

                            if (read <= 0) break
                            runLocalStorageIo("writing downloaded chunk to $partPath") {
                                bufferedSink.write(buffer, 0, read)
                            }
                            bytesDownloaded += read
                            onProgress?.invoke(bytesDownloaded, totalBytes)
                        }
                        runLocalStorageIo("flushing download file $partPath") {
                            bufferedSink.flush()
                        }
                    }

                    if (!connectionSwitched) {
                        runLocalStorageIo("moving completed download to $destinationPath") {
                            safeMove(partPath, destinationPath)
                        }
                        logger.d { "Download from $currentTargetUrl completed successfully (isPartial=$isPartial)" }
                        onProgress?.invoke(totalBytes ?: bytesDownloaded, totalBytes ?: bytesDownloaded)
                        streamCompleted = true
                    }
                }

                if (streamCompleted) {
                    break
                }

                if (connectionSwitched) {
                    continue
                }

                if (!rangeSatisfiable && !fileSystem.exists(destinationPath)) {
                    if (fileSystem.exists(partPath)) {
                        fileSystem.delete(partPath)
                    }
                    continue
                }

                break
            }

            return destinationPath.toString()
        } catch (e: Exception) {
            throw handleException(e, currentTargetUrl)
        }
    }

    override suspend fun delete(
        targetUrl: String,
        username: String?,
        password: String?
    ) {
        try {
            val response = client.request(targetUrl) {
                method = HttpMethod.Delete
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl)
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

    override suspend fun move(
        targetUrl: String,
        destinationPath: String,
        username: String?,
        password: String?
    ) {
        try {
            val response = client.request(targetUrl) {
                method = HttpMethod("MOVE")
                header("Destination", destinationPath)
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl)
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

    private suspend fun headInternal(
        targetUrl: String,
        username: String?,
        password: String?,
        logError: Boolean
    ): Map<String, List<String>> {
        return try {
            val response = client.request(targetUrl) {
                method = HttpMethod.Head
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl, isReadOperation = true, logError = logError)
            response.headers.entries().associate { it.key to it.value }
        } catch (e: Exception) {
            if (logError) {
                throw handleException(e, targetUrl)
            }
            if (e is CancellationException) throw e
            if (e is WebDavException) throw e
            throw WebDavException.NetworkError(cause = e)
        }
    }

    override suspend fun head(
        targetUrl: String,
        username: String?,
        password: String?
    ): Map<String, List<String>> = headInternal(targetUrl, username, password, logError = true)

    override suspend fun probeDeviceUuid(targetUrl: String): String? {
        return try {
            withTimeoutOrNull(2000L) {
                val response = client.request(targetUrl) {
                    method = HttpMethod.Options
                }
                response.headers.entries()
                    .firstOrNull { it.key.equals("X-Device-UUID", ignoreCase = true) }
                    ?.value?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeLatency(targetUrl: String): Long? {
        return try {
            val start = System.currentTimeMillis()
            val result = withTimeoutOrNull(3000L) {
                client.request(targetUrl) {
                    method = HttpMethod.Options
                }
            }
            if (result != null) {
                val elapsed = System.currentTimeMillis() - start
                elapsed.coerceAtLeast(0L)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

