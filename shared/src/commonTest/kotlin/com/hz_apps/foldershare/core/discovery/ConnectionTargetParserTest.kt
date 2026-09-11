package com.hz_apps.foldershare.core.discovery

import com.hz_apps.foldershare.core.server.ServerConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionTargetParserTest {

    @Test
    fun testParsePlainIp() {
        val parsed = ConnectionTargetParser.parse("192.168.1.50")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertNull(parsed.explicitPort)
        assertNull(parsed.explicitHttps)
        assertNull(parsed.username)
        assertNull(parsed.password)
    }

    @Test
    fun testParseHostWithPort() {
        val parsed = ConnectionTargetParser.parse("192.168.1.50:9090")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(9090, parsed.explicitPort)
        assertNull(parsed.explicitHttps)
    }

    @Test
    fun testParseHttpUrlWithPortAndPath() {
        val parsed = ConnectionTargetParser.parse("http://192.168.1.50:8080/webdav/share")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(8080, parsed.explicitPort)
        assertEquals(false, parsed.explicitHttps)
        assertEquals("/webdav/share", parsed.path)
    }

    @Test
    fun testParseHttpsUrl() {
        val parsed = ConnectionTargetParser.parse("https://my-desktop.local:8443")
        assertNotNull(parsed)
        assertEquals("my-desktop.local", parsed.host)
        assertEquals(8443, parsed.explicitPort)
        assertEquals(true, parsed.explicitHttps)
    }

    @Test
    fun testParseUrlWithEmbeddedCredentials() {
        val parsed = ConnectionTargetParser.parse("http://admin:secret123@192.168.1.50:8080/webdav")
        assertNotNull(parsed)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(8080, parsed.explicitPort)
        assertEquals(false, parsed.explicitHttps)
        assertEquals("admin", parsed.username)
        assertEquals("secret123", parsed.password)
        assertEquals("/webdav", parsed.path)
    }

    @Test
    fun testParseFolderShareAndWebDavSchemes() {
        val folderShare = ConnectionTargetParser.parse("foldershare:192.168.1.50:8080")
        assertNotNull(folderShare)
        assertEquals("192.168.1.50", folderShare.host)
        assertEquals(8080, folderShare.explicitPort)
        assertEquals(false, folderShare.explicitHttps)

        val webDavs = ConnectionTargetParser.parse("webdavs://192.168.1.50:8443")
        assertNotNull(webDavs)
        assertEquals("192.168.1.50", webDavs.host)
        assertEquals(8443, webDavs.explicitPort)
        assertEquals(true, webDavs.explicitHttps)
    }

    @Test
    fun testParseIPv6() {
        val bracketedWithPort = ConnectionTargetParser.parse("[::1]:8080")
        assertNotNull(bracketedWithPort)
        assertEquals("::1", bracketedWithPort.host)
        assertEquals(8080, bracketedWithPort.explicitPort)

        val unbracketed = ConnectionTargetParser.parse("fe80::1")
        assertNotNull(unbracketed)
        assertEquals("fe80::1", unbracketed.host)
        assertNull(unbracketed.explicitPort)
    }

    @Test
    fun testSanitizationOfMessyInputs() {
        val messy = ConnectionTargetParser.parse("  \"<http://192.168.1.50:8080/path?foo=bar#section>\"  ")
        assertNotNull(messy)
        assertEquals("192.168.1.50", messy.host)
        assertEquals(8080, messy.explicitPort)
        assertEquals(false, messy.explicitHttps)
    }

    @Test
    fun testCandidateGenerationPrefersDiscoveredDevice() {
        val parsed = ConnectionTargetParser.parse("192.168.1.50")!!
        val discovered = listOf(
            DiscoveredDevice(
                id = "dev1",
                name = "Discovered",
                hostAddress = "192.168.1.50",
                port = 34857,
                osDetails = "Linux",
                httpUrl = "http://192.168.1.50:34857",
                webDavUrl = "http://192.168.1.50:34857/",
                isHttps = false
            )
        )
        val candidates = ConnectionTargetParser.generateCandidates(parsed, discovered)
        assertNotNull(candidates)
        assertEquals(CandidateEndpoint(34857, false), candidates.first())
    }

    @Test
    fun testCandidateGenerationAllPorts() {
        val parsed = ConnectionTargetParser.parse("192.168.1.50")!!
        val candidates = ConnectionTargetParser.generateCandidates(parsed)
        val expectedSize = ServerConstants.ALL_APP_PORTS.size * 2
        assertEquals(expectedSize, candidates.size)
        assertEquals(CandidateEndpoint(ServerConstants.DEFAULT_PORT, false), candidates[0])
        assertEquals(CandidateEndpoint(ServerConstants.DEFAULT_PORT, true), candidates[1])
    }
}
