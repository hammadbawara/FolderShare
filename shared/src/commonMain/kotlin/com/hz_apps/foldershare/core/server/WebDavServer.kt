package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.VirtualFileSystem
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri

class WebDavServer(private val fileSystem: VirtualFileSystem) {
    private var server: EmbeddedServer<*, *>? = null

    fun start(config: WebDavServerConfig = WebDavServerConfig()) {
        if (server != null) return

        server = embeddedServer(
            factory = serverEngineFactory,
            configure = {
                if (config.isHttpsEnabled) {
                    configureSsl(config.port)
                } else {
                    connector {
                        this.port = config.port
                    }
                }
            }
        ) {
            intercept(ApplicationCallPipeline.Setup) {
                val method = call.request.httpMethod.value
                val uri = call.request.uri
                val clientIp = call.request.local.remoteHost
                println("➡️ [SERVER REQ] $method $uri (Client: $clientIp)")

                try {
                    proceed()
                } catch (t: Throwable) {
                    if (!isClientDisconnect(t)) {
                        println("❌ [SERVER ERROR] Processing $method $uri failed: ${t.message}")
                        t.printStackTrace()
                    }
                    throw t
                } finally {
                    println("⬅️ [SERVER RES] $method $uri -> Status: ${call.response.status()}")
                }
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Head)
                allowMethod(HttpMethod("PROPFIND"))
                allowMethod(HttpMethod("MKCOL"))
                allowMethod(HttpMethod("PROPPATCH"))
                allowMethod(HttpMethod("COPY"))
                allowMethod(HttpMethod("MOVE"))
                allowHeader("DAV")
                allowHeader("Depth")
                allowHeader("Destination")
                allowHeader("Overwrite")
                allowHeader("Content-Type")
                allowHeader("Range")
                allowHeader("Authorization")
                allowHeader("X-Device-UUID")
                exposeHeader("X-Device-UUID")
            }

            webDavRouting(
                fileSystem = fileSystem,
                authConfig = config.authConfig,
                deviceUuid = config.deviceUuid
            )
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 5000)
        server = null
    }
}
