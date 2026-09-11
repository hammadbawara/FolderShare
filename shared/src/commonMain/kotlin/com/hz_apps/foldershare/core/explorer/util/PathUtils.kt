package com.hz_apps.foldershare.core.explorer.util

import com.hz_apps.foldershare.feature.explorer.PathSegment
import okio.FileSystem
import okio.Path

object PathUtils {
    
    /**
     * Generates a unique destination path in the given directory.
     * If the target file already exists, it appends (1), (2), etc. to avoid collisions.
     */
    fun generateUniqueDestinationPath(
        directory: Path,
        fileName: String,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ): Path {
        val targetPath = directory / fileName
        if (!fileSystem.exists(targetPath)) {
            return targetPath
        }

        val dotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var counter = 1
        while (true) {
            val candidateName = "$nameWithoutExt ($counter)$extension"
            val candidatePath = directory / candidateName
            if (!fileSystem.exists(candidatePath)) {
                return candidatePath
            }
            counter++
        }
    }
    
    fun normalizePath(path: String): String {
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        while (p.endsWith("/") && p.length > 1) {
            p = p.dropLast(1)
        }
        return p
    }

    fun getParentPath(path: String): String {
        val normalized = normalizePath(path)
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash <= 0) return "/"
        return normalized.substring(0, lastSlash)
    }

    fun joinPath(parentPath: String, childName: String): String {
        val parent = normalizePath(parentPath)
        return if (parent == "/") "/$childName" else "$parent/$childName"
    }

    fun buildPathSegments(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        segments.add(PathSegment("Root", "/"))

        val parts = path.split("/").filter { it.isNotEmpty() }
        var currentAcc = ""
        for (part in parts) {
            currentAcc += "/$part"
            segments.add(PathSegment(part, currentAcc))
        }
        return segments
    }

    fun getMimeType(extension: String): String {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            // Video
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "ts" -> "video/mp2ts"
            "3gp" -> "video/3gpp"

            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/x-wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"

            // Image
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"

            // Documents & Text
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "py" -> "text/x-python"
            "c" -> "text/x-c"
            "cpp" -> "text/x-c++"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            else -> "*/*"
        }
    }
}
