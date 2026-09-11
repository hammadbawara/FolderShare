package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer
import okio.use

/**
 * Multiplatform implementation of VirtualFileSystem using Okio.
 */
class OkioVirtualFileSystem(
    private val rootPath: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : VirtualFileSystem {

    init {
        require(fileSystem.metadataOrNull(rootPath)?.isDirectory == true) {
            "Root path must be an existing directory: $rootPath"
        }
    }

    override suspend fun resolve(path: String): VirtualFile? {
        val cleanRelativePath = path.trimStart('/')
        val targetPath = if (cleanRelativePath.isEmpty()) rootPath else rootPath / cleanRelativePath
        
        if (!fileSystem.exists(targetPath)) return null

        // Security check to prevent directory traversal attacks
        val normalizedRoot = rootPath.normalized()
        val normalizedTarget = targetPath.normalized()
        if (!normalizedTarget.toString().startsWith(normalizedRoot.toString())) {
            return null
        }

        val displayPath = if (path.startsWith("/")) path else "/$path"
        val rootFolderName = rootPath.name.ifEmpty { "root" }

        return OkioVirtualFile(
            fileSystem = fileSystem,
            selfPath = targetPath,
            path = displayPath,
            rootFolderName = rootFolderName
        )
    }
}

/**
 * Multiplatform implementation of VirtualFile backed by Okio.
 */
class OkioVirtualFile(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    val selfPath: Path,
    override val path: String,
    private val rootFolderName: String
) : VirtualFile {

    private val metadata get() = fileSystem.metadataOrNull(selfPath)

    override val name: String
        get() = if (path == "/$rootFolderName" || path == "/") rootFolderName else selfPath.name

    override val isDirectory: Boolean
        get() = metadata?.isDirectory == true

    override val length: Long
        get() = metadata?.size ?: 0L

    override val lastModified: Long
        get() = metadata?.lastModifiedAtMillis ?: 0L

    override suspend fun listChildren(): List<VirtualFile> {
        if (!isDirectory) return emptyList()
        val children = try {
            fileSystem.list(selfPath)
        } catch (_: Exception) {
            return emptyList()
        }

        return children.map { childPath ->
            val childDisplayPath = if (path.endsWith("/")) "$path${childPath.name}" else "$path/${childPath.name}"
            OkioVirtualFile(
                fileSystem = fileSystem,
                selfPath = childPath,
                path = childDisplayPath,
                rootFolderName = rootFolderName
            )
        }
    }

    override suspend fun read(range: LongRange?): ByteReadChannel {
        if (range == null) {
            val source = fileSystem.source(selfPath)
            return source.buffer().inputStream().toByteReadChannel()
        }

        val fileHandle = fileSystem.openReadOnly(selfPath)
        val lengthToRead = range.last - range.first + 1
        val source = fileHandle.source(range.first)

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
                    fileHandle.close()
                }
            }
        }

        return boundedSource.buffer().inputStream().toByteReadChannel()
    }

    override suspend fun write(channel: ByteReadChannel, append: Boolean) {
        val sink = if (append) fileSystem.appendingSink(selfPath) else fileSystem.sink(selfPath)
        sink.buffer().use { bufferedSink ->
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
        return try {
            if (isDirectory) {
                fileSystem.deleteRecursively(selfPath)
            } else {
                fileSystem.delete(selfPath)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createChildFile(name: String): VirtualFile? {
        if (!isDirectory) return null
        val childPath = selfPath / name
        if (!childPath.normalized().toString().startsWith(selfPath.normalized().toString())) {
            return null
        }

        try {
            if (!fileSystem.exists(childPath)) {
                fileSystem.sink(childPath).close()
            }
        } catch (_: Exception) {
            return null
        }

        val childDisplayPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        return OkioVirtualFile(
            fileSystem = fileSystem,
            selfPath = childPath,
            path = childDisplayPath,
            rootFolderName = rootFolderName
        )
    }

    override suspend fun createChildDirectory(name: String): VirtualFile? {
        if (!isDirectory) return null
        val childPath = selfPath / name
        if (!childPath.normalized().toString().startsWith(selfPath.normalized().toString())) {
            return null
        }

        try {
            if (!fileSystem.exists(childPath)) {
                fileSystem.createDirectories(childPath)
            }
        } catch (_: Exception) {
            return null
        }

        val childDisplayPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        return OkioVirtualFile(
            fileSystem = fileSystem,
            selfPath = childPath,
            path = childDisplayPath,
            rootFolderName = rootFolderName
        )
    }

    override suspend fun renameTo(newName: String): Boolean {
        val parentPath = selfPath.parent ?: return false
        val targetPath = parentPath / newName
        if (fileSystem.exists(targetPath)) return false
        return try {
            fileSystem.atomicMove(selfPath, targetPath)
            true
        } catch (_: Exception) {
            false
        }
    }
}
