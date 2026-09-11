package com.hz_apps.foldershare.feature.devices.components

import com.hz_apps.foldershare.ui.components.parseEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkEndpointBadgeTest {

    @Test
    fun testParseStandardIpv4WithPort() {
        val (host, port) = parseEndpoint("192.168.1.100:8080")
        assertEquals("192.168.1.100", host)
        assertEquals(8080, port)
    }

    @Test
    fun testParseIpv4WithoutPort() {
        val (host, port) = parseEndpoint("192.168.1.100", defaultPort = 8080)
        assertEquals("192.168.1.100", host)
        assertEquals(8080, port)
    }

    @Test
    fun testParseIpv6WithPort() {
        val (host, port) = parseEndpoint("[fe80::1]:9090")
        assertEquals("fe80::1", host)
        assertEquals(9090, port)
    }

    @Test
    fun testParseHostnameWithPort() {
        val (host, port) = parseEndpoint("my-laptop.local:5000")
        assertEquals("my-laptop.local", host)
        assertEquals(5000, port)
    }

    @Test
    fun testParseEmpty() {
        val (host, port) = parseEndpoint("")
        assertEquals("", host)
        assertNull(port)
    }
}
