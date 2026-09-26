package com.aldanmaz.drivedashboard.trafficagent
import android.app.Activity
import android.speech.RecognizerIntent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlin.math.roundToInt



@SuppressLint("MissingPermission")
fun guncelKonumuAl(
    context: Context,
    basarili: (Location) -> Unit,
    hata: (String) -> Unit
) {
    val konumSaglayici =
        LocationServices
            .getFusedLocationProviderClient(
                context
            )

    val iptalKaynagi =
        CancellationTokenSource()

    konumSaglayici.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        iptalKaynagi.token
    )
        .addOnSuccessListener { konum ->

            if (konum != null) {
                basarili(konum)
            } else {
                hata(
                    "Konum alınamadı. Telefonun konum özelliğini açıp tekrar deneyin."
                )
            }
        }
        .addOnFailureListener { exception ->
            hata(
                "Konum hatası: " +
                        (
                                exception.localizedMessage
                                    ?: "Bilinmeyen hata"
                                )
            )
        }
}

fun konumMetniOlustur(
    konum: Location
): String {

    val hizKmSaat =
        if (konum.hasSpeed()) {
            konum.speed * 3.6
        } else {
            0.0
        }

    return String.format(
        Locale.forLanguageTag("tr-TR"),
        "GPS BAĞLANTISI ONAYLANDI\n" +
                "Enlem: %.6f\n" +
                "Boylam: %.6f\n" +
                "Doğruluk: ±%.0f metre\n" +
                "Hız: %.1f km/sa",
        konum.latitude,
        konum.longitude,
        konum.accuracy,
        hizKmSaat
    )
}

fun gecikmeMetniOlustur(
    saniye: Int
): String {

    if (saniye < 60) {
        return "$saniye saniye"
    }

    val dakika = saniye / 60
    val kalanSaniye = saniye % 60

    return if (kalanSaniye == 0) {
        "$dakika dakika"
    } else {
        "$dakika dakika $kalanSaniye saniye"
    }
}

fun trafikMetniOlustur(
    trafik: TrafikAkisBilgisi
): String {

    val guvenYuzdesi =
        (
                trafik.veriGuveni *
                        100.0
                ).roundToInt()

    return buildString {
        append("YAKIN YOL ANALİZİ\n")
        append(trafik.yogunlukDurumu())

        append("\nGüncel hız: ")
        append(trafik.guncelHiz)
        append(" km/sa")

        append("\nNormal hız: ")
        append(trafik.normalHiz)
        append(" km/sa")

        append("\nTahmini gecikme: ")
        append(
            gecikmeMetniOlustur(
                trafik.gecikmeSaniyesi
            )
        )

        append("\nVeri güveni: %")
        append(guvenYuzdesi)

        if (trafik.yolKapali) {
            append(
                "\nDİKKAT: Yol trafiğe kapalı."
            )
        }
    }
}

fun hedefUzaklikMetni(
    uzaklikMetre: Double?
): String {

    if (
        uzaklikMetre == null ||
        uzaklikMetre.isNaN()
    ) {
        return ""
    }

    return if (uzaklikMetre < 1000.0) {
        "${uzaklikMetre.roundToInt()} metre"
    } else {
        String.format(
            Locale.forLanguageTag("tr-TR"),
            "%.1f km",
            uzaklikMetre / 1000.0
        )
    }
}

