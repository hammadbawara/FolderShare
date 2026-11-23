package com.hz_apps.foldershare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val platformFileHandler: PlatformFileHandler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        FileKit.init(this)
        AndroidContextProvider.applicationContext = applicationContext

        if (savedInstanceState == null) {
            CoroutineScope(Dispatchers.IO).launch {
                platformFileHandler.clearTemporaryDirectory()
            }
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CoroutineScope(Dispatchers.IO).launch {
                platformFileHandler.clearTemporaryDirectory()
            }
        }
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}