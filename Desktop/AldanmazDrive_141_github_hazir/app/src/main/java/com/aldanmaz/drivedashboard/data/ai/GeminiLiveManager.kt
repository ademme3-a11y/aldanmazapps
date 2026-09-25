package com.aldanmaz.drivedashboard.data.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.aldanmaz.drivedashboard.data.alert.MediaAppController
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.aldanmaz.drivedashboard.BuildConfig
import com.aldanmaz.drivedashboard.MainActivity
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.trafficagent.TrafikSeslendirici
import com.aldanmaz.drivedashboard.trafficagent.TrafikGeminiDurumu
import com.aldanmaz.drivedashboard.trafficagent.TrafikDurumDeposu
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Transcription
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class GeminiSpeechActivity {
    NONE,
    USER,
    GEMINI,
}

enum class GeminiLiveStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    STOPPING,
    NEEDS_SETUP,
    ERROR,
}

data class GeminiLiveState(
    val status: GeminiLiveStatus = GeminiLiveStatus.IDLE,
    val message: String = "Gemini Live hazır",
    val speechActivity: GeminiSpeechActivity = GeminiSpeechActivity.NONE,
    val isRecoverableError: Boolean = false,
) {
    val occupiesMicrophone: Boolean
        get() = status == GeminiLiveStatus.CONNECTING ||
            status == GeminiLiveStatus.LISTENING ||
            status == GeminiLiveStatus.STOPPING

    val isListening: Boolean
        get() = status == GeminiLiveStatus.LISTENING
}

