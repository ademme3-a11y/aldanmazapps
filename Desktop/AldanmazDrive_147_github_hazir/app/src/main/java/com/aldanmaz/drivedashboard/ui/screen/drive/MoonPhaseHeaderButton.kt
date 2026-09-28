package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos

private const val SynodicMonthDays = 29.53058867
private val MoonCyan = Color(0xFF22D7F3)

private data class MoonPhaseState(
    val ageDays: Double,
    val illuminationPercent: Double,
    val illumination: Int,
    val name: String,
    val waxing: Boolean,
    val frame: Int,
    val year: Int
)

@Composable
internal fun MoonPhaseHeaderButton(
    modifier: Modifier = Modifier,
    onClickOverride: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val state = remember(now) { calculateMoonPhase(now) }
    var bitmap by remember { mutableStateOf(loadCachedMoon(context, state)) }
    var showDetail by remember { mutableStateOf(false) }
    val isDay = now.hour in 7..18

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val current = LocalDateTime.now()
            if (current.hour != now.hour || current.dayOfYear != now.dayOfYear) now = current
        }
    }

    LaunchedEffect(state.year, state.frame) {
        // Bir önceki saate ait görüntüyü yeni evre diye göstermeyelim.
        bitmap = loadCachedMoon(context, state)
        fetchNasaMoon(context, state)?.let { bitmap = it }
    }

    Card(
        modifier = modifier.pointerInput(onClickOverride) {
            detectTapGestures(
                onTap = { onClickOverride?.invoke() ?: run { showDetail = true } }
            )
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDay) Color(0xFF082A4A) else Color.Black),
        border = BorderStroke(1.2.dp, MoonCyan.copy(alpha = .78f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MoonArtwork(bitmap, state, Modifier.size(52.dp))
        }
    }

    if (showDetail) {
        Dialog(
            onDismissRequest = { showDetail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(.72f).clickable { showDetail = false },
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDay) Color(0xFF082A4A) else Color(0xFF020407)),
                border = BorderStroke(2.dp, MoonCyan)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MoonArtwork(bitmap, state, Modifier.size(260.dp))
                    Text(state.name, color = DashboardPaletteRuntime.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Aydınlanma %${state.illumination}", color = MoonCyan, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("NASA • Saatlik Ay görünümü", color = DashboardPaletteRuntime.primaryText.copy(alpha = .62f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MoonArtwork(bitmap: Bitmap?, state: MoonPhaseState, modifier: Modifier) {
    if (bitmap != null) {
        Box(modifier) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${state.name}, yüzde ${state.illumination}",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = MoonCyan.copy(alpha = .38f),
                    radius = size.minDimension * .485f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )

                // Yeni Ay çevresindeki NASA karelerinde gerçek hilal 52 dp'lik
                // üst-bar ikonunda bir pikselin altına düşebiliyor. Fotoğrafı ve
                // evre yönünü koruyup yalnızca %5'in altında görünürlük desteği
                // ekliyoruz; daha aydınlık evrelerde fotoğrafa dokunulmuyor.
                if (state.illuminationPercent in 0.01..5.0) {
                    val radius = size.minDimension * .455f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val visibleFraction =
                        (state.illuminationPercent / 100.0).coerceAtLeast(.055).toFloat()
                    val shift = radius * 2f * visibleFraction
                    val shadowShift = if (state.waxing) -shift else shift

                    drawCircle(
                        color = Color(0xFFE7EDF2).copy(alpha = .92f),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF05070A).copy(alpha = .98f),
                        radius = radius,
                        center = Offset(center.x + shadowShift, center.y)
                    )
                }
            }
        }
    } else {
        Canvas(modifier) {
            val radius = size.minDimension * .47f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFFB8C0C8), radius, center)
            val phase = state.ageDays / SynodicMonthDays
            val shift = (cos(phase * 2.0 * PI) * radius).toFloat()
            val shadowCenterX = if (state.waxing) center.x - shift else center.x + shift
            drawOval(
                color = Color(0xFF080A0D),
                topLeft = Offset(shadowCenterX - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
        }
    }
}

private fun calculateMoonPhase(now: LocalDateTime): MoonPhaseState {
    val epoch = LocalDateTime.of(2000, 1, 6, 18, 14).toEpochSecond(ZoneOffset.UTC)
    val utcNow = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    val seconds = utcNow.toEpochSecond(ZoneOffset.UTC) - epoch
    val totalDays = seconds / 86400.0
    val age = ((totalDays % SynodicMonthDays) + SynodicMonthDays) % SynodicMonthDays
    val illuminationPercent =
        ((1.0 - cos(2.0 * PI * age / SynodicMonthDays)) / 2.0) * 100.0
    val illumination = illuminationPercent.toInt().coerceIn(0, 100)
    val fraction = age / SynodicMonthDays
    val name = when {
        fraction < .03 || fraction >= .97 -> "Yeni Ay"
        fraction < .22 -> "Büyüyen Hilal"
        fraction < .28 -> "İlk Dördün"
        fraction < .47 -> "Büyüyen Şişkin Ay"
        fraction < .53 -> "Dolunay"
        fraction < .72 -> "Küçülen Şişkin Ay"
        fraction < .78 -> "Son Dördün"
        else -> "Küçülen Hilal"
    }
    val frame = ((utcNow.dayOfYear - 1) * 24 + utcNow.hour + 1).coerceIn(1, 8784)
    return MoonPhaseState(
        ageDays = age,
        illuminationPercent = illuminationPercent,
        illumination = illumination,
        name = name,
        waxing = fraction < .5,
        frame = frame,
        year = utcNow.year
    )
}

private fun nasaMoonUrl(state: MoonPhaseState): String? {
    val path = when (state.year) {
        2024 -> "a005100/a005187"
        2025 -> "a005400/a005415"
        2026 -> "a005500/a005587"
        else -> return null
    }
    return "https://svs.gsfc.nasa.gov/vis/a000000/$path/frames/216x216_1x1_30p/moon.${state.frame.toString().padStart(4, '0')}.jpg"
}

private fun moonCacheFile(context: Context, state: MoonPhaseState) =
    File(context.cacheDir, "nasa_moon_${state.year}_${state.frame}.jpg")

private fun loadCachedMoon(context: Context, state: MoonPhaseState): Bitmap? {
    val exact = moonCacheFile(context, state)
    if (exact.exists()) {
        val decoded = BitmapFactory.decodeFile(exact.absolutePath)
        if (decoded != null) return decoded
        // Bir ağ hata sayfası/yarım indirme jpg adıyla kalmışsa sonraki
        // açılışlarda gerçek NASA karesinin indirilmesini engellemesin.
        exact.delete()
    }
    return null
}

private suspend fun fetchNasaMoon(context: Context, state: MoonPhaseState): Bitmap? = withContext(Dispatchers.IO) {
    val url = nasaMoonUrl(state) ?: return@withContext null
    val target = moonCacheFile(context, state)
    if (target.exists()) {
        BitmapFactory.decodeFile(target.absolutePath)?.let { return@withContext it }
        target.delete()
    }
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 9000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AldanmazDrive/63")
        }
        connection.connect()
        check(connection.responseCode in 200..299) {
            "NASA Ay görseli HTTP ${connection.responseCode}"
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("NASA yanıtı geçerli görsel değil")
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            target.writeBytes(bytes)
            temporary.delete()
        }
        connection.disconnect()
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("nasa_moon_") && it != target }
            ?.sortedByDescending(File::lastModified)
            ?.drop(23)
            ?.forEach { it.delete() }
        decoded
    }.getOrNull()
}
