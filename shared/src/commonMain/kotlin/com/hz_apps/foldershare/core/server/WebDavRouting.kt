package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.VirtualFileSystem
import com.hz_apps.foldershare.core.models.VirtualFile
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodeURLPath
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.method
import io.ktor.server.routing.options
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo

val HttpMethodPropfind = HttpMethod("PROPFIND")
val HttpMethodMkcol = HttpMethod("MKCOL")
val HttpMethodMove = HttpMethod("MOVE")

/**
 * Platform-specific date formatter for WebDAV standard dates.
 */
expect fun formatHttpDate(timestamp: Long): String

/**
 * Data class representing a parsed HTTP byte range.
 */
data class ByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

/**
 * RFC 7233 / RFC 9110 compliant Range header parser.
 * Handles single ranges ("bytes=100-200"), open-ended ranges ("bytes=100-"), and suffix ranges ("bytes=-500").
 */
fun parseByteRange(rangeHeader: String, fileLength: Long): ByteRange? {
    if (fileLength <= 0L) return null
    if (!rangeHeader.startsWith("bytes=", ignoreCase = true)) return null

    val rangeValue = rangeHeader.removePrefix("bytes=").substringBefore(",").trim()
    if (rangeValue.isEmpty()) return null

    return if (rangeValue.startsWith("-")) {
        // Suffix byte range: e.g. "bytes=-500" requests the last 500 bytes of the file
        val suffixLength = rangeValue.removePrefix("-").toLongOrNull() ?: return null
        if (suffixLength <= 0) return null
        val start = (fileLength - suffixLength).coerceAtLeast(0L)
        val end = fileLength - 1
        ByteRange(start, end)
    } else {
        val parts = rangeValue.split("-", limit = 2)
        val start = parts[0].toLongOrNull() ?: return null
        val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
            parts[1].toLongOrNull() ?: (fileLength - 1)
        } else {
            fileLength - 1
        }
        val clampedEnd = end.coerceAtMost(fileLength - 1)
        if (start > clampedEnd || start >= fileLength) null else ByteRange(start, clampedEnd)
    }
}

/**
 * Resolves the appropriate ContentType for a given file name.
 */
fun resolveMimeType(fileName: String): ContentType {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4" -> ContentType("video", "mp4")
        "mkv" -> ContentType("video", "x-matroska")
        "avi" -> ContentType("video", "x-msvideo")
        "mov" -> ContentType("video", "quicktime")
        "webm" -> ContentType("video", "webm")
        "flv" -> ContentType("video", "x-flv")
        "wmv" -> ContentType("video", "x-ms-wmv")
        "3gp" -> ContentType("video", "3gpp")
        
        "mp3" -> ContentType("audio", "mpeg")
        "wav" -> ContentType("audio", "wav")
        "flac" -> ContentType("audio", "flac")
        "aac" -> ContentType("audio", "aac")
        "ogg" -> ContentType("audio", "ogg")
        "m4a" -> ContentType("audio", "mp4")
        "wma" -> ContentType("audio", "x-ms-wma")
        
        "jpg", "jpeg" -> ContentType("image", "jpeg")
        "png" -> ContentType("image", "png")
        "gif" -> ContentType("image", "gif")
        "webp" -> ContentType("image", "webp")
        "svg" -> ContentType("image", "svg+xml")
        
        "pdf" -> ContentType("application", "pdf")
        "txt" -> ContentType("text", "plain")
        "json" -> ContentType("application", "json")
        "xml" -> ContentType("text", "xml")
        "html" -> ContentType("text", "html")
        "css" -> ContentType("text", "css")
        "js" -> ContentType("text", "javascript")
        "zip" -> ContentType("application", "zip")
        else -> ContentType.Application.OctetStream
    }
}

/**
 * Adds WebDAV routing to the Ktor application with optional authentication.
 */
