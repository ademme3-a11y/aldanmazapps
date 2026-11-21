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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import android.content.ClipData
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
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import java.net.InetSocketAddress
import java.net.Socket
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Kameranin Wi-Fi agi (internetsiz olsa da). */
private fun findWifiNetwork(cm: ConnectivityManager?): Network? = try {
    @Suppress("DEPRECATION")
    cm?.allNetworks?.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
} catch (_: Throwable) { null }

private class Probe(val viaWifi: Boolean?, val viaDefault: Boolean, val text: String)

/** Kameranin RTSP kapisina (varsayilan 554) iki yoldan TCP baglantisi dener ve nedeni aciklar. Arka plan thread'inde calistirilmalidir. */
private fun probeCamera(ctx: Context, url: String): Probe {
    val uri = Uri.parse(url)
    val host = uri.host ?: return Probe(null, false, "adres çözülemedi")
    val port = if (uri.port > 0) uri.port else 554
    fun tryConnect(make: () -> Socket): String? = try {
        make().use { it.connect(InetSocketAddress(host, port), 2_500) }; null
    } catch (t: Throwable) { "${t.javaClass.simpleName}: ${(t.message ?: "").take(40)}" }
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val wifi = findWifiNetwork(cm)
    val wifiErr = if (wifi == null) "Wi-Fi ağı yok" else tryConnect { wifi.socketFactory.createSocket() }
    val defErr = tryConnect { Socket() }
    val perm = if (Build.VERSION.SDK_INT >= 37) {
        if (ctx.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED) "izin var" else "izin YOK"
    } else "izin gerekmez"
    val text = "$host:$port • Wi-Fi ile: ${wifiErr ?: "AÇIK"} • Varsayılan ağ ile: ${defErr ?: "AÇIK"} • Yerel ağ izni: $perm • Android API ${Build.VERSION.SDK_INT}"
    return Probe(if (wifi == null) null else wifiErr == null, defErr == null, text)
}

/** Taranacak olasi RTSP yollari (70mai: on kamera /livestream/12). */
private val SCAN_PATHS: List<String> =
    (0..30).map { "livestream/$it" } +
    listOf("livestream", "live", "live/0", "live/1", "live/ch0", "live/ch1", "stream", "stream0", "stream1", "stream2",
        "ch0", "ch1", "ch01", "ch02", "main", "sub", "front", "rear", "back", "video", "video0", "video1", "video2", "h264", "0", "1", "2")

/**
 * Kameranin RTSP kapisina sirayla DESCRIBE gonderip hangi yollarin var oldugunu bulur (yalnizca sorar, yayin baslatmaz).
 * Arka plan thread'inde calistirilmalidir.
 */
private fun scanRtsp(ctx: Context, baseUrl: String, progress: (String) -> Unit): String {
    val uri = Uri.parse(baseUrl.ifBlank { "rtsp://192.168.0.1:554/livestream/12" })
    val host = uri.host ?: "192.168.0.1"
    val port = if (uri.port > 0) uri.port else 554
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val wifi = findWifiNetwork(cm)
    val found = ArrayList<String>()   // 200 donenler
    val auth = ArrayList<String>()    // 401/403 donenler (yol var ama yetki ister)
    val other = java.util.TreeMap<String, Int>()
    var timeouts = 0
    var tested = 0
    for (path in SCAN_PATHS) {
        progress("Taranıyor… ${tested + 1}/${SCAN_PATHS.size}  ($path)  bulunan: ${found.size}")
        try {
            val sock = if (wifi != null) wifi.socketFactory.createSocket() else Socket()
            sock.use { sk ->
                sk.connect(InetSocketAddress(host, port), 2_500)
                sk.soTimeout = 3_000
                val req = "DESCRIBE rtsp://$host:$port/$path RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: AldDrive\r\n\r\n"
                sk.getOutputStream().write(req.toByteArray())
                sk.getOutputStream().flush()
                val buf = ByteArray(4096)
                val n = sk.getInputStream().read(buf)
                val resp = if (n > 0) String(buf, 0, n) else ""
                val code = Regex("RTSP/1\\.\\d (\\d{3})").find(resp)?.groupValues?.get(1) ?: "?"
                val tracks = Regex("(?m)^m=(\\w+)").findAll(resp).joinToString("+") { it.groupValues[1] }
                when (code) {
                    "200" -> found += "/$path" + if (tracks.isNotEmpty()) " [$tracks]" else ""
                    "401", "403" -> auth += "/$path ($code)"
                    else -> other[code] = (other[code] ?: 0) + 1
                }
            }
        } catch (t: Throwable) {
            timeouts++
            other["hata:${t.javaClass.simpleName}"] = (other["hata:${t.javaClass.simpleName}"] ?: 0) + 1
            if (timeouts >= 4 && found.isEmpty()) break
        }
        tested++
        try { Thread.sleep(300) } catch (_: InterruptedException) { break }
    }
    val sb = StringBuilder("Tarama bitti: $tested yol denendi ($host:$port, ${if (wifi != null) "Wi-Fi" else "varsayılan ağ"}).\n")
    sb.append(if (found.isEmpty()) "Yanıt veren (200) yol YOK.\n" else "VAR olan yollar: ${found.joinToString(", ")}\n")
    if (auth.isNotEmpty()) sb.append("Yetki isteyen: ${auth.joinToString(", ")}\n")
    if (other.isNotEmpty()) sb.append("Diğer yanıtlar: ${other.entries.joinToString(", ") { "${it.key}×${it.value}" }}")
    return sb.toString()
}

