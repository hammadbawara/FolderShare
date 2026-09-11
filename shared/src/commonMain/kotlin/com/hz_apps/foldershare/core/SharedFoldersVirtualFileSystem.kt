package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first

class SharedFoldersVirtualFileSystem(
    private val foldersProvider: suspend () -> List<FolderConfigEntity>,
    private val resolver: LocalFolderResolver = createLocalFolderResolver()
) : VirtualFileSystem {

    constructor(
        folders: List<FolderConfigEntity>,
        resolver: LocalFolderResolver = createLocalFolderResolver()
    ) : this(foldersProvider = { folders }, resolver = resolver)

    constructor(
        folderDao: FolderConfigDao,
        resolver: LocalFolderResolver = createLocalFolderResolver()
    ) : this(foldersProvider = { folderDao.getAllFolders().first() }, resolver = resolver)

    private suspend fun getActiveSharedFolders(): Map<String, FolderConfigEntity> {
        return foldersProvider()
            .filter { it.isShared }
            .associateBy { it.name }
    }

    override suspend fun resolve(path: String): VirtualFile? {
        val cleanPath = path.trim('/').ifEmpty { "" }
        val activeFolders = getActiveSharedFolders()

        if (cleanPath.isEmpty()) {
            return RootVirtualDirectory(
                foldersProvider = { getActiveSharedFolders() },
                resolver = resolver
            )
        }

        val segments = cleanPath.split("/")
        val folderName = segments.first()
        val folderConfig = activeFolders[folderName] ?: return null

        val subPath = if (segments.size > 1) {
            segments.drop(1).joinToString("/")
        } else {
            ""
        }

        val rawFile = resolver.resolveInFolder(folderConfig, subPath) ?: return null
        return PermissionWrapperVirtualFile(
            delegate = rawFile,
            isReadAllowed = folderConfig.isReadAllowed,
            isWriteAllowed = folderConfig.isWriteAllowed
        )
    }
}

class RootVirtualDirectory(
    private val foldersProvider: suspend () -> Map<String, FolderConfigEntity>,
    private val resolver: LocalFolderResolver
) : VirtualFile {
    override val name: String = ""
    override val path: String = "/"
    override val isDirectory: Boolean = true
    override val length: Long = 0L
    override val lastModified: Long = System.currentTimeMillis()
    override val isReadAllowed: Boolean = true
    override val isWriteAllowed: Boolean = false

    override suspend fun listChildren(): List<VirtualFile> {
        val sharedFolders = foldersProvider()
        return sharedFolders.values.mapNotNull { folder ->
            val raw = resolver.resolveInFolder(folder, "") ?: return@mapNotNull null
            PermissionWrapperVirtualFile(
                delegate = raw,
                isReadAllowed = folder.isReadAllowed,
                isWriteAllowed = folder.isWriteAllowed
            )
        }
    }

    override suspend fun read(range: LongRange?): ByteReadChannel {
        throw UnsupportedOperationException("Root directory cannot be read as stream")
    }
}

