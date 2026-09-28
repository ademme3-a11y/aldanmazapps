package com.aldanmaz.drivedashboard.trafficagent

import android.content.Context

object HedefDeposu {

    private const val DOSYA_ADI =
        "trafik_ajani_hedef"

    private const val ANAHTAR_HEDEF_VAR =
        "hedef_var"

    private const val ANAHTAR_AD =
        "hedef_ad"

    private const val ANAHTAR_ADRES =
        "hedef_adres"

    private const val ANAHTAR_ENLEM =
        "hedef_enlem"

    private const val ANAHTAR_BOYLAM =
        "hedef_boylam"

    fun hedefiKaydet(
        context: Context,
        hedef: HedefAramaSonucu
    ) {

        val tercihler =
            context.getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )

        tercihler.edit()
            .putBoolean(
                ANAHTAR_HEDEF_VAR,
                true
            )
            .putString(
                ANAHTAR_AD,
                hedef.ad
            )
            .putString(
                ANAHTAR_ADRES,
                hedef.adres
            )
            .putLong(
                ANAHTAR_ENLEM,
                hedef.enlem.toBits()
            )
            .putLong(
                ANAHTAR_BOYLAM,
                hedef.boylam.toBits()
            )
            .apply()
    }

    fun kayitliHedefiAl(
        context: Context
    ): HedefAramaSonucu? {

        val tercihler =
            context.getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )

        val hedefVar =
            tercihler.getBoolean(
                ANAHTAR_HEDEF_VAR,
                false
            )

        if (!hedefVar) {
            return null
        }

        val ad =
            tercihler.getString(
                ANAHTAR_AD,
                ""
            ).orEmpty()

        val adres =
            tercihler.getString(
                ANAHTAR_ADRES,
                ""
            ).orEmpty()

        val enlemBitleri =
            tercihler.getLong(
                ANAHTAR_ENLEM,
                Double.NaN.toBits()
            )

        val boylamBitleri =
            tercihler.getLong(
                ANAHTAR_BOYLAM,
                Double.NaN.toBits()
            )

        val enlem =
            Double.fromBits(enlemBitleri)

        val boylam =
            Double.fromBits(boylamBitleri)

        if (
            enlem.isNaN() ||
            boylam.isNaN()
        ) {
            return null
        }

        return HedefAramaSonucu(
            ad =
                ad.ifBlank {
                    "Kayıtlı hedef"
                },

            adres = adres,
            enlem = enlem,
            boylam = boylam,
            uzaklikMetre = null
        )
    }

    fun hedefiSil(
        context: Context
    ) {

        val tercihler =
            context.getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )

        tercihler.edit()
            .clear()
            .apply()
    }

    fun hedefVarMi(
        context: Context
    ): Boolean {

        return kayitliHedefiAl(
            context
        ) != null
    }
}