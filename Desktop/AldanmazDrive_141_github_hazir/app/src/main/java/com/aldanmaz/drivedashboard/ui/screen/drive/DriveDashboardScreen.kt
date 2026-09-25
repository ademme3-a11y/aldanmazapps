package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalog
import com.aldanmaz.drivedashboard.data.alert.MediaAppController
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime
import com.aldanmaz.drivedashboard.data.vehicle.VehicleVisualSource
import android.content.res.Configuration
import android.media.AudioManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aldanmaz.drivedashboard.data.weather.WeatherCondition
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherUiState
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherTemperatureScene
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignInfo
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt

private val DashboardBackground get() = when {
    DashboardPaletteRuntime.isSunlight -> Color(0xFF18212A)
    DashboardPaletteRuntime.isOled -> Color.Black
    else -> Color(0xFF02070D)
}
private val HeaderBackground get() = when {
    DashboardPaletteRuntime.isSunlight -> Color(0xFF202B35)
    DashboardPaletteRuntime.isOled -> Color.Black
    else -> Color(0xFF07111C)
}
private val PanelBackground get() = when {
    DashboardPaletteRuntime.isSunlight -> Color(0xFF222D36)
    DashboardPaletteRuntime.isOled -> Color.Black
    else -> Color(0xFF07121E)
}
private val PanelBorder get() = DashboardPaletteRuntime.accent.copy(alpha = if (DashboardPaletteRuntime.isSunlight) .82f else .35f)

private val PrimaryText get() = DashboardPaletteRuntime.primaryText
private val SecondaryText get() = DashboardPaletteRuntime.secondaryText

private val CyanAccent get() = DashboardPaletteRuntime.accent
private val CyanBright get() = DashboardPaletteRuntime.accent

private val SafeGreen = Color(0xFF31E39A)
private val WarningYellow = Color(0xFFFFC84A)
private val DangerRed = Color(0xFFFF4F5E)

private val LocalDashboardStrokeScale = staticCompositionLocalOf { 1f }

