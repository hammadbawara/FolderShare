package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile

/**
 * Platform-independent abstraction for the file system root exposed by the WebDAV server.
 */
interface VirtualFileSystem {
    /**
     * Resolves a relative path to a VirtualFile.
     * @param path The path relative to the root (e.g., "/" or "/video.mp4").
     * @return The VirtualFile if found, or null if it does not exist.
     */
    suspend fun resolve(path: String): VirtualFile?
}
