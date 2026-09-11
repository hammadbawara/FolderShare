package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
    var selectedOption by remember { mutableStateOf(FileOpenOption.DIRECT_OPEN) }
    val icon = getFileIcon(file)
    val iconTint = getFileIconTint(file)

    val directOpenRequester = remember { FocusRequester() }
    val tempSaveRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        try {
            directOpenRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open File",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // File info summary card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose how to open this file:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Direct Open
                FileOpenOptionCard(
                    option = FileOpenOption.DIRECT_OPEN,
                    title = "Direct Open",
                    description = "Stream and open directly in external app without saving locally",
                    isSelected = (selectedOption == FileOpenOption.DIRECT_OPEN),
                    focusRequester = directOpenRequester,
                    onSelect = { selectedOption = FileOpenOption.DIRECT_OPEN },
                    onConfirm = { onConfirm(FileOpenOption.DIRECT_OPEN) },
                    onNavigateUp = null,
                    onNavigateDown = {
                        try {
                            tempSaveRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Temporary Save & Open
                FileOpenOptionCard(
                    option = FileOpenOption.TEMPORARY_SAVE_AND_OPEN,
                    title = "Temporary Save & Open",
                    description = "Download to temporary cache folder and open locally",
                    isSelected = (selectedOption == FileOpenOption.TEMPORARY_SAVE_AND_OPEN),
                    focusRequester = tempSaveRequester,
                    onSelect = { selectedOption = FileOpenOption.TEMPORARY_SAVE_AND_OPEN },
                    onConfirm = { onConfirm(FileOpenOption.TEMPORARY_SAVE_AND_OPEN) },
                    onNavigateUp = {
                        try {
                            directOpenRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Up)
                        }
                    },
                    onNavigateDown = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOption) }
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FileOpenOptionCard(
    option: FileOpenOption,
    title: String,
    description: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onConfirm: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)
    val currentOnNavigateDown by rememberUpdatedState(onNavigateDown)

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isFocused -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(10.dp),
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    currentOnSelect()
                }
            }
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            currentOnSelect()
                            currentOnConfirm()
                            true
                        }
                        Key.Spacebar -> {
                            currentOnSelect()
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
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(option) {
                detectTapGestures(
                    onTap = {
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {}
                        currentOnSelect()
                    },
                    onDoubleTap = {
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {}
                        currentOnSelect()
                        currentOnConfirm()
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

