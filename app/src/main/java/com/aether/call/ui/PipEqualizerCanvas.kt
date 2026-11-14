package com.aether.call.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 5-bar animated audio equalizer Canvas optimized for Picture-in-Picture micro views.
 */
@Composable
fun PipEqualizerCanvas(
    targetAmplitude: Float,
    isMuted: Boolean = false,
    barCount: Int = 5,
    activeColor: Color = Color(0xFF10B981),
    mutedColor: Color = Color(0xFFEF4444),
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqualizerPhaseTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "EqualizerPhase",
    )

    val smoothAmp by animateFloatAsState(
        targetValue = if (isMuted) 0.05f else targetAmplitude,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SmoothAmp",
    )

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f || barCount <= 0) return@Canvas

        val barWidth = size.width / (barCount * 1.6f)
        val spacing = ((size.width - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)).coerceAtLeast(1f)
        val barBrush = Brush.verticalGradient(
            colors = listOf(activeColor, activeColor.copy(alpha = 0.6f)),
        )

        for (i in 0 until barCount) {
            val wave = sin(phase + (i * 0.9f))
            val dynamicHeight = (size.height * smoothAmp * (0.4f + 0.6f * abs(wave))).coerceIn(4.dp.toPx(), size.height)

            val left = i * (barWidth + spacing)
            val top = (size.height - dynamicHeight) / 2f

            drawRoundRect(
                brush = if (isMuted) SolidColor(mutedColor) else barBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
