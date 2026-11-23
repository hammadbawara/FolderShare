package com.hz_apps.foldershare.feature.share

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hz_apps.foldershare.feature.share.components.getFolderVectorIcon
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.tvFocusContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class AndroidStorageVolume(
    val label: String,
    val rootPath: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean
)

internal data class FolderBreadcrumb(
    val name: String,
    val path: String
)

private data class DirectoryListing(
    val directories: List<File> = emptyList(),
    val fileCount: Int = 0,
    val isReadable: Boolean = true,
    val isLoading: Boolean = true
)

internal fun getMountedStorageVolumes(context: Context): List<AndroidStorageVolume> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    val volumesByPath = LinkedHashMap<String, AndroidStorageVolume>()

    fun addVolume(path: String, label: String, isPrimary: Boolean, isRemovable: Boolean) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return
        val normalized = normalizeDirectoryPath(dir.absolutePath)
        if (!volumesByPath.containsKey(normalized)) {
            volumesByPath[normalized] = AndroidStorageVolume(
                label = label,
                rootPath = normalized,
                isPrimary = isPrimary,
                isRemovable = isRemovable
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val systemVolumes = storageManager?.storageVolumes.orEmpty()
        for (volume in systemVolumes) {
            if (volume.state != Environment.MEDIA_MOUNTED) continue
            val dir = volume.directory ?: continue
            if (!dir.exists() || !dir.isDirectory) continue
            val label = if (volume.isPrimary) {
                "Internal Storage"
            } else {
                volume.getDescription(context)?.takeIf { it.isNotBlank() }
                    ?: dir.name.ifBlank { "External Storage" }
            }
            addVolume(
                path = dir.absolutePath,
                label = label,
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable
            )
        }
    }

    if (volumesByPath.isEmpty()) {
        @Suppress("DEPRECATION")
        val primaryDir = Environment.getExternalStorageDirectory()
        if (primaryDir != null && primaryDir.exists() && primaryDir.isDirectory) {
            addVolume(
                path = primaryDir.absolutePath,
                label = "Internal Storage",
                isPrimary = true,
                isRemovable = false
            )
        }

        val allStorageVolumes = storageManager?.storageVolumes.orEmpty()
        val appExternalDirs = context.getExternalFilesDirs(null).filterNotNull()
        for (appDir in appExternalDirs) {
            val fullPath = appDir.absolutePath
            val markerIndex = fullPath.indexOf("/Android/data")
            if (markerIndex <= 0) continue
            val rootPath = fullPath.substring(0, markerIndex)
            val rootFile = File(rootPath)
            val normalizedRoot = normalizeDirectoryPath(rootFile.absolutePath)
            if (volumesByPath.containsKey(normalizedRoot)) continue
            if (!rootFile.exists() || !rootFile.isDirectory) continue

            val matchedVolume = allStorageVolumes.firstOrNull { sv ->
                val uuid = sv.uuid
                !uuid.isNullOrBlank() && rootFile.name.equals(uuid, ignoreCase = true)
            }
            val label = matchedVolume?.getDescription(context)?.takeIf { it.isNotBlank() }
                ?: if (matchedVolume?.isPrimary == true) {
                    "Internal Storage"
                } else {
                    "SD Card (${rootFile.name})"
                }
            addVolume(
                path = rootFile.absolutePath,
                label = label,
                isPrimary = matchedVolume?.isPrimary == true,
                isRemovable = matchedVolume?.isRemovable ?: true
            )
        }
    }

    if (volumesByPath.isEmpty()) {
        @Suppress("DEPRECATION")
        val fallbackDir = Environment.getExternalStorageDirectory()
        val fallbackPath = normalizeDirectoryPath(fallbackDir?.absolutePath ?: "/storage/emulated/0")
        volumesByPath[fallbackPath] = AndroidStorageVolume(
            label = "Internal Storage",
            rootPath = fallbackPath,
            isPrimary = true,
            isRemovable = false
        )
    }

    return volumesByPath.values.sortedByDescending { it.isPrimary }
}

