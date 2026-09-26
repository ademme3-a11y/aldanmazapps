package com.aldanmaz.drivedashboard.trafficagent
import com.aldanmaz.drivedashboard.BuildConfig

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RotaTrafikOlayi(
    val baslangicMetre: Double,
    val bitisMetre: Double,
    val kategori: String,
    val gecikmeSaniye: Int,
    val etkiliHizKmSaat: Int?,
    val gecikmeBuyuklugu: Int,
    val yolAdi: String?
) {

    fun kategoriMetni(): String {
        return when (kategori.uppercase()) {
            "JAM" -> "Trafik yoğunluğu"
            "ROAD_WORK" -> "Yol çalışması"
            "ROAD_CLOSURE" -> "Yol kapalı"
            "OTHER" -> "Diğer trafik sorunu"
            else -> "Trafik sorunu"
        }
    }

    fun siddetMetni(): String {
        return when (gecikmeBuyuklugu) {
            1 -> "Hafif"
            2 -> "Orta"
            3 -> "Ciddi"
            4 -> "Yol kapanması veya belirsiz gecikme"
            else -> "Belirlenemedi"
        }
    }
}

data class RotaTrafikSonucu(
    val toplamMesafeMetre: Int,
    val tahminiSureSaniye: Int,
    val normalSureSaniye: Int,
    val toplamGecikmeSaniye: Int,
    val trafiktenEtkilenenMesafeMetre: Int,
    val olaylar: List<RotaTrafikOlayi>
) {

    fun bolgedekiOlaylar(
        baslangicMetre: Double,
        bitisMetre: Double
    ): List<RotaTrafikOlayi> {

        return olaylar.filter { olay ->

            val olayBaslangici =
                olay.baslangicMetre

            val olayBitisi =
                if (
                    olay.bitisMetre >
                    olay.baslangicMetre
                ) {
                    olay.bitisMetre
                } else {
                    olay.baslangicMetre + 1.0
                }

            olayBaslangici < bitisMetre &&
                    olayBitisi >= baslangicMetre
        }
    }
}

private data class RotaNoktasi(
    val enlem: Double,
    val boylam: Double
)

object TomTomRouteClient {

    private val anaIsParcacigi =
        Handler(Looper.getMainLooper())

