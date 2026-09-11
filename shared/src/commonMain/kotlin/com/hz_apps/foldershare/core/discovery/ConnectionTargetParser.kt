package com.hz_apps.foldershare.core.discovery

import com.hz_apps.foldershare.core.server.ServerConstants

/**
 * Parsed representation of a user-entered connection target or URL.
 */
data class ParsedConnectionTarget(
    val host: String,
    val explicitPort: Int? = null,
    val explicitHttps: Boolean? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
)

/**
 * Candidate endpoint (port and HTTPS mode) for probing network reachability.
 */
data class CandidateEndpoint(
    val port: Int,
    val isHttps: Boolean
)

/**
 * Pure parser and candidate generator adhering to the Single Responsibility Principle.
 * Robustly parses and normalizes diverse user inputs (raw IPs, host:port, IPv6, URLs, userinfo credentials).
 */
object ConnectionTargetParser {

    fun parse(input: String): ParsedConnectionTarget? {
        var cleaned = input
            .replace("\uFEFF", "") // Byte Order Mark
            .replace("\u200B", "") // Zero-width space
            .replace("\u200C", "") // Zero-width non-joiner
            .replace("\u200D", "") // Zero-width joiner
            .replace("\u00A0", " ") // Non-breaking space
            .trim()

        // Strip surrounding quotes or enclosing brackets if user copied from markdown/chat/docs
        while (
            (cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'")) ||
            (cleaned.startsWith("`") && cleaned.endsWith("`")) ||
            (cleaned.startsWith("<") && cleaned.endsWith(">")) ||
            (cleaned.startsWith("(") && cleaned.endsWith(")"))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }

        if (cleaned.isEmpty()) return null

        var explicitHttps: Boolean? = null

        // Match scheme/protocol (e.g., http://, https://, http:, https:, webdav://, webdav:, ws://, ftp://, etc.)
        val knownSchemeRegex = Regex(
            "^(https?|webdavs?|foldershares?|ftps?|wss?)(?::/{1,3}|:)(.*)$",
            RegexOption.IGNORE_CASE
        )
        val genericSchemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):/{2,3}(.*)$", RegexOption.IGNORE_CASE)

        val knownMatch = knownSchemeRegex.matchEntire(cleaned)
        if (knownMatch != null) {
            val scheme = knownMatch.groupValues[1].lowercase()
            cleaned = knownMatch.groupValues[2].trim()
            when (scheme) {
                "https", "webdavs", "wss", "ftps", "foldershares" -> explicitHttps = true
                "http", "webdav", "ws", "ftp", "foldershare" -> explicitHttps = false
            }
        } else {
            val genericMatch = genericSchemeRegex.matchEntire(cleaned)
            if (genericMatch != null) {
                val scheme = genericMatch.groupValues[1].lowercase()
                cleaned = genericMatch.groupValues[2].trim()
                if (scheme.endsWith("s")) {
                    explicitHttps = true
                }
            }
        }

        // Strip leading slashes if any (e.g. "//192.168.1.5:8080" or leftover from protocol relative URLs)
        cleaned = cleaned.trimStart('/')

        // Extract path if present (e.g. /webdav/share?token=1#title)
        var path: String? = null
        val pathStartIndex = cleaned.indexOf('/')
        val queryOrFragIndex = cleaned.indexOfAny(charArrayOf('?', '#'))

        if (pathStartIndex != -1) {
            val pathEndIndex = if (queryOrFragIndex != -1 && queryOrFragIndex > pathStartIndex) queryOrFragIndex else cleaned.length
            val extractedPath = cleaned.substring(pathStartIndex, pathEndIndex).trim()
            if (extractedPath.isNotEmpty() && extractedPath != "/") {
                path = extractedPath
            }
        }

        // Strip trailing path, query parameters, or hash fragments from host/authority
        val delimiterIndex = cleaned.indexOfAny(charArrayOf('/', '?', '#'))
        if (delimiterIndex != -1) {
            cleaned = cleaned.substring(0, delimiterIndex).trim()
        }

        if (cleaned.isEmpty()) return null

        // Parse and strip userinfo if present in URL (e.g. user:pass@192.168.1.5:8080 or admin@192.168.1.5)
        var username: String? = null
        var password: String? = null
        val atIndex = cleaned.lastIndexOf('@')
        if (atIndex != -1) {
            val userInfo = cleaned.substring(0, atIndex).trim()
            cleaned = cleaned.substring(atIndex + 1).trim()
            if (userInfo.isNotEmpty()) {
                val colonIdx = userInfo.indexOf(':')
                if (colonIdx != -1) {
                    username = userInfo.substring(0, colonIdx).trim().takeIf { it.isNotEmpty() }
                    password = userInfo.substring(colonIdx + 1).takeIf { it.isNotEmpty() }
                } else {
                    username = userInfo.takeIf { it.isNotEmpty() }
                }
            }
        }

        if (cleaned.isEmpty()) return null

        var host: String
        var explicitPort: Int? = null

        if (cleaned.startsWith("[")) {
            // IPv6 bracketed format: [::1] or [fe80::1]:8080 or [fe80::1] : 8080
            val closingBracket = cleaned.indexOf(']')
            if (closingBracket != -1) {
                host = cleaned.substring(1, closingBracket).trim()
                val remaining = cleaned.substring(closingBracket + 1).trim()
                if (remaining.startsWith(":")) {
                    val portPart = remaining.substring(1).trim()
                    if (portPart.isNotEmpty()) {
                        val portCandidate = portPart.toIntOrNull()
                        if (portCandidate != null && portCandidate in 1..65535) {
                            explicitPort = portCandidate
                        } else {
                            return null // Invalid port specified
                        }
                    }
                }
            } else {
                host = cleaned.removePrefix("[").removeSuffix("]").trim()
            }
        } else {
            // Check for IPv4 or hostname with possible port
            val colonCount = cleaned.count { it == ':' }
            if (colonCount > 1) {
                // Unbracketed IPv6 address e.g. fe80::1, 2001:db8::1
                host = cleaned.trim()
            } else if (colonCount == 1) {
                // Host:Port format e.g. 192.168.1.50:8080, 192.168.1.50 : 8080, or 192.168.1.50:
                val hostPart = cleaned.substringBefore(':').trim()
                val portPart = cleaned.substringAfter(':').trim()
                host = hostPart
                if (portPart.isNotEmpty()) {
                    val portCandidate = portPart.toIntOrNull()
                    if (portCandidate != null && portCandidate in 1..65535) {
                        explicitPort = portCandidate
                    } else {
                        return null // Invalid port specified
                    }
                }
            } else {
                host = cleaned.trim()
            }
        }

        // Clean up host
        host = host.trim().trimEnd('.')
        host = host.removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .removePrefix("<").removeSuffix(">")
            .removePrefix("[").removeSuffix("]")
            .trim()

        if (host.isEmpty()) return null

        return ParsedConnectionTarget(
            host = host,
            explicitPort = explicitPort,
            explicitHttps = explicitHttps,
            username = username,
            password = password,
            path = path
        )
    }

