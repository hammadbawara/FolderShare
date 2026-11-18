package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a high-contrast QR Code on a white surface with proper quiet-zone padding,
 * making it reliably scannable on dark-themed Android TV screens and Desktop monitors.
 */
@Composable
fun QrCodeBox(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    quietZonePadding: Dp = 12.dp,
    contentDescriptionText: String = "QR Code for $data"
) {
    val matrix = remember(data) {
        QrCodeGenerator.encode(data)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(quietZonePadding)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridCount = matrix.size
            if (gridCount <= 0) return@Canvas
            val cellSize = minOf(this.size.width, this.size.height) / gridCount
            val offsetX = (this.size.width - cellSize * gridCount) / 2f
            val offsetY = (this.size.height - cellSize * gridCount) / 2f

            // Slightly overlap module cells by 0.5px to prevent sub-pixel gaps on high-DPI displays
            val drawSize = cellSize + 0.5f

            for (y in 0 until gridCount) {
                for (x in 0 until gridCount) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(
                                x = offsetX + x * cellSize,
                                y = offsetY + y * cellSize
                            ),
                            size = Size(drawSize, drawSize)
                        )
                    }
                }
            }
        }
    }
}
