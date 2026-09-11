package com.hz_apps.foldershare.feature.devices.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.ui.components.NetworkEndpointBadge
import com.hz_apps.foldershare.ui.components.parseEndpoint

fun getCategoryIcon(category: DeviceCategory): ImageVector {
    return when (category) {
        DeviceCategory.PHONE -> Icons.Default.Smartphone
        DeviceCategory.TABLET -> Icons.Default.Tablet
        DeviceCategory.COMPUTER -> Icons.Default.Laptop
        DeviceCategory.TV -> Icons.Default.Tv
        DeviceCategory.UNKNOWN -> Icons.Default.Devices
    }
}

@Composable
fun DeviceItemRow(
    deviceName: String,
    category: DeviceCategory,
    platformDisplayName: String,
    isAuthRequired: Boolean,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    hostAddress: String = "",
    port: Int = 0,
    endpoints: List<String> = emptyList(),
    isThisDevice: Boolean = false,
    networkLinksCount: Int = 1,
    isConnecting: Boolean = false,
    onClick: () -> Unit = {},
) {
    var isExpanded by remember { mutableStateOf(false) }
    val effectiveEndpoints = remember(endpoints, hostAddress, port) {
        if (endpoints.isNotEmpty()) {
            endpoints
        } else if (hostAddress.isNotBlank()) {
            if (port > 0) listOf("$hostAddress:$port") else listOf(hostAddress)
        } else {
            emptyList()
        }
    }
    val hasMultipleEndpoints = effectiveEndpoints.size > 1
    val firstEndpoint = remember(effectiveEndpoints, port) {
        effectiveEndpoints.firstOrNull()?.let { parseEndpoint(it, port.takeIf { p -> p > 0 }) }
    }
    val categoryIcon = remember(category) { getCategoryIcon(category) }

    val borderColor by animateColorAsState(
        targetValue = if (isConnecting) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "DeviceCardBorderColor"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isConnecting) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = isOnline, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Leading circular avatar with category icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = category.displayName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Middle Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Line 1: Device Name and Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isThisDevice) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "This Device",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Line 2: OS Name
                    Text(
                        text = platformDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Line 3: IP Address & Show More Button
                    if (firstEndpoint != null && firstEndpoint.first.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NetworkEndpointBadge(
                                host = firstEndpoint.first,
                                port = firstEndpoint.second
                            )

                            if (hasMultipleEndpoints) {
                                val remainingCount = effectiveEndpoints.size - 1
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "arrowRotation"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    onClick = { isExpanded = !isExpanded },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isExpanded) "Hide" else "+$remainingCount more",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            modifier = Modifier
                                                .size(13.dp)
                                                .rotate(arrowRotation)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Trailing Area
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isAuthRequired) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Protected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    AnimatedContent(
                        targetState = isConnecting,
                        label = "DeviceItemTrailingAction"
                    ) { connecting ->
                        if (connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Connect",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Endpoints Section for Multi-Link Devices
            if (hasMultipleEndpoints) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 78.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        effectiveEndpoints.drop(1).forEach { endpointStr ->
                            val (host, endpointPort) = parseEndpoint(
                                endpointStr,
                                port.takeIf { it > 0 }
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NetworkEndpointBadge(
                                    host = host,
                                    port = endpointPort
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




