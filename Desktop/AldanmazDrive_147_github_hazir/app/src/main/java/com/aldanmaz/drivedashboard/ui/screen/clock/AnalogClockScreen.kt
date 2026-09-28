package com.aldanmaz.drivedashboard.ui.screen.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.ui.screen.prayer.PrayerUiState
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherUiState
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClockScreen(
    weather: WeatherUiState,
    prayer: PrayerUiState,
    speedKmh: Int,
    onBack: () -> Unit
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val accent = DashboardPaletteRuntime.accent

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    LaunchedEffect(speedKmh) {
        if (speedKmh >= 5) onBack()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF071722), Color(0xFF01060B)),
                    radius = 1_050f
                )
            )
            .clickable(onClick = onBack)
            .padding(24.dp)
    ) {
        val configuration = LocalConfiguration.current
        val largeDisplay = configuration.smallestScreenWidthDp >= 600 && maxWidth > maxHeight
        val clockSize = if (largeDisplay) {
            minOf(maxWidth * 0.56f, maxHeight * 0.90f)
        } else {
            340.dp
        }

        Column(
            Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                weather.cityName ?: "Konum",
                color = accent,
                fontSize = if (largeDisplay) 23.sp else 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                weather.temperatureText,
                color = Color.White,
                fontSize = if (largeDisplay) 44.sp else 34.sp,
                fontWeight = FontWeight.Black
            )
        }

        ModernAnalogClock(
            now = now,
            accent = accent,
            modifier = Modifier.align(Alignment.Center).size(clockSize)
        )

        Column(
            Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "SIRADAKİ NAMAZ",
                color = accent,
                fontSize = if (largeDisplay) 15.sp else 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${prayer.nextPrayerName}  ${prayer.nextPrayerTime}",
                color = Color.White,
                fontSize = if (largeDisplay) 28.sp else 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                prayer.remainingText,
                color = Color(0xFFFFD36A),
                fontSize = if (largeDisplay) 18.sp else 15.sp
            )
        }

    }
}

@Composable
private fun ModernAnalogClock(now: LocalDateTime, accent: Color, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val largeClock = maxWidth >= 420.dp
        val numeralSize = if (largeClock) 19.sp else 13.sp
        val numeralInset = if (largeClock) 42.dp else 30.dp
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.46f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF122A38), Color(0xFF030A10)),
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )
            drawCircle(accent.copy(alpha = 0.18f), r * 1.025f, c, style = Stroke(4.dp.toPx()))
            drawCircle(accent, r, c, style = Stroke(2.2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.11f), r * 0.86f, c, style = Stroke(1.4.dp.toPx()))

            for (i in 0 until 60) {
                val angle = (i * 6 - 90) * PI / 180.0
                val major = i % 5 == 0
                val outer = Offset(
                    c.x + (cos(angle) * r * 0.92f).toFloat(),
                    c.y + (sin(angle) * r * 0.92f).toFloat()
                )
                val innerRadius = if (major) r * 0.78f else r * 0.84f
                val inner = Offset(
                    c.x + (cos(angle) * innerRadius).toFloat(),
                    c.y + (sin(angle) * innerRadius).toFloat()
                )
                drawLine(
                    color = if (major) Color.White.copy(alpha = 0.92f) else Color(0xFF547080).copy(alpha = 0.68f),
                    start = inner,
                    end = outer,
                    strokeWidth = if (major) 3.2.dp.toPx() else 1.45.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            fun taperedHand(angleDeg: Double, length: Float, halfWidth: Float, tail: Float, color: Color) {
                val a = angleDeg * PI / 180.0
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val px = -dy
                val py = dx
                val tip = Offset(c.x + dx * length, c.y + dy * length)
                val back = Offset(c.x - dx * tail, c.y - dy * tail)
                val path = Path().apply {
                    moveTo(back.x + px * halfWidth * 0.55f, back.y + py * halfWidth * 0.55f)
                    lineTo(c.x + px * halfWidth, c.y + py * halfWidth)
                    lineTo(tip.x, tip.y)
                    lineTo(c.x - px * halfWidth, c.y - py * halfWidth)
                    lineTo(back.x - px * halfWidth * 0.55f, back.y - py * halfWidth * 0.55f)
                    close()
                }
                drawPath(path, color)
            }

            val hourAngle = ((now.hour % 12) + now.minute / 60.0) * 30.0 - 90.0
            val minuteAngle = (now.minute + now.second / 60.0) * 6.0 - 90.0
            val secondAngle = now.second * 6.0 - 90.0

            taperedHand(hourAngle, r * 0.50f, 5.5.dp.toPx(), r * 0.09f, Color.White)
            taperedHand(minuteAngle, r * 0.72f, 3.8.dp.toPx(), r * 0.12f, accent)

            val a = secondAngle * PI / 180.0
            drawLine(
                color = Color(0xFFFF5C68),
                start = Offset(
                    c.x - (cos(a) * r * 0.16f).toFloat(),
                    c.y - (sin(a) * r * 0.16f).toFloat()
                ),
                end = Offset(
                    c.x + (cos(a) * r * 0.80f).toFloat(),
                    c.y + (sin(a) * r * 0.80f).toFloat()
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(Color(0xFF07121A), 8.dp.toPx(), c)
            drawCircle(Color.White, 4.dp.toPx(), c)
            drawCircle(accent, 2.dp.toPx(), c)
        }

        Text("12", color = Color.White.copy(alpha = 0.86f), fontSize = numeralSize, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = numeralInset))
        Text("3", color = Color.White.copy(alpha = 0.86f), fontSize = numeralSize, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = numeralInset))
        Text("6", color = Color.White.copy(alpha = 0.86f), fontSize = numeralSize, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = numeralInset))
        Text("9", color = Color.White.copy(alpha = 0.86f), fontSize = numeralSize, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).padding(start = numeralInset))
    }
}
