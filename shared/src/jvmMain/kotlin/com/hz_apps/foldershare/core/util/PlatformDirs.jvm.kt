package com.hz_apps.foldershare.core.util

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

actual object PlatformDirs {

    private val osName = System.getProperty("os.name")?.lowercase() ?: ""
    private val userHome = System.getProperty("user.home") ?: "."
    private val appDirName = if (System.getProperty("foldershare.alpha")?.toBoolean() == true) {
        "foldershare-alpha"
    } else {
        "foldershare"
    }

    private fun getEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    actual val dataDir: Path by lazy {
        val path = when {
            osName.contains("win") -> {
                val appData = getEnv("LOCALAPPDATA") ?: getEnv("APPDATA") ?: "$userHome\\AppData\\Local"
                "$appData\\$appDirName".toPath()
            }
            osName.contains("mac") -> {
                "$userHome/Library/Application Support/$appDirName".toPath()
            }
            else -> {
                // Linux / BSD / Unix
                val xdgDataHome = getEnv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                "$xdgDataHome/$appDirName".toPath()
            }
        }
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path
    }

    actual val logDir: Path by lazy {
        val path = when {
            osName.contains("win") -> {
                val appData = getEnv("LOCALAPPDATA") ?: getEnv("APPDATA") ?: "$userHome\\AppData\\Local"
                "$appData\\$appDirName\\logs".toPath()
            }
            osName.contains("mac") -> {
                "$userHome/Library/Logs/$appDirName".toPath()
            }
            else -> {
                // Linux / BSD / Unix
                val xdgStateHome = getEnv("XDG_STATE_HOME") ?: "$userHome/.local/state"
                "$xdgStateHome/$appDirName/logs".toPath()
            }
        }
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path
    }
}
