package com.hz_apps.foldershare

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hz_apps.foldershare.ui.navigation.FolderShareAppContent
import com.hz_apps.foldershare.ui.theme.FolderShareTheme

@Composable
@Preview
fun App() {
    FolderShareTheme {
        FolderShareAppContent()
    }
}