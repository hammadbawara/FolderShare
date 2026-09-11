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
        val inputStream = localFileSource.openStream()
        val buffer = ByteArray(64 * 1024)
        var totalUploaded = startByte
        val totalSize = localFileSource.size.takeIf { it > 0 }

        inputStream.use { stream ->
            if (startByte > 0) {
                var skipped = 0L
                while (skipped < startByte) {
                    val toSkip = startByte - skipped
                    val actualSkipped = stream.skip(toSkip)
                    if (actualSkipped <= 0) {
                        val r = stream.read(buffer, 0, minOf(buffer.size.toLong(), toSkip).toInt())
                        if (r <= 0) break
                        skipped += r
                    } else {
                        skipped += actualSkipped
                    }
                }
            }

            onProgress?.invoke(totalUploaded, totalSize)

            while (true) {
                val read = stream.read(buffer)
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

    private suspend fun handleResponseStatus(response: HttpResponse, targetUrl: String) {
        val status = response.status.value
        if (response.status.isSuccess() || status == 207 || status == 201 || status == 204) {
            return
        }

        val body = try { response.bodyAsText() } catch (_: Exception) { "" }
        logger.w { "[HTTP $status] $targetUrl | Response: ${body.take(200)}" }

        val exception = when (status) {
            401 -> WebDavException.Unauthorized()
            403 -> WebDavException.PermissionDenied(targetUrl)
            404 -> WebDavException.NotFound(targetUrl)
            405 -> WebDavException.MethodNotAllowed("Method not allowed for $targetUrl")
            409 -> WebDavException.Conflict()
            in 500..599 -> WebDavException.ServerError(status)
            else -> WebDavException.Unknown(status)
        }
        throw exception
    }

    private fun handleException(e: Throwable, targetUrl: String): WebDavException {
        if (e is CancellationException) throw e
        if (e is WebDavException) return e

        logger.e(e) { "Exception accessing $targetUrl: ${e.message}" }

        return when {
            e is HttpRequestTimeoutException || e.message?.contains("Timeout", ignoreCase = true) == true -> {
                WebDavException.Timeout("Request timed out while reaching $targetUrl", e)
            }
            else -> {
                WebDavException.NetworkError("Network error accessing $targetUrl: ${e.message ?: "Connection failed"}", e)
            }
        }
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
            handleResponseStatus(response, targetUrl)
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
                    val parentHeadHeaders = head(parentUrl, username, password)
                    val writeAllowedHeader = parentHeadHeaders.entries.firstOrNull { it.key.equals("x-write-allowed", ignoreCase = true) }?.value?.firstOrNull()
                    if (writeAllowedHeader.equals("false", ignoreCase = true)) {
                        throw WebDavException.PermissionDenied(parentUrl)
                    }
                    val allowHeader = parentHeadHeaders.entries.firstOrNull { it.key.equals("allow", ignoreCase = true) }?.value?.firstOrNull()
                    if (allowHeader != null && !allowHeader.contains("PUT", ignoreCase = true) && !allowHeader.contains("MKCOL", ignoreCase = true)) {
                        throw WebDavException.PermissionDenied(parentUrl)
                    }
                } catch (e: WebDavException) {
                    throw e
                } catch (_: Exception) {
                    // Fallback if parent HEAD is not supported
                }
            }

            // Check resume start byte if file exists on remote
            var resumeStartByte = 0L
            try {
                val headHeaders = head(currentTargetUrl, username, password)
                val contentLengthStr = headHeaders.entries.firstOrNull { it.key.equals(HttpHeaders.ContentLength, ignoreCase = true) }?.value?.firstOrNull()
                val existingLength = contentLengthStr?.toLongOrNull() ?: 0L
                if (existingLength > 0 && existingLength < localFileSource.size) {
                    resumeStartByte = existingLength
                }
            } catch (e: WebDavException) {
                if (e is WebDavException.PermissionDenied) throw e
            } catch (_: Exception) {
                // If HEAD fails or gives non-permission error (e.g. 404), proceed normally
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
            logger.e(e) { "Upload to $currentTargetUrl failed" }
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
        try {
            val parentDir = destinationPath.parent
            if (parentDir != null && !fileSystem.exists(parentDir)) {
                fileSystem.createDirectories(parentDir)
            }

            val partPath = destinationPath.parent?.let { it / "${destinationPath.name}.part" }
                ?: "${destinationPath}.part".toPath()

            var currentTargetUrl = activeUrlProvider?.invoke() ?: targetUrl
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
                            safeMove(partPath, destinationPath)
                            onProgress?.invoke(currentPartSize, serverLength)
                            streamCompleted = true
                        }
                        return@execute
                    }

                    handleResponseStatus(response, currentTargetUrl)

                    val isPartial = response.status.value == 206
                    val responseLength = response.contentLength()?.takeIf { it > 0 }
                    val totalBytes = if (isPartial && responseLength != null) currentPartSize + responseLength else responseLength

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var bytesDownloaded = if (isPartial) currentPartSize else 0L

                    val append = isPartial && currentPartSize > 0
                    val sink = if (append) fileSystem.appendingSink(partPath) else fileSystem.sink(partPath)
                    sink.buffer().use { bufferedSink ->
                        while (!channel.isClosedForRead) {
                            currentCoroutineContext().ensureActive()

                            // Check if a better connection was chosen dynamically
                            val latestUrl = activeUrlProvider?.invoke()
                            if (latestUrl != null && latestUrl.isNotBlank() && latestUrl != currentTargetUrl) {
                                logger.d { "Active link changed mid-download from $currentTargetUrl to $latestUrl. Migrating stream..." }
                                currentTargetUrl = latestUrl
                                connectionSwitched = true
                                bufferedSink.flush()
                                break
                            }

                            val read = withTimeoutOrNull(5.seconds) {
                                channel.readAvailable(buffer, 0, buffer.size)
                            } ?: throw WebDavException.Timeout("Transfer stalled: target device disconnected")

                            if (read <= 0) break
                            bufferedSink.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress?.invoke(bytesDownloaded, totalBytes)
                        }
                        bufferedSink.flush()
                    }

                    if (!connectionSwitched) {
                        safeMove(partPath, destinationPath)
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
            logger.e(e) { "Download failed from $targetUrl: ${e.message}" }
            throw handleException(e, targetUrl)
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

    override suspend fun head(
        targetUrl: String,
        username: String?,
        password: String?
    ): Map<String, List<String>> {
        return try {
            val response = client.request(targetUrl) {
                method = HttpMethod.Head
                addAuthHeader(username, password)
            }
            handleResponseStatus(response, targetUrl)
            response.headers.entries().associate { it.key to it.value }
        } catch (e: Exception) {
            throw handleException(e, targetUrl)
        }
    }

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