/**
 * 70mai uygulamasinin kodunda on = /livestream/12, arka = /livestream/13. Bu tani, ikisine de DESCRIBE gonderip
 * kameranin TAM yanit satirlarini gosterir; ayrica uygulamanin her birkac saniyede yaptigi HTTP "register" cagrisini
 * (imzasiz) dener ve ardindan /13'u yeniden sorar. Yalnizca sorgular, kayit/ayar degistirmez.
 */
private fun diagRear(ctx: Context, baseUrl: String, progress: (String) -> Unit): String {
    val uri = Uri.parse(baseUrl.ifBlank { "rtsp://192.168.0.1:554/livestream/12" })
    val host = uri.host ?: "192.168.0.1"
    val port = if (uri.port > 0) uri.port else 554
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val wifi = findWifiNetwork(cm)
    fun sock(): Socket = if (wifi != null) wifi.socketFactory.createSocket() else Socket()
    fun describe(path: String): String = try {
        sock().use { sk ->
            sk.connect(InetSocketAddress(host, port), 2_500); sk.soTimeout = 3_000
            sk.getOutputStream().write("DESCRIBE rtsp://$host:$port/$path RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: AldDrive\r\n\r\n".toByteArray())
            sk.getOutputStream().flush()
            val buf = ByteArray(4096); var total = 0
            try { while (total < buf.size) { val n = sk.getInputStream().read(buf, total, buf.size - total); if (n <= 0) break; total += n; if (total > 600) break } } catch (_: java.net.SocketTimeoutException) { }
            val resp = String(buf, 0, total)
            val head = resp.lineSequence().take(3).joinToString(" | ") { it.trim() }
            val media = Regex("(?m)^m=(\\w+)[^\\r\\n]*").findAll(resp).joinToString(" ; ") { it.value.trim().take(40) }
            if (head.isBlank()) "yanıt yok (bağlantı hemen kapandı)" else head + if (media.isNotEmpty()) "  [$media]" else ""
        }
    } catch (t: Throwable) { "hata: ${t.javaClass.simpleName} ${(t.message ?: "").take(40)}" }
    fun http(path: String): String = try {
        var localIp = "192.168.0.100"
        sock().use { sk ->
            sk.connect(InetSocketAddress(host, 80), 2_500); sk.soTimeout = 3_000
            localIp = sk.localAddress?.hostAddress ?: localIp
            sk.getOutputStream().write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
            sk.getOutputStream().flush()
            val buf = ByteArray(1024); var total = 0
            try { while (total < buf.size) { val n = sk.getInputStream().read(buf, total, buf.size - total); if (n <= 0) break; total += n } } catch (_: java.net.SocketTimeoutException) { }
            val resp = String(buf, 0, total)
            val status = resp.lineSequence().firstOrNull()?.trim() ?: ""
            val body = resp.substringAfter("\r\n\r\n", "").replace("\n", " ").trim().take(120)
            "$status  gövde: $body  (yerel IP $localIp)"
        }
    } catch (t: Throwable) { "hata: ${t.javaClass.simpleName} ${(t.message ?: "").take(40)}" }
    val sb = StringBuilder()
    progress("Tanı: /livestream/12 …"); sb.append("12: ").append(describe("livestream/12")).append("\n")
    progress("Tanı: /livestream/13 …"); sb.append("13: ").append(describe("livestream/13")).append("\n")
    progress("Tanı: HTTP register …")
    val reg = http("/cgi-bin/client.cgi?&-operation=register&-ip=192.168.0.100")
    sb.append("register: ").append(reg).append("\n")
    progress("Tanı: /livestream/13 (tekrar) …"); sb.append("13 (register sonrası): ").append(describe("livestream/13")).append("\n")
    progress("Tanı: getdeviceattr …")
    sb.append("cihaz: ").append(http("/cgi-bin/getdeviceattr.cgi")).append("\n")
    return sb.toString()
}

