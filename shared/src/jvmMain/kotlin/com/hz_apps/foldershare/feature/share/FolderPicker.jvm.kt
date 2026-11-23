package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher

@Composable
actual fun rememberFolderPickerLauncher(
    onFolderPicked: (path: String, name: String) -> Unit
): FolderPickerLauncher {
    val currentOnFolderPicked by rememberUpdatedState(onFolderPicked)
    val launcher = rememberDirectoryPickerLauncher { directory: PlatformFile? ->
        if (directory != null) {
            val file = directory.file
            val path = file.absolutePath
            val name = file.name.ifEmpty { path }
            currentOnFolderPicked(path, name)
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
