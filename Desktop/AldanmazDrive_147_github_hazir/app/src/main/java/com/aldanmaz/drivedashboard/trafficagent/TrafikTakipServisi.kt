package com.aldanmaz.drivedashboard.trafficagent
import com.aldanmaz.drivedashboard.MainActivity
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlin.math.roundToInt

class TrafikTakipServisi : Service() {

    companion object {
        const val EYLEM_BASLAT =
            "com.aldanmaz.drivedashboard.TRAFIK_AJANI_BASLAT"

        const val EYLEM_DURDUR =
            "com.aldanmaz.drivedashboard.TRAFIK_AJANI_DURDUR"

        private const val BILDIRIM_KANALI =
            "trafik_takip_kanali"

        private const val BILDIRIM_NO = 1001
        private const val MESAFEYE_GORE_KONTROL_METRE =
            2_000.0
    }

    private lateinit var konumSaglayici:
            FusedLocationProviderClient

    private var konumGeriCagirimi:
            LocationCallback? = null

    private var takipBaslatildi = false

    private var sonKonum: Location? = null
    private var sonHizKmSaat = 0.0
    private var sonYon = "belirlenemedi"

    private var sonTrafikKontrolZamani = 0L
    private var trafikSorgusuSuruyor = false
    private var sorguNumarasi = 0L
    private var sonMesafeOlcumKonumu:
            Location? = null

    private var sonSorgudanBeriMesafeMetre =
        0.0
    private var sonHedefKimligi: String? = null
    private var sonTrafikOzeti: String? = null

    private var baslangicAnonsuYapildi = false
    private var sonSesliUyariImzasi: String? = null
    private var yavaslamaBaslangici = 0L
    private var sonKontroldeTrafikVardi = false

    override fun onCreate() {
        super.onCreate()

        konumSaglayici =
            LocationServices
                .getFusedLocationProviderClient(this)

        bildirimKanaliOlustur()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            EYLEM_DURDUR -> {
                takibiDurdur()
                return START_NOT_STICKY
            }

            EYLEM_BASLAT -> {
                onPlanServisiBaslat()

                TrafikDurumDeposu
                    .servisAktifliginiKaydet(
                        context = this,
                        aktif = true
                    )

                if (!baslangicAnonsuYapildi) {
                    // Bu sabit cümle için gerçek insan kaydı olmadığı için Android TTS ile
                    // sentetik başlangıç anonsu yapmıyoruz. Trafik uyarıları aşağıda kayıtlı
                    // insan sesi + yalnızca değişken bölüm TTS şeklinde verilir.
                    baslangicAnonsuYapildi = true
                }

                konumTakibiniBaslat()
            }

            else -> {
                takibiDurdur()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    private fun onPlanServisiBaslat() {

        val ilkBildirim =
            bildirimOlustur(
                "GPS konumu bekleniyor..."
            )

        val servisTuru =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }

        ServiceCompat.startForeground(
            this,
            BILDIRIM_NO,
            ilkBildirim,
            servisTuru
        )
    }

    @SuppressLint("MissingPermission")
    private fun konumTakibiniBaslat() {

        if (takipBaslatildi) {
            return
        }

        val hassasKonumIzni =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val yaklasikKonumIzni =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hassasKonumIzni && !yaklasikKonumIzni) {
            takibiDurdur()
            return
        }

        val konumIstegi =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5_000L
            )
                .setMinUpdateIntervalMillis(2_000L)
                .setWaitForAccurateLocation(false)
                .build()

        konumGeriCagirimi =
            object : LocationCallback() {

                override fun onLocationResult(
                    sonuc: LocationResult
                ) {
                    val konum =
                        sonuc.lastLocation ?: return

                    yeniKonumuIsle(konum)
                }
            }

        konumSaglayici.requestLocationUpdates(
            konumIstegi,
            konumGeriCagirimi!!,
            mainLooper
        )

