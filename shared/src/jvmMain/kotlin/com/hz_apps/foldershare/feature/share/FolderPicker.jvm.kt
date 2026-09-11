package com.hz_apps.foldershare.feature.share

import io.github.vinceglb.filekit.core.PlatformDirectory

actual fun processFolderPick(directory: PlatformDirectory): Pair<String, String> {
    val file = directory.file
    val path = file.absolutePath
    val name = file.name.ifEmpty { "Selected Folder" }
    return Pair(path, name)
}