    fun rotaTrafikBilgisiniAl(
        baslangicEnlem: Double,
        baslangicBoylam: Double,
        hedefEnlem: Double,
        hedefBoylam: Double,
        aracYonu: Int? = null,
        basarili: (RotaTrafikSonucu) -> Unit,
        hata: (String) -> Unit
    ) {

        val apiAnahtari =
            BuildConfig.TOMTOM_API_KEY.trim()

        if (apiAnahtari.isBlank()) {
            hataMesajiGonder(
                hata,
                "TomTom API anahtarı bulunamadı."
            )
            return
        }

        thread {

            var baglanti: HttpURLConnection? = null

            try {
                val baslangicNoktasi =
                    String.format(
                        Locale.US,
                        "%.6f,%.6f",
                        baslangicEnlem,
                        baslangicBoylam
                    )

                val hedefNoktasi =
                    String.format(
                        Locale.US,
                        "%.6f,%.6f",
                        hedefEnlem,
                        hedefBoylam
                    )

                val kodlanmisAnahtar =
                    URLEncoder.encode(
                        apiAnahtari,
                        StandardCharsets.UTF_8.toString()
                    )

                val yonParametresi =
                    if (
                        aracYonu != null &&
                        aracYonu in 0..359
                    ) {
                        "&vehicleHeading=$aracYonu"
                    } else {
                        ""
                    }

                val istekAdresi =
                    "https://api.tomtom.com/routing/1/" +
                            "calculateRoute/" +
                            "$baslangicNoktasi:" +
                            "$hedefNoktasi/json" +
                            "?key=$kodlanmisAnahtar" +
                            "&traffic=true" +
                            "&travelMode=car" +
                            "&routeType=fastest" +
                            "&departAt=now" +
                            "&computeTravelTimeFor=all" +
                            "&sectionType=traffic" +
                            "&sectionType=importantRoadStretch" +
                            "&routeRepresentation=polyline" +
                            "&language=tr-TR" +
                            yonParametresi

                val url = URL(istekAdresi)

                baglanti =
                    url.openConnection() as HttpURLConnection

                baglanti.requestMethod = "GET"
                baglanti.connectTimeout = 20_000
                baglanti.readTimeout = 20_000
                baglanti.doInput = true

                val cevapKodu =
                    baglanti.responseCode

                if (
                    cevapKodu !=
                    HttpURLConnection.HTTP_OK
                ) {

                    val mesaj = when (cevapKodu) {
                        400 ->
                            "Rota hesaplama isteği geçersiz. Hata kodu: 400"

                        403 ->
                            "TomTom API anahtarı rota için kabul edilmedi. Hata kodu: 403"

                        404 ->
                            "Başlangıç ile hedef arasında uygun rota bulunamadı."

                        429 ->
                            "TomTom rota sorgu sınırı aşıldı. Hata kodu: 429"

                        500 ->
                            "TomTom rota sunucusunda hata oluştu. Hata kodu: 500"

                        else ->
                            "Rota hesaplama hatası. Hata kodu: $cevapKodu"
                    }

                    hataMesajiGonder(
                        hata,
                        mesaj
                    )

                    return@thread
                }

                val cevapMetni =
                    baglanti.inputStream
                        .bufferedReader()
                        .use { okuyucu ->
                            okuyucu.readText()
                        }

                val anaJson =
                    JSONObject(cevapMetni)

                val rotalarJson =
                    anaJson.optJSONArray("routes")

                if (
                    rotalarJson == null ||
                    rotalarJson.length() == 0
                ) {
                    hataMesajiGonder(
                        hata,
                        "Seçilen hedefe uygun rota bulunamadı."
                    )
                    return@thread
                }

                val rotaJson =
                    rotalarJson.getJSONObject(0)

                val ozetJson =
                    rotaJson.getJSONObject("summary")

                val noktalar =
                    rotaNoktalariniOku(rotaJson)

                if (noktalar.size < 2) {
                    hataMesajiGonder(
                        hata,
                        "Rota noktaları alınamadı."
                    )
                    return@thread
                }

                val birikimliMesafeler =
                    birikimliMesafeleriHesapla(
                        noktalar
                    )

                val bolumlerJson =
                    rotaJson.optJSONArray("sections")
                        ?: JSONArray()

                val yolBolumleri =
                    yolBolumleriniAyir(
                        bolumlerJson
                    )

                val trafikOlaylari =
                    trafikOlaylariniOku(
                        bolumlerJson =
                            bolumlerJson,

                        yolBolumleri =
                            yolBolumleri,

                        birikimliMesafeler =
                            birikimliMesafeler
                    )

                val tahminiSure =
                    ozetJson.optInt(
                        "travelTimeInSeconds",
                        0
                    )

                val toplamGecikme =
                    ozetJson.optInt(
                        "trafficDelayInSeconds",
                        0
                    )

                val normalSure =
                    ozetJson.optInt(
                        "noTrafficTravelTimeInSeconds",
                        (tahminiSure - toplamGecikme)
                            .coerceAtLeast(0)
                    )

                val sonuc =
                    RotaTrafikSonucu(
                        toplamMesafeMetre =
                            ozetJson.optInt(
                                "lengthInMeters",
                                0
                            ),

                        tahminiSureSaniye =
                            tahminiSure,

                        normalSureSaniye =
                            normalSure,

                        toplamGecikmeSaniye =
                            toplamGecikme,

                        trafiktenEtkilenenMesafeMetre =
                            ozetJson.optInt(
                                "trafficLengthInMeters",
                                0
                            ),

                        olaylar =
                            trafikOlaylari
                    )

                anaIsParcacigi.post {
                    basarili(sonuc)
                }

            } catch (exception: Exception) {

                hataMesajiGonder(
                    hata,
                    "Rota trafik bilgisi alınamadı: " +
                            (exception.localizedMessage
                                ?: "Bilinmeyen bağlantı hatası")
                )

            } finally {
                baglanti?.disconnect()
            }
        }
    }

