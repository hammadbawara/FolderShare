package com.hz_apps.foldershare.feature.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import com.hz_apps.foldershare.core.util.setClipboardText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hz_apps.foldershare.core.server.ServerStatus
import androidx.compose.foundation.verticalScroll
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import com.hz_apps.foldershare.feature.share.components.BackgroundPermissionRationaleDialog
import com.hz_apps.foldershare.feature.share.components.BackgroundRestrictionBanner
import com.hz_apps.foldershare.feature.share.components.FolderConfigDialog
import com.hz_apps.foldershare.feature.share.components.SharedFolderCard
import com.hz_apps.foldershare.ui.components.ActionProgressDialog
import com.hz_apps.foldershare.ui.components.StatusBadge
import com.hz_apps.foldershare.ui.components.VerticalScrollbar
import com.hz_apps.foldershare.ui.components.rememberScrollbarAdapter
import com.hz_apps.foldershare.ui.components.tvFocusContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFolderForConfig by remember { mutableStateOf<FolderConfigEntity?>(null) }
    var newFolderForConfig by remember { mutableStateOf<FolderConfigEntity?>(null) }

    val scrollState = rememberScrollState()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberBackgroundPermissionLauncher { status ->
        viewModel.onPermissionsResult(status)
    }

    androidx.compose.runtime.LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    // Folder picker launcher - opens configuration dialog for new folder setup before inserting to DB
    val folderPicker = rememberFolderPickerLauncher { path, name ->
        val emoji = when {
            name.lowercase().contains("download") -> "📥"
            name.lowercase().contains("document") -> "📄"
            name.lowercase().contains("music") || name.lowercase().contains("audio") -> "🎵"
            name.lowercase().contains("video") || name.lowercase().contains("movie") -> "🎬"
            name.lowercase().contains("picture") || name.lowercase().contains("image") || name.lowercase().contains("photo") -> "🖼️"
            else -> "📁"
        }

        newFolderForConfig = FolderConfigEntity(
            name = name,
            path = path,
            iconEmoji = emoji,
            isReadAllowed = true,
            isWriteAllowed = false,
            isShared = true
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FolderShare",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            )
        },
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {

                // Compact Background Restriction Warning Banner
                AnimatedVisibility(
                    visible = uiState.isBackgroundRestricted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        BackgroundRestrictionBanner(
                            onFixClick = { viewModel.onFixBackgroundRestrictions() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Share Status & Primary URL Control Hero Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Top Row: Share Status Badge & Master Power Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = if (uiState.isSharing) "Sharing is On" else "Sharing is Off",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusBadge(isRunning = uiState.isSharing)
                            }

                            Switch(
                                checked = uiState.isSharing,
                                onCheckedChange = { viewModel.toggleSharing() }
                            )
                        }

                        // Multiple Share Addresses Section with Copy Buttons
                        AnimatedVisibility(
                            visible = uiState.isSharing && uiState.serverUrls.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                Text(
                                    text = "Share Addresses",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val displayUrls = uiState.serverUrls

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayUrls.forEach { url ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Language,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = url,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Medium
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            setClipboardText(clipboard, url)
                                                            snackbarHostState.showSnackbar("Share address copied to clipboard")
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Share Address",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Shared Folders Header & Add Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SHARED FOLDERS (${uiState.folders.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { folderPicker.launch() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Folder")
                    }
                }

                // Shared Folders List / Empty State with AnimatedContent
                if (uiState.isLoadingFolders && uiState.folders.isEmpty()) {
                    // While initial query from DB is resolving, avoid flashing the empty folders card
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp))
                } else {
                    AnimatedContent(
                        targetState = uiState.folders.isEmpty(),
                        label = "SharedFoldersContent"
                    ) { isEmpty ->
                        if (isEmpty) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No Shared Folders",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Click 'Add Folder' to share local directories over your Wi-Fi network.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { folderPicker.launch() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Folder")
                                    }
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                uiState.folders.forEach { folder ->
                                    SharedFolderCard(
                                        folder = folder,
                                        onClick = { selectedFolderForConfig = folder },
                                        onConfigureClick = { selectedFolderForConfig = folder },
                                        onToggleShareClick = {
                                            viewModel.updateFolderConfig(folder.copy(isShared = !folder.isShared))
                                        },
                                        onDeleteClick = { viewModel.deleteFolder(folder.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState)
            )
        }
    }

    // New Folder Setup Dialog (Shown after picking folder path)
    newFolderForConfig?.let { draftFolder ->
        FolderConfigDialog(
            folder = draftFolder,
            isNewFolder = true,
            onDismissRequest = { newFolderForConfig = null },
            onSaveConfig = { configuredFolder ->
                viewModel.addFolderConfig(configuredFolder)
            }
        )
    }

    // Existing Folder Configuration Dialog (Shown when clicking card or 3-dots configure)
    selectedFolderForConfig?.let { folder ->
        FolderConfigDialog(
            folder = folder,
            isNewFolder = false,
            onDismissRequest = { selectedFolderForConfig = null },
            onSaveConfig = { updated ->
                viewModel.updateFolderConfig(updated)
            },
            onDeleteFolder = { folderId ->
                viewModel.deleteFolder(folderId)
            }
        )
    }

    if (uiState.serverStatus == ServerStatus.STARTING) {
        ActionProgressDialog(
            title = "Starting Server",
            message = "Please wait while the sharing is starting...",
            onCancel = { },
            isCancellable = false
        )
    }

    if (uiState.serverStatus == ServerStatus.STOPPING) {
        ActionProgressDialog(
            title = "Stopping Server",
            message = "Please wait while the sharing is stopping...",
            onCancel = { },
            isCancellable = false
        )
    }

    if (uiState.showPermissionRationaleDialog) {
        BackgroundPermissionRationaleDialog(
            status = uiState.permissionStatus,
            onGrantClick = {
                permissionLauncher.launchPermissionRequest()
            },
            onSkipClick = {
                viewModel.onSkipPermissions()
            },
            onDismissRequest = {
                viewModel.onDismissPermissionDialog()
            }
        )
    }
}