internal fun buildBreadcrumbs(
    volume: AndroidStorageVolume,
    currentDirPath: String
): List<FolderBreadcrumb> {
    val rootNorm = normalizeDirectoryPath(volume.rootPath)
    val currentNorm = normalizeDirectoryPath(currentDirPath)
    val breadcrumbs = mutableListOf(FolderBreadcrumb(name = volume.label, path = rootNorm))

    if (currentNorm == rootNorm) {
        return breadcrumbs
    }

    val rootPrefix = if (rootNorm == "/") "/" else "$rootNorm/"
    if (!currentNorm.startsWith(rootPrefix)) {
        return breadcrumbs
    }

    val relative = currentNorm.removePrefix(rootPrefix)
    if (relative.isEmpty()) {
        return breadcrumbs
    }

    var accumulated = if (rootNorm == "/") "" else rootNorm
    for (segment in relative.split('/')) {
        if (segment.isEmpty()) continue
        accumulated = "$accumulated/$segment"
        breadcrumbs.add(FolderBreadcrumb(name = segment, path = accumulated))
    }
    return breadcrumbs
}

internal fun resolveFolderDisplayName(
    volume: AndroidStorageVolume,
    currentDirPath: String
): String {
    val rootNorm = normalizeDirectoryPath(volume.rootPath)
    val currentNorm = normalizeDirectoryPath(currentDirPath)
    if (currentNorm == rootNorm) {
        return volume.label
    }
    return File(currentNorm).name.ifBlank { volume.label }
}

