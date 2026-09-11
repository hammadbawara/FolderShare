package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.RemoteFile

@Composable
fun RemoteFileItemRow(
    file: RemoteFile,
    onOpen: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    isWriteAllowed: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
) {
    val icon = getFileIcon(file)
    val iconTint = getFileIconTint(file)
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isFocused by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnFocused by rememberUpdatedState(onFocused)
    val currentOnContextMenu by rememberUpdatedState { offset: DpOffset ->
        contextMenuOffset = offset
        showContextMenu = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    currentOnFocused?.invoke()
                }
            }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (isFirstItem) {
                                false
                            } else {
                                focusManager.moveFocus(FocusDirection.Up)
                            }
                        }
                        Key.DirectionDown -> {
                            if (isLastItem) {
                                false
                            } else {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            currentOnOpen()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        try { focusRequester.requestFocus() } catch (e: Exception) {}
                        currentOnOpen()
                    },
                    onLongPress = { offset ->
                        try { focusRequester.requestFocus() } catch (e: Exception) {}
                        val xDp = with(density) { offset.x.toDp() }
                        val yDp = with(density) { offset.y.toDp() }
                        currentOnContextMenu(DpOffset(xDp, yDp))
                    }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val change = event.changes.firstOrNull()
                            if (change != null && !change.isConsumed) {
                                change.consume()
                                try { focusRequester.requestFocus() } catch (e: Exception) {}
                                val xDp = with(density) { change.position.x.toDp() }
                                val yDp = with(density) { change.position.y.toDp() }
                                currentOnContextMenu(DpOffset(xDp, yDp))
                            }
                        }
                    }
                }
            }
    ) {
        val containerColor = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surface
        }

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .hoverable(interactionSource)
        ) {

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = iconTint.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val subtitleText = if (file.isDirectory) {
                            "Folder"
                        } else {
                            val details = mutableListOf(file.formattedSize)
                            if (file.lastModified.isNotEmpty()) {
                                details.add(file.lastModified)
                            }
                            details.joinToString(" • ")
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Zero-size anchor box positioned at exact right-click or long-press location
                Box(
                    modifier = Modifier
                        .offset(x = contextMenuOffset.x, y = contextMenuOffset.y)
                        .size(0.dp)
                ) {
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        RemoteFileContextMenuItems(
                            file = file,
                            onDismiss = { showContextMenu = false },
                            onOpen = onOpen,
                            onSaveToDevice = onSaveToDevice,
                            onShare = onShare,
                            onRename = onRename,
                            onDelete = onDelete,
                            onProperties = onProperties,
                            isWriteAllowed = isWriteAllowed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileContextMenuItems(
    file: RemoteFile,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    isWriteAllowed: Boolean
) {
    DropdownMenuItem(
        text = { Text("Open") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        onClick = {
            onDismiss()
            onOpen()
        }
    )

    if (!file.isDirectory) {
        DropdownMenuItem(
            text = { Text("Save to Device") },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            onClick = {
                onDismiss()
                onSaveToDevice()
            }
        )
    }

    DropdownMenuItem(
        text = { Text("Share") },
        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
        onClick = {
            onDismiss()
            onShare()
        }
    )

    DropdownMenuItem(
        text = { Text("Rename") },
        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        enabled = isWriteAllowed,
        onClick = {
            onDismiss()
            onRename()
        }
    )

    DropdownMenuItem(
        text = { Text("Delete") },
        leadingIcon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = if (isWriteAllowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        enabled = isWriteAllowed,
        onClick = {
            onDismiss()
            onDelete()
        }
    )

    DropdownMenuItem(
        text = { Text("Properties") },
        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
        onClick = {
            onDismiss()
            onProperties()
        }
    )
}
