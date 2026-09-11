package com.hz_apps.foldershare.core.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun setClipboardText(clipboard: Clipboard, text: String) {
    clipboard.setClipEntry(ClipEntry(StringSelection(text)))
}