private fun normalizeDirectoryPath(path: String): String {
    if (path == "/") return "/"
    return path.trimEnd('/').ifEmpty { "/" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFolderPickerDialog(
    onFolderSelected: (path: String, name: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val volumes = remember(context) { getMountedStorageVolumes(context) }
    val initialVolume = volumes.first()

    var activeVolumeRootPath by rememberSaveable { mutableStateOf(initialVolume.rootPath) }
    var currentDirPath by rememberSaveable { mutableStateOf(initialVolume.rootPath) }
    var showHiddenFolders by rememberSaveable { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var listing by remember { mutableStateOf(DirectoryListing(isLoading = true)) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val activeVolume = volumes.firstOrNull {
        normalizeDirectoryPath(it.rootPath) == normalizeDirectoryPath(activeVolumeRootPath)
    } ?: initialVolume

    val normalizedRoot = normalizeDirectoryPath(activeVolume.rootPath)
    val normalizedCurrent = normalizeDirectoryPath(currentDirPath)
    val canNavigateUp = normalizedCurrent != normalizedRoot
    val currentFolderDisplayName = resolveFolderDisplayName(activeVolume, normalizedCurrent)
    val breadcrumbs = remember(activeVolume, normalizedCurrent) {
        buildBreadcrumbs(activeVolume, normalizedCurrent)
    }

    fun navigateUp() {
        val parentPath = File(normalizedCurrent).parentFile?.absolutePath?.let(::normalizeDirectoryPath)
        currentDirPath = if (
            parentPath != null &&
            (parentPath == normalizedRoot || parentPath.startsWith(if (normalizedRoot == "/") "/" else "$normalizedRoot/"))
        ) {
            parentPath
        } else {
            normalizedRoot
        }
    }

    LaunchedEffect(normalizedCurrent, showHiddenFolders, refreshTrigger) {
        listing = DirectoryListing(isLoading = true)
        listing = withContext(Dispatchers.IO) {
            val dir = File(normalizedCurrent)
            val entries = try {
                dir.listFiles()
            } catch (_: SecurityException) {
                null
            }
            if (entries == null) {
                DirectoryListing(
                    directories = emptyList(),
                    fileCount = 0,
                    isReadable = false,
                    isLoading = false
                )
            } else {
                val visibleEntries = if (showHiddenFolders) {
                    entries.toList()
                } else {
                    entries.filter { !it.name.startsWith(".") }
                }
                val dirs = visibleEntries
                    .filter { it.isDirectory }
                    .sortedBy { it.name.lowercase() }
                val filesCount = visibleEntries.count { !it.isDirectory }
                DirectoryListing(
                    directories = dirs,
                    fileCount = filesCount,
                    isReadable = true,
                    isLoading = false
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (canNavigateUp) {
                navigateUp()
            } else {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = "Select Folder",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentFolderDisplayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showHiddenFolders = !showHiddenFolders }) {
                                Icon(
                                    imageVector = if (showHiddenFolders) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = if (showHiddenFolders) {
                                        "Hide hidden folders"
                                    } else {
                                        "Show hidden folders"
                                    }
                                )
                            }
                            IconButton(
                                onClick = { showCreateFolderDialog = true },
                                enabled = listing.isReadable && !listing.isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Create new folder"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = normalizedCurrent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    onFolderSelected(normalizedCurrent, currentFolderDisplayName)
                                },
                                enabled = listing.isReadable && !listing.isLoading,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Use \"$currentFolderDisplayName\"",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (volumes.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            volumes.forEach { volume ->
                                val selected = normalizeDirectoryPath(volume.rootPath) == normalizedRoot
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        activeVolumeRootPath = volume.rootPath
                                        currentDirPath = volume.rootPath
                                    },
                                    label = {
                                        Text(
                                            text = volume.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (volume.isPrimary) {
                                                Icons.Default.Smartphone
                                            } else {
                                                Icons.Default.SdStorage
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    PickerBreadcrumbsRow(
                        breadcrumbs = breadcrumbs,
                        onBreadcrumbClick = { crumb ->
                            currentDirPath = crumb.path
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            listing.isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            !listing.isReadable -> {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Folder cannot be accessed",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Android restricts access to this protected system folder. Please choose another folder.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    if (canNavigateUp) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedButton(
                                            onClick = { navigateUp() },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Go Back")
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .tvFocusContainer(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    if (canNavigateUp) {
                                        item(key = "__parent_folder__") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { navigateUp() }
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowUpward,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    text = "Parent folder",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (listing.directories.isEmpty()) {
                                        item(key = "__no_subfolders__") {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(24.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(40.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(
                                                        text = "No subfolders",
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    val infoText = if (listing.fileCount > 0) {
                                                        val noun = if (listing.fileCount == 1) "file" else "files"
                                                        "Contains ${listing.fileCount} $noun. Tap \"Use \\\"$currentFolderDisplayName\\\"\" below to share this folder."
                                                    } else {
                                                        "This folder is empty. Tap \"Use \\\"$currentFolderDisplayName\\\"\" below to share it."
                                                    }
                                                    Text(
                                                        text = infoText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(
                                            items = listing.directories,
                                            key = { it.absolutePath }
                                        ) { dir ->
                                            val knownIcon = getFolderVectorIcon(dir.name)
                                            val folderIcon = if (knownIcon == Icons.Default.FolderShared) {
                                                Icons.Default.Folder
                                            } else {
                                                knownIcon
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        currentDirPath = normalizeDirectoryPath(dir.absolutePath)
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = folderIcon,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    text = dir.name,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }

    if (showCreateFolderDialog) {
        CreateLocalFolderDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            onCreateFolder = { folderName ->
                showCreateFolderDialog = false
                coroutineScope.launch {
                    val created = withContext(Dispatchers.IO) {
                        val target = File(normalizedCurrent, folderName)
                        if (target.exists()) {
                            Result.failure(IllegalStateException("A folder named \"$folderName\" already exists."))
                        } else if (target.mkdir()) {
                            Result.success(target)
                        } else {
                            Result.failure(IllegalStateException("Could not create folder \"$folderName\"."))
                        }
                    }
                    created.onSuccess {
                        refreshTrigger++
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar(err.message ?: "Failed to create folder")
                    }
                }
            }
        )
    }
}

@Composable
private fun PickerBreadcrumbsRow(
    breadcrumbs: List<FolderBreadcrumb>,
    onBreadcrumbClick: (FolderBreadcrumb) -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(breadcrumbs) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, breadcrumb ->
            val isLast = index == breadcrumbs.lastIndex

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onBreadcrumbClick(breadcrumb) }
            ) {
                Text(
                    text = breadcrumb.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isLast) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateLocalFolderDialog(
    onDismissRequest: () -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validate(input: String): String? {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> "Folder name cannot be empty"
            trimmed == "." || trimmed == ".." -> "Invalid folder name"
            trimmed.contains('/') || trimmed.contains('\\') -> "Folder name cannot contain slashes"
            else -> null
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Create New Folder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorMessage = null
                    },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = folderName.trim()
                    val validationError = validate(trimmed)
                    if (validationError != null) {
                        errorMessage = validationError
                    } else {
                        onCreateFolder(trimmed)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
