package com.hz_apps.foldershare.core.explorer

import com.hz_apps.foldershare.core.explorer.client.KtorWebDavClient
import com.hz_apps.foldershare.core.explorer.model.WebDavException
import com.hz_apps.foldershare.core.explorer.repository.createHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorWebDavClientErrorTest {

    private inline fun withLocalHttpResponse(
        statusLine: String,
        body: String = "",
        crossinline block: suspend (baseUrl: String) -> Unit
    ) = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val acceptJob = launch(Dispatchers.IO) {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            line = reader.readLine()
                        }
                        val bytes = body.toByteArray()
                        val response = buildString {
                            append("HTTP/1.1 $statusLine\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Content-Type: text/plain; charset=utf-8\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                        }.toByteArray() + bytes
                        s.getOutputStream().write(response)
                        s.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        try {
            block("http://127.0.0.1:$port")
        } finally {
            server.close()
            acceptJob.cancel()
        }
    }

    @Test
    fun testHttpErrorStatusMapsToWebDavExceptionWithoutUrl() {
        val httpClient = createHttpClient()
        val davClient = KtorWebDavClient(httpClient)

        try {
            withLocalHttpResponse("403 Forbidden", "Read access is disabled for this folder") { baseUrl ->
                val ex = kotlin.runCatching { davClient.propfind("$baseUrl/Secret", null, null) }.exceptionOrNull()
                assertTrue(ex is WebDavException.PermissionDenied)
                assertTrue(ex.isReadOperation)
                assertFalse(ex.message.contains("http://"))
            }

            withLocalHttpResponse("405 Method Not Allowed", "Resource already exists") { baseUrl ->
                val ex = kotlin.runCatching { davClient.mkcol("$baseUrl/ExistingFolder", null, null) }.exceptionOrNull()
                assertTrue(ex is WebDavException.MethodNotAllowed)
                assertEquals("Resource already exists", ex.serverReason)
                assertFalse(ex.message.contains("http://"))
            }

            withLocalHttpResponse("401 Unauthorized", "Authentication required") { baseUrl ->
                val ex = kotlin.runCatching { davClient.propfind("$baseUrl/", null, null) }.exceptionOrNull()
                assertTrue(ex is WebDavException.Unauthorized)
            }
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun testConnectionRefusedMapsToNetworkErrorWithoutUrl() = runBlocking {
        val httpClient = createHttpClient()
        val davClient = KtorWebDavClient(httpClient)

        val closedSocket = ServerSocket(0)
        val unusedPort = closedSocket.localPort
        closedSocket.close()

        try {
            val ex = kotlin.runCatching {
                davClient.propfind("http://127.0.0.1:$unusedPort/test", null, null)
            }.exceptionOrNull()

            assertTrue(ex is WebDavException.NetworkError)
            assertFalse(ex.message.contains("http://"))
            assertFalse(ex.message.contains("Exception"))
        } finally {
            httpClient.close()
        }
    }
}
