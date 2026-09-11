package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.core.PlatformDirectory

interface FolderPickerLauncher {
    fun launch()
}

expect fun processFolderPick(directory: PlatformDirectory): Pair<String, String>

@Composable
fun rememberFolderPickerLauncher(
    onFolderPicked: (path: String, name: String) -> Unit
): FolderPickerLauncher {
    val launcher = rememberDirectoryPickerLauncher { directory: PlatformDirectory? ->
        if (directory != null) {
            val (path, name) = processFolderPick(directory)
            onFolderPicked(path, name)
        }
    }

    return remember(launcher) {
        object : FolderPickerLauncher {
            override fun launch() {
                launcher.launch()
            }
        }
    }
}