    fun generateCandidates(
        target: ParsedConnectionTarget,
        discoveredDevices: List<DiscoveredDevice> = emptyList()
    ): List<CandidateEndpoint> {
        val candidates = mutableListOf<CandidateEndpoint>()

        val discoveredMatch = discoveredDevices.firstOrNull { it.hostAddress.equals(target.host, ignoreCase = true) }
        if (discoveredMatch != null) {
            candidates.add(CandidateEndpoint(discoveredMatch.port, discoveredMatch.isHttps))
        }

        if (target.explicitPort != null) {
            when (target.explicitHttps) {
                true -> {
                    candidates.add(CandidateEndpoint(target.explicitPort, true))
                    candidates.add(CandidateEndpoint(target.explicitPort, false))
                }
                false -> {
                    candidates.add(CandidateEndpoint(target.explicitPort, false))
                    candidates.add(CandidateEndpoint(target.explicitPort, true))
                }
                null -> {
                    candidates.add(CandidateEndpoint(target.explicitPort, true))
                    candidates.add(CandidateEndpoint(target.explicitPort, false))
                }
            }
        } else {
            for (port in ServerConstants.ALL_APP_PORTS) {
                when (target.explicitHttps) {
                    true -> {
                        candidates.add(CandidateEndpoint(port, true))
                        candidates.add(CandidateEndpoint(port, false))
                    }
                    false -> {
                        candidates.add(CandidateEndpoint(port, false))
                        candidates.add(CandidateEndpoint(port, true))
                    }
                    null -> {
                        candidates.add(CandidateEndpoint(port, false))
                        candidates.add(CandidateEndpoint(port, true))
                    }
                }
            }
        }

        return candidates.distinct()
    }
}
