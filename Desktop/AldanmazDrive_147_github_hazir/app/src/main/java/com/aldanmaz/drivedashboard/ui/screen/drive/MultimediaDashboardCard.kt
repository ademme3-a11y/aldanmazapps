package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveForegroundService
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveState
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveStatus
import com.aldanmaz.drivedashboard.data.ai.GeminiSpeechActivity
import com.aldanmaz.drivedashboard.data.ai.OfflineGeminiCommandController
import com.aldanmaz.drivedashboard.data.alert.MediaAppController
import kotlinx.coroutines.delay

private val MultimediaBlack = Color(0xFF030507)
private val MultimediaCyan = Color(0xFF22D7F3)
private val MultimediaGreen = Color(0xFF31E39A)
private val MultimediaRed = Color(0xFFFF4F5E)

private data class DashboardMediaItem(
    val id: String,
    val label: String,
    val symbol: String,
    val color: Color,
    val packageNames: List<String> = emptyList(),
    val video: Boolean = false
)

@Composable
internal fun EnhancedMultimediaCard(
    speedKmh: Int,
    onLiveFollowClick: () -> Unit = {},
    liveFollowActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val geminiLiveManager = remember(context.applicationContext) {
        GeminiLiveManager.getInstance(context.applicationContext)
    }
    val geminiLiveState by geminiLiveManager.state.collectAsStateWithLifecycle()
    val geminiAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (OfflineGeminiCommandController.hasUsableInternet(context)) {
                GeminiLiveForegroundService.start(context)
            } else {
                OfflineGeminiCommandController.listenOnce(context)
            }
        } else {
            Toast.makeText(
                context,
                "Gemini Live için mikrofon izni gerekli.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun toggleGeminiLive() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        // 89: ERROR/NEEDS_SETUP durumunda "YENİDEN DENE" gerçek bir yeniden
        // bağlantı başlatır. Persistent user_enabled=true olsa bile servisi kapatmaz.
        if (geminiLiveState.status == GeminiLiveStatus.ERROR ||
            geminiLiveState.status == GeminiLiveStatus.NEEDS_SETUP
        ) {
            if (hasPermission) {
                geminiLiveManager.clearError()
                if (OfflineGeminiCommandController.hasUsableInternet(context)) {
                    GeminiLiveForegroundService.start(context)
                } else {
                    OfflineGeminiCommandController.listenOnce(context)
                }
            } else {
                geminiAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }

        val persistentSessionEnabled =
            GeminiLiveForegroundService.isUserEnabled(context)
        if (persistentSessionEnabled || geminiLiveState.occupiesMicrophone) {
            GeminiLiveForegroundService.stop(context)
        } else if (hasPermission) {
            if (OfflineGeminiCommandController.hasUsableInternet(context)) {
                GeminiLiveForegroundService.start(context)
            } else {
                OfflineGeminiCommandController.listenOnce(context)
            }
        } else {
            geminiAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val items = remember {
        listOf(
            DashboardMediaItem("youtube", "YouTube", "▶", Color(0xFFFF3B30), listOf("com.google.android.youtube", "com.google.android.youtube.tv"), video = true),
            DashboardMediaItem("music", "Müzik", "♫", Color(0xFFB56CFF)),
            DashboardMediaItem("radio", "Radyo", "▤", Color(0xFFFFA43A)),
            DashboardMediaItem("chrome", "Chrome", "◎", Color(0xFF4D9DFF), listOf("com.android.chrome")),
            DashboardMediaItem("gemini", "Gemini Live", "✦", Color(0xFF8F8CFF), listOf("com.google.android.apps.bard")),
            DashboardMediaItem("livefollow", "Canlı Takip", "◎", Color(0xFF31E39A))
        )
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullScreenId by remember { mutableStateOf<String?>(null) }
    var showMusicChooser by remember { mutableStateOf(false) }
    // 136: Müzik ikonunu ekolayzere çeviren gerçek çalma durumu. MediaAppController.isMusicPlaying()
    // Gemini üzerinden başlatılan/durdurulan çalmayı da kapsar, bu yüzden buradaki poll üst bardaki
    // ile aynı mantığı izler.
    var musicPlaying by remember { mutableStateOf(false) }
    var radioPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            musicPlaying = MediaAppController.isMusicPlaying(context)
            radioPlaying = MediaAppController.isRadioPlaying(context)
            delay(300L)
        }
    }
    val musicApps = remember(showMusicChooser) {
        if (showMusicChooser) MediaAppController.musicApps(context) else emptyList()
    }
    val selected = items.firstOrNull { it.id == selectedId }
    val fullScreenItem = items.firstOrNull { it.id == fullScreenId }
    val videoLocked = speedKmh >= 5

    LaunchedEffect(videoLocked) {
        if (videoLocked) {
            if (selected?.video == true) selectedId = null
            if (fullScreenItem?.video == true) fullScreenId = null
        }
    }

    // 98: Bağlantı/dinleme geri bildirimi kısa süre görünür. Live dinlemeye
    // geçtiğinde dört saniye sonra multimedya ızgarasına dönülür; oturum ve
    // foreground service arka planda kesintisiz devam eder.
    LaunchedEffect(selectedId, geminiLiveState.status) {
        if (GeminiLiveUiPolicy.shouldAutoCollapse(selectedId, geminiLiveState.status)) {
            delay(GeminiLiveUiPolicy.AUTO_COLLAPSE_DELAY_MS)
            if (GeminiLiveUiPolicy.shouldAutoCollapse(selectedId, geminiLiveState.status)) {
                selectedId = null
            }
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                DashboardPaletteRuntime.isDayTheme && selected == null -> Color(0xF0DEAB62)
                DashboardPaletteRuntime.isSunlight -> Color(0xFF222D36)
                else -> MultimediaBlack
            }
        ),
        border = BorderStroke(
            if (DashboardPaletteRuntime.isDayTheme && selected == null) 2.5.dp
            else if (DashboardPaletteRuntime.isSunlight) 1.8.dp else 1.dp,
            if (DashboardPaletteRuntime.isDayTheme && selected == null) DashboardPaletteRuntime.dayFrame
            else if (DashboardPaletteRuntime.isSunlight) DashboardPaletteRuntime.accent.copy(alpha = .82f)
            else MultimediaCyan.copy(alpha = .45f)
        )
    ) {
        if (selected == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        rowItems.forEach { item ->
                            val locked = item.video && videoLocked
                            val displayedItem = if (item.id == "gemini") {
                                when {
                                    geminiLiveState.status == GeminiLiveStatus.LISTENING ->
                                        item.copy(label = "Gemini • CANLI")
                                    geminiLiveState.status == GeminiLiveStatus.CONNECTING ->
                                        item.copy(label = "Gemini • BAĞLANIYOR")
                                    geminiLiveState.status == GeminiLiveStatus.ERROR &&
                                            geminiLiveState.isRecoverableError ->
                                        item.copy(label = "Gemini • YENİLENİYOR")
                                    geminiLiveState.status == GeminiLiveStatus.STOPPING ->
                                        item.copy(label = "Gemini • KAPANIYOR")
                                    else -> item
                                }
                            } else item
                            MediaShortcutTile(
                                item = displayedItem,
                                locked = locked,
                                geminiLiveState = if (item.id == "gemini") geminiLiveState else null,
                                isMusicPlaying = item.id == "music" && musicPlaying,
                                isRadioPlaying = item.id == "radio" && radioPlaying,
                                liveFollowActive = item.id == "livefollow" && liveFollowActive,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onSingleTap = {
                                    if (locked) {
                                        showVideoLock(context)
                                    } else if (item.id == "youtube") {
                                        openYouTube(context, item.packageNames)
                                    } else if (item.id == "music") {
                                        val apps = MediaAppController.musicApps(context)
                                        when (apps.size) {
                                            0 -> Toast.makeText(context, "Müzik oynatıcı bulunamadı.", Toast.LENGTH_SHORT).show()
                                            1 -> {
                                                val opened = MediaAppController.openMusicPackage(context, apps.first().packageName)
                                                musicPlaying = opened || musicPlaying
                                            }
                                            else -> showMusicChooser = true
                                        }
                                    } else if (item.id == "gemini") {
                                        selectedId = item.id
                                        if (!geminiLiveState.occupiesMicrophone && geminiLiveState.status != GeminiLiveStatus.LISTENING) {
                                            toggleGeminiLive()
                                        }
                                    } else if (item.id == "livefollow") {
                                        onLiveFollowClick()
                                    } else {
                                        selectedId = item.id
                                    }
                                },
                                onDoubleTap = {
                                    if (locked) showVideoLock(context)
                                    else if (item.id == "music") {
                                        // 137: Çift dokunuş PAUSE/DEVAM şeklinde çalışır.
                                        // Parça ve mevcut konum korunur; ikinci çift dokunuşta kaldığı yerden devam eder.
                                        if (musicPlaying) {
                                            MediaAppController.stopMedia(context)
                                            musicPlaying = false
                                        } else {
                                            MediaAppController.resumeMedia(context)
                                            musicPlaying = true
                                        }
                                    }
                                    else if (item.id == "radio") {
                                        if (radioPlaying) {
                                            MediaAppController.stopRadio(context)
                                            radioPlaying = false
                                        } else {
                                            MediaAppController.resumeRadio(context)
                                            radioPlaying = true
                                        }
                                    }
                                    else if (item.id == "youtube") openYouTube(context, item.packageNames)
                                    else if (item.id == "gemini") {
                                        selectedId = item.id
                                        if (!geminiLiveState.occupiesMicrophone && geminiLiveState.status != GeminiLiveStatus.LISTENING) {
                                            toggleGeminiLive()
                                        }
                                    }
                                    else if (item.id == "livefollow") onLiveFollowClick()
                                    else fullScreenId = item.id
                                }
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else if (selected.id == "gemini") {
            GeminiLivePanel(
                state = geminiLiveState,
                onToggle = ::toggleGeminiLive,
                onClose = { selectedId = null },
                onClearError = geminiLiveManager::clearError,
            )
        } else {
            InlineMediaContent(
                item = selected,
                url = mediaUrl(context, selected.id),
                isMusicPlaying = musicPlaying,
                onClose = { selectedId = null },
                onExpand = {
                    if (selected.id == "music") {
                        val opened = MediaAppController.openMusic(context)
                        musicPlaying = opened || musicPlaying
                    }
                    else if (selected.id == "radio") MediaAppController.openRadio(context)
                    else if (selected.id == "youtube" && openInstalledApp(context, selected.packageNames)) Unit
                    else fullScreenId = selected.id
                }
            )
        }
    }

    if (fullScreenItem != null) {
        val url = mediaUrl(context, fullScreenItem.id)
        if (url != null) {
            FullScreenMediaPage(fullScreenItem.label, url) { fullScreenId = null }
        }
    }

    if (showMusicChooser) {
        AlertDialog(
            onDismissRequest = { showMusicChooser = false },
            title = { Text("Müzik oynatıcı seçin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    musicApps.forEach { app ->
                        TextButton(
                            onClick = {
                                val opened = MediaAppController.openMusicPackage(context, app.packageName)
                                musicPlaying = opened || musicPlaying
                                showMusicChooser = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(app.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMusicChooser = false }) { Text("KAPAT") }
            }
        )
    }
}

@Composable
private fun MediaShortcutTile(
    item: DashboardMediaItem,
    locked: Boolean,
    geminiLiveState: GeminiLiveState? = null,
    isMusicPlaying: Boolean = false,
    isRadioPlaying: Boolean = false,
    liveFollowActive: Boolean = false,
    modifier: Modifier,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    Surface(
        modifier = modifier.pointerInput(item.id, locked) {
            detectTapGestures(onTap = { onSingleTap() }, onDoubleTap = { onDoubleTap() })
        },
        shape = RoundedCornerShape(11.dp),
        color = when {
            DashboardPaletteRuntime.isDayTheme && locked -> Color(0xFF8C7B66)
            DashboardPaletteRuntime.isDayTheme -> androidx.compose.ui.graphics.lerp(Color(0xFFA9794A), item.color, .16f)
            locked -> Color(0xFF161A1F)
            else -> item.color.copy(alpha = .15f)
        },
        border = if (DashboardPaletteRuntime.isDayTheme) BorderStroke(1.5.dp, DashboardPaletteRuntime.dayTileBorder) else null
    ) {
        Column(
            Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (locked) {
                Text("🔒", color = MultimediaRed, fontSize = 36.sp)
            } else if (item.id == "gemini" && geminiLiveState != null) {
                GeminiAnimatedLiveIcon(
                    state = geminiLiveState,
                    modifier = Modifier.size(54.dp),
                    compact = true,
                )
            } else if (item.id == "livefollow") {
                // Enlarge the primary tracking icon so the radar fills the tile.
                LiveFollowSignalIcon(isActive = liveFollowActive, modifier = Modifier.size(66.dp))
            } else if (item.id == "radio") {
                // 141: Ekolayzer kaldırıldı. Radyo aktifken ikon hafifçe sola-sağa
                // döner; radyo kapalıyken herhangi bir animasyon oynatılmaz.
                RadioGentleSpinIcon(isActive = isRadioPlaying, modifier = Modifier.size(54.dp))
            } else {
                // _43: kutu ve sağ sütun geometrisini değiştirmeden 10 inçte
                // ikonların görünür alanını yaklaşık %23 büyüt.
                MediaItemIcon(item, Modifier.size(54.dp), isPlaying = isMusicPlaying)
            }
            Text(item.label, color = if (locked) Color.Gray else if (DashboardPaletteRuntime.isDayTheme) DashboardPaletteRuntime.dayInk else DashboardPaletteRuntime.primaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}


@Composable
private fun LiveFollowSignalIcon(isActive: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveFollowRadarPremium")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liveFollowRadarPulse"
    ).value

    Canvas(modifier = modifier) {
        val u = size.minDimension
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val accent = if (isActive) Color(0xFF32F5B5) else Color(0xFF8196A8)
        val dark = Color(0xFF07131E)

        // Premium dark-glass medallion with a crisp metallic rim.
        drawCircle(
            color = Color.Black.copy(alpha = .48f),
            radius = u * .495f,
            center = androidx.compose.ui.geometry.Offset(c.x, c.y + u * .025f)
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF405A6B), Color(0xFF0B1C2A), Color(0xFF263D4E)),
                start = androidx.compose.ui.geometry.Offset(u * .12f, u * .08f),
                end = androidx.compose.ui.geometry.Offset(u * .9f, u * .95f)
            ),
            radius = u * .485f,
            center = c
        )
        drawCircle(color = Color(0xFF02070C), radius = u * .435f, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF173747), dark, Color(0xFF03080E)),
                center = androidx.compose.ui.geometry.Offset(c.x - u * .10f, c.y - u * .16f),
                radius = u * .64f
            ),
            radius = u * .42f,
            center = c
        )

        // Connected state gets a moving radar sweep, with a soft 3D glow behind it.
        if (isActive) {
            val sweep = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x - u * .40f, c.y - u * .14f)
                quadraticTo(c.x - u * .28f, c.y, c.x - u * .40f, c.y + u * .14f)
                close()
            }
            rotate(degrees = pulse * 360f, pivot = c) {
                drawPath(
                    path = sweep,
                    brush = Brush.linearGradient(
                        colors = listOf(accent.copy(alpha = .02f), accent.copy(alpha = .25f)),
                        start = c,
                        end = androidx.compose.ui.geometry.Offset(c.x - u * .40f, c.y)
                    )
                )
                drawLine(
                    color = accent.copy(alpha = .92f),
                    start = c,
                    end = androidx.compose.ui.geometry.Offset(c.x, c.y - u * .39f),
                    strokeWidth = u * .018f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            drawCircle(
                color = accent.copy(alpha = .12f + pulse * .10f),
                radius = u * .10f,
                center = c
            )
        }

        // Radar rings remain visible when idle; active mode emits two clean pulses.
        drawCircle(accent.copy(alpha = if (isActive) .35f else .24f), u * .285f, c,
            style = Stroke(width = u * .018f))
        drawCircle(accent.copy(alpha = if (isActive) .62f else .30f), u * .215f, c,
            style = Stroke(width = u * .014f))
        if (isActive) {
            repeat(2) { index ->
                val phase = (pulse + index * .5f) % 1f
                val radius = u * (.22f + phase * .20f)
                drawCircle(
                    color = accent.copy(alpha = (1f - phase) * .58f),
                    radius = radius,
                    center = c,
                    style = Stroke(width = u * .018f)
                )
            }
        }

        // Four short compass ticks give the radar a precise instrument-panel feel.
        for (angle in listOf(0f, 90f, 180f, 270f)) {
            rotate(angle, pivot = c) {
                drawLine(
                    color = accent.copy(alpha = if (isActive) .9f else .55f),
                    start = androidx.compose.ui.geometry.Offset(c.x, c.y - u * .375f),
                    end = androidx.compose.ui.geometry.Offset(c.x, c.y - u * .335f),
                    strokeWidth = u * .022f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        // Top-down vehicle silhouette: body, shadow, glass roof and headlamps.
        val carW = u * .255f
        val carH = u * .435f
        val carLeft = c.x - carW / 2f
        val carTop = c.y - carH / 2f
        drawRoundRect(
            color = Color.Black.copy(alpha = .72f),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + u * .018f, carTop + u * .025f),
            size = androidx.compose.ui.geometry.Size(carW, carH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .075f, u * .075f)
        )
        // Wheels sit just outside the body for a readable silhouette at 54 dp.
        val wheelW = u * .045f
        val wheelH = u * .105f
        listOf(
            androidx.compose.ui.geometry.Offset(carLeft - wheelW * .45f, carTop + carH * .19f),
            androidx.compose.ui.geometry.Offset(carLeft + carW - wheelW * .55f, carTop + carH * .19f),
            androidx.compose.ui.geometry.Offset(carLeft - wheelW * .45f, carTop + carH * .68f),
            androidx.compose.ui.geometry.Offset(carLeft + carW - wheelW * .55f, carTop + carH * .68f)
        ).forEach { p ->
            drawRoundRect(
                color = Color(0xFF02060A),
                topLeft = p,
                size = androidx.compose.ui.geometry.Size(wheelW, wheelH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .018f, u * .018f)
            )
        }
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFD8EAF2), Color(0xFF6F8D9E)),
                start = androidx.compose.ui.geometry.Offset(carLeft, carTop),
                end = androidx.compose.ui.geometry.Offset(carLeft + carW, carTop + carH)
            ),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft, carTop),
            size = androidx.compose.ui.geometry.Size(carW, carH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .065f, u * .065f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF5CE8FF), Color(0xFF153F59), Color(0xFF081D2C)),
                start = androidx.compose.ui.geometry.Offset(carLeft + u * .025f, carTop + u * .07f),
                end = androidx.compose.ui.geometry.Offset(carLeft + carW - u * .025f, carTop + carH * .53f)
            ),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + u * .035f, carTop + u * .075f),
            size = androidx.compose.ui.geometry.Size(carW - u * .07f, carH * .39f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .035f, u * .035f)
        )
        drawRoundRect(
            color = Color(0xFF183344),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + u * .045f, carTop + carH * .52f),
            size = androidx.compose.ui.geometry.Size(carW - u * .09f, carH * .12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .018f, u * .018f)
        )
        drawRoundRect(
            color = Color(0xFFBDEFFF),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + u * .035f, carTop + u * .035f),
            size = androidx.compose.ui.geometry.Size(carW * .25f, u * .025f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .012f, u * .012f)
        )
        drawRoundRect(
            color = if (isActive) Color(0xFFB9FFE8) else Color(0xFFD4DEE5),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + u * .035f, carTop + carH - u * .055f),
            size = androidx.compose.ui.geometry.Size(u * .045f, u * .025f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .01f, u * .01f)
        )
        drawRoundRect(
            color = if (isActive) Color(0xFFB9FFE8) else Color(0xFFD4DEE5),
            topLeft = androidx.compose.ui.geometry.Offset(carLeft + carW - u * .08f, carTop + carH - u * .055f),
            size = androidx.compose.ui.geometry.Size(u * .045f, u * .025f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .01f, u * .01f)
        )
        // Small live-status jewel at the lower edge.
        drawCircle(
            color = accent.copy(alpha = .22f),
            radius = u * .065f,
            center = androidx.compose.ui.geometry.Offset(c.x, c.y + u * .32f)
        )
        drawCircle(color = accent, radius = u * .028f,
            center = androidx.compose.ui.geometry.Offset(c.x, c.y + u * .32f))
        drawCircle(color = Color.White.copy(alpha = .8f), radius = u * .010f,
            center = androidx.compose.ui.geometry.Offset(c.x - u * .009f, c.y + u * .311f))
    }
}