    private fun rotaNoktalariniOku(
        rotaJson: JSONObject
    ): List<RotaNoktasi> {

        val noktalar =
            mutableListOf<RotaNoktasi>()

        val ayaklarJson =
            rotaJson.optJSONArray("legs")
                ?: return emptyList()

        for (
        ayakSirasi in
        0 until ayaklarJson.length()
        ) {
            val ayakJson =
                ayaklarJson.getJSONObject(
                    ayakSirasi
                )

            val noktalarJson =
                ayakJson.optJSONArray("points")
                    ?: continue

            for (
            noktaSirasi in
            0 until noktalarJson.length()
            ) {
                val noktaJson =
                    noktalarJson.getJSONObject(
                        noktaSirasi
                    )

                val yeniNokta =
                    RotaNoktasi(
                        enlem =
                            noktaJson.getDouble(
                                "latitude"
                            ),

                        boylam =
                            noktaJson.getDouble(
                                "longitude"
                            )
                    )

                val sonNokta =
                    noktalar.lastOrNull()

                val ayniNokta =
                    sonNokta != null &&
                            sonNokta.enlem ==
                            yeniNokta.enlem &&
                            sonNokta.boylam ==
                            yeniNokta.boylam

                if (!ayniNokta) {
                    noktalar.add(yeniNokta)
                }
            }
        }

        return noktalar
    }

    private fun birikimliMesafeleriHesapla(
        noktalar: List<RotaNoktasi>
    ): List<Double> {

        val mesafeler =
            MutableList(noktalar.size) {
                0.0
            }

        for (
        sira in
        1 until noktalar.size
        ) {
            val onceki =
                noktalar[sira - 1]

            val simdiki =
                noktalar[sira]

            mesafeler[sira] =
                mesafeler[sira - 1] +
                        ikiNoktaArasiMesafe(
                            onceki,
                            simdiki
                        )
        }

        return mesafeler
    }

    private fun ikiNoktaArasiMesafe(
        ilk: RotaNoktasi,
        ikinci: RotaNoktasi
    ): Double {

        val dunyaYaricapiMetre =
            6_371_000.0

        val ilkEnlemRadyan =
            Math.toRadians(ilk.enlem)

        val ikinciEnlemRadyan =
            Math.toRadians(ikinci.enlem)

        val enlemFarki =
            Math.toRadians(
                ikinci.enlem - ilk.enlem
            )

        val boylamFarki =
            Math.toRadians(
                ikinci.boylam - ilk.boylam
            )

        val a =
            sin(enlemFarki / 2.0) *
                    sin(enlemFarki / 2.0) +
                    cos(ilkEnlemRadyan) *
                    cos(ikinciEnlemRadyan) *
                    sin(boylamFarki / 2.0) *
                    sin(boylamFarki / 2.0)

        val c =
            2.0 * atan2(
                sqrt(a),
                sqrt(1.0 - a)
            )

        return dunyaYaricapiMetre * c
    }

    private fun yolBolumleriniAyir(
        bolumlerJson: JSONArray
    ): List<JSONObject> {

        val yolBolumleri =
            mutableListOf<JSONObject>()

        for (
        sira in
        0 until bolumlerJson.length()
        ) {
            val bolum =
                bolumlerJson.getJSONObject(sira)

            if (
                bolum.optString(
                    "sectionType"
                ) == "IMPORTANT_ROAD_STRETCH"
            ) {
                yolBolumleri.add(bolum)
            }
        }

        return yolBolumleri
    }

