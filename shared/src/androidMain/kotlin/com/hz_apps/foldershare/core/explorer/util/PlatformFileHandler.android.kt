package com.hz_apps.foldershare.core.explorer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import okio.Path
import okio.Path.Companion.toPath

class AndroidPlatformFileHandler : PlatformFileHandler {

    override fun getDefaultDownloadDirectory(): Path {
        val downloadsDirStr = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        return downloadsDirStr.toPath() / "FolderShare"
    }

    override fun getTemporaryDirectory(): Path {
        val context = AndroidContextProvider.applicationContext
        val baseCacheDir = context?.externalCacheDir ?: context?.cacheDir
        val tempDir = if (baseCacheDir != null) {
            java.io.File(baseCacheDir, "temp").apply { mkdirs() }.absolutePath.toPath()
        } else {
            getDefaultDownloadDirectory() / "temp"
        }
        return tempDir
    }

    override fun getDownloadDestinationPath(fileName: String, customDirectory: Path?): Path {
        val baseDir = customDirectory ?: getDefaultDownloadDirectory()
        return baseDir / fileName
    }

    override fun openFile(
        downloadUrl: String,
        mimeType: String?,
        title: String?,
        fileSize: Long?,
        rawUrl: String?
    ) {
        val context = AndroidContextProvider.applicationContext ?: return

        val uri = downloadUrl.toUri()

        val resolvedTitle = title ?: try {
            uri.lastPathSegment
        } catch (e: Exception) {
            null
        }

        val extension = resolvedTitle?.substringAfterLast('.', "")?.lowercase()?.ifEmpty { null }
            ?: MimeTypeMap.getFileExtensionFromUrl(downloadUrl)?.lowercase()?.ifEmpty { null }

        val resolvedMimeType = when {
            mimeType != null && mimeType != "*/*" && !mimeType.endsWith("/*") -> mimeType
            extension != null -> {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: PathUtils.getMimeType(extension)
            }
            else -> mimeType ?: "*/*"
        }

        val targetRawUrl = rawUrl ?: downloadUrl

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(uri, resolvedMimeType)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (resolvedTitle != null) {
                    putExtra(Intent.EXTRA_TITLE, resolvedTitle)
                    putExtra("title", resolvedTitle)
                    putExtra("S.title", resolvedTitle)
                }

                putExtra("real_path", targetRawUrl)
                putExtra("filepath", targetRawUrl)
                putExtra("path", targetRawUrl)
                putExtra("S.real_path", targetRawUrl)
                putExtra("S.filepath", targetRawUrl)
                putExtra("S.path", targetRawUrl)

                if (fileSize != null && fileSize > 0) {
                    putExtra("android.intent.extra.SIZE", fileSize)
                    putExtra("size", fileSize)
                    putExtra("filesize", fileSize)
                    putExtra("orig_size", fileSize)
                    putExtra("length", fileSize)
                    putExtra("l.size", fileSize)
                    putExtra("l.orig_size", fileSize)
                    putExtra("l.length", fileSize)
                    putExtra("l.filesize", fileSize)
                    putExtra("l.android.intent.extra.SIZE", fileSize)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser URI launch
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun openLocalFile(
        filePath: Path,
        mimeType: String?,
        title: String?
    ) {
        val context = AndroidContextProvider.applicationContext ?: return
        val javaFile = java.io.File(filePath.toString())
        if (!javaFile.exists()) return

        val resolvedTitle = title ?: javaFile.name
        val extension = resolvedTitle.substringAfterLast('.', "").lowercase().ifEmpty { null }

        val resolvedMimeType = when {
            mimeType != null && mimeType != "*/*" && !mimeType.endsWith("/*") -> mimeType
            extension != null -> {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: PathUtils.getMimeType(extension)
            }
            else -> mimeType ?: "*/*"
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                javaFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(contentUri, resolvedMimeType)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TITLE, resolvedTitle)
                putExtra("title", resolvedTitle)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    javaFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndTypeAndNormalize(contentUri, resolvedMimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Open with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun shareFile(downloadUrl: String, title: String) {
        val context = AndroidContextProvider.applicationContext ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, downloadUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            copyToClipboard(downloadUrl)
        }
    }

    override fun copyToClipboard(text: String) {
        val context = AndroidContextProvider.applicationContext ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Folder Share Link", text)
        clipboard?.setPrimaryClip(clip)
    }
}

actual fun getPlatformFileHandler(): PlatformFileHandler = AndroidPlatformFileHandler()
