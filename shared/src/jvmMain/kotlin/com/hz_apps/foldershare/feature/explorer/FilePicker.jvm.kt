package com.hz_apps.foldershare.feature.explorer

import io.github.vinceglb.filekit.core.PlatformFile
import java.io.FileInputStream
import java.io.InputStream

actual fun platformFileSize(platformFile: PlatformFile): Long {
    return platformFile.file.length()
}

actual fun openPlatformFileStream(platformFile: PlatformFile): InputStream {
    return FileInputStream(platformFile.file)
}
