package com.aldanmaz.drivedashboard.data.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gemini Live ile Compose/UI katmanı arasındaki tek yönlü komut köprüsü.
 * Gemini doğrudan ViewModel veya NavController tutmaz; yalnızca burada bir komut bırakır.
 * MainActivity bu komutları ana thread üzerinde uygular.
 */
data class GeminiAppCommand(
    val action: String,
    val value: String? = null,
)

data class GeminiVehicleSnapshot(
    val currentScreen: String = "unknown",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val currentAddressText: String? = null,
    val speedKmh: Int = 0,
    val speedLimitKmh: Int? = null,
    val altitudeMeters: Int? = null,
    val fuelPercent: Int? = null,
    val remainingFuelLiters: Double? = null,
    val estimatedFuelRangeKm: Int? = null,
    val speedDataSource: String = "GPS",
    val fuelDataSource: String = "HESAP",
    val obdDataAvailableToday: Boolean = false,
    val todayTechnicalWarningCount: Int = 0,
    val todayTechnicalWarningSummary: String? = null,
    val tripDistanceKm: Double = 0.0,
    val todayDistanceKm: Double = 0.0,
    val tripAverageSpeedKmh: Double = 0.0,
    val isTripActive: Boolean = false,
    val isParkMode: Boolean = false,
    val parkDurationSeconds: Long = 0L,
    val vehicleMode: String = "Otomobil",
    val isTrafficAgentActive: Boolean = false,
    val isCompassPanelVisible: Boolean = false,
    val trafficStatus: String = "Kapalı",
    val trafficEventRoad: String? = null,
    val trafficEventLocationText: String? = null,
    val trafficEventDistanceMeters: Int? = null,
    val isSpeedCorridorActive: Boolean = false,
    val speedCorridorAverageKmh: Double = 0.0,
    val weatherCity: String? = null,
    val weatherTemperatureC: Int? = null,
    val weatherCondition: String? = null,
    val weatherApparentTemperatureC: Int? = null,
    val weatherWindSpeedKmh: Int? = null,
    val weatherWindGustKmh: Int? = null,
    val weatherAlertText: String? = null,
    val obdConnected: Boolean = false,
    val obdRpm: Int? = null,
    val obdVehicleSpeedKmh: Int? = null,
    val coolantCelsius: Int? = null,
    val batteryVoltage: Double? = null,
    val engineLoadPercent: Int? = null,
    val throttlePercent: Int? = null,
    val intakeAirCelsius: Int? = null,
    val mafGramsPerSecond: Double? = null,
    val manifoldPressureKpa: Int? = null,
    val boostPressureKpa: Int? = null,
    val fuelRailPressureKpa: Int? = null,
    val obdFuelLevelPercent: Int? = null,
    val ambientAirCelsius: Int? = null,
    val engineOilCelsius: Int? = null,
    val engineFuelRateLitersHour: Double? = null,
    val engineTorquePercent: Int? = null,
    val milOn: Boolean? = null,
    val dtcCodes: List<String> = emptyList(),
    val nextPrayerName: String? = null,
    val nextPrayerTime: String? = null,
    val selectedDriverName: String? = null,
    val selectedDriverHonorific: String? = null,
)

sealed interface GeminiDispatchResult {
    data class Accepted(val command: GeminiAppCommand) : GeminiDispatchResult
    data class ConfirmationRequired(val command: GeminiAppCommand) : GeminiDispatchResult
    data class Rejected(val reason: String) : GeminiDispatchResult
    data object NothingPending : GeminiDispatchResult
}

object GeminiActionBridge {
    private val _commands = MutableSharedFlow<GeminiAppCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<GeminiAppCommand> = _commands.asSharedFlow()

    @Volatile
    private var latestSnapshot = GeminiVehicleSnapshot()

    private val _snapshotFlow = MutableStateFlow(latestSnapshot)
    val snapshotFlow: StateFlow<GeminiVehicleSnapshot> = _snapshotFlow.asStateFlow()

    @Volatile
    private var pendingCriticalCommand: GeminiAppCommand? = null

