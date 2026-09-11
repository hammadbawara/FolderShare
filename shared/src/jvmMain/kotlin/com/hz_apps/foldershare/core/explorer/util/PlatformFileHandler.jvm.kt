package com.hz_apps.foldershare.core.explorer.util

import okio.Path
import okio.Path.Companion.toPath
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class JvmPlatformFileHandler(
    private val linuxOpener: DesktopFileOpener = LinuxDesktopFileOpener(),
    private val windowsOpener: DesktopFileOpener = WindowsDesktopFileOpener(),
    private val defaultOpener: DesktopFileOpener = DefaultDesktopFileOpener()
) : PlatformFileHandler {

    override fun getDefaultDownloadDirectory(): Path {
        val homeDirStr = System.getProperty("user.home") ?: "."
        return homeDirStr.toPath() / "Downloads" / "FolderShare"
    }

    override fun getTemporaryDirectory(): Path {
        val tmpDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val dir = tmpDir.toPath() / "FolderShare" / "temp"
        try {
            java.io.File(dir.toString()).mkdirs()
        } catch (ignored: Exception) {}
        return dir
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
        try {
            val os = System.getProperty("os.name").lowercase()
            val handled = if (os.contains("linux")) {
                linuxOpener.openFile(downloadUrl, mimeType, title, fileSize, rawUrl)
            } else if (os.contains("win")) {
                windowsOpener.openFile(downloadUrl, mimeType, title, fileSize, rawUrl)
            } else false

            if (!handled) {
                defaultOpener.openFile(downloadUrl, mimeType, title, fileSize, rawUrl)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun openLocalFile(
        filePath: Path,
        mimeType: String?,
        title: String?
    ) {
        val javaFile = java.io.File(filePath.toString())
        if (!javaFile.exists()) return

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(javaFile)
                return
            }
        } catch (ignored: Exception) {
        }

        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("linux") -> {
                    Runtime.getRuntime().exec(arrayOf("xdg-open", javaFile.absolutePath))
                }
                os.contains("win") -> {
                    ProcessBuilder("cmd.exe", "/c", "start", "", javaFile.absolutePath).start()
                }
                os.contains("mac") -> {
                    Runtime.getRuntime().exec(arrayOf("open", javaFile.absolutePath))
                }
                else -> {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(javaFile)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    override fun shareFile(downloadUrl: String, title: String) {
        copyToClipboard(downloadUrl)
    }

    override fun copyToClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (ignored: Exception) {
        }
    }
}

actual fun getPlatformFileHandler(): PlatformFileHandler = JvmPlatformFileHandler()
