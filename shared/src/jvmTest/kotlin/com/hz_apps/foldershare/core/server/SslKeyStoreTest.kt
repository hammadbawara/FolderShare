package com.hz_apps.foldershare.core.server

import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertNotNull

class SslKeyStoreTest {

    @Test
    fun testGetOrCreateKeyStoreGeneratesValidCertificate() {
        val config = SslKeyStoreHelper.getOrCreateKeyStore()
        assertNotNull(config)
        assertNotNull(config.keyStore)

        val cert = config.keyStore.getCertificate(config.keyAlias) as? X509Certificate
        assertNotNull(cert, "Certificate should be present in KeyStore")

        val publicKey = cert.publicKey
        assertNotNull(publicKey)

        // Verify certificate signature with its own public key
        cert.verify(publicKey)
        println("✅ Certificate self-verification succeeded: ${cert.subjectDN}")
    }
}
