package com.hz_apps.foldershare.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkUtilsTest {

    @Test
    fun testIsUsableHostAddress_validIPv4() {
        assertTrue(isUsableHostAddress("192.168.1.50"))
        assertTrue(isUsableHostAddress("10.0.0.1"))
        assertTrue(isUsableHostAddress("172.16.0.100"))
    }

    @Test
    fun testIsUsableHostAddress_rejectsLoopbackAndInvalid() {
        assertFalse(isUsableHostAddress(null))
        assertFalse(isUsableHostAddress(""))
        assertFalse(isUsableHostAddress("   "))
        assertFalse(isUsableHostAddress("127.0.0.1"))
        assertFalse(isUsableHostAddress("127.0.1.1"))
        assertFalse(isUsableHostAddress("::1"))
        assertFalse(isUsableHostAddress("0.0.0.0"))
        assertFalse(isUsableHostAddress("::"))
        assertFalse(isUsableHostAddress("localhost"))
    }

    @Test
    fun testIsUsableHostAddress_rejectsIPv6LinkLocalAndScopeIds() {
        assertFalse(isUsableHostAddress("fe80::30ce:cd11:1503:1e20"))
        assertFalse(isUsableHostAddress("fe80:0:0:0:a476:d259:aece:f28"))
        assertFalse(isUsableHostAddress("fe80::30ce:cd11:1503:1e20%wlan0"))
        assertFalse(isUsableHostAddress("192.168.1.50%eth0"))
    }

    @Test
    fun testFormatHostForUrl() {
        assertEquals("192.168.1.50", formatHostForUrl("192.168.1.50"))
        assertEquals("[2001:db8::1]", formatHostForUrl("2001:db8::1"))
        assertEquals("[fe80::1%25wlan0]", formatHostForUrl("fe80::1%wlan0"))
    }
}
