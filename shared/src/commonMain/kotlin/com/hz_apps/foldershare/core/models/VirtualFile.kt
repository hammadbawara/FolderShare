package com.hz_apps.foldershare.core.models

import io.ktor.utils.io.ByteReadChannel

/**
 * Platform-independent abstraction for a file or directory.
 */
interface VirtualFile {
    val name: String
    val path: String
    val isDirectory: Boolean
    val length: Long
    val lastModified: Long

    val isReadAllowed: Boolean get() = true
    val isWriteAllowed: Boolean get() = false

    /**
     * Lists the children if this is a directory.
     * Returns empty list if it's a file or cannot be read.
     */
    suspend fun listChildren(): List<VirtualFile>

    /**
     * Opens a read channel for this file, optionally for a specific byte range.
     * Use for HTTP 206 Partial Content.
     */
    suspend fun read(range: LongRange? = null): ByteReadChannel

    /**
     * Writes data from the channel to this file.
     * @param append if true, appends data to the existing file; if false, overwrites it.
     */
    suspend fun write(channel: ByteReadChannel, append: Boolean = false) {
        throw UnsupportedOperationException("Writing to this file is not supported")
    }

    /**
     * Deletes this file or directory.
     */
    suspend fun delete(): Boolean {
        return false
    }

    /**
     * Creates a new child file inside this directory.
     */
    suspend fun createChildFile(name: String): VirtualFile? {
        return null
    }

    /**
     * Creates a new child directory inside this directory.
     */
    suspend fun createChildDirectory(name: String): VirtualFile? {
        return null
    }

    /**
     * Renames this file or directory.
     */
    suspend fun renameTo(newName: String): Boolean {
        return false
    }
}