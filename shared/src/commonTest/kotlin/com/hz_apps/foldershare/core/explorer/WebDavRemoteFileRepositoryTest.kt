package com.hz_apps.foldershare.core.explorer

import com.hz_apps.foldershare.core.explorer.client.WebDavClient
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.core.explorer.model.WebDavException
import com.hz_apps.foldershare.core.explorer.repository.WebDavRemoteFileRepository
import com.hz_apps.foldershare.core.util.formatHostForUrl
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeWebDavClient : WebDavClient {
    var shouldFailWithException: WebDavException? = null
    var lastPutTargetUrl: String? = null
    var lastDownloadTargetUrl: String? = null
    var lastDownloadDestinationPath: okio.Path? = null

    override suspend fun propfind(
        targetUrl: String,
        username: String?,
        password: String?,
        depth: String
    ): String {
        shouldFailWithException?.let { throw it }
        return """<?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>/test/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:resourcetype><D:collection/></D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
            </D:multistatus>""".trimIndent()
    }

    override suspend fun mkcol(targetUrl: String, username: String?, password: String?) {
        shouldFailWithException?.let { throw it }
    }

    override suspend fun putContent(targetUrl: String, content: ByteArray, username: String?, password: String?) {
        shouldFailWithException?.let { throw it }
    }

    override suspend fun putFile(
        targetUrl: String,
        localFileSource: LocalFileSource,
        username: String?,
        password: String?,
        onProgress: ((bytesUploaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ) {
        lastPutTargetUrl = activeUrlProvider?.invoke() ?: targetUrl
        shouldFailWithException?.let { throw it }
    }

    override suspend fun downloadFile(
        targetUrl: String,
        destinationPath: okio.Path,
        username: String?,
        password: String?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ): String {
        lastDownloadTargetUrl = activeUrlProvider?.invoke() ?: targetUrl
        lastDownloadDestinationPath = destinationPath
        shouldFailWithException?.let { throw it }
        return destinationPath.toString()
    }

    override fun cleanupPartialDownload(destinationPath: okio.Path) {
    }

    override suspend fun delete(targetUrl: String, username: String?, password: String?) {
        shouldFailWithException?.let { throw it }
    }

    override suspend fun move(targetUrl: String, destinationPath: String, username: String?, password: String?) {
        shouldFailWithException?.let { throw it }
    }

    override suspend fun head(targetUrl: String, username: String?, password: String?): Map<String, List<String>> {
        shouldFailWithException?.let { throw it }
        return emptyMap()
    }

    override suspend fun probeDeviceUuid(targetUrl: String): String? {
        return null
    }

    override suspend fun probeLatency(targetUrl: String): Long? {
        return null
    }
}

class TestFileSource(
    override val name: String = "test.txt",
    override val size: Long = 100L
) : LocalFileSource {
    override fun openStream(): InputStream = ByteArrayInputStream(ByteArray(100))
}

class WebDavRemoteFileRepositoryTest {

    @Test
    fun testFormatHostForUrl() {
        assertEquals("192.168.1.10", formatHostForUrl("192.168.1.10"))
        assertEquals("[2001:db8::1]", formatHostForUrl("2001:db8::1"))
        assertEquals("[2001:db8::1]", formatHostForUrl("[2001:db8::1]"))
        assertEquals("[fe80::1%25wlan0]", formatHostForUrl("fe80::1%wlan0"))
        assertEquals("[fe80::1%25wlan0]", formatHostForUrl("[fe80::1%25wlan0]"))
    }

    @Test
    fun testPermissionDeniedExceptionIsNotRetryable() {
        val permEx = WebDavException.PermissionDenied("/read-only-folder")
        assertFalse(permEx.isRetryable)
        assertEquals("Write access is disabled for this folder", permEx.message)
    }

    @Test
    fun testTimeoutExceptionIsRetryable() {
        val timeoutEx = WebDavException.Timeout("Request timed out")
        assertTrue(timeoutEx.isRetryable)
    }

    @Test
    fun testRepositoryUploadFilePropagatesPermissionDeniedFailure() = runBlocking {
        val fakeClient = FakeWebDavClient()
        fakeClient.shouldFailWithException = WebDavException.PermissionDenied("/folder")

        val repository = WebDavRemoteFileRepository(fakeClient)
        val fallback = RemoteTargetDevice(
            id = "device-1",
            name = "Test",
            hostAddress = "192.168.1.10",
            port = 8080,
            isHttps = false
        )
        val result = repository.uploadFile(
            deviceId = "device-1",
            parentPath = "/folder",
            localFileSource = TestFileSource(),
            fallbackDevice = fallback
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is WebDavException.PermissionDenied)
        assertFalse((ex as WebDavException).isRetryable)
    }

    @Test
    fun testListFilesReturnsDirectoryListingResult() = runBlocking {
        val fakeClient = FakeWebDavClient()
        val repository = WebDavRemoteFileRepository(fakeClient)
        val fallback = RemoteTargetDevice(
            id = "device-1",
            name = "Test",
            hostAddress = "192.168.1.10",
            port = 8080,
            isHttps = false
        )
        val result = repository.listFiles(
            deviceId = "device-1",
            path = "/test",
            fallbackDevice = fallback
        )

        assertTrue(result.isSuccess)
        val listing = result.getOrNull()
        kotlin.test.assertNotNull(listing)
        assertTrue(listing.isWriteAllowed)
    }

    @Test
    fun testIpv6HostFormattingInRepositoryUrl() = runBlocking {
        val fakeClient = FakeWebDavClient()
        val repository = WebDavRemoteFileRepository(fakeClient)
        val fallback = RemoteTargetDevice(
            id = "device-ipv6",
            name = "IPv6 Device",
            hostAddress = "fe80::1234:5678%wlan0",
            port = 8080,
            isHttps = false
        )

        repository.uploadFile(
            deviceId = "device-ipv6",
            parentPath = "/folder",
            localFileSource = TestFileSource("doc.pdf", 500L),
            fallbackDevice = fallback
        )

        assertEquals("http://[fe80::1234:5678%25wlan0]:8080/folder/doc.pdf", fakeClient.lastPutTargetUrl)

        val downloadUrl = repository.getDownloadUrl(
            deviceId = "device-ipv6",
            path = "/folder/doc.pdf",
            fallbackDevice = fallback
        )

        assertEquals("http://[fe80::1234:5678%25wlan0]:8080/folder/doc.pdf", downloadUrl)
    }

    @Test
    fun testDownloadFileDelegatesToWebDavClient() = runBlocking {
        val fakePlatformHandler = object : com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler {
            override fun getDefaultDownloadDirectory(): okio.Path = "/downloads".toPath()
            override fun getTemporaryDirectory(): okio.Path = "/temp".toPath()
            override fun getDownloadDestinationPath(fileName: String, customDirectory: okio.Path?): okio.Path =
                (customDirectory ?: getDefaultDownloadDirectory()) / fileName
            override fun openFile(downloadUrl: String, mimeType: String?, title: String?, fileSize: Long?, rawUrl: String?) {}
            override fun openLocalFile(filePath: okio.Path, mimeType: String?, title: String?) {}
            override fun shareFile(downloadUrl: String, title: String) {}
            override fun copyToClipboard(text: String) {}
        }

        val fakeClient = FakeWebDavClient()
        val repository = WebDavRemoteFileRepository(fakeClient, platformFileHandler = fakePlatformHandler)
        val fallback = RemoteTargetDevice(
            id = "device-dl",
            name = "DL Device",
            hostAddress = "192.168.1.50",
            port = 8080,
            isHttps = false
        )

        val result = repository.downloadFile(
            deviceId = "device-dl",
            path = "/media/video.mp4",
            fileName = "video.mp4",
            fallbackDevice = fallback
        )

        assertTrue(result.isSuccess)
        assertEquals("http://192.168.1.50:8080/media/video.mp4", fakeClient.lastDownloadTargetUrl)
        assertEquals("/downloads/video.mp4", fakeClient.lastDownloadDestinationPath?.toString())
    }

    @Test
    fun testDownloadFileWithCustomDestinationDirectory() = runBlocking {
        val fakePlatformHandler = object : com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler {
            override fun getDefaultDownloadDirectory(): okio.Path = "/downloads".toPath()
            override fun getTemporaryDirectory(): okio.Path = "/temp".toPath()
            override fun getDownloadDestinationPath(fileName: String, customDirectory: okio.Path?): okio.Path =
                (customDirectory ?: getDefaultDownloadDirectory()) / fileName
            override fun openFile(downloadUrl: String, mimeType: String?, title: String?, fileSize: Long?, rawUrl: String?) {}
            override fun openLocalFile(filePath: okio.Path, mimeType: String?, title: String?) {}
            override fun shareFile(downloadUrl: String, title: String) {}
            override fun copyToClipboard(text: String) {}
        }

        val fakeClient = FakeWebDavClient()
        val repository = WebDavRemoteFileRepository(fakeClient, platformFileHandler = fakePlatformHandler)
        val fallback = RemoteTargetDevice(
            id = "device-dl",
            name = "DL Device",
            hostAddress = "192.168.1.50",
            port = 8080,
            isHttps = false
        )

        val customDir = "/custom/folder".toPath()
        val result = repository.downloadFile(
            deviceId = "device-dl",
            path = "/media/video.mp4",
            fileName = "video.mp4",
            destinationDirectory = customDir,
            fallbackDevice = fallback
        )

        assertTrue(result.isSuccess)
        assertEquals("http://192.168.1.50:8080/media/video.mp4", fakeClient.lastDownloadTargetUrl)
        assertEquals("/custom/folder/video.mp4", fakeClient.lastDownloadDestinationPath?.toString())
    }

    @Test
    fun testDownloadFileUsesDynamicEndpointFromDeviceResolver() = runBlocking {
        val fakePlatformHandler = object : com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler {
            override fun getDefaultDownloadDirectory(): okio.Path = "/downloads".toPath()
            override fun getTemporaryDirectory(): okio.Path = "/temp".toPath()
            override fun getDownloadDestinationPath(fileName: String, customDirectory: okio.Path?): okio.Path =
                (customDirectory ?: getDefaultDownloadDirectory()) / fileName
            override fun openFile(downloadUrl: String, mimeType: String?, title: String?, fileSize: Long?, rawUrl: String?) {}
            override fun openLocalFile(filePath: okio.Path, mimeType: String?, title: String?) {}
            override fun shareFile(downloadUrl: String, title: String) {}
            override fun copyToClipboard(text: String) {}
        }

        var activeIp = "192.168.1.50"
        val mockResolver = object : com.hz_apps.foldershare.core.discovery.DeviceResolver {
            override fun resolveDevice(deviceId: String): com.hz_apps.foldershare.core.discovery.DiscoveredDevice? = null
            override fun resolveEndpoint(deviceId: String, fallback: RemoteTargetDevice?): RemoteTargetDevice? {
                return RemoteTargetDevice(
                    id = deviceId,
                    name = "Dynamic Device",
                    hostAddress = activeIp,
                    port = 8080,
                    isHttps = false
                )
            }
            override fun observeDevice(deviceId: String) = kotlinx.coroutines.flow.emptyFlow<com.hz_apps.foldershare.core.discovery.DiscoveredDevice?>()
            override fun observeDeviceEndpoint(deviceId: String) = kotlinx.coroutines.flow.emptyFlow<RemoteTargetDevice?>()
            override fun registerManualDevice(device: RemoteTargetDevice) {}
            override fun refresh() {}
        }

        val fakeClient = FakeWebDavClient()
        val repository = WebDavRemoteFileRepository(
            webDavClient = fakeClient,
            deviceResolver = mockResolver,
            platformFileHandler = fakePlatformHandler
        )

        // Switch to faster network
        activeIp = "192.168.49.1"

        val result = repository.downloadFile(
            deviceId = "dyn-dev",
            path = "/docs/file.pdf",
            fileName = "file.pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals("http://192.168.49.1:8080/docs/file.pdf", fakeClient.lastDownloadTargetUrl)
    }
}

