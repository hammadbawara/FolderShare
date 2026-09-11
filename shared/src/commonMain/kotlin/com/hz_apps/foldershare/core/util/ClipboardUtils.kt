package com.hz_apps.foldershare.core.util

import androidx.compose.ui.platform.Clipboard

expect suspend fun setClipboardText(clipboard: Clipboard, text: String)