    private fun trafikOlaylariniOku(
        bolumlerJson: JSONArray,
        yolBolumleri: List<JSONObject>,
        birikimliMesafeler: List<Double>
    ): List<RotaTrafikOlayi> {

        val olaylar =
            mutableListOf<RotaTrafikOlayi>()

        val sonNoktaIndeksi =
            birikimliMesafeler.lastIndex

        for (
        sira in
        0 until bolumlerJson.length()
        ) {
            val bolum =
                bolumlerJson.getJSONObject(sira)

            if (
                bolum.optString(
                    "sectionType"
                ) != "TRAFFIC"
            ) {
                continue
            }

            val baslangicIndeksi =
                bolum.optInt(
                    "startPointIndex",
                    0
                ).coerceIn(
                    0,
                    sonNoktaIndeksi
                )

            val bitisIndeksi =
                bolum.optInt(
                    "endPointIndex",
                    baslangicIndeksi
                ).coerceIn(
                    0,
                    sonNoktaIndeksi
                )

            val yolAdi =
                yolAdiBul(
                    trafikBaslangicIndeksi =
                        baslangicIndeksi,

                    trafikBitisIndeksi =
                        bitisIndeksi,

                    yolBolumleri =
                        yolBolumleri
                )

            val hiz =
                if (
                    bolum.has(
                        "effectiveSpeedInKmh"
                    )
                ) {
                    bolum.optInt(
                        "effectiveSpeedInKmh"
                    )
                } else {
                    null
                }

            olaylar.add(
                RotaTrafikOlayi(
                    baslangicMetre =
                        birikimliMesafeler[
                            baslangicIndeksi
                        ],

                    bitisMetre =
                        birikimliMesafeler[
                            bitisIndeksi
                        ],

                    kategori =
                        bolum.optString(
                            "simpleCategory",
                            "OTHER"
                        ),

                    gecikmeSaniye =
                        bolum.optInt(
                            "delayInSeconds",
                            0
                        ),

                    etkiliHizKmSaat =
                        hiz,

                    gecikmeBuyuklugu =
                        bolum.optInt(
                            "magnitudeOfDelay",
                            0
                        ),

                    yolAdi =
                        yolAdi
                )
            )
        }

        return olaylar.sortedBy {
            it.baslangicMetre
        }
    }

    private fun yolAdiBul(
        trafikBaslangicIndeksi: Int,
        trafikBitisIndeksi: Int,
        yolBolumleri: List<JSONObject>
    ): String? {

        val uygunYolBolumu =
            yolBolumleri.firstOrNull {
                    yolBolumu ->

                val yolBaslangici =
                    yolBolumu.optInt(
                        "startPointIndex",
                        0
                    )

                val yolBitisi =
                    yolBolumu.optInt(
                        "endPointIndex",
                        yolBaslangici
                    )

                yolBaslangici <=
                        trafikBitisIndeksi &&
                        yolBitisi >=
                        trafikBaslangicIndeksi
            } ?: return null

        val sokakAdi =
            uygunYolBolumu
                .optJSONObject("streetName")
                ?.optString("text")
                ?.takeIf {
                    it.isNotBlank()
                }

        if (sokakAdi != null) {
            return sokakAdi
        }

        val yolNumaralari =
            uygunYolBolumu
                .optJSONArray("roadNumbers")

        if (
            yolNumaralari != null &&
            yolNumaralari.length() > 0
        ) {
            val ilkYolNumarasi =
                yolNumaralari
                    .optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf {
                        it.isNotBlank()
                    }

            if (ilkYolNumarasi != null) {
                return ilkYolNumarasi
            }
        }

        return null
    }

    private fun hataMesajiGonder(
        hata: (String) -> Unit,
        mesaj: String
    ) {
        anaIsParcacigi.post {
            hata(mesaj)
        }
    }
}

