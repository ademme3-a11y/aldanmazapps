package com.aldanmaz.drivedashboard.data.alert

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aldanmaz.drivedashboard.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Sabit uyarılar gerçek insan sesi (res/raw MP3), değişken bilgiler ise yalnızca gerektiğinde TTS.
 * Böylece ör. "Hız sınırı aşıldı" kayıtlı insan sesiyle, "90 kilometre" gibi değişken kısım TTS ile okunur.
 */
enum class RecordedVoiceClip(val resId: Int) {
    WELCOME_MEHMET(R.raw.welcome_mehmet),
    WELCOME_NURDAN(R.raw.welcome_nurdan),
    DESTINATION_REACHED(R.raw.destination_reached),
    DPF_CRITICAL(R.raw.dpf_critical),
    DPF_REGEN_SUITABLE(R.raw.dpf_regen_suitable),
    DPF_WARNING(R.raw.dpf_warning),
    DRIVE_START_SAFETY(R.raw.drive_start_safety),
    FUEL_CRITICAL(R.raw.fuel_critical),
    FUEL_LOW(R.raw.fuel_low),
    GPS_LOST(R.raw.gps_lost),
    GPS_RESTORED(R.raw.gps_restored),
    HOME_ROUTE_STARTED(R.raw.home_route_started),
    INTERNET_LOST(R.raw.internet_lost),
    INTERNET_RESTORED(R.raw.internet_restored),
    OBD_CONNECTED(R.raw.obd_connected),
    OBD_DISCONNECTED(R.raw.obd_disconnected),
    OBD_WARNING(R.raw.obd_warning),
    PRAYER_TIME(R.raw.prayer_time),
    PRAYER_TIME_APPROACHING(R.raw.prayer_time_approaching),
    REST_REMINDER(R.raw.rest_reminder),
    ROAD_CLOSED_WARNING(R.raw.road_closed_warning),
    ROAD_WORK_WARNING(R.raw.road_work_warning),
    ROUTE_READY(R.raw.route_ready),
    SAFETY_LOCK(R.raw.safety_lock),
    SPEED_LIMIT_20(R.raw.speed_limit_20),
    SPEED_LIMIT_30(R.raw.speed_limit_30),
    SPEED_LIMIT_40(R.raw.speed_limit_40),
    SPEED_LIMIT_50(R.raw.speed_limit_50),
    SPEED_LIMIT_60(R.raw.speed_limit_60),
    SPEED_LIMIT_70(R.raw.speed_limit_70),
    SPEED_LIMIT_80(R.raw.speed_limit_80),
    SPEED_LIMIT_82(R.raw.speed_limit_82),
    SPEED_LIMIT_90(R.raw.speed_limit_90),
    SPEED_LIMIT_100(R.raw.speed_limit_100),
    SPEED_LIMIT_110(R.raw.speed_limit_110),
    SPEED_LIMIT_120(R.raw.speed_limit_120),
    SPEED_WARNING(R.raw.speed_warning),
    TRAFFIC_DELAY_WARNING(R.raw.traffic_delay_warning),
    TRAFFIC_WARNING(R.raw.traffic_warning),
    TRIP_FINISHED(R.raw.trip_finished),
    TRIP_PRAYER_BISMILLAH(R.raw.trip_prayer_bismillah),
    TRIP_STARTED(R.raw.trip_started),
    VOICE_COMMAND_READY(R.raw.voice_command_ready),
    VOICE_COMMAND_UNKNOWN(R.raw.voice_command_unknown),
    COMMAND_NOT_UNDERSTOOD(R.raw.command_not_understood),
    RAMP_SLOPE_PERCENT_INTRO(R.raw.ramp_slope_percent_intro),
    DESCENT_SLOPE_PERCENT_INTRO(R.raw.descent_slope_percent_intro),
    TRAFFIC_CLEAR(R.raw.traffic_clear),
    TRAFFIC_MODERATE_INTRO(R.raw.traffic_moderate_intro),
    TRAFFIC_HEAVY_INTRO(R.raw.traffic_heavy_intro),
    TRAFFIC_JAM_INTRO(R.raw.traffic_jam_intro),
    TRAFFIC_AHEAD_INTRO(R.raw.traffic_ahead_intro),
    TRAFFIC_DELAY_INTRO(R.raw.traffic_delay_intro),
    TRAFFIC_DELAY_SUFFIX(R.raw.traffic_delay_suffix),
    ROAD_CLOSED_ALT_ROUTE(R.raw.road_closed_alt_route),
    CURRENT_SPEED_INTRO(R.raw.current_speed_intro),
    AVERAGE_SPEED_INTRO(R.raw.average_speed_intro),
    ALTITUDE_INTRO(R.raw.altitude_intro),
    DAILY_DISTANCE_INTRO(R.raw.daily_distance_intro),
    FUEL_CONSUMPTION_INTRO(R.raw.fuel_consumption_intro),
    FUEL_COST_INTRO(R.raw.fuel_cost_intro),
    PARK_DURATION_INTRO(R.raw.park_duration_intro),
    ENGINE_TEMPERATURE_INTRO(R.raw.engine_temperature_intro),
    BATTERY_VOLTAGE_INTRO(R.raw.battery_voltage_intro),
    TRAFFIC_DENSITY_INTRO(R.raw.traffic_density_intro),
    DISTANCE_REMAINING_INTRO(R.raw.distance_remaining_intro),
    NEXT_PRAYER_INTRO(R.raw.next_prayer_intro)
}

