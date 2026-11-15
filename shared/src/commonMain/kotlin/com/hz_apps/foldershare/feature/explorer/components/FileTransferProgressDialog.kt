package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hz_apps.foldershare.core.transfer.FileTransferProgress
import com.hz_apps.foldershare.core.transfer.TransferDirection
import com.hz_apps.foldershare.core.transfer.TransferStatus
import com.hz_apps.foldershare.ui.components.ScrollableColumn
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun FileTransferProgressDialog(
    transferProgress: FileTransferProgress,
    targetDeviceName: String? = null,
    isShowMoreExpanded: Boolean = false,
    onToggleShowMore: (Boolean) -> Unit = {},
    onCancel: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUpload = transferProgress.direction == TransferDirection.UPLOAD
    val isError = transferProgress.status == TransferStatus.PAUSED_ERROR
    val isReconnecting = transferProgress.status == TransferStatus.RECONNECTING
    val scrollState = rememberScrollState()

    val destinationName = targetDeviceName?.takeIf { it.isNotBlank() } ?: "Device"
    val titleText = when {
        isError -> if (isUpload) "Upload to $destinationName Interrupted" else "Download Interrupted"
        isReconnecting -> if (isUpload) "Reconnecting Upload to $destinationName..." else "Reconnecting Download..."
        else -> if (isUpload) "Uploading to $destinationName" else "Saving to Device"
    }

    val icon = when {
        isError -> Icons.Default.Warning
        isReconnecting -> Icons.Default.Refresh
        isUpload -> Icons.Default.FileUpload
        else -> Icons.Default.Download
    }

    val iconTint = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val containerBadgeColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    AlertDialog(
        onDismissRequest = { /* Prevent accidental dismiss */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = containerBadgeColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = titleText,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                // 1. File Name
                val nameDisplay = if (transferProgress.totalFilesCount > 1) {
                    "(${transferProgress.currentFileIndex}/${transferProgress.totalFilesCount}) ${transferProgress.fileName}"
                } else {
                    transferProgress.fileName
                }

                Text(
                    text = nameDisplay,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Thick Progress Bar
                val progress = transferProgress.progress
                val progressColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }

                val targetProgress = progress ?: 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    label = "transferProgressBarAnimation"
                )

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Percentage - Remaining Time
                val pctText = if (progress != null) "${(progress * 100).toInt()}%" else "0%"
                val etaStr = formatEta(transferProgress.estimatedRemainingSeconds, transferProgress.bytesPerSecond)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pctText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = etaStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Show more v / Show less ^ Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onToggleShowMore(!isShowMoreExpanded)
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = if (isShowMoreExpanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isShowMoreExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isShowMoreExpanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 5. Expandable Details (Speed, Downloaded out of total, Save Path)
                AnimatedVisibility(
                    visible = isShowMoreExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val transferredStr = formatByteSize(transferProgress.bytesTransferred)
                    val totalStr = transferProgress.totalBytes?.let { formatByteSize(it) }
                    val sizeDetailText = if (totalStr != null) {
                        "$transferredStr of $totalStr"
                    } else {
                        transferredStr
                    }

                    val speedStr = if (transferProgress.bytesPerSecond > 0L) {
                        "${formatByteSize(transferProgress.bytesPerSecond)}/s"
                    } else {
                        "0 B/s"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        DetailRow(
                            label = "Speed",
                            value = speedStr,
                            isMonospace = true
                        )

                        DetailRow(
                            label = if (isUpload) "Uploaded" else "Downloaded",
                            value = sizeDetailText,
                            isMonospace = true
                        )

                        if (!transferProgress.destinationPath.isNullOrBlank()) {
                            DetailRow(
                                label = if (isUpload) "Destination" else "Saving to",
                                value = transferProgress.destinationPath
                            )
                        }
                    }
                }

                if (!transferProgress.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = transferProgress.errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (transferProgress.isAutoRetrying || isReconnecting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReconnecting) "Reconnecting to device..." else "Auto-retrying when device reconnects...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isError) {
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry Now")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatEta(estimatedRemainingSeconds: Long?, bytesPerSecond: Long): String {
    if (estimatedRemainingSeconds == null || bytesPerSecond <= 0L) {
        return "Calculating time..."
    }
    if (estimatedRemainingSeconds <= 0L) {
        return "Finishing up..."
    }
    return when {
        estimatedRemainingSeconds < 60L -> "About ${estimatedRemainingSeconds}s left"
        estimatedRemainingSeconds < 3600L -> {
            val mins = estimatedRemainingSeconds / 60L
            val secs = estimatedRemainingSeconds % 60L
            if (secs > 0L) "About ${mins}m ${secs}s left" else "About ${mins}m left"
        }
        else -> {
            val hours = estimatedRemainingSeconds / 3600L
            val mins = (estimatedRemainingSeconds % 3600L) / 60L
            "About ${hours}h ${mins}m left"
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val coercedGroup = digitGroups.coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(coercedGroup.toDouble())
    return if (coercedGroup == 0) "$bytes B" else "${(value * 10).toInt() / 10.0} ${units[coercedGroup]}"
}
