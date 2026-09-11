package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Parses an endpoint string formatted as "host:port", "[ipv6]:port", or "host"
 * into a (host, port) pair.
 */
fun parseEndpoint(endpoint: String, defaultPort: Int? = null): Pair<String, Int?> {
    val trimmed = endpoint.trim()
    if (trimmed.isEmpty()) return "" to defaultPort

    if (trimmed.startsWith("[")) {
        val closeBracket = trimmed.indexOf(']')
        if (closeBracket != -1) {
            val ip = trimmed.substring(1, closeBracket)
            val portStr = trimmed.substring(closeBracket + 1).removePrefix(":")
            val port = portStr.toIntOrNull() ?: defaultPort
            return ip to port
        }
    }

    val colonCount = trimmed.count { it == ':' }
    if (colonCount == 1) {
        val parts = trimmed.split(':')
        val ip = parts[0]
        val port = parts[1].toIntOrNull() ?: defaultPort
        return ip to port
    }

    return trimmed to defaultPort
}

/**
 * A simple, clean, minimalist text component that displays an IP address and Port.
 * Uses a distinct colon separator to cleanly divide the IP and Port without heavy badges or borders.
 */
@Composable
fun NetworkEndpointBadge(
    host: String,
    port: Int?,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = "$host:$port",
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun NetworkEndpointBadge(
    endpoint: String,
    modifier: Modifier = Modifier,
    defaultPort: Int? = null,
) {
    val (host, port) = parseEndpoint(endpoint, defaultPort)
    NetworkEndpointBadge(
        host = host,
        port = port,
        modifier = modifier
    )
}