enum class VoiceAlertType { SPEED, FUEL, PRAYER, OBD, ROUTE, TRAFFIC, TRIP_START, COMMAND }

enum class VoiceAlertPriority(val level: Int) { LOW(1), NORMAL(2), HIGH(3), CRITICAL(4) }

class CentralVoiceAlertManager private constructor(context: Context) {
    private data class PendingAlert(
        val type: VoiceAlertType,
        val priority: VoiceAlertPriority,
        val clips: List<RecordedVoiceClip>,
        val dynamicText: String?,
        val tailPauseMs: Long = 0L,
        val allowDuringExternalSpeech: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("central_voice_alerts", Context.MODE_PRIVATE)
    private val lastSpokenAt = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val pendingAlerts = mutableListOf<PendingAlert>()

    @Volatile private var ttsReady = false
    @Volatile private var activePriority = 0
    @Volatile private var playbackGeneration = 0L
    @Volatile private var activeUtteranceId: String? = null
    @Volatile private var activeTailPauseMs = 0L
    @Volatile private var externalSpeechActive = false
    private var activeAlert: PendingAlert? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("tr", "TR"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                applySpeechRate()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId == activeUtteranceId) {
                            activeUtteranceId = null
                            mainHandler.post { finishCurrentAlert() }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null && utteranceId == activeUtteranceId) {
                            activeUtteranceId = null
                            mainHandler.post { finishCurrentAlert() }
                        }
                    }
                })
            }
        }
    }

    /** Tamamen değişken bir bilgi gerektiğinde kullanılır. Sabit cümlelerde bunu kullanmayın. */
    fun speak(
        type: VoiceAlertType,
        priority: VoiceAlertPriority,
        message: String,
        cooldownKey: String,
        cooldownMs: Long = 60_000L
    ) {
        if (!isMasterEnabled() || !ttsReady || message.isBlank()) return
        if (!acceptAlert(type, cooldownKey, cooldownMs)) return
        submit(PendingAlert(type, priority, emptyList(), message))
    }

    /** Tek bir sabit uyarıyı gerçek insan sesiyle çalar. */
    fun playRecorded(
        type: VoiceAlertType,
        priority: VoiceAlertPriority,
        clip: RecordedVoiceClip,
        cooldownKey: String,
        cooldownMs: Long = 60_000L,
        dynamicTextAfter: String? = null
    ) {
        playRecordedSequence(
            type = type,
            priority = priority,
            clips = listOf(clip),
            cooldownKey = cooldownKey,
            cooldownMs = cooldownMs,
            dynamicTextAfter = dynamicTextAfter
        )
    }

    /** Sürücü karşılama gibi tek olması gereken bir sesi, eski kuyruğu iptal ederek çalar. */
    fun playRecordedExclusive(
        type: VoiceAlertType,
        priority: VoiceAlertPriority,
        clip: RecordedVoiceClip
    ) {
        if (!isMasterEnabled() || !isTypeEnabled(type)) return
        mainHandler.post {
            pendingAlerts.clear()
            playbackGeneration += 1L
            activePriority = 0
            stopCurrentPlayback()
            startAlert(PendingAlert(
                type, priority, listOf(clip), null, tailPauseMs = 1_200L,
                allowDuringExternalSpeech = clip == RecordedVoiceClip.TRIP_PRAYER_BISMILLAH
            ))
        }
    }

    /**
     * Birden fazla sabit insan sesi kaydını sırayla çalar; en sonda varsa yalnızca değişken kısmı TTS okur.
     */
    fun playRecordedSequence(
        type: VoiceAlertType,
        priority: VoiceAlertPriority,
        clips: List<RecordedVoiceClip>,
        cooldownKey: String,
        cooldownMs: Long = 60_000L,
        dynamicTextAfter: String? = null
    ) {
        if (!isMasterEnabled()) return
        if (clips.isEmpty()) {
            dynamicTextAfter?.takeIf { it.isNotBlank() }?.let {
                speak(type, priority, it, cooldownKey, cooldownMs)
            }
            return
        }
        if (!acceptAlert(type, cooldownKey, cooldownMs)) return
        submit(PendingAlert(
            type, priority, clips, dynamicTextAfter,
            allowDuringExternalSpeech = clips.any { it == RecordedVoiceClip.TRIP_PRAYER_BISMILLAH }
        ))
    }

    private fun acceptAlert(
        type: VoiceAlertType,
        cooldownKey: String,
        cooldownMs: Long
    ): Boolean {
        if (!isMasterEnabled() || !isTypeEnabled(type)) return false

        val now = System.currentTimeMillis()
        if (now - (lastSpokenAt[cooldownKey] ?: 0L) < cooldownMs) return false
        lastSpokenAt[cooldownKey] = now
        return true
    }

    /** Google Haritalar benzeri küçük kuyruk: yüksek öncelik keser, diğerleri sıraya girer. */
    private fun submit(alert: PendingAlert) {
        mainHandler.post {
            if (externalSpeechActive && !alert.allowDuringExternalSpeech) {
                // 96: Gemini Live aktifken Bismillah dışındaki eski TTS/MP3 uyarıları
                // kuyruğa da alınmaz; Gemini kapandığında geçmiş uyarılar üst üste çalmasın.
                return@post
            }
            if (activePriority == 0) {
                startAlert(alert)
            } else if (alert.priority.level > activePriority) {
                stopCurrentPlayback()
                activeAlert = null
                startAlert(alert)
            } else {
                pendingAlerts += alert
                pendingAlerts.sortByDescending { it.priority.level }
                while (pendingAlerts.size > 8) pendingAlerts.removeLast()
            }
        }
    }

    private fun startAlert(alert: PendingAlert) {
        if (externalSpeechActive && !alert.allowDuringExternalSpeech) return
        playbackGeneration += 1L
        activeAlert = alert
        activePriority = alert.priority.level
        activeTailPauseMs = alert.tailPauseMs
        val generation = playbackGeneration
        if (alert.clips.isEmpty()) {
            speakDynamicInternal(alert.dynamicText.orEmpty(), alert.type, generation)
        } else {
            playClipAt(alert.type, alert.clips, 0, generation, alert.dynamicText)
        }
    }

    private fun finishCurrentAlert() {
        activeUtteranceId = null
        activeAlert = null
        val tailPause = activeTailPauseMs
        activeTailPauseMs = 0L
        if (tailPause > 0L) {
            activePriority = VoiceAlertPriority.HIGH.level
            val pauseGeneration = playbackGeneration
            mainHandler.postDelayed({
                if (playbackGeneration == pauseGeneration) {
                    activePriority = 0
                    startNextPendingAlert()
                }
            }, tailPause)
            return
        }
        activePriority = 0
        startNextPendingAlert()
    }

    private fun startNextPendingAlert() {
        if (externalSpeechActive) return
        val now = System.currentTimeMillis()
        pendingAlerts.removeAll { now - it.createdAt > 20_000L }
        val next = if (pendingAlerts.isEmpty()) null else pendingAlerts.removeAt(0)
        if (next == null) return
        startAlert(next)
    }

    private fun playClipAt(
        type: VoiceAlertType,
        clips: List<RecordedVoiceClip>,
        index: Int,
        generation: Long,
        dynamicTextAfter: String?
    ) {
        if (generation != playbackGeneration) return

        if (index >= clips.size) {
            val dynamic = dynamicTextAfter?.trim().orEmpty()
            if (!externalSpeechActive && dynamic.isNotBlank() && ttsReady) {
                speakDynamicInternal(dynamic, type, generation)
            } else {
                finishCurrentAlert()
            }
            return
        }

        // 96: Gemini Live açıkken tek kayıt istisnası Bismillah'tır.
        // Aynı sırada güvenli sürüş MP3'ü veya dinamik TTS varsa Bismillah sonrası atlanır.
        if (externalSpeechActive && clips[index] != RecordedVoiceClip.TRIP_PRAYER_BISMILLAH) {
            finishCurrentAlert()
            return
        }

        val player = runCatching {
            MediaPlayer.create(appContext, clips[index].resId)
        }.getOrNull()

        if (player == null) {
            // Bir kayıt açılamazsa diğer kayıtla devam et; sabit cümleyi TTS'ye düşürme.
            playClipAt(type, clips, index + 1, generation, dynamicTextAfter)
            return
        }

        mediaPlayer = player
        player.setVolume(volume(), volume())
        player.setOnCompletionListener { completed ->
            runCatching { completed.release() }
            if (mediaPlayer === completed) mediaPlayer = null
            if (generation == playbackGeneration) {
                playClipAt(type, clips, index + 1, generation, dynamicTextAfter)
            }
        }
        player.setOnErrorListener { failed, _, _ ->
            runCatching { failed.release() }
            if (mediaPlayer === failed) mediaPlayer = null
            if (generation == playbackGeneration) {
                playClipAt(type, clips, index + 1, generation, dynamicTextAfter)
            }
            true
        }
        runCatching { player.start() }
            .onFailure {
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                playClipAt(type, clips, index + 1, generation, dynamicTextAfter)
            }
    }

    private fun speakDynamicInternal(
        message: String,
        type: VoiceAlertType,
        generation: Long
    ) {
        if (!ttsReady || generation != playbackGeneration) {
            finishCurrentAlert()
            return
        }

        applySpeechRate()
        val utteranceId = "dynamic_${type.name}_${generation}_${System.currentTimeMillis()}"
        activeUtteranceId = utteranceId
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume())
        }
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            finishCurrentAlert()
        }
    }

    private fun stopCurrentPlayback() {
        activeUtteranceId = null
        runCatching { tts?.stop() }
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    fun isAudioPlaying(): Boolean =
        mediaPlayer?.isPlaying == true || activeUtteranceId != null


    /**
     * Gemini kendi sesiyle konuşurken ALD Drive'ın MP3/TTS uyarılarını geçici olarak susturur.
     * Devam eden uyarı kesilir ve tazeyse kuyruğa geri alınır; Gemini bitince öncelik sırasıyla sürer.
     */
    fun setExternalSpeechActive(active: Boolean) {
        if (externalSpeechActive == active) return
        externalSpeechActive = active
        mainHandler.post {
            if (active) {
                // Yolculuk başlangıcındaki Bismillah MP3 istisnadır; Gemini açılırken
                // zaten çalıyorsa yarıda kesilmez. Diğer bütün eski anonslar silinir.
                pendingAlerts.removeAll { !it.allowDuringExternalSpeech }
                if (activeAlert?.allowDuringExternalSpeech != true) {
                    playbackGeneration += 1L
                    activePriority = 0
                    activeTailPauseMs = 0L
                    activeAlert = null
                    stopCurrentPlayback()
                }
            } else if (activePriority == 0) {
                startNextPendingAlert()
            }
        }
    }

    fun isExternallySuppressed(): Boolean = externalSpeechActive

    fun isTypeEnabled(type: VoiceAlertType): Boolean =
        prefs.getBoolean("enabled_${type.name}", true)

    fun setTypeEnabled(type: VoiceAlertType, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_${type.name}", enabled).apply()
    }

    /** 91: Belirli bir ses tipinin devam eden ve bekleyen anonslarını anında keser. */
    fun stopType(type: VoiceAlertType) {
        mainHandler.post {
            pendingAlerts.removeAll { it.type == type }
            if (activeAlert?.type == type) {
                playbackGeneration += 1L
                activePriority = 0
                activeTailPauseMs = 0L
                activeAlert = null
                stopCurrentPlayback()
                startNextPendingAlert()
            }
        }
    }

    fun speechRate(): Float = prefs.getFloat("speech_rate", 0.90f)

    fun setSpeechRate(value: Float) {
        prefs.edit().putFloat("speech_rate", value.coerceIn(0.6f, 1.4f)).apply()
        applySpeechRate()
    }

    fun volume(): Float = prefs.getFloat("volume", 1.0f)

    fun setVolume(value: Float) {
        prefs.edit().putFloat("volume", value.coerceIn(0f, 1f)).apply()
        mediaPlayer?.setVolume(volume(), volume())
    }

    /** Aldanmaz Drive'ın MP3 ve TTS çıkışlarını tek noktadan açar/kapatır. */
    fun isMasterEnabled(): Boolean = prefs.getBoolean("master_enabled", true)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("master_enabled", enabled).apply()
        if (!enabled) {
            mainHandler.post {
                pendingAlerts.clear()
                playbackGeneration += 1L
                activePriority = 0
                activeTailPauseMs = 0L
                activeAlert = null
                stopCurrentPlayback()
            }
        }
    }

    fun resetDailyState() {
        lastSpokenAt.clear()
        pendingAlerts.clear()
        activePriority = 0
        activeAlert = null
    }

    private fun applySpeechRate() {
        tts?.setSpeechRate(speechRate())
    }

    companion object {
        @Volatile private var INSTANCE: CentralVoiceAlertManager? = null

        fun getInstance(context: Context): CentralVoiceAlertManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CentralVoiceAlertManager(context).also { INSTANCE = it }
            }
    }
}
