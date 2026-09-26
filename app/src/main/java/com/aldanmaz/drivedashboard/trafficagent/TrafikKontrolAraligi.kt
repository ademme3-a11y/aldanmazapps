package com.aldanmaz.drivedashboard.trafficagent

object TrafikKontrolAraligi {

    private const val BIR_DAKIKA =
        60_000L

    private const val KONTROL_DAKIKA =
        2L

    fun hesapla(
        @Suppress("UNUSED_PARAMETER")
        hizKmSaat: Float
    ): Long {
        return KONTROL_DAKIKA *
                BIR_DAKIKA
    }

    fun aciklama(
        @Suppress("UNUSED_PARAMETER")
        hizKmSaat: Float
    ): String {
        return "Trafik her 2 kilometrede " +
                "veya 2 dakikada bir kontrol edilecek"
    }
}
