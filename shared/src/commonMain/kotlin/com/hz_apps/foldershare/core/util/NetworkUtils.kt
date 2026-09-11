package com.hz_apps.foldershare.core.util

/**
 * Returns the primary local IPv4 address of the device on the network (e.g., "192.168.1.5"),
 * or null if offline / disconnected.
 */
expect fun getLocalIpAddress(): String?

/**
 * Returns all usable local IPv4 addresses of the device on the network.
 */
expect fun getAllLocalIpAddresses(): List<String>

/**
 * Determines whether a host address string is a valid, usable endpoint for peer-to-peer sharing.
 * Rejects empty/blank strings, loopback addresses ("127.x.x.x", "::1", "localhost"),
 * wildcard/unroutable addresses ("0.0.0.0", "::"), addresses containing scope IDs ('%'),
 * and IPv6 link-local addresses ("fe80:...").
 */
fun isUsableHostAddress(hostAddress: String?): Boolean {
    if (hostAddress.isNullOrBlank()) return false
    val host = hostAddress.trim()
    if (host == "0.0.0.0" || host == "::" || host.equals("localhost", ignoreCase = true)) return false
    if (host.contains("%")) return false
    if (host.startsWith("127.") || host == "::1") return false
    if (host.startsWith("fe80", ignoreCase = true)) return false
    return true
}

/**
 * Formats a host address string for use in HTTP/WebDAV URLs.
 * If [hostAddress] is an IPv6 address (contains ':'), it ensures it is properly enclosed in square brackets '[...]',
 * and that any scope ID / zone index '%' is properly percent-encoded as '%25' according to RFC 6874.
 */
fun formatHostForUrl(hostAddress: String): String {
    if (!hostAddress.contains(":")) return hostAddress
    if (hostAddress.startsWith("[") && hostAddress.endsWith("]")) return hostAddress
    val formatted = if (hostAddress.contains("%") && !hostAddress.contains("%25")) {
        hostAddress.replace("%", "%25")
    } else {
        hostAddress
    }
    return "[$formatted]"
}


