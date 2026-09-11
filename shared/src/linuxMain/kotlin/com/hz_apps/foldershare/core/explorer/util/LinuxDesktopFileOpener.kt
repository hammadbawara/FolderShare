package com.hz_apps.foldershare.core.explorer.util

import java.io.File

class LinuxDesktopFileOpener : DesktopFileOpener {
    override fun openFile(
        downloadUrl: String,
        mimeType: String?,
        title: String?,
        fileSize: Long?,
        rawUrl: String?
    ): Boolean {
        try {
            val extension = (title ?: downloadUrl.substringAfterLast('/', ""))
                .substringAfterLast('.', "")
                .lowercase()
                .ifEmpty { null }

            val resolvedMimeType = when {
                mimeType != null && mimeType != "*/*" && !mimeType.endsWith("/*") -> mimeType
                extension != null -> PathUtils.getMimeType(extension)
                else -> mimeType ?: "*/*"
            }

            if (resolvedMimeType == "text/html" || resolvedMimeType == "*/*") {
                return false
            }

            val process = Runtime.getRuntime().exec(arrayOf("xdg-mime", "query", "default", resolvedMimeType))
            val desktopApp = process.inputStream.bufferedReader().readText().trim()
            if (desktopApp.isEmpty()) {
                return false
            }

            // 1. Try gtk-launch which is standard for GTK DEs and only needs the desktop file name
            val gtkProcess = Runtime.getRuntime().exec(arrayOf("gtk-launch", desktopApp, downloadUrl))
            if (gtkProcess.waitFor() == 0) {
                return true
            }

            val desktopFilePath = findDesktopFilePath(desktopApp)
            if (desktopFilePath != null) {
                // 2. Try gio launch (Modern GNOME / GLib)
                val gioProcess = Runtime.getRuntime().exec(arrayOf("gio", "launch", desktopFilePath, downloadUrl))
                if (gioProcess.waitFor() == 0) return true

                // 3. Try dex (Generic DesktopEntry Execution tool)
                val dexProcess = Runtime.getRuntime().exec(arrayOf("dex", desktopFilePath, downloadUrl))
                if (dexProcess.waitFor() == 0) return true

                // 4. Try KDE tools (kioclient5, kioclient, kfmclient)
                val kdeTools = listOf("kioclient5", "kioclient", "kfmclient")
                for (tool in kdeTools) {
                    try {
                        val kdeProcess = Runtime.getRuntime().exec(arrayOf(tool, "exec", desktopFilePath, downloadUrl))
                        if (kdeProcess.waitFor() == 0) return true
                    } catch (ignored: Exception) {}
                }

                // 5. Fallback: Parse the Desktop file manually if no standard tools are available
                val execCommand = parseDesktopExecCommand(desktopFilePath, downloadUrl)
                if (!execCommand.isNullOrEmpty()) {
                    Runtime.getRuntime().exec(execCommand)
                    return true
                }
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    private fun findDesktopFilePath(desktopApp: String): String? {
        val appName = if (desktopApp.endsWith(".desktop")) desktopApp else "$desktopApp.desktop"
        val userHome = System.getProperty("user.home") ?: ""

        val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$userHome/.local/share"
        val dataDirs = System.getenv("XDG_DATA_DIRS")?.takeIf { it.isNotBlank() } ?: "/usr/local/share:/usr/share"

        val searchDirs = mutableListOf<String>()
        searchDirs.add("$dataHome/applications")
        dataDirs.split(":").forEach { dir ->
            if (dir.isNotBlank()) searchDirs.add("$dir/applications")
        }

        // Add common Flatpak and Snap export paths
        searchDirs.addAll(
            listOf(
                "/var/lib/flatpak/exports/share/applications",
                "$userHome/.local/share/flatpak/exports/share/applications",
                "/var/lib/snapd/desktop/applications"
            )
        )

        for (dir in searchDirs) {
            val file = File(dir, appName)
            if (file.exists()) {
                return file.absolutePath
            }
        }
        return null
    }

    private fun parseDesktopExecCommand(desktopFilePath: String, downloadUrl: String): Array<String>? {
        val file = File(desktopFilePath)
        if (!file.exists()) return null

        var inDesktopEntrySection = false
        file.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[")) {
                    inDesktopEntrySection = trimmed == "[Desktop Entry]"
                    continue
                }

                if (inDesktopEntrySection && trimmed.startsWith("Exec=")) {
                    val rawExec = trimmed.substring(5).trim()
                    var replaced = rawExec
                    var codeSubstituted = false
                    for (code in listOf("%u", "%U", "%f", "%F")) {
                        if (replaced.contains(code)) {
                            replaced = replaced.replace(code, downloadUrl)
                            codeSubstituted = true
                        }
                    }
                    if (!codeSubstituted) {
                        replaced = "$replaced $downloadUrl"
                    }
                    // Remove remaining % codes as per spec
                    replaced = replaced.replace(Regex("%[a-zA-Z]"), "").trim()
                    return parseCommandLine(replaced)
                }
            }
        }
        return null
    }

    private fun parseCommandLine(cmd: String): Array<String> {
        val list = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var escapeNext = false
        for (ch in cmd) {
            if (escapeNext) {
                sb.append(ch)
                escapeNext = false
                continue
            }
            when (ch) {
                '\\' -> escapeNext = true
                '"' -> inQuotes = !inQuotes
                ' ' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else if (sb.isNotEmpty()) {
                        list.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            list.add(sb.toString())
        }
        return list.toTypedArray()
    }
}
