package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun createLocalFolderResolver(): LocalFolderResolver {
    return JvmLocalFolderResolver()
}

class JvmLocalFolderResolver : LocalFolderResolver {
    override suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile? {
        val rootPath = folder.path.toPath()
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
