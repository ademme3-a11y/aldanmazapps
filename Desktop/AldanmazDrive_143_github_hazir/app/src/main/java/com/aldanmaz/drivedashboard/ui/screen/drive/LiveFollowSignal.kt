package com.aldanmaz.drivedashboard.ui.screen.drive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small driver identity marker for the dashboard.
 * The animation is deliberately driven by [isLive]; callers must only set it
 * true when a fresh, authenticated remote location update has been received.
 */
@Composable
fun LiveFollowSignal(
    shortName: String,
    isLive: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF37D6FF)
) {
    val transition = rememberInfiniteTransition(label = "liveFollowSignal")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liveFollowSignalPhase"
    )
    val signalColor = if (isLive) accent else Color(0xFF87919B)

    Box(modifier = modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            if (isLive) {
                for (ring in 0..1) {
                    val progress = (phase + ring * 0.5f) % 1f
                    val radius = size.minDimension * (0.20f + progress * 0.43f)
                    drawCircle(
                        color = signalColor.copy(alpha = (1f - progress) * 0.72f),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = signalColor.copy(alpha = 0.65f),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        Text(
            text = shortName.take(3).uppercase(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
