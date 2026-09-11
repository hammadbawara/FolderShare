package com.hz_apps.foldershare.feature.devices.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun ManualIpDialog(
    host: String,
    deviceName: String,
    isConnecting: Boolean,
    errorMessage: String?,
    onHostChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {

    AppAlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = {
            Text(
                text = "Connect to Device by IP",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the IP address or host of the remote Folder Share device. Port and connection security (HTTP/HTTPS) will be detected automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("IP Address / Hostname") },
                    placeholder = { Text("e.g. 192.168.1.50") },
                    singleLine = true,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

//                OutlinedTextField(
//                    value = deviceName,
//                    onValueChange = onDeviceNameChange,
//                    label = { Text("Device Name (Optional)") },
//                    placeholder = { Text("e.g. Remote Host") },
//                    singleLine = true,
//                    enabled = !isConnecting,
//                    modifier = Modifier.fillMaxWidth()
//                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConnect,
                enabled = !isConnecting && host.isNotBlank()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

