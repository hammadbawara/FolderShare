package com.hz_apps.foldershare.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hz_apps.foldershare.feature.settings.components.SettingsItem
import com.hz_apps.foldershare.ui.components.VerticalScrollbar
import com.hz_apps.foldershare.ui.components.rememberScrollbarAdapter
import com.hz_apps.foldershare.ui.components.tvFocusContainer

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (!uiState.isLoaded) {
        Box(
            modifier = modifier.fillMaxSize().statusBarsPadding()
        )
        return
    }

    var deviceNameInput by remember(uiState.deviceName) { mutableStateOf(uiState.deviceName) }
    var usernameInput by remember(uiState.username) { mutableStateOf(uiState.username) }
    var passwordInput by remember(uiState.password) { mutableStateOf(uiState.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val isDeviceNameModified = deviceNameInput.trim() != uiState.deviceName

    if (uiState.showHttpsWarningDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHttpsWarningDialog() },
            title = {
                Text(
                    text = "Enable Secure HTTPS?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This feature encrypts your traffic over the local network, so it is recommended if you are on an untrusted network.\n\n⚠️ Warning: Browsers and third-party file managers will display a 'Secure Connection Error' or untrusted certificate warning due to self-signed TLS certificates."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmEnableHttps() }
                ) {
                    Text("Enable HTTPS")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissHttpsWarningDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 768.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .tvFocusContainer()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Device Name",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Custom name broadcasted to other devices on the local network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = deviceNameInput,
                    onValueChange = { deviceNameInput = it },
                    label = { Text("Device Name") },
                    placeholder = { Text(uiState.defaultDeviceName) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Devices, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.updateDeviceName(deviceNameInput)
                    },
                    enabled = isDeviceNameModified,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Device Name")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Secure HTTPS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (uiState.isHttpsEnabled) "HTTPS / SSL Encrypted traffic active" else "HTTP Protocol Active (Unencrypted local traffic)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = uiState.isHttpsEnabled,
                onCheckedChange = { viewModel.onHttpsToggleRequested(it) }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        SettingsItem(
            title = "Server Port",
            subtitle = "Port: ${uiState.serverPort}",
            icon = Icons.Default.Router
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Require Authentication",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (uiState.isAuthRequired) "Remote devices must authenticate to view shared files" else "Authentication is disabled (anyone on network can view files)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = uiState.isAuthRequired,
                onCheckedChange = { viewModel.updateAuthRequired(it) }
            )
        }

        if (uiState.isAuthRequired) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text("Host Username") },
                leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Host Password") },
                leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.updateCredentials(usernameInput, passwordInput)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Credentials")
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        SettingsItem(
            title = "About Folder Share",
            subtitle = "Version ${uiState.version}",
            icon = Icons.Default.Info
        )
    }

    VerticalScrollbar(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState)
    )
    }
}
