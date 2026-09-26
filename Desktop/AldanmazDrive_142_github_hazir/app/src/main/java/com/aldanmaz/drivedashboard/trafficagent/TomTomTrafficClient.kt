package com.aldanmaz.drivedashboard.trafficagent
import com.aldanmaz.drivedashboard.BuildConfig

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

data class TrafikAkisBilgisi(
    val yolSinifi: String,
    val guncelHiz: Int,
    val normalHiz: Int,
    val guncelSeyahatSuresi: Int,
    val normalSeyahatSuresi: Int,
    val veriGuveni: Double,
    val yolKapali: Boolean
) {

    val gecikmeSaniyesi: Int
        get() = max(
            0,
            guncelSeyahatSuresi - normalSeyahatSuresi
        )

    fun yogunlukDurumu(): String {

        if (yolKapali) {
            return "Yol trafiğe kapalı"
        }

        if (normalHiz <= 0) {
            return "Trafik durumu belirlenemedi"
        }

        val hizOrani =
            guncelHiz.toDouble() / normalHiz.toDouble()

        return when {
            hizOrani >= 0.85 -> "Trafik akıcı"
            hizOrani >= 0.60 -> "Orta yoğunluk"
            hizOrani >= 0.35 -> "Yoğun trafik"
            else -> "Çok yoğun trafik"
        }
    }
}

object TomTomTrafficClient {

    private val anaIsParcacigi =
        Handler(Looper.getMainLooper())

    fun trafikAkisiniAl(
        enlem: Double,
        boylam: Double,
        basarili: (TrafikAkisBilgisi) -> Unit,
        hata: (String) -> Unit
    ) {

        val apiAnahtari =
            BuildConfig.TOMTOM_API_KEY.trim()

        if (apiAnahtari.isBlank()) {
            hataMesajiGonder(
                hata,
                "TomTom API anahtarı bulunamadı. local.properties dosyasını kontrol edin."
            )
            return
        }

        thread {

            var baglanti: HttpURLConnection? = null

            try {
                val gpsNoktasi = String.format(
                    Locale.US,
                    "%.6f,%.6f",
                    enlem,
                    boylam
                )

                val guvenliAnahtar = URLEncoder.encode(
                    apiAnahtari,
                    StandardCharsets.UTF_8.toString()
                )

                val istekAdresi =
                    "https://api.tomtom.com/traffic/services/4/" +
                            "flowSegmentData/absolute/18/json" +
                            "?key=$guvenliAnahtar" +
                            "&point=$gpsNoktasi" +
                            "&unit=kmph"

                val url = URL(istekAdresi)

                baglanti =
                    url.openConnection() as HttpURLConnection

                baglanti.requestMethod = "GET"
                baglanti.connectTimeout = 15_000
                baglanti.readTimeout = 15_000
                baglanti.doInput = true

                val cevapKodu = baglanti.responseCode

                if (cevapKodu != HttpURLConnection.HTTP_OK) {

                    val mesaj = when (cevapKodu) {
                        400 -> "TomTom isteği geçersiz. Hata kodu: 400"
                        403 -> "TomTom API anahtarı kabul edilmedi. Hata kodu: 403"
                        429 -> "TomTom sorgu sınırı aşıldı. Hata kodu: 429"
                        500 -> "TomTom sunucu hatası. Hata kodu: 500"
                        503 -> "TomTom servisine şu anda ulaşılamıyor. Hata kodu: 503"
                        else -> "TomTom trafik hatası. Hata kodu: $cevapKodu"
                    }

                    hataMesajiGonder(hata, mesaj)
                    return@thread
                }

                val cevapMetni =
                    baglanti.inputStream
                        .bufferedReader()
                        .use { okuyucu ->
                            okuyucu.readText()
                        }

                val anaJson = JSONObject(cevapMetni)

                val akisJson =
                    anaJson.getJSONObject("flowSegmentData")

                val trafikBilgisi = TrafikAkisBilgisi(
                    yolSinifi =
                        akisJson.optString("frc", "Bilinmiyor"),

                    guncelHiz =
                        akisJson.optInt("currentSpeed", 0),

                    normalHiz =
                        akisJson.optInt("freeFlowSpeed", 0),

                    guncelSeyahatSuresi =
                        akisJson.optInt("currentTravelTime", 0),

                    normalSeyahatSuresi =
                        akisJson.optInt("freeFlowTravelTime", 0),

                    veriGuveni =
                        akisJson.optDouble("confidence", 0.0),

                    yolKapali =
                        akisJson.optBoolean("roadClosure", false)
                )

                anaIsParcacigi.post {
                    basarili(trafikBilgisi)
                }

            } catch (exception: Exception) {

                hataMesajiGonder(
                    hata,
                    "Trafik bilgisi alınamadı: " +
                            (exception.localizedMessage
                                ?: "Bilinmeyen bağlantı hatası")
                )

            } finally {
                baglanti?.disconnect()
            }
        }
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