fun Application.webDavRouting(
    fileSystem: VirtualFileSystem,
    authConfig: ServerAuthConfig = ServerAuthConfig.Disabled,
    deviceUuid: String? = null
) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!deviceUuid.isNullOrBlank()) {
            call.response.header("X-Device-UUID", deviceUuid)
        }
        if (call.request.httpMethod == HttpMethod.Options) {
            return@intercept
        }
        if (authConfig is ServerAuthConfig.Basic) {
            var authenticated = false

            // 1. Check standard Authorization header ("Authorization: Basic <base64>")
            val authHeader = call.request.header("Authorization")
            @OptIn(ExperimentalEncodingApi::class)
            if (authHeader != null && authHeader.startsWith("Basic ", ignoreCase = true)) {
                val base64Credentials = authHeader.removePrefix("Basic ").trim()
                val decoded = try {
                    Base64.Default.decode(base64Credentials).decodeToString()
                } catch (e: Exception) {
                    ""
                }
                val parts = decoded.split(":", limit = 2)
                if (parts.size == 2 && authConfig.authenticator.authenticate(UserCredentials(parts[0], parts[1]))) {
                    authenticated = true
                }
            }

            // 2. Fallback check for URL query parameter authentication (?auth=<base64> or ?auth=user:pass or ?username=...&password=...)
            @OptIn(ExperimentalEncodingApi::class)
            if (!authenticated) {
                val authParam = call.request.queryParameters["auth"]
                val userParam = call.request.queryParameters["username"] ?: call.request.queryParameters["user"]
                val passParam = call.request.queryParameters["password"] ?: call.request.queryParameters["pass"]

                if (!authParam.isNullOrBlank()) {
                    val decoded = try {
                        Base64.Default.decode(authParam).decodeToString()
                    } catch (e: Exception) {
                        authParam
                    }
                    val parts = decoded.split(":", limit = 2)
                    if (parts.size == 2 && authConfig.authenticator.authenticate(UserCredentials(parts[0], parts[1]))) {
                        authenticated = true
                    }
                } else if (!userParam.isNullOrBlank() && passParam != null) {
                    if (authConfig.authenticator.authenticate(UserCredentials(userParam, passParam))) {
                        authenticated = true
                    }
                }
            }

            if (!authenticated) {
                call.response.header("WWW-Authenticate", "Basic realm=\"Folder Share\"")
                if (!deviceUuid.isNullOrBlank()) {
                    call.response.header("X-Device-UUID", deviceUuid)
                }
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                finish()
                return@intercept
            }
        }
    }

    routing {
        route("/{path...}") {
            // Handle OPTIONS request
            options {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val virtualPath = if (path.isEmpty()) "/" else "/$path"
                val virtualFile = fileSystem.resolve(virtualPath)
                val allowMethods = if (virtualFile != null && !virtualFile.isWriteAllowed) {
                    "OPTIONS, GET, HEAD, PROPFIND"
                } else {
                    "OPTIONS, GET, HEAD, PROPFIND, PUT, DELETE, MKCOL, MOVE"
                }
                call.response.header("DAV", "1, 2, access-control")
                call.response.header("Allow", allowMethods)
                call.response.header("X-Write-Allowed", if (virtualFile?.isWriteAllowed == false) "false" else "true")
                call.response.header("MS-Author-Via", "DAV")
                call.respond(HttpStatusCode.OK)
            }

            // Handle HEAD request (Probing media streaming support & file headers without downloading body)
            head {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val virtualPath = if (path.isEmpty()) "/" else "/$path"

                val virtualFile = fileSystem.resolve(virtualPath)
                if (virtualFile == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }

                if (!virtualFile.isReadAllowed) {
                    call.respond(HttpStatusCode.Forbidden, "Read access is disabled for this folder")
                    return@head
                }

                val allowMethods = if (!virtualFile.isWriteAllowed) {
                    "OPTIONS, GET, HEAD, PROPFIND"
                } else {
                    "OPTIONS, GET, HEAD, PROPFIND, PUT, DELETE, MKCOL, MOVE"
                }
                call.response.header("Allow", allowMethods)
                call.response.header("X-Write-Allowed", virtualFile.isWriteAllowed.toString())

                if (virtualFile.isDirectory) {
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                    call.respond(HttpStatusCode.OK)
                    return@head
                }

                val contentType = resolveMimeType(virtualFile.name)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentType, contentType.toString())
                call.response.header(HttpHeaders.LastModified, formatHttpDate(virtualFile.lastModified))

                val rangeHeader = call.request.header(HttpHeaders.Range)
                if (rangeHeader != null) {
                    val parsedRange = parseByteRange(rangeHeader, virtualFile.length)
                    if (parsedRange != null) {
                        call.response.header(HttpHeaders.ContentRange, "bytes ${parsedRange.start}-${parsedRange.end}/${virtualFile.length}")
                        call.response.header(HttpHeaders.ContentLength, parsedRange.length.toString())
                        call.respond(HttpStatusCode.PartialContent)
                        return@head
                    }
                }

                call.response.header(HttpHeaders.ContentLength, virtualFile.length.toString())
                call.respond(HttpStatusCode.OK)
            }

            // Handle PROPFIND request
            method(HttpMethodPropfind) {
                handle {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val virtualPath = if (path.isEmpty()) "/" else "/$path"
                    
                    val depthHeader = call.request.header("Depth") ?: "infinity"
                    val depth = when (depthHeader) {
                        "0" -> 0
                        "1" -> 1
                        else -> Int.MAX_VALUE
                    }

                    val virtualFile = fileSystem.resolve(virtualPath)
                    if (virtualFile == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@handle
                    }

                    if (!virtualFile.isReadAllowed) {
                        call.respond(HttpStatusCode.Forbidden, "Read access is disabled for this folder")
                        return@handle
                    }

                    val xmlResponse = buildPropfindResponse(virtualFile, depth, call.request.uri)
                    call.respondText(xmlResponse, ContentType.Text.Xml, HttpStatusCode.MultiStatus)
                }
            }

            // Handle GET request
            get {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val virtualPath = if (path.isEmpty()) "/" else "/$path"

                val virtualFile = fileSystem.resolve(virtualPath)
                if (virtualFile == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                if (!virtualFile.isReadAllowed) {
                    call.respond(HttpStatusCode.Forbidden, "Read access is disabled for this folder")
                    return@get
                }

                if (virtualFile.isDirectory) {
                    val children = virtualFile.listChildren()
                    val htmlBuilder = StringBuilder()
                    htmlBuilder.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Folder Share - ${virtualFile.name.ifEmpty { "Root" }}</title>")
                    htmlBuilder.append("<style>body{font-family:system-ui,-apple-system,sans-serif;padding:24px;background:#18181b;color:#f4f4f5;} a{color:#38bdf8;text-decoration:none;font-size:16px;} a:hover{text-decoration:underline;} ul{list-style:none;padding:0;margin:0;} li{padding:12px;border-bottom:1px solid #27272a;display:flex;align-items:center;gap:10px;}</style></head><body>")
                    htmlBuilder.append("<h2>📁 Folder Share: ${if (virtualPath == "/") "Root Directory" else virtualPath}</h2><ul>")
                    
                    if (virtualPath != "/") {
                        val parentPath = virtualPath.substringBeforeLast('/', "").ifEmpty { "/" }
                        htmlBuilder.append("<li><span>⬅️</span> <a href='${parentPath.encodeURLPath()}'>.. (Parent Directory)</a></li>")
                    }
                    
                    for (child in children) {
                        val icon = if (child.isDirectory) "📁" else "📄"
                        val hrefPath = if (virtualPath.endsWith("/")) "$virtualPath${child.name}" else "$virtualPath/${child.name}"
                        htmlBuilder.append("<li><span>$icon</span> <a href='${hrefPath.encodeURLPath()}'>${child.name}</a> ${if (!child.isDirectory) "<span style='color:#a1a1aa;font-size:14px;'>(${child.length} bytes)</span>" else ""}</li>")
                    }
                    htmlBuilder.append("</ul></body></html>")
                    call.respondText(htmlBuilder.toString(), ContentType.Text.Html, HttpStatusCode.OK)
                    return@get
                }

                val contentType = resolveMimeType(virtualFile.name)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")

                val rangeHeader = call.request.header(HttpHeaders.Range)
                if (rangeHeader != null) {
                    val parsedRange = parseByteRange(rangeHeader, virtualFile.length)
                    if (parsedRange != null) {
                        call.response.header(HttpHeaders.ContentRange, "bytes ${parsedRange.start}-${parsedRange.end}/${virtualFile.length}")
                        call.respond(VirtualFileContent(virtualFile, parsedRange.start..parsedRange.end, parsedRange.length, contentType))
                    } else {
                        call.response.header(HttpHeaders.ContentRange, "bytes */${virtualFile.length}")
                        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    }
                } else {
                    call.respond(VirtualFileContent(virtualFile, null, virtualFile.length, contentType))
                }
            }

            // Handle PUT request (Upload/Write)
            put {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val virtualPath = if (path.isEmpty()) "/" else "/$path"

                var virtualFile = fileSystem.resolve(virtualPath)
                var isNewFile = false

                if (virtualFile == null) {
                    if (virtualPath == "/") {
                        call.respond(HttpStatusCode.Forbidden, "Cannot create file at root")
                        return@put
                    }

                    val parentPath = virtualPath.substringBeforeLast('/', "").ifEmpty { "/" }
                    val fileName = virtualPath.substringAfterLast('/')

                    val parentFile = fileSystem.resolve(parentPath)
                    if (parentFile == null || !parentFile.isDirectory) {
                        call.respond(HttpStatusCode.NotFound, "Parent directory not found")
                        return@put
                    }

                    if (!parentFile.isWriteAllowed) {
                        call.respond(HttpStatusCode.Forbidden, "Write access is disabled for this folder")
                        return@put
                    }

                    virtualFile = parentFile.createChildFile(fileName)
                    if (virtualFile == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create file")
                        return@put
                    }
                    isNewFile = true
                } else {
                    if (virtualFile.isDirectory) {
                        call.respond(HttpStatusCode.MethodNotAllowed, "Cannot PUT to a directory")
                        return@put
                    }
                    if (!virtualFile.isWriteAllowed) {
                        call.respond(HttpStatusCode.Forbidden, "Write access is disabled for this folder")
                        return@put
                    }
                }

                val contentRangeHeader = call.request.header(HttpHeaders.ContentRange)
                var isAppend = false
                if (!contentRangeHeader.isNullOrBlank() && contentRangeHeader.startsWith("bytes ", ignoreCase = true)) {
                    val rangePart = contentRangeHeader.removePrefix("bytes ").substringBefore("/").trim()
                    val startByte = rangePart.substringBefore("-").toLongOrNull() ?: 0L
                    if (startByte > 0) {
                        isAppend = true
                    }
                }

                try {
                    virtualFile.write(call.receiveChannel(), append = isAppend)
                    if (isNewFile) {
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, e.message ?: "Write access denied")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Failed to write file")
                }
            }

            // Handle DELETE request
            delete {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val virtualPath = if (path.isEmpty()) "/" else "/$path"

                if (virtualPath == "/") {
                    call.respond(HttpStatusCode.Forbidden, "Cannot delete root directory")
                    return@delete
                }

                val virtualFile = fileSystem.resolve(virtualPath)
                if (virtualFile == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }

                if (!virtualFile.isWriteAllowed) {
                    call.respond(HttpStatusCode.Forbidden, "Write access is disabled for this folder")
                    return@delete
                }

                try {
                    val deleted = virtualFile.delete()
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to delete resource")
                    }
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, e.message ?: "Write access denied")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Failed to delete resource")
                }
            }

            // Handle MKCOL request (Create Directory)
            method(HttpMethodMkcol) {
                handle {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val virtualPath = if (path.isEmpty()) "/" else "/$path"

                    if (virtualPath == "/") {
                        call.respond(HttpStatusCode.MethodNotAllowed, "Root directory already exists")
                        return@handle
                    }

                    val existingFile = fileSystem.resolve(virtualPath)
                    if (existingFile != null) {
                        call.respond(HttpStatusCode.MethodNotAllowed, "Resource already exists")
                        return@handle
                    }

                    val parentPath = virtualPath.substringBeforeLast('/', "").ifEmpty { "/" }
                    val dirName = virtualPath.substringAfterLast('/')

                    val parentFile = fileSystem.resolve(parentPath)
                    if (parentFile == null || !parentFile.isDirectory) {
                        call.respond(HttpStatusCode.Conflict, "Parent directory does not exist")
                        return@handle
                    }

                    if (!parentFile.isWriteAllowed) {
                        call.respond(HttpStatusCode.Forbidden, "Write access is disabled for this folder")
                        return@handle
                    }

                    try {
                        val createdDir = parentFile.createChildDirectory(dirName)
                        if (createdDir != null) {
                            call.respond(HttpStatusCode.Created)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "Failed to create directory")
                        }
                    } catch (e: SecurityException) {
                        call.respond(HttpStatusCode.Forbidden, e.message ?: "Write access denied")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Failed to create directory")
                    }
                }
            }

            // Handle MOVE request (Rename/Move)
            method(HttpMethodMove) {
                handle {
                    val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val virtualPath = if (path.isEmpty()) "/" else "/$path"

                    if (virtualPath == "/") {
                        call.respond(HttpStatusCode.Forbidden, "Cannot move root directory")
                        return@handle
                    }

                    val virtualFile = fileSystem.resolve(virtualPath)
                    if (virtualFile == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@handle
                    }

                    if (!virtualFile.isWriteAllowed) {
                        call.respond(HttpStatusCode.Forbidden, "Write access is disabled for this folder")
                        return@handle
                    }

                    val destinationHeader = call.request.header("Destination")
                    if (destinationHeader.isNullOrEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "Missing Destination header")
                        return@handle
                    }

                    val cleanDest = destinationHeader.substringAfter("://").substringAfter("/", destinationHeader)
                    val newName = cleanDest.trim('/').substringAfterLast('/')

                    if (newName.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid new name")
                        return@handle
                    }

                    try {
                        val renamed = virtualFile.renameTo(newName)
                        if (renamed) {
                            call.respond(HttpStatusCode.Created)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "Failed to rename resource")
                        }
                    } catch (e: SecurityException) {
                        call.respond(HttpStatusCode.Forbidden, e.message ?: "Write access denied")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Failed to rename resource")
                    }
                }
            }

        }
    }
}

