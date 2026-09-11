package com.hz_apps.foldershare.feature.devices.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DiscoveryAnimation(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(tween(1000)) + scaleIn(tween(1000), initialScale = 0.8f),
            exit = fadeOut(tween(500)) + scaleOut(tween(500), targetScale = 0.8f),
            modifier = Modifier.fillMaxSize()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "DiscoveryAnimation")

            // Waves progress
            val waveProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "WaveProgress"
            )
            
            // Device blinking/pulsing animation
            val pulse1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Pulse1"
            )

            val pulse2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Pulse2"
            )
            
            val pulse3 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Pulse3"
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = minOf(size.width, size.height) / 2

                    // Draw waves
                    fun drawWave(progress: Float) {
                        if (progress <= 0f) return
                        val radius = maxRadius * progress
                        val alpha = 1f - progress
                        drawCircle(
                            color = primaryColor.copy(alpha = alpha * 0.5f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    drawWave(waveProgress)
                    drawWave((waveProgress + 0.333f) % 1f)
                    drawWave((waveProgress + 0.666f) % 1f)
                    
                    // Small stationary dots that blink
                    val dotRadii = listOf(0.4f, 0.6f, 0.8f, 0.7f, 0.5f, 0.9f)
                    val dotAngles = listOf(0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f)
                    
                    for (i in dotRadii.indices) {
                        val r = maxRadius * dotRadii[i]
                        val x = center.x + r * cos(dotAngles[i])
                        val y = center.y + r * sin(dotAngles[i])
                        val alpha = if (i % 3 == 0) pulse1 else if (i % 3 == 1) pulse2 else pulse3
                        drawCircle(
                            color = primaryColor.copy(alpha = alpha * 0.6f),
                            radius = (2 + alpha * 2).dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // Stationary floating device icons with pulse
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier
                        .offset(x = (-80).dp, y = (-60).dp)
                        .alpha(pulse1)
                        .scale(0.8f + pulse1 * 0.2f)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(6.dp)
                )

                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier
                        .offset(x = 100.dp, y = (-40).dp)
                        .alpha(pulse2)
                        .scale(0.8f + pulse2 * 0.2f)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(6.dp)
                )
                
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier
                        .offset(x = 50.dp, y = 90.dp)
                        .alpha(pulse3)
                        .scale(0.8f + pulse3 * 0.2f)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(4.dp)
                )
            }
        }

        // Center Router Icon (Always visible)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = "Router",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
