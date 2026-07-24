package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.VoiceState
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusGreen

import com.example.ui.theme.RainbowColors

@Composable
fun ArcReactorVisualizer(
    voiceState: VoiceState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.LISTENING) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val coreColor = when (voiceState) {
        VoiceState.LISTENING -> StatusGreen
        VoiceState.PROCESSING -> NeonCyan
        VoiceState.SPEAKING -> CyberBlue
        VoiceState.IDLE -> CyberBlue
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseRadius = this.size.width / 2 * 0.75f

            // Animated Rainbow Outer Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = RainbowColors,
                    center = center
                ),
                radius = baseRadius * pulseScale,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Outer rainbow glow ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = RainbowColors.map { it.copy(alpha = 0.4f) },
                    center = center
                ),
                radius = baseRadius * 1.15f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner solid core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, coreColor.copy(alpha = 0.4f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 0.5f * pulseScale
                ),
                radius = baseRadius * 0.45f * pulseScale,
                center = center
            )
        }
    }
}
