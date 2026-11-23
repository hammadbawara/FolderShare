package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

interface LocalFolderResolver {
    suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile?
}

expect fun createLocalFolderResolver(): LocalFolderResolver

internal fun resolveOkioPathInFolder(
    rootPathStr: String,
    folderName: String,
    relativePath: String,
    fileSystem: FileSystem = FileSystem.SYSTEM
): VirtualFile? {
    val rootPath = rootPathStr.toPath()
    if (!fileSystem.exists(rootPath)) return null

    val cleanRelative = relativePath.trimStart('/')
    val targetPath = if (cleanRelative.isEmpty()) rootPath else rootPath / cleanRelative

    if (!fileSystem.exists(targetPath)) return null

    // Security check for directory traversal (rejects both .. escapes and sibling prefix paths)
    val normalizedRoot = rootPath.normalized()
    val normalizedTarget = targetPath.normalized()
    val rootStr = normalizedRoot.toString()
    val targetStr = normalizedTarget.toString()
    val separator = Path.DIRECTORY_SEPARATOR
    val isWithinRoot = when {
        targetStr == rootStr -> true
        rootStr.endsWith(separator) -> targetStr.startsWith(rootStr)
        else -> targetStr.startsWith("$rootStr$separator")
    }
    if (!isWithinRoot) {
        return null
    }

    val displayPath = if (cleanRelative.isEmpty()) "/$folderName" else "/$folderName/$cleanRelative"
    return OkioVirtualFile(
        fileSystem = fileSystem,
        selfPath = normalizedTarget,
        path = displayPath,
        rootFolderName = folderName
    )
}
