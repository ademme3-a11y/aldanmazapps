package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalog
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignInfo

data class DriveDashboardUiState(
    /** Hesaplama ve güvenlik işlevlerinde kullanılan kalibre edilmemiş GPS hızı. */
    val gpsSpeedKmh: Int = 0,
    /** Kullanıcının araç göstergesine göre kalibre edilmiş ekrandaki hız. */
    val speedKmh: Int = 0,
    /** Ekrandaki hızın aktif kaynağı: OBD varsa OBD, yoksa GPS. */
    val speedDataSource: String = "GPS",
    val direction: String = "--",
    val altitudeMeters: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Yolculuk takibi
    val tripDistanceKm: Double = 0.0,
    val isTripActive: Boolean = false,
    val tripDurationSeconds: Long = 0L,
    val tripMovingDurationSeconds: Long = 0L,
    val tripParkDurationSeconds: Long = 0L,
    val tripAverageSpeedKmh: Double = 0.0,
    val tripMaxSpeedKmh: Int = 0,
    val tripEstimatedFuelConsumedLiters: Double = 0.0,
    val tripEstimatedFuelCost: Double = 0.0,
    val tripSpeed0To30Seconds: Long = 0L,
    val tripSpeed31To50Seconds: Long = 0L,
    val tripSpeed51To70Seconds: Long = 0L,
    val tripSpeed71To90Seconds: Long = 0L,
    val tripSpeed91To120Seconds: Long = 0L,
    val tripSpeedOver120Seconds: Long = 0L,
    val todayTotalDistanceKm: Double = 0.0,
    val todayEstimatedFuelConsumedLiters: Double = 0.0,
    val todayEstimatedFuelCost: Double = 0.0,
    val tripStartedAtEpochMillis: Long? = null,
    val tripEndedAtEpochMillis: Long? = null,
    val tripStartLatitude: Double? = null,
    val tripStartLongitude: Double? = null,

    // Hız koridoru
    val isSpeedCorridorActive: Boolean = false,
    val speedCorridorStartedAtEpochMillis: Long? = null,
    val speedCorridorDistanceKm: Double = 0.0,
    val speedCorridorDurationSeconds: Long = 0L,
    val speedCorridorAverageSpeedKmh: Double = 0.0,

    // _21: önümüzdeki 500 m için TomTom tabanlı yaklaşan levhalar / yol uyarıları
    val upcomingRoadSigns: List<UpcomingRoadSignInfo> = emptyList(),

    // Step 17: Trafik Ajanı
    val isTrafficAgentActive: Boolean = false,
    val trafficAgentStatus: String = "Kapalı",
    val trafficDelaySeconds: Int = 0,
    val trafficIncidentCount: Int = 0,
    val trafficSeverity: Int = 0,
    val trafficLastCheckedAtEpochMillis: Long? = null,

    // Step 15-16: Karavan rota / canlı eğim paneli
    val isRouteSlopePanelVisible: Boolean = false,
    val isLiveSlopePanelVisible: Boolean = false,
    /** Gemini/manual kontrolü için ana ekrandaki canlı yükseklik yarım paneli. */
    val isLiveSlopeHalfVisible: Boolean = false,
    /** Park verisi devam ederken görsel olarak 0 km/h hız kadranını göstermeye zorlar. */
    val forceSpeedometerDisplay: Boolean = false,
    val currentSlopePercent: Double = 0.0,
    val totalClimbMeters: Double = 0.0,
    val totalDescentMeters: Double = 0.0,
    val liveElevationProfileMeters: List<Double> = emptyList(),
    val liveElevationDistanceKm: List<Double> = emptyList(),

    val vehicleMode: String = "Otomobil",

    // Araç deposundaki seçili araç kaydı.
    // Şimdilik genel hatchback ile başlıyoruz.
    val selectedVehicleId: String =
        VehicleCatalog.DEFAULT_CAR_ID,

    // Galeriden seçilen özel görseller.
    // Otomobil ve karavan birbirinden bağımsız saklanır.
    val customCarImageUri: String? = null,
    val customCaravanImageUri: String? = null,

    val isLocationPermissionGranted: Boolean = false,
    val isGpsActive: Boolean = false,
    val gpsAccuracyMeters: Int? = null,
    val locationStatus: String = "Konum izni bekleniyor",
    val isParkMode: Boolean = false,
    val parkDurationSeconds: Long = 0L,
    val lastParkDurationSeconds: Long = 0L,
    val totalParkDurationSeconds: Long = 0L,
    val isCompassPanelVisible: Boolean = false,
    val isCompassSensorAvailable: Boolean = false,
    val compassHeadingDegrees: Float? = null,
    val compassAccuracy: CompassAccuracy = CompassAccuracy.UNAVAILABLE,
    val qiblaBearingDegrees: Float? = null,
    val isFuelConfigured: Boolean = false,
    val remainingFuelLiters: Double? = null,
    val fuelPercentage: Double? = null,
    /** Yakıt yüzdesinin kaynağı: OBD veya mevcut ALD hesap/depo takibi. */
    val fuelDataSource: String = "HESAP",
    val estimatedFuelRangeKm: Double? = null,
    val isLowFuel: Boolean = false,

    // Akıllı hız uyarısı
    val speedLimitKmh: Int? = null,
    val speedLimitSource: SpeedLimitSource = SpeedLimitSource.DEFAULT,
    val speedWarningToleranceKmh: Int = 3,
    val speedWarningState: SpeedWarningState = SpeedWarningState.NORMAL,

    // 125: Araç Modu başlığında kısa süre gösterilen sürüş/güvenlik/eğim uyarısı.
    val transientVehicleAlertMessage: String? = null
)

enum class SpeedLimitSource {
    DEFAULT,
    ROAD
}

enum class SpeedWarningState {
    NORMAL,
    APPROACHING,
    OVER_LIMIT
}

enum class CompassAccuracy {
    UNAVAILABLE,
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH
}
