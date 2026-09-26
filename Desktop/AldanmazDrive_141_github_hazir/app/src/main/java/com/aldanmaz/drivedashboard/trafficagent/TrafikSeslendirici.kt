package com.aldanmaz.drivedashboard.trafficagent

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import android.speech.tts.UtteranceProgressListener
import com.aldanmaz.drivedashboard.R
import java.util.Locale
import java.lang.ref.WeakReference

enum class TrafikKayitliSes(val resId: Int) {
    AGENT_READY(R.raw.traffic_agent_ready),
    AGENT_STARTED(R.raw.traffic_agent_started),
    AGENT_STOPPED(R.raw.traffic_agent_stopped),
    TARGET_PROMPT(R.raw.traffic_target_prompt),
    TARGET_FOUND(R.raw.traffic_target_found),
    TARGET_CONFIRM(R.raw.traffic_target_confirm),
    TARGET_SAVED(R.raw.traffic_target_saved),
    TARGET_CANCELLED(R.raw.traffic_target_cancelled),
    TARGET_NOT_FOUND(R.raw.traffic_target_not_found),
    SCAN_STARTED(R.raw.traffic_scan_started),
    STATUS_INTRO(R.raw.traffic_status_intro),
    VOICE_ON(R.raw.traffic_voice_on),
    VOICE_OFF(R.raw.traffic_voice_off),
    ACTION_CANCELLED(R.raw.traffic_action_cancelled),
    COMMAND_UNKNOWN(R.raw.voice_command_unknown)
}

/**
 * Trafik Ajanı ses motoru.
 * Sabit cümleleri res/raw içindeki gerçek insan seslerinden, yalnızca hedef adı,
 * yol adı, kilometre ve trafik puanı gibi değişken bilgileri TTS ile okur.
 */
