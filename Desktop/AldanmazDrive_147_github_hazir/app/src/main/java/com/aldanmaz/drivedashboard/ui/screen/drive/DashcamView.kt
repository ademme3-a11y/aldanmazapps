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

/**
 * Araç kamerası canlı görüntüsü (RTSP, düşük gecikme, TCP). Küçük pencere veya tam ekran.
 * Not: kameranın RTSP adresi modele göre değişir; adres alanından düzenlenir ve telefonda saklanır.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun DashcamView(
    url: String,
    expanded: Boolean,
    onUrlChange: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Bağlanıyor…") }
    var attempt by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var draftUrl by remember(url) { mutableStateOf(url) }

    DisposableEffect(url, attempt) {
        status = "Bağlanıyor…"
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wifi = findWifiNetwork(cm)
        val factory = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8_000L)
        if (wifi != null) factory.setSocketFactory(wifi.socketFactory)
        val p = ExoPlayer.Builder(ctx)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 1_500, 250, 500).build())
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status = ""
            }
            override fun onPlayerError(error: PlaybackException) {
                status = "Kamera bağlantısı kurulamadı. Telefon/multimedya kameranın Wi-Fi ağına bağlı mı? (${error.errorCodeName})"
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

    Box(
        modifier
            .clip(RoundedCornerShape(if (expanded) 0.dp else 12.dp))
            .background(Color.Black)
            .border(if (expanded) 0.dp else 2.dp, Color(0xFF3A4652), RoundedCornerShape(if (expanded) 0.dp else 12.dp))
    ) {
        AndroidView(
            factory = { SurfaceView(it) },
            update = { sv -> player?.setVideoSurfaceView(sv) },
            modifier = Modifier.fillMaxSize()
        )
        if (status.isNotEmpty()) {
            Text(
                status, color = Color.White, fontSize = if (expanded) 16.sp else 11.sp,
                modifier = Modifier.align(Alignment.Center).padding(16.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp)
            )
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
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text("KAMERA ADRESİ (RTSP)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draftUrl, { draftUrl = it }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { onUrlChange(draftUrl.trim()) }) { Text("BAĞLAN") }
                }
            }
        }
    }
}