@Composable
private fun RadioGentleSpinIcon(isActive: Boolean, modifier: Modifier = Modifier) {
    // 141: Radyo aktifken ikon -14° ile +14° arasında yumuşakça sallanır (soldan sağa
    // hafif dönüş). Radyo kapalıyken açı sıfıra döner ve animasyon durur.
    val transition = rememberInfiniteTransition(label = "radioGentleSpin")
    val angle = transition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radioGentleSpinAngle"
    ).value
    Image(
        painter = painterResource(if (DashboardPaletteRuntime.isDayTheme) R.drawable.icon_radio_day else R.drawable.icon_radio),
        contentDescription = "Radyo",
        modifier = modifier.graphicsLayer { rotationZ = if (isActive) angle else 0f },
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun GeminiLivePanel(
    state: GeminiLiveState,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val active = state.occupiesMicrophone
    val statusColor = when (state.status) {
        GeminiLiveStatus.LISTENING -> MultimediaGreen
        GeminiLiveStatus.CONNECTING,
        GeminiLiveStatus.STOPPING -> MultimediaCyan
        GeminiLiveStatus.ERROR -> if (state.isRecoverableError) MultimediaCyan else MultimediaRed
        GeminiLiveStatus.NEEDS_SETUP -> MultimediaRed
        GeminiLiveStatus.IDLE -> Color(0xFF8F8CFF)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MultimediaBlack)
    ) {
        TextButton(
            onClick = {
                GeminiLiveForegroundService.stop(context)
                onClearError()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = "✕",
                color = MultimediaRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 42.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GeminiAnimatedLiveIcon(
                    state = state,
                    modifier = Modifier.size(58.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "GEMINI LIVE",
                        color = DashboardPaletteRuntime.primaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = state.message.ifBlank { "Gemini Live hazır" },
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 3,
                    )
                    if (!active) {
                        TextButton(
                            onClick = onToggle,
                            enabled = state.status != GeminiLiveStatus.STOPPING,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = if (state.status == GeminiLiveStatus.ERROR || state.status == GeminiLiveStatus.NEEDS_SETUP) "YENİDEN DENE" else "SOHBETİ AÇ",
                                color = MultimediaCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GeminiAnimatedLiveIcon(
                    state = state,
                    modifier = Modifier.size(66.dp),
                )
                Text(
                    text = "GEMINI LIVE",
                    color = DashboardPaletteRuntime.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = state.message,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                TextButton(
                    onClick = if (active) onClose else onToggle,
                    enabled = state.status != GeminiLiveStatus.STOPPING,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (active) "ARKA PLANDA SÜRDÜR" else "YENİDEN DENE",
                        color = MultimediaCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeminiAnimatedLiveIcon(
    state: GeminiLiveState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val active = state.occupiesMicrophone
    val speaking = !compact && state.speechActivity != GeminiSpeechActivity.NONE
    // 99: Büyük panel normal hızda; 4 saniye sonra yerine dönen küçük CANLI ikon
    // yalnız tek, yavaş dönüş animasyonu kullanır. Dalga ve renk döngüsü küçük
    // ikonda çalışmadığı için 98'deki performans kazancı korunur.
    val rotating = if (compact) {
        state.status == GeminiLiveStatus.CONNECTING || state.status == GeminiLiveStatus.LISTENING
    } else {
        state.status == GeminiLiveStatus.CONNECTING ||
                (state.status == GeminiLiveStatus.LISTENING && state.speechActivity == GeminiSpeechActivity.NONE)
    }
    val rotationDurationMillis =
        if (compact) GeminiLiveUiPolicy.COMPACT_ROTATION_DURATION_MS else 5_200
    val animateColors = rotating && !compact
    val colorPhase = remember { Animatable(0f) }
    val rotationDegrees = remember { Animatable(0f) }

    LaunchedEffect(rotating, rotationDurationMillis) {
        if (rotating) {
            while (true) {
                rotationDegrees.snapTo(0f)
                rotationDegrees.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = rotationDurationMillis, easing = LinearEasing),
                )
            }
        } else {
            rotationDegrees.snapTo(0f)
        }
    }

    LaunchedEffect(animateColors) {
        if (animateColors) {
            while (true) {
                val next = colorPhase.value + 1f
                colorPhase.animateTo(
                    targetValue = next,
                    animationSpec = tween(durationMillis = 5_200, easing = LinearEasing),
                )
                if (colorPhase.value >= 4f) colorPhase.snapTo(0f)
            }
        }
        // Dönüş durduğunda son renk korunur.
    }

    val wavePhase = remember { Animatable(0f) }
    LaunchedEffect(speaking) {
        if (speaking) {
            while (true) {
                wavePhase.snapTo(0f)
                wavePhase.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1_350, easing = LinearEasing),
                )
            }
        } else {
            wavePhase.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minSide = minOf(size.width, size.height)
        val starRadius = minSide * 0.48f
        val inner = starRadius * 0.28f

        if (active && speaking) {
            val waveColor = when (state.speechActivity) {
                GeminiSpeechActivity.USER -> Color(0xFF52C7FF)
                GeminiSpeechActivity.GEMINI -> Color(0xFFFF7BD5)
                GeminiSpeechActivity.NONE -> Color.Transparent
            }
            repeat(3) { index ->
                val local = (wavePhase.value + index / 3f) % 1f
                val radius = starRadius * (1.10f + local * 1.05f)
                drawCircle(
                    color = waveColor.copy(alpha = (1f - local) * 0.62f),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = 1.8.dp.toPx()),
                )
            }
        }

        fun phaseColor(offset: Float): Color {
            val palette = listOf(
                Color(0xFFFF4F68), // Gemini kırmızı/pembe
                Color(0xFF4D8DFF), // mavi
                Color(0xFFFFD34A), // sarı
                Color(0xFF8D63FF), // mor
            )
            val pos = ((colorPhase.value + offset) % 4f + 4f) % 4f
            val i = pos.toInt().coerceIn(0, 3)
            val frac = pos - i
            return lerp(palette[i], palette[(i + 1) % palette.size], frac)
        }

        val path = Path().apply {
            moveTo(cx, cy - starRadius)
            cubicTo(
                cx + inner * 0.16f, cy - inner * 1.90f,
                cx + inner * 1.18f, cy - inner * 0.82f,
                cx + starRadius, cy,
            )
            cubicTo(
                cx + inner * 1.18f, cy + inner * 0.82f,
                cx + inner * 0.16f, cy + inner * 1.90f,
                cx, cy + starRadius,
            )
            cubicTo(
                cx - inner * 0.16f, cy + inner * 1.90f,
                cx - inner * 1.18f, cy + inner * 0.82f,
                cx - starRadius, cy,
            )
            cubicTo(
                cx - inner * 1.18f, cy - inner * 0.82f,
                cx - inner * 0.16f, cy - inner * 1.90f,
                cx, cy - starRadius,
            )
            close()
        }

        val brush = Brush.sweepGradient(
            colors = listOf(
                phaseColor(0f),
                phaseColor(1f),
                phaseColor(2f),
                phaseColor(3f),
                phaseColor(0f),
            ),
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        rotate(
            degrees = if (rotating) rotationDegrees.value else 0f,
            pivot = androidx.compose.ui.geometry.Offset(cx, cy),
        ) {
            drawPath(path = path, brush = brush)
        }

        // Canlı durumdayken merkeze küçük beyaz parlama ver; kapalıyken daha sakin görünür.
        if (active) {
            drawCircle(
                color = Color.White.copy(alpha = if (speaking) 0.92f else 0.68f),
                radius = starRadius * 0.10f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
            )
        }
    }
}

@Composable
private fun MediaItemIcon(item: DashboardMediaItem, modifier: Modifier, isPlaying: Boolean = false) {
    when {
        item.id == "music" && isPlaying -> MusicEqualizerIcon(modifier)
        item.id == "music" -> Image(painterResource(if (DashboardPaletteRuntime.isDayTheme) R.drawable.icon_music_day else R.drawable.icon_music), "Müzik", modifier, contentScale = ContentScale.Fit)
        item.id == "radio" -> Image(painterResource(if (DashboardPaletteRuntime.isDayTheme) R.drawable.icon_radio_day else R.drawable.icon_radio), "Radyo", modifier, contentScale = ContentScale.Fit)
        else -> {
            val context = LocalContext.current
            val useNightSurfaceGray =
                !DashboardPaletteRuntime.isDay &&
                        item.id in setOf("youtube", "chrome", "gemini", "chatgpt")
            val bitmap = remember(item.packageNames, useNightSurfaceGray) {
                loadInstalledIcon(context, item.packageNames)?.let { source ->
                    if (useNightSurfaceGray) source.withNightGrayWhites() else source
                }
            }
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), item.label, modifier, contentScale = ContentScale.Fit)
            } else {
                Text(item.symbol, color = item.color, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * 136: Müzik çalarken statik nota ikonu yerine gösterilen, 5 çubuklu, renkli ve canlı
 * ekolayzer animasyonu. Taban yeşil, orta sarı, tepe turuncu-kırmızı gradyanla çizilir;
 * her çubuk hafif faz farkıyla dalgalanır.
 */
@Composable
private fun MusicEqualizerIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            level = MediaAppController.currentAudioLevel(context)
            delay(45L)
        }
    }
    val transition = rememberInfiniteTransition(label = "multimediaMusicEqualizer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart),
        label = "multimediaMusicEqualizerPhase"
    ).value

    Canvas(modifier = modifier) {
        val barCount = 5
        val gap = size.width * 0.075f
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        val minH = size.height * 0.12f
        val maxH = size.height * 0.78f
        repeat(barCount) { i ->
            val wave = (kotlin.math.sin(phase + i * 0.9f) + 1f) / 2f
            val h = minH + (maxH - minH) * (level * (.50f + .50f * wave)).coerceIn(0f, 1f)
            val left = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = lerp(Color(0xFF31E39A), Color(0xFFFF6B4A), i / 4f),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun InlineMediaContent(
    item: DashboardMediaItem,
    url: String?,
    isMusicPlaying: Boolean = false,
    onClose: () -> Unit,
    onExpand: () -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(MultimediaBlack)) {
        if (url != null) {
            DashboardWebView(url, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MediaItemIcon(item, Modifier.size(48.dp), isPlaying = item.id == "music" && isMusicPlaying)
                Text("${item.label} oynatılıyor", color = DashboardPaletteRuntime.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (item.id == "radio") MediaAppController.resumeRadio(context)
                            else MediaAppController.resumeMedia(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF173A31))
                    ) { Text("OYNAT", fontSize = 8.sp) }
                    Button(
                        onClick = {
                            if (item.id == "radio") MediaAppController.stopRadio(context)
                            else MediaAppController.stopMedia(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF351C22))
                    ) { Text("DURDUR", fontSize = 8.sp) }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MediaOverlayButton("⛶", onExpand)
            MediaOverlayButton("×", onClose)
        }
    }
}

@Composable
private fun MediaOverlayButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = .82f))
    ) { Text(text, color = DashboardPaletteRuntime.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun FullScreenMediaPage(title: String, url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            DashboardWebView(url, Modifier.fillMaxSize())
            Text(title, color = DashboardPaletteRuntime.primaryText, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .75f)).padding(12.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                MediaOverlayButton("×", onClose)
            }
        }
    }
}

@Composable
private fun DashboardWebView(url: String, modifier: Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mobileLayout = url.contains("youtube.com", ignoreCase = true)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.useWideViewPort = !mobileLayout
                settings.loadWithOverviewMode = true
                if (mobileLayout) {
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)
                        if (mobileLayout) {
                            view.evaluateJavascript(
                                """
                                (function() {
                                  var viewport = document.querySelector('meta[name="viewport"]');
                                  if (!viewport) {
                                    viewport = document.createElement('meta');
                                    viewport.name = 'viewport';
                                    document.head.appendChild(viewport);
                                  }
                                  viewport.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
                                  document.documentElement.style.maxWidth = '100vw';
                                  document.documentElement.style.overflowX = 'hidden';
                                  document.body.style.maxWidth = '100vw';
                                  document.body.style.overflowX = 'hidden';
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(url)
                webView = this
            }
        },
        update = { view -> if (view.url != url) view.loadUrl(url) }
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

private fun mediaUrl(context: Context, id: String): String? {
    val raw = when (id) {
        "youtube" -> "https://www.youtube.com"
        "chrome" -> "https://www.google.com"
        "gemini" -> "https://gemini.google.com"
        "chatgpt" -> "https://chatgpt.com"
        else -> return null
    }
    if (raw.isBlank()) return null
    return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
}

private fun showVideoLock(context: Context) {
    Toast.makeText(context, "YouTube araç hareket halindeyken kilitlidir.", Toast.LENGTH_SHORT).show()
}

private fun openInstalledApp(context: Context, packageNames: List<String>): Boolean {
    val launchIntent = packageNames.firstNotNullOfOrNull { packageName ->
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
    } ?: return false
    return runCatching {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

private fun openYouTube(context: Context, packageNames: List<String>) {
    if (openInstalledApp(context, packageNames)) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "YouTube açılamadı.", Toast.LENGTH_SHORT).show()
    }
}

private fun openAppOrWeb(context: Context, item: DashboardMediaItem) {
    if (openInstalledApp(context, item.packageNames)) return
    val url = when (item.id) {
        "gemini" -> "https://gemini.google.com"
        "chatgpt" -> "https://chatgpt.com"
        else -> return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "${item.label} açılamadı.", Toast.LENGTH_SHORT).show()
    }
}

private fun loadInstalledIcon(context: Context, packageNames: List<String>): Bitmap? {
    val drawable = packageNames.firstNotNullOfOrNull { packageName ->
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    } ?: return null
    return drawable.toBitmapSafe()
}

private fun Drawable.toBitmapSafe(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
    }
}

/** Gece modunda uygulama ikonlarının yalnız beyaz/açık yüzeylerini BT gri tonuna çevirir. */
private fun Bitmap.withNightGrayWhites(): Bitmap {
    val result = copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(result.width * result.height)
    result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

    pixels.indices.forEach { index ->
        val pixel = pixels[index]
        val alpha = android.graphics.Color.alpha(pixel)
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        if (alpha > 0 && red >= 210 && green >= 210 && blue >= 210) {
            val brightness = (red + green + blue) / 3
            pixels[index] = android.graphics.Color.argb(
                alpha,
                100 * brightness / 255,
                108 * brightness / 255,
                116 * brightness / 255
            )
        }
    }

    result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    return result
}