    private val supportedActions = setOf(
        "open_drive",
        "go_back",
        "open_fuel",
        "open_statistics",
        "open_weather",
        "open_prayer",
        "open_obd",
        "open_warning_history",
        "open_traffic_agent",
        "open_navigation",
        "open_navigation_settings",
        "open_google_navigation",
        "open_yandex_navigation",
        "google_maps_search",
        "yandex_maps_search",
        "google_maps_route",
        "yandex_maps_route",
        "open_clock",
        "open_settings",
        "open_appearance",
        "open_header_layout",
        "open_vehicle_systems",
        "open_vehicle_selection",
        "open_help",
        "open_speed_corridor",
        "show_route_slope",
        "hide_route_slope",
        "show_live_slope",
        "hide_live_slope",
        "show_live_slope_half",
        "hide_live_slope_half",
        "show_speedometer",
        "show_park_screen",
        "toggle_compass",
        "compass_on",
        "compass_off",
        "start_trip",
        "stop_trip",
        "start_speed_corridor",
        "stop_speed_corridor",
        "toggle_traffic_agent",
        "traffic_agent_on",
        "traffic_agent_off",
        "set_vehicle_mode",
        "set_vehicle",
        "select_driver",
        "navigate_home",
        "navigate_work",
        "navigate_destination",
        "set_home_address",
        "set_work_address",
        "traffic_command",
        "set_traffic_target",
        "open_youtube",
        "open_music",
        "play_music_query",
        "stop_music",
        "close_music",
        "open_radio",
        "stop_radio",
        "open_chatgpt",
        "open_chrome",
        "close_chrome",
        "close_chatgpt",
        "close_youtube",
        "return_to_ald_drive",
        "email_note",
        "play_web_media",
        "emergency_mode",
        "media_next",
        "media_previous",
        "media_play",
        "media_pause",
        "set_program_toggle",
        "set_program_value",
        "set_text_scale",
        "set_day_brightness",
        "set_night_brightness",
        "volume_up",
        "volume_down",
        "volume_mute",
        "volume_unmute",
        "set_volume_percent",
        "brightness_up",
        "brightness_down",
        "set_brightness_percent",
        "set_day_mode",
        "set_night_mode",
        "set_auto_appearance",
        "open_wifi_settings",
        "open_bluetooth_settings",
        "obd_connect",
        "obd_disconnect",
        "refresh_weather",
        "close_gemini",
        "reset_statistics",
        "reset_fuel_statistics",
        "reset_trip_distance",
    )

    private val criticalActions = setOf(
        "reset_statistics",
        "reset_fuel_statistics",
        "reset_trip_distance",
        "emergency_mode",
    )

    fun updateSnapshot(snapshot: GeminiVehicleSnapshot) {
        latestSnapshot = snapshot
        _snapshotFlow.value = snapshot
    }

    fun snapshot(): GeminiVehicleSnapshot = latestSnapshot

