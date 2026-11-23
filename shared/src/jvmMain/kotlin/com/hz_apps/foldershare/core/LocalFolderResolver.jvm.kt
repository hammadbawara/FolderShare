package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity

actual fun createLocalFolderResolver(): LocalFolderResolver {
    return JvmLocalFolderResolver()
}

class JvmLocalFolderResolver : LocalFolderResolver {
    override suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile? {
        return resolveOkioPathInFolder(folder.path, folder.name, relativePath)
    }
}
