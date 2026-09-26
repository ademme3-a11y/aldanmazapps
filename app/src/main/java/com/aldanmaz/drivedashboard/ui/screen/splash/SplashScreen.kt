package com.aldanmaz.drivedashboard.ui.screen.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(420))
        scale.animateTo(1f, tween(420))
        delay(650L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17232D), Color(0xFF05080B)),
                    radius = 1100f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            Canvas(modifier = Modifier.size(112.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.39f
                drawCircle(
                    color = Color(0xFF43D9FF),
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.035f)
                )
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30.0) - 90.0)
                    val outer = radius * 0.91f
                    val inner = if (i % 3 == 0) radius * 0.68f else radius * 0.77f
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color = if (i % 3 == 0) Color.White else Color(0xFF8AA1B2),
                        start = Offset(center.x + inner * cos, center.y + inner * sin),
                        end = Offset(center.x + outer * cos, center.y + outer * sin),
                        strokeWidth = if (i % 3 == 0) 4f else 2f,
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = Color(0xFFFF5252),
                    start = center,
                    end = Offset(center.x + radius * 0.58f, center.y - radius * 0.42f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                drawCircle(Color.White, radius = 7f, center = center)
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "ALD DRIVE",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "SÜRÜŞ ASİSTANI",
                color = Color(0xFF8FA8B8),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
