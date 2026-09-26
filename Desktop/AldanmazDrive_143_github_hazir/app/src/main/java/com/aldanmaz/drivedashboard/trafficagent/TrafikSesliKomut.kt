package com.aldanmaz.drivedashboard.trafficagent

import java.util.Locale

sealed interface TrafikSesliKomut {
    data object Geri : TrafikSesliKomut
    data object AnaEkran : TrafikSesliKomut
    data object Ayarlar : TrafikSesliKomut
    data object AjanBaslat : TrafikSesliKomut
    data object AjanDurdur : TrafikSesliKomut
    data object SesliUyariAc : TrafikSesliKomut
    data object SesliUyariKapat : TrafikSesliKomut
    data object TrafikDurumu : TrafikSesliKomut
    data class Tara(val kilometre: Int) : TrafikSesliKomut
    data object HedefSor : TrafikSesliKomut
    data object HedefSil : TrafikSesliKomut
    data class HedefAyarla(val hedefMetni: String) : TrafikSesliKomut
    data object Yardim : TrafikSesliKomut
    data object Bilinmiyor : TrafikSesliKomut
}

object TrafikSesliKomutCozumleyici {

    private val tr = Locale("tr", "TR")

    fun cozumle(hamKomut: String): TrafikSesliKomut {
        val komut = hamKomut.trim().lowercase(tr)
        if (komut.isBlank()) return TrafikSesliKomut.Bilinmiyor

        if (komut == "geri" || komut.contains("geri dön") || komut.contains("geri don")) {
            return TrafikSesliKomut.Geri
        }
        if (komut.contains("ana ekran") || komut == "ana sayfa") {
            return TrafikSesliKomut.AnaEkran
        }
        if (komut.contains("ayar")) return TrafikSesliKomut.Ayarlar

        if (komut.contains("sesli uyar") &&
            (komut.contains("kapat") || komut.contains("sessiz"))) {
            return TrafikSesliKomut.SesliUyariKapat
        }
        if (komut.contains("sesli uyar") &&
            (komut.contains("aç") || komut.contains("ac") || komut.contains("başlat") || komut.contains("baslat"))) {
            return TrafikSesliKomut.SesliUyariAc
        }

        if ((komut.contains("ajan") || komut.contains("trafik")) &&
            (komut.contains("durdur") || komut.contains("kapat"))) {
            return TrafikSesliKomut.AjanDurdur
        }
        if ((komut.contains("ajan") || komut.contains("trafik")) &&
            (komut.contains("başlat") || komut.contains("baslat") || komut.contains("aç") || komut.contains("ac"))) {
            return TrafikSesliKomut.AjanBaslat
        }

        if (komut.contains("trafik durumu") ||
            komut.contains("trafik nasıl") || komut.contains("trafik nasil") ||
            komut.contains("durumu söyle") || komut.contains("durumu soyle")) {
            return TrafikSesliKomut.TrafikDurumu
        }

        if (komut.contains("tara") || komut.contains("kontrol et")) {
            val km = when {
                Regex("(^|\\D)5(\\D|$)").containsMatchIn(komut) || komut.contains("beş") || komut.contains("bes") -> 5
                Regex("(^|\\D)10(\\D|$)").containsMatchIn(komut) || komut.contains("on kilomet") -> 10
                Regex("(^|\\D)20(\\D|$)").containsMatchIn(komut) || komut.contains("yirmi") -> 20
                else -> 20
            }
            return TrafikSesliKomut.Tara(km)
        }

        if ((komut.contains("hedef") || komut.contains("yolculuk")) &&
            (komut.contains("sil") || komut.contains("iptal") || komut.contains("kaldır") || komut.contains("kaldir"))) {
            return TrafikSesliKomut.HedefSil
        }

        if (komut == "hedef belirle" || komut == "hedef seç" || komut == "hedef sec" ||
            komut.contains("hedefi değiştir") || komut.contains("hedefi degistir") ||
            komut.contains("yeni hedef")) {
            return TrafikSesliKomut.HedefSor
        }

        hedefMetniniAyikla(komut)?.let { hedef ->
            if (hedef.length >= 2) return TrafikSesliKomut.HedefAyarla(hedef)
        }

        if (komut.contains("komutları") || komut.contains("komutlari") ||
            komut.contains("yardım") || komut.contains("yardim") || komut == "ne diyebilirim") {
            return TrafikSesliKomut.Yardim
        }

        return TrafikSesliKomut.Bilinmiyor
    }

    private fun hedefMetniniAyikla(komut: String): String? {
        Regex("^(?:trafik\\s+ajanı|trafik\\s+ajani|trafik)\\s+hedef(?:i|ini)?\\s+(.+)$")
            .matchEntire(komut)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val kaliplar = listOf(
            Regex("^(.+?)(?:'?(?:yı|yi|yu|yü|ı|i|u|ü))?\\s+hedef\\s+(?:yap|olarak\\s+ayarla)$"),
            Regex("^(.+?)(?:'?(?:ya|ye|a|e))?\\s+(?:git|gidelim)$"),
            Regex("^(?:beni\\s+)?(.+?)(?:'?(?:ya|ye|a|e))?\\s+götür$"),
            Regex("^(?:beni\\s+)?(.+?)(?:'?(?:ya|ye|a|e))?\\s+gotur$")
        )

        for (kalip in kaliplar) {
            val eslesme = kalip.matchEntire(komut) ?: continue
            val hedef = eslesme.groupValues.getOrNull(1)?.trim().orEmpty()
            if (hedef.isNotBlank()) return hedef
        }
        return null
    }
}
