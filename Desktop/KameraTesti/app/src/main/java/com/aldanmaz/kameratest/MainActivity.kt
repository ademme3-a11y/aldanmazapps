package com.aldanmaz.kameratest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 70mai (resmi) uygulamasini acar: kamera tek bir RTSP akisi verir, hangi sensorun (on/arka) o akisa
 * bagli oldugunu yalnizca resmi uygulamadaki dugme degistirir. Burada hicbir kimlik dogrulama/komut
 * taklit edilmez; sadece Android'in normal "uygulama baslat" mekanizmasi kullanilir.
 */
private fun launchOfficialCameraApp(ctx: Context): Boolean {
    val candidatePackages = listOf("com.banyac.midrive.app.intl", "com.banyac.midrive.app", "com.mdc.v70mai")
    val pm = ctx.packageManager
    val intent = candidatePackages.firstNotNullOfOrNull { pkg -> runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() }
    return if (intent != null) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    } else {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.banyac.midrive.app.intl")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/**
 * Bagimsiz, kucuk bir test uygulamasi: yalnizca 70mai A800SE araç kamerasinin
 * arka kamera canli goruntusunu yakalamaya calisir. AldDrive'dan bagimsizdir.
 * Kameranin kimlik dogrulamasini (imza/connectKey) taklit etmez; yalnizca kameranin
 * zaten herkese acik birakmis oldugu RTSP izleme kapisini dener (DESCRIBE/PLAY).
 */

private const val DEFAULT_FRONT = "rtsp://192.168.0.1:554/livestream/12"

private val SCAN_PATHS: List<String> =
    (0..30).map { "livestream/$it" } +
        listOf(
            "livestream", "live", "live/0", "live/1", "live/ch0", "live/ch1", "stream", "stream0", "stream1", "stream2",
            "ch0", "ch1", "ch01", "ch02", "main", "sub", "front", "rear", "back", "video", "video0", "video1", "video2", "h264"
        )

private fun findWifiNetwork(cm: ConnectivityManager?): Network? {
    if (cm == null) return null
    for (n in cm.allNetworks) {
        val caps = cm.getNetworkCapabilities(n) ?: continue
        if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return n
    }
    return null
}

private fun nowStamp(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

/** Kameraya DESCRIBE gonderip tam yanit basligini ve varsa medya parcalarini dondurur. Yalniz sorar, yayin baslatmaz. */
private fun describe(host: String, port: Int, path: String, wifi: Network?): Pair<String, Boolean> = try {
    val sock = if (wifi != null) wifi.socketFactory.createSocket() else Socket()
    sock.use { sk ->
        sk.connect(InetSocketAddress(host, port), 2_500)
        sk.soTimeout = 3_000
        sk.getOutputStream().write(
            "DESCRIBE rtsp://$host:$port/$path RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: KameraTesti\r\n\r\n".toByteArray()
        )
        sk.getOutputStream().flush()
        val buf = ByteArray(2048)
        var total = 0
        try {
            while (total < buf.size) {
                val n = sk.getInputStream().read(buf, total, buf.size - total)
                if (n <= 0) break
                total += n
                if (total > 400) break
            }
        } catch (_: java.net.SocketTimeoutException) { }
        val resp = String(buf, 0, total)
        val head = resp.lineSequence().take(2).joinToString(" | ") { it.trim() }
        val ok = Regex("RTSP/1\\.\\d 200").containsMatchIn(resp)
        (if (head.isBlank()) "yanıt yok" else head) to ok
    }
} catch (t: Throwable) {
    "hata: ${t.javaClass.simpleName} ${(t.message ?: "").take(40)}" to false
}

/** Olasi tum RTSP yollarini dener; /12 (bilinen on kamera) disinda yanit veren bir yol cikarsa bunu "yakalama" sayar. */
private fun scanForRear(ctx: Context, knownFrontPath: String, log: (String) -> Unit): Pair<String, String?> {
    val uri = android.net.Uri.parse(DEFAULT_FRONT)
    val host = uri.host ?: "192.168.0.1"
    val port = if (uri.port > 0) uri.port else 554
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val wifi = findWifiNetwork(cm)
    val found = ArrayList<String>()
    var captured: String? = null
    var tested = 0
    for (path in SCAN_PATHS) {
        log("Taranıyor… ${tested + 1}/${SCAN_PATHS.size}  (/$path)  bulunan: ${found.size}")
        val (head, ok) = describe(host, port, path, wifi)
        if (ok) {
            found += "/$path"
            val full = "rtsp://$host:$port/$path"
            if (!full.equals(knownFrontPath, ignoreCase = true) && captured == null) captured = full
        }
        tested++
        try { Thread.sleep(250) } catch (_: InterruptedException) { break }
    }
    val summary = "Tarama bitti: $tested yol denendi. " +
        if (found.isEmpty()) "Yanıt veren yol yok." else "VAR olan yollar: ${found.joinToString(", ")}"
    return summary to captured
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KameraTestiScreen() }
    }
}

