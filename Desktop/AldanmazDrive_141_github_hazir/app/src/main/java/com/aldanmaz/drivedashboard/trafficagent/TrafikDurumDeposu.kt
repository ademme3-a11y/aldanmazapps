package com.aldanmaz.drivedashboard.trafficagent

import android.content.Context

data class KayitliTrafikDurumu(
    val hedefAdi: String,
    val tumRotaPuani: Int,
    val ilk20KmPuani: Int,
    val ilk5KmPuani: Int,
    val besOnKmPuani: Int,
    val onYirmiKmPuani: Int,
    val sonKontrolZamani: Long,
    val sonrakiKontrolZamani: Long
)

object TrafikDurumDeposu {

    private const val DOSYA_ADI =
        "trafik_ajani_canli_durum"

    private const val DURUM_VAR =
        "durum_var"

    private const val SERVIS_AKTIF =
        "servis_aktif"

    private const val HEDEF_ADI =
        "hedef_adi"

    private const val TUM_ROTA_PUANI =
        "tum_rota_puani"

    private const val ILK_20_KM_PUANI =
        "ilk_20_km_puani"

    private const val ILK_5_KM_PUANI =
        "ilk_5_km_puani"

    private const val BES_ON_KM_PUANI =
        "bes_on_km_puani"

    private const val ON_YIRMI_KM_PUANI =
        "on_yirmi_km_puani"

    private const val SON_KONTROL_ZAMANI =
        "son_kontrol_zamani"

    private const val SONRAKI_KONTROL_ZAMANI =
        "sonraki_kontrol_zamani"

    fun trafikSonucunuKaydet(
        context: Context,
        hedefAdi: String,
        sonuc: RotaTrafikSonucu,
        kontrolAraligiMillis: Long
    ) {
        val simdi =
            System.currentTimeMillis()

        val tumRota =
            TrafikPuanlayici
                .rotaPuani(sonuc)

        val ilk20Km =
            TrafikPuanlayici
                .onumuzdeki20KmPuani(sonuc)

        val ilk5Km =
            TrafikPuanlayici
                .bolgePuani(
                    sonuc = sonuc,
                    baslangicKm = 0,
                    bitisKm = 5
                )

        val besOnKm =
            TrafikPuanlayici
                .bolgePuani(
                    sonuc = sonuc,
                    baslangicKm = 5,
                    bitisKm = 10
                )

        val onYirmiKm =
            TrafikPuanlayici
                .bolgePuani(
                    sonuc = sonuc,
                    baslangicKm = 10,
                    bitisKm = 20
                )

        context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                DURUM_VAR,
                true
            )
            .putString(
                HEDEF_ADI,
                hedefAdi
            )
            .putInt(
                TUM_ROTA_PUANI,
                tumRota.puan
            )
            .putInt(
                ILK_20_KM_PUANI,
                ilk20Km.puan
            )
            .putInt(
                ILK_5_KM_PUANI,
                ilk5Km.puan
            )
            .putInt(
                BES_ON_KM_PUANI,
                besOnKm.puan
            )
            .putInt(
                ON_YIRMI_KM_PUANI,
                onYirmiKm.puan
            )
            .putLong(
                SON_KONTROL_ZAMANI,
                simdi
            )
            .putLong(
                SONRAKI_KONTROL_ZAMANI,
                simdi + kontrolAraligiMillis
            )
            .apply()
    }

    fun trafikDurumunuAl(
        context: Context
    ): KayitliTrafikDurumu? {

        val tercihler =
            context.getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )

        if (
            !tercihler.getBoolean(
                DURUM_VAR,
                false
            )
        ) {
            return null
        }

        return KayitliTrafikDurumu(
            hedefAdi =
                tercihler.getString(
                    HEDEF_ADI,
                    ""
                ) ?: "",

            tumRotaPuani =
                tercihler.getInt(
                    TUM_ROTA_PUANI,
                    0
                ),

            ilk20KmPuani =
                tercihler.getInt(
                    ILK_20_KM_PUANI,
                    0
                ),

            ilk5KmPuani =
                tercihler.getInt(
                    ILK_5_KM_PUANI,
                    0
                ),

            besOnKmPuani =
                tercihler.getInt(
                    BES_ON_KM_PUANI,
                    0
                ),

            onYirmiKmPuani =
                tercihler.getInt(
                    ON_YIRMI_KM_PUANI,
                    0
                ),

            sonKontrolZamani =
                tercihler.getLong(
                    SON_KONTROL_ZAMANI,
                    0L
                ),

            sonrakiKontrolZamani =
                tercihler.getLong(
                    SONRAKI_KONTROL_ZAMANI,
                    0L
                )
        )
    }

    fun servisAktifliginiKaydet(
        context: Context,
        aktif: Boolean
    ) {
        context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                SERVIS_AKTIF,
                aktif
            )
            .apply()
    }

    fun servisAktifMi(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                SERVIS_AKTIF,
                false
            )
    }

    fun trafikDurumunuTemizle(
        context: Context
    ) {
        context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(DURUM_VAR)
            .apply()
    }
}
