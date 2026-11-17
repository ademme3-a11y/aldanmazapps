package com.aldanmaz.drivedashboard.ui.screen.drive

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
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Tek kamera kanali (VLC oynatici motoru): kameranin RTSP akisini VLC ile ayni sekilde acar.
 * Goruntu yoksa durum metni gosterir; kamerayi kayittan alikoyan hicbir komut gondermez (yalnizca izler).
 */
@Composable
private fun DashcamPane(url: String, label: String, attempt: Int, big: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Bağlanıyor…") }
    val layout = remember { VLCVideoLayout(ctx) }

    DisposableEffect(url, attempt) {
        status = "Bağlanıyor…"
        var lib: LibVLC? = null
        // 12 sn icinde goruntu gelmezse hicbir sey olmuyormus gibi birakmayiz; durumu acikca yazariz.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val watchdog = Runnable { if (status.startsWith("Bağlanıyor")) status = "12 sn içinde görüntü gelmedi. VLC uygulamasında aynı adres açılıyor mu? Adres: $url" }
        handler.postDelayed(watchdog, 12_000L)
        var mp: MediaPlayer? = null
        try {
            // VLC uygulamasinin varsayilanlariyla ayni: TCP'yi zorlamayiz, donanim kod cozmeyi elle ayarlamayiz.
            lib = LibVLC(ctx, arrayListOf("--network-caching=400"))
            val player = MediaPlayer(lib)
            mp = player
            player.attachViews(layout, null, false, false)
            player.setEventListener { ev ->
                when (ev.type) {
                    MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> status = ""
                    MediaPlayer.Event.Opening -> status = "Bağlanıyor… (açılıyor)"
                    MediaPlayer.Event.Buffering -> if (status.isNotEmpty()) status = "Bağlanıyor… (arabellek %${ev.buffering.toInt()})"
                    MediaPlayer.Event.EncounteredError -> status = "Bağlanamadı: VLC motoru akışı açamadı (EncounteredError). Adres: $url"
                    MediaPlayer.Event.EndReached -> status = "Yayın sona erdi."
                    MediaPlayer.Event.Stopped -> if (status.startsWith("Bağlanıyor")) status = "Durduruldu (Stopped), görüntü gelmedi."
                    else -> Unit
                }
            }
            val media = Media(lib, Uri.parse(url))
            media.addOption(":network-caching=400")
            player.media = media
            media.release()
            player.play()
        } catch (t: Throwable) {
            status = "Oynatıcı başlatılamadı: ${t.javaClass.simpleName} ${(t.message ?: "").take(80)}"
        }
        onDispose {
            handler.removeCallbacks(watchdog)
            try {
                mp?.setEventListener(null)
                mp?.stop()
                mp?.detachViews()
                mp?.release()
                lib?.release()
            } catch (_: Throwable) { }
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize())
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
