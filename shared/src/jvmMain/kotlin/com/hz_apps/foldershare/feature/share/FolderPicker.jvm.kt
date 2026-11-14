package com.hz_apps.foldershare.feature.share

import io.github.vinceglb.filekit.PlatformFile

actual fun processFolderPick(directory: PlatformFile): Pair<String, String> {
    val file = directory.file
    val path = file.absolutePath
    val name = file.name.ifEmpty { "Selected Folder" }
    return Pair(path, name)
}
