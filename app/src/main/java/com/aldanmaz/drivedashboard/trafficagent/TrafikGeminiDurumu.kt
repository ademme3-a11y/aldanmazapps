package com.aldanmaz.drivedashboard.trafficagent

import android.content.Context
import kotlin.math.roundToInt

data class TrafikGeminiOlayi(
    val hedefAdi: String,
    val yolAdi: String,
    val konumMetni: String,
    val uzaklikMetre: Int,
    val durumMetni: String,
    val zaman: Long,
)

/** Gemini'nin son trafik uyarısının yerini sonradan da açıklayabilmesi için küçük kalıcı depo. */
object TrafikGeminiDurumu {
    private const val PREFS = "trafik_gemini_son_olay"

    fun kaydet(context: Context, hedefAdi: String, olay: RotaTrafikOlayi) {
        val yol = olay.yolAdi?.trim().orEmpty().ifBlank { "İlerideki yol" }
        val konum = if (hedefAdi.isNotBlank()) "$yol • $hedefAdi istikameti" else yol
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("hedef", hedefAdi)
            .putString("yol", yol)
            .putString("konum", konum)
            .putInt("uzaklik", olay.baslangicMetre.roundToInt().coerceAtLeast(0))
            .putString("durum", olay.kategoriMetni())
            .putLong("zaman", System.currentTimeMillis())
            .apply()
    }

    fun temizle(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun al(context: Context): TrafikGeminiOlayi? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zaman = p.getLong("zaman", 0L)
        if (zaman <= 0L) return null
        // Çok eski trafik olayını güncelmiş gibi söyleme.
        if (System.currentTimeMillis() - zaman > 45L * 60L * 1000L) return null
        return TrafikGeminiOlayi(
            hedefAdi = p.getString("hedef", "").orEmpty(),
            yolAdi = p.getString("yol", "").orEmpty(),
            konumMetni = p.getString("konum", "").orEmpty(),
            uzaklikMetre = p.getInt("uzaklik", 0),
            durumMetni = p.getString("durum", "Trafik sorunu").orEmpty(),
            zaman = zaman,
        )
    }
}