    fun normalizeActionName(actionRaw: String): String {
        val raw = actionRaw.trim()
            .lowercase(java.util.Locale("tr", "TR"))
            .replace('-', '_')
            .replace(' ', '_')
            .replace(Regex("_+"), "_")

        return when (raw) {
            "home", "main", "main_screen", "home_screen", "ana_ekran", "anasayfa", "ana_sayfa", "ana_sayfaya_dön", "ana_sayfaya_don", "ana_ekrana_dön", "ana_ekrana_don", "başlangıç", "baslangic", "open_home", "go_home" -> "open_drive"
            "back", "geri", "geri_git", "previous_screen", "close_screen", "bu_sayfayi_kapat", "bu_sayfayı_kapat", "sayfayi_kapat", "sayfayı_kapat" -> "go_back"
            "fuel", "yakıt", "yakit", "depo", "fuel_screen", "open_tank" -> "open_fuel"
            "stats", "statistics", "istatistik", "istatistikler", "trip_stats" -> "open_statistics"
            "weather", "hava", "hava_durumu", "weather_screen" -> "open_weather"
            "prayer", "namaz", "namaz_vakti", "prayer_times" -> "open_prayer"
            "obd", "obd_screen", "vehicle_data", "arac_verileri" -> "open_obd"
            "uyarilar", "uyarılar", "uyari_gecmisi", "uyarı_geçmişi", "warning_history", "alerts" -> "open_warning_history"
            "traffic", "trafik", "traffic_agent", "trafik_ajani" -> "open_traffic_agent"
            "navigation", "navigate", "nav", "route", "rota", "harita", "maps", "open_maps" -> "open_navigation"
            "navigation_settings", "nav_settings", "adres_ayarlari", "adres_ayarları", "navigasyon_ayarlari", "navigasyon_ayarları" -> "open_navigation_settings"
            "google_navigation", "google_maps", "google_haritalar", "google_navigasyon", "google_sec", "google_seç", "googleyi_sec", "googleyi_seç" -> "open_google_navigation"
            "yandex_navigation", "yandex_maps", "yandex_haritalar", "yandex_navigasyon", "yandex_sec", "yandex_seç", "yandexi_sec", "yandexi_seç" -> "open_yandex_navigation"
            "google_search", "google_maps_search", "google_haritalarda_ara" -> "google_maps_search"
            "yandex_search", "yandex_maps_search", "yandexte_ara" -> "yandex_maps_search"
            "google_route", "google_maps_route", "google_yol_tarifi" -> "google_maps_route"
            "yandex_route", "yandex_maps_route", "yandex_yol_tarifi" -> "yandex_maps_route"
            "clock", "saat", "analog_clock" -> "open_clock"
            "settings", "ayar", "ayarlar", "program_settings" -> "open_settings"
            "appearance", "theme", "tema", "görünüm", "gorunum", "display_settings" -> "open_appearance"
            "header", "header_layout", "ust_bar", "üst_bar" -> "open_header_layout"
            "vehicle_systems", "arac_sistemleri", "araç_sistemleri" -> "open_vehicle_systems"
            "vehicle_selection", "vehicle_store", "arac_deposu", "araç_deposu", "arac_secimi", "araç_seçimi" -> "open_vehicle_selection"
            "help", "yardim", "yardım" -> "open_help"
            "acil", "acil_durum", "emergency" -> "emergency_mode"
            "not_mail", "notu_mail_at", "eposta_not", "e_posta_not" -> "email_note"
            "web_media", "youtube_ara_çal", "youtube_ara_cal", "kurani_kerim_dinlet", "kuran_dinlet" -> "play_web_media"
            "speed_corridor", "average_speed", "ortalama_hiz", "ortalama_hız", "hiz_koridoru", "hız_koridoru" -> "open_speed_corridor"
            "compass", "pusula", "show_compass" -> "compass_on"
            "hide_compass" -> "compass_off"
            "trip_start", "start_journey", "yolculuk_baslat", "yolculuk_başlat" -> "start_trip"
            "trip_stop", "stop_journey", "yolculuk_bitir", "yolculuk_durdur" -> "stop_trip"
            "traffic_on", "trafik_ac", "trafik_aç", "enable_traffic" -> "traffic_agent_on"
            "traffic_off", "trafik_kapat", "trafik_durdur", "trafik_bitir", "trafik_ajani_kapat", "trafik_ajanini_kapat", "trafik_ajanını_kapat", "disable_traffic", "stop_traffic_agent" -> "traffic_agent_off"
            "canli_yukseklik_yarim", "canlı_yükseklik_yarım", "live_slope_half", "show_live_half", "yarim_ekran_canli_yukseklik", "yarım_ekran_canlı_yükseklik" -> "show_live_slope_half"
            "canli_yukseklik_yarim_kapat", "canlı_yükseklik_yarım_kapat", "hide_live_half", "yarim_ekrani_kapat", "yarım_ekranı_kapat" -> "hide_live_slope_half"
            "hiz_ekrani", "hız_ekranı", "hiz_kadrani", "hız_kadranı", "speedometer", "show_speed" -> "show_speedometer"
            "park_ekrani", "park_ekranı", "show_park" -> "show_park_screen"
            "traffic_target", "traffic_destination", "trafik_hedefi", "trafik_ajani_hedefi", "ajan_hedefi", "set_agent_target" -> "set_traffic_target"
            "home_address", "ev_adresi", "set_home", "ev_adresi_kaydet", "ev_adresi_gir" -> "set_home_address"
            "work_address", "is_adresi", "iş_adresi", "set_work", "is_adresi_kaydet", "iş_adresi_kaydet" -> "set_work_address"
            "youtube", "open_yt" -> "open_youtube"
            "music", "muzik", "müzik", "music_open" -> "open_music"
            "play_music_query", "song_play", "sarki_cal", "şarkı_çal", "parca_cal", "parça_çal" -> "play_music_query"
            "pause_music", "music_pause", "media_pause", "pause", "duraklat" -> "media_pause"
            "stop_media", "music_stop", "müziği_durdur", "muzigi_durdur" -> "stop_music"
            "close_music", "music_close", "muzik_kapat", "müzik_kapat" -> "close_music"
            "play", "resume", "resume_music", "devam_et", "oynat" -> "media_play"
            "radio", "radyo" -> "open_radio"
            "chatgpt", "open_chat_gpt" -> "open_chatgpt"
            "close_chatgpt", "chatgpt_kapat" -> "close_chatgpt"
            "browser", "tarayici", "tarayıcı" -> "open_chrome"
            "close_chrome", "chrome_kapat", "tarayiciyi_kapat", "tarayıcıyı_kapat" -> "close_chrome"
            "close_youtube", "youtube_kapat" -> "close_youtube"
            "ald_drive_don", "ald_drive_dön", "uygulamaya_don", "uygulamaya_dön", "return_to_app" -> "return_to_ald_drive"
            "mute", "sessiz", "sessize_al", "sesi_kapat" -> "volume_mute"
            "unmute", "sesi_ac", "sesi_aç", "sessizi_kapat" -> "volume_unmute"
            "volume_plus", "ses_artir", "ses_arttır", "sesi_artir", "sesi_arttır" -> "volume_up"
            "volume_minus", "ses_azalt", "sesi_azalt" -> "volume_down"
            "day_mode", "gunduz_modu", "gündüz_modu" -> "set_day_mode"
            "night_mode", "gece_modu" -> "set_night_mode"
            "auto_mode", "otomatik_gorunum", "otomatik_görünüm" -> "set_auto_appearance"
            "program_toggle", "setting_toggle", "ayar_ac_kapat", "ayar_aç_kapat" -> "set_program_toggle"
            "program_value", "setting_value", "ayar_degeri", "ayar_değeri" -> "set_program_value"
            "text_scale", "yazi_boyutu", "yazı_boyutu" -> "set_text_scale"
            "day_brightness", "gunduz_parlaklik", "gündüz_parlaklık" -> "set_day_brightness"
            "night_brightness", "gece_parlaklik", "gece_parlaklık" -> "set_night_brightness"
            "wifi", "wi_fi", "wifi_settings" -> "open_wifi_settings"
            "bluetooth", "bt", "bluetooth_settings" -> "open_bluetooth_settings"
            "connect_obd", "obd_on" -> "obd_connect"
            "disconnect_obd", "obd_off" -> "obd_disconnect"
            "weather_refresh", "hava_yenile" -> "refresh_weather"
            "close_live", "stop_gemini", "gemini_close", "gemini_kapat", "görüşmeyi_bitir", "gorusmeyi_bitir", "sohbeti_kapat", "sohbet_kapat", "konusmayi_kapat", "konuşmayı_kapat" -> "close_gemini"
            "reset_stats", "istatistik_sifirla", "istatistik_sıfırla" -> "reset_statistics"
            "reset_fuel", "yakıt_sifirla", "yakit_sifirla" -> "reset_fuel_statistics"
            "reset_trip", "yolculuk_sifirla", "yolculuk_sıfırla" -> "reset_trip_distance"
            else -> raw
        }
    }

