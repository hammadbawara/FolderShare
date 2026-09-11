package com.hz_apps.foldershare.core.util

import okio.Path

expect object PlatformDirs {
    val dataDir: Path
    val logDir: Path
}
