package com.aldanmaz.drivedashboard.ui.screen.prayer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveStatus
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.data.prayer.PrayerRepository
import com.aldanmaz.drivedashboard.data.prayer.PrayerTimes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

data class PrayerUiState(
    val times: PrayerTimes? = null,
    val nextPrayerName: String = "--",
    val nextPrayerTime: String = "--:--",
    val remainingText: String = "--",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val enabledPrayers: Set<String> = setOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı"),
    val speechRate: Float = .90f,
    val volume: Float = 1f,
    val enabledVoiceTypes: Set<VoiceAlertType> = VoiceAlertType.entries.toSet()
)

class PrayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PrayerRepository(application)
    private val voice = CentralVoiceAlertManager.getInstance(application)
    private val prefs = application.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(PrayerUiState(
        enabledPrayers = loadEnabled(), speechRate = voice.speechRate(), volume = voice.volume(),
        enabledVoiceTypes = VoiceAlertType.entries.filter { voice.isTypeEnabled(it) }.toSet()
    ))
    val uiState: StateFlow<PrayerUiState> = _uiState.asStateFlow()
    private var ticker: Job? = null
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastFiveMinutePrayerKey: String? = null

    init { startTicker() }

    fun refresh(latitude: Double?, longitude: Double?) {
        if (latitude == null || longitude == null) return
        if (_uiState.value.isLoading) return
        lastLatitude = latitude; lastLongitude = longitude
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.getToday(latitude, longitude) }
                .onSuccess { times -> _uiState.value = _uiState.value.copy(times = times, isLoading = false); updateNextPrayer() }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Namaz vakitleri alınamadı") }
        }
    }

    fun retry() = refresh(lastLatitude, lastLongitude)

    fun setPrayerEnabled(name: String, enabled: Boolean) {
        val updated = _uiState.value.enabledPrayers.toMutableSet().apply { if (enabled) add(name) else remove(name) }
        prefs.edit().putStringSet("enabled_prayers", updated).apply()
        _uiState.value = _uiState.value.copy(enabledPrayers = updated)
    }

    fun setSpeechRate(value: Float) { voice.setSpeechRate(value); _uiState.value = _uiState.value.copy(speechRate = value) }
    fun setVolume(value: Float) { voice.setVolume(value); _uiState.value = _uiState.value.copy(volume = value) }
    fun setVoiceTypeEnabled(type: VoiceAlertType, enabled: Boolean) {
        voice.setTypeEnabled(type, enabled)
        val updated = _uiState.value.enabledVoiceTypes.toMutableSet().apply { if (enabled) add(type) else remove(type) }
        _uiState.value = _uiState.value.copy(enabledVoiceTypes = updated)
    }

    private fun startTicker() {
        ticker?.cancel(); ticker = viewModelScope.launch { while (true) { updateNextPrayer(); delay(30_000L) } }
    }

    private fun updateNextPrayer() {
        val times = _uiState.value.times ?: return
        val entries = listOf("Sabah" to times.fajr, "Öğle" to times.dhuhr, "İkindi" to times.asr, "Akşam" to times.maghrib, "Yatsı" to times.isha)
        val now = LocalDateTime.now()
        val todayCandidates = entries.mapNotNull { (name, text) -> runCatching { name to LocalDateTime.of(now.toLocalDate(), LocalTime.parse(text)) }.getOrNull() }
        val next = todayCandidates.firstOrNull { it.second.isAfter(now) }
        val targetName: String
        val targetTime: LocalDateTime
        val displayTime: String
        if (next != null) { targetName = next.first; targetTime = next.second; displayTime = entries.first { it.first == targetName }.second }
        else { targetName = "Sabah"; targetTime = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.parse(times.fajr)); displayTime = times.fajr }
        val seconds = Duration.between(now, targetTime).seconds.coerceAtLeast(0)
        val h = seconds / 3600; val m = (seconds % 3600) / 60
        _uiState.value = _uiState.value.copy(nextPrayerName = targetName, nextPrayerTime = displayTime, remainingText = if (h > 0) "${h} sa ${m} dk" else "${m} dk")

        // 93: Her namazdan yaklaşık 5 dakika önce yalnız bir kez hatırlat.
        val fiveMinuteKey = "${times.date}|$targetName"
        if (seconds in 270..330 && targetName in _uiState.value.enabledPrayers && lastFiveMinutePrayerKey != fiveMinuteKey) {
            val gemini = GeminiLiveManager.getInstance(getApplication<Application>())
            if (gemini.state.value.status == GeminiLiveStatus.LISTENING) {
                val delivered = gemini.sendProactivePrompt(
                    "Kısa bir namaz vakti hatırlatması yap. Yalnız şunu doğal biçimde söyle: '$targetName namazına 5 dakika var.' " +
                        "Seçili sürücünün adını biliyorsan uygun hitapla başla; başka konu açma."
                )
                if (delivered) lastFiveMinutePrayerKey = fiveMinuteKey
            } else {
                voice.playRecorded(
                    type = VoiceAlertType.PRAYER,
                    priority = VoiceAlertPriority.NORMAL,
                    clip = RecordedVoiceClip.PRAYER_TIME_APPROACHING,
                    cooldownKey = "prayer_5min_${times.date}_$targetName",
                    cooldownMs = 20L * 60L * 60L * 1000L,
                    dynamicTextAfter = "$targetName namazına 5 dakika var."
                )
                lastFiveMinutePrayerKey = fiveMinuteKey
            }
        }

        // Vakit girdikten sonraki ilk 90 saniye içinde, her vakit için bir kez sesli hatırlatma.
        todayCandidates.forEach { (name, time) ->
            val elapsed = Duration.between(time, now).seconds
            if (elapsed in 0..90 && name in _uiState.value.enabledPrayers) {
                voice.playRecorded(
                    type = VoiceAlertType.PRAYER,
                    priority = VoiceAlertPriority.HIGH,
                    clip = RecordedVoiceClip.PRAYER_TIME,
                    cooldownKey = "prayer_${times.date}_$name",
                    cooldownMs = 20L * 60L * 60L * 1000L,
                    // Namazın adı değişken olduğu için yalnız bu kısa bölüm TTS'dir.
                    dynamicTextAfter = "$name namazı."
                )
            }
        }
    }

    private fun loadEnabled(): Set<String> = prefs.getStringSet("enabled_prayers", null)?.toSet()
        ?: setOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı")

    override fun onCleared() { ticker?.cancel(); super.onCleared() }
}
