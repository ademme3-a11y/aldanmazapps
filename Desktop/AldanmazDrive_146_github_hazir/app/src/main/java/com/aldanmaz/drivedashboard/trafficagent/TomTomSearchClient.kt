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

data class HedefAramaSonucu(
    val ad: String,
    val adres: String,
    val enlem: Double,
    val boylam: Double,
    val uzaklikMetre: Double?
)

object TomTomSearchClient {

    private val anaIsParcacigi =
        Handler(Looper.getMainLooper())

    fun hedefAra(
        aramaMetni: String,
        mevcutEnlem: Double,
        mevcutBoylam: Double,
        basarili: (List<HedefAramaSonucu>) -> Unit,
        hata: (String) -> Unit
    ) {

        val temizAramaMetni =
            aramaMetni.trim()

        if (temizAramaMetni.isBlank()) {
            hataMesajiGonder(
                hata,
                "Lütfen gidilecek yer veya adresi yazın."
            )
            return
        }

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
                val kodlanmisArama =
                    URLEncoder.encode(
                        temizAramaMetni,
                        StandardCharsets.UTF_8.toString()
                    ).replace("+", "%20")

                val kodlanmisAnahtar =
                    URLEncoder.encode(
                        apiAnahtari,
                        StandardCharsets.UTF_8.toString()
                    )

                val enlemMetni =
                    String.format(
                        Locale.US,
                        "%.6f",
                        mevcutEnlem
                    )

                val boylamMetni =
                    String.format(
                        Locale.US,
                        "%.6f",
                        mevcutBoylam
                    )

                val istekAdresi =
                    "https://api.tomtom.com/search/2/search/" +
                            "$kodlanmisArama.json" +
                            "?key=$kodlanmisAnahtar" +
                            "&limit=5" +
                            "&language=tr-TR" +
                            "&view=TR" +
                            "&lat=$enlemMetni" +
                            "&lon=$boylamMetni"

                val url = URL(istekAdresi)

                baglanti =
                    url.openConnection() as HttpURLConnection

                baglanti.requestMethod = "GET"
                baglanti.connectTimeout = 15_000
                baglanti.readTimeout = 15_000
                baglanti.doInput = true

                val cevapKodu =
                    baglanti.responseCode

                if (
                    cevapKodu !=
                    HttpURLConnection.HTTP_OK
                ) {

                    val mesaj = when (cevapKodu) {
                        400 ->
                            "Hedef arama isteği geçersiz. Hata kodu: 400"

                        403 ->
                            "TomTom API anahtarı arama için kabul edilmedi. Hata kodu: 403"

                        429 ->
                            "TomTom arama sorgu sınırı aşıldı. Hata kodu: 429"

                        500 ->
                            "TomTom arama sunucusunda hata oluştu. Hata kodu: 500"

                        else ->
                            "Hedef arama hatası. Hata kodu: $cevapKodu"
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

                val sonuclarJson =
                    anaJson.optJSONArray("results")

                if (
                    sonuclarJson == null ||
                    sonuclarJson.length() == 0
                ) {
                    hataMesajiGonder(
                        hata,
                        "Aradığınız hedef bulunamadı."
                    )
                    return@thread
                }

                val sonuclar =
                    mutableListOf<HedefAramaSonucu>()

                for (
                sira in
                0 until sonuclarJson.length()
                ) {
                    val sonucJson =
                        sonuclarJson.getJSONObject(sira)

                    val konumJson =
                        sonucJson.optJSONObject("position")
                            ?: continue

                    val adresJson =
                        sonucJson.optJSONObject("address")

                    val poiJson =
                        sonucJson.optJSONObject("poi")

                    val adres =
                        adresJson?.optString(
                            "freeformAddress",
                            ""
                        ).orEmpty()

                    val ad =
                        poiJson?.optString(
                            "name",
                            ""
                        )
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: adres.takeIf {
                                it.isNotBlank()
                            }
                            ?: "İsimsiz hedef"

                    val uzaklik =
                        if (sonucJson.has("dist")) {
                            sonucJson.optDouble("dist")
                        } else {
                            null
                        }

                    sonuclar.add(
                        HedefAramaSonucu(
                            ad = ad,
                            adres = adres,
                            enlem =
                                konumJson.getDouble("lat"),
                            boylam =
                                konumJson.getDouble("lon"),
                            uzaklikMetre = uzaklik
                        )
                    )
                }

                if (sonuclar.isEmpty()) {
                    hataMesajiGonder(
                        hata,
                        "Kullanılabilir hedef bulunamadı."
                    )
                    return@thread
                }

                anaIsParcacigi.post {
                    basarili(sonuclar)
                }

            } catch (exception: Exception) {

                hataMesajiGonder(
                    hata,
                    "Hedef aranamadı: " +
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