@Composable
private fun KameraTestiScreen() {
    val ctx = LocalContext.current
    var frontUrl by remember { mutableStateOf(DEFAULT_FRONT) }
    var rearUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var captured by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var fullScreenRearTest by remember { mutableStateOf(false) }

    // 70mai uygulamasindan (elle sensor degistirip) geri donulunce bu testin akisini otomatik tazeler.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var resumedOnce = false
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) attempt++
                resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun addLog(msg: String) {
        val line = "[${nowStamp()}] $msg"
        if (logs.lastOrNull()?.substringAfter("] ") == msg) return
        logs.add(line)
        while (logs.size > 300) logs.removeAt(0)
    }

    fun runScan() {
        busy = true; progress = "Tarama başlıyor…"
        val h = Handler(Looper.getMainLooper())
        val app = ctx.applicationContext
        Thread {
            val (summary, hit) = try {
                scanForRear(app, frontUrl.trim()) { m -> h.post { progress = m } }
            } catch (t: Throwable) {
                "Tarama başarısız: ${t.javaClass.simpleName} ${(t.message ?: "").take(60)}" to null
            }
            h.post {
                addLog("ADRES TARA SONUCU:\n$summary")
                if (hit != null) {
                    rearUrl = hit
                    captured = hit
                    addLog("YAKALANDI: Arka kamera adresi bulundu → $hit")
                }
                progress = ""; busy = false
            }
        }.start()
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0D1218)) {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Kamera Testi — 70mai A800SE arka kamera yakalama", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Bu uygulama kameranın kimlik doğrulamasını taklit etmez; yalnızca kameranın zaten açık bıraktığı " +
                    "izleme kapısını (RTSP) dener. AldDrive'dan bağımsızdır.",
                color = Color(0xFFB0BEC5), fontSize = 12.sp
            )

            Column(
                Modifier.fillMaxWidth().background(Color(0xFF14202B), RoundedCornerShape(10.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("TEST: 70mai ile elle değiştirip bu uygulamada izleme", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "1) 70MAİ'Yİ AÇ'a bas → 2) 70mai içinde kamerayı arkaya çevir → 3) geri dön (otomatik tazelenir) → " +
                        "4) TAM EKRAN ARKA TEST ile doğrula.",
                    color = Color(0xFFB0BEC5), fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launchOfficialCameraApp(ctx) }) { Text("70MAİ'Yİ AÇ") }
                    Button(onClick = { fullScreenRearTest = true; attempt++ }) { Text("TAM EKRAN ARKA TEST") }
                }
            }

            if (captured != null) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1B5E20), RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("✅ YAKALAMA BAŞARILI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Kamera, ön kameradan (/livestream/12) farklı ve yanıt veren bir yayın yolu verdi: $captured\n" +
                            "Bu, arka kameranın (ya da ikinci bir akışın) kimlik doğrulaması gerekmeden izlenebildiği anlamına gelir. " +
                            "Aşağıdan ARKA ÖNİZLEME ile görüntüyü doğrulayın; görüntü gerçekten arka kameraysa bu adresi AldDrive'ın " +
                            "\"Arka kamera\" alanına girerek entegre edebiliriz.",
                        color = Color(0xFFE8F5E9), fontSize = 12.sp
                    )
                }
            }

            OutlinedTextField(frontUrl, { frontUrl = it }, label = { Text("Ön kamera adresi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rearUrl, { rearUrl = it }, label = { Text("Arka kamera adresi (manuel ya da taramadan gelir)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { previewUrl = "front"; attempt++ }) { Text("ÖN ÖNİZLEME") }
                Button(enabled = rearUrl.isNotBlank(), onClick = { previewUrl = "rear"; attempt++ }) { Text("ARKA ÖNİZLEME") }
                Button(onClick = { previewUrl = null }) { Text("DURDUR") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { runScan() }) { Text("ADRES TARA") }
                Button(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("kamera günlüğü", logs.joinToString("\n")))
                }) { Text("GÜNLÜĞÜ KOPYALA") }
                Button(onClick = { logs.clear() }) { Text("TEMİZLE") }
            }
            if (progress.isNotEmpty()) Text(progress, color = Color(0xFFFFD54F), fontSize = 12.sp)

            if (previewUrl != null) {
                val url = if (previewUrl == "front") frontUrl.trim() else rearUrl.trim()
                CameraPane(url, attempt, Modifier.fillMaxWidth().height(220.dp)) { msg -> addLog("(${if (previewUrl == "rear") "ARKA" else "ÖN"}) $msg") }
            }

            Text("GÜNLÜK (basılı tutup seçebilirsiniz)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(
                    if (logs.isEmpty()) "(günlük boş)" else logs.joinToString("\n"),
                    color = Color(0xFFCFD8DC), fontSize = 11.sp
                )
            }
        }

        // Tam ekran test katmani: hicbir sinyalleme/komut yok, sadece ayni RTSP adresini (rearUrl, bosta
        // ise frontUrl'e doner) tam ekran izler. Icerigi 70mai'de elle secilen sensore gore degisir.
        if (fullScreenRearTest) {
            val testUrl = rearUrl.trim().ifBlank { frontUrl.trim() }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CameraPane(testUrl, attempt, Modifier.fillMaxSize()) { msg -> addLog("(TAM EKRAN TEST) $msg") }
                Text(
                    "ARKA (test)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "✕ KAPAT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .clickable { fullScreenRearTest = false }.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    "70MAİ'Yİ AÇ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .clickable { launchOfficialCameraApp(ctx) }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun CameraPane(url: String, attempt: Int, modifier: Modifier = Modifier, onLog: (String) -> Unit) {
    val ctx = LocalContext.current
    var status by remember(url, attempt) { mutableStateOf("Bağlanıyor…") }
    val layout = remember { VLCVideoLayout(ctx) }
    LaunchedEffect(status) { if (status.isNotEmpty()) onLog(status) }

    DisposableEffect(url, attempt) {
        if (url.isBlank()) { status = "Adres boş"; return@DisposableEffect onDispose { } }
        status = "Bağlanıyor…"
        var lib: LibVLC? = null
        var mp: MediaPlayer? = null
        val handler = Handler(Looper.getMainLooper())
        val watchdog = Runnable { if (status.startsWith("Bağlanıyor")) status = "12 sn içinde görüntü gelmedi." }
        try {
            lib = LibVLC(ctx, arrayListOf("--network-caching=400", "--rtsp-tcp"))
            mp = MediaPlayer(lib)
            mp.attachViews(layout, null, false, false)
            mp.setEventListener { ev ->
                when (ev.type) {
                    MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> status = ""
                    MediaPlayer.Event.EncounteredError -> status = "Bağlanamadı (VLC motoru hatası)."
                    MediaPlayer.Event.EndReached -> status = "Yayın sona erdi (kamera bağlantıyı kapattı)."
                }
            }
            val media = Media(lib, android.net.Uri.parse(url))
            media.addOption(":network-caching=400")
            media.addOption(":rtsp-tcp")
            mp.media = media
            media.release()
            mp.play()
            handler.postDelayed(watchdog, 12_000L)
        } catch (t: Throwable) {
            status = "Oynatıcı başlatılamadı: ${t.javaClass.simpleName} ${(t.message ?: "").take(80)}"
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
            try {
                mp?.stop(); mp?.detachViews(); mp?.release(); lib?.release()
            } catch (_: Throwable) { }
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize())
        if (status.isNotEmpty()) {
            Text(
                status, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(12.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }
    }
}