@Composable
fun DriveDashboardScreen(
    speedKmh: Int = 0,
    speedDataSource: String = "GPS",
    direction: String = "--",
    altitudeMeters: Int? = null,
    tripDistanceKm: Double = 0.0,
    isTripActive: Boolean = false,
    isSpeedCorridorActive: Boolean = false,
    isRouteSlopePanelVisible: Boolean = false,
    isLiveSlopePanelVisible: Boolean = false,
    isLiveSlopeHalfVisible: Boolean = false,
    currentSlopePercent: Double = 0.0,
    totalClimbMeters: Double = 0.0,
    totalDescentMeters: Double = 0.0,
    liveElevationProfileMeters: List<Double> = emptyList(),
    liveElevationDistanceKm: List<Double> = emptyList(),
    currentLatitude: Double? = null,
    currentLongitude: Double? = null,
    isTrafficAgentActive: Boolean = false,
    trafficAgentStatus: String = "Kapalı",
    trafficDelaySeconds: Int = 0,
    trafficIncidentCount: Int = 0,
    trafficSeverity: Int = 0,
    speedCorridorDistanceKm: Double = 0.0,
    speedCorridorDurationSeconds: Long = 0L,
    speedCorridorAverageSpeedKmh: Double = 0.0,
    upcomingRoadSigns: List<UpcomingRoadSignInfo> = emptyList(),
    tripDurationSeconds: Long = 0L,
    tripMovingDurationSeconds: Long = 0L,
    tripParkDurationSeconds: Long = 0L,
    tripAverageSpeedKmh: Double = 0.0,
    tripMaxSpeedKmh: Int = 0,
    tripEstimatedFuelConsumedLiters: Double = 0.0,
    tripEstimatedFuelCost: Double = 0.0,
    todayEstimatedFuelConsumedLiters: Double = 0.0,
    todayEstimatedFuelCost: Double = 0.0,
    todayTotalDistanceKm: Double = 0.0,
    tripStartedAtEpochMillis: Long? = null,
    tripEndedAtEpochMillis: Long? = null,
    vehicleMode: String = "Otomobil",
    selectedVehicleId: String = VehicleCatalog.DEFAULT_CAR_ID,
    customCarImageUri: String? = null,
    customCaravanImageUri: String? = null,
    locationStatus: String = "GPS verisi bekleniyor",
    isParkMode: Boolean = false,
    parkDurationSeconds: Long = 0L,
    isCompassPanelVisible: Boolean = false,
    isCompassSensorAvailable: Boolean = false,
    compassHeadingDegrees: Float? = null,
    compassAccuracy: CompassAccuracy = CompassAccuracy.UNAVAILABLE,
    qiblaBearingDegrees: Float? = null,
    fuelPercentage: Double? = null,
    fuelDataSource: String = "HESAP",
    remainingFuelLiters: Double? = null,
    estimatedFuelRangeKm: Double? = null,
    isLowFuel: Boolean = false,
    speedLimitKmh: Int? = null,
    speedLimitSource: SpeedLimitSource = SpeedLimitSource.DEFAULT,
    speedWarningState: SpeedWarningState = SpeedWarningState.NORMAL,
    transientVehicleAlertMessage: String? = null,
    obdAlertMessage: String? = null,
    weatherUiState: WeatherUiState = WeatherUiState(),
    sunriseTime: String = "06:24",
    sunsetTime: String = "19:48",
    onFuelClick: () -> Unit = {},
    onStatisticsClick: () -> Unit = {},
    onSpeedCorridorClick: () -> Unit = {},
    onPrayerClick: () -> Unit = {},
    isObdConnected: Boolean = false,
    isBluetoothConnected: Boolean = false,
    isWifiConnected: Boolean = false,
    isInternetAvailable: Boolean = false,
    wifiShortName: String = "---",
    bluetoothShortName: String = "---",
    obdShortName: String = "OBD",
    selectedDriverName: String = "---",
    isAppSoundEnabled: Boolean = true,
    isAppAudioPlaying: Boolean = false,
    isGpsActive: Boolean = false,
    onWifiClick: () -> Unit = {},
    onBluetoothClick: () -> Unit = {},
    onObdClick: () -> Unit = {},
    onAppSoundToggle: () -> Unit = {},
    onRouteSlopeClick: () -> Unit = {},
    onLiveSlopeClick: () -> Unit = {},
    onLiveSlopeHalfVisibilityChange: (Boolean) -> Unit = {},
    onTrafficAgentClick: () -> Unit = {},
    onNavigationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onWorkClick: () -> Unit = {},
    onClockClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onWeatherDetailsClick: () -> Unit = {},
    onCompassClick: () -> Unit = {},
    onVehicleModeChange: (String) -> Unit = {},
    onTripStart: () -> Unit = {},
    onTripStop: () -> Unit = {}
) {
    val clockFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(
            "d MMM yyyy EEE",
            Locale("tr", "TR")
        )
    }

    var currentTime by remember {
        mutableStateOf(LocalTime.now().format(clockFormatter))
    }
    var currentDate by remember {
        mutableStateOf(LocalDateTime.now().format(dateFormatter))
    }

    var dashboardRouteProfile by remember { mutableStateOf<RouteProfileResult?>(null) }
    var showDashboardRouteProfile by remember { mutableStateOf(false) }
    var showDashboardLiveProfile by remember { mutableStateOf(false) }
    var showDashboardRouteFullScreen by remember { mutableStateOf(false) }


    LaunchedEffect(isLiveSlopeHalfVisible) {
        showDashboardLiveProfile = isLiveSlopeHalfVisible
        if (isLiveSlopeHalfVisible) {
            showDashboardRouteProfile = false
            showDashboardRouteFullScreen = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            currentTime = now.format(clockFormatter)
            currentDate = now.format(dateFormatter)
            delay(1_000L)
        }
    }

    val activeSpeedLimit = speedLimitKmh

    val speedColor =
        when (speedWarningState) {
            SpeedWarningState.NORMAL -> SafeGreen
            SpeedWarningState.APPROACHING -> WarningYellow
            SpeedWarningState.OVER_LIMIT -> DangerRed
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DashboardBackground
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(DashboardBackground)
                .safeDrawingPadding()
                .padding(12.dp)
        ) {
            val nightContext = LocalContext.current
            val nightPrefs = remember(nightContext) {
                nightContext.getSharedPreferences("night_visual_settings", android.content.Context.MODE_PRIVATE)
            }
            var nightStarsEnabled by remember { mutableStateOf(nightPrefs.getBoolean("stars_enabled", true)) }
            androidx.compose.runtime.DisposableEffect(nightPrefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "stars_enabled") nightStarsEnabled = prefs.getBoolean(key, true)
                }
                nightPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { nightPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val showNightStars = nightStarsEnabled && !DashboardPaletteRuntime.isDay
            if (showNightStars) NightStarField(Modifier.fillMaxSize())

            val isLandscape = maxWidth > maxHeight

            // 10 inç sınıfı araç ekranlarında (sw600dp ve üzeri)
            // telefon görünümünü değiştirmeden yazı/çizgi ölçeğini güçlendir.
            val configuration = LocalConfiguration.current
            val baseDensity = LocalDensity.current
            val isLargeVehicleDisplay =
                isLandscape &&
                        configuration.smallestScreenWidthDp >= 600

            val dashboardDensity =
                if (isLargeVehicleDisplay) {
                    Density(
                        density = baseDensity.density * 1.08f,
                        fontScale = baseDensity.fontScale * 1.06f
                    )
                } else {
                    baseDensity
                }

            // Galaxy A25 referansında çizgiler normal görünüyor. 10 inç sınıfında
            // fiziksel inç yerine kullanılabilir kısa kenar (smallestScreenWidthDp)
            // esas alınarak yalnız stroke/çerçeve kalınlığı büyütülür.
            val responsiveStrokeScale =
                if (isLargeVehicleDisplay) {
                    (configuration.smallestScreenWidthDp / 360f).coerceIn(1f, 1.35f)
                } else {
                    1f
                } * if (DashboardPaletteRuntime.isSunlight) 1.35f else 1f

            CompositionLocalProvider(
                LocalDensity provides dashboardDensity,
                LocalDashboardStrokeScale provides responsiveStrokeScale
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isRouteSlopePanelVisible && !isLiveSlopePanelVisible) {
                        DashboardHeader(
                            currentTime = currentTime,
                            currentDate = currentDate,
                            direction = direction,
                            compassHeadingDegrees = compassHeadingDegrees,
                            isCompassPanelVisible = isCompassPanelVisible,
                            altitudeMeters = altitudeMeters,
                            isSpeedCorridorActive = isSpeedCorridorActive,
                            onSpeedCorridorClick = onSpeedCorridorClick,
                            onPrayerClick = onPrayerClick,
                            isObdConnected = isObdConnected,
                            isBluetoothConnected = isBluetoothConnected,
                            isWifiConnected = isWifiConnected,
                            isInternetAvailable = isInternetAvailable,
                            wifiShortName = wifiShortName,
                            bluetoothShortName = bluetoothShortName,
                            isGpsActive = isGpsActive,
                            onWifiClick = onWifiClick,
                            onBluetoothClick = onBluetoothClick,
                            onObdClick = onObdClick,
                            isRouteSlopePanelVisible = isRouteSlopePanelVisible,
                            isLiveSlopePanelVisible = isLiveSlopePanelVisible,
                            vehicleMode = vehicleMode,
                            onRouteSlopeClick = onRouteSlopeClick,
                            onLiveSlopeClick = onLiveSlopeClick,
                            isTrafficAgentActive = isTrafficAgentActive,
                            trafficAgentStatus = trafficAgentStatus,
                            trafficDelaySeconds = trafficDelaySeconds,
                            trafficIncidentCount = trafficIncidentCount,
                            trafficSeverity = trafficSeverity,
                            onTrafficAgentClick = onTrafficAgentClick,
                            onNavigationClick = onNavigationClick,
                            onHomeClick = onHomeClick,
                            onWorkClick = onWorkClick,
                            onClockClick = onClockClick,
                            onSettingsClick = onSettingsClick,
                            onCompassClick = onCompassClick
                        )

                        ConnectionStatusStrip(
                            isGpsActive = isGpsActive,
                            isInternetAvailable = isInternetAvailable,
                            isBluetoothConnected = isBluetoothConnected || isObdConnected,
                            isObdConnected = isObdConnected,
                            networkLabel = wifiShortName,
                            bluetoothLabel = bluetoothShortName,
                            obdLabel = obdShortName,
                            selectedDriverName = selectedDriverName,
                            isAppSoundEnabled = isAppSoundEnabled,
                            isAppAudioPlaying = isAppAudioPlaying,
                            onAppSoundToggle = onAppSoundToggle,
                            isSpeedCorridorActive = isSpeedCorridorActive,
                            onSpeedCorridorClick = onSpeedCorridorClick
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }

                    if (isLandscape) {
                        if (isRouteSlopePanelVisible) {
                            RoutePlannerScreen(
                                currentLat = currentLatitude,
                                currentLon = currentLongitude,
                                isCaravan = vehicleMode == "Karavan",
                                onBack = onRouteSlopeClick,
                                onShowOnDashboard = { profile ->
                                    dashboardRouteProfile = profile
                                    showDashboardRouteProfile = true
                                    showDashboardLiveProfile = false
                                    showDashboardRouteFullScreen = false
                                    onRouteSlopeClick()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isLiveSlopePanelVisible) {
                            LiveElevationScreen(
                                currentLat = currentLatitude,
                                currentLon = currentLongitude,
                                currentAltitudeM = altitudeMeters,
                                headingDegrees = compassHeadingDegrees,
                                pastDistancesKm = liveElevationDistanceKm,
                                pastElevationsM = liveElevationProfileMeters,
                                currentSlopePct = currentSlopePercent,
                                isCaravan = vehicleMode == "Karavan",
                                onBack = onLiveSlopeClick,
                                onShowHalfScreen = {
                                    showDashboardLiveProfile = true
                                    showDashboardRouteProfile = false
                                    showDashboardRouteFullScreen = false
                                    onLiveSlopeHalfVisibilityChange(true)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                LandscapeDashboard(
                                    halfTrackingActive = showDashboardRouteProfile || showDashboardLiveProfile,
                                    speedKmh = speedKmh,
                                    speedDataSource = speedDataSource,
                                    speedColor = speedColor,
                                    speedLimitKmh = activeSpeedLimit,
                                    speedLimitSource = speedLimitSource,
                                    speedWarningState = speedWarningState,
                                    transientVehicleAlertMessage = transientVehicleAlertMessage,
                                    obdAlertMessage = obdAlertMessage,
                                    isParkMode = isParkMode,
                                    parkDurationSeconds = parkDurationSeconds,
                                    isCompassPanelVisible = isCompassPanelVisible,
                                    isCompassSensorAvailable = isCompassSensorAvailable,
                                    compassHeadingDegrees = compassHeadingDegrees,
                                    compassAccuracy = compassAccuracy,
                                    qiblaBearingDegrees = qiblaBearingDegrees,
                                    direction = direction,
                                    altitudeMeters = altitudeMeters,
                                    weatherUiState = weatherUiState,
                                    tripDistanceKm = tripDistanceKm,
                                    isTripActive = isTripActive,
                                    isSpeedCorridorActive = isSpeedCorridorActive,
                                    speedCorridorDistanceKm = speedCorridorDistanceKm,
                                    speedCorridorDurationSeconds = speedCorridorDurationSeconds,
                                    speedCorridorAverageSpeedKmh = speedCorridorAverageSpeedKmh,
                                    upcomingRoadSigns = upcomingRoadSigns,
                                    tripDurationSeconds = tripDurationSeconds,
                                    tripMovingDurationSeconds = tripMovingDurationSeconds,
                                    tripParkDurationSeconds = tripParkDurationSeconds,
                                    tripAverageSpeedKmh = tripAverageSpeedKmh,
                                    tripMaxSpeedKmh = tripMaxSpeedKmh,
                                    tripEstimatedFuelConsumedLiters =
                                        tripEstimatedFuelConsumedLiters,
                                    tripEstimatedFuelCost = tripEstimatedFuelCost,
                                    todayEstimatedFuelConsumedLiters = todayEstimatedFuelConsumedLiters,
                                    todayEstimatedFuelCost = todayEstimatedFuelCost,
                                    todayTotalDistanceKm =
                                        todayTotalDistanceKm,
                                    tripStartedAtEpochMillis =
                                        tripStartedAtEpochMillis,
                                    tripEndedAtEpochMillis =
                                        tripEndedAtEpochMillis,
                                    vehicleMode = vehicleMode,
                                    selectedVehicleId = selectedVehicleId,
                                    customCarImageUri = customCarImageUri,
                                    customCaravanImageUri = customCaravanImageUri,
                                    fuelPercentage = fuelPercentage,
                                    fuelDataSource = fuelDataSource,
                                    remainingFuelLiters = remainingFuelLiters,
                                    estimatedFuelRangeKm = estimatedFuelRangeKm,
                                    isLowFuel = isLowFuel,
                                    sunriseTime = sunriseTime,
                                    sunsetTime = sunsetTime,
                                    currentDate = currentDate,
                                    currentTime = currentTime,
                                    onClockClick = onClockClick,
                                    onFuelClick = onFuelClick,
                                    onStatisticsClick = onStatisticsClick,
                                    onWeatherDetailsClick = onWeatherDetailsClick,
                                    onVehicleModeChange = onVehicleModeChange,
                                    onTripStart = onTripStart,
                                    onTripStop = onTripStop
                                )
                                if (showDashboardRouteProfile && dashboardRouteProfile != null) {
                                    RouteDashboardHalfProfile(
                                        result = dashboardRouteProfile!!,
                                        isCaravan = vehicleMode == "Karavan",
                                        onClose = {
                                            showDashboardRouteFullScreen = false
                                            showDashboardRouteProfile = false
                                        },
                                        onOpenFull = { showDashboardRouteFullScreen = true },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxWidth(0.50f)
                                            .fillMaxHeight()
                                            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                                    )
                                }
                                if (showDashboardLiveProfile) {
                                    LiveElevationDashboardHalfPanel(
                                        currentLat = currentLatitude,
                                        currentLon = currentLongitude,
                                        currentAltitudeM = altitudeMeters,
                                        headingDegrees = compassHeadingDegrees,
                                        pastDistancesKm = liveElevationDistanceKm,
                                        pastElevationsM = liveElevationProfileMeters,
                                        currentSlopePct = currentSlopePercent,
                                        isCaravan = vehicleMode == "Karavan",
                                        onClose = {
                                            showDashboardLiveProfile = false
                                            onLiveSlopeHalfVisibilityChange(false)
                                        },
                                        onOpenFull = {
                                            showDashboardLiveProfile = false
                                            onLiveSlopeHalfVisibilityChange(false)
                                            onLiveSlopeClick()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxWidth(0.50f)
                                            .fillMaxHeight()
                                            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        PortraitDashboard(
                            speedKmh = speedKmh,
                            speedDataSource = speedDataSource,
                            speedColor = speedColor,
                            speedLimitKmh = activeSpeedLimit,
                            speedLimitSource = speedLimitSource,
                            speedWarningState = speedWarningState,
                            transientVehicleAlertMessage = transientVehicleAlertMessage,
                            obdAlertMessage = obdAlertMessage,
                            isParkMode = isParkMode,
                            parkDurationSeconds = parkDurationSeconds,
                            isCompassPanelVisible = isCompassPanelVisible,
                            isCompassSensorAvailable = isCompassSensorAvailable,
                            compassHeadingDegrees = compassHeadingDegrees,
                            compassAccuracy = compassAccuracy,
                            qiblaBearingDegrees = qiblaBearingDegrees,
                            direction = direction,
                            altitudeMeters = altitudeMeters,
                            weatherUiState = weatherUiState,
                            tripDistanceKm = tripDistanceKm,
                            isTripActive = isTripActive,
                            isSpeedCorridorActive = isSpeedCorridorActive,
                            speedCorridorDistanceKm = speedCorridorDistanceKm,
                            speedCorridorDurationSeconds = speedCorridorDurationSeconds,
                            speedCorridorAverageSpeedKmh = speedCorridorAverageSpeedKmh,
                            upcomingRoadSigns = upcomingRoadSigns,
                            tripDurationSeconds = tripDurationSeconds,
                            tripMovingDurationSeconds = tripMovingDurationSeconds,
                            tripParkDurationSeconds = tripParkDurationSeconds,
                            tripAverageSpeedKmh = tripAverageSpeedKmh,
                            tripMaxSpeedKmh = tripMaxSpeedKmh,
                            tripEstimatedFuelConsumedLiters =
                                tripEstimatedFuelConsumedLiters,
                            tripEstimatedFuelCost = tripEstimatedFuelCost,
                            todayEstimatedFuelConsumedLiters = todayEstimatedFuelConsumedLiters,
                            todayEstimatedFuelCost = todayEstimatedFuelCost,
                            todayTotalDistanceKm =
                                todayTotalDistanceKm,
                            tripStartedAtEpochMillis =
                                tripStartedAtEpochMillis,
                            tripEndedAtEpochMillis =
                                tripEndedAtEpochMillis,
                            vehicleMode = vehicleMode,
                            selectedVehicleId = selectedVehicleId,
                            customCarImageUri = customCarImageUri,
                            customCaravanImageUri = customCaravanImageUri,
                            fuelPercentage = fuelPercentage,
                            fuelDataSource = fuelDataSource,
                            remainingFuelLiters = remainingFuelLiters,
                            estimatedFuelRangeKm = estimatedFuelRangeKm,
                            isLowFuel = isLowFuel,
                            sunriseTime = sunriseTime,
                            sunsetTime = sunsetTime,
                            currentDate = currentDate,
                            currentTime = currentTime,
                            onClockClick = onClockClick,
                            onFuelClick = onFuelClick,
                            onStatisticsClick = onStatisticsClick,
                            onWeatherDetailsClick = onWeatherDetailsClick,
                            onVehicleModeChange = onVehicleModeChange,
                            onTripStart = onTripStart,
                            onTripStop = onTripStop
                        )
                    }
                }

                if (showDashboardRouteFullScreen && dashboardRouteProfile != null) {
                    RouteFullScreenOverlay(
                        result = dashboardRouteProfile!!,
                        isCaravan = vehicleMode == "Karavan",
                        onBack = { showDashboardRouteFullScreen = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusStrip(
    isGpsActive: Boolean,
    isInternetAvailable: Boolean,
    isBluetoothConnected: Boolean,
    isObdConnected: Boolean,
    networkLabel: String,
    bluetoothLabel: String,
    obdLabel: String,
    selectedDriverName: String,
    isAppSoundEnabled: Boolean,
    isAppAudioPlaying: Boolean,
    onAppSoundToggle: () -> Unit,
    isSpeedCorridorActive: Boolean,
    onSpeedCorridorClick: () -> Unit
) {
    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = Modifier.fillMaxWidth().height(45.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HeaderBackground.copy(alpha = .92f)),
        border = BorderStroke(1.dp * strokeScale, CyanAccent.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionStatusItem("GPS", isGpsActive, Modifier.weight(1f))
            MasterSoundButton(isAppSoundEnabled, isAppAudioPlaying, onAppSoundToggle, Modifier.weight(.72f))
            ConnectionStatusItem(if (isInternetAvailable) networkLabel else "NET", isInternetAvailable, Modifier.weight(1f))
            SpeedCorridorMiniBar(isSpeedCorridorActive, onSpeedCorridorClick, Modifier.weight(3.23f))
            ConnectionStatusItem(if (isBluetoothConnected) bluetoothLabel else "BT", isBluetoothConnected, Modifier.weight(1f))
            ConnectionStatusItem(
                selectedDriverName.filter { it.isLetterOrDigit() }.take(3).uppercase(Locale("tr", "TR")).ifBlank { "---" },
                selectedDriverName.isNotBlank() && selectedDriverName != "---",
                Modifier.weight(.9f)
            )
            ConnectionStatusItem(if (isObdConnected) obdLabel else "OBD", isObdConnected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MasterSoundButton(
    enabled: Boolean,
    isAudioPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waveTransition = rememberInfiniteTransition(label = "masterSoundWaves")
    val waveProgress by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "masterSoundWaveProgress"
    )
    Box(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.icon_master_speaker),
            contentDescription = if (enabled) "Tüm sesler açık" else "Tüm sesler kapalı",
            modifier = Modifier.fillMaxSize().padding(1.dp),
            contentScale = ContentScale.Fit,
            alpha = if (enabled) 1f else .58f
        )
        Canvas(Modifier.fillMaxSize()) {
            if (enabled && isAudioPlaying) {
                repeat(3) { index ->
                    val phase = (waveProgress + index / 3f) % 1f
                    val radiusX = size.width * (.08f + phase * .25f)
                    val radiusY = size.height * (.18f + phase * .29f)
                    val origin = Offset(size.width * .61f, size.height * .50f)
                    drawArc(
                        color = CyanAccent.copy(alpha = (1f - phase) * .92f),
                        startAngle = -55f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(origin.x - radiusX, origin.y - radiusY),
                        size = Size(radiusX * 2f, radiusY * 2f),
                        style = Stroke(width = size.minDimension * .055f, cap = StrokeCap.Round)
                    )
                }
            } else if (!enabled) {
                drawLine(DangerRed, Offset(size.width * .10f, size.height * .14f), Offset(size.width * .90f, size.height * .86f), strokeWidth = size.minDimension * .10f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SpeedCorridorMiniBar(isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strokeScale = LocalDashboardStrokeScale.current

    Surface(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFF030507),
        border = BorderStroke(
            (if (isActive) 1.8.dp else 1.dp) * strokeScale,
            if (isActive) SafeGreen else Color.White.copy(alpha = .72f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RoadDashes(7)
            Text(
                text = " ORT HIZ KORİDORU ",
                color = PrimaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            RoadDashes(7)
        }
    }
}

@Composable
private fun RoadDashes(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) {
            Box(
                Modifier
                    .width(7.dp)
                    .height(2.dp)
                    .background(DashboardPaletteRuntime.primaryText, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun ConnectionStatusItem(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isActive) SafeGreen else SecondaryText.copy(alpha = .65f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(12.dp)) { drawCircle(color = color, radius = size.minDimension / 2f) }
        Spacer(Modifier.width(5.dp))
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private val DefaultHeaderOrder = listOf(
    "COMPASS", "HOME", "WORK", "MAP", "AGENT", "WIFI",
    "BLUETOOTH", "ROUTE", "LIVE", "MOON", "OBD", "SETTINGS"
)

@Composable
private fun DashboardHeader(
    currentTime: String,
    currentDate: String,
    direction: String,
    compassHeadingDegrees: Float?,
    isCompassPanelVisible: Boolean,
    altitudeMeters: Int?,
    isSpeedCorridorActive: Boolean,
    onSpeedCorridorClick: () -> Unit,
    onPrayerClick: () -> Unit,
    isObdConnected: Boolean,
    isBluetoothConnected: Boolean,
    isWifiConnected: Boolean,
    isInternetAvailable: Boolean,
    wifiShortName: String,
    bluetoothShortName: String,
    isGpsActive: Boolean,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onObdClick: () -> Unit,
    isRouteSlopePanelVisible: Boolean,
    isLiveSlopePanelVisible: Boolean,
    vehicleMode: String,
    onRouteSlopeClick: () -> Unit,
    onLiveSlopeClick: () -> Unit,
    isTrafficAgentActive: Boolean,
    trafficAgentStatus: String,
    trafficDelaySeconds: Int,
    trafficIncidentCount: Int,
    trafficSeverity: Int,
    onTrafficAgentClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onHomeClick: () -> Unit,
    onWorkClick: () -> Unit,
    onClockClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCompassClick: () -> Unit
) {
    val strokeScale = LocalDashboardStrokeScale.current

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ald_drive_header", android.content.Context.MODE_PRIVATE) }
    val storedOrder = remember {
        prefs.getString("order", null)?.split(",")
            ?.map { when (it) { "CORRIDOR" -> "MUSIC"; "PRAYER" -> "RADIO"; else -> it } }
            ?.filter { it in DefaultHeaderOrder }
            ?.let { saved ->
                val completed = saved + DefaultHeaderOrder.filterNot(saved::contains)
                if ("MOON" in saved) completed else completed.filterNot { it == "MOON" }.toMutableList().apply {
                    add((indexOf("LIVE") + 1).coerceAtLeast(0), "MOON")
                }
            }
            ?: DefaultHeaderOrder
    }
    var headerOrder by remember { mutableStateOf(storedOrder) }
    var hiddenItems by remember {
        mutableStateOf(prefs.getStringSet("hidden", emptySet())?.filter { it in DefaultHeaderOrder }?.toSet() ?: emptySet())
    }
    var isEditing by remember { mutableStateOf(false) }
    var radioActive by remember { mutableStateOf(false) }
    // 136: musicPlaying artık tek doğruluk kaynağı. MediaAppController.isMusicPlaying()
    // Gemini üzerinden başlatılan/durdurulan çalmayı da kapsadığı için ayrı bir yerel
    // "musicActive" değişkeni tutmuyoruz — önceden bu ikisi birbirinden kopup tek dokunuşun
    // (toggleMusic) Gemini'nin başlattığı müziği durdurmak yerine yeniden açmasına yol açıyordu.
    var musicPlaying by remember { mutableStateOf(false) }
    var showMusicChooser by remember { mutableStateOf(false) }

    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }

    LaunchedEffect(Unit) {
        while (true) {
            musicPlaying = MediaAppController.isMusicPlaying(context)
            radioActive = MediaAppController.isRadioPlaying(context)
            delay(300L)
        }
    }
    var navigationActive by remember { mutableStateOf(false) }
    LaunchedEffect(navigationActive) {
        if (navigationActive) {
            // Önce üst bardaki navigasyon ikonu yeşile dönsün ve dönüş animasyonu görünür olsun.
            delay(1000)
            onNavigationClick()
            navigationActive = false
        }
    }
    val musicApps = remember(showMusicChooser) { if (showMusicChooser) MediaAppController.musicApps(context) else emptyList() }

    fun openRadio() {
        radioActive = MediaAppController.openRadio(context)
    }
    fun toggleRadio() {
        if (radioActive) {
            MediaAppController.stopRadio(context)
            radioActive = false
        } else openRadio()
    }
    fun openMusic() {
        if (MediaAppController.selectedMusicPackage(context) == null) {
            showMusicChooser = true
        } else {
            val opened = MediaAppController.openMusic(context)
            // Optimistic update: ikon polling'i (300ms) beklemeden hemen ekolayzere dönsün.
            musicPlaying = opened || musicPlaying
            if (!opened) showMusicChooser = true
        }
    }
    // 136: Tek dokunuş artık gerçek çalma durumuna (musicPlaying) göre karar veriyor.
    // Böylece müzik Gemini tarafından başlatılmış olsa bile tek dokunuş onu doğru şekilde
    // durdurur; yerelde başlatılmamış olması "durdur" kararını engellemez.
    fun toggleMusic() {
        if (musicPlaying) {
            MediaAppController.stopMedia(context)
            musicPlaying = false
        } else {
            openMusic()
        }
    }
    // Çift dokunuş: PAUSE / DEVAM. Aynı parça ve aynı konum korunur.
    fun toggleMusicPauseResume() {
        if (musicPlaying) {
            // Çift dokunuş = PAUSE. Parça/konum korunur.
            MediaAppController.stopMedia(context)
            musicPlaying = false
        } else {
            // Çift dokunuş = DEVAM. Önceden seçilmiş/oynatılmış müzik varsa
            // aynı parçadan devam eder; hiç seçim yoksa müzik seçiciyi açar.
            if (MediaAppController.selectedMusicPackage(context) == null) {
                showMusicChooser = true
            } else {
                MediaAppController.resumeMedia(context)
                musicPlaying = true
            }
        }
    }

    fun toggleRadioPauseResume() {
        // Radyo ikonunda çift dokunuş doğrudan radyoyu açar; ikinci kez durdurmaz.
        if (!radioActive) {
            radioActive = MediaAppController.openRadio(context)
        }
    }

    fun saveLayout() {
        prefs.edit()
            .putString("order", headerOrder.joinToString(","))
            .putStringSet("hidden", hiddenItems)
            .apply()
    }

    fun resetLayout() {
        headerOrder = DefaultHeaderOrder
        hiddenItems = emptySet()
        prefs.edit().remove("order").remove("hidden").apply()
    }

    fun hideItem(id: String) {
        val updated = hiddenItems + id
        hiddenItems = updated
        prefs.edit().putStringSet("hidden", updated).apply()
    }

    fun showItem(id: String) {
        val updated = hiddenItems - id
        hiddenItems = updated
        prefs.edit().putStringSet("hidden", updated).apply()
    }

    fun moveItem(id: String, directionStep: Int) {
        val visible = headerOrder.filterNot(hiddenItems::contains)
        val visibleIndex = visible.indexOf(id)
        val targetVisibleIndex = (visibleIndex + directionStep).coerceIn(0, visible.lastIndex)
        if (visibleIndex < 0 || targetVisibleIndex == visibleIndex) return
        val targetId = visible[targetVisibleIndex]
        val list = headerOrder.toMutableList()
        val from = list.indexOf(id)
        val to = list.indexOf(targetId)
        list.removeAt(from)
        list.add(to, id)
        headerOrder = list
        prefs.edit().putString("order", list.joinToString(",")).apply()
    }

    if (showMusicChooser) {
        AlertDialog(
            onDismissRequest = { showMusicChooser = false },
            title = { Text("Müzik uygulaması seç", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (musicApps.isEmpty()) {
                        Text("Uygun müzik uygulaması bulunamadı.")
                    } else {
                        musicApps.take(10).forEach { app ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    musicPlaying = MediaAppController.openMusicPackage(context, app.packageName)
                                    showMusicChooser = false
                                }
                            ) {
                                Text(app.label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMusicChooser = false }) { Text("KAPAT") } }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(isEditing) {
                if (!isEditing) detectTapGestures(onLongPress = { isEditing = true })
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HeaderBackground),
        border = BorderStroke(1.dp * strokeScale, if (isEditing) CyanAccent else CyanAccent.copy(alpha = .35f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                headerOrder.filterNot(hiddenItems::contains).forEach { id ->
                    var dragX by remember(id) { mutableFloatStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .pointerInput(isEditing, id, headerOrder, hiddenItems) {
                                if (isEditing) {
                                    detectDragGesturesAfterLongPress(
                                        onDragEnd = { dragX = 0f; saveLayout() },
                                        onDragCancel = { dragX = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                            if (dragX > 45f) { moveItem(id, 1); dragX = 0f }
                                            else if (dragX < -45f) { moveItem(id, -1); dragX = 0f }
                                        }
                                    )
                                }
                            }
                    ) {
                        when (id) {
                            "COMPASS" -> TopCompassButton(direction, compassHeadingDegrees, isCompassPanelVisible, if (isEditing) ({ hideItem(id) }) else onCompassClick, Modifier.fillMaxSize())
                            "HOME" -> HomeHeaderButton(if (isEditing) ({ hideItem(id) }) else onHomeClick, Modifier.fillMaxSize())
                            "WORK" -> WorkHeaderButton(if (isEditing) ({ hideItem(id) }) else onWorkClick, Modifier.fillMaxSize())
                            "WIFI" -> WifiHeaderButton(isWifiConnected, wifiShortName, if (isEditing) ({ hideItem(id) }) else onWifiClick, Modifier.fillMaxSize())
                            "BLUETOOTH" -> BluetoothHeaderButton(isBluetoothConnected || isObdConnected, bluetoothShortName, if (isEditing) ({ hideItem(id) }) else onBluetoothClick, Modifier.fillMaxSize())
                            "MUSIC" -> MediaHeaderButton(
                                R.drawable.icon_music,
                                "Müzik",
                                musicPlaying,
                                if (isEditing) ({ hideItem(id) }) else ::toggleMusic,
                                if (isEditing) ({ hideItem(id) }) else ({ showMusicChooser = true }),
                                if (isEditing) ({}) else (::toggleMusicPauseResume),
                                Modifier.fillMaxSize()
                            )
                            "RADIO" -> MediaHeaderButton(
                                R.drawable.icon_radio,
                                "Radyo",
                                radioActive,
                                if (isEditing) ({ hideItem(id) }) else ::toggleRadio,
                                if (isEditing) ({ hideItem(id) }) else ::openRadio,
                                if (isEditing) ({}) else ::toggleRadioPauseResume,
                                Modifier.fillMaxSize()
                            )
                            "OBD" -> ObdHeaderButton(isObdConnected, if (isEditing) ({ hideItem(id) }) else onObdClick, Modifier.fillMaxSize())
                            "ROUTE" -> RouteSlopeHeaderButton("ROTA", isRouteSlopePanelVisible, true, if (isEditing) ({ hideItem(id) }) else onRouteSlopeClick, Modifier.fillMaxSize())
                            "LIVE" -> SatelliteHeaderButton(
                                isPanelActive = isLiveSlopePanelVisible,
                                isSignalActive = isGpsActive,
                                isEnabled = true,
                                onClick = if (isEditing) ({ hideItem(id) }) else onLiveSlopeClick,
                                modifier = Modifier.fillMaxSize()
                            )
                            "MOON" -> MoonPhaseHeaderButton(
                                modifier = Modifier.fillMaxSize(),
                                onClickOverride = if (isEditing) ({ hideItem(id) }) else null
                            )
                            "AGENT" -> TrafficAgentHeaderButton(isTrafficAgentActive, trafficDelaySeconds, trafficIncidentCount, trafficSeverity, trafficAgentStatus, if (isEditing) ({ hideItem(id) }) else onTrafficAgentClick, Modifier.fillMaxSize())
                            "MAP" -> GlobeHeaderButton(isActive = navigationActive, onClick = if (isEditing) ({ hideItem(id) }) else ({ if (!navigationActive) navigationActive = true }), modifier = Modifier.fillMaxSize())
                            "SETTINGS" -> SettingsHeaderButton(if (isEditing) ({ hideItem(id) }) else onSettingsClick, Modifier.fillMaxSize())
                        }
                        if (isEditing) {
                            Text("×", color = DangerRed, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
                        }
                    }
                }
            }
            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DÜZENLE", color = CyanBright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Basılı tut + sürükle • Dokun = gizle", color = SecondaryText, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    hiddenItems.forEach { id ->
                        Text(
                            text = "+${headerShortName(id)}",
                            color = SafeGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showItem(id) }.padding(4.dp)
                        )
                    }
                    Text("SIFIRLA", color = WarningYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { resetLayout() }.padding(4.dp))
                    Text("BİTTİ", color = SafeGreen, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { saveLayout(); isEditing = false }.padding(4.dp))
                }
            }
        }
    }
}

private fun headerShortName(id: String): String = when (id) {
    "COMPASS" -> "PUSULA"; "HOME" -> "EV"; "WORK" -> "İŞ"; "WIFI" -> "WIFI"; "BLUETOOTH" -> "BT"
    ; "OBD" -> "OBD"; "ROUTE" -> "ROTA"
    "LIVE" -> "CANLI"; "MOON" -> "AY"; "AGENT" -> "AJAN"; "MAP" -> "HARİTA"; "SETTINGS" -> "AYAR"; else -> id
}

@Composable
private fun RouteSlopeHeaderButton(
    label: String,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(
        modifier = modifier,
        isActive = isActive,
        enabled = isEnabled,
        onClick = onClick
    ) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_caravan_route,
            contentDescription = label,
            modifier = Modifier,
            alpha = if (isEnabled) 1f else .42f,
            scale = 1.30f
        )
    }
}

@Composable
private fun MountainSlopePanel(
    isRouteMode: Boolean,
    altitudeMeters: Int?,
    slopePercent: Double,
    totalClimbMeters: Double,
    totalDescentMeters: Double,
    profile: List<Double>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayProfile = if (profile.size >= 3) profile else listOf(
        (altitudeMeters ?: 0).toDouble() - 4.0,
        (altitudeMeters ?: 0).toDouble(),
        (altitudeMeters ?: 0).toDouble() + slopePercent * 2.0
    )
    val minAlt = displayProfile.minOrNull() ?: 0.0
    val maxAlt = displayProfile.maxOrNull() ?: (minAlt + 20.0)
    val range = (maxAlt - minAlt).coerceAtLeast(20.0)
    val currentIndex = (displayProfile.size * .52f).toInt().coerceIn(0, displayProfile.lastIndex)
    val currentAlt = displayProfile[currentIndex]
    val slopeColor = when {
        kotlin.math.abs(slopePercent) >= 10.0 -> DangerRed
        kotlin.math.abs(slopePercent) >= 5.0 -> WarningYellow
        else -> SafeGreen
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030812)),
        border = BorderStroke(1.dp, Color(0xFF132536))
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Canvas(Modifier.fillMaxSize().padding(top = 42.dp, start = 50.dp, bottom = 30.dp, end = 8.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val y = h * i / 4f
                    drawLine(Color(0xFF34404F), Offset(0f, y), Offset(w, y), 1f)
                }
                val line = Path()
                val fill = Path()
                displayProfile.forEachIndexed { i, a ->
                    val x = if (displayProfile.size == 1) 0f else i.toFloat() / (displayProfile.size - 1) * w
                    val y = h - (((a - minAlt) / range).toFloat() * h * .82f + h * .06f)
                    if (i == 0) { line.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y) }
                    else { line.lineTo(x, y); fill.lineTo(x, y) }
                }
                fill.lineTo(w, h); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFB5A895).copy(alpha=.78f), Color(0xFF40372D).copy(alpha=.78f))))
                drawPath(line, SafeGreen, style = Stroke(width = 3f, cap = StrokeCap.Round))

                val x = currentIndex.toFloat() / displayProfile.lastIndex.coerceAtLeast(1) * w
                val y = h - (((currentAlt - minAlt) / range).toFloat() * h * .82f + h * .06f)
                drawLine(Color(0xFFDD3344), Offset(x, 0f), Offset(x, h), 1f)
                drawCircle(Color(0xFF91A7C2), 6f, Offset(x, y))
                drawCircle(Color.White, 6f, Offset(x, y), style = Stroke(1.5f))

                rotate((-slopePercent * 1.8).toFloat().coerceIn(-22f,22f), Offset(x, y - 20f)) {
                    val vy = y - 20f
                    // eski uygulamadaki gibi çekici SUV + karavan silueti
                    drawRoundRect(Color(0xFFE8EEF2), Offset(x+4f,vy-18f), Size(42f,18f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(5f,5f))
                    drawRect(Color(0xFF7D98A9), Offset(x+10f,vy-14f), Size(12f,7f))
                    drawCircle(Color(0xFF111820),4f,Offset(x+14f,vy+1f)); drawCircle(Color(0xFF111820),4f,Offset(x+37f,vy+1f))
                    drawLine(Color(0xFFD7DEE3), Offset(x+4f,vy-3f), Offset(x-5f,vy-3f), 2f)
                    drawRoundRect(Color(0xFFE6EAED), Offset(x-40f,vy-15f), Size(34f,15f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(5f,5f))
                    drawRoundRect(Color(0xFFB8C8D1), Offset(x-32f,vy-22f), Size(18f,10f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(4f,4f))
                    drawCircle(Color(0xFF111820),4f,Offset(x-31f,vy+1f)); drawCircle(Color(0xFF111820),4f,Offset(x-12f,vy+1f))
                }
            }

            Column(Modifier.align(Alignment.TopStart)) {
                Text(if (isRouteMode) "← GERİ   ROTA / YOL EĞİMİ" else "← GERİ   CANLI / YOL EĞİMİ", color=CyanBright, fontSize=14.sp, fontWeight=FontWeight.Black, modifier=Modifier.clickable(onClick=onBack).padding(4.dp))
                Text("Şimdi ${altitudeMeters ?: 0} m    ${String.format(Locale.getDefault(), "%+.1f%%", slopePercent)}", color=slopeColor, fontSize=13.sp, fontWeight=FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.CenterStart), verticalArrangement=Arrangement.SpaceBetween) {
                for (i in 4 downTo 0) {
                    val a = minAlt + range * i / 4.0
                    Text("${a.roundToInt()}m", color=WarningYellow, fontSize=9.sp, fontWeight=FontWeight.Bold)
                    if (i > 0) Spacer(Modifier.height(13.dp))
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start=55.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("0km", color=WarningYellow, fontSize=9.sp, fontWeight=FontWeight.Bold)
                Text("${(tripDistanceLabel(displayProfile.size, .5f))}km", color=WarningYellow, fontSize=9.sp, fontWeight=FontWeight.Bold)
                Text("${tripDistanceLabel(displayProfile.size, 1f)}km", color=WarningYellow, fontSize=9.sp, fontWeight=FontWeight.Bold)
            }
            Text("▲ ${totalClimbMeters.roundToInt()}m   ▼ ${totalDescentMeters.roundToInt()}m", color=PrimaryText, fontSize=9.sp, fontWeight=FontWeight.Bold, modifier=Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun tripDistanceLabel(pointCount: Int, fraction: Float): Int =
    ((pointCount.coerceAtLeast(2) - 1) * fraction).roundToInt()

@Composable
private fun AltitudeHeaderCard(
    altitudeMeters: Int?,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier) {
        Text(
            text = "RAKIM",
            color = CyanBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = "${altitudeMeters ?: "--"} m",
            color = PrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MountainAltitudeArtwork(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val backMountain = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.28f, h * 0.48f)
            lineTo(w * 0.43f, h * 0.67f)
            lineTo(w * 0.63f, h * 0.28f)
            lineTo(w, h)
            close()
        }

        drawPath(
            path = backMountain,
            color = Color(0xFF183D63)
        )

        val frontMountain = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.22f, h * 0.62f)
            lineTo(w * 0.36f, h * 0.76f)
            lineTo(w * 0.58f, h * 0.38f)
            lineTo(w * 0.72f, h * 0.60f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w, h)
            close()
        }

        drawPath(
            path = frontMountain,
            color = Color(0xFF2E6DA0)
        )

        val snow = Path().apply {
            moveTo(w * 0.46f, h * 0.60f)
            lineTo(w * 0.58f, h * 0.38f)
            lineTo(w * 0.69f, h * 0.55f)
            lineTo(w * 0.62f, h * 0.51f)
            lineTo(w * 0.57f, h * 0.58f)
            lineTo(w * 0.53f, h * 0.52f)
            close()
        }

        drawPath(
            path = snow,
            color = Color(0xFFB9D9F2).copy(alpha = 0.88f)
        )

        drawLine(
            color = CyanAccent.copy(alpha = 0.42f),
            start = Offset(0f, h - 1.dp.toPx()),
            end = Offset(w, h - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun WeatherHeaderCard(
    weatherUiState: WeatherUiState,
    currentDate: String,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PanelBackground
        ),
        border = BorderStroke(
            width = 1.dp * strokeScale,
            color = when {
                weatherUiState.errorMessage != null && !weatherUiState.hasData ->
                    DangerRed.copy(alpha = 0.75f)

                weatherUiState.isUsingCachedData ->
                    WarningYellow.copy(alpha = 0.85f)

                else ->
                    PanelBorder
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(isExpanded) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (isExpanded) {
                                onDetails()
                            } else {
                                onExpand()
                            }
                        },
                        onTap = {
                            if (isExpanded) {
                                onCollapse()
                            }
                        }
                    )
                }
        ) {
            WeatherTemperatureScene(
                temperatureCelsius = weatherUiState.temperatureCelsius,
                condition = weatherUiState.condition,
                isDay = weatherUiState.isDay,
                windSpeedKmh = weatherUiState.windSpeedKmh,
                windGustKmh = weatherUiState.windGustKmh,
                modifier = Modifier.matchParentSize()
            )

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WeatherVectorIcon(
                        condition = weatherUiState.condition,
                        isDay = weatherUiState.isDay,
                        modifier = Modifier.size(58.dp)
                    )

                    Column(
                        modifier = Modifier.weight(0.8f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = weatherUiState.temperatureCelsius?.let {
                                String.format(
                                    Locale.getDefault(),
                                    "%.0f°",
                                    it
                                )
                            } ?: if (weatherUiState.isLoading) "…" else "--°",
                            color = PrimaryText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        Text(
                            text = weatherConditionText(
                                weatherUiState.condition
                            ),
                            color = CyanAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.6f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (weatherUiState.hasData) {
                            val feels =
                                weatherUiState.apparentTemperatureCelsius?.let {
                                    String.format(
                                        Locale.getDefault(),
                                        "%.0f°",
                                        it
                                    )
                                } ?: "--°"

                            val humidity =
                                weatherUiState.relativeHumidityPercent?.let {
                                    "$it%"
                                } ?: "--%"

                            val wind =
                                weatherUiState.windSpeedKmh?.let {
                                    String.format(
                                        Locale.getDefault(),
                                        "%.0f",
                                        it
                                    )
                                } ?: "--"

                            val gust =
                                weatherUiState.windGustKmh?.let {
                                    String.format(
                                        Locale.getDefault(),
                                        "%.0f",
                                        it
                                    )
                                } ?: "--"

                            Text(
                                text = weatherUiState.cityName ?: "Konum",
                                color = PrimaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Text(
                                text =
                                    if (weatherUiState.isUsingCachedData) {
                                        "Çevrimdışı • Son veri"
                                    } else {
                                        "Çevrim içi"
                                    },
                                color =
                                    if (weatherUiState.isUsingCachedData) {
                                        WarningYellow
                                    } else {
                                        SafeGreen
                                    },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Text(
                                text = "Hiss. $feels  •  Nem $humidity",
                                color = SecondaryText,
                                fontSize = 14.sp,
                                maxLines = 1
                            )

                            Text(
                                text =
                                    "Rüzgâr ${weatherUiState.windDirectionText} $wind km/h",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1
                            )

                            Text(
                                text = "Hamle $gust km/h",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = when {
                                    weatherUiState.isLoading ->
                                        "Hava bilgisi alınıyor…"

                                    weatherUiState.errorMessage != null ->
                                        "Hava verisi alınamadı"

                                    else ->
                                        "Konum bekleniyor"
                                },
                                color = SecondaryText,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    text = "Tek dokun: küçült • Çift dokun: detay",
                    modifier = Modifier.align(Alignment.BottomEnd),
                    color = SecondaryText.copy(alpha = 0.55f),
                    fontSize = 7.sp,
                    maxLines = 1
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherVectorIcon(
                        condition = weatherUiState.condition,
                        isDay = weatherUiState.isDay,
                        modifier = Modifier.size(40.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = weatherUiState.temperatureCelsius?.let {
                                    String.format(
                                        Locale.getDefault(),
                                        "%.0f°",
                                        it
                                    )
                                } ?: if (weatherUiState.isLoading) "…" else "--°",
                                color = PrimaryText,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Text(
                                text = weatherConditionText(
                                    weatherUiState.condition
                                ),
                                color = CyanAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = when {
                                weatherUiState.hasData -> {
                                    val feels =
                                        weatherUiState.apparentTemperatureCelsius?.let {
                                            String.format(
                                                Locale.getDefault(),
                                                "%.0f°",
                                                it
                                            )
                                        } ?: "--°"

                                    "Hiss. $feels"
                                }

                                weatherUiState.isLoading ->
                                    "Hava bilgisi alınıyor…"

                                weatherUiState.errorMessage != null ->
                                    "Hava verisi alınamadı"

                                else ->
                                    "Konum bekleniyor"
                            },
                            color = SecondaryText,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun WeatherVectorIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sunCenter = Offset(w * 0.40f, h * 0.38f)
        val sunRadius = min(w, h) * 0.17f
        val cloud = Color(0xFFD8E6F0)
        val rain = CyanAccent

        fun drawSun() {
            drawCircle(
                color = WarningYellow,
                radius = sunRadius,
                center = sunCenter
            )
            for (i in 0 until 8) {
                val angle = i * 45f
                val r1 = sunRadius * 1.35f
                val r2 = sunRadius * 1.75f
                drawLine(
                    color = WarningYellow,
                    start = pointOnCircle(sunCenter, r1, angle),
                    end = pointOnCircle(sunCenter, r2, angle),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        fun drawCloud() {
            drawCircle(cloud, h * 0.15f, Offset(w * 0.44f, h * 0.52f))
            drawCircle(cloud, h * 0.19f, Offset(w * 0.58f, h * 0.47f))
            drawCircle(cloud, h * 0.14f, Offset(w * 0.72f, h * 0.54f))
            drawRoundRect(
                color = cloud,
                topLeft = Offset(w * 0.31f, h * 0.51f),
                size = Size(w * 0.52f, h * 0.20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.10f)
            )
        }

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) {
                    drawSun()
                } else {
                    drawCircle(
                        color = CyanBright,
                        radius = h * 0.22f,
                        center = Offset(w * 0.50f, h * 0.45f)
                    )
                    drawCircle(
                        color = PanelBackground,
                        radius = h * 0.21f,
                        center = Offset(w * 0.59f, h * 0.37f)
                    )
                }
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSun()
                drawCloud()
            }
            WeatherCondition.CLOUDY, WeatherCondition.FOG -> {
                drawCloud()
                if (condition == WeatherCondition.FOG) {
                    repeat(2) { index ->
                        drawLine(
                            color = SecondaryText,
                            start = Offset(w * 0.28f, h * (0.79f + index * 0.10f)),
                            end = Offset(w * 0.82f, h * (0.79f + index * 0.10f)),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.FREEZING_RAIN,
            WeatherCondition.RAIN_SHOWERS -> {
                drawCloud()
                listOf(0.40f, 0.57f, 0.74f).forEach { x ->
                    drawLine(
                        color = rain,
                        start = Offset(w * x, h * 0.75f),
                        end = Offset(w * (x - 0.04f), h * 0.91f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> {
                drawCloud()
                listOf(0.40f, 0.58f, 0.75f).forEach { x ->
                    drawCircle(
                        color = CyanBright,
                        radius = 1.6.dp.toPx(),
                        center = Offset(w * x, h * 0.84f)
                    )
                }
            }
            WeatherCondition.THUNDERSTORM -> {
                drawCloud()
                val bolt = Path().apply {
                    moveTo(w * 0.56f, h * 0.70f)
                    lineTo(w * 0.47f, h * 0.84f)
                    lineTo(w * 0.56f, h * 0.82f)
                    lineTo(w * 0.50f, h * 0.98f)
                    lineTo(w * 0.68f, h * 0.76f)
                    lineTo(w * 0.58f, h * 0.78f)
                    close()
                }
                drawPath(bolt, WarningYellow)
            }
            WeatherCondition.UNKNOWN -> {
                drawCircle(
                    color = PanelBorder,
                    radius = h * 0.29f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

        }
    }
}

private fun weatherConditionText(condition: WeatherCondition): String =
    when (condition) {
        WeatherCondition.CLEAR -> "Açık"
        WeatherCondition.PARTLY_CLOUDY -> "Parçalı"
        WeatherCondition.CLOUDY -> "Bulutlu"
        WeatherCondition.FOG -> "Sisli"
        WeatherCondition.DRIZZLE -> "Çisenti"
        WeatherCondition.RAIN -> "Yağmur"
        WeatherCondition.FREEZING_RAIN -> "Donan yağmur"
        WeatherCondition.SNOW -> "Kar"
        WeatherCondition.RAIN_SHOWERS -> "Sağanak"
        WeatherCondition.SNOW_SHOWERS -> "Kar sağanağı"
        WeatherCondition.THUNDERSTORM -> "Fırtına"
        WeatherCondition.UNKNOWN -> "Hava"
    }


@Composable
private fun StatisticsHeaderButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF06111B)
        ),
        border = BorderStroke(
            1.5.dp,
            CyanAccent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "▥",
                color = CyanBright,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "İST",
                color = CyanAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun rememberHeaderSpinRotation(isActive: Boolean, durationMillis: Int): Float {
    if (!isActive) return 0f
    val infiniteTransition = rememberInfiniteTransition(label = "headerSpin")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerSpinValue"
    ).value
}

@Composable
private fun HeaderPngIcon(
    drawableRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    tintColor: Color? = null,
    rotationDegrees: Float = 0f,
    scale: Float = 1f
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxSize()
            // 58 dp kutuda taşma payını korur, PNG iç boşluklarının ikonu küçültmesini azaltır.
            .padding(3.dp)
            .graphicsLayer(
                alpha = alpha,
                rotationZ = rotationDegrees,
                scaleX = scale.coerceIn(0.90f, 1.32f),
                scaleY = scale.coerceIn(0.90f, 1.32f)
            ),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MediaHeaderButton(
    drawableRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(
        modifier = modifier,
        isActive = isActive,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleTap = onDoubleTap
    ) {
        if (isActive && contentDescription == "Müzik") {
            HeaderMusicEqualizer(modifier = Modifier.fillMaxSize())
        } else if (contentDescription == "Radyo") {
            // 141: Ekolayzer kaldırıldı. Radyo aktifken ikon hafifçe sola-sağa döner;
            // kapalıyken dönme olmaz.
            val radioSpinTransition = rememberInfiniteTransition(label = "headerRadioGentleSpin")
            val radioSpinAngle by radioSpinTransition.animateFloat(
                initialValue = -14f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "headerRadioGentleSpinAngle"
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HeaderPngIcon(
                    drawableRes = drawableRes,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                            rotationZ = if (isActive) radioSpinAngle else 0f
                        },
                    alpha = if (isActive) 1f else .94f,
                    scale = 1.0f
                )
            }
        } else {
            HeaderPngIcon(
                drawableRes = drawableRes,
                contentDescription = contentDescription,
                modifier = Modifier,
                alpha = if (isActive) 1f else .94f,
                scale = 1.0f
            )
        }
    }
}

@Composable
private fun HeaderMusicEqualizer(modifier: Modifier) {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(0.12f) }
    LaunchedEffect(Unit) {
        while (true) {
            level = MediaAppController.currentAudioLevel(context).coerceIn(0.08f, 1f)
            delay(45L)
        }
    }

    val transition = rememberInfiniteTransition(label = "headerMusicEqualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerMusicEqualizerPhase"
    )

    Row(
        modifier = modifier.padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { i ->
            val wave = ((kotlin.math.sin(phase + i * .9f) + 1f) / 2f)
            val heightFraction = (.18f + .78f * level * (.38f + .62f * wave)).coerceIn(.18f, .96f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .width(7.dp)
                    .background(SafeGreen, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun SpeedCorridorHeaderButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, isActive = isActive, onClick = onClick) {
        Text(
            text = "Kdor",
            color = if (isActive) SafeGreen else CyanBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = if (isActive) "AKTİF" else "A",
            color = if (isActive) SafeGreen else CyanAccent,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PrayerHeaderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    HeaderStandardCard(modifier = modifier, onClick = onClick) {
        Text("NAM", color = CyanBright, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("☾", color = CyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ObdHeaderButton(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, isActive = isConnected, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_obd,
            contentDescription = "OBD",
            modifier = Modifier,
            alpha = if (isConnected) 1f else .9f,
            scale = 1.20f
        )
    }
}

@Composable
private fun TrafficAgentHeaderButton(
    isActive: Boolean,
    delaySeconds: Int,
    incidentCount: Int,
    severity: Int,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "header_traffic_radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "header_traffic_radar_sweep"
    )
    val score = status.substringAfter("PUAN:", "").substringBefore(' ').toIntOrNull()?.coerceIn(0, 10)
    val radarColor = when {
        !isActive -> SecondaryText.copy(alpha = .86f)
        score == null -> CyanAccent
        score <= 2 -> SafeGreen
        score <= 4 -> WarningYellow
        score <= 6 -> Color(0xFFFF8A3D)
        else -> DangerRed
    }

    HeaderStandardCard(modifier = modifier, isActive = isActive, onClick = onClick) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(56.dp).padding(2.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * .46f
                drawCircle(Color(0xFF07151D), radius, center)
                for (ring in 1..3) {
                    drawCircle(
                        radarColor.copy(alpha = if (isActive) .55f else .34f),
                        radius * ring / 3f,
                        center,
                        style = Stroke(width = 1.7.dp.toPx())
                    )
                }
                drawLine(radarColor.copy(alpha = .32f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.2.dp.toPx())
                drawLine(radarColor.copy(alpha = .32f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.2.dp.toPx())
                rotate(degrees = if (isActive) sweepAngle else -90f, pivot = center) {
                    drawArc(
                        color = radarColor.copy(alpha = if (isActive) .30f else .08f),
                        startAngle = -42f,
                        sweepAngle = 42f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    drawLine(
                        radarColor.copy(alpha = if (isActive) .98f else .40f),
                        center,
                        Offset(center.x + radius, center.y),
                        strokeWidth = 3.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(radarColor, 2.5.dp.toPx(), center)
            }
            if (score != null || isActive) {
                Text(
                    text = score?.let { "$it/10" } ?: "…",
                    color = radarColor,
                    fontSize = if (score != null) 9.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp)
                        .background(Color(0xD906111B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderStandardCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null || onLongClick != null || onDoubleTap != null) {
        modifier.pointerInput(enabled, onClick, onLongClick, onDoubleTap) {
            detectTapGestures(
                onTap = { if (enabled) onClick?.invoke() },
                onDoubleTap = { if (enabled) onDoubleTap?.invoke() },
                onLongPress = { if (enabled) onLongClick?.invoke() }
            )
        }
    } else modifier

    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = clickableModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF082A25) else Color(0xFF06111B)
        ),
        border = BorderStroke(
            width = (if (isActive) 1.7.dp else 1.dp) * strokeScale,
            color = when {
                !enabled -> PanelBorder.copy(alpha = .45f)
                isActive -> SafeGreen
                else -> CyanAccent.copy(alpha = .78f)
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = { content() }
        )
    }
}

@Composable
private fun HomeHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_home,
            contentDescription = "Eve Git",
            modifier = Modifier,
            scale = 1.10f
        )
    }
}

@Composable
private fun WorkHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, onClick = onClick) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = 3.2.dp.toPx()
            drawRoundRect(
                color = CyanAccent,
                topLeft = Offset(size.width * .08f, size.height * .27f),
                size = Size(size.width * .84f, size.height * .62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                style = Stroke(width = stroke)
            )
            drawLine(CyanAccent, Offset(size.width * .34f, size.height * .27f), Offset(size.width * .34f, size.height * .10f), stroke)
            drawLine(CyanAccent, Offset(size.width * .34f, size.height * .10f), Offset(size.width * .66f, size.height * .10f), stroke)
            drawLine(CyanAccent, Offset(size.width * .66f, size.height * .10f), Offset(size.width * .66f, size.height * .27f), stroke)
            drawLine(CyanAccent, Offset(size.width * .08f, size.height * .55f), Offset(size.width * .92f, size.height * .55f), stroke)
            // Minik çanta kilitleme dili / toka.
            drawRoundRect(
                color = CyanAccent,
                topLeft = Offset(size.width * .43f, size.height * .49f),
                size = Size(size.width * .14f, size.height * .18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF06111B),
                topLeft = Offset(size.width * .47f, size.height * .53f),
                size = Size(size.width * .06f, size.height * .08f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
    }
}

@Composable
private fun WifiHeaderButton(
    isConnected: Boolean,
    shortName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, isActive = isConnected, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_wifi,
            contentDescription = "Wi-Fi",
            modifier = Modifier,
            alpha = if (isConnected) 1f else .84f,
            scale = 1.08f
        )
    }
}

@Composable
private fun BluetoothHeaderButton(
    isConnected: Boolean,
    shortName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, isActive = isConnected, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_bluetooth,
            contentDescription = "Bluetooth",
            modifier = Modifier,
            alpha = if (isConnected) 1f else .84f,
            scale = 1.16f
        )
    }
}

@Composable
private fun GlobeHeaderButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spinRotation = rememberHeaderSpinRotation(isActive = isActive, durationMillis = 6400)
    HeaderStandardCard(modifier = modifier, isActive = isActive, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_navigation,
            contentDescription = "Navigasyon",
            modifier = Modifier,
            alpha = if (isActive) 1f else .94f,
            rotationDegrees = spinRotation,
            scale = 1.08f
        )
    }
}

@Composable
private fun SatelliteHeaderButton(
    isPanelActive: Boolean,
    isSignalActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val signalTransition = rememberInfiniteTransition(label = "liveAltitudeSignal")
    val signalProgress by signalTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liveAltitudeSignalProgress"
    )

    HeaderStandardCard(modifier = modifier, isActive = isPanelActive, enabled = isEnabled, onClick = onClick) {
        Box(Modifier.fillMaxSize()) {
            HeaderPngIcon(
                drawableRes = R.drawable.icon_live,
                contentDescription = "Canlı Yükseklik",
                modifier = Modifier,
                alpha = if (isEnabled) 1f else .42f,
                scale = 1.30f
            )
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * .648f, size.height * .409f)
                val coreRadius = size.minDimension * .052f
                val maxSignalRadius = size.minDimension * .225f

                if (isSignalActive && isEnabled) {
                    listOf(signalProgress, (signalProgress + .5f) % 1f).forEach { progress ->
                        drawCircle(
                            color = Color(0xFFFF3030).copy(alpha = (1f - progress) * .78f),
                            radius = coreRadius + (maxSignalRadius - coreRadius) * progress,
                            center = center,
                            style = Stroke(width = (2.2.dp.toPx() * (1f - progress * .55f)))
                        )
                    }
                }
                drawCircle(
                    color = Color(0xFFFF3030).copy(alpha = if (isEnabled) 1f else .34f),
                    radius = coreRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFB0B0).copy(alpha = if (isEnabled) .92f else .28f),
                    radius = coreRadius * .42f,
                    center = center
                )
            }
        }
    }
}

@Composable
private fun SettingsHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderStandardCard(modifier = modifier, onClick = onClick) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_settings,
            contentDescription = "Ayarlar",
            modifier = Modifier,
            scale = 1.08f
        )
    }
}

@Composable
private fun TopCompassButton(
    direction: String,
    headingDegrees: Float?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spinRotation = rememberHeaderSpinRotation(isActive = isSelected, durationMillis = 5200)
    HeaderStandardCard(
        modifier = modifier,
        isActive = isSelected,
        onClick = onClick
    ) {
        HeaderPngIcon(
            drawableRes = R.drawable.icon_compass,
            contentDescription = "Pusula",
            modifier = Modifier,
            alpha = if (isSelected) 1f else .94f,
            rotationDegrees = spinRotation,
            scale = 1.08f
        )
    }
}

@Composable
private fun MiniCompassDial(
    headingDegrees: Float?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(
            size.width / 2f,
            size.height / 2f
        )

        val radius =
            min(size.width, size.height) * 0.46f

        drawCircle(
            color = Color(0xFF0B1826),
            radius = radius,
            center = center
        )

        drawCircle(
            color = Color(0xFF536D81),
            radius = radius,
            center = center,
            style = Stroke(
                width = 1.3.dp.toPx()
            )
        )

        for (degree in 0 until 360 step 45) {
            val outer =
                pointOnCircle(
                    center,
                    radius * 0.88f,
                    degree.toFloat()
                )

            val inner =
                pointOnCircle(
                    center,
                    radius * 0.68f,
                    degree.toFloat()
                )

            drawLine(
                color = PrimaryText,
                start = inner,
                end = outer,
                strokeWidth = 1.5.dp.toPx()
            )
        }

        val heading = headingDegrees ?: 0f
        val northAngle = -heading

        val northPoint =
            pointOnCircle(
                center,
                radius * 0.70f,
                northAngle
            )

        val southPoint =
            pointOnCircle(
                center,
                radius * 0.55f,
                northAngle + 180f
            )

        drawLine(
            color = DangerRed,
            start = center,
            end = northPoint,
            strokeWidth = 3.dp.toPx()
        )

        drawLine(
            color = PrimaryText,
            start = center,
            end = southPoint,
            strokeWidth = 2.dp.toPx()
        )

        drawCircle(
            color = PrimaryText,
            radius = 3.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun LandscapeDashboard(
    halfTrackingActive: Boolean,
    speedKmh: Int,
    speedDataSource: String,
    speedColor: Color,
    speedLimitKmh: Int?,
    speedLimitSource: SpeedLimitSource,
    speedWarningState: SpeedWarningState,
    transientVehicleAlertMessage: String?,
    obdAlertMessage: String?,
    isParkMode: Boolean,
    parkDurationSeconds: Long,
    isCompassPanelVisible: Boolean,
    isCompassSensorAvailable: Boolean,
    compassHeadingDegrees: Float?,
    compassAccuracy: CompassAccuracy,
    qiblaBearingDegrees: Float?,
    direction: String,
    altitudeMeters: Int?,
    weatherUiState: WeatherUiState,
    tripDistanceKm: Double,
    isTripActive: Boolean,
    isSpeedCorridorActive: Boolean,
    speedCorridorDistanceKm: Double,
    speedCorridorDurationSeconds: Long,
    speedCorridorAverageSpeedKmh: Double,
    upcomingRoadSigns: List<UpcomingRoadSignInfo>,
    tripDurationSeconds: Long,
    tripMovingDurationSeconds: Long,
    tripParkDurationSeconds: Long,
    tripAverageSpeedKmh: Double,
    tripMaxSpeedKmh: Int,
    tripEstimatedFuelConsumedLiters: Double,
    tripEstimatedFuelCost: Double,
    todayEstimatedFuelConsumedLiters: Double,
    todayEstimatedFuelCost: Double,
    todayTotalDistanceKm: Double,
    tripStartedAtEpochMillis: Long?,
    tripEndedAtEpochMillis: Long?,
    vehicleMode: String,
    selectedVehicleId: String,
    customCarImageUri: String?,
    customCaravanImageUri: String?,
    fuelPercentage: Double?,
    fuelDataSource: String,
    remainingFuelLiters: Double?,
    estimatedFuelRangeKm: Double?,
    isLowFuel: Boolean,
    sunriseTime: String,
    sunsetTime: String,
    currentDate: String,
    currentTime: String,
    onClockClick: () -> Unit,
    onFuelClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onWeatherDetailsClick: () -> Unit,
    onVehicleModeChange: (String) -> Unit,
    onTripStart: () -> Unit,
    onTripStop: () -> Unit
) {
    var isWeatherExpanded by remember {
        mutableStateOf(false)
    }

    val weatherPanelWeight by animateFloatAsState(
        targetValue = if (isWeatherExpanded) 1.75f else 1f,
        animationSpec = tween(durationMillis = 280),
        label = "weatherPanelWeight"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.82f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DashboardClockPanel(
                    currentTime = currentTime,
                    currentDate = currentDate,
                    isSpeedCorridorActive = isSpeedCorridorActive,
                    speedCorridorAverageSpeedKmh = speedCorridorAverageSpeedKmh,
                    onClick = onClockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.88f)
                )

                FuelGaugeCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.12f),
                    fuelPercentage = fuelPercentage,
                    fuelDataSource = fuelDataSource,
                    remainingFuelLiters = remainingFuelLiters,
                    estimatedFuelRangeKm = estimatedFuelRangeKm,
                    isLowFuel = isLowFuel,
                    onClick = onFuelClick
                )
            }

            WeatherHeaderCard(
                weatherUiState = weatherUiState,
                currentDate = currentDate,
                isExpanded = isWeatherExpanded,
                onExpand = { isWeatherExpanded = true },
                onCollapse = { isWeatherExpanded = false },
                onDetails = onWeatherDetailsClick,
                modifier = Modifier
                    .weight(weatherPanelWeight)
                    .fillMaxWidth()
            )
        }

        SpeedPanel(
            modifier = Modifier
                .weight(if (halfTrackingActive) 0.82f else 1.08f)
                .fillMaxHeight(),
            speedKmh = speedKmh,
            speedDataSource = speedDataSource,
            speedColor = speedColor,
            speedLimitKmh = speedLimitKmh,
            speedLimitSource = speedLimitSource,
            speedWarningState = speedWarningState,
            isParkMode = isParkMode,
            parkDurationSeconds = parkDurationSeconds,
            dailyFuelCost = todayEstimatedFuelCost,
            dailyFuelLiters = todayEstimatedFuelConsumedLiters,
            dailyDistanceKm = todayTotalDistanceKm,
            isCompassPanelVisible = isCompassPanelVisible,
            isCompassSensorAvailable = isCompassSensorAvailable,
            compassHeadingDegrees = compassHeadingDegrees,
            compassAccuracy = compassAccuracy,
            qiblaBearingDegrees = qiblaBearingDegrees,
            direction = direction,
            altitudeMeters = altitudeMeters,
            compact = halfTrackingActive,
            sunriseTime = sunriseTime,
            sunsetTime = sunsetTime,
            currentDate = currentDate
        )

        if (halfTrackingActive) {
            // Yarım ekran takip aktifken YOLCULUK ve ARAÇ MODU tamamen kapanır.
            // Sağ yarı, üstteki takip paneli için boş bırakılır.
            Spacer(
                modifier = Modifier
                    .weight(1.64f)
                    .fillMaxHeight()
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(0.82f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnhancedMultimediaCard(
                    speedKmh = speedKmh,
                    modifier = Modifier.weight(1f)
                )

                VehicleModeCard(
                    modifier = Modifier.weight(1f),
                    vehicleMode = vehicleMode,
                    selectedVehicleId = selectedVehicleId,
                    customCarImageUri = customCarImageUri,
                    customCaravanImageUri = customCaravanImageUri,
                    speedWarningState = speedWarningState,
                    transientVehicleAlertMessage = transientVehicleAlertMessage,
                    obdAlertMessage = obdAlertMessage,
                    onVehicleModeChange = onVehicleModeChange
                )
            }
        }
    }
}

@Composable
private fun PortraitDashboard(
    speedKmh: Int,
    speedDataSource: String,
    speedColor: Color,
    speedLimitKmh: Int?,
    speedLimitSource: SpeedLimitSource,
    speedWarningState: SpeedWarningState,
    transientVehicleAlertMessage: String?,
    obdAlertMessage: String?,
    isParkMode: Boolean,
    parkDurationSeconds: Long,
    isCompassPanelVisible: Boolean,
    isCompassSensorAvailable: Boolean,
    compassHeadingDegrees: Float?,
    compassAccuracy: CompassAccuracy,
    qiblaBearingDegrees: Float?,
    direction: String,
    altitudeMeters: Int?,
    weatherUiState: WeatherUiState,
    tripDistanceKm: Double,
    isTripActive: Boolean,
    isSpeedCorridorActive: Boolean,
    speedCorridorDistanceKm: Double,
    speedCorridorDurationSeconds: Long,
    speedCorridorAverageSpeedKmh: Double,
    upcomingRoadSigns: List<UpcomingRoadSignInfo>,
    tripDurationSeconds: Long,
    tripMovingDurationSeconds: Long,
    tripParkDurationSeconds: Long,
    tripAverageSpeedKmh: Double,
    tripMaxSpeedKmh: Int,
    tripEstimatedFuelConsumedLiters: Double,
    tripEstimatedFuelCost: Double,
    todayEstimatedFuelConsumedLiters: Double,
    todayEstimatedFuelCost: Double,
    todayTotalDistanceKm: Double,
    tripStartedAtEpochMillis: Long?,
    tripEndedAtEpochMillis: Long?,
    vehicleMode: String,
    selectedVehicleId: String,
    customCarImageUri: String?,
    customCaravanImageUri: String?,
    fuelPercentage: Double?,
    fuelDataSource: String,
    remainingFuelLiters: Double?,
    estimatedFuelRangeKm: Double?,
    isLowFuel: Boolean,
    sunriseTime: String,
    sunsetTime: String,
    currentDate: String,
    currentTime: String,
    onClockClick: () -> Unit,
    onFuelClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onWeatherDetailsClick: () -> Unit,
    onVehicleModeChange: (String) -> Unit,
    onTripStart: () -> Unit,
    onTripStop: () -> Unit
) {
    var isWeatherExpanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpeedPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.25f),
            speedKmh = speedKmh,
            speedDataSource = speedDataSource,
            speedColor = speedColor,
            speedLimitKmh = speedLimitKmh,
            speedLimitSource = speedLimitSource,
            speedWarningState = speedWarningState,
            isParkMode = isParkMode,
            parkDurationSeconds = parkDurationSeconds,
            dailyFuelCost = todayEstimatedFuelCost,
            dailyFuelLiters = todayEstimatedFuelConsumedLiters,
            dailyDistanceKm = todayTotalDistanceKm,
            isCompassPanelVisible = isCompassPanelVisible,
            isCompassSensorAvailable = isCompassSensorAvailable,
            compassHeadingDegrees = compassHeadingDegrees,
            compassAccuracy = compassAccuracy,
            qiblaBearingDegrees = qiblaBearingDegrees,
            direction = direction,
            altitudeMeters = altitudeMeters,
            compact = true,
            sunriseTime = sunriseTime,
            sunsetTime = sunsetTime,
            currentDate = currentDate
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DashboardClockPanel(
                    currentTime = currentTime,
                    currentDate = currentDate,
                    isSpeedCorridorActive = isSpeedCorridorActive,
                    speedCorridorAverageSpeedKmh = speedCorridorAverageSpeedKmh,
                    onClick = onClockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.88f)
                )
                FuelGaugeCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.12f),
                    fuelPercentage = fuelPercentage,
                    fuelDataSource = fuelDataSource,
                    remainingFuelLiters = remainingFuelLiters,
                    estimatedFuelRangeKm = estimatedFuelRangeKm,
                    isLowFuel = isLowFuel,
                    onClick = onFuelClick
                )
            }

            WeatherHeaderCard(
                weatherUiState = weatherUiState,
                currentDate = currentDate,
                isExpanded = isWeatherExpanded,
                onExpand = { isWeatherExpanded = true },
                onCollapse = { isWeatherExpanded = false },
                onDetails = onWeatherDetailsClick,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EnhancedMultimediaCard(
                speedKmh = speedKmh,
                modifier = Modifier.weight(1f)
            )

            VehicleModeCard(
                modifier = Modifier.weight(1f),
                vehicleMode = vehicleMode,
                selectedVehicleId = selectedVehicleId,
                customCarImageUri = customCarImageUri,
                customCaravanImageUri = customCaravanImageUri,
                speedWarningState = speedWarningState,
                transientVehicleAlertMessage = transientVehicleAlertMessage,
                obdAlertMessage = obdAlertMessage,
                onVehicleModeChange = onVehicleModeChange
            )
        }
    }
}

@Composable
private fun SpeedPanel(
    modifier: Modifier,
    speedKmh: Int,
    speedDataSource: String,
    speedColor: Color,
    speedLimitKmh: Int?,
    speedLimitSource: SpeedLimitSource,
    speedWarningState: SpeedWarningState,
    isParkMode: Boolean,
    parkDurationSeconds: Long,
    dailyFuelCost: Double,
    dailyFuelLiters: Double,
    dailyDistanceKm: Double,
    isCompassPanelVisible: Boolean,
    isCompassSensorAvailable: Boolean,
    compassHeadingDegrees: Float?,
    compassAccuracy: CompassAccuracy,
    qiblaBearingDegrees: Float?,
    direction: String,
    altitudeMeters: Int?,
    compact: Boolean,
    sunriseTime: String,
    sunsetTime: String,
    currentDate: String
) {
    val strokeScale = LocalDashboardStrokeScale.current
    val fuelCostPerKm = if (dailyDistanceKm > 0.01) dailyFuelCost / dailyDistanceKm else 0.0

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = PanelBackground
        ),
        border = BorderStroke(
            1.dp * strokeScale,
            if (DashboardPaletteRuntime.isSunlight) PanelBorder else Color(0xFF294B63)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            val panelContext = LocalContext.current
            val panelStars = panelContext.getSharedPreferences("night_visual_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("stars_enabled", true)
            if (panelStars && !DashboardPaletteRuntime.isDay) {
                NightStarField(Modifier.fillMaxSize().padding(3.dp))
            }
            when {
                isCompassPanelVisible -> {
                    LargeCompassPanel(
                        isSensorAvailable = isCompassSensorAvailable,
                        headingDegrees = compassHeadingDegrees,
                        qiblaBearingDegrees = qiblaBearingDegrees,
                        direction = direction,
                        accuracy = compassAccuracy,
                        compact = compact,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isParkMode -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SunOrbitMarker(
                            sunriseTime = sunriseTime,
                            sunsetTime = sunsetTime,
                            compact = compact,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = if (compact) 8.dp else 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TL", color = WarningYellow, fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Black)
                            Text(
                                text = String.format(Locale("tr", "TR"), "%.1f ₺", dailyFuelCost),
                                color = PrimaryText,
                                fontSize = if (compact) 16.sp else 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (compact) 3.dp else 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "GÜN KM",
                                color = WarningYellow,
                                fontSize = if (compact) 12.sp else 16.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = String.format(
                                    Locale("tr", "TR"),
                                    "%.1f KM / %.2f ₺/KM",
                                    dailyDistanceKm,
                                    fuelCostPerKm
                                ),
                                color = PrimaryText,
                                fontSize = if (compact) 13.sp else 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationY = -(if (compact) 2.dp else 4.dp).toPx()
                                }
                                .size(
                                    width = if (compact) 92.dp else 120.dp,
                                    height = if (compact) 132.dp else 170.dp
                                )
                                .background(
                                    color = Color(0xFF006FD6),
                                    shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
                                )
                                .border(
                                    width = 3.dp,
                                    color = CyanAccent,
                                    shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "P",
                                color = PrimaryText,
                                fontSize = if (compact) 78.sp else 104.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Text(
                            text = formatParkDuration(parkDurationSeconds),
                            color = PrimaryText,
                            fontSize = if (compact) 25.sp else 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (compact) 35.dp else 44.dp)
                        )

                        Text(
                            text = currentDate,
                            color = CyanAccent.copy(alpha = .95f),
                            fontSize = if (compact) 12.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = if (compact) 8.dp else 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("LT", color = CyanAccent, fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Black)
                            Text(
                                text = String.format(Locale("tr", "TR"), "%.2f L", dailyFuelLiters),
                                color = PrimaryText,
                                fontSize = if (compact) 16.sp else 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                else -> {
                    SpeedometerGauge(
                        speedKmh = speedKmh,
                        speedDataSource = speedDataSource,
                        compassHeadingDegrees = compassHeadingDegrees,
                        speedColor = speedColor,
                        speedLimitKmh = speedLimitKmh,
                        speedLimitSource = speedLimitSource,
                        speedWarningState = speedWarningState,
                        compact = compact,
                        altitudeMeters = altitudeMeters,
                        sunriseTime = sunriseTime,
                        sunsetTime = sunsetTime,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SunOrbitMarker(
    sunriseTime: String,
    sunsetTime: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val hour by produceState(initialValue = LocalDateTime.now().hour) {
        while (true) {
            value = LocalDateTime.now().hour
            delay(60_000L)
        }
    }
    fun hourOf(value: String, fallback: Int): Int =
        runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm")).hour }.getOrDefault(fallback)

    val sunriseHour = hourOf(sunriseTime, 6)
    val sunsetHour = hourOf(sunsetTime, 18)
    val sunColor = when {
        hour == sunriseHour || hour == sunsetHour -> Color(0xFFFF8A24)
        hour in sunriseHour..sunsetHour -> Color(0xFFFFD43B)
        else -> Color(0xFFE34234)
    }

    Canvas(modifier = modifier) {
        val symbolRadius = (if (compact) 7.dp else 10.dp).toPx()
        val orbitX = size.width / 2f - symbolRadius - 1.dp.toPx()
        val orbitY = size.height / 2f - symbolRadius - 1.dp.toPx()
        val angle = Math.toRadians((90.0 + hour * 15.0))
        val center = Offset(
            x = size.width / 2f + cos(angle).toFloat() * orbitX,
            y = size.height / 2f + sin(angle).toFloat() * orbitY
        )
        repeat(8) { index ->
            val ray = Math.toRadians(index * 45.0)
            val start = symbolRadius * 1.28f
            val end = symbolRadius * 1.72f
            drawLine(
                color = sunColor,
                start = Offset(center.x + cos(ray).toFloat() * start, center.y + sin(ray).toFloat() * start),
                end = Offset(center.x + cos(ray).toFloat() * end, center.y + sin(ray).toFloat() * end),
                strokeWidth = (if (compact) 1.4.dp else 1.9.dp).toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = sunColor.copy(alpha = .22f), radius = symbolRadius * 1.45f, center = center)
        drawCircle(color = sunColor, radius = symbolRadius, center = center)
    }
}

@Composable
private fun SpeedometerGauge(
    speedKmh: Int,
    speedDataSource: String,
    compassHeadingDegrees: Float?,
    speedColor: Color,
    speedLimitKmh: Int?,
    speedLimitSource: SpeedLimitSource,
    speedWarningState: SpeedWarningState,
    compact: Boolean,
    altitudeMeters: Int?,
    sunriseTime: String,
    sunsetTime: String,
    modifier: Modifier = Modifier
) {
    val strokeScale = LocalDashboardStrokeScale.current


    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        DaciaNightRoadAnimation(
            speedKmh = speedKmh,
            compassHeadingDegrees = compassHeadingDegrees,
            compact = compact,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.70f)
        )

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center =
                Offset(
                    size.width / 2f,
                    size.height * 0.43f
                )

            val radius =
                min(
                    size.width * 0.44f,
                    size.height * 0.40f
                )

            if (radius <= 0f) {
                return@Canvas
            }

            // _41: dış çap değişmeden renkli hız halkası merkeze doğru %50 kalınlaşır.
            // _46: dış kenar sabit; ek kalınlık yalnız merkeze doğru büyür.
            // Dış çap sabit kalırken ilave kalınlığın tamamı kadranın içine büyür.
            val speedRingWidth = 16.dp.toPx() * strokeScale
            val speedRingOuterRadius = radius + (5.2.dp.toPx() * strokeScale)
            val speedRingRadius = speedRingOuterRadius - speedRingWidth / 2f

            drawArc(
                color = Color(0xFF0B3737),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(
                    center.x - speedRingRadius,
                    center.y - speedRingRadius
                ),
                size = Size(
                    speedRingRadius * 2f,
                    speedRingRadius * 2f
                ),
                style = Stroke(
                    width = speedRingWidth,
                    cap = StrokeCap.Round
                )
            )

            val progress =
                (speedKmh / 160f)
                    .coerceIn(0f, 1f)

            drawArc(
                color = speedColor,
                startAngle = 140f,
                sweepAngle = 260f * progress,
                useCenter = false,
                topLeft = Offset(
                    center.x - speedRingRadius,
                    center.y - speedRingRadius
                ),
                size = Size(
                    speedRingRadius * 2f,
                    speedRingRadius * 2f
                ),
                style = Stroke(
                    width = speedRingWidth,
                    cap = StrokeCap.Round
                )
            )

            for (i in 0..20) {
                val angle =
                    -130f + i * 13f

                val outer =
                    pointOnCircle(
                        center,
                        radius * 0.90f,
                        angle
                    )

                val inner =
                    pointOnCircle(
                        center,
                        radius *
                                if (i % 5 == 0) 0.79f
                                else 0.84f,
                        angle
                    )

                drawLine(
                    color =
                        if (i % 5 == 0)
                            SafeGreen
                        else
                            Color(0xFF127070),
                    start = inner,
                    end = outer,
                    strokeWidth =
                        if (i % 5 == 0)
                            2.dp.toPx() * strokeScale
                        else
                            1.dp.toPx() * strokeScale
                )
            }

        }

        // Gerçek/aktif yol hız sınırı levhası + veri kaynağı
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (compact) 4.dp else 6.dp,
                    end = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .size(if (compact) 48.dp else 58.dp),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = DashboardPaletteRuntime.primaryText
                ),
                border = BorderStroke(
                    width = (if (compact) 4.dp else 5.dp) * strokeScale,
                    color = DangerRed
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speedLimitKmh?.toString() ?: "--",
                        color = Color(0xFF111111),
                        fontSize = if (compact) 18.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    if (compact) 2.dp else 3.dp
                )
            )

            Text(
                text = "YOL",
                color =
                    if (speedLimitSource == SpeedLimitSource.ROAD) {
                        SafeGreen
                    } else {
                        SecondaryText
                    },
                fontSize = if (compact) 7.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        SpeedAltitudeBadge(
            altitudeMeters = altitudeMeters,
            compact = compact,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-3).dp)
                .padding(
                    top = if (compact) 4.dp else 6.dp,
                    start = 0.dp
                )
        )

        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (compact) (-105).dp else (-140).dp)
                .zIndex(8f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xD907131B)
            ),
            border = BorderStroke(
                width = 1.dp * strokeScale,
                color = PanelBorder
            )
        ) {
            Text(
                text = "km/h",
                color = PrimaryText,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = if (compact) 9.dp else 11.dp,
                    vertical = if (compact) 3.dp else 4.dp
                )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = if (compact) 54.dp else 78.dp)
                .zIndex(9f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = speedKmh.toString(),
                color = speedColor,
                fontSize = if (compact) 86.sp else 116.sp,
                fontWeight = FontWeight.Black
            )
        }

        SunriseBlock(
            title = sunriseTime,
            isSunrise = true,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 10.dp,
                    bottom = 5.dp
                )
        )

        SunriseBlock(
            title = sunsetTime,
            isSunrise = false,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 10.dp,
                    bottom = 5.dp
                )
        )

    }
}

internal fun daciaNightRoadAnimationRate(speedKmh: Int): Float =
    if (speedKmh <= 2) 0f else speedKmh.coerceAtMost(160) / 1200f

internal fun shortestNightRoadHeadingDelta(previous: Float, current: Float): Float =
    ((current - previous + 540f) % 360f) - 180f

@Composable
private fun DaciaNightRoadAnimation(
    speedKmh: Int,
    compassHeadingDegrees: Float?,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(.16f) }
    var previousHeading by remember { mutableStateOf<Float?>(null) }
    val smoothedRate = remember { Animatable(daciaNightRoadAnimationRate(speedKmh)) }
    val smoothedTurn = remember { Animatable(0f) }

    LaunchedEffect(speedKmh) {
        smoothedRate.animateTo(
            targetValue = daciaNightRoadAnimationRate(speedKmh),
            animationSpec = tween(durationMillis = 1_800, easing = LinearEasing)
        )
    }

    LaunchedEffect(Unit) {
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val elapsedSeconds =
                ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .05f)
            previousFrame = frame
            phase = (phase + elapsedSeconds * smoothedRate.value) % 1f
        }
    }

    LaunchedEffect(compassHeadingDegrees, speedKmh) {
        val current = compassHeadingDegrees
        val previous = previousHeading
        previousHeading = current
        val target =
            if (speedKmh > 2 && current != null && previous != null) {
                val delta = shortestNightRoadHeadingDelta(previous, current)
                if (kotlin.math.abs(delta) < 2f) 0f
                else (delta * .035f).coerceIn(-1.2f, 1.2f)
            } else {
                0f
            }

        smoothedTurn.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 1_750)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(PanelBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.018f
                    scaleY = 1.018f
                    rotationZ = smoothedTurn.value
                }
        ) {
            Image(
                painter = painterResource(R.drawable.aldanmaz_drive_road_scene_night_109),
                contentDescription = "07 MTU 93 plakalı Dacia gece yol animasyonu",
                // Sahnenin mavi yol sınırları ve hareketli reflektör koordinatları
                // aynı normalize alanı kullanır; kırpma hizayı bozacağı için tam alana yayılır.
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PanelBackground.copy(alpha = .54f),
                                PanelBackground.copy(alpha = .12f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val visualScale = if (compact) .88f else 1f

                fun perspective(value: Float): Float =
                    value.coerceIn(0f, 1f).pow(1.72f)

                fun roadPoint(bottomX: Float, horizonX: Float, value: Float): Offset {
                    val q = perspective(value)
                    return Offset(
                        x = horizonX + (bottomX - horizonX) * q,
                        y = size.height * .39f + size.height * .69f * q
                    )
                }

                fun edgePoint(side: Int, value: Float): Pair<Offset, Float> {
                    val q = perspective(value)
                    // 109: Reflektörler gece görselindeki neon mavi yol sınırlarının
                    // gerçek perspektif eğrisine oturtuldu. Sol beyaz ve sağ kırmızı
                    // reflektörler artık çizginin iç/dış tarafına kaçmıyor.
                    val fittedX =
                        if (side < 0) {
                            .5682f * (1f - q) + (-.2953f) * q - .1653f * q * (1f - q)
                        } else {
                            .5312f * (1f - q) + 1.1537f * q + .0186f * q * (1f - q)
                        }
                    // Ufukta iki çizgi birbirine çok yaklaştığı için ilk bölüm ayrıca
                    // düzgün bir vanishing-point geçişiyle sabitlenir; böylece sağ kırmızı
                    // reflektörler ufukta sol tarafa atlamaz.
                    val horizonBlendEnd = .12f
                    val horizonX = if (side < 0) .555f else .595f
                    val fitAtBlend =
                        if (side < 0) {
                            .5682f * (1f - horizonBlendEnd) + (-.2953f) * horizonBlendEnd -
                                    .1653f * horizonBlendEnd * (1f - horizonBlendEnd)
                        } else {
                            .5312f * (1f - horizonBlendEnd) + 1.1537f * horizonBlendEnd +
                                    .0186f * horizonBlendEnd * (1f - horizonBlendEnd)
                        }
                    val normalizedX =
                        if (q < horizonBlendEnd) {
                            horizonX + (fitAtBlend - horizonX) * (q / horizonBlendEnd)
                        } else {
                            fittedX
                        }.coerceIn(if (side < 0) -.04f else .53f, if (side < 0) .57f else 1.05f)
                    return Offset(
                        x = size.width * normalizedX,
                        y = size.height * .39f + size.height * .65f * q
                    ) to q
                }

                repeat(6) { index ->
                    val value = (index / 6f + phase) % 1f
                    if (value > .035f) {
                        val q = perspective(value)
                        val start = roadPoint(size.width * .285f, size.width * .555f, value)
                        val end = roadPoint(
                            size.width * .285f,
                            size.width * .555f,
                            (value + .055f + value * .025f).coerceAtMost(1f)
                        )
                        drawLine(
                            color = Color.White.copy(alpha = .96f),
                            start = start,
                            end = end,
                            strokeWidth = (1.dp.toPx() + q * 8.dp.toPx()) * visualScale,
                            cap = StrokeCap.Round
                        )
                    }
                }

                repeat(6) { index ->
                    val base = (index / 6f + phase * .78f) % 1f
                    listOf(-1 to base, 1 to ((base + .045f) % 1f)).forEach { (side, value) ->
                        if (value > .04f) {
                            val (point, q) = edgePoint(side, value)
                            val postHeight = (2.dp.toPx() + q * 12.dp.toPx()) * visualScale
                            val postWidth = (.7.dp.toPx() + q * 2.1.dp.toPx()) * visualScale
                            val lampWidth = (1.2.dp.toPx() + q * 4.6.dp.toPx()) * visualScale
                            val lampHeight = (.9.dp.toPx() + q * 3.1.dp.toPx()) * visualScale
                            val lampColor = if (side < 0) Color.White else DangerRed

                            drawRect(
                                color = Color(0xFFB9DCE5).copy(alpha = .88f),
                                topLeft = Offset(point.x - postWidth / 2f, point.y - postHeight),
                                size = Size(postWidth, postHeight)
                            )
                            drawCircle(
                                color = lampColor.copy(alpha = .20f),
                                radius = lampWidth * 1.15f,
                                center = Offset(point.x, point.y - postHeight)
                            )
                            drawRect(
                                color = lampColor,
                                topLeft = Offset(
                                    point.x - lampWidth / 2f,
                                    point.y - postHeight - lampHeight / 2f
                                ),
                                size = Size(lampWidth, lampHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedAltitudeBadge(
    altitudeMeters: Int?,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(if (compact) 68.dp else 86.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MountainAltitudeArtwork(
                Modifier.fillMaxSize().padding(5.dp).graphicsLayer(alpha = .10f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "RAKIM",
                    color = CyanBright,
                    fontSize = if (compact) 9.sp else 12.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "${altitudeMeters ?: "--"}",
                    color = PrimaryText,
                    fontSize = if (compact) 21.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("m", color = CyanBright, fontSize = if (compact) 9.sp else 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SunriseBlock(
    title: String,
    isSunrise: Boolean,
    modifier: Modifier = Modifier
) {
    val iconColor =
        if (isSunrise) {
            WarningYellow
        } else {
            Color(0xFFFF6238)
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .width(40.dp)
                .height(23.dp)
        ) {
            val center =
                Offset(
                    size.width / 2f,
                    size.height * 0.78f
                )

            val radius =
                size.height * 0.34f

            drawArc(
                color = iconColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = Size(
                    radius * 2f,
                    radius * 2f
                ),
                style = Stroke(
                    width = 2.dp.toPx()
                )
            )

            drawLine(
                color = iconColor,
                start = Offset(
                    3.dp.toPx(),
                    center.y
                ),
                end = Offset(
                    size.width -
                            3.dp.toPx(),
                    center.y
                ),
                strokeWidth = 2.dp.toPx()
            )

            for (degree in listOf(
                -60f,
                -30f,
                0f,
                30f,
                60f
            )) {
                val inner =
                    pointOnCircle(
                        center,
                        radius * 1.25f,
                        degree
                    )

                val outer =
                    pointOnCircle(
                        center,
                        radius * 1.65f,
                        degree
                    )

                drawLine(
                    color = iconColor,
                    start = inner,
                    end = outer,
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            if (isSunrise) {
                drawLine(
                    color = iconColor,
                    start = Offset(
                        size.width * 0.82f,
                        size.height * 0.70f
                    ),
                    end = Offset(
                        size.width * 0.82f,
                        size.height * 0.47f
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
            } else {
                drawLine(
                    color = iconColor,
                    start = Offset(
                        size.width * 0.82f,
                        size.height * 0.47f
                    ),
                    end = Offset(
                        size.width * 0.82f,
                        size.height * 0.70f
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        Text(
            text = title,
            color = PrimaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun VehicleModeCard(
    modifier: Modifier,
    vehicleMode: String,
    selectedVehicleId: String,
    customCarImageUri: String?,
    customCaravanImageUri: String?,
    speedWarningState: SpeedWarningState,
    transientVehicleAlertMessage: String?,
    obdAlertMessage: String?,
    onVehicleModeChange: (String) -> Unit
) {
    val strokeScale = LocalDashboardStrokeScale.current

    val context = LocalContext.current

    val selectedVehicle =
        remember(selectedVehicleId) {
            VehicleCatalog.findById(
                selectedVehicleId
            )
        }

    val selectedVehicleDrawableResId =
        remember(
            selectedVehicleId,
            selectedVehicle?.drawableName
        ) {
            selectedVehicle
                ?.drawableName
                ?.let { drawableName ->
                    context.resources.getIdentifier(
                        drawableName,
                        "drawable",
                        context.packageName
                    )
                }
                ?: 0
        }

    val useDrawableVehicle =
        selectedVehicle?.visualSource ==
                VehicleVisualSource.DRAWABLE_RESOURCE &&
                selectedVehicleDrawableResId != 0

    val activeCustomImageUri =
        if (vehicleMode == "Karavan") {
            customCaravanImageUri
        } else {
            customCarImageUri
        }

    data class VehicleModeVisualAlert(
        val source: String,
        val message: String,
        val color: Color
    )

    val activeVehicleAlerts = remember { mutableStateListOf<VehicleModeVisualAlert>() }

    fun putVehicleAlert(source: String, message: String, color: Color) {
        activeVehicleAlerts.removeAll { it.source == source }
        activeVehicleAlerts.add(VehicleModeVisualAlert(source, message, color))
        while (activeVehicleAlerts.size > 2) {
            activeVehicleAlerts.removeAt(0)
        }
    }

    fun clearVehicleAlert(source: String, expectedMessage: String? = null) {
        activeVehicleAlerts.removeAll {
            it.source == source && (expectedMessage == null || it.message == expectedMessage)
        }
    }

    val obdAlertCategory = remember(obdAlertMessage) {
        when {
            obdAlertMessage.isNullOrBlank() -> null
            obdAlertMessage.contains("MOTOR ARIZA", ignoreCase = true) -> "obd_mil"
            obdAlertMessage.contains("ARIZA KODU", ignoreCase = true) -> "obd_dtc"
            obdAlertMessage.contains("MOTOR SICAKLIĞI", ignoreCase = true) -> "obd_temp"
            obdAlertMessage.contains("AKÜ/ŞARJ", ignoreCase = true) -> "obd_voltage"
            obdAlertMessage.contains("OBD", ignoreCase = true) -> "obd_connection"
            else -> "obd_other"
        }
    }

    LaunchedEffect(transientVehicleAlertMessage) {
        val message = transientVehicleAlertMessage
        if (message.isNullOrBlank()) {
            clearVehicleAlert("transient")
        } else {
            putVehicleAlert("transient", message, WarningYellow)
            delay(4_000L)
            clearVehicleAlert("transient", message)
        }
    }

    LaunchedEffect(speedWarningState) {
        val message = when (speedWarningState) {
            SpeedWarningState.OVER_LIMIT -> "HIZ SINIRI AŞILDI"
            SpeedWarningState.APPROACHING -> "HIZ SINIRINA YAKLAŞIYORSUNUZ"
            SpeedWarningState.NORMAL -> null
        }
        if (message == null) {
            clearVehicleAlert("speed")
        } else {
            putVehicleAlert(
                source = "speed",
                message = message,
                color = if (speedWarningState == SpeedWarningState.OVER_LIMIT) DangerRed else WarningYellow
            )
            delay(4_000L)
            clearVehicleAlert("speed", message)
        }
    }

    LaunchedEffect(obdAlertCategory) {
        val message = obdAlertMessage
        if (obdAlertCategory == null || message.isNullOrBlank()) {
            clearVehicleAlert("obd")
        } else {
            // Aynı OBD arıza kategorisi sabit kalsa bile yalnız bir kez 4 saniye gösterilir.
            putVehicleAlert("obd", message, DangerRed)
            delay(4_000L)
            clearVehicleAlert("obd")
        }
    }

    val customVehicleImage by
    produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = activeCustomImageUri
    ) {
        value =
            activeCustomImageUri
                ?.takeIf { it.isNotBlank() }
                ?.let { uriText ->
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val source =
                                ImageDecoder.createSource(
                                    context.contentResolver,
                                    Uri.parse(uriText)
                                )

                            ImageDecoder
                                .decodeBitmap(source)
                                .asImageBitmap()
                        }.getOrNull()
                    }
                }
    }

    val carRotationY = remember {
        Animatable(0f)
    }

    LaunchedEffect(vehicleMode, selectedVehicleId, activeCustomImageUri) {
        carRotationY.snapTo(0f)
        carRotationY.animateTo(
            targetValue = -720f,
            animationSpec = tween(
                durationMillis = 4500,
                easing = LinearEasing
            )
        )
    }

    Card(
        modifier = modifier.clickable {
            onVehicleModeChange(
                if (vehicleMode == "Karavan") {
                    "Otomobil"
                } else {
                    "Karavan"
                }
            )
        },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = PanelBackground
        ),
        border = BorderStroke(
            width = 1.dp * strokeScale,
            color = PanelBorder
        )
    ) {
        if (activeVehicleAlerts.isNotEmpty()) {
            // 130: Her geçici uyarı 4 saniye görünür. Aynı anda iki uyarı varsa
            // Araç Modu kartı iki eşit bölüme ayrılır.
            if (activeVehicleAlerts.size == 1) {
                val alert = activeVehicleAlerts.first()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE607131B))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = alert.message,
                        color = alert.color,
                        fontSize = when {
                            alert.message.length >= 46 -> 12.sp
                            alert.message.length >= 34 -> 14.sp
                            else -> 17.sp
                        },
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE607131B)),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    activeVehicleAlerts.take(2).forEach { alert ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp * strokeScale,
                                    color = alert.color.copy(alpha = .75f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = alert.message,
                                color = alert.color,
                                fontSize = when {
                                    alert.message.length >= 42 -> 10.sp
                                    alert.message.length >= 30 -> 12.sp
                                    else -> 14.sp
                                },
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ARAÇ MODU",
                        color = SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(170.dp)
                            .height(70.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        drawOval(
                            color = CyanAccent.copy(alpha = 0.10f),
                            topLeft = Offset(
                                size.width * 0.06f,
                                size.height * 0.45f
                            ),
                            size = Size(
                                size.width * 0.88f,
                                size.height * 0.42f
                            )
                        )

                        drawOval(
                            color = CyanAccent.copy(alpha = 0.26f),
                            topLeft = Offset(
                                size.width * 0.10f,
                                size.height * 0.49f
                            ),
                            size = Size(
                                size.width * 0.80f,
                                size.height * 0.33f
                            ),
                            style = Stroke(
                                width = 5.dp.toPx()
                            )
                        )

                        drawOval(
                            color = CyanBright,
                            topLeft = Offset(
                                size.width * 0.12f,
                                size.height * 0.51f
                            ),
                            size = Size(
                                size.width * 0.76f,
                                size.height * 0.29f
                            ),
                            style = Stroke(
                                width = 1.8.dp.toPx()
                            )
                        )
                    }

                    if (customVehicleImage != null) {
                        Image(
                            bitmap = customVehicleImage!!,
                            contentDescription =
                                "$vehicleMode özel görseli",
                            contentScale = ContentScale.Fit,
                            colorFilter = if (DashboardPaletteRuntime.isDay) {
                                null
                            } else {
                                ColorFilter.tint(Color(0xFF646C74), BlendMode.Modulate)
                            },
                            modifier = Modifier
                                .width(176.dp)
                                .height(82.dp)
                                .graphicsLayer {
                                    rotationY =
                                        carRotationY.value
                                    cameraDistance =
                                        14f * density
                                }
                        )
                    } else if (useDrawableVehicle) {
                        Image(
                            painter =
                                painterResource(
                                    id =
                                        selectedVehicleDrawableResId
                                ),
                            contentDescription =
                                selectedVehicle?.displayName
                                    ?: vehicleMode,
                            contentScale = ContentScale.Fit,
                            colorFilter = if (DashboardPaletteRuntime.isDay) {
                                null
                            } else {
                                ColorFilter.tint(Color(0xFF646C74), BlendMode.Modulate)
                            },
                            modifier = Modifier
                                .width(176.dp)
                                .height(82.dp)
                                .graphicsLayer {
                                    rotationY =
                                        carRotationY.value
                                    cameraDistance =
                                        14f * density
                                }
                        )
                    } else {
                        Canvas(
                            modifier = Modifier
                                .width(158.dp)
                                .height(74.dp)
                                .graphicsLayer {
                                    rotationY =
                                        carRotationY.value
                                    cameraDistance =
                                        14f * density
                                }
                        ) {
                            if (vehicleMode == "Karavan") {
                                drawCompactCaravan()
                            } else {
                                drawCutRearHatchback()
                            }
                        }
                    }
                }

                Text(
                    text = selectedVehicle?.displayName ?: vehicleMode,
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "aktif",
                    color = CyanAccent,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCutRearHatchback() {
    val w = size.width
    val h = size.height

    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(w * 0.10f, h * 0.76f),
        size = Size(w * 0.80f, h * 0.10f)
    )

    // Arkası kesik, kompakt hatchback gövde.
    val body = Path().apply {
        moveTo(w * 0.08f, h * 0.67f)

        // Kesik/dik arka bölüm
        lineTo(w * 0.10f, h * 0.42f)
        quadraticBezierTo(
            w * 0.11f, h * 0.35f,
            w * 0.17f, h * 0.31f
        )

        // Tavan
        lineTo(w * 0.30f, h * 0.24f)
        quadraticBezierTo(
            w * 0.39f, h * 0.19f,
            w * 0.52f, h * 0.20f
        )
        quadraticBezierTo(
            w * 0.65f, h * 0.21f,
            w * 0.75f, h * 0.32f
        )

        // Ön cam ve kaput
        lineTo(w * 0.82f, h * 0.44f)
        lineTo(w * 0.93f, h * 0.49f)
        quadraticBezierTo(
            w * 0.98f, h * 0.52f,
            w * 0.98f, h * 0.60f
        )
        lineTo(w * 0.98f, h * 0.66f)

        // Alt hat
        quadraticBezierTo(
            w * 0.97f, h * 0.70f,
            w * 0.91f, h * 0.70f
        )
        lineTo(w * 0.13f, h * 0.70f)
        quadraticBezierTo(
            w * 0.08f, h * 0.70f,
            w * 0.08f, h * 0.67f
        )
        close()
    }

    drawPath(
        path = body,
        color = Color(0xFF287FA8)
    )

    // Siyah cam bandı
    val glass = Path().apply {
        moveTo(w * 0.18f, h * 0.34f)
        lineTo(w * 0.31f, h * 0.27f)
        quadraticBezierTo(
            w * 0.39f, h * 0.23f,
            w * 0.51f, h * 0.24f
        )
        quadraticBezierTo(
            w * 0.63f, h * 0.25f,
            w * 0.71f, h * 0.34f
        )
        lineTo(w * 0.77f, h * 0.44f)
        lineTo(w * 0.18f, h * 0.44f)
        close()
    }

    drawPath(
        path = glass,
        color = Color(0xFF091923)
    )

    // B sütunu
    drawLine(
        color = Color(0xFF627886),
        start = Offset(w * 0.47f, h * 0.24f),
        end = Offset(w * 0.47f, h * 0.44f),
        strokeWidth = 1.dp.toPx()
    )

    // Omuz çizgisi
    drawLine(
        color = CyanBright.copy(alpha = 0.55f),
        start = Offset(w * 0.16f, h * 0.50f),
        end = Offset(w * 0.90f, h * 0.50f),
        strokeWidth = 1.1.dp.toPx(),
        cap = StrokeCap.Round
    )

    // İnce ön LED
    drawLine(
        color = Color(0xFFE8FCFF),
        start = Offset(w * 0.88f, h * 0.52f),
        end = Offset(w * 0.95f, h * 0.55f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Dikey hatchback stop
    drawLine(
        color = Color(0xFFFF5663),
        start = Offset(w * 0.105f, h * 0.44f),
        end = Offset(w * 0.105f, h * 0.58f),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Kapı çizgileri
    drawLine(
        color = Color(0xFF15526A),
        start = Offset(w * 0.47f, h * 0.47f),
        end = Offset(w * 0.47f, h * 0.66f),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = Color(0xFF15526A),
        start = Offset(w * 0.68f, h * 0.47f),
        end = Offset(w * 0.68f, h * 0.66f),
        strokeWidth = 1.dp.toPx()
    )

    // Gizli kapı kolları
    drawLine(
        color = CyanBright.copy(alpha = 0.7f),
        start = Offset(w * 0.52f, h * 0.54f),
        end = Offset(w * 0.57f, h * 0.54f),
        strokeWidth = 1.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = CyanBright.copy(alpha = 0.7f),
        start = Offset(w * 0.71f, h * 0.54f),
        end = Offset(w * 0.76f, h * 0.54f),
        strokeWidth = 1.dp.toPx(),
        cap = StrokeCap.Round
    )

    drawVehicleWheel(w * 0.25f, h * 0.69f, h)
    drawVehicleWheel(w * 0.78f, h * 0.69f, h)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompactCaravan() {
    val w = size.width
    val h = size.height

    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(w * 0.08f, h * 0.76f),
        size = Size(w * 0.84f, h * 0.10f)
    )

    // Motokaravan / camper van silueti
    val body = Path().apply {
        moveTo(w * 0.08f, h * 0.66f)
        lineTo(w * 0.08f, h * 0.29f)
        quadraticBezierTo(
            w * 0.08f, h * 0.23f,
            w * 0.15f, h * 0.23f
        )
        lineTo(w * 0.67f, h * 0.23f)
        quadraticBezierTo(
            w * 0.75f, h * 0.23f,
            w * 0.81f, h * 0.32f
        )
        lineTo(w * 0.92f, h * 0.48f)
        quadraticBezierTo(
            w * 0.97f, h * 0.53f,
            w * 0.97f, h * 0.60f
        )
        lineTo(w * 0.97f, h * 0.66f)
        quadraticBezierTo(
            w * 0.96f, h * 0.70f,
            w * 0.90f, h * 0.70f
        )
        lineTo(w * 0.13f, h * 0.70f)
        quadraticBezierTo(
            w * 0.08f, h * 0.70f,
            w * 0.08f, h * 0.66f
        )
        close()
    }

    drawPath(
        path = body,
        color = Color(0xFF2A7895)
    )

    // Ön cam
    val frontGlass = Path().apply {
        moveTo(w * 0.69f, h * 0.28f)
        quadraticBezierTo(
            w * 0.74f, h * 0.29f,
            w * 0.78f, h * 0.35f
        )
        lineTo(w * 0.87f, h * 0.48f)
        lineTo(w * 0.68f, h * 0.48f)
        close()
    }
    drawPath(frontGlass, Color(0xFF091923))

    // Yan yaşam alanı camları
    drawRoundRect(
        color = Color(0xFF0B1C28),
        topLeft = Offset(w * 0.18f, h * 0.32f),
        size = Size(w * 0.18f, h * 0.15f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF0B1C28),
        topLeft = Offset(w * 0.41f, h * 0.32f),
        size = Size(w * 0.18f, h * 0.15f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )

    // Kapı
    drawRoundRect(
        color = Color.Transparent,
        topLeft = Offset(w * 0.48f, h * 0.50f),
        size = Size(w * 0.14f, h * 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(
            width = 1.dp.toPx()
        )
    )

    // İnce far
    drawLine(
        color = Color(0xFFE8FCFF),
        start = Offset(w * 0.88f, h * 0.53f),
        end = Offset(w * 0.95f, h * 0.56f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Arka stop
    drawLine(
        color = Color(0xFFFF5663),
        start = Offset(w * 0.085f, h * 0.45f),
        end = Offset(w * 0.085f, h * 0.58f),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Alt marşpiyel
    drawLine(
        color = Color(0xFF173340),
        start = Offset(w * 0.12f, h * 0.69f),
        end = Offset(w * 0.90f, h * 0.69f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    drawVehicleWheel(w * 0.25f, h * 0.69f, h)
    drawVehicleWheel(w * 0.78f, h * 0.69f, h)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVehicleWheel(
    cx: Float,
    cy: Float,
    h: Float
) {
    val center = Offset(cx, cy)

    drawCircle(
        color = Color(0xFF05080B),
        radius = h * 0.145f,
        center = center
    )
    drawCircle(
        color = Color(0xFF8799A6),
        radius = h * 0.095f,
        center = center
    )
    drawCircle(
        color = Color(0xFF182A36),
        radius = h * 0.052f,
        center = center
    )
    drawCircle(
        color = CyanAccent.copy(alpha = 0.70f),
        radius = h * 0.021f,
        center = center
    )
}

@Composable
private fun DashboardClockPanel(
    currentTime: String,
    currentDate: String,
    isSpeedCorridorActive: Boolean,
    speedCorridorAverageSpeedKmh: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBackground),
        border = BorderStroke(1.dp * strokeScale, CyanAccent.copy(alpha = .55f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            val isLargeClock = LocalConfiguration.current.smallestScreenWidthDp >= 600
            val heightDriven = maxHeight.value * 0.98f
            val widthDriven = maxWidth.value / 2.48f
            val largeClockValue = kotlin.math.min(heightDriven, widthDriven).coerceIn(54f, 158f)
            val largeClockSize = largeClockValue.sp
            val corridorAlpha by animateFloatAsState(
                targetValue = if (isSpeedCorridorActive) 1f else 0f,
                animationSpec = tween(durationMillis = 280),
                label = "corridorAverageAlpha"
            )
            if (isSpeedCorridorActive) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTime, color = PrimaryText,
                        fontSize = if (isLargeClock) (largeClockValue * .58f).sp else 20.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Black, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1.32f)
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.0f", speedCorridorAverageSpeedKmh),
                        color = Color(0xFFFF8A00),
                        fontSize = if (isLargeClock) (largeClockValue * .58f).sp else 20.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Black, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(.68f).graphicsLayer { alpha = corridorAlpha }
                    )
                }
            } else {
                Text(
                    text = currentTime,
                    color = PrimaryText,
                    fontSize = if (isLargeClock) largeClockSize else 30.sp,
                    letterSpacing = if (isLargeClock) (largeClockValue * 0.018f).sp else 1.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FuelGaugeCard(
    fuelPercentage: Double?,
    fuelDataSource: String,
    remainingFuelLiters: Double?,
    estimatedFuelRangeKm: Double?,
    isLowFuel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeScale = LocalDashboardStrokeScale.current

    val percentage = (fuelPercentage ?: 0.0).coerceIn(0.0, 100.0)
    // Yakıt barının kırmızı bölgesi: yaklaşık %18 ve altı. Bu seviyede tüm kart alarm çerçevesi olur.
    val isCriticalFuel = fuelPercentage != null && (percentage <= 18.0 || isLowFuel)
    val fuelPulse = rememberInfiniteTransition(label = "fuelCriticalPulse")
    val criticalAlpha by fuelPulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fuelBorderAlpha"
    )
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCriticalFuel) Color(0xFF17080B).copy(alpha = 0.88f + 0.12f * criticalAlpha) else PanelBackground
        ),
        border = BorderStroke(
            (if (isCriticalFuel) 3.2.dp else 1.dp) * strokeScale,
            if (isCriticalFuel) DangerRed.copy(alpha = criticalAlpha) else CyanAccent.copy(alpha = .55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rangeIsCritical = estimatedFuelRangeKm != null && estimatedFuelRangeKm <= 79.0
            val fuelSummaryText = formatFuelSummaryText(
                percentage = percentage,
                estimatedFuelRangeKm = estimatedFuelRangeKm
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⛽",
                    fontSize = 15.sp
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isCriticalFuel) "DÜŞÜK YAKIT" else "YAKIT",
                    color = if (isCriticalFuel) DangerRed else SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = fuelDataSource,
                    color = if (fuelDataSource == "OBD") SafeGreen else WarningYellow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = fuelSummaryText,
                    color = if (isCriticalFuel || rangeIsCritical) DangerRed else CyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(3.dp))

            ModernFuelBar(
                fuelPercentage = percentage,
                isLowFuel = isCriticalFuel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("0", color = SecondaryText, fontSize = 7.sp)
                Spacer(Modifier.weight(1f))
                Text("25", color = SecondaryText, fontSize = 7.sp)
                Spacer(Modifier.weight(1f))
                Text("50", color = SecondaryText, fontSize = 7.sp)
                Spacer(Modifier.weight(1f))
                Text("75", color = SecondaryText, fontSize = 7.sp)
                Spacer(Modifier.weight(1f))
                Text("100", color = SecondaryText, fontSize = 7.sp)
            }

        }
    }
}

internal fun formatFuelSummaryText(
    percentage: Double,
    estimatedFuelRangeKm: Double?
): String {
    val formattedRange = estimatedFuelRangeKm
        ?.takeIf { it.isFinite() }
        ?.let { "${it.roundToInt()} KM" }
        ?: "— KM"
    return "%${percentage.coerceIn(0.0, 100.0).roundToInt()} • $formattedRange"
}

@Composable
private fun ModernFuelBar(
    fuelPercentage: Double,
    isLowFuel: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val segmentCount = 16
        val gap = 2.2.dp.toPx()
        val trackTop = size.height * .12f
        val trackHeight = size.height * .70f
        val segmentWidth = (size.width - gap * (segmentCount - 1)) / segmentCount
        val litSegments = ((fuelPercentage / 100.0) * segmentCount)
            .let { kotlin.math.ceil(it).toInt() }
            .coerceIn(0, segmentCount)

        drawRoundRect(
            color = Color(0xFF02080E),
            topLeft = Offset(0f, trackTop - 3.dp.toPx()),
            size = Size(size.width, trackHeight + 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )

        for (i in 0 until segmentCount) {
            val x = i * (segmentWidth + gap)
            val ratio = (i + 1f) / segmentCount
            val baseColor = when {
                ratio <= .18f -> DangerRed
                ratio <= .36f -> WarningYellow
                ratio <= .70f -> CyanBright
                else -> SafeGreen
            }
            val lit = i < litSegments
            val color = when {
                !lit -> Color(0xFF1A2A35)
                isLowFuel -> DangerRed
                else -> baseColor
            }
            if (lit) {
                drawRoundRect(
                    color = color.copy(alpha = .20f),
                    topLeft = Offset(x - 2.dp.toPx(), trackTop - 2.dp.toPx()),
                    size = Size(segmentWidth + 4.dp.toPx(), trackHeight + 4.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
            }
            drawRoundRect(
                brush = if (lit) {
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = .95f), color.copy(alpha = .48f))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFF263B48), Color(0xFF111B22))
                    )
                },
                topLeft = Offset(x, trackTop),
                size = Size(segmentWidth, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawLine(
                color = if (lit) Color.White.copy(alpha = .25f) else Color.Transparent,
                start = Offset(x + 2.dp.toPx(), trackTop + 2.dp.toPx()),
                end = Offset(x + segmentWidth - 2.dp.toPx(), trackTop + 2.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TripCard(
    modifier: Modifier,
    tripDistanceKm: Double,
    isTripActive: Boolean,
    isSpeedCorridorActive: Boolean,
    speedCorridorDistanceKm: Double,
    speedCorridorDurationSeconds: Long,
    speedCorridorAverageSpeedKmh: Double,
    upcomingRoadSigns: List<UpcomingRoadSignInfo>,
    tripDurationSeconds: Long,
    tripMovingDurationSeconds: Long,
    tripParkDurationSeconds: Long,
    tripAverageSpeedKmh: Double,
    tripMaxSpeedKmh: Int,
    tripEstimatedFuelConsumedLiters: Double,
    todayTotalDistanceKm: Double,
    tripStartedAtEpochMillis: Long?,
    tripEndedAtEpochMillis: Long?,
    onStatisticsClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = modifier
            .pointerInput(upcomingRoadSigns, isTripActive) {
                detectTapGestures(
                    onTap = {
                        if (upcomingRoadSigns.isEmpty()) {
                            if (isTripActive) onStop() else onStart()
                        }
                    },
                    onDoubleTap = {
                        if (upcomingRoadSigns.isNotEmpty()) {
                            onStatisticsClick()
                        }
                    }
                )
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = PanelBackground
        ),
        border = BorderStroke(
            width = (if (isTripActive) 1.8.dp else 1.dp) * strokeScale,
            color = if (isTripActive) SafeGreen else PanelBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (upcomingRoadSigns.isNotEmpty()) {
                Text(
                    text = "İLERİDEKİ TRAFİK LEVHALARI",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Spacer(Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    upcomingRoadSigns.take(2).forEach { sign ->
                        UpcomingRoadSignBadge(
                            sign = sign,
                            onDoubleClick = onStatisticsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Yol: %.1f km  •  Süre: %s",
                        tripDistanceKm,
                        formatTripDuration(tripDurationSeconds)
                    ),
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Ort: %.1f Km  •  Max: %d Km",
                        tripAverageSpeedKmh,
                        tripMaxSpeedKmh
                    ),
                    color = SecondaryText.copy(alpha = .9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            } else {
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.1f",
                        tripDistanceKm
                    ),
                    color = PrimaryText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "km",
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "•",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )

                    Text(
                        text = formatTripDuration(
                            tripDurationSeconds
                        ),
                        color =
                            if (isTripActive) {
                                SafeGreen
                            } else {
                                SecondaryText
                            },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Hrk: ${formatTripDuration(tripMovingDurationSeconds)}" +
                                " • Prk: ${formatTripDuration(tripParkDurationSeconds)}",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Ort: %.1f Km • Max: %d Km",
                        tripAverageSpeedKmh,
                        tripMaxSpeedKmh
                    ),
                    color = SecondaryText.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                Text(
                    text =
                        "Baş: ${formatTripClock(tripStartedAtEpochMillis)}" +
                                " • Bit: " +
                                if (isTripActive) {
                                    "--:--"
                                } else {
                                    formatTripClock(tripEndedAtEpochMillis)
                                },
                    color = SecondaryText.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Yakıt: %.2f L",
                        tripEstimatedFuelConsumedLiters
                    ),
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Bugün: %.1f Km",
                        todayTotalDistanceKm
                    ),
                    color = SecondaryText.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                Text(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onStatisticsClick)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    text = "İSTATİSTİKLER  ›",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(
                    modifier = Modifier.height(1.dp)
                )

                Text(
                    text =
                        if (isTripActive) {
                            "İstersen dokunarak bitir"
                        } else {
                            "Hareket edince otomatik başlar"
                        },
                    color = SecondaryText.copy(alpha = 0.68f),
                    fontSize = 8.sp,
                    maxLines = 1
                )

            }
        }
    }
}

@Composable
private fun UpcomingRoadSignBadge(
    sign: UpcomingRoadSignInfo,
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBack by remember(
        sign.kind,
        sign.distanceMeters,
        sign.speedLimitKmh,
        sign.label,
        sign.roadName
    ) { mutableStateOf(false) }
    val rotationY by animateFloatAsState(
        targetValue = if (showBack) 180f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "roadSignFlip"
    )

    Column(
        modifier = modifier
            .pointerInput(sign) {
                detectTapGestures(
                    onTap = { showBack = !showBack },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .graphicsLayer {
                this.rotationY = rotationY
                cameraDistance = 12f * density
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (rotationY <= 90f) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                when (sign.kind) {
                    UpcomingRoadSignKind.SPEED_LIMIT -> {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = DashboardPaletteRuntime.primaryText,
                            border = BorderStroke(5.dp, Color(0xFFE53935))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = sign.speedLimitKmh?.toString() ?: "?",
                                    color = Color.Black,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    UpcomingRoadSignKind.ROAD_CLOSURE -> {
                        Canvas(Modifier.size(56.dp)) {
                            drawCircle(Color(0xFFE53935), radius = size.minDimension / 2f)
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(size.width * .17f, size.height * .42f),
                                size = Size(size.width * .66f, size.height * .16f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }

                    UpcomingRoadSignKind.TUNNEL -> {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF175AA8),
                            border = BorderStroke(2.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("∩", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    UpcomingRoadSignKind.ROAD_WORK,
                    UpcomingRoadSignKind.TRAFFIC_JAM -> {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(60.dp)) {
                                val p = Path().apply {
                                    moveTo(size.width / 2f, size.height * .05f)
                                    lineTo(size.width * .95f, size.height * .90f)
                                    lineTo(size.width * .05f, size.height * .90f)
                                    close()
                                }
                                drawPath(p, Color.White)
                                drawPath(p, Color(0xFFE53935), style = Stroke(width = 5.dp.toPx()))
                            }
                            Text(
                                text = if (sign.kind == UpcomingRoadSignKind.ROAD_WORK) "YOL" else "!",
                                color = Color.Black,
                                fontSize = if (sign.kind == UpcomingRoadSignKind.ROAD_WORK) 11.sp else 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .size(width = 104.dp, height = 80.dp)
                    .graphicsLayer { this.rotationY = 180f },
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFF102536),
                border = BorderStroke(1.5.dp, WarningYellow)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(sign.label, color = PrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 2)
                    Text("${sign.distanceMeters} m ileride", color = WarningYellow, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(sign.roadName ?: "Yol bilgisi yok", color = CyanBright, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    unit: String
) {
    val strokeScale = LocalDashboardStrokeScale.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = PanelBackground
        ),
        border = BorderStroke(
            1.dp * strokeScale,
            PanelBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = SecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = value,
                color = PrimaryText,
                fontSize = 37.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                text = unit,
                color = CyanAccent,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun LargeCompassPanel(
    isSensorAvailable: Boolean,
    headingDegrees: Float?,
    qiblaBearingDegrees: Float?,
    direction: String,
    accuracy: CompassAccuracy,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isSensorAvailable && headingDegrees == null) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GPS PUSULASI HAZIRLANIYOR",
                color = CyanAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Yön bilgisi için araçla kısa süre hareket edin",
                color = SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    Box(modifier = modifier) {
        Text(
            text = "PUSULA / GPS YÖNÜ",
            color = CyanAccent,
            fontSize =
                if (compact)
                    11.sp
                else
                    13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(
                Alignment.TopCenter
            )
        )

        if (headingDegrees == null) {
            Text(
                text = "GPS YÖNÜ BEKLENİYOR • HAREKET EDİN",
                color = WarningYellow,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        CompassDial(
            headingDegrees = headingDegrees,
            qiblaBearingDegrees = qiblaBearingDegrees,
            direction = direction,
            compact = compact,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top =
                        if (compact)
                            16.dp
                        else
                            20.dp,
                    bottom =
                        if (compact)
                            25.dp
                        else
                            30.dp
                )
        )

        Text(
            text = buildString {
                append(
                    compassAccuracyText(
                        accuracy
                    )
                )

                append("  •  ")

                append(
                    if (
                        qiblaBearingDegrees != null
                    ) {
                        "Altın çizgi: Kâbe"
                    } else {
                        "Kâbe yönü için GPS bekleniyor"
                    }
                )
            },
            color = SecondaryText,
            fontSize =
                if (compact)
                    9.sp
                else
                    10.sp,
            modifier = Modifier.align(
                Alignment.BottomCenter
            )
        )
    }
}

@Composable
private fun CompassDial(
    headingDegrees: Float?,
    qiblaBearingDegrees: Float?,
    direction: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center =
            Offset(
                size.width / 2f,
                size.height / 2f
            )

        val radius =
            min(
                size.width,
                size.height
            ) *
                    if (compact)
                        0.43f
                    else
                        0.46f

        if (radius <= 0f) {
            return@Canvas
        }

        val heading =
            headingDegrees ?: 0f

        drawCircle(
            color = Color(0xFF07111D),
            radius = radius,
            center = center
        )

        drawCircle(
            color = Color(0xFF294258),
            radius = radius,
            center = center,
            style = Stroke(
                width = 2.dp.toPx()
            )
        )

        drawCircle(
            color = Color(0xFF17283A),
            radius = radius * 0.72f,
            center = center,
            style = Stroke(
                width = 1.dp.toPx()
            )
        )

        for (
        worldDegree in
        0 until 360 step 10
        ) {
            val relativeAngle =
                worldDegree - heading

            val isMajor =
                worldDegree % 30 == 0

            val outer =
                pointOnCircle(
                    center,
                    radius * 0.96f,
                    relativeAngle
                )

            val inner =
                pointOnCircle(
                    center,
                    radius *
                            if (isMajor)
                                0.84f
                            else
                                0.89f,
                    relativeAngle
                )

            drawLine(
                color =
                    if (worldDegree == 0)
                        DangerRed
                    else
                        SecondaryText,
                start = inner,
                end = outer,
                strokeWidth =
                    if (isMajor)
                        2.dp.toPx()
                    else
                        1.dp.toPx()
            )
        }

        val cardinalPaint =
            AndroidPaint().apply {
                textAlign =
                    AndroidPaint.Align.CENTER

                textSize =
                    if (compact)
                        12.sp.toPx()
                    else
                        14.sp.toPx()

                isAntiAlias = true
                isFakeBoldText = true
            }

        listOf(
            0f to "K",
            90f to "D",
            180f to "G",
            270f to "B"
        ).forEach { (bearing, label) ->
            val relative =
                bearing - heading

            val point =
                pointOnCircle(
                    center,
                    radius * 0.72f,
                    relative
                )

            cardinalPaint.color =
                if (label == "K")
                    DangerRed.toArgb()
                else
                    PrimaryText.toArgb()

            drawContext.canvas.nativeCanvas.drawText(
                label,
                point.x,
                point.y +
                        cardinalPaint.textSize *
                        0.34f,
                cardinalPaint
            )
        }

        val northPoint =
            pointOnCircle(
                center,
                radius * 0.63f,
                -heading
            )

        drawLine(
            color = DangerRed,
            start = center,
            end = northPoint,
            strokeWidth = 3.dp.toPx()
        )

        qiblaBearingDegrees?.let { qibla ->
            val relative =
                qibla - heading

            val point =
                pointOnCircle(
                    center,
                    radius * 0.78f,
                    relative
                )

            drawLine(
                color = WarningYellow,
                start = center,
                end = point,
                strokeWidth = 4.dp.toPx()
            )

            drawCircle(
                color = WarningYellow,
                radius = 5.dp.toPx(),
                center = point
            )
        }

        drawCircle(
            color = PanelBackground,
            radius = radius * 0.29f,
            center = center
        )

        drawCircle(
            color = CyanAccent,
            radius = radius * 0.29f,
            center = center,
            style = Stroke(
                width = 1.5.dp.toPx()
            )
        )

        val centerPaint =
            AndroidPaint().apply {
                textAlign =
                    AndroidPaint.Align.CENTER

                color =
                    PrimaryText.toArgb()

                textSize =
                    if (compact)
                        16.sp.toPx()
                    else
                        20.sp.toPx()

                isAntiAlias = true
                isFakeBoldText = true
            }

        val directionPaint =
            AndroidPaint(
                centerPaint
            ).apply {
                color =
                    CyanAccent.toArgb()

                textSize =
                    if (compact)
                        10.sp.toPx()
                    else
                        12.sp.toPx()
            }

        drawContext.canvas.nativeCanvas.drawText(
            headingDegrees?.let {
                "${it.toInt()}°"
            } ?: "--°",
            center.x,
            center.y,
            centerPaint
        )

        drawContext.canvas.nativeCanvas.drawText(
            direction,
            center.x,
            center.y +
                    directionPaint.textSize *
                    1.3f,
            directionPaint
        )

        val topStart =
            pointOnCircle(
                center,
                radius * 1.02f,
                0f
            )

        val topEnd =
            pointOnCircle(
                center,
                radius * 0.88f,
                0f
            )

        drawLine(
            color = CyanAccent,
            start = topStart,
            end = topEnd,
            strokeWidth = 3.dp.toPx()
        )
    }
}

private fun pointOnCircle(
    center: Offset,
    radius: Float,
    angleFromTopDegrees: Float
): Offset {
    val radians =
        angleFromTopDegrees /
                180f *
                PI.toFloat()

    return Offset(
        x =
            center.x +
                    sin(radians) *
                    radius,

        y =
            center.y -
                    cos(radians) *
                    radius
    )
}

private fun compassAccuracyText(
    accuracy: CompassAccuracy
): String =
    when (accuracy) {
        CompassAccuracy.UNAVAILABLE ->
            "Sensör yok"

        CompassAccuracy.UNRELIABLE ->
            "Pusula kalibrasyonu gerekli"

        CompassAccuracy.LOW ->
            "Pusula doğruluğu düşük"

        CompassAccuracy.MEDIUM ->
            "Pusula doğruluğu orta"

        CompassAccuracy.HIGH ->
            "Pusula doğruluğu yüksek"
    }

private fun formatTripClock(
    epochMillis: Long?
): String {
    if (epochMillis == null) return "--:--"

    return Instant
        .ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(
            DateTimeFormatter.ofPattern(
                "HH:mm",
                Locale.getDefault()
            )
        )
}

private fun formatTripDuration(
    totalSeconds: Long
): String {
    val safeSeconds =
        totalSeconds.coerceAtLeast(0L)

    val hours =
        safeSeconds / 3_600L

    val minutes =
        (safeSeconds % 3_600L) / 60L

    val seconds =
        safeSeconds % 60L

    return String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        hours,
        minutes,
        seconds
    )
}

private fun formatParkDuration(
    totalSeconds: Long
): String {
    val safeSeconds =
        totalSeconds.coerceAtLeast(0L)

    val hours =
        safeSeconds / 3_600L

    val minutes =
        (
                safeSeconds %
                        3_600L
                ) / 60L

    val seconds =
        safeSeconds % 60L

    return if (hours > 0L) {
        String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}


@Composable
private fun NightStarField(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val seeds = listOf(
            .05f to .16f, .12f to .28f, .20f to .11f, .28f to .35f, .36f to .18f,
            .44f to .30f, .52f to .13f, .60f to .25f, .68f to .09f, .77f to .31f,
            .84f to .15f, .92f to .27f, .16f to .56f, .33f to .62f, .58f to .54f,
            .74f to .66f, .90f to .58f, .07f to .78f, .22f to .86f, .41f to .74f,
            .63f to .88f, .81f to .79f, .96f to .91f
        )
        seeds.forEachIndexed { i, (x, y) ->
            drawCircle(
                color = Color(0xFFDCEBFF).copy(alpha = if (i % 3 == 0) .72f else .48f),
                radius = if (i % 4 == 0) 2.4.dp.toPx() else 1.35.dp.toPx(),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 1024,
    heightDp = 600,
    uiMode =
        Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DriveDashboardScreenPreview() {
    MaterialTheme {
        DriveDashboardScreen(
            speedKmh = 78,
            direction = "GD",
            altitudeMeters = 820,
            tripDistanceKm = 152.4,
            vehicleMode = "Otomobil",
            fuelPercentage = 18.0,
            remainingFuelLiters = 12.4,
            sunriseTime = "06:24",
            sunsetTime = "19:48"
        )
    }
}