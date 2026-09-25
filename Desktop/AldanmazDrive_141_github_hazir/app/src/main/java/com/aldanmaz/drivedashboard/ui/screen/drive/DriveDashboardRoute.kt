package com.aldanmaz.drivedashboard.ui.screen.drive

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherViewModel
import com.aldanmaz.drivedashboard.trafficagent.TrafikDurumDeposu
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignInfo
import com.aldanmaz.drivedashboard.data.speedlimit.UpcomingRoadSignKind
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DriveDashboardRoute(
    onFuelClick: () -> Unit = {},
    onStatisticsClick: () -> Unit = {},
    onSpeedCorridorClick: () -> Unit = {},
    onPrayerClick: () -> Unit = {},
    isObdConnected: Boolean = false,
    obdAlertMessage: String? = null,
    obdShortName: String = "OBD",
    selectedDriverName: String = "---",
    onObdClick: () -> Unit = {},
    onTrafficAgentClick: () -> Unit = {},
    onNavigationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onWorkClick: () -> Unit = {},
    onClockClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onWeatherDetailsClick: () -> Unit = {},
    onVehicleSelectionClick: () -> Unit = {},
    viewModel: DriveDashboardViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel()
) {
    val context = LocalContext.current
    val voiceManager = remember { CentralVoiceAlertManager.getInstance(context) }
    var isAppSoundEnabled by remember { mutableStateOf(voiceManager.isMasterEnabled()) }
    var isAppAudioPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isAppAudioPlaying = voiceManager.isAudioPlaying()
            delay(120L)
        }
    }

    val roadSignTestPrefs = remember {
        context.getSharedPreferences("road_sign_test_mode", Context.MODE_PRIVATE)
    }
    var roadSignTestEnabled by remember { mutableStateOf(roadSignTestPrefs.getBoolean("enabled", false)) }
    var roadSignTestSigns by remember { mutableStateOf<List<UpcomingRoadSignInfo>>(emptyList()) }

    var isWifiConnected by remember { mutableStateOf(hasWifiConnection(context)) }
    var isInternetAvailable by remember { mutableStateOf(hasInternetConnection(context)) }
    var isBluetoothConnected by remember { mutableStateOf(hasBluetoothConnection(context)) }
    var networkShortName by remember { mutableStateOf(getNetworkShortName(context)) }
    var bluetoothShortName by remember { mutableStateOf(getBluetoothShortName(context)) }

    // Standalone Trafik Ajanı'nın kendi deposunu üst bardaki radar ikonuna yansıt.
    var fullTrafficAgentActive by remember {
        mutableStateOf(TrafikDurumDeposu.servisAktifMi(context))
    }
    var fullTrafficAgentStatus by remember { mutableStateOf("Kapalı") }
    var fullTrafficAgentSeverity by remember { mutableStateOf(0) }


    // _21 araç testi: park halinde sahte levhalar üretir. Gerçek sürüşte otomatik olarak devreden çıkar.
    LaunchedEffect(Unit) {
        while (true) {
            val enabled = roadSignTestPrefs.getBoolean("enabled", false)
            val mode = roadSignTestPrefs.getString("mode", "sequence") ?: "sequence"
            var startedAt = roadSignTestPrefs.getLong("started_at", 0L)
            if (startedAt <= 0L) {
                startedAt = System.currentTimeMillis()
                roadSignTestPrefs.edit().putLong("started_at", startedAt).apply()
            }

            roadSignTestEnabled = enabled
            roadSignTestSigns = if (enabled) {
                buildRoadSignTestSigns(mode, System.currentTimeMillis() - startedAt)
            } else {
                emptyList()
            }
            delay(500L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isWifiConnected = hasWifiConnection(context)
            isInternetAvailable = hasInternetConnection(context)
            isBluetoothConnected = hasBluetoothConnection(context)
            networkShortName = getNetworkShortName(context)
            bluetoothShortName = getBluetoothShortName(context)

            fullTrafficAgentActive = TrafikDurumDeposu.servisAktifMi(context)
            val trafficSnapshot = TrafikDurumDeposu.trafikDurumunuAl(context)
            if (fullTrafficAgentActive) {
                val score = trafficSnapshot?.ilk20KmPuani?.coerceIn(0, 10)
                fullTrafficAgentStatus = if (score != null) {
                    "PUAN:$score"
                } else {
                    "AJAN aktif • ilk tarama bekleniyor"
                }
                fullTrafficAgentSeverity = when {
                    score == null -> 0
                    score >= 9 -> 4
                    score >= 7 -> 3
                    score >= 4 -> 2
                    score >= 1 -> 1
                    else -> 0
                }
            } else {
                fullTrafficAgentStatus = "Kapalı"
                fullTrafficAgentSeverity = 0
            }
            delay(2_000L)
        }
    }

    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    val weatherUiState by
    weatherViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(
        uiState.latitude,
        uiState.longitude
    ) {
        val latitude =
            uiState.latitude

        val longitude =
            uiState.longitude

        if (
            latitude != null &&
            longitude != null
        ) {
            weatherViewModel.refreshWeather(
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    LaunchedEffect(isInternetAvailable) {
        if (isInternetAvailable) {
            val latitude = uiState.latitude
            val longitude = uiState.longitude
            if (latitude != null && longitude != null) {
                weatherViewModel.refreshWeather(latitude, longitude, force = true)
            }
        }
    }

    // Eski uygulamadaki davranış: araç ünitesinin interneti geç hazır olabiliyor.
    // Hava verisi yoksa 30 sn'de bir yeniden dene; kullanıcı elle yenilemek zorunda kalmasın.
    LaunchedEffect(uiState.latitude, uiState.longitude) {
        while (true) {
            delay(30_000L)
            val latitude = uiState.latitude
            val longitude = uiState.longitude
            val state = weatherViewModel.uiState.value
            if (latitude != null && longitude != null && (!state.hasData || state.errorMessage != null)) {
                weatherViewModel.refreshWeather(latitude, longitude, force = true)
            }
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            if (permissions.containsKey(Manifest.permission.ACCESS_FINE_LOCATION) ||
                permissions.containsKey(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                val isGranted =
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                viewModel.onLocationPermissionResult(isGranted)
            }
        }

    LaunchedEffect(Unit) {

        if (hasLocationPermission(context)) {

            viewModel.onLocationPermissionResult(
                true
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            }

        } else {

            val requested = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requested += Manifest.permission.BLUETOOTH_CONNECT
                requested += Manifest.permission.BLUETOOTH_SCAN
            }
            permissionLauncher.launch(requested.toTypedArray())
        }
    }

    LifecycleStartEffect(Unit) {

        viewModel.startCompassTracking()

        if (hasLocationPermission(context)) {
            viewModel.startLocationTracking()
        }

        onStopOrDispose {
            viewModel.stopLocationTracking()
            viewModel.stopCompassTracking()
        }
    }

    val todayForecast = weatherUiState.dailyForecast.firstOrNull()
    val sunriseTime = todayForecast?.sunrise?.substringAfter("T")?.take(5) ?: "--:--"
    val sunsetTime = todayForecast?.sunset?.substringAfter("T")?.take(5) ?: "--:--"

    val displayedRoadSigns =
        if (roadSignTestEnabled && uiState.speedKmh < 5) roadSignTestSigns
        else uiState.upcomingRoadSigns

    Box(modifier = Modifier.fillMaxSize()) {
    DriveDashboardScreen(

        speedKmh =
            uiState.speedKmh,
        speedDataSource = uiState.speedDataSource,

        direction =
            uiState.direction,

        altitudeMeters =
            uiState.altitudeMeters,

        tripDistanceKm =
            uiState.tripDistanceKm,

        isTripActive =
            uiState.isTripActive,

        isSpeedCorridorActive =
            uiState.isSpeedCorridorActive,

        isRouteSlopePanelVisible = uiState.isRouteSlopePanelVisible,
        isLiveSlopePanelVisible = uiState.isLiveSlopePanelVisible,
        isLiveSlopeHalfVisible = uiState.isLiveSlopeHalfVisible,
        currentSlopePercent = uiState.currentSlopePercent,
        totalClimbMeters = uiState.totalClimbMeters,
        totalDescentMeters = uiState.totalDescentMeters,
        liveElevationProfileMeters = uiState.liveElevationProfileMeters,
        liveElevationDistanceKm = uiState.liveElevationDistanceKm,
        currentLatitude = uiState.latitude,
        currentLongitude = uiState.longitude,
        isTrafficAgentActive = fullTrafficAgentActive,
        trafficAgentStatus = fullTrafficAgentStatus,
        trafficDelaySeconds = 0,
        trafficIncidentCount = 0,
        trafficSeverity = fullTrafficAgentSeverity,

        speedCorridorDistanceKm =
            uiState.speedCorridorDistanceKm,

        speedCorridorDurationSeconds =
            uiState.speedCorridorDurationSeconds,

        speedCorridorAverageSpeedKmh =
            uiState.speedCorridorAverageSpeedKmh,

        upcomingRoadSigns =
            displayedRoadSigns,

        tripDurationSeconds =
            uiState.tripDurationSeconds,

        tripMovingDurationSeconds =
            uiState.tripMovingDurationSeconds,

        tripParkDurationSeconds =
            uiState.tripParkDurationSeconds,

        tripAverageSpeedKmh =
            uiState.tripAverageSpeedKmh,

        tripMaxSpeedKmh =
            uiState.tripMaxSpeedKmh,

        tripEstimatedFuelConsumedLiters =
            uiState.tripEstimatedFuelConsumedLiters,

        tripEstimatedFuelCost =
            uiState.tripEstimatedFuelCost,
        todayEstimatedFuelConsumedLiters = uiState.todayEstimatedFuelConsumedLiters,
        todayEstimatedFuelCost = uiState.todayEstimatedFuelCost,

        todayTotalDistanceKm =
            uiState.todayTotalDistanceKm,

        tripStartedAtEpochMillis =
            uiState.tripStartedAtEpochMillis,

        tripEndedAtEpochMillis =
            uiState.tripEndedAtEpochMillis,

        vehicleMode =
            uiState.vehicleMode,

        selectedVehicleId =
            uiState.selectedVehicleId,

        customCarImageUri =
            uiState.customCarImageUri,

        customCaravanImageUri =
            uiState.customCaravanImageUri,

        locationStatus =
            uiState.locationStatus,

        isParkMode =
            uiState.isParkMode && !uiState.forceSpeedometerDisplay,

        parkDurationSeconds =
            uiState.parkDurationSeconds,

        isCompassPanelVisible =
            uiState.isCompassPanelVisible,

        isCompassSensorAvailable =
            uiState.isCompassSensorAvailable,

        compassHeadingDegrees =
            uiState.compassHeadingDegrees,

        compassAccuracy =
            uiState.compassAccuracy,

        qiblaBearingDegrees =
            uiState.qiblaBearingDegrees,

        fuelPercentage =
            uiState.fuelPercentage,
        fuelDataSource = uiState.fuelDataSource,

        remainingFuelLiters =
            uiState.remainingFuelLiters,

        estimatedFuelRangeKm =
            uiState.estimatedFuelRangeKm,

        isLowFuel =
            uiState.isLowFuel,

        // Akıllı hız uyarısı
        speedLimitKmh =
            uiState.speedLimitKmh,

        speedLimitSource =
            uiState.speedLimitSource,

        speedWarningState =
            uiState.speedWarningState,

        transientVehicleAlertMessage =
            uiState.transientVehicleAlertMessage,

        obdAlertMessage = obdAlertMessage,

        weatherUiState =
            weatherUiState,
        sunriseTime = sunriseTime,
        sunsetTime = sunsetTime,

        onFuelClick =
            onFuelClick,

        onStatisticsClick =
            onStatisticsClick,

        onSpeedCorridorClick =
            onSpeedCorridorClick,

        onPrayerClick =
            onPrayerClick,

        isObdConnected =
            isObdConnected,

        isBluetoothConnected =
            isBluetoothConnected,

        isWifiConnected =
            isWifiConnected,

        isInternetAvailable =
            isInternetAvailable,

        wifiShortName = networkShortName,
        bluetoothShortName = bluetoothShortName,
        obdShortName = obdShortName,
        selectedDriverName = selectedDriverName,
        isAppSoundEnabled = isAppSoundEnabled,
        isAppAudioPlaying = isAppAudioPlaying,
        onAppSoundToggle = {
            val enabled = !isAppSoundEnabled
            voiceManager.setMasterEnabled(enabled)
            isAppSoundEnabled = enabled
        },

        isGpsActive =
            uiState.isGpsActive,

        onWifiClick = { openWifiPanel(context) },

        onBluetoothClick = { openBluetoothSettings(context) },

        onObdClick =
            onObdClick,

        onRouteSlopeClick = viewModel::toggleRouteSlopePanel,
        onLiveSlopeClick = viewModel::toggleLiveSlopePanel,
        onLiveSlopeHalfVisibilityChange = viewModel::setLiveSlopeHalfVisible,
        onTrafficAgentClick = onTrafficAgentClick,

        onNavigationClick = onNavigationClick,
        onHomeClick = onHomeClick,
        onWorkClick = onWorkClick,
        onClockClick = onClockClick,
        onSettingsClick = onSettingsClick,

        onWeatherDetailsClick =
            onWeatherDetailsClick,

        onCompassClick =
            viewModel::toggleCompassPanel,

        onVehicleModeChange = {
            onVehicleSelectionClick()
        },

        onTripStart =
            viewModel::startTrip,

        onTripStop =
            viewModel::stopTrip
    )
    }
}

private fun buildRoadSignTestSigns(
    mode: String,
    elapsedMillis: Long
): List<UpcomingRoadSignInfo> {
    if (mode == "dual") {
        return listOf(
            UpcomingRoadSignInfo(
                kind = UpcomingRoadSignKind.ROAD_WORK,
                distanceMeters = 180,
                label = "TEST • Yol çalışması"
            ),
            UpcomingRoadSignInfo(
                kind = UpcomingRoadSignKind.SPEED_LIMIT,
                distanceMeters = 320,
                speedLimitKmh = 90,
                label = "TEST • Hız sınırı 90"
            )
        )
    }

    val sequence = listOf(
        UpcomingRoadSignInfo(UpcomingRoadSignKind.SPEED_LIMIT, 500, 90, "TEST • Hız sınırı 90"),
        UpcomingRoadSignInfo(UpcomingRoadSignKind.ROAD_WORK, 500, null, "TEST • Yol çalışması"),
        UpcomingRoadSignInfo(UpcomingRoadSignKind.ROAD_CLOSURE, 500, null, "TEST • Yol kapalı"),
        UpcomingRoadSignInfo(UpcomingRoadSignKind.TRAFFIC_JAM, 500, null, "TEST • Trafik yoğunluğu"),
        UpcomingRoadSignInfo(UpcomingRoadSignKind.TUNNEL, 500, null, "TEST • Tünel")
    )

    val cycleMillis = 12_000L
    val safeElapsed = elapsedMillis.coerceAtLeast(0L)
    val index = ((safeElapsed / cycleMillis) % sequence.size).toInt()
    val inCycle = safeElapsed % cycleMillis
    val progress = (inCycle.toDouble() / cycleMillis.toDouble()).coerceIn(0.0, 1.0)
    val rawDistance = 500.0 - (450.0 * progress)
    val distance = ((rawDistance / 10.0).roundToInt() * 10).coerceIn(50, 500)

    return listOf(sequence[index].copy(distanceMeters = distance))
}

private fun hasLocationPermission(
    context: Context
): Boolean {

    val fineLocationGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    val coarseLocationGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    return fineLocationGranted ||
            coarseLocationGranted
}

private fun hasWifiConnection(context: Context): Boolean {
    return try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        false
    }
}


private fun hasInternetConnection(context: Context): Boolean {
    return try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (_: Exception) {
        false
    }
}

private fun openWifiPanel(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}

private fun openBluetoothSettings(context: Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun hasBluetoothConnection(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return false
        if (!adapter.isEnabled) return false

        val profileConnected =
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        if (profileConnected) true else {
            adapter.bondedDevices?.any { device ->
                runCatching {
                    val method = device.javaClass.getDeclaredMethod("isConnected")
                    method.isAccessible = true
                    method.invoke(device) as? Boolean == true
                }.getOrDefault(false)
            } == true
        }
    } catch (_: Exception) {
        false
    }
}


@Suppress("DEPRECATION")
private fun getWifiShortName(context: Context): String {
    if (!hasWifiConnection(context)) return "---"
    return runCatching {
        var raw = ""
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = caps?.transportInfo as? android.net.wifi.WifiInfo
            raw = wifiInfo?.ssid?.trim('\"').orEmpty()
        }
        if (raw.isBlank() || raw.equals("<unknown ssid>", true)) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            raw = wifi?.connectionInfo?.ssid?.trim('\"').orEmpty()
        }
        if (raw.isBlank() || raw.equals("<unknown ssid>", true) || raw == "0x") "WFI"
        else raw.removePrefix("\"").removeSuffix("\"")
            .filter { it.isLetterOrDigit() }
            .take(3)
            .uppercase()
            .ifBlank { "WFI" }
    }.getOrDefault("NET")
}

/** Bağlı internet taşıyıcısını ekrana sığan üç karakterlik bir etikete dönüştürür. */
private fun getNetworkShortName(context: Context): String {
    if (!hasInternetConnection(context)) return "NET"
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching "NET"
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching "NET"
        val raw = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getWifiShortName(context)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                telephony?.networkOperatorName?.takeIf { it.isNotBlank() }
                    ?: telephony?.simOperatorName?.takeIf { it.isNotBlank() }
                    ?: "NET"
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
            else -> "NET"
        }
        shortConnectionLabel(raw, "NET")
    }.getOrDefault("NET")
}

