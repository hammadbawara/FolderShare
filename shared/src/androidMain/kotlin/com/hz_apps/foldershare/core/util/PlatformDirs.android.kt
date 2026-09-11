package com.hz_apps.foldershare.core.util

import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import okio.Path
import okio.Path.Companion.toOkioPath

actual object PlatformDirs {
    private val context get() = requireNotNull(AndroidContextProvider.applicationContext) {
        "AndroidContextProvider.applicationContext must be initialized"
    }

    actual val dataDir: Path
        get() = context.filesDir.toOkioPath()

    actual val logDir: Path
        get() = java.io.File(context.cacheDir, "logs").apply { mkdirs() }.toOkioPath()
}
