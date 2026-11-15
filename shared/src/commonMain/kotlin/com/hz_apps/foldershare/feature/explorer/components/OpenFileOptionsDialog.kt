package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.FileOpenOption
import com.hz_apps.foldershare.core.explorer.model.RemoteFile
import com.hz_apps.foldershare.ui.components.AppAlertDialog

@Composable
fun OpenFileOptionsDialog(
    file: RemoteFile,
    onDismiss: () -> Unit,
    onConfirm: (FileOpenOption) -> Unit
) {
    val icon = getFileIcon(file)
    val iconTint = getFileIconTint(file)

    val directOpenRequester = remember { FocusRequester() }
    val tempSaveRequester = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasConfirmed by remember { mutableStateOf(false) }

    val triggerConfirm: (FileOpenOption) -> Unit = { option ->
        if (!hasConfirmed) {
            hasConfirmed = true
            onConfirm(option)
        }
    }

    LaunchedEffect(Unit) {
        try {
            directOpenRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // File header card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = iconTint.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (file.formattedSize.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = file.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Open Instantly
                FileOpenActionCard(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open Instantly",
                    subtitle = "Open directly without downloading",
                    secondaryNote = null,
                    focusRequester = directOpenRequester,
                    onClick = { triggerConfirm(FileOpenOption.DIRECT_OPEN) },
                    onNavigateUp = null,
                    onNavigateDown = {
                        try {
                            tempSaveRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Download & Open
                FileOpenActionCard(
                    icon = Icons.Default.Download,
                    title = "Download & Open",
                    subtitle = "Use if the app can't open directly",
                    secondaryNote = "Clears automatically on app exit",
                    focusRequester = tempSaveRequester,
                    onClick = { triggerConfirm(FileOpenOption.TEMPORARY_SAVE_AND_OPEN) },
                    onNavigateUp = {
                        try {
                            directOpenRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Up)
                        }
                    },
                    onNavigateDown = {
                        try {
                            cancelRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(cancelRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            try {
                                tempSaveRequester.requestFocus()
                                true
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FileOpenActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    secondaryNote: String?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)
    val currentOnNavigateDown by rememberUpdatedState(onNavigateDown)

    val isHighlighted = isFocused || isHovered

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "ActionCardBackground"
    )

    val borderStroke = when {
        isFocused -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isHovered -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    val iconContainerColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "ActionCardIconBg"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "ActionCardIconTint"
    )

    Surface(
        onClick = currentOnClick,
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = borderStroke,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            currentOnClick()
                            true
                        }
                        Key.DirectionUp -> {
                            if (currentOnNavigateUp != null) {
                                currentOnNavigateUp?.invoke()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (currentOnNavigateDown != null) {
                                currentOnNavigateDown?.invoke()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (secondaryNote != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = secondaryNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


