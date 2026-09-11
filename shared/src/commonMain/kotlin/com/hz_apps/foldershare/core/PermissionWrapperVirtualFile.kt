package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import io.ktor.utils.io.ByteReadChannel

class PermissionWrapperVirtualFile(
    private val delegate: VirtualFile,
    override val isReadAllowed: Boolean,
    override val isWriteAllowed: Boolean
) : VirtualFile {
    override val name: String get() = delegate.name
    override val path: String get() = delegate.path
    override val isDirectory: Boolean get() = delegate.isDirectory
    override val length: Long get() = delegate.length
    override val lastModified: Long get() = delegate.lastModified

    override suspend fun listChildren(): List<VirtualFile> {
        if (!isReadAllowed) return emptyList()
        val children = delegate.listChildren()
        return children.map { child ->
            PermissionWrapperVirtualFile(child, isReadAllowed, isWriteAllowed)
        }
    }

    override suspend fun read(range: LongRange?): ByteReadChannel {
        if (!isReadAllowed) {
            throw SecurityException("Read access disabled for this folder")
        }
        return delegate.read(range)
    }

    override suspend fun write(channel: ByteReadChannel, append: Boolean) {
        if (!isWriteAllowed) {
            throw SecurityException("Write access disabled for this folder")
        }
        delegate.write(channel, append)
    }

    override suspend fun delete(): Boolean {
        if (!isWriteAllowed) {
            throw SecurityException("Write access disabled for this folder")
        }
        return delegate.delete()
    }

    override suspend fun createChildFile(name: String): VirtualFile? {
        if (!isWriteAllowed) {
            throw SecurityException("Write access disabled for this folder")
        }
        val child = delegate.createChildFile(name) ?: return null
        return PermissionWrapperVirtualFile(child, isReadAllowed, isWriteAllowed)
    }

    override suspend fun createChildDirectory(name: String): VirtualFile? {
        if (!isWriteAllowed) {
            throw SecurityException("Write access disabled for this folder")
        }
        val child = delegate.createChildDirectory(name) ?: return null
        return PermissionWrapperVirtualFile(child, isReadAllowed, isWriteAllowed)
    }

    override suspend fun renameTo(newName: String): Boolean {
        if (!isWriteAllowed) {
            throw SecurityException("Write access disabled for this folder")
        }
        return delegate.renameTo(newName)
    }
}

