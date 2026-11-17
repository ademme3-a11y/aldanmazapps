package com.aldanmaz.drivedashboard.ui.screen.drive

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

/** Araç kamerasının (ör. 70mai) Wi-Fi ağındaki ağı bulur; RTSP soketleri bu ağa bağlanır, harita/internet mobil veride kalır. */
private fun findWifiNetwork(cm: ConnectivityManager?): Network? = try {
    @Suppress("DEPRECATION")
    cm?.allNetworks?.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
} catch (_: Throwable) { null }

/** Tek kamera kanali: RTSP/TCP, dusuk gecikme. Goruntu yoksa durum metni gosterir; kayit akisina mudahale etmez (yalnizca izler). */
@OptIn(UnstableApi::class)
@Composable
private fun DashcamPane(url: String, label: String, attempt: Int, big: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Bağlanıyor…") }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    // Ilk deneme Wi-Fi agina baglanir; olmazsa bir kez de sistem varsayilan agiyla denenir.
    var bindToWifi by remember(url, attempt) { mutableStateOf(true) }

    DisposableEffect(url, attempt, bindToWifi) {
        status = "Bağlanıyor…"
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wifi = findWifiNetwork(cm)
        val factory = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8_000L)
        if (wifi != null && bindToWifi) factory.setSocketFactory(wifi.socketFactory)
        val p = ExoPlayer.Builder(ctx)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 1_500, 250, 500).build())
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status = ""
            }
            override fun onPlayerError(error: PlaybackException) {
                if (bindToWifi && wifi != null) {
                    bindToWifi = false      // Wi-Fi'a ozel baglanti olmadi: varsayilan agla yeniden dene
                } else {
                    val why = (error.cause?.message ?: error.message ?: "").take(90)
                    status = "Bağlanamadı (${error.errorCodeName}) $why"
                }
            }
        })
        try {
            p.setMediaSource(factory.createMediaSource(MediaItem.fromUri(url)))
            p.prepare()
            p.playWhenReady = true
        } catch (t: Throwable) {
            status = "Adres geçersiz: ${t.message ?: url}"
        }
        player = p
        onDispose { player = null; p.release() }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { SurfaceView(it) },
            update = { sv -> player?.setVideoSurfaceView(sv) },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            label, color = Color.White, fontSize = if (big) 14.sp else 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (status.isNotEmpty()) {
            Text(
                status, color = Color.White, fontSize = if (big) 14.sp else 10.sp,
                modifier = Modifier.align(Alignment.Center).padding(12.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }
    }
}

/**
 * Araç kamerası (ör. 70mai) ön + arka canlı görüntü: küçük pencere veya tam ekran.
 * Adresler modele göre değişir; tam ekranda düzenlenir ve telefonda saklanır. Boş bırakılan kanal gösterilmez.
 */
@Composable
internal fun DashcamView(
    frontUrl: String,
    rearUrl: String,
    expanded: Boolean,
    landscape: Boolean,
    onUrlsChange: (String, String) -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var attempt by remember { mutableIntStateOf(0) }
    var draftFront by remember(frontUrl) { mutableStateOf(frontUrl) }
    var draftRear by remember(rearUrl) { mutableStateOf(rearUrl) }
    val shape = RoundedCornerShape(if (expanded) 0.dp else 12.dp)
    val channels = listOf("ÖN" to frontUrl, "ARKA" to rearUrl).filter { it.second.isNotBlank() }

    Box(modifier.clip(shape).background(Color.Black).border(if (expanded) 0.dp else 2.dp, Color(0xFF3A4652), shape)) {
        val sideBySide = expanded && landscape
        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((label, url) in channels) DashcamPane(url, label, attempt, true, Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((label, url) in channels) DashcamPane(url, label, attempt, expanded, Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (channels.isEmpty()) {
            Text("Kamera adresi girilmedi", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, action) in listOf("⟳" to { attempt++ }, (if (expanded) "▭" else "⤢") to onToggleExpand, "✕" to onClose)) {
                Text(
                    label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .clickable { action() }.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (expanded) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color(0xDD000000), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("KAMERA ADRESLERİ (RTSP) — boş bırakılan kanal gösterilmez", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(draftFront, { draftFront = it }, label = { Text("Ön kamera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draftRear, { draftRear = it }, label = { Text("Arka kamera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onUrlsChange(draftFront.trim(), draftRear.trim()) }) { Text("BAĞLAN") }
            }
        }
    }
}
