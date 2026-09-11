package com.hz_apps.foldershare.feature.share

import android.content.Intent
import android.provider.DocumentsContract
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import io.github.vinceglb.filekit.core.PlatformDirectory

actual fun processFolderPick(directory: PlatformDirectory): Pair<String, String> {
    val uri = directory.uri
    val context = AndroidContextProvider.applicationContext
    var folderName: String? = null

    if (context != null) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}

        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    folderName = cursor.getString(0)
                }
            }
        } catch (_: Exception) {}
    }

    val finalName = folderName ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
        ?: "Selected Folder"
    return Pair(uri.toString(), finalName)
}
