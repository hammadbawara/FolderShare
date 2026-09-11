package com.hz_apps.foldershare.core.explorer.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.core.explorer.repository.DirectoryListingResult
import com.hz_apps.foldershare.core.explorer.util.parseHttpDateToTimestamp

/**
 * Pure Kotlin Multiplatform WebDAV XML PROPFIND parser powered by KSoup.
 */
object WebDavXmlParser {

    fun parsePropfindResponse(xmlContent: String, requestPath: String): DirectoryListingResult {
        val resultList = mutableListOf<RemoteFile>()
        val normalizedRequestPath = normalizePath(requestPath)
        var targetFolderWriteAllowed = true

        try {
            val doc = Ksoup.parse(html = xmlContent, baseUri = "", parser = Parser.xmlParser())
            val responseElements = doc.findDescendants("response")

            for (responseEl in responseElements) {
                val hrefEl = responseEl.findFirstDescendant("href") ?: continue
                val rawHref = hrefEl.text().trim()
                val decodedHref = urlDecode(rawHref)
                val normalizedItemPath = normalizePath(decodedHref)

                // Inspect requested directory itself
                if (normalizedItemPath == normalizedRequestPath || normalizedItemPath.isEmpty()) {
                    targetFolderWriteAllowed = responseEl.isWriteAllowed()
                    continue
                }

                // Ensure direct child
                val parentPathOfItem = getParentPath(normalizedItemPath)
                if (parentPathOfItem != normalizedRequestPath) {
                    continue
                }

                // Directory check (<collection>)
                val isDirectory = responseEl.findFirstDescendant("collection") != null || rawHref.trim().endsWith("/")

                // Content length
                val size = responseEl.findFirstDescendant("getcontentlength")?.text()?.trim()?.toLongOrNull() ?: 0L

                // Last modified
                val lastModified = responseEl.findFirstDescendant("getlastmodified")?.text()?.trim().orEmpty()
                val lastModifiedTimestamp = parseHttpDateToTimestamp(lastModified)

                val isWriteAllowed = responseEl.isWriteAllowed()

                val name = normalizedItemPath.substringAfterLast('/').ifEmpty { normalizedItemPath }

                resultList.add(
                    RemoteFile(
                        name = name,
                        path = normalizedItemPath,
                        isDirectory = isDirectory,
                        size = size,
                        lastModifiedTimestamp = lastModifiedTimestamp,
                        isWriteAllowed = isWriteAllowed
                    )
                )
            }
        } catch (_: Exception) {
            // Handle parsing failure gracefully
        }

        val sortedFiles = resultList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        return DirectoryListingResult(isWriteAllowed = targetFolderWriteAllowed, files = sortedFiles)
    }

    private fun Element.isWriteAllowed(): Boolean {
        val isWriteAllowedEl = findFirstDescendant("iswriteallowed")
        if (isWriteAllowedEl != null) {
            val text = isWriteAllowedEl.text().trim().lowercase()
            return text == "true" || text == "1"
        }
        val isReadOnlyEl = findFirstDescendant("isreadonly")
        if (isReadOnlyEl != null) {
            val text = isReadOnlyEl.text().trim().lowercase()
            return text == "false" || text == "0"
        }
        val privilegeSetEl = findFirstDescendant("current-user-privilege-set")
        if (privilegeSetEl != null) {
            return privilegeSetEl.findFirstDescendant("write") != null
        }
        return true
    }

    private fun Element.findDescendants(localName: String): List<Element> {
        val target = localName.lowercase()
        return this.getAllElements().filter { el ->
            val name = el.tagName().lowercase()
            name == target || name.endsWith(":$target")
        }
    }

    private fun Element.findFirstDescendant(localName: String): Element? {
        val target = localName.lowercase()
        return this.getAllElements().firstOrNull { el ->
            val name = el.tagName().lowercase()
            name == target || name.endsWith(":$target")
        }
    }

    private fun normalizePath(path: String): String {
        var p = path.replace("\\", "/").trim()
        if (!p.startsWith("/")) p = "/$p"
        while (p.endsWith("/") && p.length > 1) {
            p = p.dropLast(1)
        }
        return p
    }

    private fun getParentPath(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        if (lastSlash <= 0) return "/"
        return path.substring(0, lastSlash)
    }

    private fun urlDecode(url: String): String {
        return try {
            val bytes = ByteArray(url.length * 4)
            var byteCount = 0
            var i = 0
            val len = url.length
            while (i < len) {
                val c = url[i]
                if (c == '%') {
                    if (i + 2 < len) {
                        val hex = url.substring(i + 1, i + 3)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            bytes[byteCount++] = code.toByte()
                            i += 3
                            continue
                        }
                    }
                } else if (c == '+') {
                    bytes[byteCount++] = ' '.code.toByte()
                    i++
                    continue
                }
                val charBytes = c.toString().encodeToByteArray()
                for (b in charBytes) {
                    bytes[byteCount++] = b
                }
                i++
            }
            bytes.copyOf(byteCount).decodeToString()
        } catch (_: Exception) {
            url
        }
    }
}