@Composable
fun TrafikAjaniEkrani(
    sesliHedefIstekSayaci: Int = 0,
    disSesliKomut: String = "",
    disSesliKomutSayaci: Int = 0,
    onDisSesliKomutIslendi: () -> Unit = {},
    onBack: () -> Unit = {},
    onHome: () -> Unit = onBack,
    onSettings: () -> Unit = {}
) {

    val context =
        LocalContext.current
    val hedefSeslendirici =
        remember {
            TrafikSeslendirici(context)
        }

    DisposableEffect(Unit) {
        onDispose {
            hedefSeslendirici.kapat()
        }
    }

    var sesliDinlemeAsamasi by
    rememberSaveable {
        mutableStateOf("HEDEF")
    }

    var sesliAramaBekliyor by
    rememberSaveable {
        mutableStateOf(false)
    }

    var sonIslenenSesliIstek by
    rememberSaveable {
        mutableStateOf(0)
    }

    var sonIslenenDisSesliKomut by rememberSaveable {
        mutableStateOf(0)
    }

    var sesliTaramaIstekSayaci by rememberSaveable {
        mutableStateOf(0)
    }

    var sesliTaramaMesafesiKm by rememberSaveable {
        mutableStateOf(20)
    }

    var sesliTaramaSonucuBekleniyor by rememberSaveable {
        mutableStateOf(false)
    }

    var sesliOnayBekleyenHedef by
    remember {
        mutableStateOf<HedefAramaSonucu?>(
            null
        )
    }

    // 92: Gemini/harici komut ile hedef geldiğinde eski Google SpeechRecognizer
    // onay penceresi açılmaz. İlk TomTom sonucu doğrudan hedef olarak kaydedilir.
    var geminiHariciHedefAkisi by rememberSaveable {
        mutableStateOf(false)
    }

    val baslangicServisAktif =
        remember {
            TrafikDurumDeposu
                .servisAktifMi(context)
        }

    var takipAktif by rememberSaveable {
        mutableStateOf(
            baslangicServisAktif
        )
    }

    var durumMesaji by rememberSaveable {
        mutableStateOf(
            if (baslangicServisAktif) {
                "Ajan arka planda çalışıyor. Son trafik verileri yükleniyor..."
            } else {
                "Ajan beklemede. Görevi başlatmak için sistemi etkinleştirin."
            }
        )
    }

    var mevcutEnlem by rememberSaveable {
        mutableStateOf<Double?>(null)
    }

    var mevcutBoylam by rememberSaveable {
        mutableStateOf<Double?>(null)
    }

    var hedefAramaMetni by rememberSaveable {
        mutableStateOf("")
    }

    var hedefAramaDurumu by rememberSaveable {
        mutableStateOf("")
    }

    var hedefSonuclari by remember {
        mutableStateOf<List<HedefAramaSonucu>>(
            emptyList()
        )
    }

    var secilenHedef by remember {
        mutableStateOf(
            HedefDeposu.kayitliHedefiAl(
                context
            )
        )
    }

    var hedefAramaAcik by rememberSaveable {
        mutableStateOf(
            secilenHedef == null
        )
    }

    var sonRotaSonucu by remember {
        mutableStateOf<RotaTrafikSonucu?>(
            null
        )
    }
    var kayitliTrafikDurumu by remember {
        mutableStateOf(
            TrafikDurumDeposu
                .trafikDurumunuAl(context)
        )
    }

    var simdikiZaman by remember {
        mutableStateOf(
            System.currentTimeMillis()
        )
    }
    var sesliMikrofonAcilsin by
    remember {
        mutableStateOf(false)
    }
    var sesliTakipBaslatilsin by
    remember {
        mutableStateOf(false)
    }

    var sesliSoruMetni by
    remember {
        mutableStateOf<String?>(null)
    }

    fun hedefiSecVeKaydet(
        hedef: HedefAramaSonucu
    ) {
        secilenHedef = hedef
        sonRotaSonucu = null
        kayitliTrafikDurumu = null

        TrafikDurumDeposu
            .trafikDurumunuTemizle(
                context
            )

        HedefDeposu.hedefiKaydet(
            context = context,
            hedef = hedef
        )

        hedefAramaDurumu =
            "Hedef kaydedildi: ${hedef.ad}"

        hedefSonuclari =
            emptyList()

        hedefAramaAcik =
            false
    }
    val sesTanimaLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult()
        ) { sonuc ->

            val soylenenMetin =
                if (
                    sonuc.resultCode ==
                    Activity.RESULT_OK
                ) {
                    sonuc.data
                        ?.getStringArrayListExtra(
                            RecognizerIntent
                                .EXTRA_RESULTS
                        )
                        ?.firstOrNull()
                        ?.trim()
                } else {
                    null
                }

            if (soylenenMetin.isNullOrBlank()) {

                hedefAramaDurumu =
                    "Sesli yanıt alınamadı."

                sesliSoruMetni =
                    "Sizi anlayamadım. Lütfen tekrar söyleyin."

            } else if (
                sesliDinlemeAsamasi ==
                "HEDEF"
            ) {

                val hedefCevabi =
                    soylenenMetin.lowercase(
                        Locale(
                            "tr",
                            "TR"
                        )
                    )

                val hedefIptalEdildi =
                    hedefCevabi.contains(
                        "iptal"
                    ) ||
                            hedefCevabi.contains(
                                "hedef yok"
                            ) ||
                            hedefCevabi.contains(
                                "gitmeyeceğim"
                            ) ||
                            hedefCevabi.contains(
                                "yolculuk yok"
                            )

                if (hedefIptalEdildi) {

                    HedefDeposu.hedefiSil(
                        context
                    )

                    TrafikDurumDeposu
                        .trafikDurumunuTemizle(
                            context
                        )

                    secilenHedef = null
                    sonRotaSonucu = null
                    kayitliTrafikDurumu = null
                    hedefSonuclari = emptyList()
                    hedefAramaAcik = false
                    sesliAramaBekliyor = false
                    sesliOnayBekleyenHedef = null

                    hedefAramaDurumu =
                        "Yolculuk hedefi iptal edildi."

                    hedefSeslendirici.kayitliKonus(
                        TrafikKayitliSes.TARGET_CANCELLED
                    )

                } else {

                    hedefAramaMetni =
                        soylenenMetin

                    hedefAramaAcik =
                        true

                    hedefAramaDurumu =
                        "Sesli hedef aranıyor: $soylenenMetin"

                    sesliAramaBekliyor =
                        true
                }

            } else {

                val cevap =
                    soylenenMetin.lowercase(
                        Locale(
                            "tr",
                            "TR"
                        )
                    )

                val onaylandi =
                    cevap.contains("evet") ||
                            cevap.contains("doğru") ||
                            cevap.contains("onay") ||
                            cevap.contains("tamam")

                val reddedildi =
                    cevap.contains("hayır") ||
                            cevap.contains("yanlış") ||
                            cevap.contains("değil") ||
                            cevap.contains("iptal")

                val bekleyenHedef =
                    sesliOnayBekleyenHedef

                when {

                    onaylandi &&
                            bekleyenHedef != null -> {

                        hedefiSecVeKaydet(
                            bekleyenHedef
                        )
                        hedefSeslendirici.kayitliKonus(
                            TrafikKayitliSes.TARGET_SAVED,
                            tamamlaninca = {
                                sesliTakipBaslatilsin = true
                            }
                        )

                        sesliOnayBekleyenHedef =
                            null

                        sesliDinlemeAsamasi =
                            "HEDEF"
                    }

                    reddedildi -> {

                        sesliOnayBekleyenHedef =
                            null

                        sesliDinlemeAsamasi =
                            "HEDEF"

                        sesliSoruMetni =
                            "Hedefi tekrar söyleyin."
                    }

                    else -> {

                        sesliSoruMetni =
                            "Lütfen evet veya hayır deyin."
                    }
                }
            }
        }

    val mikrofonIzniLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission()
        ) { izinVerildi ->

            if (izinVerildi) {
                sesliMikrofonAcilsin =
                    true
            } else {
                hedefAramaDurumu =
                    "Mikrofon izni verilmedi."
            }
        }
    LaunchedEffect(
        sesliMikrofonAcilsin
    ) {
        if (!sesliMikrofonAcilsin) {
            return@LaunchedEffect
        }

        sesliMikrofonAcilsin = false

        val sesTanimaIntent =
            Intent(
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "tr-TR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    if (
                        sesliDinlemeAsamasi ==
                        "ONAY"
                    ) {
                        "Evet veya hayır deyin"
                    } else {
                        "Gideceğiniz yeri söyleyin"
                    }
                )
            }

        if (
            sesTanimaIntent.resolveActivity(
                context.packageManager
            ) != null
        ) {
            sesTanimaLauncher.launch(
                sesTanimaIntent
            )
        } else {
            hedefAramaDurumu =
                "Bu cihazda konuşma tanıma hizmeti bulunamadı."
        }
    }

    LaunchedEffect(
        sesliSoruMetni
    ) {
        val soru = sesliSoruMetni ?: return@LaunchedEffect
        sesliSoruMetni = null

        val mikrofonuAc: () -> Unit = {
            val mikrofonIzniVar =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            if (mikrofonIzniVar) {
                sesliMikrofonAcilsin = true
            } else {
                mikrofonIzniLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        when {
            soru == "Nereye gidiyorsunuz?" -> {
                hedefSeslendirici.kayitliKonus(
                    TrafikKayitliSes.TARGET_PROMPT,
                    tamamlaninca = mikrofonuAc
                )
            }

            soru == "Hedefi tekrar söyleyin." -> {
                hedefSeslendirici.kayitliKonus(
                    TrafikKayitliSes.ACTION_CANCELLED,
                    tamamlaninca = {
                        hedefSeslendirici.kayitliKonus(
                            TrafikKayitliSes.TARGET_PROMPT,
                            tamamlaninca = mikrofonuAc
                        )
                    }
                )
            }

            soru.startsWith("Hedef bulunamadı") ||
                    soru.startsWith("Hedef aranamadı") -> {
                hedefSeslendirici.kayitliKonus(
                    TrafikKayitliSes.TARGET_NOT_FOUND,
                    tamamlaninca = mikrofonuAc
                )
            }

            soru.contains("hedefini buldum") && sesliOnayBekleyenHedef != null -> {
                val hedefAdi = sesliOnayBekleyenHedef?.ad.orEmpty()
                hedefSeslendirici.kayitliKonus(
                    TrafikKayitliSes.TARGET_FOUND,
                    dinamikMetinSonra = hedefAdi,
                    tamamlaninca = {
                        hedefSeslendirici.kayitliKonus(
                            TrafikKayitliSes.TARGET_CONFIRM,
                            tamamlaninca = mikrofonuAc
                        )
                    }
                )
            }

            else -> {
                hedefSeslendirici.konus(
                    metin = soru,
                    tamamlaninca = mikrofonuAc
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        hedefSeslendirici.kayitliKonus(TrafikKayitliSes.AGENT_READY)
    }

    LaunchedEffect(
        sesliHedefIstekSayaci
    ) {
        if (
            sesliHedefIstekSayaci <= 0 ||
            sesliHedefIstekSayaci ==
            sonIslenenSesliIstek
        ) {
            return@LaunchedEffect
        }

        sonIslenenSesliIstek =
            sesliHedefIstekSayaci

        sesliDinlemeAsamasi =
            "HEDEF"

        sesliOnayBekleyenHedef =
            null

        hedefAramaAcik =
            true

        sesliSoruMetni =
            "Nereye gidiyorsunuz?"
    }
    LaunchedEffect(
        sesliAramaBekliyor,
        mevcutEnlem,
        mevcutBoylam
    ) {
        if (!sesliAramaBekliyor) {
            return@LaunchedEffect
        }

        val enlem =
            mevcutEnlem

        val boylam =
            mevcutBoylam

        if (
            enlem == null ||
            boylam == null
        ) {
            hedefAramaDurumu =
                "Sesli hedef için GPS konumu alınıyor..."

            guncelKonumuAl(
                context = context,

                basarili = { konum ->

                    mevcutEnlem =
                        konum.latitude

                    mevcutBoylam =
                        konum.longitude
                },

                hata = { hataMesaji ->

                    sesliAramaBekliyor =
                        false

                    hedefAramaDurumu =
                        hataMesaji

                    sesliDinlemeAsamasi =
                        "HEDEF"

                    sesliSoruMetni =
                        "Konum alınamadı. Hedefi daha sonra tekrar deneyin."
                }
            )

            return@LaunchedEffect
        }

        sesliAramaBekliyor =
            false

        hedefAramaDurumu =
            "Sesli hedef TomTom ağında aranıyor..."

        hedefSonuclari =
            emptyList()

        TomTomSearchClient.hedefAra(
            aramaMetni =
                hedefAramaMetni,

            mevcutEnlem =
                enlem,

            mevcutBoylam =
                boylam,

            basarili = { sonuclar ->

                hedefSonuclari =
                    sonuclar

                val ilkHedef =
                    sonuclar.firstOrNull()

                if (ilkHedef == null) {
                    geminiHariciHedefAkisi = false

                    hedefAramaDurumu =
                        "Sesli hedef bulunamadı."

                    sesliDinlemeAsamasi =
                        "HEDEF"

                    sesliSoruMetni =
                        "Hedef bulunamadı. Lütfen daha ayrıntılı söyleyin."

                } else if (geminiHariciHedefAkisi) {
                    // Gemini zaten hedefi açıkça belirledi. Eski Google konuşma
                    // penceresini açmadan hedefi kaydet ve akışı tamamla.
                    hedefiSecVeKaydet(ilkHedef)
                    geminiHariciHedefAkisi = false
                    sesliOnayBekleyenHedef = null
                    sesliDinlemeAsamasi = "HEDEF"
                    hedefAramaDurumu = "Gemini hedefi kaydedildi: ${ilkHedef.ad}"
                } else {

                    sesliOnayBekleyenHedef =
                        ilkHedef

                    sesliDinlemeAsamasi =
                        "ONAY"

                    hedefAramaDurumu =
                        "Sesli onay bekleniyor: ${ilkHedef.ad}"

                    sesliSoruMetni =
                        "${ilkHedef.ad} hedefini buldum. Onaylıyor musunuz?"
                }
            },

            hata = { hataMesaji ->
                geminiHariciHedefAkisi = false

                hedefAramaDurumu =
                    hataMesaji

                sesliDinlemeAsamasi =
                    "HEDEF"

                sesliSoruMetni =
                    "Hedef aranamadı. Lütfen tekrar söyleyin."
            }
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            val depoServisAktif =
                TrafikDurumDeposu
                    .servisAktifMi(context)

            if (
                depoServisAktif != takipAktif
            ) {
                takipAktif =
                    depoServisAktif

                durumMesaji =
                    if (depoServisAktif) {
                        "Ajan arka planda çalışıyor."
                    } else {
                        "Ajan görevi durduruldu."
                    }
            }

            kayitliTrafikDurumu =
                TrafikDurumDeposu
                    .trafikDurumunuAl(context)

            simdikiZaman =
                System.currentTimeMillis()

            delay(1_000L)
        }
    }
    var sesliUyariAcik by rememberSaveable {
        mutableStateOf(
            AjanAyarlari
                .sesliUyariAcikMi(context)
        )
    }
    val ilk20KmPuani =
        sonRotaSonucu?.let { sonuc ->
            TrafikPuanlayici
                .onumuzdeki20KmPuani(
                    sonuc
                )
        } ?: kayitliTrafikDurumu?.let {
                kayitliDurum ->

            TrafikPuanlayici
                .puanBilgisiOlustur(
                    kayitliDurum
                        .ilk20KmPuani
                )
        }

    fun trafikDurumOzetiniOlustur(kilometre: Int = 20): String {
        val sonuc = sonRotaSonucu
        if (sonuc != null) {
            val puan = when (kilometre) {
                5 -> TrafikPuanlayici.bolgePuani(sonuc, 0, 5)
                10 -> TrafikPuanlayici.bolgePuani(sonuc, 0, 10)
                else -> TrafikPuanlayici.onumuzdeki20KmPuani(sonuc)
            }
            val sinirMetre = kilometre * 1000.0
            val olaylar = sonuc.bolgedekiOlaylar(0.0, sinirMetre)
            val gecikme = olaylar.sumOf { it.gecikmeSaniye }.coerceAtLeast(0)
            return when {
                olaylar.isEmpty() -> "Trafik açık."
                gecikme >= 300 -> "Yoğun trafik. Yaklaşık ${gecikmeMetniOlustur(gecikme)} gecikme."
                gecikme > 0 -> "Trafik orta. Yaklaşık ${gecikmeMetniOlustur(gecikme)} gecikme."
                else -> "Trafik olayı var."
            }
        }

        val kayitli = kayitliTrafikDurumu
        if (kayitli != null) {
            val puan = when (kilometre) {
                5 -> kayitli.ilk5KmPuani
                10 -> ((kayitli.ilk5KmPuani + kayitli.besOnKmPuani) / 2.0).roundToInt()
                else -> kayitli.ilk20KmPuani
            }
            return when {
                puan >= 8 -> "Trafik sıkışık."
                puan >= 5 -> "Trafik orta."
                else -> "Trafik açık."
            }
        }

        return "Henüz okunabilecek güncel trafik verisi yok."
    }

    fun konumuVeTrafikBilgisiniOku() {

        durumMesaji =
            "GPS uydularına bağlanılıyor..."

        guncelKonumuAl(
            context = context,

            basarili = { konum ->

                mevcutEnlem =
                    konum.latitude

                mevcutBoylam =
                    konum.longitude

                val gpsMetni =
                    konumMetniOlustur(
                        konum
                    )

                durumMesaji =
                    "$gpsMetni\n\nTomTom ağı taranıyor..."

                TomTomTrafficClient
                    .trafikAkisiniAl(
                        enlem =
                            konum.latitude,

                        boylam =
                            konum.longitude,

                        basarili = {
                                trafikBilgisi ->

                            durumMesaji =
                                "$gpsMetni\n\n" +
                                        trafikMetniOlustur(
                                            trafikBilgisi
                                        )
                        },

                        hata = {
                                hataMesaji ->

                            durumMesaji =
                                "$gpsMetni\n\n$hataMesaji"
                        }
                    )
            },

            hata = { hataMesaji ->
                durumMesaji = hataMesaji
                takipAktif = false
            }
        )
    }

    fun takipServisiniBaslat() {

        try {
            val servisIntent =
                Intent(
                    context,
                    TrafikTakipServisi::class.java
                ).apply {
                    action =
                        TrafikTakipServisi
                            .EYLEM_BASLAT
                }

            ContextCompat
                .startForegroundService(
                    context,
                    servisIntent
                )

            takipAktif = true
            hedefSeslendirici.kayitliKonus(TrafikKayitliSes.AGENT_STARTED)

            konumuVeTrafikBilgisiniOku()

        } catch (exception: Exception) {

            takipAktif = false

            durumMesaji =
                "Ajan başlatılamadı: " +
                        (
                                exception.localizedMessage
                                    ?: "Bilinmeyen hata"
                                )
        }
    }

    fun takipServisiniDurdur() {
        TrafikDurumDeposu.servisAktifliginiKaydet(
            context = context,
            aktif = false
        )
        takipAktif = false
        durumMesaji = "Ajan görevi durduruldu."

        val durdurmaIntent = Intent(
            context,
            TrafikTakipServisi::class.java
        ).apply {
            action = TrafikTakipServisi.EYLEM_DURDUR
        }

        try {
            context.startService(durdurmaIntent)
        } catch (_: Exception) {
            context.stopService(Intent(context, TrafikTakipServisi::class.java))
        }

        hedefSeslendirici.kayitliKonus(TrafikKayitliSes.AGENT_STOPPED)
    }

    val bildirimIzniLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission()
        ) { izinVerildi ->

            if (izinVerildi) {
                takipServisiniBaslat()
            } else {
                takipAktif = false

                durumMesaji =
                    "Bildirim izni verilmedi. Sürekli takip başlatılamadı."
            }
        }

    fun bildirimIzniniKontrolEtVeBaslat() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            val bildirimIzniVar =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission
                        .POST_NOTIFICATIONS
                ) ==
                        PackageManager
                            .PERMISSION_GRANTED

            if (bildirimIzniVar) {
                takipServisiniBaslat()
            } else {
                bildirimIzniLauncher.launch(
                    Manifest.permission
                        .POST_NOTIFICATIONS
                )
            }
        } else {
            takipServisiniBaslat()
        }
    }

    val konumIzniLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) { izinler ->

            val hassasKonumIzni =
                izinler[
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ] == true

            val yaklasikKonumIzni =
                izinler[
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ] == true

            if (
                hassasKonumIzni ||
                yaklasikKonumIzni
            ) {
                bildirimIzniniKontrolEtVeBaslat()
            } else {
                takipAktif = false

                durumMesaji =
                    "Konum izni verilmedi. Trafik ajanı çalışamaz."
            }
        }
    LaunchedEffect(
        sesliTakipBaslatilsin
    ) {
        if (!sesliTakipBaslatilsin) {
            return@LaunchedEffect
        }

        sesliTakipBaslatilsin =
            false

        if (takipAktif) {
            durumMesaji =
                "Yeni hedef kaydedildi. Trafik ajanı hedefi izlemeye başladı."

            return@LaunchedEffect
        }

        val hassasKonumIzniVar =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission
                    .ACCESS_FINE_LOCATION
            ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        val yaklasikKonumIzniVar =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission
                    .ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        if (
            hassasKonumIzniVar ||
            yaklasikKonumIzniVar
        ) {
            bildirimIzniniKontrolEtVeBaslat()
        } else {
            konumIzniLauncher.launch(
                arrayOf(
                    Manifest.permission
                        .ACCESS_FINE_LOCATION,

                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    LaunchedEffect(disSesliKomutSayaci) {
        if (
            disSesliKomutSayaci <= 0 ||
            disSesliKomutSayaci == sonIslenenDisSesliKomut ||
            disSesliKomut.isBlank()
        ) {
            return@LaunchedEffect
        }

        sonIslenenDisSesliKomut = disSesliKomutSayaci
        val alinmisKomut = disSesliKomut
        onDisSesliKomutIslendi()

        when (val komut = TrafikSesliKomutCozumleyici.cozumle(alinmisKomut)) {
            TrafikSesliKomut.Geri -> onBack()
            TrafikSesliKomut.AnaEkran -> onHome()
            TrafikSesliKomut.Ayarlar -> onSettings()

            TrafikSesliKomut.AjanBaslat -> {
                if (takipAktif) {
                    hedefSeslendirici.konus("Trafik Ajanı zaten çalışıyor.")
                } else {
                    val hassasKonumIzniVar =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                    val yaklasikKonumIzniVar =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                    if (hassasKonumIzniVar || yaklasikKonumIzniVar) {
                        bildirimIzniniKontrolEtVeBaslat()
                    } else {
                        konumIzniLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }

            TrafikSesliKomut.AjanDurdur -> {
                if (takipAktif) {
                    takipServisiniDurdur()
                } else {
                    hedefSeslendirici.konus("Trafik Ajanı zaten duruyor.")
                }
            }

            TrafikSesliKomut.SesliUyariAc -> {
                sesliUyariAcik = true
                AjanAyarlari.sesliUyariyiAyarla(context = context, acik = true)
                hedefSeslendirici.kayitliKonus(TrafikKayitliSes.VOICE_ON)
            }

            TrafikSesliKomut.SesliUyariKapat -> {
                sesliUyariAcik = false
                AjanAyarlari.sesliUyariyiAyarla(context = context, acik = false)
                hedefSeslendirici.kayitliKonus(TrafikKayitliSes.VOICE_OFF)
            }

            TrafikSesliKomut.TrafikDurumu -> {
                hedefSeslendirici.kayitliKonus(
                    TrafikKayitliSes.STATUS_INTRO,
                    dinamikMetinSonra = trafikDurumOzetiniOlustur(20)
                )
            }

            is TrafikSesliKomut.Tara -> {
                if (secilenHedef == null) {
                    sesliDinlemeAsamasi = "HEDEF"
                    sesliOnayBekleyenHedef = null
                    hedefAramaAcik = true
                    sesliSoruMetni = "Nereye gidiyorsunuz?"
                } else {
                    sesliTaramaMesafesiKm = komut.kilometre
                    sesliTaramaSonucuBekleniyor = true
                    sesliTaramaIstekSayaci += 1
                    hedefSeslendirici.kayitliKonus(
                        TrafikKayitliSes.SCAN_STARTED,
                        dinamikMetinSonra = "Önümüzdeki ${komut.kilometre} kilometre kontrol ediliyor."
                    )
                }
            }

            TrafikSesliKomut.HedefSor -> {
                sesliDinlemeAsamasi = "HEDEF"
                sesliOnayBekleyenHedef = null
                hedefAramaAcik = true
                hedefAramaDurumu = ""
                sesliSoruMetni = "Nereye gidiyorsunuz?"
            }

            TrafikSesliKomut.HedefSil -> {
                HedefDeposu.hedefiSil(context)
                TrafikDurumDeposu.trafikDurumunuTemizle(context)
                secilenHedef = null
                sonRotaSonucu = null
                kayitliTrafikDurumu = null
                hedefSonuclari = emptyList()
                hedefAramaAcik = true
                hedefAramaDurumu = "Kayıtlı hedef silindi."
                sesliOnayBekleyenHedef = null
                sesliAramaBekliyor = false
                hedefSeslendirici.kayitliKonus(TrafikKayitliSes.TARGET_CANCELLED)
            }

            is TrafikSesliKomut.HedefAyarla -> {
                hedefAramaMetni = komut.hedefMetni
                hedefAramaAcik = true
                hedefAramaDurumu = "Gemini hedefi aranıyor: ${komut.hedefMetni}"
                sesliDinlemeAsamasi = "HEDEF"
                sesliOnayBekleyenHedef = null
                geminiHariciHedefAkisi = true
                sesliAramaBekliyor = true
            }

            TrafikSesliKomut.Yardim -> {
                hedefSeslendirici.konus(
                    "Şunları söyleyebilirsiniz. Hedef belirle. Ankara'ya git. Hedefi sil. Trafik Ajanını başlat veya durdur. 5, 10 ya da 20 kilometreyi tara. Trafik durumunu söyle. Sesli uyarıları aç veya kapat. Ayarları aç. Ana ekran. Geri."
                )
            }

            TrafikSesliKomut.Bilinmiyor -> {
                hedefSeslendirici.kayitliKonus(TrafikKayitliSes.COMMAND_UNKNOWN)
            }
        }
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize(),

        containerColor =
            AjanArkaPlan
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    AjanArkaPlan
                )
                .padding(innerPadding)
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 18.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "TRAFİK AJANI",

                        color = AjanYazi,
                        fontSize = 26.sp,
                        fontWeight =
                            FontWeight.ExtraBold,

                        letterSpacing = 1.6.sp
                    )

                    Text(
                        text =
                            "CANLI YOL İSTİHBARATI",

                        color = AjanCyan,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold,

                        letterSpacing = 1.4.sp
                    )
                }

                AjanDurumRozeti(
                    takipAktif =
                        takipAktif
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text =
                    "TA // MOBİL OPERASYON MERKEZİ",

                color = AjanSolukYazi,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            AjanRadar(
                takipAktif = takipAktif,

                trafikPuani =
                    ilk20KmPuani?.puan,

                modifier =
                    Modifier.widthIn(
                        max = 340.dp
                    )
            )
            kayitliTrafikDurumu?.let {
                    kayitliDurum ->

                val kayitliPuanBilgisi =
                    TrafikPuanlayici
                        .puanBilgisiOlustur(
                            kayitliDurum
                                .ilk20KmPuani
                        )
                val ilk5KmPuanBilgisi =
                    TrafikPuanlayici
                        .puanBilgisiOlustur(
                            kayitliDurum
                                .ilk5KmPuani
                        )

                val besOnKmPuanBilgisi =
                    TrafikPuanlayici
                        .puanBilgisiOlustur(
                            kayitliDurum
                                .besOnKmPuani
                        )

                val onYirmiKmPuanBilgisi =
                    TrafikPuanlayici
                        .puanBilgisiOlustur(
                            kayitliDurum
                                .onYirmiKmPuani
                        )
                AjanPanel(
                    baslik = "Otomatik tarama",

                    vurguRengi =
                        ajanTrafikRengi(
                            kayitliPuanBilgisi.puan
                        )
                ) {
                    Text(
                        text =
                            "HEDEF: ${kayitliDurum.hedefAdi}",

                        color = AjanSolukYazi,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "Önümüzdeki 20 km",

                        durum =
                            "${kayitliPuanBilgisi.puan}/10 • " +
                                    kayitliPuanBilgisi.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                kayitliPuanBilgisi.puan
                            )
                    )
                    Text(
                        text = "MESAFE BÖLGELERİ",
                        color = AjanSolukYazi,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "0–5 km",

                        durum =
                            "${ilk5KmPuanBilgisi.puan}/10 • " +
                                    ilk5KmPuanBilgisi.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                ilk5KmPuanBilgisi.puan
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(7.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "5–10 km",

                        durum =
                            "${besOnKmPuanBilgisi.puan}/10 • " +
                                    besOnKmPuanBilgisi.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                besOnKmPuanBilgisi.puan
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(7.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "10–20 km",

                        durum =
                            "${onYirmiKmPuanBilgisi.puan}/10 • " +
                                    onYirmiKmPuanBilgisi.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                onYirmiKmPuanBilgisi.puan
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )
                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            "Son tarama: " +
                                    taramaSaatiMetni(
                                        kayitliDurum
                                            .sonKontrolZamani
                                    ),

                        color = AjanYazi,
                        fontSize = 13.sp
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            if (takipAktif) {
                                "Yeni taramaya: " +
                                        kalanTaramaSuresiMetni(
                                            sonrakiKontrolZamani =
                                                kayitliDurum
                                                    .sonrakiKontrolZamani,

                                            simdikiZaman =
                                                simdikiZaman
                                        )
                            } else {
                                "Otomatik takip durduruldu"
                            },

                        color =
                            if (takipAktif) {
                                AjanCyan
                            } else {
                                AjanSolukYazi
                            },

                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }
            sonRotaSonucu?.let { sonuc ->

                AjanPanel(
                    baslik =
                        "20 km durum özeti",

                    vurguRengi =
                        ajanTrafikRengi(
                            ilk20KmPuani?.puan
                                ?: 0
                        )
                ) {
                    val ilkBolge =
                        TrafikPuanlayici
                            .bolgePuani(
                                sonuc,
                                0,
                                5
                            )

                    val ikinciBolge =
                        TrafikPuanlayici
                            .bolgePuani(
                                sonuc,
                                5,
                                10
                            )

                    val ucuncuBolge =
                        TrafikPuanlayici
                            .bolgePuani(
                                sonuc,
                                10,
                                20
                            )

                    AjanMesafeDurumSatiri(
                        mesafe = "0–5 km",
                        durum =
                            "${ilkBolge.puan}/10 • " +
                                    ilkBolge.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                ilkBolge.puan
                            )
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "5–10 km",
                        durum =
                            "${ikinciBolge.puan}/10 • " +
                                    ikinciBolge.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                ikinciBolge.puan
                            )
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    AjanMesafeDurumSatiri(
                        mesafe = "10–20 km",
                        durum =
                            "${ucuncuBolge.puan}/10 • " +
                                    ucuncuBolge.durum,

                        durumRengi =
                            ajanTrafikRengi(
                                ucuncuBolge.puan
                            )
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )
            }

            Button(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (takipAktif) {
                                AjanKirmizi
                            } else {
                                AjanYesil
                            },

                        contentColor =
                            AjanArkaPlan
                    ),

                shape =
                    RoundedCornerShape(14.dp),

                onClick = {

                    if (takipAktif) {
                        takipServisiniDurdur()
                    } else {

                        val hassasKonumIzniVar =
                            ContextCompat
                                .checkSelfPermission(
                                    context,
                                    Manifest.permission
                                        .ACCESS_FINE_LOCATION
                                ) ==
                                    PackageManager
                                        .PERMISSION_GRANTED

                        val yaklasikKonumIzniVar =
                            ContextCompat
                                .checkSelfPermission(
                                    context,
                                    Manifest.permission
                                        .ACCESS_COARSE_LOCATION
                                ) ==
                                    PackageManager
                                        .PERMISSION_GRANTED

                        if (
                            hassasKonumIzniVar ||
                            yaklasikKonumIzniVar
                        ) {
                            bildirimIzniniKontrolEtVeBaslat()
                        } else {
                            konumIzniLauncher.launch(
                                arrayOf(
                                    Manifest.permission
                                        .ACCESS_FINE_LOCATION,

                                    Manifest.permission
                                        .ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                }
            ) {
                Text(
                    text =
                        if (takipAktif) {
                            "AJANI DURDUR"
                        } else {
                            "AJANI BAŞLAT"
                        },

                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,

                    modifier =
                        Modifier.padding(
                            vertical = 5.dp
                        )
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            AjanPanel(
                baslik = "Canlı sistem durumu",

                vurguRengi =
                    if (takipAktif) {
                        AjanYesil
                    } else {
                        AjanSolukYazi
                    }
            ) {
                Text(
                    text = durumMesaji,
                    color = AjanYazi,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )
            AjanPanel(
                baslik = "Ajan ayarları",

                vurguRengi =
                    if (sesliUyariAcik) {
                        AjanYesil
                    } else {
                        AjanSolukYazi
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "TTS + MP3 trafik sesleri",
                            color = AjanYazi,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(3.dp)
                        )

                        Text(
                            text =
                                if (sesliUyariAcik) {
                                    "Eski TTS ve MP3 trafik anonsları açık; trafik olaylarını bunlar seslendirir."
                                } else {
                                    "Eski TTS/MP3 kapalı. Sesli uyarıları Gemini verir."
                                },

                            color = AjanSolukYazi,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = sesliUyariAcik,

                        onCheckedChange = { acik ->
                            sesliUyariAcik = acik

                            AjanAyarlari
                                .sesliUyariyiAyarla(
                                    context = context,
                                    acik = acik
                                )

                            hedefSeslendirici.kayitliKonus(
                                if (acik) TrafikKayitliSes.VOICE_ON
                                else TrafikKayitliSes.VOICE_OFF
                            )
                        },

                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor =
                                    AjanArkaPlan,

                                checkedTrackColor =
                                    AjanYesil,

                                uncheckedThumbColor =
                                    AjanSolukYazi,

                                uncheckedTrackColor =
                                    AjanPanelIkinciRenk,

                                uncheckedBorderColor =
                                    AjanSolukYazi
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )
            AjanPanel(
                baslik = "Görev hedefi",
                vurguRengi = AjanCyan
            ) {
                secilenHedef?.let { hedef ->

                    Text(
                        text = "AKTİF HEDEF",
                        color = AjanCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text = hedef.ad,
                        color = AjanYazi,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (
                        hedef.adres.isNotBlank() &&
                        hedef.adres != hedef.ad
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text = hedef.adres,
                            color = AjanSolukYazi,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        OutlinedButton(
                            modifier =
                                Modifier.weight(1f),

                            border =
                                BorderStroke(
                                    1.dp,
                                    AjanCyan
                                ),

                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            AjanCyan
                                    ),

                            onClick = {
                                hedefAramaAcik = true
                                hedefAramaDurumu = ""
                            }
                        ) {
                            Text("HEDEFİ DEĞİŞTİR")
                        }

                        OutlinedButton(
                            modifier =
                                Modifier.weight(1f),

                            border =
                                BorderStroke(
                                    1.dp,
                                    AjanKirmizi
                                ),

                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            AjanKirmizi
                                    ),

                            onClick = {
                                HedefDeposu.hedefiSil(
                                    context
                                )

                                TrafikDurumDeposu
                                    .trafikDurumunuTemizle(
                                        context
                                    )

                                secilenHedef = null
                                sonRotaSonucu = null
                                kayitliTrafikDurumu = null
                                hedefAramaAcik = true

                                hedefAramaDurumu =
                                    "Kayıtlı hedef silindi."
                            }
                        ) {
                            Text("HEDEFİ SİL")
                        }
                    }
                }

                if (secilenHedef == null) {
                    Text(
                        text =
                            "Henüz bir görev hedefi seçilmedi.",

                        color = AjanSolukYazi
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    AjanCyan,

                                contentColor =
                                    AjanArkaPlan
                            ),

                        onClick = {
                            hedefAramaAcik = true
                        }
                    ) {
                        Text(
                            text = "HEDEF BELİRLE",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                if (hedefAramaAcik) {

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    HorizontalDivider(
                        color =
                            AjanCyan.copy(
                                alpha = 0.20f
                            )
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    Text(
                        text = "YENİ HEDEF ARA",
                        color = AjanYazi,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    OutlinedTextField(
                        value =
                            hedefAramaMetni,

                        onValueChange = {
                            hedefAramaMetni = it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Şehir, adres veya yer"
                            )
                        },

                        placeholder = {
                            Text(
                                "Örnek: Antalya Havalimanı"
                            )
                        },

                        singleLine = true,

                        colors =
                            OutlinedTextFieldDefaults
                                .colors(
                                    focusedTextColor =
                                        AjanYazi,

                                    unfocusedTextColor =
                                        AjanYazi,

                                    cursorColor =
                                        AjanCyan,

                                    focusedBorderColor =
                                        AjanCyan,

                                    unfocusedBorderColor =
                                        AjanSolukYazi
                                            .copy(
                                                alpha = 0.45f
                                            ),

                                    focusedLabelColor =
                                        AjanCyan,

                                    unfocusedLabelColor =
                                        AjanSolukYazi,

                                    focusedPlaceholderColor =
                                        AjanSolukYazi,

                                    unfocusedPlaceholderColor =
                                        AjanSolukYazi
                                )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AjanPanelIkinciRenk,
                            contentColor = AjanCyan
                        ),
                        onClick = {
                            sesliDinlemeAsamasi = "HEDEF"
                            sesliOnayBekleyenHedef = null
                            sesliSoruMetni = "Nereye gidiyorsunuz?"
                        }
                    ) {
                        Text("🎙  HEDEFİ SESLE GİR", fontWeight = FontWeight.Bold)
                    }

                    if (
                        mevcutEnlem == null ||
                        mevcutBoylam == null
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(7.dp)
                        )

                        Text(
                            text =
                                "Hedef aramak için önce ajanı başlatın.",

                            color = AjanSari,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            hedefAramaMetni
                                .isNotBlank() &&
                                    mevcutEnlem != null &&
                                    mevcutBoylam != null,

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    AjanCyan,

                                contentColor =
                                    AjanArkaPlan,

                                disabledContainerColor =
                                    AjanPanelIkinciRenk,

                                disabledContentColor =
                                    AjanSolukYazi
                            ),

                        onClick = {
                            val enlem =
                                mevcutEnlem

                            val boylam =
                                mevcutBoylam

                            if (
                                enlem == null ||
                                boylam == null
                            ) {
                                hedefAramaDurumu =
                                    "Önce ajanı başlatın."

                                return@Button
                            }

                            hedefAramaDurumu =
                                "Hedef istihbaratı aranıyor..."

                            hedefSonuclari =
                                emptyList()

                            TomTomSearchClient
                                .hedefAra(
                                    aramaMetni =
                                        hedefAramaMetni,

                                    mevcutEnlem =
                                        enlem,

                                    mevcutBoylam =
                                        boylam,

                                    basarili = {
                                            sonuclar ->

                                        hedefSonuclari =
                                            sonuclar

                                        hedefAramaDurumu =
                                            "${sonuclar.size} hedef bulundu."
                                    },

                                    hata = {
                                            hataMesaji ->

                                        hedefSonuclari =
                                            emptyList()

                                        hedefAramaDurumu =
                                            hataMesaji
                                    }
                                )
                        }
                    ) {
                        Text(
                            text = "HEDEF ARA",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    if (
                        hedefAramaDurumu
                            .isNotBlank()
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            text =
                                hedefAramaDurumu,

                            color = AjanCyan,
                            fontSize = 13.sp,
                            textAlign =
                                TextAlign.Center,

                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    hedefSonuclari.forEach {
                            hedef ->

                        Spacer(
                            modifier =
                                Modifier.height(9.dp)
                        )

                        OutlinedButton(
                            modifier =
                                Modifier.fillMaxWidth(),

                            border =
                                BorderStroke(
                                    width = 1.dp,
                                    color =
                                        AjanCyan.copy(
                                            alpha = 0.35f
                                        )
                                ),

                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            AjanYazi
                                    ),

                            shape =
                                RoundedCornerShape(
                                    12.dp
                                ),

                            onClick = {
                                secilenHedef = hedef
                                sonRotaSonucu = null
                                kayitliTrafikDurumu = null

                                TrafikDurumDeposu
                                    .trafikDurumunuTemizle(
                                        context
                                    )

                                HedefDeposu
                                    .hedefiKaydet(
                                        context =
                                            context,

                                        hedef =
                                            hedef
                                    )

                                hedefAramaDurumu =
                                    "Hedef kaydedildi: ${hedef.ad}"

                                hedefSonuclari =
                                    emptyList()

                                hedefAramaAcik =
                                    false
                            }
                        ) {
                            val uzaklik =
                                hedefUzaklikMetni(
                                    hedef
                                        .uzaklikMetre
                                )

                            Text(
                                text =
                                    buildString {
                                        append(
                                            hedef.ad
                                        )

                                        if (
                                            hedef.adres
                                                .isNotBlank() &&
                                            hedef.adres !=
                                            hedef.ad
                                        ) {
                                            append("\n")
                                            append(
                                                hedef.adres
                                            )
                                        }

                                        if (
                                            uzaklik
                                                .isNotBlank()
                                        ) {
                                            append(
                                                "\nYaklaşık "
                                            )

                                            append(
                                                uzaklik
                                            )
                                        }
                                    },

                                textAlign =
                                    TextAlign.Center
                            )
                        }
                    }
                }
            }

            secilenHedef?.let { hedef ->

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                RotaAnalizKarti(
                    baslangicEnlem = mevcutEnlem,
                    baslangicBoylam = mevcutBoylam,
                    hedef = hedef,
                    yenilemeIstekSayaci = sesliTaramaIstekSayaci,
                    onRotaSonucu = { sonuc ->
                        sonRotaSonucu = sonuc

                        if (sonuc != null && sesliTaramaSonucuBekleniyor) {
                            sesliTaramaSonucuBekleniyor = false
                            hedefSeslendirici.kayitliKonus(
                                TrafikKayitliSes.STATUS_INTRO,
                                dinamikMetinSonra = trafikDurumOzetiniOlustur(sesliTaramaMesafesiKm)
                            )
                        }
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text =
                    "TRAFİK AJANI // TOMTOM LIVE DATA",

                color = AjanSolukYazi,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )

            Spacer(
                modifier = Modifier.height(30.dp)
            )
        }
    }
}
fun taramaSaatiMetni(
    zamanMillis: Long
): String {

    if (zamanMillis <= 0L) {
        return "--:--"
    }

    return SimpleDateFormat(
        "HH:mm:ss",
        Locale.forLanguageTag("tr-TR")
    ).format(
        Date(zamanMillis)
    )
}

fun kalanTaramaSuresiMetni(
    sonrakiKontrolZamani: Long,
    simdikiZaman: Long
): String {

    val kalanMillis =
        sonrakiKontrolZamani -
                simdikiZaman

    if (kalanMillis <= 0L) {
        return "kontrol bekleniyor"
    }

    val toplamSaniye =
        (kalanMillis + 999L) /
                1_000L

    val dakika =
        toplamSaniye / 60L

    val saniye =
        toplamSaniye % 60L

    return if (dakika > 0L) {
        "$dakika dk $saniye sn"
    } else {
        "$saniye sn"
    }
}
