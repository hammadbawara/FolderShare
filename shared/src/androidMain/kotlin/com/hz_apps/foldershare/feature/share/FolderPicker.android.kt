package com.hz_apps.foldershare.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
actual fun rememberFolderPickerLauncher(
    onFolderPicked: (path: String, name: String) -> Unit
): FolderPickerLauncher {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val currentOnFolderPicked by rememberUpdatedState(onFolderPicked)

    if (showPicker) {
        LocalFolderPickerDialog(
            onFolderSelected = { path, name ->
                showPicker = false
                currentOnFolderPicked(path, name)
            },
            onDismissRequest = {
                showPicker = false
            }
        )
    }

    return remember {
        object : FolderPickerLauncher {
            override fun launch() {
                showPicker = true
            }
        }
    }
}