/**
 * ALD Drive içindeki tek Gemini Live oturumunu yönetir.
 *
 * 96: genel Gemini + tam araç asistanı + tek ses önceliği + bakım/sağlık/konum/hava özellikleri.
 * foreground service tarafindan Activity'den bagimsiz tutulur; ses odagi kaybi
 * oturumu otomatik kapatmaz ve close_gemini dogrudan servisi sonlandirir.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(GeminiLiveState())
    val state: StateFlow<GeminiLiveState> = _state.asStateFlow()

    private var session: LiveSession? = null
    private var connectJob: Job? = null
    private var monitorJob: Job? = null
    private var speechResetJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusGainType = 0
    private var appCheckConfigured = false
    private val liveModelNames = listOf(
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-3.1-flash-live-preview",
    )
    private var preferredLiveModelIndex = 0
    private var activeLiveModelIndex = -1
    // Gemini başlamadan önce müzik çalıyorsa, Live kapandığında aynı oturum kaldığı yerden devam eder.
    private var musicWasPlayingBeforeGemini = false

    // 88 - 85 tabanındaki akıllı sürüş asistanı / güvenli proaktif konuşma durumu.
    private var latestVehicleSnapshot = GeminiVehicleSnapshot()
    private var lastSpeedWarningAtElapsed = 0L
    private var speedWarningArmed = true
    private var wasMovingThisStop = false
    private var exitQuestionAskedThisStop = false
    private var exitQuestionPending = false
    private var exitQuestionPendingUntilElapsed = 0L
    private var lastProactiveSpeechAtElapsed = 0L
    private var pendingWeatherSafetyAlert: String? = null
    private var lastWeatherAlertKey: String? = null
    private var lastWeatherAlertAtElapsed = 0L
    private var lowFuelAlerted = false
    private var coolantAlerted = false
    private var batteryAlerted = false
    private var dtcAlerted = false
    private var lastCabinNoiseBoostAtElapsed = 0L
    private var cabinVolumeBeforeBoost: Int? = null
    private var lastCalmReminderAtElapsed = 0L
    private var fallbackCommandJob: Job? = null
    private var lastToolCallAtElapsed = 0L
    private var lastVehicleHealthPersistAtElapsed = 0L
    private var proactivePromptBusyUntilElapsed = 0L

    // 100 - Rakım eşikleri aynı yolculukta yalnız yükselirken ve bir kez duyurulur.
    private val announcedAltitudeMilestones = mutableSetOf<Int>()
    private var altitudeTripWasActive = false
    private var previousAltitudeMeters: Int? = null
    private var altitudeCandidateMeters: Int? = null
    private var altitudeCandidateSinceElapsed = 0L

    // 100 - Günlük finans sorusu yalnız Live açıkken, günde bir kez sorulur.
    private val dailyFinancePrefs =
        appContext.getSharedPreferences("gemini_daily_finance", Context.MODE_PRIVATE)

    // 91 - Live kilitlenmesini ve yaklaşık 10 dakikalık bağlantı sınırını yönetir.
    private var sessionStartedAtElapsed = 0L
    private var lastUserTranscriptAtElapsed = 0L
    private var lastGeminiActivityAtElapsed = 0L

    // Eski MP3 sürücü karşılama yerine Gemini kendi sesiyle bir kez karşılar.
    private var pendingDriverWelcome: Pair<String, String>? = null
    private var lastDeliveredWelcomeKey: String? = null
    private var pendingTrafficAnnouncement: Pair<String, Long>? = null

    /**
     * MainActivity her güncel araç durumu değiştiğinde çağırır. Live oturumu açıksa
     * yalnız önemli ve seyrek olaylarda Gemini'yi proaktif konuşturur.
     */
    fun onVehicleSnapshot(snapshot: GeminiVehicleSnapshot) {
        latestVehicleSnapshot = snapshot
        val now = SystemClock.elapsedRealtime()

        updateAltitudeTripState(snapshot)

        if (snapshot.speedKmh >= 8) {
            wasMovingThisStop = true
            exitQuestionAskedThisStop = false
            exitQuestionPending = false
            exitQuestionPendingUntilElapsed = 0L
        }

        updateVehicleHealthHistory(snapshot)
        if (_state.value.status != GeminiLiveStatus.LISTENING) {
            // Live kapalıyken geçmiş rakım eşikleri sonradan topluca okunmaz.
            previousAltitudeMeters = snapshot.altitudeMeters
            return
        }

        // Öncelik: bölgesel acil hava > kritik OBD > hız > yakıt > konfor/çıkış sorusu.
        handleSevereWeatherWarning(snapshot, now)
        handleSmartObdWarnings(snapshot)
        handleSmartSpeedWarning(snapshot, now)
        handleSmartFuelWarning(snapshot)
        handleSmartStopQuestion(snapshot, now)
        handleAltitudeMilestone(snapshot, now)
        maybeAskDailyFinance(now)
    }

    private fun updateAltitudeTripState(snapshot: GeminiVehicleSnapshot) {
        val currentAltitude = snapshot.altitudeMeters
        if (snapshot.isTripActive && !altitudeTripWasActive) {
            announcedAltitudeMilestones.clear()
            altitudeCandidateMeters = null
            altitudeCandidateSinceElapsed = 0L
            previousAltitudeMeters = currentAltitude
        } else if (!snapshot.isTripActive) {
            altitudeCandidateMeters = null
            altitudeCandidateSinceElapsed = 0L
            previousAltitudeMeters = currentAltitude
        }
        altitudeTripWasActive = snapshot.isTripActive
    }

    private fun handleAltitudeMilestone(snapshot: GeminiVehicleSnapshot, now: Long) {
        val currentAltitude = snapshot.altitudeMeters ?: return
        val previousAltitude = previousAltitudeMeters
        previousAltitudeMeters = currentAltitude

        if (!snapshot.isTripActive || previousAltitude == null) return

        val activeCandidate = altitudeCandidateMeters
        if (activeCandidate != null) {
            if (currentAltitude < activeCandidate - GeminiLivePerformancePolicy.ALTITUDE_HYSTERESIS_METERS) {
                altitudeCandidateMeters = null
                altitudeCandidateSinceElapsed = 0L
                return
            }
            if (now - altitudeCandidateSinceElapsed < GeminiLivePerformancePolicy.ALTITUDE_STABILITY_MS) return

            val driver = driverAddress(snapshot)
            if (sendProactivePrompt(
                    "$driver bulunduğunuz rakım $activeCandidate metre. Yalnız bu kısa bilgiyi söyle."
                )
            ) {
                announcedAltitudeMilestones +=
                    GeminiLivePerformancePolicy.ALTITUDE_MILESTONES_METERS.filter { it <= activeCandidate }
                altitudeCandidateMeters = null
                altitudeCandidateSinceElapsed = 0L
            }
            return
        }

        val crossed = GeminiLivePerformancePolicy.crossedAltitudeMilestone(
            previousAltitudeMeters = previousAltitude,
            currentAltitudeMeters = currentAltitude,
            alreadyAnnounced = announcedAltitudeMilestones,
        ) ?: return

        altitudeCandidateMeters = crossed
        altitudeCandidateSinceElapsed = now
    }

    private fun maybeAskDailyFinance(nowElapsed: Long) {
        if (assistantSilentMode()) return
        val localNow = ZonedDateTime.now()
        if (!GeminiLivePerformancePolicy.isDailyFinanceWindow(localNow.hour, localNow.minute)) return

        val todayKey = localNow.format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (dailyFinancePrefs.getString("last_question_date", null) == todayKey) return
        if (_state.value.speechActivity != GeminiSpeechActivity.NONE) return
        if (pendingWeatherSafetyAlert != null || pendingTrafficAnnouncement != null || pendingDriverWelcome != null) return
        if (exitQuestionPending) return
        if (nowElapsed < proactivePromptBusyUntilElapsed) return

        val driver = driverAddress(latestVehicleSnapshot)
        if (sendProactivePrompt(
                "$driver günlük finans bilgilerini ister misiniz? Yalnız bu soruyu sor. " +
                    "Kullanıcı evet derse Google Search grounding kullanarak o andaki gram altın satış fiyatını, " +
                    "Amerikan doları/TL kurunu, BIST 100 endeksini ve Arçelik ARCLK hisse fiyatını kısa biçimde söyle. " +
                    "Her değerin güncelleme zamanını veya piyasa kapalıysa son kapanış olduğunu belirt. " +
                    "Güncel ve güvenilir veri bulamazsan sayı uydurma. Kullanıcı hayır derse yalnız 'Tamam.' de ve konuyu kapat."
            )
        ) {
            dailyFinancePrefs.edit().putString("last_question_date", todayKey).apply()
        }
    }

    private fun handleSmartSpeedWarning(snapshot: GeminiVehicleSnapshot, now: Long) {
        val fallbackLimit = if (snapshot.vehicleMode.equals("Karavan", ignoreCase = true)) 80 else 90
        val limit = (snapshot.speedLimitKmh ?: fallbackLimit).coerceIn(30, 140)
        val warningAt = limit + 5

        if (snapshot.speedKmh <= limit - 5) {
            speedWarningArmed = true
            return
        }
        if (snapshot.speedKmh < warningAt) return

        val cooldown = if (snapshot.speedKmh >= limit + 20) 90_000L else 180_000L
        if (!speedWarningArmed && now - lastSpeedWarningAtElapsed < cooldown) return
        if (now - lastSpeedWarningAtElapsed < cooldown) return

        val driver = driverAddress(snapshot)
        val severity = if (snapshot.speedKmh >= limit + 20) {
            "Hız belirgin şekilde yüksek. Sakin ve kısa bir güvenlik uyarısı yap."
        } else {
            "Kısa, nazik bir hız uyarısı yap."
        }
        if (sendSafetyPrompt(
                "$severity $driver hızınız ${snapshot.speedKmh} kilometre. " +
                    "Geçerli hız sınırı yaklaşık $limit kilometre. Dikkatli olun ve uygun hıza dönün. " +
                    "Yalnız bu uyarıyı söyle; sohbeti uzatma."
            )
        ) {
            lastSpeedWarningAtElapsed = now
            speedWarningArmed = false
        }
    }

    private fun handleSmartStopQuestion(snapshot: GeminiVehicleSnapshot, now: Long) {
        if (!wasMovingThisStop || exitQuestionAskedThisStop || exitQuestionPending) return
        // Kırmızı ışık / kısa trafik duruşlarında soru sormamak için gerçek Park durumunda
        // en az 35 saniye beklenir.
        if (!snapshot.isParkMode || snapshot.speedKmh > 1 || snapshot.parkDurationSeconds < 35L) return
        if (now - lastProactiveSpeechAtElapsed < 12_000L) return

        val driver = driverAddress(snapshot)
        if (sendProactivePrompt(
                "$driver araç uzun süredir durmuş durumda. Şimdi yalnız şu soruyu sor: " +
                    "\"Araçtan iniyor musunuz?\" Kullanıcı evet derse ald_drive action=exit_vehicle_yes, " +
                    "hayır derse action=exit_vehicle_no çağır. Başka bir şey söyleme."
            )
        ) {
            exitQuestionAskedThisStop = true
            exitQuestionPending = true
            exitQuestionPendingUntilElapsed = SystemClock.elapsedRealtime() + 20_000L
        }
    }

    private fun handleSmartFuelWarning(snapshot: GeminiVehicleSnapshot) {
        val isLow = (snapshot.fuelPercent != null && snapshot.fuelPercent <= 12) ||
            (snapshot.estimatedFuelRangeKm != null && snapshot.estimatedFuelRangeKm <= 70)
        if (!isLow) {
            lowFuelAlerted = false
            return
        }
        if (lowFuelAlerted) return
        val range = snapshot.estimatedFuelRangeKm?.let { " Tahmini menzil $it kilometre." }.orEmpty()
        if (sendProactivePrompt(
                "Kısa bir yakıt uyarısı yap. Yakıt seviyesi ${snapshot.fuelPercent ?: "düşük"} yüzde.$range " +
                    "Uygun olduğunda yakıt almayı hatırlat; gereksiz konuşma."
            )
        ) {
            lowFuelAlerted = true
        }
    }

    private fun handleSmartObdWarnings(snapshot: GeminiVehicleSnapshot) {
        if (!snapshot.obdConnected) {
            coolantAlerted = false
            batteryAlerted = false
            dtcAlerted = false
            return
        }

        val coolant = snapshot.coolantCelsius
        if (coolant != null && coolant >= 108 && !coolantAlerted) {
            if (sendSafetyPrompt(
                    "Öncelikli fakat sakin bir araç uyarısı yap: motor soğutma suyu sıcaklığı $coolant derece. " +
                        "Sürücüye güvenli bir yerde durup sıcaklığı kontrol etmesini söyle. Teşhis koyma."
                )
            ) {
                coolantAlerted = true
            }
        } else if (coolant != null && coolant <= 100) {
            coolantAlerted = false
        }

        val voltage = snapshot.batteryVoltage
        if (voltage != null && (voltage < 11.8 || voltage > 15.2) && !batteryAlerted) {
            if (sendSafetyPrompt(
                    "Kısa OBD elektrik sistemi uyarısı yap: ölçülen voltaj ${"%.1f".format(Locale.US, voltage)} volt. " +
                        "Değer olağan aralığın dışında görünüyor; uygun zamanda kontrol edilmesini öner, kesin arıza teşhisi koyma."
                )
            ) {
                batteryAlerted = true
            }
        } else if (voltage != null && voltage in 12.0..15.0) {
            batteryAlerted = false
        }

        val hasDtc = snapshot.milOn == true || snapshot.dtcCodes.isNotEmpty()
        if (hasDtc && !dtcAlerted) {
            val codes = snapshot.dtcCodes.take(4).joinToString(", ").ifBlank { "arıza lambası aktif" }
            if (sendSafetyPrompt(
                    "Kısa OBD uyarısı yap: $codes. Bunun profesyonel servis teşhisi olmadığını belirt ve uygun zamanda kontrol öner."
                )
            ) {
                dtcAlerted = true
            }
        } else if (!hasDtc) {
            dtcAlerted = false
        }
    }

    private fun driverAddress(snapshot: GeminiVehicleSnapshot): String {
        val name = snapshot.selectedDriverName?.trim().orEmpty()
        val honorific = snapshot.selectedDriverHonorific?.trim().orEmpty()
        return when {
            name.isNotBlank() && honorific.isNotBlank() -> "$name $honorific,"
            name.isNotBlank() -> "$name,"
            else -> ""
        }
    }

    fun queueDriverWelcome(name: String, honorific: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val cleanHonorific = honorific.trim()
        val key = "$cleanName|$cleanHonorific"
        if (lastDeliveredWelcomeKey == key || pendingDriverWelcome?.let { "${it.first}|${it.second}" } == key) return
        pendingDriverWelcome = cleanName to cleanHonorific
        deliverPendingDriverWelcome()
    }

    private fun deliverPendingDriverWelcome() {
        val welcome = pendingDriverWelcome ?: return
        if (_state.value.status != GeminiLiveStatus.LISTENING) return
        val hitap = listOf(welcome.first, welcome.second).filter { it.isNotBlank() }.joinToString(" ")
        val s = latestVehicleSnapshot
        val weather = s.weatherTemperatureC?.let { temp ->
            val city = s.weatherCity?.let { "$it'ta " }.orEmpty()
            "$city$temp derece, ${s.weatherCondition ?: "hava bilgisi mevcut"}"
        } ?: "hava bilgisi henüz yok"
        val fuel = s.fuelPercent?.let { "yakıt yüzde $it" } ?: "yakıt bilgisi yok"
        val traffic = s.trafficStatus.takeIf { it.isNotBlank() } ?: "trafik bilgisi yok"
        val prayer = if (!s.nextPrayerName.isNullOrBlank() && !s.nextPrayerTime.isNullOrBlank())
            "sonraki namaz ${s.nextPrayerName} saat ${s.nextPrayerTime}" else "namaz vakti bilgisi yok"
        if (sendProactivePrompt(
                "Kısa sürüş öncesi özet ver. '$hitap hoş geldiniz. İyi sürüşler.' ile başla; sonra tek cümlede: " +
                    "hava: $weather; $fuel; trafik: $traffic; $prayer. Bilinmeyen kısmı atla. Karşılık bekleme, uzatma."
            )
        ) {
            lastDeliveredWelcomeKey = "${welcome.first}|${welcome.second}"
            pendingDriverWelcome = null
        }
    }

    fun announceTraffic(prompt: String) {
        if (prompt.isBlank()) return
        if (sendProactivePrompt(prompt)) {
            pendingTrafficAnnouncement = null
        } else if (_state.value.status == GeminiLiveStatus.LISTENING) {
            pendingTrafficAnnouncement = prompt to SystemClock.elapsedRealtime()
        }
    }

    private fun deliverPendingTrafficAnnouncement() {
        val pending = pendingTrafficAnnouncement ?: return
        val age = SystemClock.elapsedRealtime() - pending.second
        if (age > 90_000L) {
            pendingTrafficAnnouncement = null
            return
        }
        if (sendProactivePrompt(pending.first)) {
            pendingTrafficAnnouncement = null
        }
    }

    /** Live oturumuna metin gönderip Gemini'nin kendi sesiyle konuşmasını sağlar. */
    fun sendProactivePrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        if (assistantSilentMode()) return false
        val now = SystemClock.elapsedRealtime()
        if (_state.value.status != GeminiLiveStatus.LISTENING) return false
        if (_state.value.speechActivity != GeminiSpeechActivity.NONE) return false
        if (now < proactivePromptBusyUntilElapsed) return false
        if (now - lastProactiveSpeechAtElapsed < 8_000L) return false
        val activeSession = session ?: return false
        lastProactiveSpeechAtElapsed = now
        proactivePromptBusyUntilElapsed = now + 12_000L
        scope.launch {
            if (runCatching { activeSession.send(prompt) }.isFailure) {
                proactivePromptBusyUntilElapsed = 0L
            }
        }
        return true
    }

    /** Kritik sürüş/hava/OBD uyarıları sessiz modda bile konuşabilir. */
    private fun sendSafetyPrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        val now = SystemClock.elapsedRealtime()
        if (_state.value.status != GeminiLiveStatus.LISTENING) return false
        if (_state.value.speechActivity != GeminiSpeechActivity.NONE) return false
        if (now < proactivePromptBusyUntilElapsed) return false
        val activeSession = session ?: return false
        lastProactiveSpeechAtElapsed = now
        proactivePromptBusyUntilElapsed = now + 12_000L
        scope.launch {
            if (runCatching { activeSession.send(prompt) }.isFailure) {
                proactivePromptBusyUntilElapsed = 0L
            }
        }
        return true
    }

    private fun assistantSilentMode(): Boolean =
        appContext.getSharedPreferences("gemini_assistant", Context.MODE_PRIVATE)
            .getBoolean("silent_mode", false)

    /** 94: Aynı konuşma akışının zorunlu kısa devamı; proaktif 8 sn cooldown'a takılmaz. */
    private fun sendImmediatePrompt(prompt: String): Boolean {
        if (prompt.isBlank() || _state.value.status != GeminiLiveStatus.LISTENING) return false
        if (_state.value.speechActivity != GeminiSpeechActivity.NONE) return false
        val activeSession = session ?: return false
        proactivePromptBusyUntilElapsed = SystemClock.elapsedRealtime() + 10_000L
        scope.launch {
            if (runCatching { activeSession.send(prompt) }.isFailure) {
                proactivePromptBusyUntilElapsed = 0L
            }
        }
        return true
    }

    fun toggle() {
        val enabled = GeminiLiveForegroundService.isUserEnabled(appContext)
        if (enabled || _state.value.occupiesMicrophone) {
            GeminiLiveForegroundService.stop(appContext)
        } else {
            GeminiLiveForegroundService.start(appContext)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_state.value.occupiesMicrophone) return

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = GeminiLiveState(
                status = GeminiLiveStatus.ERROR,
                message = "Gemini Live için mikrofon izni gerekli.",
            )
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            // 138: Gemini Live ses oturumu bazı OEM/car-unit çalarlarda müziği
            // sistem tarafından geçici olarak durdurabiliyor. Önceki durumu kaydet.
            musicWasPlayingBeforeGemini = MediaAppController.rememberMusicBeforeExternalSpeech(appContext)
            _state.value = GeminiLiveState(
                status = GeminiLiveStatus.CONNECTING,
                message = "Gemini Live bağlanıyor…",
            )
            // 96: Kullanıcı Gemini ikonuna bastığı andan Live kapanana kadar
            // Bismillah dışında ALD Drive'ın diğer ses motorları susar.
            setAldDriveWarningsSuppressed(true)
            // 137: Gemini Live açıkken arka plan müziğine AUDIOFOCUS_MAY_DUCK uygulanmıyor.
            // Böylece müzik sesi Live oturumu nedeniyle otomatik kısılmıyor.

            try {
                ensureFirebaseReady()
                GeminiActionBridge.clearPendingCritical()

                val systemInstruction = content {
                    text(
                        "Sen ALDANMAZ DRIVE içinde çalışan tam yetenekli Türkçe Gemini yardımcısısın. Yalnız araç sorularıyla sınırlı değilsin. " +
                            GeminiConversationPolicy.SYSTEM_INSTRUCTION +
                            "Genel bilgi, bilim, teknoloji, tarih, günlük yaşam, seyahat, kültür, hesaplama ve benzeri normal Gemini sorularını doğrudan yanıtla. " +
                            "Konuşmaya gereksiz giriş cümlesi ekleme; mümkün olduğunca ilk cümlede yanıtı ver, kısa ve akıcı konuş. " +
                            "Bir konuda emin değilsen kesinmiş gibi uydurma; belirsizliği açıkça söyle. Güncel haber, fiyat, spor, siyaset, kişi/kurum durumu, ürün, işletme saati veya değişebilen bilgilerde Google Search grounding kullan. " +
                            "Yer/işletme/konum hakkında güncel bilgi gerektiğinde Google Search kullan; Live API oturumunda Google Maps aracı kullanma. Genel sorularda ald_drive aracını gereksiz yere çağırma. " +
                            "Araç hareket halindeyken yanıtları kısa, net ve sürüşü bölmeyecek şekilde ver; park halindeyken kullanıcı isterse daha ayrıntılı anlat. " +
                            "Kullanıcı ALD Drive içinde bir işlem istediğinde ald_drive aracını kullan; araç çağrısı başarılı olmadan işlemi yapılmış gibi söyleme. " +
                            "Uygulama, hız, yakıt, OBD, hava, trafik veya araç durumunu sorduğunda action=get_status kullan. " +
                            "Saat, tarih, bugün hangi gün veya yerel zaman sorulduğunda ASLA UTC saati tahmin etme; mutlaka action=get_local_time çağır ve cihazın yerel saatini söyle. " +
                            "OBD sorularında get_status içindeki OBD değerlerini kullan; bilinmeyen değeri uydurma. RPM, OBD hızı, motor suyu, yağ sıcaklığı, akü voltajı, yük, gaz kelebeği, MAF, manifold/basınç, turbo, yakıt rayı, yakıt seviyesi, yakıt debisi, tork, MIL ve DTC kodlarını okuyabilirsin. " +
                            "Trafik Ajanı için hedef istendiğinde action=set_traffic_target kullan ve value alanına yalnız hedefi yaz. Örnek: 'Trafik ajanına Ankara'yı hedef yap' => action=set_traffic_target, value=Ankara. Trafik sorununun nerede olduğu sorulursa get_status içindeki trafficEventLocationText/trafficEventRoad/trafficEventDistanceMeters alanlarını kullan. " +
                            "'Trafik ajanını kapat/durdur/bitir' için mutlaka action=traffic_agent_off kullan. " +
                            "Canlı yükseklik yarım ekran için show_live_slope_half, kapatmak için hide_live_slope_half kullan. Park ekranında hız kadranını görmek için show_speedometer; park görünümüne dönmek için show_park_screen kullan. " +
                            "Kullanıcı 'ana sayfaya dön', 'ana ekrana dön', 'ALD Drive'a dön' veya benzeri derse action=open_drive; 'geri git', 'bu sayfayı kapat', 'önceki sayfa' derse action=go_back kullan. Harici bir uygulama öndeyse open_drive ALD Drive'ı yeniden öne getirir. " +
                            "Chrome/ChatGPT/YouTube gibi harici uygulamalar için 'kapat' dendiğinde Android uygulamayı zorla öldürmez; close_chrome/close_chatgpt/close_youtube veya return_to_ald_drive ile ALD Drive'ı öne getir. Müzik için close_music oynatmayı durdurup ALD Drive'a döner. " +
                            "Müzik çaları aç için open_music; belirli bir parça/şarkı istendiğinde play_music_query ve value=parça adı kullan; oynat/devam et için media_play, duraklat için media_pause, kapat için close_music kullan. " +
                            "Uygulama ayarlarını sesle değiştirebilirsin. Aç/kapat ayarları için set_program_toggle ve value='ayar:on' veya 'ayar:off' kullan. Sayısal program ayarları için set_program_value ve value='ayar:değer' kullan. Desteklenen ayar anahtarları: driver_selection, safety_reminder, trip_prayer, rest_reminder, night_stars, safety_lock, road_sign_test, traffic_voice, prayer_sabah, prayer_ogle, prayer_ikindi, prayer_aksam, prayer_yatsi. Yazı boyutu için set_text_scale; gündüz/gece parlaklığı için set_day_brightness/set_night_brightness kullan. " +
                            "Navigasyon simgesi için action=open_navigation yalnız Google ve Yandex seçim penceresini açar. Kullanıcı seçim penceresi açıkken 'Google' veya 'Yandex' derse sırasıyla open_google_navigation veya open_yandex_navigation çağır. 'Google Maps aç' için open_google_navigation, 'Yandex aç' için open_yandex_navigation kullan. " +
                            "Haritada sesli arama için google_maps_search veya yandex_maps_search ve value=aranacak yer kullan. Yol tarifi için google_maps_route veya yandex_maps_route ve value=hedef kullan. Örnek: 'Google Maps ile Antalya Havalimanına yol tarifi' => google_maps_route, value=Antalya Havalimanı. 'Yandexte benzinlik ara' => yandex_maps_search, value=benzinlik. " +
                            "Kullanıcı ev adresini kaydetmek/girmek isterse action=set_home_address ve value=adres; iş adresi için set_work_address kullan. Adres belirtilmemişse önce adresi sor. Navigasyon adres ayar ekranını özellikle isterse open_navigation_settings kullan. " +
                            "Araç çağrısı confirmation_required döndürürse kullanıcıdan açık onay iste; evet/onaylıyorum derse action=confirm, hayır/iptal derse action=cancel kullan. " +
                            "Sistem sana araçtan inme sorusu sordurursa, sonraki net evet cevabında action=exit_vehicle_yes, hayır cevabında action=exit_vehicle_no çağır. " +
                            "Kullanıcı Gemini'yi kapat, sohbeti kapat, görüşmeyi bitir, konuşmayı kapat veya dinlemeyi bırak derse mutlaka action=close_gemini çağır. Live kapandıktan sonra arka planda hiçbir uyanma mikrofonu çalışmaz; yeniden başlatmak için kullanıcı Gemini ikonuna dokunur. " +
                            "Kabin ortamında sürekli yol/rüzgar gürültüsü konuşmayı belirgin biçimde bastırıyorsa action=cabin_noise_high çağır; tek korna, müzik veya kısa ses için çağırma. Ortam yeniden belirgin biçimde sakinleşirse action=cabin_noise_normal çağır. " +
                            "İnsanların uzun süre belirgin biçimde bağırdığı/tartıştığı açıkça anlaşılıyorsa action=cabin_shouting çağır; yalnız yüksek yol gürültüsünü veya sürücünün sana yüksek sesle komut vermesini bağırma sayma. " +
                            "Kullanıcının kelimeleri action adlarıyla birebir aynı olmak zorunda değildir: ana ekran/başlangıç/ana sayfaya dön=open_drive; geri git/bu sayfayı kapat=go_back; yakıt/depo=open_fuel; hava/hava durumu=open_weather; trafik=open_traffic_agent; rota/navigasyon/harita=open_navigation; Google Maps=open_google_navigation; Yandex=open_yandex_navigation; ayarlar=open_settings; görünüm/tema=open_appearance; araç/araç deposu=open_vehicle_selection; hız koridoru/ortalama hız=open_speed_corridor; müzik durdur/duraklat=stop_music; oynat/devam et=media_play; sesi kapat=volume_mute; sesi aç=volume_unmute. " +
                            "Aç/kapat isteklerinde toggle yerine açıkça *_on veya *_off eylemini seç. Sürücü adını uygun olduğunda kullan ama her cümlede tekrar etme. " +
                            "Ses biyometrisi yapma ve yalnız sesten konuşmacının Mehmet veya Nurdan olduğunu kesin doğruladığını iddia etme. Birden fazla kişinin bulunduğu kabinde komutun seçili sürücüden geldiği belirsizse araç/uygulama kontrol aracı çağırma. Konuşmacının çocuk olduğu açıkça anlaşılıyorsa komut veya uygulama kontrolü uygulama; yalnız 'Sürücü onayı gerekli.' de. Bu kural bir ses kimlik doğrulama sistemi değildir ve güvenlik için ihtiyatlı davran. " +
                            "Dacia Sandero Stepway 2017 1.5 dizel için genel bakım ve arıza bilgisini kullanabilirsin. Arıza lambası/OBD kodu/simptom verildiğinde olası nedenleri önem ve olasılık sırasıyla anlat; kesin teşhis koyma, güvenli kontrol/servis adımı öner. Araç sağlık geçmişi için get_vehicle_health, bakım listesi için get_maintenance/set_maintenance kullan. Kullanıcı bugün araç/OBD değerlerinin normal olup olmadığını sorarsa get_vehicle_health sonucundaki todayHealthConclusion alanını esas al: yeterli OBD verisi ve teknik uyarı yoksa normal de; teknik uyarı varsa normal dışı uyarı bulunduğunu söyle ve ayrıntı için Uyarılar sayfasını ziyaret etmesini belirt; veri yoksa normal olduğunu iddia etme. " +
                            "Kullanıcının konumunu sorarsa get_status içindeki currentAddressText/latitude/longitude alanlarını kullan; şehir, mahalle, sokak ve mevcutsa bina numarasını söyle. Konum bilinmiyorsa uydurma. " +
                            "Ürün fiyatı, işletme/ziyaretçi yorumu, güncel dizel fiyatı, uçak-otobüs-tren-özel araç maliyeti veya başka güncel fiyat istenirse Google Search grounding kullan; tarih ve konumu gerekiyorsa önce get_local_time/get_status ile al. Tahmini değer ile canlı fiyatı ayır. " +
                            "Belirli sanatçı/parça için önce play_music_query; müzik uygulamasıyla bulunamayabilecek Kur'an, tilavet veya web içeriği için play_web_media kullan. Telifli içeriği metin olarak uzun uzun okuma; uygun uygulamada oynat. " +
                            "Not oluşturup e-posta için email_note kullan. Kayıtlı e-posta yoksa kullanıcıdan bir kez adres iste ve set_note_email ile kaydet. " +
                            "Sürüş özeti için get_drive_summary; son park yeri için get_last_park; park notu için set_parking_note; sessiz/aile modu için assistant_silent_on/off kullan. Acil durumda emergency_mode açık onay ister. " +
                            "Bölgesel şiddetli yağmur, fırtına, dolu riski veya aşırı sıcak uyarısı sistemden geldiğinde kısa ve öncelikli güvenlik uyarısı olarak hemen söyle. " +
                            "Sistem günlük finans sorusunu sordurduğunda kullanıcı evet derse Google Search grounding ile gram altın satış, Amerikan doları/TL, BIST 100 ve Arçelik ARCLK değerlerini o anda doğrula; güncelleme zamanını veya son kapanış bilgisini belirt. Güncel veri yoksa sayı uydurma. Hayır derse yalnız 'Tamam.' de. " +
                            "Güvenlik uyarılarında teşhis koyma; gözlenen değeri söyle ve güvenli davranışı öner. " +
                            "Kullanıcı sana genel bir soru sorduğunda araç komutu bekleme; mümkün olan en doğru cevabı ver ve güncellik gerekiyorsa grounding kullan."
                    )
                }

                // 100: Öncelik Firebase Android akışında belgelenmiş 2.5 native-audio modeli.
                // Model erişimi geçici sorun çıkarırsa 3.1 Flash Live'a otomatik geri dönülür. Her iki model de ALD Drive araçları
                // ve güncel bilgi için Google Search grounding aracını alır. Google Maps Live API tarafından desteklenmediği için eklenmez.
                val tools = buildGeminiTools()
                var newSession: LiveSession? = null
                var lastModelError: Exception? = null
                var connectedModelIndex = -1
                val modelAttemptOrder = liveModelNames.indices.map { offset ->
                    (preferredLiveModelIndex + offset) % liveModelNames.size
                }

                for (modelIndex in modelAttemptOrder) {
                    val modelName = liveModelNames[modelIndex]
                    var candidate: LiveSession? = null
                    try {
                        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                            modelName = modelName,
                            generationConfig = liveGenerationConfig {
                                responseModality = ResponseModality.AUDIO
                                speechConfig = SpeechConfig(voice = Voice("FENRIR"))
                                inputAudioTranscription = AudioTranscriptionConfig()
                                outputAudioTranscription = AudioTranscriptionConfig()
                            },
                            tools = tools,
                            systemInstruction = systemInstruction,
                        )
                        val connectedCandidate = model.connect()
                        candidate = connectedCandidate
                        connectedCandidate.startAudioConversation(
                            ::handleFunctionCall,
                            ::handleTranscription,
                        )
                        delay(650L)
                        if (connectedCandidate.isClosed()) {
                            error("$modelName bağlantısı sunucu tarafından kapatıldı.")
                        }
                        newSession = connectedCandidate
                        connectedModelIndex = modelIndex
                        break
                    } catch (modelError: Exception) {
                        lastModelError = modelError
                        runCatching { candidate?.stopAudioConversation() }
                        runCatching { candidate?.close() }
                    }
                }

                val connectedSession = newSession
                    ?: throw (lastModelError ?: IllegalStateException("Gemini Live modeli bağlanamadı."))
                session = connectedSession
                activeLiveModelIndex = connectedModelIndex
                preferredLiveModelIndex = connectedModelIndex.coerceAtLeast(0)
                sessionStartedAtElapsed = SystemClock.elapsedRealtime()
                lastUserTranscriptAtElapsed = 0L
                lastGeminiActivityAtElapsed = sessionStartedAtElapsed

                _state.value = GeminiLiveState(
                    status = GeminiLiveStatus.LISTENING,
                    message = "Dinliyorum… Konuşabilirsiniz.",
                )
                // 96: Gemini açık olduğu sürece eski TTS/MP3/trafik sesleri susar.
                // CentralVoiceAlertManager içindeki Bismillah klibi özel istisnadır.
                setAldDriveWarningsSuppressed(true)
                startSessionMonitor(connectedSession)
                scope.launch {
                    delay(900L)
                    deliverPendingDriverWelcome()
                    deliverPendingTrafficAnnouncement()
                }
            } catch (cancelled: CancellationException) {
                setAldDriveWarningsSuppressed(false)
                closeSession()
                abandonAudioFocus()
                restoreMusicAfterGemini()
                throw cancelled
            } catch (error: Exception) {
                val setupMissing = error is GeminiLiveSetupException
                if (!setupMissing) {
                    preferredLiveModelIndex = GeminiReconnectPolicy.nextModelIndex(
                        currentIndex = preferredLiveModelIndex,
                        modelCount = liveModelNames.size,
                    )
                }
                setAldDriveWarningsSuppressed(false)
                closeSession()
                abandonAudioFocus()
                restoreMusicAfterGemini()
                val rawMessage = error.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gemini Live bağlantısı kurulamadı."
                val displayMessage = rawMessage
                _state.value = GeminiLiveState(
                    status = if (setupMissing) GeminiLiveStatus.NEEDS_SETUP else GeminiLiveStatus.ERROR,
                    message = displayMessage,
                    isRecoverableError = !setupMissing &&
                        GeminiLiveForegroundService.isUserEnabled(appContext),
                )
            }
        }
    }

    fun stop() {
        forceStopNow()
    }

    /**
     * 91: X düğmesi veya bildirimdeki kapat komutu sunucu cevabını beklemez.
     * UI/mikrofon durumu anında IDLE olur; uzak LiveSession arka planda güvenle kapatılır.
     */
    fun forceStopNow() {
        connectJob?.cancel()
        connectJob = null
        monitorJob?.cancel()
        monitorJob = null
        speechResetJob?.cancel()
        speechResetJob = null
        fallbackCommandJob?.cancel()
        fallbackCommandJob = null
        pendingTrafficAnnouncement = null
        // 136: Bir önceki Live oturumundan kalan sürücü karşılama mesajı yeni oturumda yanlış kişiye okunmasın.
        pendingDriverWelcome = null
        GeminiActionBridge.clearPendingCritical()
        setAldDriveWarningsSuppressed(false)
        abandonAudioFocus()
        restoreMusicAfterGemini()

        val activeSession = session
        session = null
        activeLiveModelIndex = -1
        sessionStartedAtElapsed = 0L
        lastUserTranscriptAtElapsed = 0L
        lastGeminiActivityAtElapsed = 0L
        proactivePromptBusyUntilElapsed = 0L
        _state.value = GeminiLiveState(
            status = GeminiLiveStatus.IDLE,
            message = "Gemini Live hazır",
        )

        scope.launch {
            withTimeoutOrNull(1_500L) {
                runCatching { activeSession?.stopAudioConversation() }
                runCatching { activeSession?.close() }
            }
        }
    }

    fun clearError() {
        if (_state.value.status == GeminiLiveStatus.ERROR ||
            _state.value.status == GeminiLiveStatus.NEEDS_SETUP
        ) {
            _state.value = GeminiLiveState()
        }
    }

    private fun handleTranscription(input: Transcription?, output: Transcription?) {
        val nowElapsed = SystemClock.elapsedRealtime()
        input?.text?.trim()?.takeIf { it.isNotBlank() }?.let { heard ->
            lastUserTranscriptAtElapsed = nowElapsed
            // 96: Çıkış sorusundan sonraki 20 saniyede evet/hayır model tool-call'ına
            // bırakılmaz; transkript doğrudan ve deterministik olarak işlenir.
            if (exitQuestionPending && nowElapsed > exitQuestionPendingUntilElapsed) {
                exitQuestionPending = false
                exitQuestionPendingUntilElapsed = 0L
            }
            val normalizedAnswer = heard.lowercase(Locale("tr", "TR")).trim()
            val exitAnswer = if (exitQuestionPending && nowElapsed <= exitQuestionPendingUntilElapsed) {
                when {
                    normalizedAnswer in setOf("evet", "evet iniyorum", "iniyorum", "ineceğim", "inecegim", "tamam evet", "evet ineceğim", "evet inecegim", "evet çıkıyorum", "evet cikiyorum") -> true
                    normalizedAnswer in setOf("hayır", "hayir", "hayır inmiyorum", "hayir inmiyorum", "inmiyorum", "yok", "hayır kalıyorum", "hayir kaliyorum", "hayır inmeyeceğim", "hayir inmeyecegim") -> false
                    else -> null
                }
            } else null

            fallbackCommandJob?.cancel()
            if (exitAnswer != null) {
                exitQuestionPending = false
                exitQuestionPendingUntilElapsed = 0L
                fallbackCommandJob = scope.launch {
                    repeat(25) {
                        if (_state.value.speechActivity == GeminiSpeechActivity.NONE) return@repeat
                        delay(100L)
                    }
                    val prompt = if (exitAnswer) {
                        "Yalnız şunu söyle: Araç içinde telefon, anahtar, cüzdan, çanta veya kişisel eşyanızı unutmadığınızdan emin olun."
                    } else {
                        "Yalnız 'Tamam.' de."
                    }
                    sendImmediatePrompt(prompt)
                }
            } else {
                fallbackCommandJob = scope.launch {
                    delay(1_150L)
                    if (SystemClock.elapsedRealtime() - lastToolCallAtElapsed >= 1_050L) {
                        tryDeterministicCommandFallback(heard)
                    }
                }
            }
        }

        val activity = when {
            !output?.text.isNullOrBlank() -> GeminiSpeechActivity.GEMINI
            !input?.text.isNullOrBlank() -> GeminiSpeechActivity.USER
            else -> return
        }
        if (activity == GeminiSpeechActivity.GEMINI) {
            lastGeminiActivityAtElapsed = nowElapsed
            proactivePromptBusyUntilElapsed = nowElapsed + 5_000L
            // 137: Konuşma sırasında da müzik akışına otomatik duck uygulanmaz.
        } else if (nowElapsed - lastGeminiActivityAtElapsed >= 2_500L) {
            // Hoparlörden dönen kısa transkriptler Gemini konuşurken ses odağını
            // düşürüp tekrar yükseltmesin; multimedya cihazındaki kekemeliği azaltır.
            // 137: Müzik sesini Gemini oturumu boyunca otomatik kısmıyoruz.
        }

        val current = _state.value
        if (current.status == GeminiLiveStatus.LISTENING) {
            _state.value = current.copy(
                speechActivity = activity,
                message = if (activity == GeminiSpeechActivity.USER) "Sizi dinliyorum…" else "Gemini konuşuyor…",
            )
        }

        speechResetJob?.cancel()
        val activityMarkerElapsed = nowElapsed
        speechResetJob = scope.launch {
            delay(if (activity == GeminiSpeechActivity.GEMINI) 2_600L else 1_100L)
            val newerActivityArrived = if (activity == GeminiSpeechActivity.GEMINI) {
                lastGeminiActivityAtElapsed > activityMarkerElapsed
            } else {
                lastUserTranscriptAtElapsed > activityMarkerElapsed
            }
            if (newerActivityArrived) return@launch
            val latest = _state.value
            if (latest.status == GeminiLiveStatus.LISTENING) {
                // 137: Müzik sesini Gemini oturumu boyunca otomatik kısmıyoruz.
                if (activity == GeminiSpeechActivity.GEMINI) {
                    proactivePromptBusyUntilElapsed = 0L
                }
                _state.value = latest.copy(
                    speechActivity = GeminiSpeechActivity.NONE,
                    message = "Dinliyorum… Konuşabilirsiniz.",
                )
                deliverPendingWeatherAlert()
                deliverPendingTrafficAnnouncement()
                deliverPendingDriverWelcome()
            }
        }
    }

    private fun buildGeminiTools(): List<Tool> {
        val aldDrive = FunctionDeclaration(
            name = "ald_drive",
            description =
                "ALD Drive uygulamasını kontrol eder veya durumunu okur. " +
                    "action için kullanılabilecek değerler: get_status, get_local_time, confirm, cancel, exit_vehicle_yes, exit_vehicle_no, cabin_noise_high, cabin_noise_normal, cabin_shouting, " +
                    "open_drive, go_back, open_fuel, open_statistics, open_weather, open_prayer, open_obd, open_warning_history, " +
                    "open_traffic_agent, open_navigation, open_navigation_settings, open_google_navigation, open_yandex_navigation, google_maps_search, yandex_maps_search, google_maps_route, yandex_maps_route, open_clock, open_settings, open_appearance, " +
                    "open_header_layout, open_vehicle_systems, open_vehicle_selection, open_help, open_speed_corridor, " +
                    "show_route_slope, hide_route_slope, show_live_slope, hide_live_slope, show_live_slope_half, hide_live_slope_half, show_speedometer, show_park_screen, toggle_compass, compass_on, compass_off, " +
                    "start_trip, stop_trip, start_speed_corridor, stop_speed_corridor, toggle_traffic_agent, traffic_agent_on, traffic_agent_off, " +
                    "set_vehicle_mode, set_vehicle, select_driver, navigate_home, navigate_work, navigate_destination, set_home_address, set_work_address, traffic_command, set_traffic_target, " +
                    "open_youtube, open_music, play_music_query, stop_music, close_music, open_radio, stop_radio, open_chatgpt, open_chrome, close_chrome, close_chatgpt, close_youtube, return_to_ald_drive, media_next, media_previous, " +
                    "media_play, media_pause, set_program_toggle, set_program_value, set_text_scale, set_day_brightness, set_night_brightness, volume_up, volume_down, volume_mute, volume_unmute, set_volume_percent, brightness_up, brightness_down, " +
                    "set_brightness_percent, set_day_mode, set_night_mode, set_auto_appearance, open_wifi_settings, open_bluetooth_settings, " +
                    "obd_connect, obd_disconnect, refresh_weather, close_gemini, reset_statistics, reset_fuel_statistics, reset_trip_distance, " +
                    "set_note_email, email_note, get_vehicle_health, get_maintenance, set_maintenance, get_drive_summary, get_last_park, set_parking_note, assistant_silent_on, assistant_silent_off, emergency_mode, play_web_media. " +
                    "value yalnız işlem ek bilgi gerektiriyorsa kullanılır: hedef, sürücü, araç modu, araç veya 0-100 yüzde gibi. " +
                    "Doğal Türkçe eş anlamları kabul et: ana ekran/başlangıç=open_drive, depo/yakıt=open_fuel, hava durumu=open_weather, " +
                    "harita/rota=open_navigation, Google Maps=open_google_navigation, Yandex=open_yandex_navigation, Google haritada ara=google_maps_search, Yandexte ara=yandex_maps_search, Google yol tarifi=google_maps_route, Yandex yol tarifi=yandex_maps_route, geri git/bu sayfayı kapat=go_back, ev adresi kaydet=set_home_address, iş adresi kaydet=set_work_address, tema/görünüm=open_appearance, araç deposu=open_vehicle_selection, ortalama hız/hız koridoru=open_speed_corridor, " +
                    "müziği durdur/duraklat=stop_music, devam et/oynat=media_play, sessize al=volume_mute, sesi geri aç=volume_unmute, canlı yüksekliği yarım ekran aç=show_live_slope_half, yarım ekranı kapat=hide_live_slope_half, hız kadranını göster=show_speedometer, notu mail at=email_note, Kur'an/tilavet/web medya=play_web_media. " +
                    "Saat/tarih sorularında get_local_time; Trafik Ajanı hedefinde set_traffic_target kullan.",
            parameters = mapOf(
                "action" to Schema.string("Yapılacak ALD Drive işleminin kimliği."),
                "value" to Schema.string("İşlemin gerekiyorsa ek değeri veya hedef metni."),
            ),
            optionalParameters = listOf("value"),
        )
        return listOf(
            Tool.functionDeclarations(listOf(aldDrive)),
            Tool.googleSearch(),
        )
    }

    private fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        val action = runCatching {
            call.args["action"]?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty().let(GeminiActionBridge::normalizeActionName)

        val value = runCatching {
            call.args["value"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        lastToolCallAtElapsed = SystemClock.elapsedRealtime()
        lastGeminiActivityAtElapsed = lastToolCallAtElapsed

        val response = when {
            call.name != "ald_drive" -> buildJsonObject {
                put("status", "rejected")
                put("message", "Bilinmeyen araç çağrısı: ${call.name}")
            }

            action == "get_status" -> buildStatusResponse()

            action == "get_local_time" -> buildLocalTimeResponse()

            action == "get_vehicle_health" -> buildVehicleHealthResponse()

            action == "get_maintenance" -> buildMaintenanceResponse()

            action == "set_maintenance" -> updateMaintenance(value)

            action == "get_drive_summary" -> buildDriveSummaryResponse(value)

            action == "get_last_park" -> buildLastParkResponse()

            action == "set_parking_note" -> setParkingNote(value)

            action == "assistant_silent_on" -> setAssistantSilentMode(true)

            action == "assistant_silent_off" -> setAssistantSilentMode(false)

            action == "set_note_email" -> setNoteEmail(value)

            action == "email_note" -> prepareEmailNote(value)

            action == "exit_vehicle_yes" -> {
                val pending = exitQuestionPending && SystemClock.elapsedRealtime() <= exitQuestionPendingUntilElapsed
                exitQuestionPending = false
                exitQuestionPendingUntilElapsed = 0L
                buildJsonObject {
                    put("status", if (pending) "accepted" else "nothing_pending")
                    put(
                        "message",
                        if (pending) {
                            "Araç içinde telefon, anahtar, cüzdan, çanta veya kişisel eşyanızı unutmadığınızdan emin olun."
                        } else {
                            "Araçtan inme sorusu beklenmiyor."
                        }
                    )
                }
            }

            action == "exit_vehicle_no" -> {
                val pending = exitQuestionPending && SystemClock.elapsedRealtime() <= exitQuestionPendingUntilElapsed
                exitQuestionPending = false
                exitQuestionPendingUntilElapsed = 0L
                buildJsonObject {
                    put("status", if (pending) "accepted" else "nothing_pending")
                    put("message", if (pending) "Tamam." else "Araçtan inme sorusu beklenmiyor.")
                }
            }

            action == "cabin_noise_high" -> handleCabinNoiseBoost()

            action == "cabin_noise_normal" -> handleCabinNoiseRestore()

            action == "cabin_shouting" -> handleCalmCabinReminder()

            action in setOf("set_home_address", "set_work_address") && (value?.trim()?.length ?: 0) < 5 -> buildJsonObject {
                put("status", "rejected")
                put("message", "Bu işlem için açık bir adres değeri gerekli. Kullanıcıdan adresi sorun; 'gir' veya 'kaydet' kelimesini adres olarak kullanmayın.")
            }

            action == "close_gemini" -> {
                // Function response sunucuya once donsun; hemen ardindan Live servisini
                // gercekten kapat. Bu islem UI/Activity kolektorune bagli degildir.
                scope.launch {
                    delay(700L)
                    GeminiLiveForegroundService.stop(appContext)
                }
                buildJsonObject {
                    put("status", "accepted")
                    put("action", "close_gemini")
                    put("message", "Gemini Live oturumu kapatiliyor.")
                }
            }

            action == "confirm" -> when (val result = GeminiActionBridge.confirmCritical()) {
                is GeminiDispatchResult.Accepted -> buildJsonObject {
                    put("status", "accepted")
                    put("action", result.command.action)
                    put("message", "Kullanıcı onayı alındı; kritik işlem ALD Drive'a iletildi.")
                }
                is GeminiDispatchResult.Rejected -> buildJsonObject {
                    put("status", "rejected")
                    put("message", result.reason)
                }
                is GeminiDispatchResult.ConfirmationRequired -> buildJsonObject {
                    put("status", "confirmation_required")
                }
                GeminiDispatchResult.NothingPending -> buildJsonObject {
                    put("status", "nothing_pending")
                    put("message", "Onay bekleyen işlem yok.")
                }
            }

            action == "cancel" -> when (val result = GeminiActionBridge.cancelCritical()) {
                is GeminiDispatchResult.Rejected -> buildJsonObject {
                    put("status", "cancelled")
                    put("message", result.reason)
                }
                else -> buildJsonObject {
                    put("status", "nothing_pending")
                    put("message", "İptal edilecek bekleyen işlem yok.")
                }
            }

            action.isBlank() -> buildJsonObject {
                put("status", "rejected")
                put("message", "action alanı boş olamaz.")
            }

            else -> {
                if (action in setOf("open_drive", "go_back", "return_to_ald_drive", "close_chrome", "close_chatgpt", "close_youtube", "close_music")) {
                    runCatching {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        )
                    }
                }
                when (val result = GeminiActionBridge.request(action, value)) {
                is GeminiDispatchResult.Accepted -> buildJsonObject {
                    put("status", "accepted")
                    put("action", result.command.action)
                    put("message", "Komut ALD Drive'a iletildi.")
                }
                is GeminiDispatchResult.ConfirmationRequired -> buildJsonObject {
                    put("status", "confirmation_required")
                    put("action", result.command.action)
                    put("message", "Bu kritik işlem henüz uygulanmadı. Kullanıcıdan açık onay isteyin.")
                }
                is GeminiDispatchResult.Rejected -> buildJsonObject {
                    put("status", "rejected")
                    put("message", result.reason)
                }
                GeminiDispatchResult.NothingPending -> buildJsonObject {
                    put("status", "rejected")
                    put("message", "Bekleyen işlem yok.")
                }
                }
            }
        }

        return FunctionResponsePart(
            name = call.name,
            response = response,
            id = call.id,
        )
    }

    private fun buildStatusResponse() = GeminiActionBridge.snapshot().let { s ->
        val trafficEvent = TrafikGeminiDurumu.al(appContext)
        buildJsonObject {
            put("status", "ok")
            put("currentScreen", s.currentScreen)
            put("latitude", s.latitude?.toString() ?: "unknown")
            put("longitude", s.longitude?.toString() ?: "unknown")
            put("currentAddressText", s.currentAddressText ?: "unknown")
            put("speedKmh", s.speedKmh)
            put("speedLimitKmh", s.speedLimitKmh?.toString() ?: "unknown")
            put("altitudeMeters", s.altitudeMeters?.toString() ?: "unknown")
            put("fuelPercent", s.fuelPercent?.toString() ?: "unknown")
            put("remainingFuelLiters", s.remainingFuelLiters?.let { "%.1f".format(it) } ?: "unknown")
            put("estimatedFuelRangeKm", s.estimatedFuelRangeKm?.toString() ?: "unknown")
            put("speedDataSource", s.speedDataSource)
            put("fuelDataSource", s.fuelDataSource)
            put("obdDataAvailableToday", s.obdDataAvailableToday)
            put("todayTechnicalWarningCount", s.todayTechnicalWarningCount)
            put("todayTechnicalWarningSummary", s.todayTechnicalWarningSummary ?: "none")
            put("tripDistanceKm", "%.1f".format(s.tripDistanceKm))
            put("todayDistanceKm", "%.1f".format(s.todayDistanceKm))
            put("tripAverageSpeedKmh", "%.1f".format(s.tripAverageSpeedKmh))
            put("tripActive", s.isTripActive)
            put("parkMode", s.isParkMode)
            put("parkDurationSeconds", s.parkDurationSeconds)
            put("vehicleMode", s.vehicleMode)
            put("trafficAgentActive", s.isTrafficAgentActive || TrafikDurumDeposu.servisAktifMi(appContext))
            put("compassVisible", s.isCompassPanelVisible)
            put("trafficStatus", s.trafficStatus)
            put("trafficEventRoad", trafficEvent?.yolAdi ?: s.trafficEventRoad ?: "unknown")
            put("trafficEventLocationText", trafficEvent?.konumMetni ?: s.trafficEventLocationText ?: "unknown")
            put("trafficEventDistanceMeters", trafficEvent?.uzaklikMetre?.toString() ?: s.trafficEventDistanceMeters?.toString() ?: "unknown")
            put("trafficEventType", trafficEvent?.durumMetni ?: "unknown")
            put("speedCorridorActive", s.isSpeedCorridorActive)
            put("speedCorridorAverageKmh", "%.1f".format(s.speedCorridorAverageKmh))
            put("weatherCity", s.weatherCity ?: "unknown")
            put("weatherTemperatureC", s.weatherTemperatureC?.toString() ?: "unknown")
            put("weatherCondition", s.weatherCondition ?: "unknown")
            put("weatherApparentTemperatureC", s.weatherApparentTemperatureC?.toString() ?: "unknown")
            put("weatherWindSpeedKmh", s.weatherWindSpeedKmh?.toString() ?: "unknown")
            put("weatherWindGustKmh", s.weatherWindGustKmh?.toString() ?: "unknown")
            put("weatherAlert", s.weatherAlertText ?: "none")
            put("assistantSilentMode", assistantSilentMode())
            put("obdConnected", s.obdConnected)
            put("obdRpm", s.obdRpm?.toString() ?: "unknown")
            put("obdVehicleSpeedKmh", s.obdVehicleSpeedKmh?.toString() ?: "unknown")
            put("coolantCelsius", s.coolantCelsius?.toString() ?: "unknown")
            put("batteryVoltage", s.batteryVoltage?.let { "%.1f".format(Locale.US, it) } ?: "unknown")
            put("engineLoadPercent", s.engineLoadPercent?.toString() ?: "unknown")
            put("throttlePercent", s.throttlePercent?.toString() ?: "unknown")
            put("intakeAirCelsius", s.intakeAirCelsius?.toString() ?: "unknown")
            put("mafGramsPerSecond", s.mafGramsPerSecond?.let { "%.1f".format(Locale.US, it) } ?: "unknown")
            put("manifoldPressureKpa", s.manifoldPressureKpa?.toString() ?: "unknown")
            put("boostPressureKpa", s.boostPressureKpa?.toString() ?: "unknown")
            put("fuelRailPressureKpa", s.fuelRailPressureKpa?.toString() ?: "unknown")
            put("obdFuelLevelPercent", s.obdFuelLevelPercent?.toString() ?: "unknown")
            put("ambientAirCelsius", s.ambientAirCelsius?.toString() ?: "unknown")
            put("engineOilCelsius", s.engineOilCelsius?.toString() ?: "unknown")
            put("engineFuelRateLitersHour", s.engineFuelRateLitersHour?.let { "%.1f".format(Locale.US, it) } ?: "unknown")
            put("engineTorquePercent", s.engineTorquePercent?.toString() ?: "unknown")
            put("milOn", s.milOn?.toString() ?: "unknown")
            put("dtcCodes", if (s.dtcCodes.isEmpty()) "none" else s.dtcCodes.joinToString(","))
            put("nextPrayerName", s.nextPrayerName ?: "unknown")
            put("nextPrayerTime", s.nextPrayerTime ?: "unknown")
            put("selectedDriver", s.selectedDriverName ?: "unknown")
            put("selectedDriverHonorific", s.selectedDriverHonorific ?: "unknown")
        }
    }

    private fun handleSevereWeatherWarning(snapshot: GeminiVehicleSnapshot, now: Long) {
        val alert = snapshot.weatherAlertText?.trim().orEmpty()
        if (alert.isBlank()) return
        val key = alert.lowercase(Locale("tr", "TR"))
        if (lastWeatherAlertKey == key && now - lastWeatherAlertAtElapsed < 45 * 60_000L) return
        val driver = driverAddress(snapshot)
        val prompt = buildString {
            if (driver.isNotBlank()) append(driver).append(' ')
            append("Hava verilerine göre öncelikli uyarı: ").append(alert)
            append(" Sürüş güvenliği için kısa ve net biçimde dikkatli olunmasını söyle; resmi meteoroloji uyarısıymış gibi iddia etme.")
        }
        if (sendSafetyPrompt(prompt)) {
            lastWeatherAlertKey = key
            lastWeatherAlertAtElapsed = now
            pendingWeatherSafetyAlert = null
        } else {
            pendingWeatherSafetyAlert = prompt
        }
    }

    private fun deliverPendingWeatherAlert() {
        val prompt = pendingWeatherSafetyAlert ?: return
        if (sendSafetyPrompt(prompt)) {
            lastWeatherAlertKey = latestVehicleSnapshot.weatherAlertText?.lowercase(Locale("tr", "TR"))
            lastWeatherAlertAtElapsed = SystemClock.elapsedRealtime()
            pendingWeatherSafetyAlert = null
        }
    }

    private fun updateVehicleHealthHistory(snapshot: GeminiVehicleSnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (!GeminiLivePerformancePolicy.shouldPersistVehicleHealth(
                obdConnected = snapshot.obdConnected,
                lastPersistAtElapsed = lastVehicleHealthPersistAtElapsed,
                nowElapsed = now,
            )
        ) return

        val prefs = appContext.getSharedPreferences("gemini_vehicle_health", Context.MODE_PRIVATE)
        val count = prefs.getInt("sample_count", 0).coerceAtLeast(0)
        val alpha = if (count < 20) 1.0 / (count + 1).coerceAtLeast(1) else 0.05
        val editor = prefs.edit()
        fun update(key: String, value: Double?) {
            if (value == null || value.isNaN()) return
            val old = if (prefs.contains(key)) prefs.getFloat(key, value.toFloat()).toDouble() else value
            val next = old + alpha * (value - old)
            editor.putFloat(key, next.toFloat())
        }
        update("coolant_avg", snapshot.coolantCelsius?.toDouble())
        update("battery_avg", snapshot.batteryVoltage)
        update("oil_avg", snapshot.engineOilCelsius?.toDouble())
        update("load_avg", snapshot.engineLoadPercent?.toDouble())
        editor
            .putInt("sample_count", (count + 1).coerceAtMost(1000000))
            .putLong("last_update", System.currentTimeMillis())
            .putString("last_dtc", snapshot.dtcCodes.joinToString(","))
            .apply()
        lastVehicleHealthPersistAtElapsed = now
    }

    private fun buildVehicleHealthResponse() = buildJsonObject {
        val s = GeminiActionBridge.snapshot()
        val prefs = appContext.getSharedPreferences("gemini_vehicle_health", Context.MODE_PRIVATE)
        val coolantAvg = prefs.takeIf { it.contains("coolant_avg") }?.getFloat("coolant_avg", 0f)?.toDouble()
        val voltageAvg = prefs.takeIf { it.contains("battery_avg") }?.getFloat("battery_avg", 0f)?.toDouble()
        val oilAvg = prefs.takeIf { it.contains("oil_avg") }?.getFloat("oil_avg", 0f)?.toDouble()
        put("status", "ok")
        put("vehicle", "Dacia Sandero Stepway 2017 1.5 dizel")
        put("obdConnected", s.obdConnected)
        put("coolantNowC", s.coolantCelsius?.toString() ?: "unknown")
        put("coolantHistoricalAverageC", coolantAvg?.let { "%.1f".format(Locale.US, it) } ?: "unknown")
        put("batteryNowV", s.batteryVoltage?.let { "%.2f".format(Locale.US, it) } ?: "unknown")
        put("batteryHistoricalAverageV", voltageAvg?.let { "%.2f".format(Locale.US, it) } ?: "unknown")
        put("oilNowC", s.engineOilCelsius?.toString() ?: "unknown")
        put("oilHistoricalAverageC", oilAvg?.let { "%.1f".format(Locale.US, it) } ?: "unknown")
        put("milOn", s.milOn?.toString() ?: "unknown")
        put("dtcCodes", if (s.dtcCodes.isEmpty()) "none" else s.dtcCodes.joinToString(","))
        put("speedDataSource", s.speedDataSource)
        put("fuelDataSource", s.fuelDataSource)
        put("obdDataAvailableToday", s.obdDataAvailableToday)
        put("todayTechnicalWarningCount", s.todayTechnicalWarningCount)
        put("todayTechnicalWarningSummary", s.todayTechnicalWarningSummary ?: "none")
        val todayConclusion = when {
            s.todayTechnicalWarningCount > 0 -> "Bugün normal dışı araç/OBD uyarıları kaydedildi. Ayrıntılı incelemek için Uyarılar sayfasını ziyaret edin."
            s.obdDataAvailableToday -> "Bugünkü araç ve OBD değerleri normal görünüyor. Kritik teknik araç uyarısı kaydedilmedi."
            else -> "Bugün için yeterli OBD verisi yok; araç değerlerinin normal olduğunu doğrulayamıyorum."
        }
        put("todayHealthConclusion", todayConclusion)
        put("sampleCount", prefs.getInt("sample_count", 0))
        put("message", "Geçmiş ortalamalar ALD Drive'ın yerel OBD örneklerinden türetilir; servis teşhisi değildir.")
    }

    private fun buildMaintenanceResponse() = buildJsonObject {
        val prefs = appContext.getSharedPreferences("maintenance_settings", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", null)
        put("status", "ok")
        put("items", raw ?: "[]")
        put("message", if (raw.isNullOrBlank()) "Henüz özel bakım kaydı yok; uygulamadaki varsayılan bakım başlıkları kullanılabilir." else "Bakım kayıtları alındı.")
    }

    private fun updateMaintenance(value: String?) = buildJsonObject {
        val pieces = value.orEmpty().split("|||", limit = 3)
        val title = pieces.getOrNull(0)?.trim().orEmpty()
        val km = pieces.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val date = pieces.getOrNull(2)?.trim().orEmpty()
        if (title.isBlank()) {
            put("status", "rejected")
            put("message", "Bakım başlığı gerekli. value biçimi: başlık|||km|||tarih")
            return@buildJsonObject
        }
        val prefs = appContext.getSharedPreferences("maintenance_settings", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString("items", "[]") ?: "[]") }.getOrElse { JSONArray() }
        var found = false
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("title").equals(title, ignoreCase = true)) {
                item.put("dueKm", km).put("dueDate", date)
                found = true
                break
            }
        }
        if (!found) {
            arr.put(JSONObject().put("id", "gemini_${System.currentTimeMillis()}").put("title", title).put("dueKm", km).put("dueDate", date))
        }
        prefs.edit().putString("items", arr.toString()).apply()
        put("status", "accepted")
        put("message", "$title bakım kaydı güncellendi.")
    }

    private fun buildDriveSummaryResponse(value: String?) = buildJsonObject {
        val now = ZonedDateTime.now()
        val period = value.orEmpty().lowercase(Locale("tr", "TR"))
        val start = when {
            "yıl" in period || "yil" in period || "year" in period -> now.withDayOfYear(1).toLocalDate().atStartOfDay(now.zone)
            "ay" in period || "month" in period -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
            "hafta" in period || "week" in period -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(now.zone)
            else -> now.toLocalDate().atStartOfDay(now.zone)
        }
        val summary = runCatching {
            runBlocking(Dispatchers.IO) {
                AldanmazDriveDatabase.getInstance(appContext).tripDao().getStatisticsForPeriod(
                    start.toInstant().toEpochMilli(),
                    now.plusSeconds(1).toInstant().toEpochMilli(),
                    null,
                    null,
                )
            }
        }.getOrNull()
        if (summary == null) {
            put("status", "error")
            put("message", "Sürüş günlüğü okunamadı.")
        } else {
            put("status", "ok")
            put("periodStart", start.toString())
            put("tripCount", summary.tripCount)
            put("distanceKm", "%.1f".format(Locale.US, summary.totalDistanceKm))
            put("averageSpeedKmh", "%.1f".format(Locale.US, summary.averageSpeedKmh))
            put("maxSpeedKmh", summary.maxSpeedKmh)
            put("fuelLiters", "%.2f".format(Locale.US, summary.estimatedFuelConsumedLiters))
            put("fuelCostTl", "%.2f".format(Locale.US, summary.estimatedFuelCost))
            put("movingSeconds", summary.movingDurationSeconds)
            put("parkSeconds", summary.parkDurationSeconds)
        }
    }

    private fun buildLastParkResponse() = buildJsonObject {
        val latest = runCatching {
            runBlocking(Dispatchers.IO) { AldanmazDriveDatabase.getInstance(appContext).tripDao().getLatestTrip() }
        }.getOrNull()
        val note = appContext.getSharedPreferences("parking_memory", Context.MODE_PRIVATE).getString("note", null)
        put("status", if (latest != null || !note.isNullOrBlank()) "ok" else "unknown")
        put("address", latest?.endAddress?.takeIf { it.isNotBlank() } ?: latestVehicleSnapshot.currentAddressText ?: "unknown")
        put("latitude", latest?.endLatitude?.toString() ?: latestVehicleSnapshot.latitude?.toString() ?: "unknown")
        put("longitude", latest?.endLongitude?.toString() ?: latestVehicleSnapshot.longitude?.toString() ?: "unknown")
        put("parkedAtEpochMillis", latest?.endedAtEpochMillis?.toString() ?: "unknown")
        put("note", note ?: "none")
    }

    private fun setParkingNote(value: String?) = buildJsonObject {
        val note = value?.trim().orEmpty()
        if (note.isBlank()) {
            put("status", "rejected"); put("message", "Park notu boş olamaz.")
        } else {
            appContext.getSharedPreferences("parking_memory", Context.MODE_PRIVATE).edit()
                .putString("note", note).putLong("saved_at", System.currentTimeMillis()).apply()
            put("status", "accepted"); put("message", "Park notu kaydedildi.")
        }
    }

    private fun setAssistantSilentMode(enabled: Boolean) = buildJsonObject {
        appContext.getSharedPreferences("gemini_assistant", Context.MODE_PRIVATE).edit().putBoolean("silent_mode", enabled).apply()
        put("status", "accepted")
        put("silentMode", enabled)
        put("message", if (enabled) "Sessiz/aile modu açık. Yalnız kritik sürüş, OBD ve hava uyarıları konuşulacak." else "Normal konuşma modu açık.")
    }

    private fun setNoteEmail(value: String?) = buildJsonObject {
        val email = value?.trim().orEmpty()
        if (!email.contains('@') || !email.contains('.')) {
            put("status", "rejected"); put("message", "Geçerli bir e-posta adresi söyleyin.")
        } else {
            appContext.getSharedPreferences("gemini_notes", Context.MODE_PRIVATE).edit().putString("email", email).apply()
            put("status", "accepted"); put("message", "Not e-posta adresi kaydedildi.")
        }
    }

    private fun prepareEmailNote(value: String?) = buildJsonObject {
        val savedEmail = appContext.getSharedPreferences("gemini_notes", Context.MODE_PRIVATE).getString("email", null)
        if (savedEmail.isNullOrBlank()) {
            put("status", "needs_email")
            put("message", "Kayıtlı e-posta adresi yok. Kullanıcıdan e-posta adresini isteyin ve set_note_email ile kaydedin.")
            return@buildJsonObject
        }
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) {
            put("status", "rejected"); put("message", "E-posta not metni boş olamaz.")
            return@buildJsonObject
        }
        val parts = raw.split("|||", limit = 2)
        val subject = if (parts.size == 2) parts[0].trim().ifBlank { "ALD Drive notu" } else "ALD Drive notu"
        val body = if (parts.size == 2) parts[1].trim() else raw
        when (val result = GeminiActionBridge.request("email_note", "$savedEmail|||$subject|||$body")) {
            is GeminiDispatchResult.Accepted -> { put("status", "accepted"); put("message", "E-posta uygulaması not ile açılıyor; göndermeyi kullanıcı tamamlayacak.") }
            is GeminiDispatchResult.Rejected -> { put("status", "rejected"); put("message", result.reason) }
            is GeminiDispatchResult.ConfirmationRequired -> { put("status", "confirmation_required"); put("message", "Onay gerekiyor.") }
            GeminiDispatchResult.NothingPending -> { put("status", "rejected"); put("message", "Komut iletilemedi.") }
        }
    }

    private fun buildLocalTimeResponse() = ZonedDateTime.now().let { now ->
        val tr = Locale("tr", "TR")
        buildJsonObject {
            put("status", "ok")
            put("localTime", now.format(DateTimeFormatter.ofPattern("HH:mm", tr)))
            put("localDate", now.format(DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", tr)))
            put("timeZone", now.zone.id)
            put("message", "Bu cihazın yerel saatidir; UTC değildir.")
        }
    }

    private fun handleCabinNoiseBoost() = buildJsonObject {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCabinNoiseBoostAtElapsed < 5 * 60_000L) {
            put("status", "cooldown")
            put("message", "Kabin gürültüsü için ses yakın zamanda zaten yükseltildi; tekrar değiştirme.")
        } else {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cabinVolumeBeforeBoost == null) cabinVolumeBeforeBoost = current
            val target = (current + 2).coerceAtMost(max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            lastCabinNoiseBoostAtElapsed = now
            put("status", "accepted")
            put("message", "Kabin gürültüsü nedeniyle konuşma sesini bir miktar yükselttim.")
        }
    }

    private fun handleCabinNoiseRestore() = buildJsonObject {
        val previous = cabinVolumeBeforeBoost
        if (previous == null) {
            put("status", "nothing_pending")
            put("message", "Gürültü nedeniyle uygulanmış geçici bir ses artışı yok.")
        } else {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previous.coerceIn(0, max), 0)
            cabinVolumeBeforeBoost = null
            put("status", "accepted")
            put("message", "Ortam sakinleştiği için konuşma sesini önceki düzeyine aldım.")
        }
    }

    private fun handleCalmCabinReminder() = buildJsonObject {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCalmReminderAtElapsed < 10 * 60_000L) {
            put("status", "cooldown")
            put("message", "Sakinleştirme hatırlatması yakın zamanda yapıldı; tekrar etme.")
        } else {
            lastCalmReminderAtElapsed = now
            put("status", "accepted")
            put("message", "Lütfen biraz sakin olalım. Daha sakin konuşursak birbirimizi daha iyi anlayabiliriz.")
        }
    }

    /**
     * Function calling bazen doğal Türkçe komutu kaçırırsa sık kullanılan güvenli komutlar
     * ikinci bir yerel eşleme katmanından geçirilir. Toggle yerine idempotent on/off kullanılır.
     */
    private fun tryDeterministicCommandFallback(heardText: String) {
        val text = heardText.lowercase(Locale("tr", "TR")).trim()
        if (text.length < 2) return

        fun emit(action: String, value: String? = null) {
            GeminiActionBridge.request(action, value)
        }

        val trafficTargetPatterns = listOf(
            Regex("trafik\\s+ajan(?:ı|i|ına|ina)?\\s+(?:hedef(?:i|ine)?\\s+)?(.+?)(?:\\s+hedef\\s+(?:yap|gir|ayarla)|\\s+hedef\\s+olsun)$"),
            Regex("trafik\\s+ajan(?:ı|i)?\\s+hedef(?:i|ine)?\\s+(.+)$")
        )
        trafficTargetPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.length >= 2 }
            ?.let { emit("set_traffic_target", it); return }

        val homeAddressPatterns = listOf(
            Regex("(?:navigasyon\\s+)?ev\\s+adres(?:i|ine)?\\s+(.+?)(?:\\s+(?:gir|kaydet|ayarla))$"),
            Regex("ev\\s+adres(?:i|im)?\\s+(?:olarak\\s+)?(.+?)\\s+(?:kaydet|ayarla)$")
        )
        homeAddressPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.length >= 5 }
            ?.let { emit("set_home_address", it); return }

        val workAddressPatterns = listOf(
            Regex("(?:navigasyon\\s+)?(?:iş|is)\\s+adres(?:i|ine)?\\s+(.+?)(?:\\s+(?:gir|kaydet|ayarla))$"),
            Regex("(?:iş|is)\\s+adres(?:i|im)?\\s+(?:olarak\\s+)?(.+?)\\s+(?:kaydet|ayarla)$")
        )
        workAddressPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.length >= 5 }
            ?.let { emit("set_work_address", it); return }

        val googleRoutePatterns = listOf(
            Regex("""google(?:\s+maps|\s+haritalar)?(?:\s+ile)?\s+(.+?)(?:\s+(?:yol\s+tarifi|rota|navigasyon)(?:\s+(?:aç|ac|başlat|baslat|ver))?)$"""),
            Regex("""(.+?)\s+(?:için|icin)?\s*google(?:\s+maps)?\s+(?:yol\s+tarifi|rota)(?:\s+(?:aç|ac|ver))?$""")
        )
        googleRoutePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.length >= 2 }
            ?.let { emit("google_maps_route", it); return }

        val yandexRoutePatterns = listOf(
            Regex("""yandex(?:\s+maps|\s+haritalar)?(?:\s+ile)?\s+(.+?)(?:\s+(?:yol\s+tarifi|rota|navigasyon)(?:\s+(?:aç|ac|başlat|baslat|ver))?)$"""),
            Regex("""(.+?)\s+(?:için|icin)?\s*yandex(?:\s+maps)?\s+(?:yol\s+tarifi|rota)(?:\s+(?:aç|ac|ver))?$""")
        )
        yandexRoutePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.length >= 2 }
            ?.let { emit("yandex_maps_route", it); return }

        Regex("""google(?:\s+maps|\s+haritalar)?(?:'da|da|\s+üzerinde|\s+uzerinde)?\s+(.+?)\s+ara$""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }
            ?.let { emit("google_maps_search", it); return }
        Regex("""yandex(?:'te|te|\s+maps|\s+haritalar)?(?:\s+üzerinde|\s+uzerinde)?\s+(.+?)\s+ara$""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }
            ?.let { emit("yandex_maps_search", it); return }

        Regex("""(?:müzik|muzik)(?:\s+çalarda|\s+çalar)?\s+(.+?)\s+(?:çal|cal|oynat)$""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }
            ?.let { emit("play_music_query", it); return }

        if ("saat kaç" in text || "saat kac" in text || "yerel saat" in text ||
            "tarih ne" in text || "bugün günlerden" in text || "bugun gunlerden" in text
        ) {
            val now = ZonedDateTime.now()
            val tr = Locale("tr", "TR")
            val local = now.format(DateTimeFormatter.ofPattern("HH:mm", tr))
            val date = now.format(DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", tr))
            sendProactivePrompt("Kullanıcı yerel saati/tarihi sordu. Yalnız cihazın yerel bilgisini söyle: saat $local, tarih $date. UTC kullanma.")
            return
        }

        when {
            ("gemini" in text || "sohbet" in text || "konuşma" in text || "konusma" in text) && listOf("kapat", "bitir", "durdur").any(text::contains) -> GeminiLiveForegroundService.stop(appContext)
            "ana ekran" in text || text == "ana sayfa" -> emit("open_drive")
            ("yakıt" in text || "yakit" in text || "depo" in text) && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_fuel")
            "istatistik" in text && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_statistics")
            ("hava" in text) && listOf("aç", "ac", "göster", "goster", "durumu").any(text::contains) -> emit("open_weather")
            ("uyarı" in text || "uyari" in text) && ("geçmiş" in text || "gecmis" in text) && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_warning_history")
            "obd" in text && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_obd")
            "trafik ajan" in text && listOf("aç", "ac", "başlat", "baslat").any(text::contains) -> emit("traffic_agent_on")
            "trafik ajan" in text && listOf("kapat", "durdur", "bitir").any(text::contains) -> emit("traffic_agent_off")
            ("canlı yükseklik" in text || "canli yukseklik" in text) && ("yarım" in text || "yarim" in text) && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("show_live_slope_half")
            ("yarım ekran" in text || "yarim ekran" in text) && listOf("kapat", "gizle").any(text::contains) -> emit("hide_live_slope_half")
            ("hız ekran" in text || "hiz ekran" in text || "hız kadran" in text || "hiz kadran" in text) -> emit("show_speedometer")
            ("ana sayfaya dön" in text || "ana sayfaya don" in text || "ana ekrana dön" in text || "ana ekrana don" in text) -> emit("open_drive")
            ("geri git" in text || "bu sayfayı kapat" in text || "bu sayfayi kapat" in text || "önceki sayfa" in text || "onceki sayfa" in text) -> emit("go_back")
            text == "google" || text == "google maps" || text == "google haritalar" || "google seç" in text || "google sec" in text -> emit("open_google_navigation")
            text == "yandex" || text == "yandex maps" || "yandex seç" in text || "yandex sec" in text -> emit("open_yandex_navigation")
            ("google maps" in text || "google haritalar" in text || "google navigasyon" in text) && listOf("aç", "ac", "başlat", "baslat").any(text::contains) -> emit("open_google_navigation")
            ("yandex" in text) && listOf("aç", "ac", "başlat", "baslat").any(text::contains) -> emit("open_yandex_navigation")
            ("navigasyon" in text || "harita" in text || "rota" in text) && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_navigation")
            "ayar" in text && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_settings")
            ("tema" in text || "görünüm" in text || "gorunum" in text) && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("open_appearance")
            "pusula" in text && listOf("aç", "ac", "göster", "goster").any(text::contains) -> emit("compass_on")
            "pusula" in text && listOf("kapat", "gizle").any(text::contains) -> emit("compass_off")
            "eve git" in text || "eve götür" in text || "eve gotur" in text -> emit("navigate_home")
            "işe git" in text || "ise git" in text || "işe götür" in text || "ise gotur" in text -> emit("navigate_work")
            "youtube" in text && listOf("aç", "ac").any(text::contains) -> emit("open_youtube")
            "chatgpt" in text && listOf("kapat", "çık", "cik").any(text::contains) -> emit("close_chatgpt")
            "chatgpt" in text && listOf("aç", "ac").any(text::contains) -> emit("open_chatgpt")
            "chrome" in text && listOf("kapat", "çık", "cik").any(text::contains) -> emit("close_chrome")
            "chrome" in text && listOf("aç", "ac").any(text::contains) -> emit("open_chrome")
            "müzik" in text && "kapat" in text -> emit("close_music")
            "müzik" in text && listOf("durdur", "duraklat").any(text::contains) -> emit("stop_music")
            "müzik" in text && listOf("oynat", "devam", "başlat", "baslat").any(text::contains) -> emit("media_play")
            "müzik" in text && listOf("aç", "ac").any(text::contains) -> emit("open_music")
            ("ald drive" in text || "uygulamaya" in text) && listOf("dön", "don", "geri").any(text::contains) -> emit("return_to_ald_drive")
            ("sesi kapat" in text || "sessize al" in text) -> emit("volume_mute")
            ("sesi aç" in text || "sesi ac" in text || "sessizi kapat" in text) -> emit("volume_unmute")
            ("sesi artır" in text || "sesi arttır" in text || "sesi artir" in text || "sesi arttir" in text) -> emit("volume_up")
            "sesi azalt" in text -> emit("volume_down")
            "karavan modu" in text -> emit("set_vehicle_mode", "Karavan")
            "otomobil modu" in text || "araba modu" in text -> emit("set_vehicle_mode", "Otomobil")
        }
    }

    private fun startSessionMonitor(activeSession: LiveSession) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (session === activeSession) {
                delay(1_000L)
                val now = SystemClock.elapsedRealtime()
                val closed = activeSession.isClosed()
                val unansweredUserSpeech =
                    lastUserTranscriptAtElapsed > lastGeminiActivityAtElapsed &&
                        now - lastUserTranscriptAtElapsed >= 22_000L
                // Firebase Live bağlantısı yaklaşık 10 dakika ile sınırlı. 91, sessiz bir
                // anda sınırdan önce kontrollü yeniden bağlanarak 'dönüyor ama cevap yok'
                // durumunu önler.
                val scheduledRefresh =
                    sessionStartedAtElapsed > 0L &&
                        now - sessionStartedAtElapsed >= 8L * 60_000L + 45_000L &&
                        _state.value.speechActivity == GeminiSpeechActivity.NONE

                if (closed || unansweredUserSpeech || scheduledRefresh) {
                    if (!scheduledRefresh && activeLiveModelIndex >= 0) {
                        preferredLiveModelIndex = GeminiReconnectPolicy.nextModelIndex(
                            currentIndex = activeLiveModelIndex,
                            modelCount = liveModelNames.size,
                        )
                    }
                    setAldDriveWarningsSuppressed(false)
                    closeSession()
                    abandonAudioFocus()
                    _state.value = GeminiLiveState(
                        status = GeminiLiveStatus.ERROR,
                        message = when {
                            scheduledRefresh -> "Gemini Live oturumu yenileniyor…"
                            unansweredUserSpeech -> "Gemini yanıt vermedi. Bağlantı yenileniyor…"
                            else -> "Gemini Live bağlantısı sona erdi. Yeniden bağlanılıyor…"
                        },
                        isRecoverableError = true,
                    )
                    return@launch
                }
            }
        }
    }

    private fun ensureFirebaseReady() {
        val firebaseApp = runCatching { FirebaseApp.getInstance() }.getOrNull()
            ?: FirebaseApp.initializeApp(appContext)
            ?: throw GeminiLiveSetupException(
                "Gemini Live kurulumu eksik. app/google-services.json dosyasını ekleyin."
            )

        if (!appCheckConfigured) {
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                // 91: Firebase'in resmi Android debug sağlayıcısı kullanılır.
                // Debug secret cihazdaki SharedPreferences'ta tutulur; özel/internal
                // provider kullanmayız. 87-88'deki recursive provider StackOverflow'a
                // sebep oluyordu.
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
            appCheckConfigured = true
        }
    }

    private fun restoreMusicAfterGemini() {
        if (!musicWasPlayingBeforeGemini) return
        musicWasPlayingBeforeGemini = false
        MediaAppController.resumeMusicAfterExternalSpeech(appContext)
    }

    private fun setAldDriveWarningsSuppressed(suppressed: Boolean) {
        CentralVoiceAlertManager.getInstance(appContext).setExternalSpeechActive(suppressed)
        TrafikSeslendirici.setExternalSpeechActive(suppressed)
    }

    private fun requestAudioFocus(exclusive: Boolean = false) {
        val requestedGain = if (exclusive) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        }
        if (audioFocusRequest != null && audioFocusGainType == requestedGain) return

        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
        audioFocusGainType = 0

        // 98: Özel odak yalnız Gemini yanıt verirken tutulur. Dinleme sırasında
        // normal geçici odak kullanıldığı için navigasyon ve diğer araç sesleri
        // Gemini tüm oturum boyunca açıkken gereksiz yere engellenmez.
        val request = AudioFocusRequest.Builder(requestedGain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { _ ->
                // 82: Baska bir uygulama ses odagini aldiginda Gemini Live'i kapatma.
                // Oturum foreground service tarafindan arka planda aktif tutulur.
            }
            .build()
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
            audioFocusGainType = requestedGain
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
        audioFocusGainType = 0
    }

    private suspend fun closeSession() {
        val activeSession = session
        session = null
        activeLiveModelIndex = -1
        withTimeoutOrNull(1_500L) {
            runCatching { activeSession?.stopAudioConversation() }
            runCatching { activeSession?.close() }
        }
    }

    private class GeminiLiveSetupException(message: String) : IllegalStateException(message)

    companion object {
        @Volatile
        private var instance: GeminiLiveManager? = null

        fun getInstance(context: Context): GeminiLiveManager =
            instance ?: synchronized(this) {
                instance ?: GeminiLiveManager(context).also { instance = it }
            }
    }
}
