package com.hz_apps.foldershare.feature.explorer

import android.content.Intent
import android.provider.OpenableColumns
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.FileInputStream
import java.io.InputStream

actual fun platformFileSize(platformFile: PlatformFile): Long {
    val uri = when (val androidFile = platformFile.androidFile) {
        is AndroidFile.FileWrapper -> return androidFile.file.length()
        is AndroidFile.UriWrapper -> androidFile.uri
    }
    val context = AndroidContextProvider.applicationContext ?: return 0L
    var fileSize: Long = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {}
    return fileSize
}

actual fun openPlatformFileStream(platformFile: PlatformFile): InputStream {
    val uri = when (val androidFile = platformFile.androidFile) {
        is AndroidFile.FileWrapper -> return FileInputStream(androidFile.file)
        is AndroidFile.UriWrapper -> androidFile.uri
    }
    val context = AndroidContextProvider.applicationContext
        ?: throw IllegalStateException("Application context is null")
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
        // Some ContentProviders / pickers don't support persistable permission grants
    }
    return context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("Unable to open stream for URI: $uri")
}
