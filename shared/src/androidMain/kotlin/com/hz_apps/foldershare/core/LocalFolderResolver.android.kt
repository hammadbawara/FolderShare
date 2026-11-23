package com.hz_apps.foldershare.core

import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity

actual fun createLocalFolderResolver(): LocalFolderResolver {
    return AndroidLocalFolderResolver()
}

class AndroidLocalFolderResolver : LocalFolderResolver {
    override suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile? {
        val resolvedRoot = if (folder.path.startsWith("content://")) {
            resolveLegacyExternalStorageTreePath(folder.path) ?: return null
        } else {
            folder.path
        }
        return resolveOkioPathInFolder(resolvedRoot, folder.name, relativePath)
    }
}

internal fun resolveLegacyExternalStorageTreePath(uriString: String): String? {
    return try {
        val uri = uriString.toUri()
        if (uri.authority != "com.android.externalstorage.documents") return null
        val treeDocId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = treeDocId.split(":", limit = 2)
        val volumeId = parts.getOrNull(0) ?: return null
        val subPath = parts.getOrNull(1)?.trim('/') ?: ""
        val volumeRoot = if (volumeId.equals("primary", ignoreCase = true)) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory().absolutePath
        } else if (volumeId.isNotBlank()) {
            "/storage/$volumeId"
        } else {
            return null
        }
        if (subPath.isEmpty()) volumeRoot else "$volumeRoot/$subPath"
    } catch (_: Exception) {
        null
    }
}