private fun shortConnectionLabel(value: String?, fallback: String): String =
    value.orEmpty()
        .filter { it.isLetterOrDigit() }
        .take(3)
        .uppercase()
        .ifBlank { fallback }

@Suppress("MissingPermission", "DEPRECATION")
private fun getBluetoothShortName(context: Context): String {
    if (!hasBluetoothConnection(context)) return "---"
    return runCatching {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return@runCatching "BT"
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return@runCatching "BT"

        // Eski çalışan uygulamadaki yöntem: bondedDevices içinde gerçekten bağlı olanı
        // isConnected() ile bul. getDeclaredMethod bazı üreticilerde getMethod'dan daha güvenilir.
        val connected = adapter.bondedDevices?.firstOrNull { device ->
            runCatching {
                val method = device.javaClass.getDeclaredMethod("isConnected")
                method.isAccessible = true
                method.invoke(device) as? Boolean == true
            }.getOrDefault(false)
        }
        val profileConnected =
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        // Eski çalışan sürümdeki fallback: profil bağlıysa reflection cihazı bulamazsa
        // bağlı sistemin adını gösterebilmek için bondedDevices içinden ilk okunabilir adı kullan.
        val name = connected?.name
            ?: if (profileConnected) adapter.bondedDevices?.firstOrNull { !it.name.isNullOrBlank() }?.name else null
            ?: "BT"
        name?.filter { it.isLetterOrDigit() }?.take(3)?.uppercase().orEmpty().ifBlank { "BT" }
    }.getOrDefault("BT")
}
