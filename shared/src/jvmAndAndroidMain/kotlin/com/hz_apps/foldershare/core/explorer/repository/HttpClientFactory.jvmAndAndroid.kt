package com.hz_apps.foldershare.core.explorer.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

actual fun createHttpClient(): HttpClient {
    val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
    }

    return HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = null // Disable request timeout for long uploads
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 60_000L
        }
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS) // Disable write timeout for large streaming uploads
                sslSocketFactory(sslContext.socketFactory, trustAllManager)
                hostnameVerifier { _, _ -> true }
            }
        }
    }
}