        takipBaslatildi = true
    }

    private fun yeniKonumuIsle(
        konum: Location
    ) {

        val oncekiOlcumKonumu =
            sonMesafeOlcumKonumu

        if (oncekiOlcumKonumu != null) {

            val adimMesafesi =
                oncekiOlcumKonumu
                    .distanceTo(konum)
                    .toDouble()

            val asgariGecerliMesafe =
                maxOf(
                    10.0,
                    oncekiOlcumKonumu
                        .accuracy
                        .toDouble(),
                    konum.accuracy
                        .toDouble()
                )

            val gecerliGpsAdimi =
                adimMesafesi >=
                        asgariGecerliMesafe &&
                        adimMesafesi <=
                        2_000.0

            if (gecerliGpsAdimi) {
                sonSorgudanBeriMesafeMetre +=
                    adimMesafesi
            }
        }

        sonMesafeOlcumKonumu =
            Location(konum)

        sonKonum = konum

        sonHizKmSaat =
            if (konum.hasSpeed()) {
                konum.speed * 3.6
            } else {
                0.0
            }

        sonYon =
            if (konum.hasBearing()) {
                "${konum.bearing.roundToInt()}°"
            } else {
                "belirlenemedi"
            }

        otomatikTrafikKontrolu(
            konum = konum,
            hizKmSaat = sonHizKmSaat.toFloat()
        )

        bildirimiGuncelle()
    }

    private fun otomatikTrafikKontrolu(
        konum: Location,
        hizKmSaat: Float
    ) {
        val hedef =
            HedefDeposu.kayitliHedefiAl(this)

        if (hedef == null) {
            if (sonHedefKimligi != null) {
                sorguyuGecersizKil()
                sonHedefKimligi = null
                sonTrafikOzeti = null
                sonTrafikKontrolZamani = 0L
                sonSorgudanBeriMesafeMetre =
                    0.0

                sonMesafeOlcumKonumu =
                    Location(konum)
                sonSorgudanBeriMesafeMetre =
                    0.0

                sonMesafeOlcumKonumu =
                    null
                TrafikDurumDeposu
                    .trafikDurumunuTemizle(this)
            }
            return
        }

        val hedefKimligi =
            "${hedef.enlem};${hedef.boylam}"

        if (hedefKimligi != sonHedefKimligi) {
            sorguyuGecersizKil()
            sonHedefKimligi = hedefKimligi
            sonTrafikOzeti = null
            sonTrafikKontrolZamani = 0L

            TrafikDurumDeposu
                .trafikDurumunuTemizle(this)
        }

        if (trafikSorgusuSuruyor) {
            return
        }

        val simdi =
            SystemClock.elapsedRealtime()

        val kontrolAraligi = if (sonKontroldeTrafikVardi) 60_000L
        else TrafikKontrolAraligi.hesapla(hizKmSaat)

        if (hizKmSaat in 1f..15f) {
            if (yavaslamaBaslangici == 0L) yavaslamaBaslangici = simdi
        } else yavaslamaBaslangici = 0L
        val aniYavaslama = yavaslamaBaslangici > 0L &&
                simdi - yavaslamaBaslangici >= 30_000L &&
                simdi - sonTrafikKontrolZamani >= 30_000L

        val kontrolZamaniGeldi =
            sonTrafikKontrolZamani == 0L ||
                    simdi - sonTrafikKontrolZamani >=
                    kontrolAraligi

        val besKmIlerlendi =
            sonSorgudanBeriMesafeMetre >=
                    MESAFEYE_GORE_KONTROL_METRE

        val yenidenKontrolGerekli = kontrolZamaniGeldi || besKmIlerlendi || aniYavaslama

        if (!yenidenKontrolGerekli) {
            return
        }

        trafikSorgusuSuruyor = true
        sonTrafikKontrolZamani = simdi
        sonTrafikOzeti =
            "${hedef.ad} için trafik kontrol ediliyor..."

        val buSorguNumarasi =
            ++sorguNumarasi

        val aracYonu =
            if (konum.hasBearing()) {
                konum.bearing
                    .roundToInt()
                    .coerceIn(0, 359)
            } else {
                null
            }

        TomTomRouteClient.rotaTrafikBilgisiniAl(
            baslangicEnlem = konum.latitude,
            baslangicBoylam = konum.longitude,
            hedefEnlem = hedef.enlem,
            hedefBoylam = hedef.boylam,
            aracYonu = aracYonu,

            basarili = basarili@{ sonuc ->

                if (
                    buSorguNumarasi != sorguNumarasi ||
                    !takipBaslatildi
                ) {
                    return@basarili
                }

                trafikSorgusuSuruyor = false
                yavaslamaBaslangici = 0L
                sonKontroldeTrafikVardi = sonuc.toplamGecikmeSaniye >= 60 || sonuc.olaylar.isNotEmpty()
                sonSorgudanBeriMesafeMetre =
                    0.0
                sonMesafeOlcumKonumu =
                    Location(konum)
                sonTrafikOzeti =
                    rotaBildirimOzetiOlustur(
                        hedefAdi = hedef.ad,
                        sonuc = sonuc
                    )

                TrafikDurumDeposu
                    .trafikSonucunuKaydet(
                        context = this,
                        hedefAdi = hedef.ad,
                        sonuc = sonuc,
                        kontrolAraligiMillis =
                            kontrolAraligi
                    )
                AnlikTrafikUyarisiYoneticisi
                    .rotaSonucunuKontrolEt(
                        context = this,
                        hedefAdi = hedef.ad,
                        sonuc = sonuc
                    )

                sesliTrafikUyarisiYap(
                    hedefAdi = hedef.ad,
                    sonuc = sonuc
                )

                bildirimiGuncelle()
            },

            hata = hata@{ hataMesaji ->

                if (
                    buSorguNumarasi != sorguNumarasi ||
                    !takipBaslatildi
                ) {
                    return@hata
                }

                trafikSorgusuSuruyor = false

                sonTrafikOzeti =
                    "${hedef.ad}: $hataMesaji"

                bildirimiGuncelle()
            }
        )
    }

    private fun sesliTrafikUyarisiYap(
        hedefAdi: String,
        sonuc: RotaTrafikSonucu
    ) {
        val legacyTtsMp3Enabled = AjanAyarlari.sesliUyariAcikMi(this)

        // 91: Varsayılan ses kanalı Gemini'dir. Eski TTS/MP3 yalnız kullanıcı
        // Trafik Ajanı sayfasındaki anahtarı açarsa devreye girer.
        // 56: Sürüşte uzun yol/puan dökümü yerine tek, kısa ve değişiklik bazlı uyarı.
        val enYakinOlay = sonuc.olaylar.minByOrNull { it.baslangicMetre }
        val dakika = ((sonuc.toplamGecikmeSaniye + 59) / 60).coerceAtLeast(0)
        val mesafe = enYakinOlay?.baslangicMetre
        val yolKapali = enYakinOlay?.kategori.equals("ROAD_CLOSURE", ignoreCase = true)
        val trafikHizi = enYakinOlay?.etkiliHizKmSaat
        val imza = "${enYakinOlay?.kategori}:${mesafe?.div(500)?.roundToInt()}:$dakika"
        if (imza == sonSesliUyariImzasi) return
        sonSesliUyariImzasi = imza

        val clip = when {
            yolKapali -> RecordedVoiceClip.ROAD_CLOSED_ALT_ROUTE
            trafikHizi != null && trafikHizi <= 5 -> RecordedVoiceClip.TRAFFIC_JAM_INTRO
            sonuc.toplamGecikmeSaniye >= 300 -> RecordedVoiceClip.TRAFFIC_HEAVY_INTRO
            enYakinOlay != null -> RecordedVoiceClip.TRAFFIC_MODERATE_INTRO
            else -> RecordedVoiceClip.TRAFFIC_CLEAR
        }
        val kisaDegisken = buildString {
            if (!yolKapali && mesafe != null) append("${sesliUzaklikMetniOlustur(mesafe)} ileride.")
            if (!yolKapali && dakika > 0) append(" $dakika dakika gecikme.")
        }.trim().ifBlank { null }

        if (!legacyTtsMp3Enabled) {
            val durum = when {
                yolKapali -> "Yol kapanması algılandı."
                trafikHizi != null && trafikHizi <= 5 -> "Trafik neredeyse durmuş durumda."
                sonuc.toplamGecikmeSaniye >= 300 -> "Yoğun trafik var."
                enYakinOlay != null -> "Önünüzde trafik olayı var."
                else -> "Trafik akışı normal."
            }
            enYakinOlay?.let { TrafikGeminiDurumu.kaydet(this, hedefAdi, it) }
            val konum = enYakinOlay?.yolAdi?.takeIf { it.isNotBlank() }?.let { "$it, $hedefAdi istikameti." }.orEmpty()
            GeminiLiveManager.getInstance(this).announceTraffic(
                "Trafik Ajanı uyarısı. Sürücüye yalnız kısa ve sakin biçimde söyle: " +
                    durum + " " + (kisaDegisken ?: "") + " " + konum + " Gereksiz açıklama yapma."
            )
            return
        }

        CentralVoiceAlertManager.getInstance(this).playRecorded(
            type = VoiceAlertType.TRAFFIC,
            priority = if (yolKapali) VoiceAlertPriority.CRITICAL else VoiceAlertPriority.HIGH,
            clip = clip,
            cooldownKey = "traffic_short_$imza",
            cooldownMs = 60_000L,
            dynamicTextAfter = kisaDegisken
        )
        // Eski ayrıntılı metin üreticisi kaynakta geri dönüş amacıyla tutulur,
        // fakat 56 kısa anons modunda çalıştırılmaz.
        if (
            getSharedPreferences("traffic_agent", MODE_PRIVATE)
                .getBoolean("legacy_long_voice", false)
        ) {
        val toplamMesafe =
            sonuc.toplamMesafeMetre.toDouble()

        val trafikPuanBilgisi =
            TrafikPuanlayici
                .onumuzdeki20KmPuani(sonuc)

        val bolgeler =
            listOf(
                Triple(
                    "İlk 5 kilometrede",
                    0.0,
                    5_000.0
                ),
                Triple(
                    "5 ile 10 kilometre arasında",
                    5_000.0,
                    10_000.0
                ),
                Triple(
                    "10 ile 20 kilometre arasında",
                    10_000.0,
                    20_000.0
                )
            ).filter { bolge ->
                bolge.second < toplamMesafe
            }

        val bolgeOlaylari =
            bolgeler.map { bolge ->

                val olay =
                    sonuc.bolgedekiOlaylar(
                        bolge.second,
                        minOf(
                            bolge.third,
                            toplamMesafe
                        )
                    ).maxWithOrNull(
                        compareBy<RotaTrafikOlayi> {
                            it.gecikmeBuyuklugu
                        }.thenBy {
                            it.gecikmeSaniye
                        }
                    )

                bolge to olay
            }
        val uzakCiddiOlaylar =
            if (toplamMesafe > 20_000.0) {

                sonuc.bolgedekiOlaylar(
                    baslangicMetre =
                        20_000.0,

                    bitisMetre =
                        minOf(
                            50_000.0,
                            toplamMesafe
                        )
                )
                    .filter { olay ->

                        val yolKapali =
                            olay.kategori.equals(
                                "ROAD_CLOSURE",
                                ignoreCase = true
                            )

                        val ciddiYolCalismasi =
                            olay.kategori.equals(
                                "ROAD_WORK",
                                ignoreCase = true
                            ) &&
                                    olay.gecikmeSaniye >=
                                    300

                        val ciddiGecikme =
                            olay.gecikmeBuyuklugu >=
                                    3 ||
                                    olay.gecikmeSaniye >=
                                    600

                        val cokDusukHiz =
                            olay.etkiliHizKmSaat
                                ?.let { hiz ->
                                    hiz <= 10
                                } == true

                        yolKapali ||
                                ciddiYolCalismasi ||
                                ciddiGecikme ||
                                cokDusukHiz
                    }
                    .sortedWith(
                        compareByDescending<RotaTrafikOlayi> {
                            it.kategori.equals(
                                "ROAD_CLOSURE",
                                ignoreCase = true
                            )
                        }.thenByDescending {
                            it.gecikmeBuyuklugu
                        }.thenByDescending {
                            it.gecikmeSaniye
                        }
                    )
                    .take(2)

            } else {
                emptyList()
            }
        val uzakCiddiOlayImzasi =
            uzakCiddiOlaylar.joinToString(
                separator = "|"
            ) { olay ->

                buildString {
                    append(
                        olay.kategori
                    )
                    append(":")
                    append(
                        olay.gecikmeBuyuklugu
                    )
                    append(":")
                    append(
                        olay.gecikmeSaniye / 300
                    )
                    append(":")
                    append(
                        (
                                olay.baslangicMetre /
                                        1000.0
                                ).roundToInt()
                    )
                }
            }
        val uyariImzasi =
            buildString {
                append(trafikPuanBilgisi.puan)
                append(":")
                append(
                    sonuc.toplamGecikmeSaniye / 300
                )

                bolgeOlaylari.forEach {
                        (bolge, olay) ->

                    append("|")
                    append(bolge.first)
                    append(":")

                    if (olay == null) {
                        append("YOK")
                    } else {
                        append(olay.kategori)
                        append(":")
                        append(olay.gecikmeBuyuklugu)
                        append(":")
                        append(olay.gecikmeSaniye / 300)
                        append(":")
                        append(
                            (
                                    olay.baslangicMetre /
                                            1000.0
                                    ).roundToInt()
                        )
                        append(":")
                        append(
                            olay.etkiliHizKmSaat
                                ?.div(5)
                        )
                    }
                }
            }

        val tamUyariImzasi =
            "$uyariImzasi|UZAK:$uzakCiddiOlayImzasi"

        if (
            tamUyariImzasi ==
            sonSesliUyariImzasi
        ) {
            return
        }

        sonSesliUyariImzasi =
            tamUyariImzasi

        val sesMetni =
            buildString {
                append("$hedefAdi yönünde. ")

                bolgeOlaylari.forEach {
                        (bolge, olay) ->

                    if (olay == null) {
                        append(
                            "${bolge.first} trafik normal. "
                        )

                        return@forEach
                    }

                    val uzaklikMetni =
                        sesliUzaklikMetniOlustur(
                            olay.baslangicMetre
                        )

                    append("${bolge.first}, ")
                    append(
                        "yaklaşık $uzaklikMetni ileride "
                    )

                    olay.yolAdi
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let { yolAdi ->
                            append(
                                "$yolAdi üzerinde "
                            )
                        }

                    val trafikHizi =
                        olay.etkiliHizKmSaat

                    val trafikDurumu =
                        when {
                            olay.kategori.equals(
                                "ROAD_CLOSURE",
                                ignoreCase = true
                            ) ->
                                "yol kapalı."

                            olay.kategori.equals(
                                "ROAD_WORK",
                                ignoreCase = true
                            ) ->
                                "yol çalışması var."

                            trafikHizi != null &&
                                    trafikHizi <= 5 ->
                                "trafik tıkalı."

                            trafikHizi != null &&
                                    trafikHizi <= 15 ->
                                "trafik hızı çok yavaş."

                            trafikHizi != null &&
                                    trafikHizi <= 30 ->
                                "trafik yavaş."

                            olay.kategori.equals(
                                "JAM",
                                ignoreCase = true
                            ) ->
                                "yoğun trafik var."

                            else ->
                                "trafik sorunu var."
                        }

                    append(trafikDurumu)

                    if (trafikHizi != null) {
                        append(
                            " Trafik hızı saatte " +
                                    "$trafikHizi kilometre."
                        )
                    }

                    if (olay.gecikmeSaniye >= 60) {
                        val dakika =
                            (
                                    olay.gecikmeSaniye +
                                            59
                                    ) / 60

                        append(
                            " Tahmini gecikme " +
                                    "$dakika dakika."
                        )
                    }

                    append(" ")
                }

                if (uzakCiddiOlaylar.isNotEmpty()) {
                    append(
                        "İleride dikkat. "
                    )

                    uzakCiddiOlaylar.forEach { olay ->

                        val uzaklikMetni =
                            sesliUzaklikMetniOlustur(
                                olay.baslangicMetre
                            )

                        append(
                            "Yaklaşık $uzaklikMetni ileride "
                        )

                        olay.yolAdi
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { yolAdi ->
                                append(
                                    "$yolAdi üzerinde "
                                )
                            }

                        val uzakTrafikDurumu =
                            when {
                                olay.kategori.equals(
                                    "ROAD_CLOSURE",
                                    ignoreCase = true
                                ) ->
                                    "yol kapanması var."

                                olay.kategori.equals(
                                    "ROAD_WORK",
                                    ignoreCase = true
                                ) ->
                                    "ciddi yol çalışması var."

                                olay.etkiliHizKmSaat != null &&
                                        olay.etkiliHizKmSaat <= 5 ->
                                    "trafik tıkalı."

                                olay.etkiliHizKmSaat != null &&
                                        olay.etkiliHizKmSaat <= 10 ->
                                    "trafik çok yavaş."

                                else ->
                                    "ciddi trafik yoğunluğu var."
                            }

                        append(uzakTrafikDurumu)

                        if (olay.gecikmeSaniye >= 60) {
                            val dakika =
                                (
                                        olay.gecikmeSaniye +
                                                59
                                        ) / 60

                            append(
                                " Tahmini gecikme " +
                                        "$dakika dakika."
                            )
                        }

                        append(" ")
                    }
                }

                if (
                    sonuc.toplamGecikmeSaniye >= 60
                ) {
                    val toplamDakika =
                        (
                                sonuc.toplamGecikmeSaniye +
                                        59
                                ) / 60

                    append(
                        "Rotanın toplam gecikmesi " +
                                "yaklaşık " +
                                "$toplamDakika dakika."
                    )
                }
            }

        val kayitliUyari = when {
            sonuc.olaylar.any {
                it.kategori.equals("ROAD_CLOSURE", ignoreCase = true)
            } -> RecordedVoiceClip.ROAD_CLOSED_WARNING

            sonuc.olaylar.any {
                it.kategori.equals("ROAD_WORK", ignoreCase = true)
            } -> RecordedVoiceClip.ROAD_WORK_WARNING

            sonuc.toplamGecikmeSaniye >= 120 ->
                RecordedVoiceClip.TRAFFIC_DELAY_WARNING

            else -> RecordedVoiceClip.TRAFFIC_WARNING
        }

        CentralVoiceAlertManager
            .getInstance(this)
            .playRecorded(
                type = VoiceAlertType.TRAFFIC,
                priority = VoiceAlertPriority.HIGH,
                clip = kayitliUyari,
                cooldownKey = "traffic_agent_$tamUyariImzasi",
                cooldownMs = 60_000L,
                dynamicTextAfter =
                    "Trafik seviyesi ${trafikPuanBilgisi.puan}. " +
                            "${trafikPuanBilgisi.durum}. " +
                            sesMetni
            )
        }
    }

    private fun sesliUzaklikMetniOlustur(
        metre: Double
    ): String {
        return if (metre < 1_000.0) {
            "${metre.roundToInt()} metre"
        } else {
            String.format(
                Locale.forLanguageTag("tr-TR"),
                "%.1f kilometre",
                metre / 1_000.0
            )
        }
    }

    private fun rotaBildirimOzetiOlustur(
        hedefAdi: String,
        sonuc: RotaTrafikSonucu
    ): String {
        val trafikPuanBilgisi =
            TrafikPuanlayici
                .onumuzdeki20KmPuani(sonuc)

        val gecikmeMetni =
            if (sonuc.toplamGecikmeSaniye > 0) {
                val dakika =
                    (
                            sonuc.toplamGecikmeSaniye +
                                    59
                            ) / 60

                "toplam gecikme yaklaşık $dakika dk"
            } else {
                "rota genelinde gecikme yok"
            }

        val ilkBolge =
            bolgeOzetiOlustur(
                sonuc = sonuc,
                baslangicMetre = 0.0,
                bitisMetre = 5_000.0,
                etiket = "0–5 km"
            )

        val ikinciBolge =
            bolgeOzetiOlustur(
                sonuc = sonuc,
                baslangicMetre = 5_000.0,
                bitisMetre = 10_000.0,
                etiket = "5–10 km"
            )

        val ucuncuBolge =
            bolgeOzetiOlustur(
                sonuc = sonuc,
                baslangicMetre = 10_000.0,
                bitisMetre = 20_000.0,
                etiket = "10–20 km"
            )

        return "$hedefAdi • " +
                "Trafik ${trafikPuanBilgisi.puan}/10 • " +
                "${trafikPuanBilgisi.durum} • " +
                "$gecikmeMetni\n" +
                "$ilkBolge • " +
                "$ikinciBolge • " +
                ucuncuBolge
    }

    private fun bolgeOzetiOlustur(
        sonuc: RotaTrafikSonucu,
        baslangicMetre: Double,
        bitisMetre: Double,
        etiket: String
    ): String {

        if (
            baslangicMetre >=
            sonuc.toplamMesafeMetre.toDouble()
        ) {
            return "$etiket: hedef daha yakın"
        }

        val gercekBitis =
            minOf(
                bitisMetre,
                sonuc.toplamMesafeMetre.toDouble()
            )

        val olaylar =
            sonuc.bolgedekiOlaylar(
                baslangicMetre,
                gercekBitis
            )

        if (olaylar.isEmpty()) {
            return "$etiket: sorun yok"
        }

        val onemliOlay =
            olaylar.maxWithOrNull(
                compareBy<RotaTrafikOlayi> {
                    it.gecikmeBuyuklugu
                }.thenBy {
                    it.gecikmeSaniye
                }
            ) ?: return "$etiket: sorun yok"

        val toplamGecikme =
            olaylar.sumOf {
                it.gecikmeSaniye
            }

        val gecikme =
            if (toplamGecikme > 0) {
                val dakika =
                    (toplamGecikme + 59) / 60

                " yaklaşık $dakika dk gecikme"
            } else {
                ""
            }

        val yol =
            onemliOlay.yolAdi
                ?.takeIf { it.isNotBlank() }
                ?.let { " ($it)" }
                ?: ""

        return "$etiket: " +
                onemliOlay.kategoriMetni() +
                yol +
                gecikme
    }

    @SuppressLint("MissingPermission")
    private fun bildirimiGuncelle() {

        if (!takipBaslatildi) {
            return
        }

        val konum =
            sonKonum ?: return

        val gpsMetni =
            String.format(
                Locale.forLanguageTag("tr-TR"),
                "Hız: %.1f km/sa • " +
                        "Yön: %s • " +
                        "Doğruluk: ±%.0f m",
                sonHizKmSaat,
                sonYon,
                konum.accuracy
            )

        val hedef =
            HedefDeposu.kayitliHedefiAl(this)

        val trafikMetni =
            when {
                hedef == null ->
                    "Otomatik trafik kontrolü için hedef seçin."

                sonTrafikOzeti != null ->
                    sonTrafikOzeti!!

                else ->
                    "${hedef.ad} için ilk trafik kontrolü bekleniyor..."
            }

        val mesaj =
            "$trafikMetni\n$gpsMetni"

        val bildirim =
            bildirimOlustur(mesaj)

        try {
            NotificationManagerCompat
                .from(this)
                .notify(
                    BILDIRIM_NO,
                    bildirim
                )
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş olabilir.
        }
    }

    private fun bildirimOlustur(
        mesaj: String
    ): Notification {

        val uygulamayiAcIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val bekleyenIntent =
            PendingIntent.getActivity(
                this,
                0,
                uygulamayiAcIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val durdurmaIntent =
            Intent(
                this,
                TrafikTakipServisi::class.java
            ).apply {
                action = EYLEM_DURDUR
            }

        val durdurmaBekleyenIntent =
            PendingIntent.getService(
                this,
                1,
                durdurmaIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            BILDIRIM_KANALI
        )
            .setSmallIcon(
                android.R.drawable
                    .ic_menu_mylocation
            )
            .setContentTitle(
                "Trafik Ajanı çalışıyor"
            )
            .setContentText(
                mesaj.replace("\n", " • ")
            )
            .setStyle(
                NotificationCompat
                    .BigTextStyle()
                    .bigText(mesaj)
            )
            .setSubText(
                TrafikKontrolAraligi.aciklama(
                    sonHizKmSaat.toFloat()
                )
            )
            .setContentIntent(bekleyenIntent)
            .addAction(
                android.R.drawable
                    .ic_menu_close_clear_cancel,
                "Ajanı Durdur",
                durdurmaBekleyenIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun bildirimKanaliOlustur() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val kanal =
                NotificationChannel(
                    BILDIRIM_KANALI,
                    "Trafik takibi",
                    NotificationManager
                        .IMPORTANCE_LOW
                ).apply {
                    description =
                        "Trafik ajanının GPS ve rota trafik bildirimi"

                    setShowBadge(false)
                }

            val bildirimYoneticisi =
                getSystemService(
                    NotificationManager::class.java
                )

            bildirimYoneticisi
                .createNotificationChannel(kanal)
        }
    }

    private fun sorguyuGecersizKil() {
        sorguNumarasi++
        trafikSorgusuSuruyor = false
        sonSesliUyariImzasi = null
    }

    private fun takibiDurdur() {

        TrafikDurumDeposu
            .servisAktifliginiKaydet(
                context = this,
                aktif = false
            )

        sorguyuGecersizKil()

        konumGeriCagirimi?.let { geriCagirim ->
            konumSaglayici
                .removeLocationUpdates(
                    geriCagirim
                )
        }

        konumGeriCagirimi = null
        takipBaslatildi = false

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        TrafikDurumDeposu
            .servisAktifliginiKaydet(
                context = this,
                aktif = false
            )

        sorguyuGecersizKil()

        konumGeriCagirimi?.let { geriCagirim ->
            konumSaglayici
                .removeLocationUpdates(
                    geriCagirim
                )
        }

        konumGeriCagirimi = null
        takipBaslatildi = false

        super.onDestroy()
    }
}
