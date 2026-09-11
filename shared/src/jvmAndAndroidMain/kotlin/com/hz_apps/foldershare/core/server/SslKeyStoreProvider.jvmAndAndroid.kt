package com.hz_apps.foldershare.core.server

import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.sslConnector
import java.io.File
import java.security.KeyStore

data class KeyStoreConfig(
    val keyStore: KeyStore,
    val keyAlias: String = "foldershare",
    val keyPassword: String = "foldershare_pass",
    val keyStorePassword: String = "foldershare_pass"
)

actual fun ApplicationEngine.Configuration.configureSsl(port: Int) {
    val ksConfig = SslKeyStoreHelper.getOrCreateKeyStore()
    sslConnector(
        keyStore = ksConfig.keyStore,
        keyAlias = ksConfig.keyAlias,
        keyStorePassword = { ksConfig.keyStorePassword.toCharArray() },
        privateKeyPassword = { ksConfig.keyPassword.toCharArray() }
    ) {
        this.port = port
    }
}

object SslKeyStoreHelper {
    private var cachedConfig: KeyStoreConfig? = null

    @Synchronized
    fun getOrCreateKeyStore(): KeyStoreConfig {
        cachedConfig?.let { return it }

        val alias = "foldershare"
        val password = "foldershare_pass"

        val tempFile = File.createTempFile("foldershare_ks", ".p12")
        tempFile.deleteOnExit()

        val keyStore = generateCertificate(
            file = tempFile,
            keyAlias = alias,
            keyPassword = password,
            jksPassword = password
        )

        val config = KeyStoreConfig(
            keyStore = keyStore,
            keyAlias = alias,
            keyPassword = password,
            keyStorePassword = password
        )
        cachedConfig = config
        return config
    }
}
