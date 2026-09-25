package com.aldanmaz.drivedashboard.ui.screen.drive

import android.app.Application
import android.location.Location
import android.location.Geocoder
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.compass.CompassSensorAccuracy as TrackerCompassAccuracy
import com.aldanmaz.drivedashboard.data.compass.CompassTracker
import com.aldanmaz.drivedashboard.data.compass.calculateQiblaBearingDegrees
import com.aldanmaz.drivedashboard.data.fuel.FuelPreferencesRepository
import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseHistoryRepository
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.trafficagent.AjanAyarlari
import com.aldanmaz.drivedashboard.data.fuel.FuelSettings
import com.aldanmaz.drivedashboard.data.fuel.calculateForDistance
import com.aldanmaz.drivedashboard.data.location.LocationTracker
import com.aldanmaz.drivedashboard.data.obd.ObdLiveData
import com.aldanmaz.drivedashboard.data.speedlimit.RoadSpeedLimitRepository
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignRepository
import com.aldanmaz.drivedashboard.data.traffic.TrafficAgentRepository
import com.aldanmaz.drivedashboard.data.traffic.TrafficAgentBackgroundService
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripEntity
import com.aldanmaz.drivedashboard.data.trip.TripRepository
import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalog
import com.aldanmaz.drivedashboard.data.vehicle.VehicleType
import com.aldanmaz.drivedashboard.data.vehicle.VehiclePreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.abs
import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private data class TripStopRecord(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class DriveDashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val PARK_ENTRY_DELAY_MS = 10_000L
        private const val PARK_EXIT_MIN_SPEED_KMH = 5
        private const val PARK_EXIT_REQUIRED_SAMPLES = 3

        // Akıllı hız uyarısı
        private const val DEFAULT_CAR_SPEED_LIMIT_KMH = 90
        private const val DEFAULT_CARAVAN_SPEED_LIMIT_KMH = 70
        private const val SPEED_WARNING_TOLERANCE_KMH = 3
        private const val SPEED_APPROACHING_MARGIN_KMH = 2
        private const val SPEED_VOICE_REARM_GAP_KMH = 20

        // 128: TomTom Freemium kotasını korumak için hız sınırı sorguları seyreltilir.
        // Araç dururken hiç sorgu yapılmaz. Normalde en az 500 m hareket aranır;
        // belirgin yön değişiminde 150 m sonra erken sorguya izin verilir.
        private const val ROAD_SPEED_LIMIT_MIN_SPEED_KMH = 5f
        private const val ROAD_SPEED_LIMIT_MIN_MOVE_METERS = 500f
        private const val ROAD_SPEED_LIMIT_TURN_MIN_MOVE_METERS = 150f
        private const val ROAD_SPEED_LIMIT_TURN_DEGREES = 35f
        private const val ROAD_SPEED_LIMIT_FORCE_REFRESH_MS = 10 * 60 * 1_000L
        private const val ROAD_SPEED_LIMIT_FORCE_REFRESH_MIN_MOVE_METERS = 100f
        private const val ROAD_SPEED_LIMIT_QUOTA_COOLDOWN_MS = 6 * 60 * 60 * 1_000L
        private const val SPEED_LIMIT_82_HOLD_MS = 3 * 60 * 1_000L

        // Gerçek TomTom sınırını kısa duraklamalarda hemen silme; 500 m tabanlı
        // yeni sorgu düzeninde eski 45 sn eşiği gereksiz varsayılan 90/70'e düşürüyordu.
        private const val ROAD_SPEED_LIMIT_MAX_AGE_MS = 12 * 60 * 1_000L

        // _21: yaklaşan levha/uyarı için Routing API de daha seyrek kullanılır.
        private const val UPCOMING_SIGN_REFRESH_MS = 30_000L
        private const val UPCOMING_SIGN_MIN_MOVE_METERS = 250f

        // Yolculuk mesafesi: GPS sapmalarını filtrelemek için temel eşikler.
        // LocationTracker'ın 500 ms güncelleme hızına dokunulmaz.
        private const val TRIP_MIN_SPEED_KMH = 5
        private const val TRIP_MAX_ACCURACY_METERS = 30f
        private const val TRIP_MAX_SEGMENT_METERS = 1_500f

        // Otomatik günlük yolculuk:
        // 500 ms GPS akışında 3 ardışık hareket örneği (~1.5 sn) ile başlat.
        // Park etmek yolculuğu BİTİRMEZ. Günlük sayaç yalnızca gece yarısında sıfırlanır.
        private const val TRIP_START_REQUIRED_SAMPLES = 3

        // Trafik Ajanı: normalde 2 dakika/2 km; yoğunlukta 60 saniye.
        private const val TRAFFIC_LOOK_AHEAD_KM = 20.0
        private const val TRAFFIC_CHECK_INTERVAL_MS = 2 * 60 * 1000L
        private const val TRAFFIC_DENSE_CHECK_INTERVAL_MS = 60 * 1000L
        private const val TRAFFIC_RETRY_INTERVAL_MS = 60 * 1000L
        private const val TRAFFIC_RECHECK_DISTANCE_METERS = 2_000f
    }

    private val appContext = application.applicationContext
    private val locationTracker = LocationTracker(application)
    private val vehiclePreferencesRepository =
        VehiclePreferencesRepository(application)
    private val compassTracker = CompassTracker(application)
    private val fuelRepository = FuelPreferencesRepository(application)
    private val fuelPurchaseHistoryRepository = FuelPurchaseHistoryRepository(application)
    private val roadSpeedLimitRepository = RoadSpeedLimitRepository(application)
    private val upcomingRoadSignRepository = UpcomingRoadSignRepository()
    private val trafficAgentRepository = TrafficAgentRepository()
    private val tripRepository = TripRepository(
        AldanmazDriveDatabase.getInstance(application).tripDao()
    )

    // Açılışta 10 saniye içinde seçim yapılmazsa arayüz de aynı sürücüyü seçer.
    // Böylece seçimden önce başlayan birkaç saniyelik hareket yanlışlıkla Mehmet'e yazılmaz.
    private var activeDriverId = "1"
    private var activeDriverName = "Mehmet"
    private var hasSelectedDriverSession = false
    private var tripStartLatitude: Double? = null
    private var tripStartLongitude: Double? = null

    fun setDriverSession(id: String, name: String) {
        activeDriverId = id
        activeDriverName = name
        hasSelectedDriverSession = true
    }

    /** 94: ViewModel configuration change boyunca yaşadığı için ekran döndürmede sürücü seçimini korur. */
    fun currentDriverSessionIdOrNull(): String? = if (hasSelectedDriverSession) activeDriverId else null


    // 131: Ortak veri-kaynağı kuralı. OBD canlı ve güncelse önceliklidir;
    // veri bayatlar/koparsa mevcut GPS/ALD hesaplarına otomatik geri dönülür.
    private var obdConnectedForPriority = false
    private var latestObdLiveDataForPriority = ObdLiveData()
    private var latestObdUpdateEpochMsForPriority: Long? = null

    private fun hasFreshObdData(now: Long = System.currentTimeMillis()): Boolean {
        val epoch = latestObdUpdateEpochMsForPriority ?: return false
        return obdConnectedForPriority && now - epoch in 0L..5_000L
    }

    fun updateObdPriorityData(isConnected: Boolean, liveData: ObdLiveData, lastUpdateEpochMs: Long?) {
        obdConnectedForPriority = isConnected
        latestObdLiveDataForPriority = liveData
        latestObdUpdateEpochMsForPriority = lastUpdateEpochMs
        val gps = _uiState.value.gpsSpeedKmh
        val offset = calibrationPrefs.getInt("speed_offset_kmh", 4).coerceIn(-10, 10)
        _uiState.value = _uiState.value.copy(
            speedKmh = if (gps == 0) 0 else (gps + offset).coerceAtLeast(0),
            speedDataSource = "GPS"
        )
        updateFuelValues()
    }

    private var restReminderSpoken = false
    private var lowFuelVoiceAnnounced = false

    private val _uiState = MutableStateFlow(
        DriveDashboardUiState(
            isLocationPermissionGranted =
                locationTracker.hasLocationPermission(),
            isCompassSensorAvailable = compassTracker.isAvailable,
            compassAccuracy =
                if (compassTracker.isAvailable) {
                    CompassAccuracy.UNRELIABLE
                } else {
                    CompassAccuracy.UNAVAILABLE
                }
        )
    )

    val uiState: StateFlow<DriveDashboardUiState> =
        _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var parkEntryJob: Job? = null
    private var parkTimerJob: Job? = null
    private var forceSpeedometerJob: Job? = null
    private var fuelSettingsJob: Job? = null
    private var roadSpeedLimitJob: Job? = null
    private var upcomingRoadSignJob: Job? = null
    private var tripTimerJob: Job? = null
    private var tripStopJob: Job? = null
    private var transientVehicleAlertJob: Job? = null
    private var parkStartedElapsedRealtime: Long? = null
    private var tripStartedElapsedRealtime: Long? = null
    private var tripTimerBaseDurationSeconds: Long = 0L
    private var tripMovementConfirmationSamples = 0
    private var movementConfirmationSamples = 0
    private var isCompassTracking = false
    private var latestFuelSettings = FuelSettings()

    private var latestRoadSpeedLimitKmh: Int? = null

    // Step 17 Trafik Ajanı; aynı merkezi Location akışını kullanır.
    private var trafficCheckJob: Job? = null
    private var lastTrafficCheckElapsedRealtime: Long = 0L
    private var lastTrafficCheckLocation: Location? = null
    private var latestLocation: Location? = null
    private val compassFallbackPrefs = application.getSharedPreferences("gps_compass_fallback", Context.MODE_PRIVATE)
    private var lastGpsHeadingDegrees: Float? =
        compassFallbackPrefs.getFloat("last_heading", Float.NaN).takeUnless { it.isNaN() }
    private var lastGpsHeadingPersistElapsedMs: Long = 0L
    private var previousSpeedLocation: Location? = null
    private var lowMotionSpeedSamples: Int = 0
    private var lastLocationFixElapsedNanos: Long = 0L
    private var lastLocationReceivedElapsedMs: Long = 0L
    private val trafficPrefs = application.getSharedPreferences("traffic_agent", Context.MODE_PRIVATE)
    private val calibrationPrefs = application.getSharedPreferences("drive_calibration", Context.MODE_PRIVATE)

    // Aynı merkezi GPS akışından yolculuk mesafesi toplamak için son güvenilir nokta.
    private var lastTripLocation: Location? = null
    private val tripStopRecords = mutableListOf<TripStopRecord>()
    private var uncommittedFuelDistanceKm: Double = 0.0
    private var lastSpeedCorridorLocation: Location? = null
    private val speedCorridorPrefs = application.getSharedPreferences("speed_corridor", Context.MODE_PRIVATE)
    private val liveElevationPrefs = application.getSharedPreferences("live_elevation_daily", Context.MODE_PRIVATE)
    private val dailyTripPrefs = application.getSharedPreferences("daily_trip_state", Context.MODE_PRIVATE)
    private val dailyDistancePrefs = application.getSharedPreferences("daily_distance_ledger", Context.MODE_PRIVATE)
    private var activeDailyKey: String = LocalDate.now().toString()
    private var tripStartAnnouncementDoneThisProcess = false

    // Step 15-16 canlı eğim: mevcut merkezi GPS akışını kullanır; ikinci GPS listener yoktur.
    private var lastSlopeLocation: Location? = null
    // Sesli eğim uyarısı için anlık GPS rakım sıçraması yerine yaklaşık 50 m'lik yol penceresi kullanılır.
    private var slopeAlertReferenceLocation: Location? = null
    private var lastSlopeAlertElapsedMs: Long = 0L
    private var lastSlopeAlertDirection: Int = 0
    private val slopeToneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
    private var latestRoadSpeedLimitReceivedElapsedRealtime = 0L
    private var speedLimit82HoldUntilElapsedRealtime = 0L
    private var lastRoadSpeedLimitQueryElapsedRealtime = 0L
    private var lastRoadSpeedLimitQueryLocation: Location? = null
    private var lastRoadSpeedLimitQueryBearing: Float? = null
    private var roadSpeedLimitQuotaBlockedUntilElapsedRealtime = 0L
    private var lastUpcomingSignQueryElapsedRealtime: Long = 0L
    private var lastUpcomingSignQueryLocation: Location? = null

    // Step 13: tüm sesli uyarılar tek merkezi yöneticiden geçer.
    private val voiceAlertManager = CentralVoiceAlertManager.getInstance(application)
    private var hasAnnouncedSpeedLimitExceeded = false

    init {
        restoreSpeedCorridor()
        restoreDailyLiveElevation()
        restoreDailyDistanceLedger()
        restoreDailyTripState()
        // 129: Eski sürümlerin oluşturduğu mükerrer kayıtları açılışta tekilleştir.
        viewModelScope.launch {
            tripRepository.removeDuplicateTrips()
            refreshTodayTotalDistance()
        }
        // Eski sade ALD trafik ajanı devre dışı. Tam Trafik Ajanı modülü kendi
        // TrafikTakipServisi ve TrafikDurumDeposu ile çalışır. Eski tercihi de temizle.
        trafficPrefs.edit().putBoolean("active", false).apply()
        observeFuelSettings()
        observeVehiclePreferences()
        viewModelScope.launch {
            while (true) {
                ensureDailyState()
                delay(30_000L)
            }
        }
        // Eski ALD uygulamasındaki GPS timeout davranışı: araç durduğunda GPS yeni fix
        // üretmeyi keserse son hız ekranda asılı kalmasın. 2.5 sn sonra sıfıra zorla.
        viewModelScope.launch {
            while (true) {
                delay(500L)
                val last = lastLocationReceivedElapsedMs
                if (last > 0L && SystemClock.elapsedRealtime() - last > 2_500L && _uiState.value.speedKmh != 0) {
                    _uiState.value = _uiState.value.copy(speedKmh = 0, gpsSpeedKmh = 0)
                    updateParkMode(speedKmh = 0)
                }
            }
        }
    }

    fun startLocationTracking() {
        if (!locationTracker.hasLocationPermission()) {
            _uiState.value = _uiState.value.copy(
                isLocationPermissionGranted = false,
                isGpsActive = false,
                locationStatus = "Konum izni bekleniyor"
            )
            return
        }

        if (_uiState.value.isTrafficAgentActive) {
            val app = getApplication<Application>()
            runCatching {
                ContextCompat.startForegroundService(
                    app, Intent(app, TrafficAgentBackgroundService::class.java)
                )
            }
        }

        if (locationJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(
            isLocationPermissionGranted = true,
            isGpsActive = true,
            locationStatus = "GPS bağlantısı kuruluyor"
        )

        locationJob = viewModelScope.launch {
            locationTracker
                .locationUpdates()
                .catch { error ->
                    cancelParkEntryCheck()

                    _uiState.value = _uiState.value.copy(
                        isGpsActive = false,
                        locationStatus =
                            "GPS hatası: ${error.localizedMessage ?: "Bilinmeyen hata"}"
                    )
                }
                .collect { location ->
                    updateLocation(location)
                }
        }
    }

    fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
        cancelParkEntryCheck()
        movementConfirmationSamples = 0
        tripMovementConfirmationSamples = 0
        cancelTripStopCheck()
        lastTripLocation = null

        _uiState.value = _uiState.value.copy(
            isGpsActive = false,
            locationStatus = "GPS duraklatıldı"
        )
    }

    fun startCompassTracking() {
        if (isCompassTracking) return

        isCompassTracking = true

        val gpsFallback = lastGpsHeadingDegrees
        _uiState.value = _uiState.value.copy(
            isCompassSensorAvailable = compassTracker.isAvailable || gpsFallback != null || _uiState.value.isGpsActive,
            compassHeadingDegrees = _uiState.value.compassHeadingDegrees ?: gpsFallback,
            compassAccuracy =
                if (compassTracker.isAvailable) CompassAccuracy.UNRELIABLE
                else if (gpsFallback != null) CompassAccuracy.LOW
                else CompassAccuracy.UNAVAILABLE
        )

        compassTracker.start { reading ->
            val sensorHeading = reading.headingDegrees
            val heading = sensorHeading ?: lastGpsHeadingDegrees
            _uiState.value = _uiState.value.copy(
                isCompassSensorAvailable = compassTracker.isAvailable || heading != null || _uiState.value.isGpsActive,
                compassHeadingDegrees = heading,
                compassAccuracy = if (sensorHeading != null) reading.accuracy.toUiAccuracy()
                    else if (heading != null) CompassAccuracy.LOW else CompassAccuracy.UNAVAILABLE,
                direction = heading?.let(::bearingToDirection) ?: _uiState.value.direction
            )
        }
    }

    fun stopCompassTracking() {
        if (!isCompassTracking) return

        compassTracker.stop()
        isCompassTracking = false
    }

    fun toggleCompassPanel() {
        _uiState.value = _uiState.value.copy(
            isCompassPanelVisible = !_uiState.value.isCompassPanelVisible
        )
    }

    fun onLocationPermissionResult(isGranted: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLocationPermissionGranted = isGranted,
            locationStatus = if (isGranted) {
                "GPS bağlantısı kuruluyor"
            } else {
                "Konum izni verilmedi"
            }
        )

        if (isGranted) {
            startLocationTracking()
        } else {
            stopLocationTracking()
            _uiState.value = _uiState.value.copy(
                locationStatus = "Konum izni verilmedi"
            )
        }
    }

    fun setVehicleMode(mode: String) {
        val normalizedMode =
            if (mode == "Karavan") {
                "Karavan"
            } else {
                "Otomobil"
            }

        if (_uiState.value.vehicleMode == normalizedMode) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                vehicleMode = normalizedMode
            )

        /*
         * Gerçek yol hız sınırı varsa aynı yol sınırı kullanılmaya devam eder.
         * Gerçek yol sınırı yoksa Otomobil/Karavan varsayılan sınırı
         * değişen moda göre hemen yeniden hesaplanır.
         */
        refreshSpeedWarningForCurrentSpeed()
    }

    fun setSelectedVehicle(vehicleId: String) {
        val selectedVehicle =
            VehicleCatalog.findById(vehicleId)
                ?: return

        val newVehicleMode =
            when (selectedVehicle.type) {
                VehicleType.CAR ->
                    "Otomobil"

                VehicleType.CARAVAN ->
                    "Karavan"
            }

        _uiState.value =
            _uiState.value.copy(
                selectedVehicleId = selectedVehicle.id,
                vehicleMode = newVehicleMode
            )

        refreshSpeedWarningForCurrentSpeed()
        updateFuelValues()

        viewModelScope.launch {
            vehiclePreferencesRepository
                .saveSelectedVehicle(
                    selectedVehicle.id
                )
        }
    }

    fun setCustomVehicleImageUri(
        vehicleType: VehicleType,
        uri: String?
    ) {
        val normalizedUri =
            uri?.trim()?.takeIf { it.isNotEmpty() }

        _uiState.value =
            when (vehicleType) {
                VehicleType.CAR ->
                    _uiState.value.copy(
                        customCarImageUri = normalizedUri
                    )

                VehicleType.CARAVAN ->
                    _uiState.value.copy(
                        customCaravanImageUri = normalizedUri
                    )
            }

        viewModelScope.launch {
            vehiclePreferencesRepository
                .saveCustomVehicleImageUri(
                    vehicleType = vehicleType,
                    uri = normalizedUri
                )
        }
    }

    private fun observeFuelSettings() {
        fuelSettingsJob?.cancel()

        fuelSettingsJob = viewModelScope.launch {
            fuelRepository.settings.collect { settings ->
                latestFuelSettings = settings
                updateFuelValues()
            }
        }
    }

    private fun updateFuelValues() {
        val activeConsumption =
            resolveActiveFuelConsumptionLPer100Km()

        val activeTankCapacity =
            if (latestFuelSettings.isConfigured && latestFuelSettings.tankCapacityLiters > 0.0) {
                latestFuelSettings.tankCapacityLiters
            } else {
                VehicleCatalog.findById(_uiState.value.selectedVehicleId)
                    ?.tankCapacityLiters
                    ?: latestFuelSettings.tankCapacityLiters
            }

        val activeFuelSettings =
            latestFuelSettings.copy(
                tankCapacityLiters =
                    activeTankCapacity,
                currentFuelLiters =
                    latestFuelSettings.currentFuelLiters
                        .coerceAtMost(activeTankCapacity),
                averageConsumptionLitersPer100Km =
                    activeConsumption
            )

        val calculation =
            activeFuelSettings.calculateForDistance(
                distanceKm = uncommittedFuelDistanceKm
            )
        val freshObdFuelPercent: Double? = null

        val costReferenceLiters =
            when {
                latestFuelSettings.totalPurchasedLiters > 0.0 ->
                    latestFuelSettings.totalPurchasedLiters

                // Eski deneme sürümlerinde "Depoyu doldur" ile maliyet kaydolup
                // alınan litre 0 kalabiliyordu. Eski veriyi tamamen 0 TL bırakmamak
                // için depo kapasitesini güvenli yaklaşık referans olarak kullan.
                latestFuelSettings.totalFuelCost > 0.0 && activeTankCapacity > 0.0 ->
                    activeTankCapacity

                else -> 0.0
            }

        val currentLiterPrice = latestFuelSettings.lastFuelPricePerLiter.takeIf { it > 0.0 }
            ?: if (costReferenceLiters > 0.0) latestFuelSettings.totalFuelCost / costReferenceLiters else 0.0
        val estimatedTripFuelLiters =
            (_uiState.value.tripDistanceKm * activeConsumption / 100.0).coerceAtLeast(0.0)
        val estimatedDailyFuelCost = estimatedTripFuelLiters * currentLiterPrice
        val estimatedDayFuelLiters =
            (_uiState.value.todayTotalDistanceKm * activeConsumption / 100.0).coerceAtLeast(0.0)
        val estimatedDayFuelCost = estimatedDayFuelLiters * currentLiterPrice

        val calculatedFuelPercentage =
            if (activeFuelSettings.isConfigured && activeFuelSettings.tankCapacityLiters > 0.0) {
                (calculation.remainingFuelLiters / activeFuelSettings.tankCapacityLiters * 100.0).coerceIn(0.0, 100.0)
            } else null
        val fuelPercentage = freshObdFuelPercent ?: calculatedFuelPercentage
        val effectiveRemainingFuelLiters = if (freshObdFuelPercent != null && activeTankCapacity > 0.0) {
            activeTankCapacity * freshObdFuelPercent / 100.0
        } else if (activeFuelSettings.isConfigured) calculation.remainingFuelLiters else null
        val effectiveRangeKm = if (effectiveRemainingFuelLiters != null && activeConsumption > 0.0) {
            effectiveRemainingFuelLiters / activeConsumption * 100.0
        } else null
        val effectiveIsLowFuel = when {
            freshObdFuelPercent != null && effectiveRemainingFuelLiters != null && activeFuelSettings.isConfigured ->
                effectiveRemainingFuelLiters <= activeFuelSettings.lowFuelThresholdLiters
            freshObdFuelPercent != null -> freshObdFuelPercent <= 18.0
            else -> calculation.isLowFuel
        }

        _uiState.value = _uiState.value.copy(
            isFuelConfigured = activeFuelSettings.isConfigured,
            remainingFuelLiters = effectiveRemainingFuelLiters,
            fuelPercentage = fuelPercentage,
            fuelDataSource = if (freshObdFuelPercent != null) "OBD" else "HESAP",
            estimatedFuelRangeKm = effectiveRangeKm,
            isLowFuel = effectiveIsLowFuel,
            tripEstimatedFuelConsumedLiters = estimatedTripFuelLiters,
            tripEstimatedFuelCost = estimatedDailyFuelCost,
            todayEstimatedFuelConsumedLiters = estimatedDayFuelLiters,
            todayEstimatedFuelCost = estimatedDayFuelCost
        )

        // Düşük yakıt uyarısının sabit cümlesi gerçek insan sesiyle çalınır.
        if (effectiveIsLowFuel) {
            if (!lowFuelVoiceAnnounced) {
                lowFuelVoiceAnnounced = true
                voiceAlertManager.playRecorded(
                    type = VoiceAlertType.FUEL,
                    priority = VoiceAlertPriority.HIGH,
                    clip = RecordedVoiceClip.FUEL_LOW,
                    cooldownKey = "fuel_low",
                    cooldownMs = 30L * 60L * 1000L
                )
            }
        } else {
            lowFuelVoiceAnnounced = false
        }
    }

    private fun resolveActiveFuelConsumptionLPer100Km(): Double {
        // _44: bütün LT/TL ve istatistik hesaplarının tek kaynağı Yakıt
        // ekranının ikinci sayfasında kullanıcının kaydettiği ortalama tüketimdir.
        return latestFuelSettings.averageConsumptionLitersPer100Km
    }

    private fun updateLocation(location: Location) {
        ensureDailyState()

        lastLocationReceivedElapsedMs = SystemClock.elapsedRealtime()
        val rawSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val previousFix = previousSpeedLocation
        val previousFixNanos = lastLocationFixElapsedNanos
        val movedMeters = previousFix?.distanceTo(location) ?: Float.MAX_VALUE
        val fixTimestampChanged = location.elapsedRealtimeNanos != previousFixNanos
        val dtSeconds = if (previousFixNanos > 0L && location.elapsedRealtimeNanos > previousFixNanos) {
            (location.elapsedRealtimeNanos - previousFixNanos) / 1_000_000_000.0
        } else 0.0
        val displacementSpeedKmh = if (dtSeconds > 0.15 && movedMeters.isFinite()) {
            (movedMeters / dtSeconds * 3.6).toFloat()
        } else rawSpeedKmh
        lastLocationFixElapsedNanos = location.elapsedRealtimeNanos
        previousSpeedLocation = Location(location)

        // Araç ekranlarında GPS'in 8-12 km/s hayalet hızını bastır.
        // Gerçek hareket varsa hem GPS speed hem koordinat değişimi birlikte bunu doğrular.
        val lowMotionCandidate = rawSpeedKmh < 16f && (
            !fixTimestampChanged ||
                movedMeters < 0.8f ||
                displacementSpeedKmh < 3.5f
            )
        lowMotionSpeedSamples = if (lowMotionCandidate) lowMotionSpeedSamples + 1 else 0
        val speedUncertaintyKmh = if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond * 3.6f
        } else 0f
        val uncertainLowSpeed = rawSpeedKmh < 16f && speedUncertaintyKmh > 0f && rawSpeedKmh <= speedUncertaintyKmh * 1.35f
        val safeSpeedKmh = when {
            rawSpeedKmh < 5.0f -> 0
            rawSpeedKmh > 250f -> _uiState.value.gpsSpeedKmh
            lowMotionSpeedSamples >= 2 -> 0
            uncertainLowSpeed && displacementSpeedKmh < 5f -> 0
            else -> rawSpeedKmh.toInt()
        }
        val speedDisplayOffsetKmh = calibrationPrefs.getInt("speed_offset_kmh", 4).coerceIn(-10, 10)
        val effectiveSpeedKmh = safeSpeedKmh
        val displaySpeedKmh = if (safeSpeedKmh == 0) 0 else (safeSpeedKmh + speedDisplayOffsetKmh).coerceAtLeast(0)

        updateAutomaticTripLifecycle(
            speedKmh = effectiveSpeedKmh
        )

        updateTripDistance(
            location = location,
            speedKmh = effectiveSpeedKmh
        )

        updateSpeedCorridor(location, effectiveSpeedKmh)

        requestRoadSpeedLimitIfNeeded(location, effectiveSpeedKmh)
        clearExpiredRoadSpeedLimitIfNeeded()

        // 129: TomTom gerçek yol limiti vermediyse ekranda 90/70 uydurma.
        // Levha "--" olur ve hız sınırı uyarısı üretilmez.
        val speedLimitKmh = latestRoadSpeedLimitKmh

        val warningState =
            if (speedLimitKmh == null) {
                SpeedWarningState.NORMAL
            } else {
                when {
                    effectiveSpeedKmh >= speedLimitKmh + SPEED_WARNING_TOLERANCE_KMH ->
                        SpeedWarningState.OVER_LIMIT

                    effectiveSpeedKmh >= speedLimitKmh - SPEED_APPROACHING_MARGIN_KMH ->
                        SpeedWarningState.APPROACHING

                    else ->
                        SpeedWarningState.NORMAL
                }
            }
        if (speedLimitKmh != null) {
            handleSpeedVoiceWarning(
                warningState = warningState,
                speedLimitKmh = speedLimitKmh,
                currentSpeedKmh = effectiveSpeedKmh
            )
        } else {
            hasAnnouncedSpeedLimitExceeded = false
        }

        val accuracy = if (location.hasAccuracy()) {
            location.accuracy.roundToInt()
        } else {
            null
        }

        val previousForHeading = latestLocation
        val derivedGpsHeading = when {
            location.hasBearing() && safeSpeedKmh >= 1 -> location.bearing
            previousForHeading != null && previousForHeading.distanceTo(location) >= 2f ->
                previousForHeading.bearingTo(location)
            else -> lastGpsHeadingDegrees
        }
        if (derivedGpsHeading != null) {
            lastGpsHeadingDegrees = derivedGpsHeading
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastGpsHeadingPersistElapsedMs >= 15_000L) {
                lastGpsHeadingPersistElapsedMs = nowElapsed
                compassFallbackPrefs.edit().putFloat("last_heading", derivedGpsHeading).apply()
            }
        }

        val sensorHeading = if (compassTracker.isAvailable) _uiState.value.compassHeadingDegrees else null
        val effectiveHeading = sensorHeading ?: derivedGpsHeading
        requestUpcomingRoadSignsIfNeeded(location, effectiveHeading)
        val gpsDirection = effectiveHeading?.let(::bearingToDirection) ?: _uiState.value.direction
        val direction = gpsDirection

        val altitudeOffsetMeters = calibrationPrefs.getInt("altitude_offset_m", -25).coerceIn(-100, 100)
        val altitude = if (location.hasAltitude()) {
            (location.altitude + altitudeOffsetMeters).roundToInt()
        } else {
            _uiState.value.altitudeMeters
        }

        val status = when {
            accuracy == null -> "GPS aktif"
            accuracy <= 15 -> "GPS güçlü • ±${accuracy} m"
            accuracy <= 40 -> "GPS aktif • ±${accuracy} m"
            else -> "GPS zayıf • ±${accuracy} m"
        }

        val altitudeForCompass =
            if (location.hasAltitude()) location.altitude + altitudeOffsetMeters else 0.0

        compassTracker.updateLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitudeForCompass
        )

        val qiblaBearing =
            calculateQiblaBearingDegrees(
                latitude = location.latitude,
                longitude = location.longitude
            )

        val calibratedLocation = Location(location).apply {
            if (hasAltitude()) this.altitude = location.altitude + altitudeOffsetMeters
        }
        updateLiveSlope(calibratedLocation)
        latestLocation = Location(location)
        checkTrafficAgentIfNeeded(location)

        _uiState.value = _uiState.value.copy(
            gpsSpeedKmh = safeSpeedKmh,
            speedKmh = displaySpeedKmh,
            speedDataSource = "GPS",
            speedLimitKmh = speedLimitKmh,
            speedLimitSource =
                if (latestRoadSpeedLimitKmh != null) {
                    SpeedLimitSource.ROAD
                } else {
                    SpeedLimitSource.DEFAULT
                },
            speedWarningToleranceKmh = SPEED_WARNING_TOLERANCE_KMH,
            speedWarningState = warningState,
            direction = direction,
            altitudeMeters = altitude,
            latitude = location.latitude,
            longitude = location.longitude,
            isLocationPermissionGranted = true,
            isGpsActive = true,
            gpsAccuracyMeters = accuracy,
            locationStatus = status,
            isCompassSensorAvailable = true,
            compassHeadingDegrees = if (compassTracker.isAvailable) _uiState.value.compassHeadingDegrees else effectiveHeading,
            compassAccuracy = if (compassTracker.isAvailable) _uiState.value.compassAccuracy
                else if (effectiveHeading != null) CompassAccuracy.LOW else CompassAccuracy.UNAVAILABLE,
            qiblaBearingDegrees = qiblaBearing,
            forceSpeedometerDisplay = if (effectiveSpeedKmh >= 5) false else _uiState.value.forceSpeedometerDisplay
        )

        updateParkMode(speedKmh = effectiveSpeedKmh)
    }

    fun toggleTrafficAgent() {
        val next = !_uiState.value.isTrafficAgentActive
        trafficPrefs.edit().putBoolean("active", next).apply()
        val app = getApplication<Application>()
        if (!next) {
            app.stopService(Intent(app, TrafficAgentBackgroundService::class.java))
            trafficCheckJob?.cancel()
            trafficCheckJob = null
            _uiState.value = _uiState.value.copy(
                isTrafficAgentActive = false,
                trafficAgentStatus = "Kapalı",
                trafficDelaySeconds = 0,
                trafficIncidentCount = 0,
                trafficSeverity = 0
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isTrafficAgentActive = true,
            trafficAgentStatus = "AJAN aktif • 20 km tarama hazırlanıyor"
        )
        runCatching {
            ContextCompat.startForegroundService(
                app, Intent(app, TrafficAgentBackgroundService::class.java)
            )
        }
        lastTrafficCheckElapsedRealtime = 0L
        latestLocation?.let { checkTrafficAgentIfNeeded(it, force = true) }
    }

    private fun checkTrafficAgentIfNeeded(location: Location, force: Boolean = false) {
        if (!_uiState.value.isTrafficAgentActive) return
        if (trafficCheckJob?.isActive == true) return

        // Araç dururken de AJAN açılabilsin. GPS yönü yoksa pusula yönünü kullan.
        val scanHeading = when {
            location.hasBearing() -> location.bearing
            _uiState.value.compassHeadingDegrees != null -> _uiState.value.compassHeadingDegrees!!
            lastGpsHeadingDegrees != null -> lastGpsHeadingDegrees!!
            else -> null
        }
        if (scanHeading == null) {
            _uiState.value = _uiState.value.copy(
                trafficAgentStatus = "AJAN aktif • yön bekleniyor"
            )
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsedEnough = now - lastTrafficCheckElapsedRealtime >= TRAFFIC_CHECK_INTERVAL_MS
        val movedEnough = lastTrafficCheckLocation?.distanceTo(location)?.let {
            it >= TRAFFIC_RECHECK_DISTANCE_METERS
        } ?: true
        if (!force && !elapsedEnough && !movedEnough) return

        trafficCheckJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(trafficAgentStatus = "Önümüzdeki 20 km taranıyor")
            val result = trafficAgentRepository.checkAhead(
                latitude = location.latitude,
                longitude = location.longitude,
                headingDegrees = scanHeading,
                lookAheadKm = TRAFFIC_LOOK_AHEAD_KM
            )

            if (result == null) {
                lastTrafficCheckElapsedRealtime = now - TRAFFIC_CHECK_INTERVAL_MS + TRAFFIC_RETRY_INTERVAL_MS
                _uiState.value = _uiState.value.copy(
                    trafficAgentStatus = "Trafik verisi alınamadı • yeniden denenecek"
                )
                return@launch
            }

            lastTrafficCheckElapsedRealtime = SystemClock.elapsedRealtime()
            lastTrafficCheckLocation = Location(location)
            val zoneText = listOf(
                "0-5:${result.first5DelaySeconds / 60}dk",
                "5-10:${result.fiveTo10DelaySeconds / 60}dk",
                "10-20:${result.tenTo20DelaySeconds / 60}dk"
            ).joinToString(" • ")
            val status = when {
                result.delaySeconds >= 600 -> "Yoğun trafik • +${result.delaySeconds / 60} dk • $zoneText"
                result.delaySeconds >= 120 -> "Trafik var • +${result.delaySeconds / 60} dk • $zoneText"
                result.delaySeconds > 0 -> "Hafif trafik • +${maxOf(1, result.delaySeconds / 60)} dk • $zoneText"
                result.incidentCount > 0 -> "${result.incidentCount} trafik olayı • $zoneText"
                else -> "Önümüzdeki 20 km normal"
            }
            _uiState.value = _uiState.value.copy(
                trafficAgentStatus = status,
                trafficDelaySeconds = result.delaySeconds,
                trafficIncidentCount = result.incidentCount,
                trafficSeverity = result.severity,
                trafficLastCheckedAtEpochMillis = System.currentTimeMillis()
            )

            if (result.delaySeconds >= 120 || result.incidentCount > 0) {
                val minutes = maxOf(1, result.delaySeconds / 60)
                val dynamicTrafficText = if (result.delaySeconds > 0) {
                    "Tahmini gecikme $minutes dakika."
                } else {
                    "${result.incidentCount} trafik olayı."
                }

                if (!AjanAyarlari.sesliUyariAcikMi(appContext)) {
                    GeminiLiveManager.getInstance(appContext).announceTraffic(
                        "Trafik Ajanı uyarısı. Sürücüye kısa ve sakin biçimde söyle: $dynamicTrafficText"
                    )
                } else {
                    voiceAlertManager.playRecorded(
                        type = VoiceAlertType.TRAFFIC,
                        priority = VoiceAlertPriority.HIGH,
                        clip = if (result.delaySeconds >= 120) {
                            RecordedVoiceClip.TRAFFIC_DELAY_WARNING
                        } else {
                            RecordedVoiceClip.TRAFFIC_WARNING
                        },
                        cooldownKey = "traffic_${result.severity}_${minutes}",
                        cooldownMs = TRAFFIC_CHECK_INTERVAL_MS,
                        dynamicTextAfter = dynamicTrafficText
                    )
                }
            }
        }
    }

    fun toggleRouteSlopePanel() {
        val next = !_uiState.value.isRouteSlopePanelVisible
        _uiState.value = _uiState.value.copy(
            isRouteSlopePanelVisible = next,
            isLiveSlopePanelVisible = if (next) false else _uiState.value.isLiveSlopePanelVisible,
            isLiveSlopeHalfVisible = if (next) false else _uiState.value.isLiveSlopeHalfVisible
        )
    }

    fun toggleLiveSlopePanel() {
        val next = !_uiState.value.isLiveSlopePanelVisible
        _uiState.value = _uiState.value.copy(
            isLiveSlopePanelVisible = next,
            isRouteSlopePanelVisible = if (next) false else _uiState.value.isRouteSlopePanelVisible,
            isLiveSlopeHalfVisible = if (next) false else _uiState.value.isLiveSlopeHalfVisible
        )
    }

    fun setLiveSlopeHalfVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLiveSlopeHalfVisible = visible,
            isLiveSlopePanelVisible = if (visible) false else _uiState.value.isLiveSlopePanelVisible,
            isRouteSlopePanelVisible = if (visible) false else _uiState.value.isRouteSlopePanelVisible
        )
    }

    fun showSpeedometerDisplay() {
        forceSpeedometerJob?.cancel()
        _uiState.value = _uiState.value.copy(forceSpeedometerDisplay = true)
        // 94: Parkta bile 0 km/h kadranı en az 5 saniye sabit kalsın.
        forceSpeedometerJob = viewModelScope.launch {
            delay(5_000L)
            if (_uiState.value.speedKmh < 5) {
                _uiState.value = _uiState.value.copy(forceSpeedometerDisplay = false)
            }
        }
    }

    fun showParkDisplay() {
        forceSpeedometerJob?.cancel()
        forceSpeedometerJob = null
        _uiState.value = _uiState.value.copy(forceSpeedometerDisplay = false)
    }

    private fun updateLiveSlope(location: Location) {
        val state = _uiState.value
        if (!location.hasAltitude()) {
            lastSlopeLocation = Location(location)
            return
        }
        val previous = lastSlopeLocation
        lastSlopeLocation = Location(location)
        if (previous == null || !previous.hasAltitude()) return
        val horizontalMeters = previous.distanceTo(location).toDouble()
        if (horizontalMeters < 3.0 || horizontalMeters > 120.0) return
        val deltaAltitude = location.altitude - previous.altitude
        val slope = (deltaAltitude / horizontalMeters * 100.0).coerceIn(-30.0, 30.0)
        val previousDistance = state.liveElevationDistanceKm.lastOrNull() ?: 0.0
        val newDistance = previousDistance + horizontalMeters / 1000.0
        val allElevations = state.liveElevationProfileMeters + location.altitude
        val allDistances = state.liveElevationDistanceKm + newDistance
        val keepFrom = allDistances.indexOfFirst { it >= newDistance - 5.0 }.let { if (it < 0) 0 else it }
        val profile = allElevations.drop(keepFrom).takeLast(240)
        val profileDistances = allDistances.drop(keepFrom).takeLast(240)
        val climb = state.totalClimbMeters + deltaAltitude.coerceAtLeast(0.0)
        val descent = state.totalDescentMeters + (-deltaAltitude).coerceAtLeast(0.0)
        _uiState.value = state.copy(
            currentSlopePercent = slope,
            totalClimbMeters = climb,
            totalDescentMeters = descent,
            liveElevationProfileMeters = profile,
            liveElevationDistanceKm = profileDistances
        )
        persistDailyLiveElevation()

        // EĞİM SESİ: düz yolda GPS rakım gürültüsünün ötmesini engelle.
        // Tehlikeli eğim en az 100 metrelik yol penceresinde doğrulanır.
        val alertReference = slopeAlertReferenceLocation
        if (alertReference == null || !alertReference.hasAltitude()) {
            slopeAlertReferenceLocation = Location(location)
            return
        }

        val alertDistanceMeters = alertReference.distanceTo(location).toDouble()
        if (alertDistanceMeters < 100.0) return
        if (alertDistanceMeters > 500.0) {
            // Uzun GPS kopması / sıçraması: eski referansı kullanma.
            slopeAlertReferenceLocation = Location(location)
            lastSlopeAlertDirection = 0
            return
        }

        val alertSlope = ((location.altitude - alertReference.altitude) / alertDistanceMeters * 100.0)
            .coerceIn(-20.0, 20.0)
        slopeAlertReferenceLocation = Location(location)

        // Konuşmalı eğim uyarıları kaldırıldı. Her iki yönde eşik mutlak %7'dir.
        val slopeDirection = when {
            alertSlope >= 7.0 -> 1
            alertSlope <= -7.0 -> -1
            else -> 0
        }
        val now = SystemClock.elapsedRealtime()

        if (slopeDirection == 0) {
            // Histerezis: yol %5'in altına dönünce yeni tehlikeli eğim tekrar uyarılabilir.
            if (kotlin.math.abs(alertSlope) < 5.0) lastSlopeAlertDirection = 0
            return
        }

        val directionChanged = slopeDirection != lastSlopeAlertDirection
        val cooldownFinished = now - lastSlopeAlertElapsedMs >= 90_000L
        if (directionChanged || cooldownFinished) {
            if (slopeDirection > 0) {
                playSlopeWarningTone(isUphill = true)
                showTransientVehicleAlert(
                    "RAMPA ${String.format(Locale.getDefault(), "%+.1f%%", alertSlope)}"
                )
            } else {
                playSlopeWarningTone(isUphill = false)
                showTransientVehicleAlert(
                    "İNİŞ ${String.format(Locale.getDefault(), "%+.1f%%", alertSlope)}"
                )
            }
            lastSlopeAlertDirection = slopeDirection
            lastSlopeAlertElapsedMs = now
        }
    }

    private fun playSlopeWarningTone(isUphill: Boolean) {
        if (!voiceAlertManager.isMasterEnabled()) return
        val handler = Handler(Looper.getMainLooper())
        if (isUphill) {
            handler.post { slopeToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 140) }
            handler.postDelayed({ slopeToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 140) }, 260L)
        } else {
            handler.post { slopeToneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 360) }
        }
    }

    private fun showTransientVehicleAlert(message: String, durationMs: Long = 4_000L) {
        transientVehicleAlertJob?.cancel()
        _uiState.value = _uiState.value.copy(transientVehicleAlertMessage = message)
        transientVehicleAlertJob = viewModelScope.launch {
            delay(durationMs)
            if (_uiState.value.transientVehicleAlertMessage == message) {
                _uiState.value = _uiState.value.copy(transientVehicleAlertMessage = null)
            }
        }
    }

    private fun ensureDailyState() {
        val today = LocalDate.now().toString()
        if (today == activeDailyKey) return
        activeDailyKey = today
        dailyDistancePrefs.edit()
            .clear()
            .putString("day", today)
            .putLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
            .apply()
        resetDailyTripAtMidnight()
        tripStartAnnouncementDoneThisProcess = false
        restReminderSpoken = false
        voiceAlertManager.resetDailyState()
        lastSlopeLocation = null
        slopeAlertReferenceLocation = null
        lastSlopeAlertDirection = 0
        _uiState.value = _uiState.value.copy(
            currentSlopePercent = 0.0,
            totalClimbMeters = 0.0,
            totalDescentMeters = 0.0,
            liveElevationProfileMeters = emptyList(),
            liveElevationDistanceKm = emptyList()
        )
        liveElevationPrefs.edit().clear().putString("day", activeDailyKey).apply()
    }

    /**
     * Günlük araç kilometresi aktif yolculuktan bağımsız tutulur. Uygulama yeniden
     * başlasa veya sürücü değişse bile ekrandaki gün toplamı geriye düşemez.
     */
    private fun restoreDailyDistanceLedger() {
        val today = LocalDate.now().toString()
        if (dailyDistancePrefs.getString("day", null) != today) {
            dailyDistancePrefs.edit().clear()
                .putString("day", today)
                .putLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
                .apply()
            return
        }
        val total = java.lang.Double.longBitsToDouble(
            dailyDistancePrefs.getLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
        ).coerceAtLeast(0.0)
        _uiState.value = _uiState.value.copy(todayTotalDistanceKm = total)
    }

    private fun addToDailyDistanceLedger(deltaKm: Double): Double {
        if (deltaKm <= 0.0) return _uiState.value.todayTotalDistanceKm
        val today = LocalDate.now().toString()
        if (today != activeDailyKey) ensureDailyState()
        val stored = if (dailyDistancePrefs.getString("day", null) == today) {
            java.lang.Double.longBitsToDouble(
                dailyDistancePrefs.getLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
            ).coerceAtLeast(0.0)
        } else 0.0
        val total = maxOf(stored, _uiState.value.todayTotalDistanceKm) + deltaKm
        dailyDistancePrefs.edit()
            .putString("day", today)
            .putLong("totalBits", java.lang.Double.doubleToRawLongBits(total))
            .apply()
        return total
    }

    private fun restoreDailyTripState() {
        val today = LocalDate.now().toString()
        if (dailyTripPrefs.getString("day", null) != today) {
            dailyTripPrefs.edit().clear().putString("day", today).apply()
            return
        }
        val startedAt = dailyTripPrefs.getLong("startedAt", 0L)
        if (startedAt <= 0L) return

        val restoredDuration = dailyTripPrefs.getLong("duration", 0L).coerceAtLeast(0L)
        val restoredMoving = dailyTripPrefs.getLong("moving", 0L).coerceIn(0L, restoredDuration)
        val distance = java.lang.Double.longBitsToDouble(
            dailyTripPrefs.getLong("distanceBits", java.lang.Double.doubleToRawLongBits(0.0))
        ).coerceAtLeast(0.0)
        val fuel = java.lang.Double.longBitsToDouble(
            dailyTripPrefs.getLong("fuelBits", java.lang.Double.doubleToRawLongBits(0.0))
        ).coerceAtLeast(0.0)
        val endedAt = dailyTripPrefs.getLong("lastPersistedAt", startedAt).coerceAtLeast(startedAt)
        val storedDriverId = dailyTripPrefs.getString("driverId", "1") ?: "1"
        val storedDriverName = dailyTripPrefs.getString("driverName", "Mehmet") ?: "Mehmet"
        val storedStartLatitude = dailyTripPrefs.takeIf { it.contains("startLatitudeBits") }?.let {
            java.lang.Double.longBitsToDouble(it.getLong("startLatitudeBits", 0L))
        }
        val storedStartLongitude = dailyTripPrefs.takeIf { it.contains("startLongitudeBits") }?.let {
            java.lang.Double.longBitsToDouble(it.getLong("startLongitudeBits", 0L))
        }
        val storedEndLatitude = dailyTripPrefs.takeIf { it.contains("lastLatitudeBits") }?.let {
            java.lang.Double.longBitsToDouble(it.getLong("lastLatitudeBits", 0L))
        }
        val storedEndLongitude = dailyTripPrefs.takeIf { it.contains("lastLongitudeBits") }?.let {
            java.lang.Double.longBitsToDouble(it.getLong("lastLongitudeBits", 0L))
        }

        // 50'den 51'e ilk geçişte günlük defter henüz yoksa eldeki aktif
        // mesafeyi başlangıç değeri yap; yükselmiş sayaç hiçbir zaman küçülmesin.
        if (distance > _uiState.value.todayTotalDistanceKm) {
            _uiState.value = _uiState.value.copy(todayTotalDistanceKm = distance)
            dailyDistancePrefs.edit()
                .putString("day", today)
                .putLong("totalBits", java.lang.Double.doubleToRawLongBits(distance))
                .apply()
        }

        // Önceki uygulama sürecinden kalan oturumu son güvenilir kayıt anında kapat.
        // Uygulamanın/ekranın kapalı kaldığı süre park süresine eklenmez.
        if (restoredDuration > 0L || distance > 0.0) {
            val restoredTrip = TripEntity(
                startedAtEpochMillis = startedAt,
                endedAtEpochMillis = endedAt,
                distanceKm = distance,
                totalDurationSeconds = restoredDuration,
                movingDurationSeconds = restoredMoving,
                parkDurationSeconds = (restoredDuration - restoredMoving).coerceAtLeast(0L),
                averageSpeedKmh = if (restoredMoving > 0L) distance / (restoredMoving / 3_600.0) else 0.0,
                maxSpeedKmh = dailyTripPrefs.getInt("maxSpeed", 0),
                vehicleMode = dailyTripPrefs.getString("vehicleMode", "Otomobil") ?: "Otomobil",
                vehicleId = dailyTripPrefs.getString("vehicleId", "car_sandero") ?: "car_sandero",
                estimatedFuelConsumedLiters = fuel,
                estimatedFuelCost = java.lang.Double.longBitsToDouble(
                    dailyTripPrefs.getLong("costBits", java.lang.Double.doubleToRawLongBits(0.0))
                ).coerceAtLeast(0.0),
                driverId = storedDriverId,
                driverName = storedDriverName,
                startLatitude = storedStartLatitude,
                startLongitude = storedStartLongitude,
                endLatitude = storedEndLatitude,
                endLongitude = storedEndLongitude,
                speed0To30Seconds = dailyTripPrefs.getLong("b0", 0L),
                speed31To50Seconds = dailyTripPrefs.getLong("b1", 0L),
                speed51To70Seconds = dailyTripPrefs.getLong("b2", 0L),
                speed71To90Seconds = dailyTripPrefs.getLong("b3", 0L),
                speed91To120Seconds = dailyTripPrefs.getLong("b4", 0L),
                speedOver120Seconds = dailyTripPrefs.getLong("b5", 0L)
            )
            viewModelScope.launch {
                val existingTrip = tripRepository.getTripByLogicalKey(
                    driverId = restoredTrip.driverId,
                    startedAtEpochMillis = restoredTrip.startedAtEpochMillis
                )
                val enrichedTrip = withContext(Dispatchers.IO) {
                    restoredTrip.copy(
                        startAddress = resolveAddress(restoredTrip.startLatitude, restoredTrip.startLongitude),
                        endAddress = resolveAddress(restoredTrip.endLatitude, restoredTrip.endLongitude)
                    )
                }
                tripRepository.saveTrip(enrichedTrip)

                // 129: Aynı restore tekrar çalışırsa yakıtı ikinci kez düşürme.
                val newlyCommittedDistance =
                    (distance - (existingTrip?.distanceKm ?: 0.0)).coerceAtLeast(0.0)
                if (newlyCommittedDistance > 0.0) {
                    fuelRepository.commitConsumedDistance(
                        distanceKm = newlyCommittedDistance,
                        averageConsumptionLitersPer100Km =
                            if (distance > 0.0) fuel * 100.0 / distance else null
                    )
                }
                refreshTodayTotalDistance()
            }
        }
        clearDailyTripState()
        _uiState.value = _uiState.value.copy(isTripActive = false, tripDistanceKm = 0.0)
    }

    private fun persistDailyTripState() {
        val s = _uiState.value
        if (!s.isTripActive || s.tripStartedAtEpochMillis == null) return
        val editor = dailyTripPrefs.edit()
            .putString("day", activeDailyKey)
            .putLong("startedAt", s.tripStartedAtEpochMillis)
            .putLong("distanceBits", java.lang.Double.doubleToRawLongBits(s.tripDistanceKm))
            .putLong("duration", s.tripDurationSeconds)
            .putLong("moving", s.tripMovingDurationSeconds)
            .putLong("avgBits", java.lang.Double.doubleToRawLongBits(s.tripAverageSpeedKmh))
            .putInt("maxSpeed", s.tripMaxSpeedKmh)
            .putLong("fuelBits", java.lang.Double.doubleToRawLongBits(s.tripEstimatedFuelConsumedLiters))
            .putLong("costBits", java.lang.Double.doubleToRawLongBits(s.tripEstimatedFuelCost))
            .putLong("lastPersistedAt", System.currentTimeMillis())
            .putString("driverId", activeDriverId)
            .putString("driverName", activeDriverName)
            .putString("vehicleMode", s.vehicleMode)
            .putString("vehicleId", s.selectedVehicleId)
            .putLong("b0", s.tripSpeed0To30Seconds)
            .putLong("b1", s.tripSpeed31To50Seconds)
            .putLong("b2", s.tripSpeed51To70Seconds)
            .putLong("b3", s.tripSpeed71To90Seconds)
            .putLong("b4", s.tripSpeed91To120Seconds)
            .putLong("b5", s.tripSpeedOver120Seconds)
        tripStartLatitude?.let {
            editor.putLong("startLatitudeBits", java.lang.Double.doubleToRawLongBits(it))
        } ?: editor.remove("startLatitudeBits")
        tripStartLongitude?.let {
            editor.putLong("startLongitudeBits", java.lang.Double.doubleToRawLongBits(it))
        } ?: editor.remove("startLongitudeBits")
        s.latitude?.let {
            editor.putLong("lastLatitudeBits", java.lang.Double.doubleToRawLongBits(it))
        } ?: editor.remove("lastLatitudeBits")
        s.longitude?.let {
            editor.putLong("lastLongitudeBits", java.lang.Double.doubleToRawLongBits(it))
        } ?: editor.remove("lastLongitudeBits")
        editor.apply()
    }

    private fun clearDailyTripState() {
        dailyTripPrefs.edit().clear().putString("day", activeDailyKey).apply()
    }

    private fun resetDailyTripAtMidnight() {
        val midnightState = _uiState.value
        val wasActive = midnightState.isTripActive
        val oldDistance = midnightState.tripDistanceKm
        val now = System.currentTimeMillis()
        if (wasActive && midnightState.tripStartedAtEpochMillis != null &&
            (midnightState.tripDurationSeconds > 0L || oldDistance > 0.0)
        ) {
            saveMidnightTripSegment(midnightState, now - 1L)
        }
        if (oldDistance > 0.0) {
            val consumption = resolveActiveFuelConsumptionLPer100Km().coerceAtLeast(0.0)
            viewModelScope.launch {
                fuelRepository.commitConsumedDistance(oldDistance, consumption)
            }
        }
        tripTimerJob?.cancel()
        tripTimerJob = null
        lastTripLocation = null
        uncommittedFuelDistanceKm = 0.0
        tripTimerBaseDurationSeconds = 0L
        tripStartedElapsedRealtime = if (wasActive) SystemClock.elapsedRealtime() else null
        _uiState.value = _uiState.value.copy(
            isTripActive = wasActive,
            tripDistanceKm = 0.0,
            tripDurationSeconds = 0L,
            tripMovingDurationSeconds = 0L,
            tripParkDurationSeconds = 0L,
            tripAverageSpeedKmh = 0.0,
            tripMaxSpeedKmh = 0,
            tripEstimatedFuelConsumedLiters = 0.0,
            tripEstimatedFuelCost = 0.0,
            tripSpeed0To30Seconds = 0L,
            tripSpeed31To50Seconds = 0L,
            tripSpeed51To70Seconds = 0L,
            tripSpeed71To90Seconds = 0L,
            tripSpeed91To120Seconds = 0L,
            tripSpeedOver120Seconds = 0L,
            todayTotalDistanceKm = 0.0,
            todayEstimatedFuelConsumedLiters = 0.0,
            todayEstimatedFuelCost = 0.0,
            tripStartedAtEpochMillis = if (wasActive) now else null,
            tripEndedAtEpochMillis = null
        )
        clearDailyTripState()
        if (wasActive) {
            tripStartLatitude = latestLocation?.latitude
            tripStartLongitude = latestLocation?.longitude
            tripStopRecords.clear()
            _uiState.value = _uiState.value.copy(
                tripStartLatitude = tripStartLatitude,
                tripStartLongitude = tripStartLongitude
            )
            persistDailyTripState()
            startTripTimer()
        }
    }

    /** Gece yarısını aşan aktif sürüşün önceki güne ait bölümünü kaybetmeden kapatır. */
    private fun saveMidnightTripSegment(state: DriveDashboardUiState, endedAtEpochMillis: Long) {
        val startedAt = state.tripStartedAtEpochMillis ?: return
        val consumption = resolveActiveFuelConsumptionLPer100Km().coerceAtLeast(0.0)
        val fuelLiters = state.tripDistanceKm * consumption / 100.0
        val fuelPrice = latestFuelSettings.lastFuelPricePerLiter.takeIf { it > 0.0 }
            ?: if (latestFuelSettings.totalPurchasedLiters > 0.0) {
                latestFuelSettings.totalFuelCost / latestFuelSettings.totalPurchasedLiters
            } else 0.0
        val endLatitude = latestLocation?.latitude ?: state.latitude
        val endLongitude = latestLocation?.longitude ?: state.longitude
        val completedStops = tripStopRecords.toList()
        val segment = TripEntity(
            startedAtEpochMillis = startedAt,
            endedAtEpochMillis = endedAtEpochMillis,
            distanceKm = state.tripDistanceKm,
            totalDurationSeconds = state.tripDurationSeconds,
            movingDurationSeconds = state.tripMovingDurationSeconds,
            parkDurationSeconds = state.tripParkDurationSeconds,
            averageSpeedKmh = state.tripAverageSpeedKmh,
            maxSpeedKmh = state.tripMaxSpeedKmh,
            vehicleMode = state.vehicleMode,
            vehicleId = state.selectedVehicleId,
            estimatedFuelConsumedLiters = fuelLiters,
            estimatedFuelCost = fuelLiters * fuelPrice,
            driverId = activeDriverId,
            driverName = activeDriverName,
            startLatitude = tripStartLatitude,
            startLongitude = tripStartLongitude,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
            speed0To30Seconds = state.tripSpeed0To30Seconds,
            speed31To50Seconds = state.tripSpeed31To50Seconds,
            speed51To70Seconds = state.tripSpeed51To70Seconds,
            speed71To90Seconds = state.tripSpeed71To90Seconds,
            speed91To120Seconds = state.tripSpeed91To120Seconds,
            speedOver120Seconds = state.tripSpeedOver120Seconds
        )
        viewModelScope.launch {
            val enriched = withContext(Dispatchers.IO) {
                val stops = JSONArray()
                completedStops.forEach { stop ->
                    stops.put(JSONObject().apply {
                        put("start", stop.startedAtEpochMillis)
                        put("end", stop.endedAtEpochMillis ?: endedAtEpochMillis)
                        put("lat", stop.latitude ?: JSONObject.NULL)
                        put("lon", stop.longitude ?: JSONObject.NULL)
                        put("address", resolveAddress(stop.latitude, stop.longitude))
                    })
                }
                segment.copy(
                    startAddress = resolveAddress(segment.startLatitude, segment.startLongitude),
                    endAddress = resolveAddress(segment.endLatitude, segment.endLongitude),
                    stopEventsJson = stops.toString()
                )
            }
            tripRepository.saveTrip(enriched)
            refreshTodayTotalDistance()
        }
    }

    private fun restoreDailyLiveElevation() {
        val today = LocalDate.now().toString()
        activeDailyKey = today
        if (liveElevationPrefs.getString("day", null) != today) {
            liveElevationPrefs.edit().clear().putString("day", today).apply()
            return
        }
        val rows = liveElevationPrefs.getString("points", "").orEmpty()
        val pairs = rows.split(';').mapNotNull { row ->
            val parts = row.split(',')
            val d = parts.getOrNull(0)?.toDoubleOrNull()
            val e = parts.getOrNull(1)?.toDoubleOrNull()
            if (d != null && e != null) d to e else null
        }
        if (pairs.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                liveElevationDistanceKm = pairs.map { it.first },
                liveElevationProfileMeters = pairs.map { it.second },
                totalClimbMeters = liveElevationPrefs.getFloat("climb", 0f).toDouble(),
                totalDescentMeters = liveElevationPrefs.getFloat("descent", 0f).toDouble()
            )
        }
    }

    private fun persistDailyLiveElevation() {
        val state = _uiState.value
        val points = state.liveElevationDistanceKm.zip(state.liveElevationProfileMeters)
            .joinToString(";") { (d, e) -> "${"%.5f".format(java.util.Locale.US, d)},${"%.1f".format(java.util.Locale.US, e)}" }
        liveElevationPrefs.edit()
            .putString("day", activeDailyKey)
            .putString("points", points)
            .putFloat("climb", state.totalClimbMeters.toFloat())
            .putFloat("descent", state.totalDescentMeters.toFloat())
            .apply()
    }

    private fun updateAutomaticTripLifecycle(
        speedKmh: Int
    ) {
        if (!_uiState.value.isTripActive) {
            cancelTripStopCheck()

            if (speedKmh >= TRIP_MIN_SPEED_KMH) {
                tripMovementConfirmationSamples += 1

                if (
                    tripMovementConfirmationSamples >=
                    TRIP_START_REQUIRED_SAMPLES
                ) {
                    tripMovementConfirmationSamples = 0
                    startTrip()
                }
            } else {
                tripMovementConfirmationSamples = 0
            }

            return
        }

        // Günlük yolculuk parkta kapanmaz. Park süresi ayrı sayaçta birikir;
        // hareket yeniden başladığında aynı yolculuk devam eder.
        tripMovementConfirmationSamples = 0
        cancelTripStopCheck()
    }

    private fun cancelTripStopCheck() {
        tripStopJob?.cancel()
        tripStopJob = null
    }

    private fun updateTripDistance(
        location: Location,
        speedKmh: Int
    ) {
        if (!_uiState.value.isTripActive) {
            lastTripLocation = null
            return
        }

        val hasGoodAccuracy =
            location.hasAccuracy() &&
                    location.accuracy <= TRIP_MAX_ACCURACY_METERS

        // Dururken veya GPS doğruluğu zayıfken mesafe toplamıyoruz.
        // Ayrıca sonraki hareket başlangıcında eski noktadan sıçrama oluşmaması
        // için referans noktayı sıfırlıyoruz.
        if (!hasGoodAccuracy) {
            return
        }

        // Dururken son güvenilir noktayı koru. Yeniden harekette ilk yol parçası kaybolmasın.
        if (speedKmh < TRIP_MIN_SPEED_KMH) {
            lastTripLocation = Location(location)
            return
        }

        val previousLocation =
            lastTripLocation

        lastTripLocation =
            Location(location)

        if (previousLocation == null) {
            return
        }

        val segmentMeters =
            previousLocation.distanceTo(location)

        // Tek GPS sıçramalarını yolculuk mesafesine katma.
        val elapsedSeconds = if (
            previousLocation.elapsedRealtimeNanos > 0L &&
            location.elapsedRealtimeNanos > previousLocation.elapsedRealtimeNanos
        ) (location.elapsedRealtimeNanos - previousLocation.elapsedRealtimeNanos) / 1_000_000_000.0 else 0.0
        val plausibleMeters = if (elapsedSeconds > 0.0) {
            ((maxOf(speedKmh, 30) / 3.6) * elapsedSeconds * 1.8 + 60.0)
                .coerceIn(120.0, TRIP_MAX_SEGMENT_METERS.toDouble())
        } else 120.0
        if (segmentMeters <= 0f || segmentMeters > plausibleMeters) {
            return
        }

        val deltaKm = segmentMeters / 1_000.0
        val updatedTripDistanceKm = _uiState.value.tripDistanceKm + deltaKm
        val updatedDailyDistanceKm = addToDailyDistanceLedger(deltaKm)

        uncommittedFuelDistanceKm =
            updatedTripDistanceKm

        val activeConsumptionLPer100Km =
            resolveActiveFuelConsumptionLPer100Km()
                .coerceAtLeast(0.0)

        val estimatedFuelConsumedLiters =
            updatedTripDistanceKm *
                    activeConsumptionLPer100Km /
                    100.0

        _uiState.value =
            _uiState.value.copy(
                tripDistanceKm = updatedTripDistanceKm,
                todayTotalDistanceKm = updatedDailyDistanceKm,
                tripEstimatedFuelConsumedLiters =
                    estimatedFuelConsumedLiters
            )

        // Yakıt kartındaki anlık kalan yakıt/menzil hesabı da
        // yolculuk mesafesiyle birlikte güncellensin. Kalıcı yakıt düşümü
        // yolculuk tamamlandığında ayrı adımda yapılacak.
        updateFuelValues()
    }

    fun startTrip() {
        if (_uiState.value.isTripActive) return

        cancelTripStopCheck()
        tripMovementConfirmationSamples = 0
        lastTripLocation = null
        tripStopRecords.clear()
        uncommittedFuelDistanceKm = 0.0
        tripStartedElapsedRealtime = SystemClock.elapsedRealtime()
        tripTimerBaseDurationSeconds = 0L
        tripStartLatitude = _uiState.value.latitude
        tripStartLongitude = _uiState.value.longitude

        _uiState.value =
            _uiState.value.copy(
                isTripActive = true,
                tripDistanceKm = 0.0,
                tripDurationSeconds = 0L,
                tripMovingDurationSeconds = 0L,
                tripParkDurationSeconds = 0L,
                tripAverageSpeedKmh = 0.0,
                tripMaxSpeedKmh = 0,
                tripEstimatedFuelConsumedLiters = 0.0,
                tripEstimatedFuelCost = 0.0,
            tripSpeed0To30Seconds = 0L,
                tripSpeed31To50Seconds = 0L,
                tripSpeed51To70Seconds = 0L,
                tripSpeed71To90Seconds = 0L,
                tripSpeed91To120Seconds = 0L,
                tripSpeedOver120Seconds = 0L,
                tripStartedAtEpochMillis = System.currentTimeMillis(),
                tripEndedAtEpochMillis = null,
                tripStartLatitude = tripStartLatitude,
                tripStartLongitude = tripStartLongitude
            )

        updateFuelValues()
        persistDailyTripState()
        startTripTimer()
        announceTripStartOnce()
    }

    private fun announceTripStartOnce() {
        ensureDailyState()
        if (tripStartAnnouncementDoneThisProcess) return
        val prefs = getApplication<Application>()
            .getSharedPreferences("drive_start_settings", Context.MODE_PRIVATE)
        val safetyEnabled = prefs.getBoolean("safety_enabled", true)
        val prayerEnabled = prefs.getBoolean("trip_prayer_enabled", true)
        if (!safetyEnabled && !prayerEnabled) return

        val clips = mutableListOf<RecordedVoiceClip>()
        // Kayıt sırası: önce besmele, ardından güvenli sürüş anonsu.
        if (prayerEnabled) {
            clips += RecordedVoiceClip.TRIP_PRAYER_BISMILLAH
        }
        if (safetyEnabled) {
            clips += RecordedVoiceClip.DRIVE_START_SAFETY
            showTransientVehicleAlert(
                "GÜVENLİ SÜRÜŞ • KEMERİNİZİ KONTROL EDİN",
                durationMs = 4_000L
            )
        }
        tripStartAnnouncementDoneThisProcess = true
        voiceAlertManager.playRecordedSequence(
            type = VoiceAlertType.TRIP_START,
            priority = VoiceAlertPriority.NORMAL,
            clips = clips,
            cooldownKey = "trip_start_$activeDailyKey",
            cooldownMs = 0L
        )
    }

    fun stopTrip() {
        if (!_uiState.value.isTripActive) return

        cancelTripStopCheck()
        tripMovementConfirmationSamples = 0

        // Sayaç yalnızca güncel GPS akışı varken ilerler. Burada duvar saati
        // farkını tekrar eklemek, ekranın kapalı kaldığı süreyi park sayardı.
        val completedDuration = _uiState.value.tripDurationSeconds

        tripTimerJob?.cancel()
        tripTimerJob = null
        tripStartedElapsedRealtime = null
        tripTimerBaseDurationSeconds = 0L
        lastTripLocation = null

        val stateBeforeCompletion = _uiState.value

        val completedMovingDuration =
            stateBeforeCompletion.tripMovingDurationSeconds
                .coerceAtMost(completedDuration)

        val completedParkDuration =
            (completedDuration - completedMovingDuration)
                .coerceAtLeast(0L)

        val completedAverageSpeed =
            if (completedMovingDuration > 0L) {
                stateBeforeCompletion.tripDistanceKm /
                        (completedMovingDuration / 3_600.0)
            } else {
                0.0
            }

        val endedAtEpochMillis =
            System.currentTimeMillis()

        if (tripStopRecords.lastOrNull()?.endedAtEpochMillis == null && tripStopRecords.isNotEmpty()) {
            val index = tripStopRecords.lastIndex
            tripStopRecords[index] = tripStopRecords[index].copy(endedAtEpochMillis = endedAtEpochMillis)
        }

        val startedAtEpochMillis =
            stateBeforeCompletion.tripStartedAtEpochMillis
                ?: endedAtEpochMillis

        val activeConsumptionLPer100Km =
            resolveActiveFuelConsumptionLPer100Km()
                .coerceAtLeast(0.0)

        val estimatedFuelConsumedLiters =
            stateBeforeCompletion.tripDistanceKm *
                    activeConsumptionLPer100Km /
                    100.0

        val completedTrip =
            TripEntity(
                startedAtEpochMillis = startedAtEpochMillis,
                endedAtEpochMillis = endedAtEpochMillis,
                distanceKm = stateBeforeCompletion.tripDistanceKm,
                totalDurationSeconds = completedDuration,
                movingDurationSeconds = completedMovingDuration,
                parkDurationSeconds = completedParkDuration,
                averageSpeedKmh = completedAverageSpeed,
                maxSpeedKmh = stateBeforeCompletion.tripMaxSpeedKmh,
                vehicleMode = stateBeforeCompletion.vehicleMode,
                vehicleId = stateBeforeCompletion.selectedVehicleId,
                estimatedFuelConsumedLiters = estimatedFuelConsumedLiters,
                estimatedFuelCost = estimatedFuelConsumedLiters * (
                    latestFuelSettings.lastFuelPricePerLiter.takeIf { it > 0.0 }
                        ?: if (latestFuelSettings.totalPurchasedLiters > 0.0) {
                            latestFuelSettings.totalFuelCost / latestFuelSettings.totalPurchasedLiters
                        } else 0.0
                    ),
                driverId = activeDriverId,
                driverName = activeDriverName,
                startLatitude = tripStartLatitude,
                startLongitude = tripStartLongitude,
                endLatitude = stateBeforeCompletion.latitude,
                endLongitude = stateBeforeCompletion.longitude,
                speed0To30Seconds = stateBeforeCompletion.tripSpeed0To30Seconds,
                speed31To50Seconds = stateBeforeCompletion.tripSpeed31To50Seconds,
                speed51To70Seconds = stateBeforeCompletion.tripSpeed51To70Seconds,
                speed71To90Seconds = stateBeforeCompletion.tripSpeed71To90Seconds,
                speed91To120Seconds = stateBeforeCompletion.tripSpeed91To120Seconds,
                speedOver120Seconds = stateBeforeCompletion.tripSpeedOver120Seconds
            )

        _uiState.value =
            stateBeforeCompletion.copy(
                isTripActive = false,
                tripDurationSeconds = completedDuration,
                tripMovingDurationSeconds = completedMovingDuration,
                tripParkDurationSeconds = completedParkDuration,
                tripAverageSpeedKmh = completedAverageSpeed,
                tripEstimatedFuelConsumedLiters =
                    estimatedFuelConsumedLiters,
                tripEstimatedFuelCost = completedTrip.estimatedFuelCost,
                tripEndedAtEpochMillis = endedAtEpochMillis
            )

        val completedDistanceKm =
            stateBeforeCompletion.tripDistanceKm
        val completedStops = tripStopRecords.toList()

        uncommittedFuelDistanceKm = 0.0

        viewModelScope.launch {
            val enrichedTrip = withContext(Dispatchers.IO) {
                val stops = JSONArray()
                completedStops.forEach { stop ->
                    stops.put(JSONObject().apply {
                        put("start", stop.startedAtEpochMillis)
                        put("end", stop.endedAtEpochMillis ?: endedAtEpochMillis)
                        put("lat", stop.latitude ?: JSONObject.NULL)
                        put("lon", stop.longitude ?: JSONObject.NULL)
                        put("address", resolveAddress(stop.latitude, stop.longitude))
                    })
                }
                completedTrip.copy(
                    startAddress = resolveAddress(completedTrip.startLatitude, completedTrip.startLongitude),
                    endAddress = resolveAddress(completedTrip.endLatitude, completedTrip.endLongitude),
                    stopEventsJson = stops.toString()
                )
            }
            val existingTrip = tripRepository.getTripByLogicalKey(
                driverId = enrichedTrip.driverId,
                startedAtEpochMillis = enrichedTrip.startedAtEpochMillis
            )
            tripRepository.saveTrip(enrichedTrip)

            // Restore edilmiş aynı sürüş varsa yalnız eklenen mesafenin yakıtını düş.
            val newlyCommittedDistance =
                (completedDistanceKm - (existingTrip?.distanceKm ?: 0.0)).coerceAtLeast(0.0)
            if (newlyCommittedDistance > 0.0) {
                fuelRepository.commitConsumedDistance(
                    distanceKm = newlyCommittedDistance,
                    averageConsumptionLitersPer100Km = activeConsumptionLPer100Km
                )
            }

            refreshTodayTotalDistance()
        }

        clearDailyTripState()
        tripStartLatitude = null
        tripStartLongitude = null

        voiceAlertManager.playRecorded(
            type = VoiceAlertType.TRIP_START,
            priority = VoiceAlertPriority.NORMAL,
            clip = RecordedVoiceClip.TRIP_FINISHED,
            cooldownKey = "trip_finished_${endedAtEpochMillis}",
            cooldownMs = 0L
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveAddress(latitude: Double?, longitude: Double?): String {
        if (latitude == null || longitude == null) return "Konum alınamadı"
        return runCatching {
            Geocoder(getApplication<Application>(), Locale("tr", "TR"))
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    listOfNotNull(address.thoroughfare, address.subLocality, address.locality)
                        .distinct().joinToString(", ").ifBlank { address.getAddressLine(0) }
                }
        }.getOrNull().orEmpty().ifBlank { "%.5f, %.5f".format(Locale.US, latitude, longitude) }
    }

    private fun refreshTodayTotalDistance() {
        viewModelScope.launch {
            val completedDistanceKm = tripRepository.getTodayTotalDistanceKm().coerceAtLeast(0.0)
            val current = _uiState.value
            val databaseAndActive = completedDistanceKm + if (current.isTripActive) current.tripDistanceKm else 0.0
            // Room + aktif sürüş tek doğruluk kaynağıdır. Eski/yanlış günlük defter
            // değeri maxOf ile sonsuza kadar korunmaz.
            val totalDistanceKm = databaseAndActive.coerceAtLeast(0.0)

            dailyDistancePrefs.edit()
                .putString("day", activeDailyKey)
                .putLong("totalBits", java.lang.Double.doubleToRawLongBits(totalDistanceKm))
                .apply()

            _uiState.value =
                _uiState.value.copy(
                    todayTotalDistanceKm =
                        totalDistanceKm
                )
            updateFuelValues()
        }
    }

    /**
     * 129: Yakıt veya yolculuk istatistiklerinden "sıfırla" denildiğinde
     * istatistik üreten tüm günlük/kayıtlı kaynakları tek işlemde temizler.
     * Depo kapasitesi, yakıt türü, tüketim ve mevcut depo seviyesi AYAR olarak korunur.
     */
    fun resetAllStatistics(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            tripTimerJob?.cancel()
            tripTimerJob = null
            tripStopJob?.cancel()
            tripStopJob = null
            tripStartedElapsedRealtime = null
            tripTimerBaseDurationSeconds = 0L
            tripMovementConfirmationSamples = 0
            lastTripLocation = null
            tripStartLatitude = null
            tripStartLongitude = null
            tripStopRecords.clear()
            uncommittedFuelDistanceKm = 0.0

            tripRepository.clearAllTrips()
            fuelRepository.resetFuelStatistics()
            fuelPurchaseHistoryRepository.clearAll()

            val today = LocalDate.now().toString()
            activeDailyKey = today
            dailyTripPrefs.edit().clear().putString("day", today).apply()
            dailyDistancePrefs.edit()
                .clear()
                .putString("day", today)
                .putLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
                .apply()

            _uiState.value = _uiState.value.copy(
                isTripActive = false,
                tripDistanceKm = 0.0,
                tripDurationSeconds = 0L,
                tripMovingDurationSeconds = 0L,
                tripParkDurationSeconds = 0L,
                tripAverageSpeedKmh = 0.0,
                tripMaxSpeedKmh = 0,
                tripEstimatedFuelConsumedLiters = 0.0,
                tripEstimatedFuelCost = 0.0,
                tripSpeed0To30Seconds = 0L,
                tripSpeed31To50Seconds = 0L,
                tripSpeed51To70Seconds = 0L,
                tripSpeed71To90Seconds = 0L,
                tripSpeed91To120Seconds = 0L,
                tripSpeedOver120Seconds = 0L,
                todayTotalDistanceKm = 0.0,
                todayEstimatedFuelConsumedLiters = 0.0,
                todayEstimatedFuelCost = 0.0,
                tripStartedAtEpochMillis = null,
                tripEndedAtEpochMillis = null,
                tripStartLatitude = null,
                tripStartLongitude = null
            )
            updateFuelValues()
            onComplete()
        }
    }

    fun resetTripDistance() {
        if (_uiState.value.isTripActive) return

        lastTripLocation = null
        tripStartedElapsedRealtime = null
        uncommittedFuelDistanceKm = 0.0

        _uiState.value =
            _uiState.value.copy(
                tripDistanceKm = 0.0,
                tripDurationSeconds = 0L,
                tripMovingDurationSeconds = 0L,
                tripParkDurationSeconds = 0L,
                tripAverageSpeedKmh = 0.0,
                tripMaxSpeedKmh = 0,
                tripEstimatedFuelConsumedLiters = 0.0,
                tripEstimatedFuelCost = 0.0,
            tripSpeed0To30Seconds = 0L,
                tripSpeed31To50Seconds = 0L,
                tripSpeed51To70Seconds = 0L,
                tripSpeed71To90Seconds = 0L,
                tripSpeed91To120Seconds = 0L,
                tripSpeedOver120Seconds = 0L,
                tripStartedAtEpochMillis = null,
                tripEndedAtEpochMillis = null
            )

        clearDailyTripState()
        updateFuelValues()
    }

    private fun startTripTimer() {
        tripTimerJob?.cancel()

        tripTimerJob = viewModelScope.launch {
            var lastTickElapsedRealtime = SystemClock.elapsedRealtime()

            while (_uiState.value.isTripActive) {
                delay(1_000L)
                val nowElapsedRealtime = SystemClock.elapsedRealtime()
                val rawDeltaSeconds =
                    ((nowElapsedRealtime - lastTickElapsedRealtime) / 1_000L).coerceIn(0L, 2L)
                lastTickElapsedRealtime = nowElapsedRealtime
                val currentState = _uiState.value
                // GPS akışı durmuşsa uygulama/ekran kapalı kabul edilir. Aradaki
                // saatler park süresine yazılmaz; sonradan tek seferde de eklenmez.
                val hasFreshLocation = lastLocationReceivedElapsedMs > 0L &&
                    nowElapsedRealtime - lastLocationReceivedElapsedMs <= 10_000L
                val elapsedDeltaSeconds = if (hasFreshLocation) rawDeltaSeconds else 0L
                val elapsedSeconds = currentState.tripDurationSeconds + elapsedDeltaSeconds

                val isMoving =
                    currentState.gpsSpeedKmh >= TRIP_MIN_SPEED_KMH

                val movingDuration =
                    if (isMoving) {
                        currentState.tripMovingDurationSeconds +
                                elapsedDeltaSeconds
                    } else {
                        currentState.tripMovingDurationSeconds
                    }
                        .coerceAtMost(elapsedSeconds)

                val parkDuration =
                    (elapsedSeconds - movingDuration)
                        .coerceAtLeast(0L)

                val averageSpeed =
                    if (movingDuration > 0L) {
                        currentState.tripDistanceKm /
                                (movingDuration / 3_600.0)
                    } else {
                        0.0
                    }

                val maxSpeed =
                    maxOf(
                        currentState.tripMaxSpeedKmh,
                        currentState.gpsSpeedKmh
                    )

                val bucketDelta = if (isMoving) elapsedDeltaSeconds else 0L

                _uiState.value =
                    currentState.copy(
                        tripDurationSeconds = elapsedSeconds,
                        tripMovingDurationSeconds = movingDuration,
                        tripParkDurationSeconds = parkDuration,
                        tripAverageSpeedKmh = averageSpeed,
                        tripMaxSpeedKmh = maxSpeed,
                        tripSpeed0To30Seconds = currentState.tripSpeed0To30Seconds + if (currentState.gpsSpeedKmh in 5..30) bucketDelta else 0L,
                        tripSpeed31To50Seconds = currentState.tripSpeed31To50Seconds + if (currentState.gpsSpeedKmh in 31..50) bucketDelta else 0L,
                        tripSpeed51To70Seconds = currentState.tripSpeed51To70Seconds + if (currentState.gpsSpeedKmh in 51..70) bucketDelta else 0L,
                        tripSpeed71To90Seconds = currentState.tripSpeed71To90Seconds + if (currentState.gpsSpeedKmh in 71..90) bucketDelta else 0L,
                        tripSpeed91To120Seconds = currentState.tripSpeed91To120Seconds + if (currentState.gpsSpeedKmh in 91..120) bucketDelta else 0L,
                        tripSpeedOver120Seconds = currentState.tripSpeedOver120Seconds + if (currentState.gpsSpeedKmh > 120) bucketDelta else 0L
                    )

                persistDailyTripState()
                maybeAnnounceRestReminder(movingDuration)

            }
        }
    }

    private fun maybeAnnounceRestReminder(movingDurationSeconds: Long) {
        if (restReminderSpoken) return
        val prefs = getApplication<Application>().getSharedPreferences("drive_rest_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return
        val minutes = prefs.getInt("reminder_minutes", 120).coerceIn(60, 240)
        if (movingDurationSeconds < minutes * 60L) return
        restReminderSpoken = true
        showTransientVehicleAlert("MOLA ÖNERİSİ • $minutes DK SÜRÜŞ")
        voiceAlertManager.playRecorded(
            type = VoiceAlertType.TRIP_START,
            priority = VoiceAlertPriority.NORMAL,
            clip = RecordedVoiceClip.REST_REMINDER,
            cooldownKey = "rest_reminder_${System.currentTimeMillis()}",
            cooldownMs = 0L
        )
    }

    private fun updateParkMode(speedKmh: Int) {
        if (_uiState.value.isParkMode) {
            cancelParkEntryCheck()

            if (speedKmh >= PARK_EXIT_MIN_SPEED_KMH) {
                movementConfirmationSamples += 1

                if (
                    movementConfirmationSamples >=
                    PARK_EXIT_REQUIRED_SAMPLES
                ) {
                    exitParkMode()
                }
            } else {
                movementConfirmationSamples = 0
            }

            return
        }

        movementConfirmationSamples = 0

        if (speedKmh == 0) {
            startParkEntryCheck()
        } else {
            cancelParkEntryCheck()
        }
    }

    private fun startParkEntryCheck() {
        if (parkEntryJob?.isActive == true) return

        parkEntryJob = viewModelScope.launch {
            delay(PARK_ENTRY_DELAY_MS)

            val canEnterParkMode =
                _uiState.value.gpsSpeedKmh == 0 &&
                        !_uiState.value.isParkMode &&
                        locationJob?.isActive == true

            if (canEnterParkMode) {
                enterParkMode()
            }

            parkEntryJob = null
        }
    }

    private fun cancelParkEntryCheck() {
        parkEntryJob?.cancel()
        parkEntryJob = null
    }

    private fun enterParkMode() {
        if (_uiState.value.isParkMode) return

        parkStartedElapsedRealtime = SystemClock.elapsedRealtime()
        movementConfirmationSamples = 0

        if (_uiState.value.isTripActive &&
            (tripStopRecords.isEmpty() || tripStopRecords.lastOrNull()?.endedAtEpochMillis != null)
        ) {
            tripStopRecords += TripStopRecord(
                startedAtEpochMillis = System.currentTimeMillis(),
                latitude = _uiState.value.latitude,
                longitude = _uiState.value.longitude
            )
        }

        _uiState.value = _uiState.value.copy(
            isParkMode = true,
            parkDurationSeconds = 0L
        )

        startParkTimer()
    }

    private fun startParkTimer() {
        parkTimerJob?.cancel()

        parkTimerJob = viewModelScope.launch {
            while (true) {
                val startedAt =
                    parkStartedElapsedRealtime ?: break

                if (!_uiState.value.isParkMode) break

                val elapsedSeconds =
                    (SystemClock.elapsedRealtime() - startedAt) /
                            1_000L

                _uiState.value = _uiState.value.copy(
                    parkDurationSeconds = elapsedSeconds
                )

                delay(1_000L)
            }
        }
    }

    private fun exitParkMode() {
        if (!_uiState.value.isParkMode) return

        val startedAt = parkStartedElapsedRealtime

        val completedDuration = if (startedAt != null) {
            maxOf(
                _uiState.value.parkDurationSeconds,
                (SystemClock.elapsedRealtime() - startedAt) /
                        1_000L
            )
        } else {
            _uiState.value.parkDurationSeconds
        }

        if (_uiState.value.isTripActive && tripStopRecords.lastOrNull()?.endedAtEpochMillis == null) {
            val index = tripStopRecords.lastIndex
            if (index >= 0) tripStopRecords[index] = tripStopRecords[index].copy(endedAtEpochMillis = System.currentTimeMillis())
        }

        parkTimerJob?.cancel()
        parkTimerJob = null
        parkStartedElapsedRealtime = null
        movementConfirmationSamples = 0

        _uiState.value = _uiState.value.copy(
            isParkMode = false,
            parkDurationSeconds = 0L,
            lastParkDurationSeconds = completedDuration,
            totalParkDurationSeconds =
                _uiState.value.totalParkDurationSeconds +
                        completedDuration
        )
    }

    private fun requestRoadSpeedLimitIfNeeded(
        location: Location,
        effectiveVehicleSpeedKmh: Int,
    ) {
        if (roadSpeedLimitJob?.isActive == true) return

        val speedKmh = effectiveVehicleSpeedKmh.toFloat()
        if (speedKmh < ROAD_SPEED_LIMIT_MIN_SPEED_KMH) return

        val now = SystemClock.elapsedRealtime()
        if (now < roadSpeedLimitQuotaBlockedUntilElapsedRealtime) return

        val previousQueryLocation = lastRoadSpeedLimitQueryLocation
        val movedMeters = previousQueryLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val timeSinceLastQuery = now - lastRoadSpeedLimitQueryElapsedRealtime

        val currentBearing = location.bearing.takeIf { location.hasBearing() }
        val previousBearing = lastRoadSpeedLimitQueryBearing
        val bearingDelta = if (currentBearing != null && previousBearing != null) {
            abs(((currentBearing - previousBearing + 540f) % 360f) - 180f)
        } else {
            0f
        }

        val firstQuery = previousQueryLocation == null
        val movedEnough = movedMeters >= ROAD_SPEED_LIMIT_MIN_MOVE_METERS
        val turnedEnough =
            movedMeters >= ROAD_SPEED_LIMIT_TURN_MIN_MOVE_METERS &&
                bearingDelta >= ROAD_SPEED_LIMIT_TURN_DEGREES
        val forcedRefresh =
            timeSinceLastQuery >= ROAD_SPEED_LIMIT_FORCE_REFRESH_MS &&
                movedMeters >= ROAD_SPEED_LIMIT_FORCE_REFRESH_MIN_MOVE_METERS

        if (!firstQuery && !movedEnough && !turnedEnough && !forcedRefresh) return

        lastRoadSpeedLimitQueryElapsedRealtime = now
        lastRoadSpeedLimitQueryLocation = Location(location)
        lastRoadSpeedLimitQueryBearing = currentBearing ?: previousBearing

        roadSpeedLimitJob =
            viewModelScope.launch {
                val result =
                    roadSpeedLimitRepository.getSpeedLimit(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        headingDegrees = currentBearing
                    )

                if (roadSpeedLimitRepository.lastRequestQuotaBlocked) {
                    roadSpeedLimitQuotaBlockedUntilElapsedRealtime =
                        SystemClock.elapsedRealtime() + ROAD_SPEED_LIMIT_QUOTA_COOLDOWN_MS
                    roadSpeedLimitJob = null
                    return@launch
                }

                val receivedLimit =
                    result
                        ?.speedLimitKmh
                        ?.takeIf { it in 10..150 }

                if (receivedLimit != null) {
                    val receivedAt = SystemClock.elapsedRealtime()
                    val previousLimit = latestRoadSpeedLimitKmh

                    // 82 km/s yol seçildikten hemen sonra paralel/yan yolun düşük
                    // sınırını ana yol sınırı sanma. Üç dakika boyunca düşük
                    // sonuçları hem ekrandan hem ses kuyruğundan dışarıda tut.
                    if (
                        previousLimit == 82 &&
                        receivedLimit < 82 &&
                        receivedAt < speedLimit82HoldUntilElapsedRealtime
                    ) {
                        roadSpeedLimitJob = null
                        return@launch
                    }

                    latestRoadSpeedLimitKmh = receivedLimit

                    if (receivedLimit == 82) {
                        speedLimit82HoldUntilElapsedRealtime =
                            receivedAt + SPEED_LIMIT_82_HOLD_MS
                    }

                    latestRoadSpeedLimitReceivedElapsedRealtime = receivedAt

                    refreshSpeedWarningForCurrentSpeed()
                    updateFuelValues()

                    if (previousLimit != receivedLimit && canAnnounceDrivingSpeed()) {
                        val recordedLimitClip = when (receivedLimit) {
                            20 -> RecordedVoiceClip.SPEED_LIMIT_20
                            30 -> RecordedVoiceClip.SPEED_LIMIT_30
                            40 -> RecordedVoiceClip.SPEED_LIMIT_40
                            50 -> RecordedVoiceClip.SPEED_LIMIT_50
                            60 -> RecordedVoiceClip.SPEED_LIMIT_60
                            70 -> RecordedVoiceClip.SPEED_LIMIT_70
                            80 -> RecordedVoiceClip.SPEED_LIMIT_80
                            82 -> RecordedVoiceClip.SPEED_LIMIT_82
                            90 -> RecordedVoiceClip.SPEED_LIMIT_90
                            100 -> RecordedVoiceClip.SPEED_LIMIT_100
                            110 -> RecordedVoiceClip.SPEED_LIMIT_110
                            120 -> RecordedVoiceClip.SPEED_LIMIT_120
                            else -> null
                        }
                        if (recordedLimitClip != null) {
                            voiceAlertManager.playRecorded(
                                type = VoiceAlertType.SPEED,
                                priority = VoiceAlertPriority.NORMAL,
                                clip = recordedLimitClip,
                                cooldownKey = "road_speed_limit_$receivedLimit",
                                cooldownMs = 60_000L
                            )
                        }
                    }

                } else if (result != null) {
                    // TomTom yanıt verdi ancak bu nokta için hız limiti yoksa
                    // önceki yolun değerini taşımıyoruz.
                    val keepLocked82 =
                        latestRoadSpeedLimitKmh == 82 &&
                            SystemClock.elapsedRealtime() < speedLimit82HoldUntilElapsedRealtime
                    if (!keepLocked82) {
                        latestRoadSpeedLimitKmh = null
                        latestRoadSpeedLimitReceivedElapsedRealtime = 0L
                    }

                    refreshSpeedWarningForCurrentSpeed()
                    updateFuelValues()
                }

                roadSpeedLimitJob = null
            }
    }

    private fun requestUpcomingRoadSignsIfNeeded(
        location: Location,
        headingDegrees: Float?
    ) {
        if (headingDegrees == null || !headingDegrees.isFinite()) return

        // Park halinde gereksiz TomTom sorgusu yapma ve eski levhayı ekranda tutma.
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        if (speedKmh < 5f) {
            if (_uiState.value.upcomingRoadSigns.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(upcomingRoadSigns = emptyList())
            }
            return
        }

        if (upcomingRoadSignJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        val lastLocation = lastUpcomingSignQueryLocation
        val moved = lastLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val enoughTime = now - lastUpcomingSignQueryElapsedRealtime >= UPCOMING_SIGN_REFRESH_MS
        val movedEnough = moved >= UPCOMING_SIGN_MIN_MOVE_METERS

        if (lastLocation != null && (!enoughTime || !movedEnough)) return

        lastUpcomingSignQueryElapsedRealtime = now
        lastUpcomingSignQueryLocation = Location(location)

        upcomingRoadSignJob = viewModelScope.launch {
            val signs = runCatching {
                upcomingRoadSignRepository.getUpcomingSigns(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    headingDegrees = headingDegrees,
                    currentRoadSpeedLimitKmh = latestRoadSpeedLimitKmh
                )
            }.getOrDefault(emptyList())

            _uiState.value = _uiState.value.copy(upcomingRoadSigns = signs)
            upcomingRoadSignJob = null
        }
    }

    private fun clearExpiredRoadSpeedLimitIfNeeded() {
        val currentLimit = latestRoadSpeedLimitKmh ?: return
        val receivedAt = latestRoadSpeedLimitReceivedElapsedRealtime

        if (receivedAt <= 0L) {
            latestRoadSpeedLimitKmh = null
            return
        }

        val ageMs =
            SystemClock.elapsedRealtime() - receivedAt

        if (
            currentLimit == 82 &&
            SystemClock.elapsedRealtime() < speedLimit82HoldUntilElapsedRealtime
        ) return

        if (ageMs > ROAD_SPEED_LIMIT_MAX_AGE_MS) {
            latestRoadSpeedLimitKmh = null
            latestRoadSpeedLimitReceivedElapsedRealtime = 0L
            updateFuelValues()
        }
    }

    private fun refreshSpeedWarningForCurrentSpeed() {
        val currentSpeedKmh =
            _uiState.value.gpsSpeedKmh

        val speedLimitKmh =
            latestRoadSpeedLimitKmh
                ?: if (_uiState.value.vehicleMode == "Karavan") {
                    DEFAULT_CARAVAN_SPEED_LIMIT_KMH
                } else {
                    DEFAULT_CAR_SPEED_LIMIT_KMH
                }

        val warningState =
            when {
                currentSpeedKmh >=
                        speedLimitKmh + SPEED_WARNING_TOLERANCE_KMH ->
                    SpeedWarningState.OVER_LIMIT

                currentSpeedKmh >=
                        speedLimitKmh - SPEED_APPROACHING_MARGIN_KMH ->
                    SpeedWarningState.APPROACHING

                else ->
                    SpeedWarningState.NORMAL
            }

        handleSpeedVoiceWarning(
            warningState = warningState,
            speedLimitKmh = speedLimitKmh,
            currentSpeedKmh = currentSpeedKmh
        )

        _uiState.value =
            _uiState.value.copy(
                speedLimitKmh = speedLimitKmh,
                speedLimitSource =
                    if (latestRoadSpeedLimitKmh != null) {
                        SpeedLimitSource.ROAD
                    } else {
                        SpeedLimitSource.DEFAULT
                    },
                speedWarningToleranceKmh =
                    SPEED_WARNING_TOLERANCE_KMH,
                speedWarningState = warningState
            )
    }

    private fun handleSpeedVoiceWarning(
        warningState: SpeedWarningState,
        speedLimitKmh: Int,
        currentSpeedKmh: Int
    ) {
        if (!canAnnounceDrivingSpeed(currentSpeedKmh)) {
            hasAnnouncedSpeedLimitExceeded = false
            return
        }
        if (
            hasAnnouncedSpeedLimitExceeded &&
            currentSpeedKmh <= speedLimitKmh - SPEED_VOICE_REARM_GAP_KMH
        ) {
            hasAnnouncedSpeedLimitExceeded = false
        }

        if (
            warningState == SpeedWarningState.OVER_LIMIT &&
            !hasAnnouncedSpeedLimitExceeded
        ) {
            hasAnnouncedSpeedLimitExceeded = true

            voiceAlertManager.playRecorded(
                type = VoiceAlertType.SPEED,
                priority = VoiceAlertPriority.CRITICAL,
                clip = RecordedVoiceClip.SPEED_WARNING,
                cooldownKey = "speed_limit_exceeded",
                cooldownMs = 30_000L
            )
        }
    }

    /** Sürücü seçilmeden, araç dururken veya Park ekranındayken hız sesi üretme. */
    private fun canAnnounceDrivingSpeed(currentSpeedKmh: Int = _uiState.value.gpsSpeedKmh): Boolean =
        hasSelectedDriverSession &&
            !_uiState.value.isParkMode &&
            currentSpeedKmh >= TRIP_MIN_SPEED_KMH

    private fun bearingToDirection(bearing: Float): String {
        val normalizedBearing =
            ((bearing % 360f) + 360f) % 360f

        val directions = listOf(
            "K",  // Kuzey
            "KD", // Kuzeydoğu
            "D",  // Doğu
            "GD", // Güneydoğu
            "G",  // Güney
            "GB", // Güneybatı
            "B",  // Batı
            "KB"  // Kuzeybatı
        )

        val index =
            ((normalizedBearing + 22.5f) / 45f)
                .toInt() % directions.size

        return directions[index]
    }

    private fun TrackerCompassAccuracy.toUiAccuracy(): CompassAccuracy =
        when (this) {
            TrackerCompassAccuracy.UNAVAILABLE -> CompassAccuracy.UNAVAILABLE
            TrackerCompassAccuracy.UNRELIABLE -> CompassAccuracy.UNRELIABLE
            TrackerCompassAccuracy.LOW -> CompassAccuracy.LOW
            TrackerCompassAccuracy.MEDIUM -> CompassAccuracy.MEDIUM
            TrackerCompassAccuracy.HIGH -> CompassAccuracy.HIGH
        }


    private fun observeVehiclePreferences() {
        viewModelScope.launch {
            vehiclePreferencesRepository
                .preferences
                .collect { preferences ->

                    val selectedVehicle =
                        VehicleCatalog.findById(
                            preferences.selectedVehicleId
                        )
                            ?: VehicleCatalog.defaultFor(
                                VehicleType.CAR
                            )

                    val vehicleMode =
                        when (selectedVehicle.type) {
                            VehicleType.CAR ->
                                "Otomobil"

                            VehicleType.CARAVAN ->
                                "Karavan"
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            selectedVehicleId =
                                selectedVehicle.id,
                            vehicleMode =
                                vehicleMode,
                            customCarImageUri =
                                preferences.customCarImageUri,
                            customCaravanImageUri =
                                preferences.customCaravanImageUri
                        )

                    refreshSpeedWarningForCurrentSpeed()
                    updateFuelValues()
                }
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
        parkEntryJob?.cancel()
        parkTimerJob?.cancel()
        fuelSettingsJob?.cancel()
        roadSpeedLimitJob?.cancel()
        upcomingRoadSignJob?.cancel()
        tripTimerJob?.cancel()
        compassTracker.stop()
        slopeToneGenerator.release()

        super.onCleared()
    }

    fun toggleSpeedCorridor() {
        if (_uiState.value.isSpeedCorridorActive) stopSpeedCorridor() else startSpeedCorridor()
    }

    fun startSpeedCorridor() {
        if (_uiState.value.isSpeedCorridorActive) return
        val now = System.currentTimeMillis()
        lastSpeedCorridorLocation = null
        _uiState.value = _uiState.value.copy(
            isSpeedCorridorActive = true,
            speedCorridorStartedAtEpochMillis = now,
            speedCorridorDistanceKm = 0.0,
            speedCorridorDurationSeconds = 0L,
            speedCorridorAverageSpeedKmh = 0.0
        )
        persistSpeedCorridor()
    }

    fun stopSpeedCorridor() {
        val state = _uiState.value
        if (!state.isSpeedCorridorActive) return
        val endedAt = System.currentTimeMillis()
        val record = listOf(
            state.speedCorridorStartedAtEpochMillis ?: endedAt,
            endedAt,
            state.speedCorridorDistanceKm,
            state.speedCorridorDurationSeconds,
            state.speedCorridorAverageSpeedKmh
        ).joinToString("|")
        val old = speedCorridorPrefs.getString("history", "").orEmpty()
        speedCorridorPrefs.edit()
            .putString("history", (record + "\n" + old).lineSequence().take(20).joinToString("\n"))
            .putBoolean("active", false)
            .apply()
        lastSpeedCorridorLocation = null
        _uiState.value = state.copy(isSpeedCorridorActive = false)
    }

    fun getSpeedCorridorHistory(): List<SpeedCorridorRecord> =
        speedCorridorPrefs.getString("history", "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split('|')
                if (p.size != 5) return@mapNotNull null
                SpeedCorridorRecord(
                    startedAtEpochMillis = p[0].toLongOrNull() ?: return@mapNotNull null,
                    endedAtEpochMillis = p[1].toLongOrNull() ?: return@mapNotNull null,
                    distanceKm = p[2].toDoubleOrNull() ?: return@mapNotNull null,
                    durationSeconds = p[3].toLongOrNull() ?: return@mapNotNull null,
                    averageSpeedKmh = p[4].toDoubleOrNull() ?: return@mapNotNull null
                )
            }.toList()

    private fun updateSpeedCorridor(location: Location, speedKmh: Int) {
        val state = _uiState.value
        if (!state.isSpeedCorridorActive) {
            lastSpeedCorridorLocation = null
            return
        }
        val startedAt = state.speedCorridorStartedAtEpochMillis ?: return
        val duration = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        var distance = state.speedCorridorDistanceKm
        val good = location.hasAccuracy() && location.accuracy <= TRIP_MAX_ACCURACY_METERS && speedKmh >= TRIP_MIN_SPEED_KMH
        if (good) {
            val previous = lastSpeedCorridorLocation
            lastSpeedCorridorLocation = Location(location)
            if (previous != null) {
                val meters = previous.distanceTo(location)
                if (meters in 0.01f..TRIP_MAX_SEGMENT_METERS) distance += meters / 1000.0
            }
        } else {
            lastSpeedCorridorLocation = null
        }
        val average = if (duration > 0L) distance / (duration / 3600.0) else 0.0
        _uiState.value = state.copy(
            speedCorridorDistanceKm = distance,
            speedCorridorDurationSeconds = duration,
            speedCorridorAverageSpeedKmh = average
        )
        persistSpeedCorridor()
    }

    private fun persistSpeedCorridor() {
        val s = _uiState.value
        speedCorridorPrefs.edit()
            .putBoolean("active", s.isSpeedCorridorActive)
            .putLong("started", s.speedCorridorStartedAtEpochMillis ?: 0L)
            .putString("distance", s.speedCorridorDistanceKm.toString())
            .apply()
    }

    private fun restoreSpeedCorridor() {
        if (!speedCorridorPrefs.getBoolean("active", false)) return
        val started = speedCorridorPrefs.getLong("started", 0L).takeIf { it > 0L } ?: return
        val distance = speedCorridorPrefs.getString("distance", "0")?.toDoubleOrNull() ?: 0.0
        val duration = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0L)
        _uiState.value = _uiState.value.copy(
            isSpeedCorridorActive = true,
            speedCorridorStartedAtEpochMillis = started,
            speedCorridorDistanceKm = distance,
            speedCorridorDurationSeconds = duration,
            speedCorridorAverageSpeedKmh = if (duration > 0) distance / (duration / 3600.0) else 0.0
        )
    }

}


data class SpeedCorridorRecord(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val averageSpeedKmh: Double
)
