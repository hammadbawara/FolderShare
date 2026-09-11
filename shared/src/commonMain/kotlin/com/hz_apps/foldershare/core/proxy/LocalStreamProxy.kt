package com.hz_apps.foldershare.core.proxy

import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.explorer.repository.createHttpClient
import com.hz_apps.foldershare.core.explorer.util.PathUtils
import com.hz_apps.foldershare.core.server.isClientDisconnect
import com.hz_apps.foldershare.core.server.serverEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodeURLPath
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local HTTP Stream Proxy.
 * Runs an embedded HTTP server on localhost (127.0.0.1) that proxies requests from external
 * media players (e.g., VLC, MX Player, Next Player) to remote WebDAV servers.
 *
 * Tracks active streaming connections to allow background services to perform auto-shutdown
 * when streaming activity finishes.
 */
class LocalStreamProxy(
    private val client: HttpClient = createHttpClient(),
    private val repository: RemoteFileRepository? = null
) {
    private val _activeStreamsCount = MutableStateFlow(0)
    val activeStreamsCount: StateFlow<Int> = _activeStreamsCount.asStateFlow()

    private val _isProxyRunning = MutableStateFlow(false)
    val isProxyRunning: StateFlow<Boolean> = _isProxyRunning.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null
    private var boundPort: Int = 0
    private val lock = Mutex()

    suspend fun ensureStarted(): Int = lock.withLock {
        if (server != null && boundPort > 0) return boundPort

        val defaultPort = 34858
        var currentPort = defaultPort

        fun createServer(port: Int): EmbeddedServer<*, *> {
            return embeddedServer(
                factory = serverEngineFactory,
                configure = {
                    connector {
                        host = "127.0.0.1"
                        this.port = port
                    }
                }
            ) {
                install(CORS) {
                    anyHost()
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Head)
                    allowHeader(HttpHeaders.Range)
                    allowHeader(HttpHeaders.Authorization)
                }

                routing {
                    val proxyHandler: suspend (ApplicationCall) -> Unit = { call ->
                        _activeStreamsCount.update { it + 1 }
                        try {
                            val deviceId = call.request.queryParameters["deviceId"]
                            val path = call.request.queryParameters["path"]

                            var targetUrl = if (!deviceId.isNullOrBlank() && !path.isNullOrBlank() && repository != null) {
                                repository.getDownloadUrl(deviceId, path)
                            } else ""

                            if (targetUrl.isBlank()) {
                                targetUrl = call.request.queryParameters["target"]
                                    ?: call.request.queryParameters["url"]
                                    ?: ""
                            }

                            if (targetUrl.isBlank()) {
                                call.respond(HttpStatusCode.BadRequest, "Missing target URL or deviceId/path")
                            } else {
                                val authParam = call.request.queryParameters["auth"]
                                val rangeHeader = call.request.header(HttpHeaders.Range)
                                val userAgentHeader = call.request.header(HttpHeaders.UserAgent)

                                suspend fun executeProxyRequest(urlToFetch: String) {
                                    val isHead = call.request.httpMethod == HttpMethod.Head
                                    val statement = if (isHead) {
                                        client.prepareHead(urlToFetch) {
                                            if (!rangeHeader.isNullOrBlank()) header(HttpHeaders.Range, rangeHeader)
                                            if (!authParam.isNullOrBlank()) header(HttpHeaders.Authorization, "Basic $authParam")
                                            if (!userAgentHeader.isNullOrBlank()) header(HttpHeaders.UserAgent, userAgentHeader)
                                        }
                                    } else {
                                        client.prepareGet(urlToFetch) {
                                            if (!rangeHeader.isNullOrBlank()) header(HttpHeaders.Range, rangeHeader)
                                            if (!authParam.isNullOrBlank()) header(HttpHeaders.Authorization, "Basic $authParam")
                                            if (!userAgentHeader.isNullOrBlank()) header(HttpHeaders.UserAgent, userAgentHeader)
                                        }
                                    }

                                    statement.execute { response ->
                                        val status = response.status
                                        val contentType = response.contentType()
                                        val contentLength = response.contentLength()
                                        val contentRange = response.headers[HttpHeaders.ContentRange]
                                        val acceptRanges = response.headers[HttpHeaders.AcceptRanges]
                                        val lastModified = response.headers[HttpHeaders.LastModified]

                                        call.response.status(status)
                                        if (contentType != null) call.response.header(HttpHeaders.ContentType, contentType.toString())
                                        if (contentLength != null && contentLength > 0) call.response.header(HttpHeaders.ContentLength, contentLength.toString())
                                        if (!contentRange.isNullOrBlank()) call.response.header(HttpHeaders.ContentRange, contentRange)
                                        call.response.header(HttpHeaders.AcceptRanges, acceptRanges ?: "bytes")
                                        if (!lastModified.isNullOrBlank()) call.response.header(HttpHeaders.LastModified, lastModified)

                                        if (!isHead && (status.isSuccess() || status == HttpStatusCode.PartialContent)) {
                                            val channel = response.bodyAsChannel()
                                            call.respondBytesWriter(status = status) {
                                                try {
                                                    channel.copyTo(this)
                                                } catch (e: Exception) {
                                                    if (!isClientDisconnect(e)) {
                                                        throw e
                                                    }
                                                }
                                            }
                                        } else {
                                            call.respond(status)
                                        }
                                    }
                                }

                                try {
                                    executeProxyRequest(targetUrl)
                                } catch (t: Throwable) {
                                    var refreshedSuccess = false
                                    if (!deviceId.isNullOrBlank() && !path.isNullOrBlank() && repository != null && !isClientDisconnect(t)) {
                                        val refreshedUrl = runCatching { repository.getDownloadUrl(deviceId, path) }.getOrNull()
                                        if (!refreshedUrl.isNullOrBlank()) {
                                            try {
                                                executeProxyRequest(refreshedUrl)
                                                refreshedSuccess = true
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    if (!refreshedSuccess && !isClientDisconnect(t)) {
                                        call.respond(HttpStatusCode.BadGateway, t.message ?: "Proxy error")
                                    }
                                }
                            }
                        } finally {
                            _activeStreamsCount.update { (it - 1).coerceAtLeast(0) }
                        }
                    }

                    get("/stream/{fileName...}") { proxyHandler(call) }
                    head("/stream/{fileName...}") { proxyHandler(call) }
                    get("/proxy") { proxyHandler(call) }
                    head("/proxy") { proxyHandler(call) }
                }
            }
        }

        try {
            val s = createServer(currentPort)
            s.start(wait = false)
            server = s
            boundPort = currentPort
            _isProxyRunning.value = true
            return currentPort
        } catch (e: Exception) {
            // Fallback to port 0 (dynamic open port)
            try {
                val s = createServer(0)
                s.start(wait = false)
                server = s
                boundPort = defaultPort
                _isProxyRunning.value = true
                return boundPort
            } catch (fallbackEx: Exception) {
                _isProxyRunning.value = false
                throw fallbackEx
            }
        }
    }

    suspend fun getStreamableUrl(
        downloadUrl: String,
        fileName: String,
        deviceId: String? = null,
        path: String? = null
    ): String {
        val port = ensureStarted()
        val cleanFileName = PathUtils.normalizePath(fileName).removePrefix("/")
        val encodedFileName = cleanFileName.encodeURLPath()
        val queryParams = mutableListOf<String>()
        if (!deviceId.isNullOrBlank()) {
            queryParams.add("deviceId=${deviceId.encodeURLQueryComponent()}")
        }
        if (!path.isNullOrBlank()) {
            queryParams.add("path=${path.encodeURLQueryComponent()}")
        }
        if (downloadUrl.isNotBlank()) {
            queryParams.add("target=${downloadUrl.encodeURLQueryComponent()}")
        }
        val queryString = queryParams.joinToString("&")
        return "http://127.0.0.1:$port/stream/$encodedFileName?$queryString"
    }

    fun stop() {
        try {
            server?.stop(1000, 2000)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            server = null
            boundPort = 0
            _activeStreamsCount.value = 0
            _isProxyRunning.value = false
        }
    }
}
