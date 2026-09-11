package com.hz_apps.foldershare.core.explorer.repository

import com.hz_apps.foldershare.core.discovery.DeviceResolver
import com.hz_apps.foldershare.core.explorer.client.KtorWebDavClient
import com.hz_apps.foldershare.core.explorer.client.WebDavClient
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.model.WebDavException
import com.hz_apps.foldershare.core.explorer.parser.WebDavXmlParser
import com.hz_apps.foldershare.core.explorer.util.PathUtils
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import com.hz_apps.foldershare.core.explorer.util.getPlatformFileHandler
import com.hz_apps.foldershare.core.util.formatHostForUrl
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64

/**
 * Concrete implementation of RemoteFileRepository that delegates low-level WebDAV HTTP operations
 * to a dedicated WebDavClient, adhering to SOLID principles (Single Responsibility & Dependency Inversion).
 * Encapsulates dynamic device endpoint resolution, credential lookup, and automatic IP re-resolution.
 */
class WebDavRemoteFileRepository(
    private val webDavClient: WebDavClient = KtorWebDavClient(createHttpClient()),
    private val deviceResolver: DeviceResolver? = null,
    private val credentialDao: DeviceCredentialDao? = null,
    private val platformFileHandler: PlatformFileHandler = getPlatformFileHandler(),
    private val fileSystem: okio.FileSystem = okio.FileSystem.SYSTEM
) : RemoteFileRepository {

    private data class ResolvedDeviceSession(
        val hostAddress: String,
        val port: Int,
        val isHttps: Boolean,
        val username: String?,
        val password: String?
    )

    private suspend fun resolveSession(deviceId: String, fallbackDevice: RemoteTargetDevice?): ResolvedDeviceSession? {
        val endpoint = deviceResolver?.resolveEndpoint(deviceId, fallbackDevice) ?: fallbackDevice ?: return null
        val savedCred = credentialDao?.getCredentialForDevice(deviceId)
        return ResolvedDeviceSession(
            hostAddress = endpoint.hostAddress,
            port = endpoint.port,
            isHttps = endpoint.isHttps,
            username = savedCred?.username,
            password = savedCred?.password
        )
    }

    /**
     * Reusable higher-order executor (DRY). Encapsulates session lookup, exception catching into Result<T>,
     * and dynamic mDNS IP re-resolution if a device's IP address changes while its UUID stays the same.
     */
    private suspend fun <T> executeWithSession(
        deviceId: String,
        fallbackDevice: RemoteTargetDevice? = null,
        action: suspend (session: ResolvedDeviceSession) -> T
    ): Result<T> {
        val session = resolveSession(deviceId, fallbackDevice)
            ?: return Result.failure(IllegalStateException("Device '$deviceId' could not be resolved."))

        val firstResult = runCatching { action(session) }
        val exception = firstResult.exceptionOrNull()
        if (exception is CancellationException) {
            throw exception
        }
        if (firstResult.isSuccess || deviceResolver == null) return firstResult

        if (exception is WebDavException && !exception.isRetryable) {
            return firstResult
        }

        // Network error occurred. Invalidate this endpoint and refresh mDNS discovery
        deviceResolver.invalidateEndpoint(session.hostAddress, session.port)
        deviceResolver.refresh()
        val refreshedSession = resolveSession(deviceId, fallbackDevice)
        if (refreshedSession != null) {
            val retryResult = runCatching { action(refreshedSession) }
            val retryException = retryResult.exceptionOrNull()
            if (retryException is CancellationException) {
                throw retryException
            }
            return retryResult
        }

        return firstResult
    }

    private fun buildTargetUrl(isHttps: Boolean, hostAddress: String, port: Int, path: String): String {
        val scheme = if (isHttps) "https" else "http"
        val normalizedPath = PathUtils.normalizePath(path)
        val encodedPath = normalizedPath.encodeURLPath()
        val formattedHost = formatHostForUrl(hostAddress)
        return "$scheme://$formattedHost:$port$encodedPath"
    }

    override suspend fun listFiles(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<DirectoryListingResult> = executeWithSession(deviceId, fallbackDevice) { session ->
        val normalizedPath = PathUtils.normalizePath(path)
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, path)
        val xmlContent = webDavClient.propfind(
            targetUrl = targetUrl,
            username = session.username,
            password = session.password,
            depth = "1"
        )
        WebDavXmlParser.parsePropfindResponse(xmlContent, normalizedPath)
    }

    override suspend fun createFolder(
        deviceId: String,
        parentPath: String,
        folderName: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> = executeWithSession(deviceId, fallbackDevice) { session ->
        val folderPath = PathUtils.joinPath(parentPath, folderName)
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, folderPath)
        webDavClient.mkcol(targetUrl = targetUrl, username = session.username, password = session.password)
    }

    override suspend fun createFile(
        deviceId: String,
        parentPath: String,
        fileName: String,
        content: ByteArray,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> = executeWithSession(deviceId, fallbackDevice) { session ->
        val filePath = PathUtils.joinPath(parentPath, fileName)
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, filePath)
        webDavClient.putContent(targetUrl = targetUrl, content = content, username = session.username, password = session.password)
    }

    override suspend fun uploadFile(
        deviceId: String,
        parentPath: String,
        localFileSource: LocalFileSource,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> = executeWithSession(deviceId, fallbackDevice) { session ->
        val filePath = PathUtils.joinPath(parentPath, localFileSource.name)
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, filePath)
        webDavClient.putFile(
            targetUrl = targetUrl,
            localFileSource = localFileSource,
            username = session.username,
            password = session.password,
            onProgress = onProgress,
            activeUrlProvider = {
                deviceResolver?.resolveEndpoint(deviceId, fallbackDevice)?.let {
                    buildTargetUrl(it.isHttps, it.hostAddress, it.port, filePath)
                } ?: targetUrl
            }
        )
    }

    override suspend fun downloadFile(
        deviceId: String,
        path: String,
        fileName: String,
        destinationPath: okio.Path?,
        destinationDirectory: okio.Path?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)?,
        fallbackDevice: RemoteTargetDevice?
    ): Result<String> = executeWithSession(deviceId, fallbackDevice) { session ->
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, path)
        val resolvedDestinationPath = destinationPath ?: run {
            val dir = destinationDirectory ?: platformFileHandler.getDefaultDownloadDirectory()
            PathUtils.generateUniqueDestinationPath(dir, fileName, fileSystem)
        }
        webDavClient.downloadFile(
            targetUrl = targetUrl,
            destinationPath = resolvedDestinationPath,
            username = session.username,
            password = session.password,
            onProgress = onProgress,
            activeUrlProvider = {
                deviceResolver?.resolveEndpoint(deviceId, fallbackDevice)?.let {
                    buildTargetUrl(it.isHttps, it.hostAddress, it.port, path)
                } ?: targetUrl
            }
        )
    }

    override fun cleanupPartialDownload(destinationPath: okio.Path) {
        webDavClient.cleanupPartialDownload(destinationPath)
    }

    override suspend fun deleteFile(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> = executeWithSession(deviceId, fallbackDevice) { session ->
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, path)
        webDavClient.delete(targetUrl = targetUrl, username = session.username, password = session.password)
    }

    override suspend fun renameFile(
        deviceId: String,
        oldPath: String,
        newName: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<Unit> = executeWithSession(deviceId, fallbackDevice) { session ->
        val normalizedOldPath = PathUtils.normalizePath(oldPath)
        val parentPath = PathUtils.getParentPath(normalizedOldPath)
        val destinationPath = PathUtils.joinPath(parentPath, newName)
        val targetUrl = buildTargetUrl(session.isHttps, session.hostAddress, session.port, oldPath)
        webDavClient.move(
            targetUrl = targetUrl,
            destinationPath = destinationPath,
            username = session.username,
            password = session.password
        )
    }

    override suspend fun getDownloadUrl(
        deviceId: String,
        path: String,
        fallbackDevice: RemoteTargetDevice?
    ): String {
        val endpoint = deviceResolver?.resolveEndpoint(deviceId, fallbackDevice) ?: fallbackDevice ?: return ""
        val username = credentialDao?.getCredentialForDevice(deviceId)?.username
        val password = credentialDao?.getCredentialForDevice(deviceId)?.password
        val baseUrl = buildTargetUrl(endpoint.isHttps, endpoint.hostAddress, endpoint.port, path)
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val credentials = Base64.encode("$username:$password".encodeToByteArray())
            val separator = if (baseUrl.contains("?")) "&" else "?"
            return "$baseUrl${separator}auth=$credentials"
        }
        return baseUrl
    }

    override suspend fun authenticateDevice(
        deviceId: String,
        username: String,
        password: String,
        fallbackDevice: RemoteTargetDevice?
    ): Result<DirectoryListingResult> = runCatching {
        val endpoint = deviceResolver?.resolveEndpoint(deviceId, fallbackDevice) ?: fallbackDevice
            ?: throw IllegalStateException("Device '$deviceId' could not be resolved.")
        val normalizedPath = PathUtils.normalizePath("/")
        val targetUrl = buildTargetUrl(endpoint.isHttps, endpoint.hostAddress, endpoint.port, "/")
        val xmlContent = webDavClient.propfind(
            targetUrl = targetUrl,
            username = username,
            password = password,
            depth = "1"
        )
        WebDavXmlParser.parsePropfindResponse(xmlContent, normalizedPath)
    }

    override suspend fun probeDeviceUuid(
        hostAddress: String,
        port: Int,
        isHttps: Boolean
    ): String? {
        val targetUrl = buildTargetUrl(isHttps, hostAddress, port, "/")
        return webDavClient.probeDeviceUuid(targetUrl)
    }
}
