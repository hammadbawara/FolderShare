package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity

interface LocalFolderResolver {
    suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile?
}

expect fun createLocalFolderResolver(): LocalFolderResolver
