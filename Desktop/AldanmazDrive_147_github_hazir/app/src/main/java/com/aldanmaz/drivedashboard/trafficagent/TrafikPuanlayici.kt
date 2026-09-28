package com.aldanmaz.drivedashboard.trafficagent

data class TrafikPuanBilgisi(
    val puan: Int,
    val durum: String
)

object TrafikPuanlayici {

    fun rotaPuani(
        sonuc: RotaTrafikSonucu
    ): TrafikPuanBilgisi {

        val surePuani =
            sureFarkindanPuanHesapla(
                normalSureSaniye =
                    sonuc.normalSureSaniye,

                tahminiSureSaniye =
                    sonuc.tahminiSureSaniye,

                toplamGecikmeSaniye =
                    sonuc.toplamGecikmeSaniye
            )

        val olayPuani =
            olaylardanPuanHesapla(
                sonuc.olaylar
            )

        return puanBilgisiOlustur(
            maxOf(
                surePuani,
                olayPuani
            )
        )
    }

    fun onumuzdeki20KmPuani(
        sonuc: RotaTrafikSonucu
    ): TrafikPuanBilgisi {

        val olaylar =
            sonuc.bolgedekiOlaylar(
                baslangicMetre = 0.0,
                bitisMetre = 20_000.0
            )

        val olayPuani =
            olaylardanPuanHesapla(
                olaylar
            )

        /*
         * Hedef 20 kilometreden daha yakınsa rota
         * genelindeki süre gecikmesini de hesaba kat.
         */
        val puan =
            if (
                sonuc.toplamMesafeMetre <= 20_000
            ) {
                maxOf(
                    olayPuani,
                    sureFarkindanPuanHesapla(
                        normalSureSaniye =
                            sonuc.normalSureSaniye,

                        tahminiSureSaniye =
                            sonuc.tahminiSureSaniye,

                        toplamGecikmeSaniye =
                            sonuc.toplamGecikmeSaniye
                    )
                )
            } else {
                olayPuani
            }

        return puanBilgisiOlustur(puan)
    }

    fun bolgePuani(
        sonuc: RotaTrafikSonucu,
        baslangicKm: Int,
        bitisKm: Int
    ): TrafikPuanBilgisi {

        val olaylar =
            sonuc.bolgedekiOlaylar(
                baslangicMetre =
                    baslangicKm * 1000.0,

                bitisMetre =
                    bitisKm * 1000.0
            )

        return puanBilgisiOlustur(
            olaylardanPuanHesapla(
                olaylar
            )
        )
    }

    fun puanBilgisiOlustur(
        puan: Int
    ): TrafikPuanBilgisi {

        val guvenliPuan =
            puan.coerceIn(0, 10)

        val durum =
            when (guvenliPuan) {
                0 ->
                    "Trafik yok"

                1, 2 ->
                    "Trafik akıcı"

                3, 4 ->
                    "Yer yer yavaşlama"

                5 ->
                    "Orta yoğunluk"

                6 ->
                    "Yoğun trafik"

                7 ->
                    "Ciddi yoğunluk"

                8 ->
                    "Uzun trafik kuyruğu"

                9 ->
                    "Trafik neredeyse durmuş"

                10 ->
                    "Trafik durmuş veya yol kapalı"

                else ->
                    "Bilinmiyor"
            }

        return TrafikPuanBilgisi(
            puan = guvenliPuan,
            durum = durum
        )
    }

    private fun sureFarkindanPuanHesapla(
        normalSureSaniye: Int,
        tahminiSureSaniye: Int,
        toplamGecikmeSaniye: Int
    ): Int {

        if (normalSureSaniye <= 0) {
            return 0
        }

        val hesaplananGecikme =
            (
                    tahminiSureSaniye -
                            normalSureSaniye
                    ).coerceAtLeast(0)

        val gecikmeSaniye =
            maxOf(
                hesaplananGecikme,
                toplamGecikmeSaniye
            )

        val gecikmeYuzdesi =
            gecikmeSaniye.toDouble() /
                    normalSureSaniye.toDouble() *
                    100.0

        return when {
            gecikmeYuzdesi <= 2.0 -> 0
            gecikmeYuzdesi <= 5.0 -> 1
            gecikmeYuzdesi <= 10.0 -> 2
            gecikmeYuzdesi <= 20.0 -> 3
            gecikmeYuzdesi <= 30.0 -> 4
            gecikmeYuzdesi <= 45.0 -> 5
            gecikmeYuzdesi <= 60.0 -> 6
            gecikmeYuzdesi <= 80.0 -> 7
            gecikmeYuzdesi <= 110.0 -> 8
            gecikmeYuzdesi <= 150.0 -> 9
            else -> 10
        }
    }

    private fun olaylardanPuanHesapla(
        olaylar: List<RotaTrafikOlayi>
    ): Int {

        if (olaylar.isEmpty()) {
            return 0
        }

        return olaylar.maxOf { olay ->
            tekOlayPuani(olay)
        }
    }

    private fun tekOlayPuani(
        olay: RotaTrafikOlayi
    ): Int {

        if (
            olay.kategori.equals(
                "ROAD_CLOSURE",
                ignoreCase = true
            ) ||
            olay.gecikmeBuyuklugu >= 4
        ) {
            return 10
        }

        var puan =
            when (olay.gecikmeBuyuklugu) {
                1 -> 3
                2 -> 5
                3 -> 8
                else -> 2
            }

        puan =
            maxOf(
                puan,
                gecikmedenPuanHesapla(
                    olay.gecikmeSaniye
                )
            )

        olay.etkiliHizKmSaat?.let { hiz ->

            val hizPuani =
                when {
                    hiz <= 5 -> 10
                    hiz <= 10 -> 9
                    hiz <= 20 -> 8
                    hiz <= 30 -> 7
                    hiz <= 40 -> 6
                    hiz <= 55 -> 4
                    else -> 2
                }

            puan =
                maxOf(
                    puan,
                    hizPuani
                )
        }

        if (
            olay.kategori.equals(
                "ROAD_WORK",
                ignoreCase = true
            )
        ) {
            puan = maxOf(puan, 4)
        }

        return puan.coerceIn(0, 10)
    }

    private fun gecikmedenPuanHesapla(
        gecikmeSaniye: Int
    ): Int {

        return when {
            gecikmeSaniye < 60 -> 0
            gecikmeSaniye < 180 -> 3
            gecikmeSaniye < 300 -> 4
            gecikmeSaniye < 600 -> 6
            gecikmeSaniye < 900 -> 7
            gecikmeSaniye < 1_800 -> 8
            else -> 9
        }
    }
}

