package com.hz_apps.foldershare.core

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import okio.use

actual fun createLocalFolderResolver(): LocalFolderResolver {
    return AndroidLocalFolderResolver()
}

class AndroidLocalFolderResolver : LocalFolderResolver {
    override suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile? {
        val pathStr = folder.path
        if (pathStr.startsWith("content://")) {
            return resolveSafUri(pathStr, folder.name, relativePath)
        } else {
            val rootPath = pathStr.toPath()
            if (!FileSystem.SYSTEM.exists(rootPath)) return null

            val cleanRelative = relativePath.trimStart('/')
            val targetPath = if (cleanRelative.isEmpty()) rootPath else rootPath / cleanRelative

            if (!FileSystem.SYSTEM.exists(targetPath)) return null

            // Security check for directory traversal
            val normalizedRoot = rootPath.normalized()
            val normalizedTarget = targetPath.normalized()
            if (!normalizedTarget.toString().startsWith(normalizedRoot.toString())) {
                return null
            }

            val displayPath = if (relativePath.isEmpty()) "/${folder.name}" else "/${folder.name}/$relativePath"
            return OkioVirtualFile(
                fileSystem = FileSystem.SYSTEM,
                selfPath = targetPath,
                path = displayPath,
                rootFolderName = folder.name
            )
        }
    }

    private fun resolveSafUri(uriString: String, folderName: String, relativePath: String): VirtualFile? {
        val context = AndroidContextProvider.applicationContext ?: return null
        val treeUri = Uri.parse(uriString)
        var currentDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null

        if (relativePath.isNotEmpty()) {
            val segments = relativePath.split("/")
            for (segment in segments) {
                if (segment.isEmpty()) continue
                currentDoc = currentDoc.findFile(segment) ?: return null
            }
        }

        val displayPath = if (relativePath.isEmpty()) "/$folderName" else "/$folderName/$relativePath"
        return AndroidSafVirtualFile(currentDoc, displayPath, folderName)
    }
}

class AndroidSafVirtualFile(
    private val doc: DocumentFile,
    override val path: String,
    private val rootFolderName: String
) : VirtualFile {
    override val name: String = if (path == "/$rootFolderName") rootFolderName else (doc.name ?: "file")
    override val isDirectory: Boolean = doc.isDirectory
    override val length: Long = doc.length()
    override val lastModified: Long = doc.lastModified()

    override suspend fun listChildren(): List<VirtualFile> {
        if (!isDirectory) return emptyList()
        val children = doc.listFiles()
        return children.map { child ->
            val childName = child.name ?: "file"
            val childPath = if (path.endsWith("/")) "$path$childName" else "$path/$childName"
            AndroidSafVirtualFile(child, childPath, rootFolderName)
        }
    }

    override suspend fun read(range: LongRange?): ByteReadChannel {
        val context = requireNotNull(AndroidContextProvider.applicationContext)
        val pfd = context.contentResolver.openFileDescriptor(doc.uri, "r")
            ?: throw IllegalStateException("Cannot open input stream for SAF URI: ${doc.uri}")

        val fis = java.io.FileInputStream(pfd.fileDescriptor)
        if (range != null) {
            fis.channel.position(range.first)
        }

        val lengthToRead = range?.let { it.last - it.first + 1 } ?: length
        val source = fis.source()

        val boundedSource = object : Source by source {
            var remaining = lengthToRead
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining <= 0) return -1L
                val bytesToRead = minOf(byteCount, remaining)
                val read = source.read(sink, bytesToRead)
                if (read != -1L) {
                    remaining -= read
                }
                return read
            }

            override fun close() {
                try {
                    source.close()
                } finally {
                    pfd.close()
                }
            }
        }

        return boundedSource.buffer().inputStream().toByteReadChannel()
    }

    override suspend fun write(channel: ByteReadChannel, append: Boolean) {
        val context = requireNotNull(AndroidContextProvider.applicationContext)
        val mode = if (append) "wa" else "wt"
        val outputStream = context.contentResolver.openOutputStream(doc.uri, mode)
            ?: throw IllegalStateException("Cannot open output stream for SAF URI: ${doc.uri}")

        outputStream.sink().buffer().use { bufferedSink ->
            channel.toInputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bufferedSink.write(buffer, 0, bytesRead)
                }
                bufferedSink.flush()
            }
        }
    }

    override suspend fun delete(): Boolean {
        return doc.delete()
    }

    override suspend fun createChildFile(name: String): VirtualFile? {
        if (!isDirectory) return null
        val existing = doc.findFile(name)
        val childDoc = existing ?: doc.createFile(getSafMimeType(name), name) ?: return null
        val childPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        return AndroidSafVirtualFile(childDoc, childPath, rootFolderName)
    }

    override suspend fun createChildDirectory(name: String): VirtualFile? {
        if (!isDirectory) return null
        val existing = doc.findFile(name)
        val childDoc = if (existing != null && existing.isDirectory) {
            existing
        } else {
            doc.createDirectory(name) ?: return null
        }
        val childPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        return AndroidSafVirtualFile(childDoc, childPath, rootFolderName)
    }

    private fun getSafMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return "application/octet-stream"
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
