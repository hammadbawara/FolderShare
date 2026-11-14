package com.hz_apps.foldershare.feature.explorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher as rememberFileKitLauncher
import io.github.vinceglb.filekit.name
import java.io.InputStream

interface FilePickerLauncher {
    fun launch()
}

class FileKitFileSource(
    private val platformFile: PlatformFile
) : LocalFileSource {
    override val name: String get() = platformFile.name
    override val size: Long get() = platformFileSize(platformFile)
    override fun openStream(): InputStream {
        return openPlatformFileStream(platformFile)
    }
}

expect fun platformFileSize(platformFile: PlatformFile): Long
expect fun openPlatformFileStream(platformFile: PlatformFile): InputStream

@Composable
fun rememberFilePickerLauncher(
    onFilesPicked: (fileSources: List<LocalFileSource>) -> Unit
): FilePickerLauncher {
    val launcher = rememberFileKitLauncher(
        mode = FileKitMode.Multiple()
    ) { files: List<PlatformFile>? ->
        if (!files.isNullOrEmpty()) {
            onFilesPicked(files.map { FileKitFileSource(it) })
        }
    }

    return remember(launcher) {
        object : FilePickerLauncher {
            override fun launch() {
                launcher.launch()
            }
        }
    }
}
