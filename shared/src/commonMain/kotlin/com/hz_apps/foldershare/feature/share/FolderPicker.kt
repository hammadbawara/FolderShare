package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher

interface FolderPickerLauncher {
    fun launch()
}

expect fun processFolderPick(directory: PlatformFile): Pair<String, String>

@Composable
fun rememberFolderPickerLauncher(
    onFolderPicked: (path: String, name: String) -> Unit
): FolderPickerLauncher {
    val launcher = rememberDirectoryPickerLauncher { directory: PlatformFile? ->
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