/**
 * Builds the XML response for a PROPFIND request.
 */
private suspend fun buildPropfindResponse(rootFile: VirtualFile, depth: Int, requestUri: String): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n")
    sb.append("<D:multistatus xmlns:D=\"DAV:\">\n")

    suspend fun appendFile(file: VirtualFile, uriPath: String, currentDepth: Int) {
        if (!file.isReadAllowed) return

        sb.append("  <D:response>\n")
        sb.append("    <D:href>$uriPath</D:href>\n")
        sb.append("    <D:propstat>\n")
        sb.append("      <D:prop>\n")
        
        val dateString = formatHttpDate(file.lastModified)
        sb.append("        <D:getlastmodified>$dateString</D:getlastmodified>\n")
        sb.append("        <D:current-user-privilege-set>\n")
        sb.append("          <D:privilege><D:read/></D:privilege>\n")
        if (file.isWriteAllowed) {
            sb.append("          <D:privilege><D:write/></D:privilege>\n")
        }
        sb.append("        </D:current-user-privilege-set>\n")
        sb.append("        <D:isreadonly>${if (file.isWriteAllowed) 0 else 1}</D:isreadonly>\n")
        sb.append("        <D:iswriteallowed>${if (file.isWriteAllowed) 1 else 0}</D:iswriteallowed>\n")
        
        if (file.isDirectory) {
            sb.append("        <D:resourcetype><D:collection/></D:resourcetype>\n")
        } else {
            val contentType = resolveMimeType(file.name)
            sb.append("        <D:resourcetype/>\n")
            sb.append("        <D:getcontentlength>${file.length}</D:getcontentlength>\n")
            sb.append("        <D:getcontenttype>$contentType</D:getcontenttype>\n")
        }
        
        sb.append("      </D:prop>\n")
        sb.append("      <D:status>HTTP/1.1 200 OK</D:status>\n")
        sb.append("    </D:propstat>\n")
        sb.append("  </D:response>\n")

        if (file.isDirectory && currentDepth < depth) {
            val children = file.listChildren()
            for (child in children) {
                if (!child.isReadAllowed) continue
                val encodedChildName = child.name.encodeURLPath()
                val childUri = if (uriPath.endsWith("/")) "$uriPath$encodedChildName" else "$uriPath/$encodedChildName"
                val finalChildUri = if (child.isDirectory && !childUri.endsWith("/")) "$childUri/" else childUri
                appendFile(child, finalChildUri, currentDepth + 1)
            }
        }
    }

    val baseUri = if (rootFile.isDirectory && !requestUri.endsWith("/")) "$requestUri/" else requestUri
    appendFile(rootFile, baseUri, 0)
    
    sb.append("</D:multistatus>\n")
    return sb.toString()
}