    @Synchronized
    fun request(actionRaw: String, value: String?): GeminiDispatchResult {
        val action = normalizeActionName(actionRaw)
        if (action !in supportedActions) {
            return GeminiDispatchResult.Rejected("Desteklenmeyen ALD Drive işlemi: $action")
        }

        val command = GeminiAppCommand(action = action, value = value?.trim()?.takeIf { it.isNotBlank() })
        if (action in criticalActions) {
            if (action in setOf("reset_statistics", "reset_fuel_statistics", "reset_trip_distance") && latestSnapshot.speedKmh >= 5) {
                return GeminiDispatchResult.Rejected("Kritik silme/sıfırlama işlemleri araç hareket halindeyken kullanılamaz.")
            }
            pendingCriticalCommand = command
            return GeminiDispatchResult.ConfirmationRequired(command)
        }

        return if (_commands.tryEmit(command)) {
            GeminiDispatchResult.Accepted(command)
        } else {
            GeminiDispatchResult.Rejected("Komut kuyruğu şu anda dolu. Tekrar deneyin.")
        }
    }

    @Synchronized
    fun confirmCritical(): GeminiDispatchResult {
        val command = pendingCriticalCommand ?: return GeminiDispatchResult.NothingPending
        pendingCriticalCommand = null
        return if (_commands.tryEmit(command)) {
            GeminiDispatchResult.Accepted(command)
        } else {
            GeminiDispatchResult.Rejected("Onaylanan komut uygulamaya iletilemedi.")
        }
    }

    @Synchronized
    fun cancelCritical(): GeminiDispatchResult {
        val command = pendingCriticalCommand ?: return GeminiDispatchResult.NothingPending
        pendingCriticalCommand = null
        return GeminiDispatchResult.Rejected("${command.action} işlemi kullanıcı tarafından iptal edildi.")
    }

    @Synchronized
    fun clearPendingCritical() {
        pendingCriticalCommand = null
    }
}
