package com.hz_apps.foldershare.feature.devices

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.feature.devices.components.ConnectingDialog
import com.hz_apps.foldershare.feature.devices.components.ConnectionErrorDialog
import com.hz_apps.foldershare.feature.devices.components.DeviceAuthDialog
import com.hz_apps.foldershare.feature.devices.components.DeviceItemRow
import com.hz_apps.foldershare.feature.devices.components.DiscoveryAnimation
import com.hz_apps.foldershare.feature.devices.components.ManualIpDialog
import com.hz_apps.foldershare.ui.components.VerticalScrollbar
import com.hz_apps.foldershare.ui.components.rememberRetainedFocusState
import com.hz_apps.foldershare.ui.components.rememberScrollbarAdapter
import com.hz_apps.foldershare.ui.components.retainedFocusItem
import com.hz_apps.foldershare.ui.components.tvFocusContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onDeviceClick: (DeviceItem) -> Unit = {},
    onNavigateToFileExplorer: (RemoteTargetDevice, String?, String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val retainedFocusState = rememberRetainedFocusState()

    LaunchedEffect(uiState.devices) {
        if (uiState.devices.isNotEmpty()) {
            retainedFocusState.restoreFocus(
                itemKeys = uiState.devices.map { it.id },
                listState = listState,
                defaultIndex = 0
            )
        }
    }

    LaunchedEffect(uiState.connectionErrorMessage) {
        val errorMsg = uiState.connectionErrorMessage
        if (!errorMsg.isNullOrBlank() && !uiState.isErrorDialogOpen) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearConnectionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nearby Devices",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    ScanControlChip(
                        isScanning = uiState.isSearching,
                        onStartScan = { viewModel.startScanning() },
                        onStopScan = { viewModel.stopScanning() }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.showManualIpDialog() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "Connect manually"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 768.dp)
                    .fillMaxWidth()
                    .tvFocusContainer()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {

                AnimatedContent(
                    targetState = uiState.devices.isEmpty(),
                    label = "DevicesScreenContentAnimation"
                ) { isEmpty ->
                    if (isEmpty) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp)
                        ) {
                            DiscoveryAnimation(
                                isScanning = uiState.isSearching,
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            AnimatedContent(
                                targetState = uiState.isSearching,
                                label = "ScanningStateTransition"
                            ) { isSearching ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isSearching) {
                                        Text(
                                            text = "Looking for devices...",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Make sure the other device has Folder Share open and is connected to the same Wi-Fi network.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.showManualIpDialog() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lan,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connect manually")
                                        }
                                    } else {
                                        Text(
                                            text = "Scanning paused",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Scanning was stopped. Click 'Scan again' to search for devices on your network.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        FilledTonalButton(
                                            onClick = { viewModel.startScanning() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Scan again")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.showManualIpDialog() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lan,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connect manually")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .tvFocusContainer()
                        ) {
                            itemsIndexed(uiState.devices, key = { _, dev -> dev.id }) { index, device ->
                                DeviceItemRow(
                                    deviceName = device.name,
                                    category = device.category,
                                    platformDisplayName = device.platformDisplayName,
                                    hostAddress = device.hostAddress,
                                    port = device.port,
                                    endpoints = device.endpoints,
                                    isAuthRequired = device.isAuthRequired,
                                    isOnline = device.isOnline,
                                    isThisDevice = device.isThisDevice,
                                    networkLinksCount = device.networkLinksCount,
                                    isConnecting = uiState.connectingDeviceId == device.id,
                                    onClick = {
                                        retainedFocusState.onItemFocused(device.id)
                                        onDeviceClick(device)
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .retainedFocusItem(device.id, retainedFocusState)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.devices.isNotEmpty()) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }

            // Modal Dialogs
            if (uiState.isConnecting) {
                ConnectingDialog(
                    device = uiState.connectingDevice,
                    onCancel = { viewModel.cancelConnection() }
                )
            }

            if (uiState.isAuthDialogOpen) {
                DeviceAuthDialog(
                    device = uiState.authDevice,
                    username = uiState.authUsernameInput,
                    password = uiState.authPasswordInput,
                    isPasswordVisible = uiState.isAuthPasswordVisible,
                    isAuthenticating = uiState.isAuthenticating,
                    errorMessage = uiState.authErrorMessage,
                    onUsernameChange = { viewModel.updateAuthUsername(it) },
                    onPasswordChange = { viewModel.updateAuthPassword(it) },
                    onTogglePasswordVisibility = { viewModel.toggleAuthPasswordVisibility() },
                    onDismiss = { viewModel.hideAuthDialog() },
                    onSubmit = { viewModel.submitAuthentication(onNavigateToFileExplorer) }
                )
            }

            if (uiState.isManualIpDialogOpen) {
                ManualIpDialog(
                    host = uiState.manualHostInput,
                    deviceName = uiState.manualDeviceNameInput,
                    isConnecting = uiState.isConnectingManual,
                    errorMessage = uiState.manualConnectionError,
                    onHostChange = { viewModel.updateManualHost(it) },
                    onDeviceNameChange = { viewModel.updateManualDeviceName(it) },
                    onDismiss = { viewModel.hideManualIpDialog() },
                    onConnect = {
                        viewModel.connectToManualIp(
                            onNavigateToFileExplorer = onNavigateToFileExplorer
                        )
                    }
                )
            }

            if (uiState.isErrorDialogOpen) {
                ConnectionErrorDialog(
                    device = uiState.errorDialogDevice,
                    errorMessage = uiState.errorDialogMessage.orEmpty(),
                    onDismiss = { viewModel.hideErrorDialog() },
                    onRetry = uiState.errorDialogDevice?.let { dev ->
                        { onDeviceClick(dev) }
                    },
                    onConnectViaIp = { viewModel.showManualIpDialog() }
                )
            }
        }
    }
}

@Composable
private fun ScanControlChip(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isScanning) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "ScanChipContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isScanning) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "ScanChipContentColor"
    )

    Surface(
        onClick = if (isScanning) onStopScan else onStartScan,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(36.dp)
    ) {
        AnimatedContent(
            targetState = isScanning,
            label = "ScanControlChipContent"
        ) { scanning ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scanning",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop scanning",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

