package com.hz_apps.foldershare.core.util

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

actual suspend fun setClipboardText(clipboard: Clipboard, text: String) {
    val clipData = ClipData.newPlainText("FolderShare", text)
    clipboard.setClipEntry(clipData.toClipEntry())
}
