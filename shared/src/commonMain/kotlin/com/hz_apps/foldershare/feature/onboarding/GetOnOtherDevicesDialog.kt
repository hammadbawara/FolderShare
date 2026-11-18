package com.hz_apps.foldershare.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hz_apps.foldershare.core.util.AppLinks
import com.hz_apps.foldershare.core.util.setClipboardText
import com.hz_apps.foldershare.getPlatform
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.QrCodeBox
import com.hz_apps.foldershare.ui.components.tvFocusContainer
import kotlinx.coroutines.launch

@Composable
fun GetOnOtherDevicesDialog(
    onDismiss: () -> Unit
) {
    val isTv = remember { getPlatform().isTv }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var copiedFeedback by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(0.92f),
        title = {
            Text(
                text = "Get Folder Share on Other Devices",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusContainer()
                    .verticalScroll(rememberScrollState())
            ) {
                DownloadOnOtherDevicesContent(
                    isTv = isTv,
                    copiedFeedback = copiedFeedback,
                    onCopyUrl = {
                        coroutineScope.launch {
                            setClipboardText(clipboard, AppLinks.DOWNLOAD_URL)
                            copiedFeedback = true
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun DownloadOnOtherDevicesContent(
    isTv: Boolean,
    copiedFeedback: Boolean,
    onCopyUrl: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isTv) {
                "Scan with your Android Phone camera or visit the website on your Windows / Linux PC to install Folder Share:"
            } else {
                "Install Folder Share on your other devices (Android TV, Android Mobile, Windows, or Linux):"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        QrCodeBox(
            data = AppLinks.DOWNLOAD_URL,
            size = 164.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Download Website URL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = AppLinks.DOWNLOAD_DISPLAY_URL,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!isTv) {
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(onClick = onCopyUrl) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (copiedFeedback) "Download Link Copied!" else "Copy Download Link")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PlatformDownloadRow(
                icon = Icons.Default.PhoneAndroid,
                title = "Android Mobile & Tablet (.apk)",
                subtitle = "Scan the QR code above with your phone camera."
            )
            PlatformDownloadRow(
                icon = Icons.Default.Computer,
                title = "Windows (.exe / .msi / .zip) & Linux (.deb / .tar.gz)",
                subtitle = "Open ${AppLinks.DOWNLOAD_DISPLAY_URL} in your PC browser."
            )
            PlatformDownloadRow(
                icon = Icons.Default.Tv,
                title = "Android TV (.apk — Receiver)",
                subtitle = "Stream videos and browse folders on your TV."
            )
            PlatformDownloadRow(
                icon = Icons.Default.Language,
                title = "Any Web Browser",
                subtitle = "Open the Share Address URL (http://...) in any browser."
            )
        }
    }
}

@Composable
private fun PlatformDownloadRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
