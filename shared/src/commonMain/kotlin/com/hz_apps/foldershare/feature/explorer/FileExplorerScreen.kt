package com.hz_apps.foldershare.feature.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hz_apps.foldershare.core.explorer.model.FileOpenOption
import com.hz_apps.foldershare.feature.explorer.components.BackgroundContextMenu
import com.hz_apps.foldershare.feature.explorer.components.BreadcrumbsBar
import com.hz_apps.foldershare.feature.explorer.components.CreateFileDialog
import com.hz_apps.foldershare.feature.explorer.components.CreateFolderDialog
import com.hz_apps.foldershare.feature.explorer.components.DeleteConfirmDialog
import com.hz_apps.foldershare.feature.explorer.components.ExplorerMenuContent
import com.hz_apps.foldershare.feature.explorer.components.FilePropertiesDialog
import com.hz_apps.foldershare.feature.explorer.components.FileTransferProgressDialog
import com.hz_apps.foldershare.feature.explorer.components.OpenFileOptionsDialog
import com.hz_apps.foldershare.feature.explorer.components.RemoteFileItemRow
import com.hz_apps.foldershare.feature.explorer.components.RenameDialog
import com.hz_apps.foldershare.feature.explorer.components.SortOptionsDialog
import com.hz_apps.foldershare.ui.components.BackHandler
import com.hz_apps.foldershare.ui.components.CascadingDropdownMenu
import com.hz_apps.foldershare.ui.components.VerticalScrollbar
import com.hz_apps.foldershare.ui.components.rememberRetainedFocusState
import com.hz_apps.foldershare.ui.components.rememberScrollbarAdapter
import com.hz_apps.foldershare.ui.components.tvFocusContainer
import kotlinx.coroutines.launch

