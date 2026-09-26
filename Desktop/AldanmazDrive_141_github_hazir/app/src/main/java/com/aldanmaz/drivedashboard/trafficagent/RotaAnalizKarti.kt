package com.aldanmaz.drivedashboard.trafficagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.min

@Composable
fun RotaAnalizKarti(
    baslangicEnlem: Double?,
    baslangicBoylam: Double?,
    hedef: HedefAramaSonucu,
    modifier: Modifier = Modifier,
    yenilemeIstekSayaci: Int = 0,
    onRotaSonucu: (RotaTrafikSonucu?) -> Unit = {}
) {
    var analizYapiliyor by remember(
        hedef.enlem,
        hedef.boylam
    ) {
        mutableStateOf(false)
    }

    var analizMesaji by remember(
        hedef.enlem,
        hedef.boylam
    ) {
        mutableStateOf("")
    }

    var rotaSonucu by remember(
        hedef.enlem,
        hedef.boylam
    ) {
        mutableStateOf<RotaTrafikSonucu?>(null)
    }
    var otomatikTaramaYapildi by remember(
        hedef.enlem,
        hedef.boylam
    ) {
        mutableStateOf(false)
    }
    var sonIslenenYenilemeIstek by remember(hedef.enlem, hedef.boylam) {
        mutableStateOf(0)
    }
    LaunchedEffect(
        hedef.enlem,
        hedef.boylam
    ) {
        onRotaSonucu(null)
    }
    LaunchedEffect(
        baslangicEnlem,
        baslangicBoylam,
        hedef.enlem,
        hedef.boylam
    ) {
        val enlem = baslangicEnlem
        val boylam = baslangicBoylam

        if (
            enlem != null &&
            boylam != null &&
            !otomatikTaramaYapildi
        ) {
            otomatikTaramaYapildi = true
            analizYapiliyor = true
            rotaSonucu = null
            onRotaSonucu(null)

            analizMesaji =
                "Otomatik güzergâh taraması yapılıyor..."

            TomTomRouteClient
                .rotaTrafikBilgisiniAl(
                    baslangicEnlem = enlem,
                    baslangicBoylam = boylam,
                    hedefEnlem = hedef.enlem,
                    hedefBoylam = hedef.boylam,

                    basarili = { sonuc ->
                        analizYapiliyor = false
                        rotaSonucu = sonuc

                        analizMesaji =
                            "Otomatik rota istihbaratı güncellendi."

                        onRotaSonucu(sonuc)
                    },

                    hata = { hataMesaji ->
                        analizYapiliyor = false
                        rotaSonucu = null
                        analizMesaji = hataMesaji
                        onRotaSonucu(null)
                    }
                )
        }
    }
    LaunchedEffect(
        yenilemeIstekSayaci,
        baslangicEnlem,
        baslangicBoylam,
        hedef.enlem,
        hedef.boylam
    ) {
        if (yenilemeIstekSayaci <= 0 || yenilemeIstekSayaci == sonIslenenYenilemeIstek) {
            return@LaunchedEffect
        }

        val enlem = baslangicEnlem
        val boylam = baslangicBoylam
        if (enlem == null || boylam == null) {
            analizMesaji = "Sesli tarama için GPS konumu bekleniyor."
            return@LaunchedEffect
        }

        sonIslenenYenilemeIstek = yenilemeIstekSayaci
        analizYapiliyor = true
        rotaSonucu = null
        onRotaSonucu(null)
        analizMesaji = "Sesli komutla güzergâh taraması yapılıyor..."

        TomTomRouteClient.rotaTrafikBilgisiniAl(
            baslangicEnlem = enlem,
            baslangicBoylam = boylam,
            hedefEnlem = hedef.enlem,
            hedefBoylam = hedef.boylam,
            basarili = { sonuc ->
                analizYapiliyor = false
                rotaSonucu = sonuc
                analizMesaji = "Sesli tarama tamamlandı."
                onRotaSonucu(sonuc)
            },
            hata = { hataMesaji ->
                analizYapiliyor = false
                rotaSonucu = null
                analizMesaji = hataMesaji
                onRotaSonucu(null)
            }
        )
    }

    AjanPanel(
        baslik = "Rota istihbaratı",
        modifier = modifier,
        vurguRengi = AjanCyan
    ) {
        Text(
            text =
                "Ajan, seçilen güzergâhın önümüzdeki " +
                        "5, 10 ve 20 kilometresini tarayacak.",

            color = AjanSolukYazi,
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Button(
            modifier = Modifier.fillMaxWidth(),

            enabled =
                !analizYapiliyor &&
                        baslangicEnlem != null &&
                        baslangicBoylam != null,

            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AjanCyan,
                    contentColor = AjanArkaPlan,
                    disabledContainerColor =
                        AjanPanelIkinciRenk,
                    disabledContentColor =
                        AjanSolukYazi
                ),

            shape =
                RoundedCornerShape(12.dp),

            onClick = {
                val enlem = baslangicEnlem
                val boylam = baslangicBoylam

                if (
                    enlem == null ||
                    boylam == null
                ) {
                    analizMesaji =
                        "Önce trafik ajanını başlatın."
                    return@Button
                }

                analizYapiliyor = true
                rotaSonucu = null
                onRotaSonucu(null)

                analizMesaji =
                    "Güzergâh taranıyor ve trafik puanı hesaplanıyor..."

                TomTomRouteClient
                    .rotaTrafikBilgisiniAl(
                        baslangicEnlem = enlem,
                        baslangicBoylam = boylam,
                        hedefEnlem = hedef.enlem,
                        hedefBoylam = hedef.boylam,

                        basarili = { sonuc ->
                            analizYapiliyor = false
                            rotaSonucu = sonuc

                            analizMesaji =
                                "Rota istihbaratı güncellendi."

                            onRotaSonucu(sonuc)
                        },

                        hata = { hataMesaji ->
                            analizYapiliyor = false
                            rotaSonucu = null
                            analizMesaji = hataMesaji
                            onRotaSonucu(null)
                        }
                    )
            }
        ) {
            Text(
                text =
                    if (analizYapiliyor) {
                        "TARANIYOR..."
                    } else {
                        "5–10–20 KM TARAMASI BAŞLAT"
                    },

                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }

        if (analizMesaji.isNotBlank()) {
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = analizMesaji,

                color =
                    when {
                        analizYapiliyor ->
                            AjanCyan

                        rotaSonucu != null ->
                            AjanYesil

                        else ->
                            AjanKirmizi
                    },

                fontSize = 13.sp
            )
        }

        rotaSonucu?.let { sonuc ->

            val ilk20KmPuani =
                TrafikPuanlayici
                    .onumuzdeki20KmPuani(sonuc)

            val tumRotaPuani =
                TrafikPuanlayici
                    .rotaPuani(sonuc)

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            TrafikPuanKarti(
                baslik = "Önümüzdeki 20 km",
                puanBilgisi = ilk20KmPuani
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            RotaBilgiSatiri(
                etiket = "Tüm rota trafik puanı",
                deger =
                    "${tumRotaPuani.puan}/10 • " +
                            tumRotaPuani.durum,

                degerRengi =
                    ajanTrafikRengi(
                        tumRotaPuani.puan
                    )
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            HorizontalDivider(
                color =
                    AjanCyan.copy(
                        alpha = 0.20f
                    )
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "ROTA ÖZETİ",
                color = AjanYazi,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            RotaBilgiSatiri(
                etiket = "Toplam mesafe",
                deger =
                    mesafeMetniOlustur(
                        sonuc.toplamMesafeMetre
                    )
            )

            RotaBilgiSatiri(
                etiket = "Tahmini süre",
                deger =
                    gecikmeMetniOlustur(
                        sonuc.tahminiSureSaniye
                    )
            )

            RotaBilgiSatiri(
                etiket = "Trafiksiz süre",
                deger =
                    gecikmeMetniOlustur(
                        sonuc.normalSureSaniye
                    )
            )

            RotaBilgiSatiri(
                etiket = "Toplam gecikme",
                deger =
                    gecikmeMetniOlustur(
                        sonuc.toplamGecikmeSaniye
                    ),

                degerRengi =
                    if (
                        sonuc.toplamGecikmeSaniye > 0
                    ) {
                        AjanTuruncu
                    } else {
                        AjanYesil
                    }
            )

            RotaBilgiSatiri(
                etiket = "Etkilenen yol",
                deger =
                    mesafeMetniOlustur(
                        sonuc
                            .trafiktenEtkilenenMesafeMetre
                    )
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            RotaBolgeKarti(
                baslangicKm = 0,
                bitisKm = 5,
                sonuc = sonuc
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            RotaBolgeKarti(
                baslangicKm = 5,
                bitisKm = 10,
                sonuc = sonuc
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            RotaBolgeKarti(
                baslangicKm = 10,
                bitisKm = 20,
                sonuc = sonuc
            )
        }
    }
}

@Composable
private fun TrafikPuanKarti(
    baslik: String,
    puanBilgisi: TrafikPuanBilgisi
) {
    val puanRengi =
        ajanTrafikRengi(
            puanBilgisi.puan
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),

        color =
            puanRengi.copy(
                alpha = 0.10f
            ),

        shape =
            RoundedCornerShape(16.dp),

        border =
            BorderStroke(
                width = 1.dp,
                color =
                    puanRengi.copy(
                        alpha = 0.65f
                    )
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = "${puanBilgisi.puan}",
                color = puanRengi,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "/10",
                color = AjanSolukYazi,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier =
                    Modifier.padding(
                        start = 16.dp
                    )
            ) {
                Text(
                    text = baslik.uppercase(),
                    color = AjanSolukYazi,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = puanBilgisi.durum,
                    color = puanRengi,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RotaBilgiSatiri(
    etiket: String,
    deger: String,
    degerRengi: Color = AjanYazi
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = etiket,
            modifier = Modifier.weight(1f),
            color = AjanSolukYazi,
            fontSize = 13.sp
        )

        Text(
            text = deger,
            color = degerRengi,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RotaBolgeKarti(
    baslangicKm: Int,
    bitisKm: Int,
    sonuc: RotaTrafikSonucu
) {
    val baslangicMetre =
        baslangicKm * 1000.0

    val bitisMetre =
        bitisKm * 1000.0

    val rotaBuBolgeyeUlasiyor =
        sonuc.toplamMesafeMetre >
                baslangicMetre

    val gercekBitisKm =
        min(
            bitisKm.toDouble(),
            sonuc.toplamMesafeMetre /
                    1000.0
        )

    val olaylar =
        sonuc.bolgedekiOlaylar(
            baslangicMetre =
                baslangicMetre,

            bitisMetre =
                bitisMetre
        )

    val puanBilgisi =
        TrafikPuanlayici.bolgePuani(
            sonuc = sonuc,
            baslangicKm = baslangicKm,
            bitisKm = bitisKm
        )

    val bitisMetni =
        if (
            rotaBuBolgeyeUlasiyor &&
            gercekBitisKm != bitisKm.toDouble()
        ) {
            String.format(
                Locale.forLanguageTag("tr-TR"),
                "%.1f",
                gercekBitisKm
            )
        } else {
            bitisKm.toString()
        }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AjanMesafeDurumSatiri(
            mesafe =
                "$baslangicKm–$bitisMetni km",

            durum =
                if (rotaBuBolgeyeUlasiyor) {
                    "${puanBilgisi.puan}/10 • " +
                            puanBilgisi.durum
                } else {
                    "HEDEF DAHA YAKIN"
                },

            durumRengi =
                if (rotaBuBolgeyeUlasiyor) {
                    ajanTrafikRengi(
                        puanBilgisi.puan
                    )
                } else {
                    AjanSolukYazi
                }
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (!rotaBuBolgeyeUlasiyor) {
            Text(
                text =
                    "Seçilen hedef bu mesafeye ulaşmadan önce bitiyor.",

                color = AjanSolukYazi,
                fontSize = 13.sp
            )
        } else if (olaylar.isEmpty()) {
            Text(
                text =
                    "Bu bölümde bildirilen özel bir trafik sorunu yok.",

                color = AjanYesil,
                fontSize = 13.sp
            )
        } else {
            olaylar.forEachIndexed { sira, olay ->

                if (sira > 0) {
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                TrafikOlayKarti(olay)
            }
        }
    }
}

@Composable
private fun TrafikOlayKarti(
    olay: RotaTrafikOlayi
) {
    val kritik =
        olay.kategori.equals(
            "ROAD_CLOSURE",
            ignoreCase = true
        ) ||
                olay.gecikmeBuyuklugu >= 3

    val olayRengi =
        if (kritik) {
            AjanKirmizi
        } else {
            AjanTuruncu
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),

        color =
            olayRengi.copy(
                alpha = 0.08f
            ),

        shape =
            RoundedCornerShape(12.dp),

        border =
            BorderStroke(
                width = 1.dp,
                color =
                    olayRengi.copy(
                        alpha = 0.35f
                    )
            )
    ) {
        Column(
            modifier = Modifier.padding(13.dp)
        ) {
            Text(
                text =
                    olayUzaklikMetniOlustur(
                        olay.baslangicMetre
                    ) + " ileride",

                color = olayRengi,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = olay.kategoriMetni(),
                color = AjanYazi,
                fontWeight = FontWeight.SemiBold
            )

            olay.yolAdi
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { yolAdi ->
                    Text(
                        text = "Yol: $yolAdi",
                        color = AjanSolukYazi,
                        fontSize = 13.sp
                    )
                }

            Text(
                text =
                    "Gecikme: " +
                            gecikmeMetniOlustur(
                                olay.gecikmeSaniye
                            ),

                color = AjanSolukYazi,
                fontSize = 13.sp
            )

            olay.etkiliHizKmSaat?.let {
                    trafikHizi ->

                Text(
                    text =
                        "Trafik hızı: " +
                                "$trafikHizi km/sa",

                    color = AjanSolukYazi,
                    fontSize = 13.sp
                )
            }

            Text(
                text =
                    "Önem derecesi: " +
                            olay.siddetMetni(),

                color = olayRengi,
                fontSize = 13.sp
            )
        }
    }
}

private fun mesafeMetniOlustur(
    metre: Int
): String {
    return if (metre < 1000) {
        "$metre metre"
    } else {
        String.format(
            Locale.forLanguageTag("tr-TR"),
            "%.1f km",
            metre / 1000.0
        )
    }
}

private fun olayUzaklikMetniOlustur(
    metre: Double
): String {
    return if (metre < 1000.0) {
        "${metre.toInt()} metre"
    } else {
        String.format(
            Locale.forLanguageTag("tr-TR"),
            "%.1f km",
            metre / 1000.0
        )
    }
}