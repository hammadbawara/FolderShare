package com.hz_apps.foldershare.core.explorer.util

import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.TimeUnit

class DesktopDownloadDirectoryResolver(
    private val osNameProvider: () -> String = { System.getProperty("os.name") ?: "" },
    private val envProvider: (String) -> String? = { System.getenv(it) },
    private val userHomeProvider: () -> String = { System.getProperty("user.home") ?: "." },
    private val fileTextReader: (Path) -> String? = ::defaultReadFileText,
    private val commandRunner: (List<String>) -> String? = ::defaultRunCommand
) {

    fun resolve(): Path {
        val os = osNameProvider().lowercase()
        val baseDownloadsDir = when {
            os.contains("win") -> resolveWindowsDownloadsDir()
            os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("bsd") -> resolveLinuxDownloadsDir()
            else -> resolveDefaultFallbackDir()
        }
        return baseDownloadsDir / FOLDER_SHARE_DIR_NAME
    }

    private fun resolveLinuxDownloadsDir(): Path {
        val homeDir = envProvider("HOME")?.trim()?.takeIf { it.isNotEmpty() }
            ?: userHomeProvider().trim().ifEmpty { "." }

        // 1. Check XDG_DOWNLOAD_DIR environment variable directly
        envProvider("XDG_DOWNLOAD_DIR")
            ?.let { expandLinuxPath(it, homeDir) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toPath() }

        // 2. Parse $XDG_CONFIG_HOME/user-dirs.dirs (or ~/.config/user-dirs.dirs)
        val configHome = envProvider("XDG_CONFIG_HOME")
            ?.let { expandLinuxPath(it, homeDir) }
            ?.takeIf { it.isNotEmpty() }
            ?.toPath()
            ?: (homeDir.toPath() / ".config")

        val userDirsFile = configHome / "user-dirs.dirs"
        fileTextReader(userDirsFile)
            ?.let { parseXdgUserDirsDownloadPath(it, homeDir) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toPath() }

        // 3. Fallback to xdg-user-dir CLI if available
        commandRunner(listOf("xdg-user-dir", "DOWNLOAD"))
            ?.let { expandLinuxPath(it, homeDir) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toPath() }

        // 4. Standard fallback
        return homeDir.toPath() / "Downloads"
    }

    private fun resolveWindowsDownloadsDir(): Path {
        val userProfile = envProvider("USERPROFILE")?.trim()?.takeIf { it.isNotEmpty() }
            ?: userHomeProvider().trim().ifEmpty { "." }

        // 1. Query Windows Registry for FOLDERID_Downloads ({374DE290-123F-4565-9164-39C4925E467B})
        val regOutput = commandRunner(
            listOf(
                "reg",
                "query",
                WINDOWS_USER_SHELL_FOLDERS_KEY,
                "/v",
                WINDOWS_DOWNLOADS_FOLDER_GUID
            )
        )
        if (!regOutput.isNullOrBlank()) {
            parseWindowsRegistryDownloadPath(regOutput, userProfile)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it.toPath() }
        }

        // 2. Fallback to PowerShell Shell.Application Known Folder lookup
        val psOutput = commandRunner(
            listOf(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "(New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path"
            )
        )
        if (!psOutput.isNullOrBlank()) {
            expandWindowsPath(psOutput.trim(), userProfile)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it.toPath() }
        }

        // 3. Fallback to %USERPROFILE%\Downloads
        return userProfile.toPath() / "Downloads"
    }

    private fun resolveDefaultFallbackDir(): Path {
        val homeDir = userHomeProvider().trim().ifEmpty { "." }
        return homeDir.toPath() / "Downloads"
    }

    internal fun parseXdgUserDirsDownloadPath(content: String, homeDir: String): String? {
        var resolved: String? = null
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (!line.startsWith("XDG_DOWNLOAD_DIR")) continue

            val eqIndex = line.indexOf('=')
            if (eqIndex == -1) continue

            val key = line.substring(0, eqIndex).trim()
            if (key != "XDG_DOWNLOAD_DIR") continue

            var value = line.substring(eqIndex + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))
            ) {
                if (value.length >= 2) {
                    value = value.substring(1, value.length - 1)
                }
            }
            value = value.replace("\\\"", "\"").replace("\\\\", "\\")
            val expanded = expandLinuxPath(value, homeDir)
            if (expanded.isNotEmpty()) {
                resolved = expanded
            }
        }
        return resolved
    }

    private fun expandLinuxPath(rawPath: String, homeDir: String): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed == "\$HOME" || trimmed == "\${HOME}" || trimmed == "~" -> homeDir
            trimmed.startsWith("\$HOME/") -> homeDir + trimmed.removePrefix("\$HOME")
            trimmed.startsWith("\${HOME}/") -> homeDir + trimmed.removePrefix("\${HOME}")
            trimmed.startsWith("~/") -> homeDir + trimmed.removePrefix("~")
            else -> trimmed
                .replace("\${HOME}", homeDir)
                .replace("\$HOME", homeDir)
        }
    }

    internal fun parseWindowsRegistryDownloadPath(regOutput: String, userProfile: String): String? {
        val regex = Regex(
            pattern = """(?i)\{374DE290-123F-4565-9164-39C4925E467B\}\s+REG_(?:EXPAND_)?SZ\s+(.+)"""
        )
        for (line in regOutput.lineSequence()) {
            val match = regex.find(line.trim()) ?: continue
            val rawValue = match.groupValues[1].trim()
            val expanded = expandWindowsPath(rawValue, userProfile)
            if (!expanded.isNullOrEmpty()) {
                return expanded
            }
        }
        return null
    }

    private fun expandWindowsPath(rawPath: String, userProfile: String): String? {
        val trimmed = rawPath.trim().removeSurrounding("\"")
        if (trimmed.isEmpty()) return null

        val envPlaceholderRegex = Regex("""%([^%]+)%""")
        val expanded = envPlaceholderRegex.replace(trimmed) { matchResult ->
            val varName = matchResult.groupValues[1]
            val envValue = envProvider(varName)
                ?: envProvider(varName.uppercase())
                ?: if (varName.equals("USERPROFILE", ignoreCase = true)) userProfile else null
            envValue?.trim()?.takeIf { it.isNotEmpty() } ?: matchResult.value
        }

        // If an environment variable placeholder could not be resolved, treat as invalid
        if (envPlaceholderRegex.containsMatchIn(expanded)) {
            return null
        }
        return expanded
    }

    companion object {
        private const val FOLDER_SHARE_DIR_NAME = "FolderShare"
        internal const val WINDOWS_USER_SHELL_FOLDERS_KEY =
            """HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders"""
        internal const val WINDOWS_DOWNLOADS_FOLDER_GUID =
            "{374DE290-123F-4565-9164-39C4925E467B}"

        private fun defaultReadFileText(path: Path): String? {
            return try {
                val file = File(path.toString())
                if (file.isFile && file.canRead()) {
                    file.readText()
                } else {
                    null
                }
            } catch (ignored: Exception) {
                null
            }
        }

        private fun defaultRunCommand(command: List<String>): String? {
            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                val completed = process.waitFor(3, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    return null
                }
                if (process.exitValue() != 0) {
                    return null
                }
                process.inputStream.bufferedReader().readText().trim()
            } catch (ignored: Exception) {
                null
            }
        }
    }
}
