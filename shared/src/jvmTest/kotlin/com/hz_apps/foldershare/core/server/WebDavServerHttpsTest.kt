package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.explorer.repository.createHttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WebDavServerHttpsTest {

    @Test
    fun testWebDavServerWithHttps() = runBlocking {
        val rootDir = FakeVirtualFile("Root", "/", isDirectory = true)
        val fileSystem = object : com.hz_apps.foldershare.core.VirtualFileSystem {
            override suspend fun resolve(path: String): com.hz_apps.foldershare.core.models.VirtualFile? = rootDir
        }

        val serverPort = 8443
        val server = WebDavServer(fileSystem)
        val config = WebDavServerConfig(
            port = serverPort,
            isHttpsEnabled = true
        )

        try {
            server.start(config)
            println("Server started on https://127.0.0.1:$serverPort")

            // Wait brief moment for Netty to bind
            kotlinx.coroutines.delay(500.milliseconds)

            val client = createHttpClient()
            val response = client.get("https://127.0.0.1:$serverPort/")
            println("HTTPS Response status: ${response.status}")
            assertTrue(response.status.value in 200..499, "Response status should be valid HTTP status code")
            client.close()
        } finally {
            server.stop()
        }
    }
}
