package com.aldanmaz.drivedashboard.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import java.util.Locale

/**
 * 96: İnternet yokken yalnız kullanıcının Gemini ikonuna dokunmasıyla bir kez çalışan
 * yerel komut yedeği. Arka planda/wake-word dinlemesi yapmaz.
 */
object OfflineGeminiCommandController {

    fun hasUsableInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Araç tablet klonlarında Android'in VALIDATED işareti her zaman güvenilir olmayabilir.
        // Aktif ağda INTERNET yeteneği varsa Gemini Live'ı denemek önceliklidir.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun listenOnce(context: Context) {
        Toast.makeText(
            context,
            "İnternet yok • Gemini için manuel giriş kullanın.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dispatch(context: Context, spoken: String) {
        val text = spoken.lowercase(Locale("tr", "TR")).trim()
        val action = when {
            text.contains("ana ekran") || text.contains("ana sayfa") -> "open_drive"
            text == "geri" || text.contains("geri git") || text.contains("geri dön") || text.contains("geri don") -> "go_back"
            text.contains("yakıt") || text.contains("yakit") || text.contains("depo") -> "open_fuel"
            text.contains("hava") -> "open_weather"
            text.contains("namaz") -> "open_prayer"
            text.contains("obd") -> "open_obd"
            text.contains("trafik") && listOf("kapat", "durdur", "bitir").any(text::contains) -> "traffic_agent_off"
            text.contains("trafik") && listOf("aç", "ac", "başlat", "baslat").any(text::contains) -> "traffic_agent_on"
            text.contains("pusula") && listOf("kapat", "gizle").any(text::contains) -> "compass_off"
            text.contains("pusula") -> "compass_on"
            text.contains("hız kadran") || text.contains("hiz kadran") || text.contains("hız ekran") || text.contains("hiz ekran") -> "show_speedometer"
            text.contains("ayar") -> "open_settings"
            text.contains("müzik") || text.contains("muzik") -> if (listOf("kapat", "durdur").any(text::contains)) "stop_music" else "open_music"
            text.contains("sesi kapat") || text.contains("sessize") -> "volume_mute"
            text.contains("sesi aç") || text.contains("sesi ac") -> "volume_unmute"
            text.contains("sesi artır") || text.contains("sesi arttır") || text.contains("sesi artir") -> "volume_up"
            text.contains("sesi azalt") -> "volume_down"
            else -> null
        }
        if (action == null) {
            Toast.makeText(context, "İnternet yok • Bu komut çevrimdışı desteklenmiyor.", Toast.LENGTH_SHORT).show()
            return
        }
        when (val result = GeminiActionBridge.request(action, null)) {
            is GeminiDispatchResult.Accepted -> Toast.makeText(context, "Yerel komut uygulandı.", Toast.LENGTH_SHORT).show()
            is GeminiDispatchResult.Rejected -> Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(context, "Yerel komut uygulanamadı.", Toast.LENGTH_SHORT).show()
        }
    }
}
