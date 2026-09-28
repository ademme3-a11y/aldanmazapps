package com.aldanmaz.drivedashboard.data.ai

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Çevrimdışı ses tanıma bu cihazda kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var finished = false
        fun close() {
            if (finished) return
            finished = true
            runCatching { recognizer.destroy() }
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(context, "İnternet yok • Yerel komutu söyleyin", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) dispatch(context, text)
                close()
            }
            override fun onError(error: Int) {
                Toast.makeText(context, "Yerel ses komutu alınamadı.", Toast.LENGTH_SHORT).show()
                close()
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                close()
                Toast.makeText(context, "Yerel ses tanıma başlatılamadı.", Toast.LENGTH_SHORT).show()
            }
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
