package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.feature.explorer.PathSegment
import com.hz_apps.foldershare.ui.components.HorizontalScrollbar
import com.hz_apps.foldershare.ui.components.rememberScrollbarAdapter

@Composable
fun BreadcrumbsBar(
    pathSegments: List<PathSegment>,
    onSegmentClick: (PathSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(pathSegments.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pathSegments.forEachIndexed { index, segment ->
                val isLast = index == pathSegments.lastIndex

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isLast) { onSegmentClick(segment) }
                ) {
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
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

        HorizontalScrollbar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}