@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToFolder: ((String) -> Unit)? = null,

) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val device = uiState.device
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showBackgroundMenu by remember { mutableStateOf(false) }
    var backgroundMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showTopBarMenu by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val filePickerLauncher = rememberFilePickerLauncher { localFileSources ->
        viewModel.uploadFiles(localFileSources)
    }

    val retainedFocusState = rememberRetainedFocusState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.currentPath, uiState.files) {
        if (uiState.files.isNotEmpty()) {
            retainedFocusState.restoreFocus(
                itemKeys = uiState.files.map { it.path },
                listState = listState,
                fallbackKey = uiState.targetFocusPath,
                defaultIndex = 0
            )
        }
    }

    BackHandler(enabled = true) {
        when {
            showTopBarMenu -> showTopBarMenu = false
            showBackgroundMenu -> showBackgroundMenu = false
            uiState.transferProgress != null -> viewModel.cancelTransfer()
            uiState.showCreateFolderDialog -> viewModel.dismissCreateFolderDialog()
            uiState.showCreateFileDialog -> viewModel.dismissCreateFileDialog()
            uiState.showSortDialog -> viewModel.dismissSortDialog()
            uiState.selectedFileForProperties != null -> viewModel.dismissPropertiesDialog()
            uiState.selectedFileForRename != null -> viewModel.dismissRenameDialog()
            uiState.selectedFileForDelete != null -> viewModel.dismissDeleteDialog()
            uiState.selectedFileForOpenOptions != null -> viewModel.dismissOpenFileOptionsDialog()
            else -> {
                if (onNavigateToFolder != null) {
                    onBackClick()
                } else if (!viewModel.navigateUp()) {
                    onBackClick()
                }
            }
        }
    }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.name ?: "Remote Device Explorer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${device.hostAddress}:${device.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!uiState.isCurrentFolderWriteAllowed) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Read Only",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (onNavigateToFolder != null) {
                                onBackClick()
                            } else if (!viewModel.navigateUp()) {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopBarMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }

                        CascadingDropdownMenu(
                            expanded = showTopBarMenu,
                            onDismissRequest = { showTopBarMenu = false }
                        ) {
                            ExplorerMenuContent(
                                sortOption = uiState.sortOption,
                                onRefresh = { viewModel.refresh() },
                                onToggleShowHiddenFiles = { viewModel.toggleShowHiddenFiles() },
                                onUpdateSortOption = { option -> viewModel.updateSortOption(option) },
                                onUploadFile = { filePickerLauncher.launch() },
                                onCreateFolder = { viewModel.showCreateFolderDialog() },
                                onCreateFile = { viewModel.showCreateFileDialog() },
                                isWriteAllowed = uiState.isCurrentFolderWriteAllowed,
                                isUploadEnabled = !uiState.isLoading && !uiState.isActionLoading && uiState.transferProgress == null,
                                isCreateEnabled = !uiState.isLoading && !uiState.isActionLoading
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .tvFocusContainer()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Action Loading Bar
            if (uiState.isActionLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Breadcrumbs Path Bar
            BreadcrumbsBar(
                pathSegments = uiState.pathSegments,
                onSegmentClick = { segment ->
                    if (onNavigateToFolder != null) {
                        onNavigateToFolder(segment.fullPath)
                    } else {
                        viewModel.loadDirectory(segment.fullPath)
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Main Content Area with Desktop Max Width limit
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null && !change.isConsumed) {
                                        val xDp = with(density) { change.position.x.toDp() }
                                        val yDp = with(density) { change.position.y.toDp() }
                                        backgroundMenuOffset = DpOffset(xDp, yDp)
                                        change.consume()
                                        showBackgroundMenu = true
                                    }
                                }
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1000.dp)
                        .align(Alignment.TopCenter)
                ) {
                    when {
                        uiState.isLoading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Connecting to device & fetching files...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        uiState.errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Failed to load directory",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.errorMessage ?: "Unknown error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Retry Connection")
                                }
                            }
                        }

                        uiState.files.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Directory is Empty",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No files or subfolders found in this directory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = uiState.files,
                                    key = { _, file -> file.path }
                                ) { index, file ->
                                    val itemFocusRequester = retainedFocusState.getRequester(file.path)
                                    RemoteFileItemRow(
                                        file = file,
                                        focusRequester = itemFocusRequester,
                                        isFirstItem = index == 0,
                                        isLastItem = index == uiState.files.lastIndex,
                                        onFocused = {
                                            retainedFocusState.onItemFocused(file.path)
                                            viewModel.setFocusedPath(file.path)
                                        },
                                        onOpen = {
                                            retainedFocusState.onItemFocused(file.path)
                                            viewModel.setFocusedPath(file.path)
                                            if (file.isDirectory) {
                                                if (onNavigateToFolder != null) {
                                                    onNavigateToFolder(file.path)
                                                } else {
                                                    viewModel.navigateToFolder(file)
                                                }
                                            } else {
                                                viewModel.showOpenFileOptionsDialog(file)
                                            }
                                        },
                                        onSaveToDevice = { viewModel.saveToDevice(file) },
                                        onShare = { viewModel.shareFile(file) },
                                        onRename = { viewModel.showRenameDialog(file) },
                                        onDelete = { viewModel.showDeleteDialog(file) },
                                        onProperties = { viewModel.showPropertiesDialog(file) },
                                        isWriteAllowed = uiState.isCurrentFolderWriteAllowed
                                    )
                                    if (index < uiState.files.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 56.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty space background right-click context menu (positioned at click location)
                Box(
                    modifier = Modifier
                        .offset(x = backgroundMenuOffset.x, y = backgroundMenuOffset.y)
                        .size(0.dp)
                ) {
                    BackgroundContextMenu(
                        expanded = showBackgroundMenu,
                        onDismissRequest = { showBackgroundMenu = false },
                        sortOption = uiState.sortOption,
                        onRefresh = { viewModel.refresh() },
                        onToggleShowHiddenFiles = { viewModel.toggleShowHiddenFiles() },
                        onUpdateSortOption = { option -> viewModel.updateSortOption(option) },
                        onUploadFile = { filePickerLauncher.launch() },
                        onCreateFolder = { viewModel.showCreateFolderDialog() },
                        onCreateFile = { viewModel.showCreateFileDialog() },
                        isWriteAllowed = uiState.isCurrentFolderWriteAllowed,
                        offset = DpOffset.Zero
                    )
                }

                if (uiState.files.isNotEmpty()) {
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState)
                    )
                }
            }
        }

        // Render Dialogs
        if (uiState.showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { viewModel.dismissCreateFolderDialog() },
                onCreate = { folderName -> viewModel.createFolder(folderName) }
            )
        }

        if (uiState.showCreateFileDialog) {
            CreateFileDialog(
                onDismiss = { viewModel.dismissCreateFileDialog() },
                onCreate = { fileName, content -> viewModel.createFile(fileName, content) }
            )
        }

        if (uiState.showSortDialog) {
            SortOptionsDialog(
                currentOption = uiState.sortOption,
                onDismiss = { viewModel.dismissSortDialog() },
                onApply = { option -> viewModel.updateSortOption(option) }
            )
        }

        uiState.selectedFileForRename?.let { file ->
            RenameDialog(
                file = file,
                onDismiss = { viewModel.dismissRenameDialog() },
                onRename = { newName -> viewModel.renameFile(file, newName) }
            )
        }

        uiState.selectedFileForDelete?.let { file ->
            DeleteConfirmDialog(
                file = file,
                onDismiss = { viewModel.dismissDeleteDialog() },
                onConfirmDelete = { viewModel.deleteFile(file) }
            )
        }

        uiState.selectedFileForProperties?.let { file ->
            FilePropertiesDialog(
                file = file,
                downloadUrl = uiState.propertiesDownloadUrl.orEmpty(),
                onDismiss = { viewModel.dismissPropertiesDialog() },
                onCopyLink = { url -> viewModel.copyToClipboard(url) }
            )
        }

        uiState.selectedFileForOpenOptions?.let { file ->
            OpenFileOptionsDialog(
                file = file,
                onDismiss = { viewModel.dismissOpenFileOptionsDialog() },
                onConfirm = { option ->
                    when (option) {
                        FileOpenOption.DIRECT_OPEN -> viewModel.directOpenFile(file)
                        FileOpenOption.TEMPORARY_SAVE_AND_OPEN -> viewModel.tempSaveAndOpenFile(file)
                    }
                }
            )
        }

        uiState.transferProgress?.let { progress ->
            FileTransferProgressDialog(
                transferProgress = progress,
                targetDeviceName = uiState.device?.name,
                onCancel = { viewModel.cancelTransfer() },
                onRetry = { viewModel.retryTransfer() }
            )
        }
    }
}