/**
 * Checks whether an exception was caused by a client disconnecting/closing the socket prematurely.
 */
fun isClientDisconnect(e: Throwable): Boolean {
    var current: Throwable? = e
    while (current != null) {
        val name = current::class.simpleName ?: ""
        val msg = current.message ?: ""
        if (current is kotlinx.coroutines.CancellationException ||
            name.contains("ClosedByteChannelException") ||
            name.contains("ChannelWriteException") ||
            name.contains("ClosedChannelException") ||
            msg.contains("Broken pipe", ignoreCase = true) ||
            msg.contains("Cannot write to channel", ignoreCase = true) ||
            msg.contains("Connection reset", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * Custom Ktor OutgoingContent that reads from our VirtualFile.
 * It uses WriteChannelContent which allows suspending while streaming the response.
 */
class VirtualFileContent(
    private val virtualFile: VirtualFile,
    private val range: LongRange?,
    override val contentLength: Long,
    override val contentType: ContentType = ContentType.Application.OctetStream,
    override val status: HttpStatusCode = if (range != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        try {
            val readChannel = virtualFile.read(range)
            readChannel.copyTo(channel)
        } catch (e: Exception) {
            if (isClientDisconnect(e)) {
                // Client closed socket connection during stream playback / seek
                return
            }
            throw e
        }
    }
}
