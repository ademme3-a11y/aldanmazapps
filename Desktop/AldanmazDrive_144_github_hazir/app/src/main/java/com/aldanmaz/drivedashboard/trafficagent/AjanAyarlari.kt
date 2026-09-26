package com.aldanmaz.drivedashboard.trafficagent

import android.content.Context
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType

object AjanAyarlari {

    private const val DOSYA_ADI =
        "trafik_ajani_ayarlari"

    /**
     * 91: Trafik Ajanı'nda varsayılan ses yalnız Gemini'dir.
     * Eski TTS + MP3 motoru ancak kullanıcı bu sayfadaki anahtarı açarsa çalışır.
     * Yeni anahtar adı eski 'sesli_uyari_acik' değerinden bilinçli olarak ayrıdır;
     * böylece 91'e ilk geçişte eski sesler otomatik olarak kapalı gelir.
     */
    private const val ESKI_TTS_MP3_ANAHTARI =
        "legacy_tts_mp3_enabled_v91"

    fun sesliUyariAcikMi(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                ESKI_TTS_MP3_ANAHTARI,
                false
            )
    }

    fun sesliUyariyiAyarla(
        context: Context,
        acik: Boolean
    ) {
        context
            .getSharedPreferences(
                DOSYA_ADI,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                ESKI_TTS_MP3_ANAHTARI,
                acik
            )
            .apply()

        val central = CentralVoiceAlertManager.getInstance(context)
        central.setTypeEnabled(VoiceAlertType.TRAFFIC, acik)
        if (!acik) {
            central.stopType(VoiceAlertType.TRAFFIC)
            TrafikSeslendirici.stopActivePlayback()
        }
    }
}
