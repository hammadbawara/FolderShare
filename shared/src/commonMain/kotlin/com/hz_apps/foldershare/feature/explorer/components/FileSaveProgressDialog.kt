package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hz_apps.foldershare.core.transfer.FileTransferProgress

@Composable
fun FileSaveProgressDialog(
    saveProgress: FileTransferProgress,
    targetDeviceName: String? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    FileTransferProgressDialog(
        transferProgress = saveProgress,
        targetDeviceName = targetDeviceName,
        onCancel = onCancel,
        modifier = modifier
    )
}
