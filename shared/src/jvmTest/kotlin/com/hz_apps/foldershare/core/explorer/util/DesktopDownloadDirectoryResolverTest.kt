package com.hz_apps.foldershare.core.explorer.util

import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDownloadDirectoryResolverTest {

    @Test
    fun `linux uses XDG_DOWNLOAD_DIR environment variable when set`() {
        val env = mapOf(
            "HOME" to "/home/alice",
            "XDG_DOWNLOAD_DIR" to "/mnt/storage/Downloads"
        )
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { env[it] },
            userHomeProvider = { "/home/alice" },
            fileTextReader = { null },
            commandRunner = { null }
        )

        assertEquals("/mnt/storage/Downloads/FolderShare".toPath(), resolver.resolve())
    }

    @Test
    fun `linux expands HOME in XDG_DOWNLOAD_DIR environment variable`() {
        val env = mapOf(
            "HOME" to "/home/alice",
            "XDG_DOWNLOAD_DIR" to "\$HOME/MyDownloads"
        )
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { env[it] },
            userHomeProvider = { "/home/alice" },
            fileTextReader = { null },
            commandRunner = { null }
        )

        assertEquals("/home/alice/MyDownloads/FolderShare".toPath(), resolver.resolve())
    }

    @Test
    fun `linux parses user-dirs_dirs from XDG_CONFIG_HOME`() {
        val env = mapOf(
            "HOME" to "/home/alice",
            "XDG_CONFIG_HOME" to "/home/alice/.custom-config"
        )
        val expectedConfigPath = "/home/alice/.custom-config/user-dirs.dirs".toPath()
        val userDirsContent = """
            # This file is written by xdg-user-dirs-update
            XDG_DESKTOP_DIR="${'$'}HOME/Desktop"
            XDG_DOWNLOAD_DIR="${'$'}HOME/Descargas"
            XDG_DOCUMENTS_DIR="${'$'}HOME/Documents"
        """.trimIndent()

        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { env[it] },
            userHomeProvider = { "/home/alice" },
            fileTextReader = { path -> if (path == expectedConfigPath) userDirsContent else null },
            commandRunner = { null }
        )

        assertEquals("/home/alice/Descargas/FolderShare".toPath(), resolver.resolve())
    }

    @Test
    fun `linux falls back to xdg-user-dir command when env and config file are absent`() {
        val env = mapOf("HOME" to "/home/alice")
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { env[it] },
            userHomeProvider = { "/home/alice" },
            fileTextReader = { null },
            commandRunner = { cmd ->
                if (cmd == listOf("xdg-user-dir", "DOWNLOAD")) "/home/alice/CLI_Downloads" else null
            }
        )

        assertEquals("/home/alice/CLI_Downloads/FolderShare".toPath(), resolver.resolve())
    }

    @Test
    fun `linux falls back to HOME Downloads when all environment lookups fail`() {
        val env = mapOf("HOME" to "/home/alice")
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { env[it] },
            userHomeProvider = { "/home/alice" },
            fileTextReader = { null },
            commandRunner = { null }
        )

        assertEquals("/home/alice/Downloads/FolderShare".toPath(), resolver.resolve())
    }

    @Test
    fun `windows resolves Downloads path from registry and expands USERPROFILE`() {
        val env = mapOf("USERPROFILE" to """C:\Users\Alice""")
        val regOutput = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders
                {374DE290-123F-4565-9164-39C4925E467B}    REG_EXPAND_SZ    %USERPROFILE%\Downloads
        """.trimIndent()

        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Windows 11" },
            envProvider = { env[it] },
            userHomeProvider = { """C:\Users\Alice""" },
            fileTextReader = { null },
            commandRunner = { cmd ->
                if (cmd.firstOrNull() == "reg") regOutput else null
            }
        )

        assertEquals("""C:\Users\Alice\Downloads""".toPath() / "FolderShare", resolver.resolve())
    }

    @Test
    fun `windows resolves custom relocated Downloads path from registry`() {
        val env = mapOf("USERPROFILE" to """C:\Users\Alice""")
        val regOutput = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders
                {374DE290-123F-4565-9164-39C4925E467B}    REG_SZ    D:\CustomDownloads
        """.trimIndent()

        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Windows 10" },
            envProvider = { env[it] },
            userHomeProvider = { """C:\Users\Alice""" },
            fileTextReader = { null },
            commandRunner = { cmd ->
                if (cmd.firstOrNull() == "reg") regOutput else null
            }
        )

        assertEquals("""D:\CustomDownloads""".toPath() / "FolderShare", resolver.resolve())
    }

    @Test
    fun `windows falls back to PowerShell Known Folder when registry query fails`() {
        val env = mapOf("USERPROFILE" to """C:\Users\Alice""")
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Windows 11" },
            envProvider = { env[it] },
            userHomeProvider = { """C:\Users\Alice""" },
            fileTextReader = { null },
            commandRunner = { cmd ->
                when (cmd.firstOrNull()) {
                    "reg" -> null
                    "powershell" -> """E:\MediaDownloads"""
                    else -> null
                }
            }
        )

        assertEquals("""E:\MediaDownloads""".toPath() / "FolderShare", resolver.resolve())
    }

    @Test
    fun `windows falls back to USERPROFILE Downloads when registry and PowerShell fail`() {
        val env = mapOf("USERPROFILE" to """C:\Users\Bob""")
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Windows 11" },
            envProvider = { env[it] },
            userHomeProvider = { """C:\Users\Default""" },
            fileTextReader = { null },
            commandRunner = { null }
        )

        assertEquals("""C:\Users\Bob""".toPath() / "Downloads" / "FolderShare", resolver.resolve())
    }

    @Test
    fun `jvmPlatformFileHandler uses injected resolver for default and destination paths`() {
        val resolver = DesktopDownloadDirectoryResolver(
            osNameProvider = { "Linux" },
            envProvider = { if (it == "XDG_DOWNLOAD_DIR") "/custom/dl" else null },
            userHomeProvider = { "/home/user" },
            fileTextReader = { null },
            commandRunner = { null }
        )
        val handler = JvmPlatformFileHandler(downloadDirectoryResolver = resolver)

        assertEquals("/custom/dl/FolderShare".toPath(), handler.getDefaultDownloadDirectory())
        assertEquals(
            "/custom/dl/FolderShare/video.mp4".toPath(),
            handler.getDownloadDestinationPath("video.mp4")
        )
    }
}