class TrafikSeslendirici(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val anaIsParcacigi = Handler(Looper.getMainLooper())

    private val textToSpeech = TextToSpeech(appContext, this)
    private var mediaPlayer: MediaPlayer? = null

    private var hazir = false
    private var bekleyenMetin: String? = null
    private var bekleyenTamamlaninca: (() -> Unit)? = null
    private var etkinTamamlaninca: (() -> Unit)? = null
    private var etkinTtsKimligi: String? = null
    private var oynatmaNesli = 0L

    init {
        activeInstance = WeakReference(this)
    }

    override fun onInit(durum: Int) {
        if (durum != TextToSpeech.SUCCESS) {
            hazir = false
            return
        }

        val dilSonucu = textToSpeech.setLanguage(Locale("tr", "TR"))
        hazir = dilSonucu != TextToSpeech.LANG_MISSING_DATA &&
                dilSonucu != TextToSpeech.LANG_NOT_SUPPORTED

        textToSpeech.setSpeechRate(0.95f)
        textToSpeech.setPitch(1.0f)

        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    tamamlanmaIsleminiCalistir(utteranceId)
                }

                @Deprecated("Eski Android sürümleri için")
                override fun onError(utteranceId: String?) {
                    tamamlanmaIsleminiCalistir(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    tamamlanmaIsleminiCalistir(utteranceId)
                }
            }
        )

        if (hazir) {
            val metin = bekleyenMetin
            val tamamlaninca = bekleyenTamamlaninca
            bekleyenMetin = null
            bekleyenTamamlaninca = null
            if (metin != null) {
                konus(metin, tamamlaninca)
            }
        }
    }

    /** Sadece değişken bilgi için TTS kullanır. */
    fun konus(
        metin: String,
        tamamlaninca: (() -> Unit)? = null
    ) {
        if (!AjanAyarlari.sesliUyariAcikMi(appContext)) {
            tamamlaninca?.invoke()
            return
        }
        val centralVoice = CentralVoiceAlertManager.getInstance(appContext)
        if (!centralVoice.isMasterEnabled() || centralVoice.isExternallySuppressed() || externalSpeechActive) {
            tamamlaninca?.invoke()
            return
        }
        if (metin.isBlank()) {
            tamamlaninca?.invoke()
            return
        }

        if (!hazir) {
            bekleyenMetin = metin
            bekleyenTamamlaninca = tamamlaninca
            return
        }

        val nesil = ++oynatmaNesli
        mevcutOynatmayiDurdur(ttsDahil = true)
        etkinTamamlaninca = {
            if (nesil == oynatmaNesli) tamamlaninca?.invoke()
        }

        val utteranceId = "trafik_tts_${nesil}_${System.currentTimeMillis()}"
        etkinTtsKimligi = utteranceId
        textToSpeech.speak(
            metin,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    /**
     * Sabit cümleyi gerçek insan sesiyle çalar. İstenirse kayıt bittikten sonra yalnızca
     * değişken kısım TTS ile okunur ve en son callback çalışır.
     */
    fun kayitliKonus(
        ses: TrafikKayitliSes,
        dinamikMetinSonra: String? = null,
        tamamlaninca: (() -> Unit)? = null
    ) {
        if (!AjanAyarlari.sesliUyariAcikMi(appContext)) {
            tamamlaninca?.invoke()
            return
        }
        val centralVoice = CentralVoiceAlertManager.getInstance(appContext)
        if (!centralVoice.isMasterEnabled() || centralVoice.isExternallySuppressed() || externalSpeechActive) {
            tamamlaninca?.invoke()
            return
        }
        val nesil = ++oynatmaNesli
        mevcutOynatmayiDurdur(ttsDahil = true)

        val player = runCatching {
            MediaPlayer.create(appContext, ses.resId)
        }.getOrNull()

        if (player == null) {
            val dinamik = dinamikMetinSonra?.trim().orEmpty()
            if (dinamik.isNotBlank()) {
                konus(dinamik, tamamlaninca)
            } else {
                tamamlaninca?.invoke()
            }
            return
        }

        mediaPlayer = player
        player.setOnCompletionListener { tamamlanan ->
            runCatching { tamamlanan.release() }
            if (mediaPlayer === tamamlanan) mediaPlayer = null
            if (nesil != oynatmaNesli) return@setOnCompletionListener

            val dinamik = dinamikMetinSonra?.trim().orEmpty()
            if (dinamik.isNotBlank()) {
                konus(dinamik, tamamlaninca)
            } else {
                anaIsParcacigi.post { tamamlaninca?.invoke() }
            }
        }
        player.setOnErrorListener { hatali, _, _ ->
            runCatching { hatali.release() }
            if (mediaPlayer === hatali) mediaPlayer = null
            if (nesil == oynatmaNesli) {
                val dinamik = dinamikMetinSonra?.trim().orEmpty()
                if (dinamik.isNotBlank()) konus(dinamik, tamamlaninca)
                else anaIsParcacigi.post { tamamlaninca?.invoke() }
            }
            true
        }

        runCatching { player.start() }
            .onFailure {
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                val dinamik = dinamikMetinSonra?.trim().orEmpty()
                if (dinamik.isNotBlank()) konus(dinamik, tamamlaninca)
                else tamamlaninca?.invoke()
            }
    }

    private fun tamamlanmaIsleminiCalistir(utteranceId: String?) {
        if (utteranceId == null || utteranceId != etkinTtsKimligi) return
        etkinTtsKimligi = null
        val islem = etkinTamamlaninca
        etkinTamamlaninca = null
        if (islem != null) anaIsParcacigi.post { islem() }
    }

    private fun mevcutOynatmayiDurdur(ttsDahil: Boolean) {
        etkinTamamlaninca = null
        etkinTtsKimligi = null
        if (ttsDahil) runCatching { textToSpeech.stop() }
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    fun sustur() {
        oynatmaNesli += 1L
        mevcutOynatmayiDurdur(ttsDahil = true)
    }

    fun kapat() {
        oynatmaNesli += 1L
        bekleyenMetin = null
        bekleyenTamamlaninca = null
        mevcutOynatmayiDurdur(ttsDahil = true)
        textToSpeech.shutdown()
        hazir = false
        if (activeInstance?.get() === this) activeInstance = null
    }

    companion object {
        @Volatile private var externalSpeechActive = false
        @Volatile private var activeInstance: WeakReference<TrafikSeslendirici>? = null

        /** Gemini ses çıkışı başladığında Trafik Ajanı'nın ayrı MP3/TTS motorunu da susturur. */
        fun setExternalSpeechActive(active: Boolean) {
            externalSpeechActive = active
            if (active) activeInstance?.get()?.sustur()
        }

        fun stopActivePlayback() {
            activeInstance?.get()?.sustur()
        }
    }
}
