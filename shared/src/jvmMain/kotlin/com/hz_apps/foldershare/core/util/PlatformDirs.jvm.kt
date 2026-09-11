package com.hz_apps.foldershare.core.util

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

actual object PlatformDirs {

    private val osName = System.getProperty("os.name")?.lowercase() ?: ""
    private val userHome = System.getProperty("user.home") ?: "."

    private fun getEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    actual val dataDir: Path by lazy {
        val path = when {
            osName.contains("win") -> {
                val appData = getEnv("LOCALAPPDATA") ?: getEnv("APPDATA") ?: "$userHome\\AppData\\Local"
                "$appData\\foldershare".toPath()
            }
            osName.contains("mac") -> {
                "$userHome/Library/Application Support/foldershare".toPath()
            }
            else -> {
                // Linux / BSD / Unix
                val xdgDataHome = getEnv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                "$xdgDataHome/foldershare".toPath()
            }
        }
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path
    }

    actual val logDir: Path by lazy {
        val path = when {
            osName.contains("win") -> {
                val appData = getEnv("LOCALAPPDATA") ?: getEnv("APPDATA") ?: "$userHome\\AppData\\Local"
                "$appData\\foldershare\\logs".toPath()
            }
            osName.contains("mac") -> {
                "$userHome/Library/Logs/foldershare".toPath()
            }
            else -> {
                // Linux / BSD / Unix
                val xdgStateHome = getEnv("XDG_STATE_HOME") ?: "$userHome/.local/state"
                "$xdgStateHome/foldershare/logs".toPath()
            }
        }
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path
    }
}