/**
 * Tek kamera kanali (VLC oynatici motoru): kameranin RTSP akisini VLC ile ayni sekilde acar.
 * Goruntu yoksa durum metni gosterir; kamerayi kayittan alikoyan hicbir komut gondermez (yalnizca izler).
 */
@Composable
private fun DashcamPane(url: String, label: String, attempt: Int, big: Boolean, showStatus: Boolean, onLog: (String) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Bağlanıyor…") }
    val layout = remember { VLCVideoLayout(ctx) }
    var retryKey by remember(url, attempt) { mutableIntStateOf(0) }
    LaunchedEffect(status) { if (status.isNotEmpty() && !status.startsWith("Bağlanıyor… (arabellek")) onLog("$label: $status") }
    // Aktarim kipi: 0 = VLC varsayilani, 1 = RTSP over TCP, 2 = TCP + yazilim kod cozme. Hata olursa sirayla denenir.
    var mode by remember(url, attempt) { mutableIntStateOf(0) }

    DisposableEffect(url, attempt, retryKey, mode) {
        status = if (mode == 0) "Bağlanıyor…" else "Bağlanıyor… (deneme ${mode + 1}/3: " + (if (mode == 1) "TCP" else "TCP + yazılım çözme") + ")"
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
                    MediaPlayer.Event.EncounteredError -> {
                        if (mode < 2) {
                            // Kamera tek oturum tasiyor olabilir: once bu oturumu kapat, sonra bekleyip bir sonraki kipi dene.
                            handler.post { status = "Kamera oturumu kapatılıyor, sonraki kip denenecek…"; runCatching { player.stop() } }
                            handler.postDelayed({ mode += 1 }, 4_000L)
                            return@setEventListener
                        }
                        handler.post { runCatching { player.stop() } }   // yoklamadan once kendi baglantimizi birak
                        status = "Bağlanamadı (VLC motoru). Neden araştırılıyor…"
                        Thread {
                            Thread.sleep(3_000)
                            val pr = probeCamera(ctx, url)
                            handler.post {
                                if (pr.viaWifi == true && !pr.viaDefault && retryKey == 0) {
                                    // Kameraya yalnizca Wi-Fi uzerinden ulasiliyor: bu uygulamayi Wi-Fi'a baglayip bir kez daha dene.
                                    val cm = ctx.getSystemService(ConnectivityManager::class.java)
                                    val w = findWifiNetwork(cm)
                                    if (w != null && runCatching { cm.bindProcessToNetwork(w) }.getOrDefault(false)) {
                                        retryKey = 1
                                        return@post
                                    }
                                }
                                status = "Bağlanamadı. " + pr.text
                            }
                        }.start()
                    }
                    MediaPlayer.Event.EndReached -> if (mode < 2) { handler.post { status = "Yayın kapandı, oturum kapatılıp sonraki kip denenecek…"; runCatching { player.stop() } }; handler.postDelayed({ mode += 1 }, 4_000L) } else status = "Yayın sona erdi (3 aktarım kipi denendi): kamera bağlantıyı kapattı. Kamera aynı anda tek izleyiciye izin veriyor olabilir; 70mai/VLC uygulamaları kapalı mı? ⟳ ile yeniden deneyin."
                    MediaPlayer.Event.Stopped -> if (status.startsWith("Bağlanıyor")) status = "Durduruldu (Stopped), görüntü gelmedi."
                    else -> Unit
                }
            }
            val media = Media(lib, Uri.parse(url))
            media.addOption(":network-caching=400")
            if (mode >= 1) media.addOption(":rtsp-tcp")
            if (mode >= 2) media.addOption(":avcodec-hw=none")
            player.media = media
            media.release()
            player.play()
        } catch (t: Throwable) {
            status = "Oynatıcı başlatılamadı: ${t.javaClass.simpleName} ${(t.message ?: "").take(80)}"
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
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
        if (showStatus && status.isNotEmpty()) {
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
    val viewCtx = LocalContext.current
    // Kamera penceresi kapaninca uygulama yeniden varsayilan aga (mobil veri/harita) doner.
    DisposableEffect(Unit) {
        onDispose { runCatching { viewCtx.getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(null) } }
    }
    var draftFront by remember(frontUrl) { mutableStateOf(frontUrl) }
    var draftRear by remember(rearUrl) { mutableStateOf(rearUrl) }
    val shape = RoundedCornerShape(if (expanded) 0.dp else 12.dp)
    var scanning by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }   // tarama/tani sirasindaki tek satirlik ilerleme
    val logs = remember { mutableStateListOf<String>() }
    fun addLog(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val line = "[$t] $msg"
        if (logs.lastOrNull()?.substringAfter("] ") == msg) return
        logs.add(line)
        while (logs.size > 200) logs.removeAt(0)
    }
    // Tarama sirasinda canli yayinlar kapatilir: kamera tek oturum tasiyor olabilir.
    val channels = if (scanning) emptyList() else listOf("ÖN" to frontUrl, "ARKA" to rearUrl).filter { it.second.isNotBlank() }

    Box(modifier.clip(shape).background(Color.Black).border(if (expanded) 0.dp else 2.dp, Color(0xFF3A4652), shape)) {
        val sideBySide = expanded && landscape
        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((label, url) in channels) DashcamPane(url, label, attempt, true, !expanded, { addLog(it) }, Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((label, url) in channels) DashcamPane(url, label, attempt, expanded, !expanded, { addLog(it) }, Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (channels.isEmpty() && !expanded) {
            Text(
                "Kamera adresi girilmedi", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(16.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(10.dp)
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
            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).heightIn(max = 380.dp)
                    .background(Color(0xEE000000), RoundedCornerShape(12.dp)).padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("KAMERA AYARLARI — adresler (RTSP), boş bırakılan kanal gösterilmez", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(draftFront, { draftFront = it }, label = { Text("Ön kamera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draftRear, { draftRear = it }, label = { Text("Arka kamera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUrlsChange(draftFront.trim(), draftRear.trim()) }) { Text("BAĞLAN") }
                    Button(enabled = !scanning, onClick = {
                        scanning = true; scanStatus = "Tarama başlıyor…"
                        val h = android.os.Handler(android.os.Looper.getMainLooper())
                        val app = viewCtx.applicationContext
                        val base = draftFront.trim().ifBlank { frontUrl }
                        Thread {
                            val result = try { scanRtsp(app, base) { m -> h.post { scanStatus = m } } } catch (t: Throwable) { "Tarama başarısız: ${t.javaClass.simpleName} ${(t.message ?: "").take(60)}" }
                            h.post { addLog("TARAMA SONUCU:\n$result"); scanStatus = ""; scanning = false }
                        }.start()
                    }) { Text("ADRES TARA") }
                    Button(enabled = !scanning, onClick = {
                        scanning = true; scanStatus = "Tanı başlıyor…"
                        val h = android.os.Handler(android.os.Looper.getMainLooper())
                        val app = viewCtx.applicationContext
                        val base = draftFront.trim().ifBlank { frontUrl }
                        Thread {
                            val result = try { diagRear(app, base) { m -> h.post { scanStatus = m } } } catch (t: Throwable) { "Tanı başarısız: ${t.javaClass.simpleName} ${(t.message ?: "").take(60)}" }
                            h.post { addLog("ARKA TANI:\n$result"); scanStatus = ""; scanning = false }
                        }.start()
                    }) { Text("ARKA TANI") }
                }
                if (scanStatus.isNotEmpty()) Text(scanStatus, color = Color(0xFFFFD54F), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("GÜNLÜK (basılı tutup seçebilirsiniz)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        runCatching {
                            val cmgr = viewCtx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cmgr.setPrimaryClip(ClipData.newPlainText("kamera günlüğü", logs.joinToString("\n")))
                        }
                    }) { Text("KOPYALA") }
                    Button(onClick = { logs.clear() }) { Text("TEMİZLE") }
                }
                SelectionContainer {
                    Text(
                        if (logs.isEmpty()) "(günlük boş)" else logs.joinToString("\n"),
                        color = Color(0xFFE0E0E0), fontSize = 12.sp
                    )
                }
            }
        }
    }
}
