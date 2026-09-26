package com.aldanmaz.drivedashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import android.location.Geocoder
import android.telephony.TelephonyManager
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aldanmaz.drivedashboard.ui.screen.drive.DriveDashboardRoute
import com.aldanmaz.drivedashboard.ui.screen.corridor.SpeedCorridorScreen
import com.aldanmaz.drivedashboard.ui.screen.drive.DriveDashboardViewModel
import com.aldanmaz.drivedashboard.ui.screen.fuel.FuelRoute
import com.aldanmaz.drivedashboard.ui.screen.statistics.StatisticsRoute
import com.aldanmaz.drivedashboard.ui.screen.vehicle.VehicleSelectionScreen
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherDetailScreen
import com.aldanmaz.drivedashboard.ui.screen.weather.WeatherViewModel
import com.aldanmaz.drivedashboard.ui.screen.prayer.PrayerScreen
import com.aldanmaz.drivedashboard.ui.screen.prayer.PrayerViewModel
import com.aldanmaz.drivedashboard.ui.screen.obd.ObdScreen
import com.aldanmaz.drivedashboard.ui.screen.obd.ObdViewModel
import com.aldanmaz.drivedashboard.ui.screen.alerts.WarningHistoryScreen
import com.aldanmaz.drivedashboard.ui.screen.navigation.NavigationScreen
import com.aldanmaz.drivedashboard.ui.screen.navigation.launchSavedHome
import com.aldanmaz.drivedashboard.ui.screen.navigation.launchSavedWork
import com.aldanmaz.drivedashboard.ui.screen.clock.AnalogClockScreen
import com.aldanmaz.drivedashboard.ui.theme.AldanmazDriveTheme
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime
import com.aldanmaz.drivedashboard.ui.screen.settings.AppearanceSettings
import com.aldanmaz.drivedashboard.ui.screen.settings.AppearanceSettingsRepository
import com.aldanmaz.drivedashboard.ui.screen.settings.AppearanceSettingsScreen
import com.aldanmaz.drivedashboard.ui.screen.settings.HeaderLayoutSettingsScreen
import com.aldanmaz.drivedashboard.ui.screen.settings.VehicleSystemSettingsScreen
import com.aldanmaz.drivedashboard.ui.screen.settings.ProgramSettingsScreen
import com.aldanmaz.drivedashboard.ui.screen.help.HelpScreen
import com.aldanmaz.drivedashboard.ui.call.IncomingCallController
import com.aldanmaz.drivedashboard.ui.call.IncomingCallOverlay
import com.aldanmaz.drivedashboard.ui.screen.splash.SplashScreen
import com.aldanmaz.drivedashboard.trafficagent.TrafficAgentHostScreen
import com.aldanmaz.drivedashboard.trafficagent.TrafikTakipServisi
import com.aldanmaz.drivedashboard.trafficagent.TrafikDurumDeposu
import com.aldanmaz.drivedashboard.trafficagent.AjanAyarlari
import com.aldanmaz.drivedashboard.data.ai.GeminiActionBridge
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveForegroundService
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveStatus
import com.aldanmaz.drivedashboard.data.ai.GeminiReconnectPolicy
import com.aldanmaz.drivedashboard.data.ai.GeminiVehicleSnapshot
import com.aldanmaz.drivedashboard.data.alert.MediaAppController
import com.aldanmaz.drivedashboard.data.alert.WarningHistoryRepository
import com.aldanmaz.drivedashboard.data.fuel.FuelPreferencesRepository
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalog
import com.aldanmaz.drivedashboard.voice.HandsFreeWakeWordController
import com.aldanmaz.drivedashboard.voice.VoiceCommandSpeaker
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripEntity
import com.aldanmaz.drivedashboard.ui.screen.driver.DRIVER_PREFS
import com.aldanmaz.drivedashboard.ui.screen.driver.DriverProfile
import com.aldanmaz.drivedashboard.ui.screen.driver.DriverSelectionOverlay
import com.aldanmaz.drivedashboard.ui.screen.driver.loadDrivers

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isColdLaunch = savedInstanceState == null
        hideSystemBars()

        setContent {
            AldanmazDriveTheme(darkTheme = true) {

                val navController =
                    rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                var trafficVoiceCommand by remember { mutableStateOf("") }
                var trafficVoiceCommandRequestId by remember { mutableStateOf(0) }
                var showNavigationChooser by rememberSaveable { mutableStateOf(false) }

                val safeBack: () -> Unit = {
                    when (currentRoute) {
                        null -> {
                            navController.navigate(ROUTE_DRIVE) {
                                launchSingleTop = true
                            }
                        }
                        ROUTE_DRIVE, ROUTE_SPLASH -> Unit
                        else -> {
                            val popped = navController.popBackStack()
                            if (!popped) {
                                navController.navigate(ROUTE_DRIVE) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }

                // Araç multimedya cihazlarında sistem geri tuşu bazen Activity'yi kapatıp
                // beyaz launcher ekranında bırakabiliyor. Uygulama içindeyken geri her zaman
                // güvenli biçimde önceki ALD ekranına gider; ana ekranda ise uygulamayı kapatmaz.
                BackHandler(enabled = currentRoute != ROUTE_SPLASH) {
                    safeBack()
                }

                val context = LocalContext.current
                val geminiLiveManager = remember(context.applicationContext) {
                    GeminiLiveManager.getInstance(context.applicationContext)
                }
                val geminiLiveState by geminiLiveManager.state.collectAsStateWithLifecycle()
                LaunchedEffect(
                    geminiLiveState.status,
                    geminiLiveState.message,
                    geminiLiveState.isRecoverableError,
                ) {
                    // 99: Otomatik yenilenen kısa Live kopmaları sürücüyü sürekli
                    // tostla rahatsız etmez. Kurulum ve kalıcı hatalar görünür kalır.
                    if (GeminiReconnectPolicy.shouldShowUserError(
                            geminiLiveState.status,
                            geminiLiveState.isRecoverableError,
                        )
                    ) {
                        android.widget.Toast.makeText(
                            context,
                            geminiLiveState.message,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                val voicePrefs = remember {
                    val current = context.getSharedPreferences("hey_car_voice", android.content.Context.MODE_PRIVATE)
                    val legacy = context.getSharedPreferences("hey_sandero_voice", android.content.Context.MODE_PRIVATE)
                    if (!current.contains("enabled") && legacy.contains("enabled")) {
                        current.edit().putBoolean("enabled", legacy.getBoolean("enabled", true)).apply()
                    }
                    current
                }
                var microphoneMode by remember {
                    mutableStateOf(
                        voicePrefs.getString(
                            "microphone_mode",
                            "AUTO"
                        ) ?: "AUTO"
                    )
                }
                LaunchedEffect(Unit) {
                    // 82: Eski Hey Car / yüzen mikrofon dinleyicisi tamamen kapalı.
                    // Ses girişi yalnız Multimedya kartındaki Gemini simgesinden başlatılır.
                    if (microphoneMode != "OFF") {
                        microphoneMode = "OFF"
                        voicePrefs.edit()
                            .putString("microphone_mode", "OFF")
                            .putBoolean("hands_free_enabled", false)
                            .putBoolean("enabled", false)
                            .apply()
                    }
                }
                var handsFreeEnabled by remember { mutableStateOf(false) }
                var handsFreeCommandInProgress by remember { mutableStateOf(false) }
                var followUpLaunchRequestId by remember { mutableStateOf(0) }
                var followUpUntilElapsedMs by remember { mutableStateOf(0L) }
                var followUpResumeDelayMs by remember { mutableStateOf(0L) }
                var continuousCommandMode by rememberSaveable { mutableStateOf(false) }
                var manualCommandListening by remember { mutableStateOf(false) }
                var voiceRecognizerGeneration by remember { mutableStateOf(0) }
                var pendingVoiceConfirmation by rememberSaveable { mutableStateOf<String?>(null) }
                LaunchedEffect(pendingVoiceConfirmation) {
                    val waitingFor = pendingVoiceConfirmation ?: return@LaunchedEffect
                    delay(20_000L)
                    if (pendingVoiceConfirmation == waitingFor) pendingVoiceConfirmation = null
                }
                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val incomingCallController = remember {
                    IncomingCallController(context.applicationContext)
                }
                val incomingCallState by
                    incomingCallController.state.collectAsStateWithLifecycle()

                val phonePermissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val phoneStateGranted =
                            permissions[Manifest.permission.READ_PHONE_STATE] == true ||
                                    incomingCallController.hasPhoneStatePermission()
                        if (phoneStateGranted) {
                            incomingCallController.start()
                        }
                    }

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasAudioPermission = granted
                    if (!granted) {
                        voicePrefs.edit()
                            .putString("microphone_mode", "OFF")
                            .putBoolean("hands_free_enabled", false)
                            .apply()
                    }
                }

                val geminiMicPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasAudioPermission = granted
                    if (granted) {
                        GeminiLiveForegroundService.start(context)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Gemini Live için mikrofon izni gerekli.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 96: Gemini yalnız kullanıcı Gemini ikonuna dokunduğunda başlar.
                // Otomatik Live açılışı ve arka planda wake-word SpeechRecognizer tamamen kaldırıldı.

                DisposableEffect(voicePrefs) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        if (key == "microphone_mode") {
                            microphoneMode = prefs.getString(key, "OFF") ?: "OFF"
                            handsFreeEnabled = false
                            manualCommandListening = false
                            if (microphoneMode == "OFF") {
                                followUpUntilElapsedMs = 0L
                                continuousCommandMode = false
                            }
                        }
                    }
                    voicePrefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { voicePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                LaunchedEffect(microphoneMode) {
                    if (microphoneMode != "OFF" && !hasAudioPermission) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                DisposableEffect(incomingCallController) {
                    incomingCallController.start()
                    onDispose { incomingCallController.stop() }
                }

                LaunchedEffect(Unit) {
                    // Telefon köprüsü kaldırıldı. Yalnız mevcut gelen arama ekranı için
                    // telefon durum/yanıtlama izinleri telefon cihazında istenir.
                    delay(1800L)
                    if (deviceHasTelephony(context)) {
                        val callStatePermissions = arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ANSWER_PHONE_CALLS,
                        )
                        val missing = callStatePermissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                        if (missing) phonePermissionLauncher.launch(callStatePermissions) else {
                            incomingCallController.start()
                        }
                    }
                }

                val appearanceRepository = remember { AppearanceSettingsRepository(context.applicationContext) }
                val appearance by appearanceRepository.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
                val appearanceScope = rememberCoroutineScope()
                SideEffect {
                    DashboardPaletteRuntime.accent = Color(appearance.accentColorArgb)
                    DashboardPaletteRuntime.isOled = appearance.theme == "OLED"
                }

                val driveViewModel:
                        DriveDashboardViewModel =
                    viewModel()

                val driverPrefs = remember { context.getSharedPreferences(DRIVER_PREFS, android.content.Context.MODE_PRIVATE) }
                var driverSelectionEnabled by remember { mutableStateOf(driverPrefs.getBoolean("selection_enabled", true)) }
                val drivers = loadDrivers(context)
                // 137: İlk kurulum/ilk açılış varsayılanı daima 1. kullanıcı Mehmet olsun.
                // Eski sürümlerde 2 kaydedilmişse yalnız bir kez 1'e migrate edilir; kullanıcı
                // sonradan ayarlardan farklı bir varsayılan seçerse o seçim korunur.
                val configuredDefaultDriverId = remember {
                    if (!driverPrefs.getBoolean("default_driver_initialized_137", false)) {
                        driverPrefs.edit()
                            .putString("default_driver_id", "1")
                            .putBoolean("default_driver_initialized_137", true)
                            .apply()
                        "1"
                    } else {
                        driverPrefs.getString("default_driver_id", "1") ?: "1"
                    }
                }
                val configuredDefaultDriver = drivers.firstOrNull { it.id == configuredDefaultDriverId } ?: drivers.first()
                // 94: Rotation/configuration change gerçek uygulama başlangıcı değildir.
                // DriveDashboardViewModel configuration change boyunca yaşar; mevcut sürücüyü oradan korur.
                // Gerçek soğuk başlangıçta ViewModel yeni olduğu için sürücü seçimi yeniden gösterilir.
                val initialDriverId = remember(driverSelectionEnabled) {
                    driveViewModel.currentDriverSessionIdOrNull() ?: if (!driverSelectionEnabled) {
                        configuredDefaultDriver.id
                    } else null
                }
                var selectedDriverId by remember { mutableStateOf(initialDriverId) }
                var driverSelectionSeconds by remember { mutableIntStateOf(10) }
                var driverSelectionCommitted by remember { mutableStateOf(initialDriverId != null) }
                var driverWelcomeInProgress by remember { mutableStateOf(false) }
                val selectedDriver = drivers.firstOrNull { it.id == selectedDriverId }
                fun selectDriver(driver: com.aldanmaz.drivedashboard.ui.screen.driver.DriverProfile, announce: Boolean = true) {
                    if (driverSelectionCommitted) return
                    driverSelectionCommitted = true
                    driverSelectionSeconds = 0
                    selectedDriverId = driver.id
                    driverPrefs.edit()
                        .putString("active_driver_id", driver.id)
                        .putLong("active_driver_selected_at", System.currentTimeMillis())
                        .apply()
                    driveViewModel.setDriverSession(driver.id, driver.name)
                    if (announce) {
                        // 91: Eski Mehmet/Nurdan MP3 karşılama kaldırıldı. Seçili sürücüyü
                        // Gemini kendi sesiyle, Live hazır olduğunda bir kez karşılar.
                        driverWelcomeInProgress = false
                    }
                }
                LaunchedEffect(currentRoute) {
                    driverSelectionEnabled = driverPrefs.getBoolean("selection_enabled", true)
                }
                LaunchedEffect(driverSelectionEnabled) {
                    if (!driverSelectionEnabled && selectedDriverId == null) {
                        selectedDriverId = configuredDefaultDriver.id
                    }
                }
                LaunchedEffect(driverSelectionEnabled, selectedDriverId, currentRoute == ROUTE_SPLASH) {
                    if (driverSelectionEnabled && selectedDriverId == null && currentRoute != ROUTE_SPLASH) {
                        while (driverSelectionSeconds > 0 && selectedDriverId == null && !driverSelectionCommitted) {
                            delay(1_000L)
                            driverSelectionSeconds -= 1
                        }
                        if (selectedDriverId == null && !driverSelectionCommitted) {
                            // 138: 10 saniye içinde kullanıcı seçilmezse 2. kişi Nurdan seçilir.
                            // Varsayılan kullanıcı/1. kişi yine Mehmet'tir; bu yalnız zaman aşımı davranışıdır.
                            val timeoutDriver = drivers.firstOrNull { it.id == "2" } ?: configuredDefaultDriver
                            selectDriver(timeoutDriver)
                        }
                    }
                }
                LaunchedEffect(selectedDriver?.id) {
                    selectedDriver?.let { driver ->
                        // 136: Önce gerçek aktif sürücü oturumunu güncelle, sonra yalnız bu sürücü için karşılama kuyruğu oluştur.
                        // Böylece önceki/varsayılan sürücünün bekleyen karşılama mesajı yeni seçimi geçemez.
                        driveViewModel.setDriverSession(driver.id, driver.name)
                        geminiLiveManager.queueDriverWelcome(driver.name, driver.honorific)
                    }
                }

                val weatherViewModel:
                        WeatherViewModel =
                    viewModel()
                val prayerViewModel: PrayerViewModel = viewModel()
                val obdViewModel: ObdViewModel = viewModel()
                val obdUiState by obdViewModel.uiState.collectAsStateWithLifecycle()
                val warningHistoryRepository = remember { WarningHistoryRepository.getInstance(context.applicationContext) }
                val warningHistoryEntries by warningHistoryRepository.entries.collectAsStateWithLifecycle()
                var lastTechnicalWarningSignature by remember { mutableStateOf<String?>(null) }
                val prayerUiState by prayerViewModel.uiState.collectAsStateWithLifecycle()
                val driveGeminiState by driveViewModel.uiState.collectAsStateWithLifecycle()
                val weatherGeminiState by weatherViewModel.uiState.collectAsStateWithLifecycle()

                // 131: OBD sağlıklı ve tazeyse araç hız/yakıt için birincil kaynaktır;
                // OBD bayatlarsa DriveViewModel mevcut GPS/HESAP kaynağına otomatik döner.
                LaunchedEffect(obdUiState.isConnected, obdUiState.liveData, obdUiState.lastLiveDataEpochMs) {
                    driveViewModel.updateObdPriorityData(
                        isConnected = obdUiState.isConnected,
                        liveData = obdUiState.liveData,
                        lastUpdateEpochMs = obdUiState.lastLiveDataEpochMs,
                    )
                }

                // Uyarı geçmişine yalnız teknik OBD ve hız/TomTom uyarıları girer.
                // Eğim/rampa, genel güvenlik ve hava uyarıları özellikle hariçtir.
                LaunchedEffect(obdUiState.isConnected, obdUiState.lastLiveDataEpochMs) {
                    val freshObd = obdUiState.isConnected && obdUiState.lastLiveDataEpochMs?.let { System.currentTimeMillis() - it in 0L..10_000L } == true
                    if (!freshObd) {
                        if (!obdUiState.isConnected) lastTechnicalWarningSignature = null
                    } else {
                        val obd = obdUiState.liveData
                        val messages = buildList {
                            if (obd.milOn == true) add("MOTOR ARIZA LAMBASI AKTİF")
                            if (obd.dtcCodes.isNotEmpty()) add("ARIZA KODU ${obd.dtcCodes.take(3).joinToString(", ")}")
                            if ((obd.coolantCelsius ?: 0) >= 105) add("MOTOR SICAKLIĞI YÜKSEK")
                            val voltage = obd.batteryVoltage ?: obd.controlModuleVoltage
                            if (voltage != null && (voltage < 11.5 || voltage > 15.2)) add("AKÜ/ŞARJ VOLTAJI NORMAL DIŞI")
                            if ((obd.engineOilCelsius ?: 0) >= 125) add("MOTOR YAĞI SICAKLIĞI YÜKSEK")
                            if (obd.egrErrorPercent != null && kotlin.math.abs(obd.egrErrorPercent) >= 20) add("EGR SAPMASI YÜKSEK")
                        }
                        val signature = messages.sorted().joinToString("|").ifBlank { null }
                        if (signature != lastTechnicalWarningSignature) {
                            messages.forEach { warningHistoryRepository.record("OBD", it, true) }
                            lastTechnicalWarningSignature = signature
                        }
                    }
                }
                LaunchedEffect(warningHistoryEntries) {
                    if (warningHistoryEntries.none { it.technicalVehicleAlert }) lastTechnicalWarningSignature = null
                }
                LaunchedEffect(obdUiState.errorMessage) {
                    obdUiState.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        warningHistoryRepository.record("SİSTEM", "OBD • ${it.take(80)}", false)
                    }
                }
                LaunchedEffect(driveGeminiState.speedWarningState, driveGeminiState.speedLimitKmh) {
                    val limit = driveGeminiState.speedLimitKmh
                    if (limit != null) {
                        when (driveGeminiState.speedWarningState.name) {
                            "OVER_LIMIT" -> warningHistoryRepository.record("HIZ", "HIZ SINIRI AŞILDI • limit $limit km/sa", false)
                            "APPROACHING" -> warningHistoryRepository.record("HIZ", "HIZ SINIRINA YAKLAŞIYORSUNUZ • limit $limit km/sa", false)
                        }
                    }
                }

                val fuelRepository = remember { FuelPreferencesRepository(context.applicationContext) }
                var currentAddressText by remember { mutableStateOf<String?>(null) }
                val addressLatKey = driveGeminiState.latitude?.let { (it * 1000.0).roundToInt() }
                val addressLonKey = driveGeminiState.longitude?.let { (it * 1000.0).roundToInt() }
                LaunchedEffect(addressLatKey, addressLonKey) {
                    val lat = driveGeminiState.latitude
                    val lon = driveGeminiState.longitude
                    currentAddressText = if (lat != null && lon != null) {
                        withContext(Dispatchers.IO) { resolveCurrentAddress(context, lat, lon) }
                    } else null
                }

                LaunchedEffect(
                    currentRoute,
                    driveGeminiState,
                    weatherGeminiState,
                    obdUiState,
                    prayerUiState,
                    selectedDriver,
                    currentAddressText,
                    warningHistoryEntries
                ) {
                    GeminiActionBridge.updateSnapshot(
                        GeminiVehicleSnapshot(
                            currentScreen = currentRoute ?: ROUTE_DRIVE,
                            latitude = driveGeminiState.latitude,
                            longitude = driveGeminiState.longitude,
                            currentAddressText = currentAddressText,
                            speedKmh = driveGeminiState.speedKmh,
                            speedLimitKmh = driveGeminiState.speedLimitKmh,
                            altitudeMeters = driveGeminiState.altitudeMeters,
                            fuelPercent = driveGeminiState.fuelPercentage?.roundToInt(),
                            remainingFuelLiters = driveGeminiState.remainingFuelLiters,
                            estimatedFuelRangeKm = driveGeminiState.estimatedFuelRangeKm?.roundToInt(),
                            speedDataSource = driveGeminiState.speedDataSource,
                            fuelDataSource = driveGeminiState.fuelDataSource,
                            obdDataAvailableToday = obdUiState.lastLiveDataEpochMs?.let { epoch ->
                                java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
                            } == true,
                            todayTechnicalWarningCount = warningHistoryRepository.todayTechnicalAlerts().size,
                            todayTechnicalWarningSummary = warningHistoryRepository.todayTechnicalAlerts().take(4).joinToString(" | ") { it.message }.takeIf { it.isNotBlank() },
                            tripDistanceKm = driveGeminiState.tripDistanceKm,
                            todayDistanceKm = driveGeminiState.todayTotalDistanceKm,
                            tripAverageSpeedKmh = driveGeminiState.tripAverageSpeedKmh,
                            isTripActive = driveGeminiState.isTripActive,
                            isParkMode = driveGeminiState.isParkMode,
                            parkDurationSeconds = driveGeminiState.parkDurationSeconds,
                            vehicleMode = driveGeminiState.vehicleMode,
                            isTrafficAgentActive = driveGeminiState.isTrafficAgentActive,
                            isCompassPanelVisible = driveGeminiState.isCompassPanelVisible,
                            trafficStatus = driveGeminiState.trafficAgentStatus,
                            isSpeedCorridorActive = driveGeminiState.isSpeedCorridorActive,
                            speedCorridorAverageKmh = driveGeminiState.speedCorridorAverageSpeedKmh,
                            weatherCity = weatherGeminiState.cityName,
                            weatherTemperatureC = weatherGeminiState.temperatureCelsius?.roundToInt(),
                            weatherCondition = weatherGeminiState.condition.name,
                            weatherApparentTemperatureC = weatherGeminiState.apparentTemperatureCelsius?.roundToInt(),
                            weatherWindSpeedKmh = weatherGeminiState.windSpeedKmh?.roundToInt(),
                            weatherWindGustKmh = weatherGeminiState.windGustKmh?.roundToInt(),
                            weatherAlertText = buildSevereWeatherAlert(weatherGeminiState),
                            obdConnected = obdUiState.isConnected,
                            obdRpm = obdUiState.liveData.rpm,
                            obdVehicleSpeedKmh = obdUiState.liveData.vehicleSpeedKmh,
                            coolantCelsius = obdUiState.liveData.coolantCelsius,
                            batteryVoltage = obdUiState.liveData.batteryVoltage ?: obdUiState.liveData.controlModuleVoltage,
                            engineLoadPercent = obdUiState.liveData.engineLoadPercent,
                            throttlePercent = obdUiState.liveData.throttlePercent,
                            intakeAirCelsius = obdUiState.liveData.intakeAirCelsius,
                            mafGramsPerSecond = obdUiState.liveData.mafGramsPerSecond,
                            manifoldPressureKpa = obdUiState.liveData.manifoldPressureKpa,
                            boostPressureKpa = obdUiState.liveData.boostPressureKpa,
                            fuelRailPressureKpa = obdUiState.liveData.fuelRailPressureKpa,
                            obdFuelLevelPercent = obdUiState.liveData.fuelLevelPercent,
                            ambientAirCelsius = obdUiState.liveData.ambientAirCelsius,
                            engineOilCelsius = obdUiState.liveData.engineOilCelsius,
                            engineFuelRateLitersHour = obdUiState.liveData.engineFuelRateLitersHour,
                            engineTorquePercent = obdUiState.liveData.actualEngineTorquePercent,
                            milOn = obdUiState.liveData.milOn,
                            dtcCodes = obdUiState.liveData.dtcCodes,
                            nextPrayerName = prayerUiState.nextPrayerName.takeUnless { it == "--" },
                            nextPrayerTime = prayerUiState.nextPrayerTime.takeUnless { it == "--:--" },
                            selectedDriverName = selectedDriver?.name,
                            selectedDriverHonorific = selectedDriver?.honorific,
                        )
                    )
                    geminiLiveManager.onVehicleSnapshot(GeminiActionBridge.snapshot())
                }

                LaunchedEffect(Unit) {
                    GeminiActionBridge.commands.collect { command ->
                        fun goDrive() {
                            bringAldanmazToFront(context)
                            driveViewModel.setLiveSlopeHalfVisible(false)
                            if (driveViewModel.uiState.value.isLiveSlopePanelVisible) driveViewModel.toggleLiveSlopePanel()
                            if (driveViewModel.uiState.value.isRouteSlopePanelVisible) driveViewModel.toggleRouteSlopePanel()
                            navController.navigate(ROUTE_DRIVE) {
                                popUpTo(ROUTE_DRIVE) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        fun setVehicleByName(value: String?) {
                            val v = value.orEmpty().lowercase(java.util.Locale("tr", "TR"))
                            val id = when {
                                "sandero" in v || "dacia" in v -> "dacia_sandero_stepway_white"
                                "sedan" in v -> "sedan_white"
                                "hatch" in v -> "hatchback_white"
                                "suv" in v -> "suv_white"
                                "coupe" in v || "kupe" in v -> "coupe_white"
                                "station" in v -> "station_wagon_white"
                                "cabrio" in v || "kabrio" in v -> "cabrio_white"
                                "mini van" in v || "minivan" in v -> "mini_van_white"
                                "panel" in v -> "panelvan_white"
                                "pickup" in v || "pikap" in v -> "pickup_white"
                                "micro" in v || "mikro" in v -> "micro_white"
                                "camper" in v -> "camper_van_white"
                                "truck" in v || "kamyon" in v -> "truck_white"
                                "karavan" in v -> "trailer_caravan_white"
                                else -> null
                            }
                            id?.let { driveViewModel.setSelectedVehicle(it); goDrive() }
                        }

                        when (command.action) {
                            "open_drive" -> goDrive()
                            "go_back" -> {
                                bringAldanmazToFront(context)
                                when {
                                    driveViewModel.uiState.value.isLiveSlopeHalfVisible -> driveViewModel.setLiveSlopeHalfVisible(false)
                                    driveViewModel.uiState.value.isLiveSlopePanelVisible -> driveViewModel.toggleLiveSlopePanel()
                                    driveViewModel.uiState.value.isRouteSlopePanelVisible -> driveViewModel.toggleRouteSlopePanel()
                                    else -> safeBack()
                                }
                            }
                            "open_fuel" -> navController.navigate(ROUTE_FUEL) { launchSingleTop = true }
                            "open_statistics" -> navController.navigate(ROUTE_STATISTICS) { launchSingleTop = true }
                            "open_weather" -> navController.navigate(ROUTE_WEATHER_DETAILS) { launchSingleTop = true }
                            "open_prayer" -> navController.navigate(ROUTE_PRAYER) { launchSingleTop = true }
                            "open_obd" -> navController.navigate(ROUTE_OBD) { launchSingleTop = true }
                            "open_warning_history" -> navController.navigate(ROUTE_WARNING_HISTORY) { launchSingleTop = true }
                            "open_traffic_agent" -> navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                            "open_navigation" -> showNavigationChooser = true
                            "open_navigation_settings" -> navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                            "open_google_navigation" -> { showNavigationChooser = false; launchNavigationApp(context, "google") }
                            "open_yandex_navigation" -> { showNavigationChooser = false; launchNavigationApp(context, "yandex") }
                            "google_maps_search" -> command.value?.takeIf { it.isNotBlank() }?.let { showNavigationChooser = false; launchMapSearch(context, "google", it) }
                            "yandex_maps_search" -> command.value?.takeIf { it.isNotBlank() }?.let { showNavigationChooser = false; launchMapSearch(context, "yandex", it) }
                            "google_maps_route" -> command.value?.takeIf { it.isNotBlank() }?.let { showNavigationChooser = false; launchMapRoute(context, "google", it) }
                            "yandex_maps_route" -> command.value?.takeIf { it.isNotBlank() }?.let { showNavigationChooser = false; launchMapRoute(context, "yandex", it) }
                            "open_clock" -> navController.navigate(ROUTE_ANALOG_CLOCK) { launchSingleTop = true }
                            "open_settings" -> navController.navigate(ROUTE_PROGRAM_SETTINGS) { launchSingleTop = true }
                            "open_appearance" -> navController.navigate(ROUTE_APPEARANCE_SETTINGS) { launchSingleTop = true }
                            "open_header_layout" -> navController.navigate(ROUTE_HEADER_LAYOUT_SETTINGS) { launchSingleTop = true }
                            "open_vehicle_systems" -> navController.navigate(ROUTE_VEHICLE_SYSTEMS) { launchSingleTop = true }
                            "open_vehicle_selection" -> navController.navigate(ROUTE_VEHICLE_SELECTION) { launchSingleTop = true }
                            "open_help" -> navController.navigate(ROUTE_HELP) { launchSingleTop = true }
                            "open_speed_corridor" -> navController.navigate(ROUTE_SPEED_CORRIDOR) { launchSingleTop = true }

                            "show_route_slope" -> { goDrive(); if (!driveViewModel.uiState.value.isRouteSlopePanelVisible) driveViewModel.toggleRouteSlopePanel() }
                            "hide_route_slope" -> if (driveViewModel.uiState.value.isRouteSlopePanelVisible) driveViewModel.toggleRouteSlopePanel()
                            "show_live_slope" -> {
                                goDrive()
                                driveViewModel.setLiveSlopeHalfVisible(false)
                                if (!driveViewModel.uiState.value.isLiveSlopePanelVisible) driveViewModel.toggleLiveSlopePanel()
                            }
                            "hide_live_slope" -> if (driveViewModel.uiState.value.isLiveSlopePanelVisible) driveViewModel.toggleLiveSlopePanel()
                            "show_live_slope_half" -> {
                                goDrive()
                                driveViewModel.setLiveSlopeHalfVisible(true)
                            }
                            "hide_live_slope_half" -> driveViewModel.setLiveSlopeHalfVisible(false)
                            "show_speedometer" -> {
                                goDrive()
                                driveViewModel.showSpeedometerDisplay()
                            }
                            "show_park_screen" -> {
                                goDrive()
                                driveViewModel.showParkDisplay()
                            }
                            "toggle_compass" -> { goDrive(); driveViewModel.toggleCompassPanel() }
                            "compass_on" -> { goDrive(); if (!driveViewModel.uiState.value.isCompassPanelVisible) driveViewModel.toggleCompassPanel() }
                            "compass_off" -> if (driveViewModel.uiState.value.isCompassPanelVisible) driveViewModel.toggleCompassPanel()
                            "start_trip" -> { driveViewModel.startTrip(); goDrive() }
                            "stop_trip" -> { driveViewModel.stopTrip(); goDrive() }
                            "start_speed_corridor" -> driveViewModel.startSpeedCorridor()
                            "stop_speed_corridor" -> driveViewModel.stopSpeedCorridor()
                            "toggle_traffic_agent" -> driveViewModel.toggleTrafficAgent()
                            "traffic_agent_on" -> if (!driveViewModel.uiState.value.isTrafficAgentActive) driveViewModel.toggleTrafficAgent()
                            "traffic_agent_off" -> {
                                if (driveViewModel.uiState.value.isTrafficAgentActive) driveViewModel.toggleTrafficAgent()
                                TrafikDurumDeposu.servisAktifliginiKaydet(context, false)
                                val stopIntent = Intent(context, TrafikTakipServisi::class.java).apply {
                                    action = TrafikTakipServisi.EYLEM_DURDUR
                                }
                                runCatching { context.startService(stopIntent) }
                                runCatching { context.stopService(Intent(context, TrafikTakipServisi::class.java)) }
                            }
                            "set_vehicle_mode" -> command.value?.let { mode ->
                                when (mode.lowercase(java.util.Locale("tr", "TR"))) {
                                    "karavan" -> driveViewModel.setVehicleMode("Karavan")
                                    "otomobil", "araba" -> driveViewModel.setVehicleMode("Otomobil")
                                }
                            }
                            "set_vehicle" -> setVehicleByName(command.value)
                            "select_driver" -> if (driveViewModel.uiState.value.speedKmh < 5) {
                                val wanted = command.value.orEmpty().lowercase(java.util.Locale("tr", "TR"))
                                drivers.firstOrNull {
                                    val name = it.name.lowercase(java.util.Locale("tr", "TR"))
                                    name.contains(wanted) || wanted.contains(name)
                                }?.let { driver ->
                                    driverSelectionCommitted = true
                                    driverSelectionSeconds = 0
                                    selectedDriverId = driver.id
                                    driverPrefs.edit()
                                        .putString("active_driver_id", driver.id)
                                        .putLong("active_driver_selected_at", System.currentTimeMillis())
                                        .apply()
                                    driveViewModel.setDriverSession(driver.id, driver.name)
                                }
                            }

                            "navigate_home" -> if (!launchSavedHome(context)) navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                            "navigate_work" -> if (!launchSavedWork(context)) navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                            "navigate_destination" -> command.value?.takeIf { it.isNotBlank() }?.let { launchNavigationTo(context, it) }
                            "set_home_address" -> command.value?.trim()?.takeIf { it.isNotBlank() }?.let { address ->
                                context.getSharedPreferences("navigation_settings", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("home_address", address).apply()
                            }
                            "set_work_address" -> command.value?.trim()?.takeIf { it.isNotBlank() }?.let { address ->
                                context.getSharedPreferences("navigation_settings", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("work_address", address).apply()
                            }
                            "traffic_command" -> {
                                trafficVoiceCommand = command.value.orEmpty()
                                trafficVoiceCommandRequestId += 1
                                navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                            }
                            "set_traffic_target" -> {
                                val destination = command.value.orEmpty().trim()
                                if (destination.isNotBlank()) {
                                    trafficVoiceCommand = "trafik ajanı hedefi $destination"
                                    trafficVoiceCommandRequestId += 1
                                    navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                                }
                            }

                            "open_youtube" -> launchYouTube(context)
                            "open_music" -> MediaAppController.openMusicOrBest(context)
                            "play_music_query" -> command.value?.takeIf { it.isNotBlank() }?.let { MediaAppController.playMusicQuery(context, it) }
                            "stop_music" -> MediaAppController.stopMedia(context)
                            "close_music" -> { MediaAppController.stopMedia(context); bringAldanmazToFront(context); goDrive() }
                            "open_radio" -> MediaAppController.openRadio(context)
                            "stop_radio" -> MediaAppController.stopRadio(context)
                            "open_chatgpt" -> launchNamedApp(context, listOf("com.openai.chatgpt"))
                            "open_chrome" -> launchNamedApp(context, listOf("com.android.chrome"))
                            "close_chrome", "close_chatgpt", "close_youtube", "return_to_ald_drive" -> { bringAldanmazToFront(context); goDrive() }
                            "email_note" -> command.value?.takeIf { it.isNotBlank() }?.let { launchEmailNote(context, it) }
                            "play_web_media" -> command.value?.takeIf { it.isNotBlank() }?.let { launchYouTubeSearch(context, it) }
                            "emergency_mode" -> launchEmergencyAssist(context, currentAddressText)
                            "media_next" -> MediaAppController.nextMedia(context)
                            "media_previous" -> MediaAppController.previousMedia(context)
                            "media_play" -> MediaAppController.resumeMedia(context)
                            "media_pause" -> MediaAppController.stopMedia(context)
                            "set_program_toggle" -> applyGeminiProgramToggle(context, command.value, prayerViewModel)
                            "set_program_value" -> applyGeminiProgramValue(context, command.value, prayerViewModel)
                            "set_text_scale" -> command.value?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()?.let { raw ->
                                val scale = if (raw > 3f) raw / 100f else raw
                                appearanceScope.launch { appearanceRepository.setTextScale(scale.coerceIn(.75f, 1.8f)) }
                            }
                            "set_day_brightness" -> command.value?.filter { it.isDigit() }?.toIntOrNull()?.let { appearanceScope.launch { appearanceRepository.setDayBrightness((it.coerceIn(10, 100) / 100f)) } }
                            "set_night_brightness" -> command.value?.filter { it.isDigit() }?.toIntOrNull()?.let { appearanceScope.launch { appearanceRepository.setNightBrightness((it.coerceIn(8, 100) / 100f)) } }

                            "volume_up", "volume_down", "volume_mute", "volume_unmute", "set_volume_percent" -> {
                                val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                when (command.action) {
                                    "volume_up" -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                                    "volume_down" -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                                    "volume_mute" -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
                                    "volume_unmute" -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
                                    "set_volume_percent" -> {
                                        val percent = command.value?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 100) ?: 50
                                        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * percent / 100f).roundToInt(), android.media.AudioManager.FLAG_SHOW_UI)
                                    }
                                }
                            }
                            "brightness_up", "brightness_down", "set_brightness_percent" -> {
                                val attrs = window.attributes
                                val current = attrs.screenBrightness.takeIf { it >= 0f } ?: .6f
                                attrs.screenBrightness = when (command.action) {
                                    "brightness_up" -> (current + .1f).coerceAtMost(1f)
                                    "brightness_down" -> (current - .1f).coerceAtLeast(.08f)
                                    else -> {
                                        val percent = command.value?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(8, 100) ?: 60
                                        percent / 100f
                                    }
                                }
                                window.attributes = attrs
                            }
                            "set_day_mode" -> appearanceRepository.setDayNightMode("GUNDUZ")
                            "set_night_mode" -> appearanceRepository.setDayNightMode("GECE")
                            "set_auto_appearance" -> appearanceRepository.setDayNightMode("OTOMATIK")
                            "open_wifi_settings" -> runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
                            "open_bluetooth_settings" -> runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            "obd_connect" -> obdViewModel.connectSelected()
                            "obd_disconnect" -> obdViewModel.disconnect()
                            "refresh_weather" -> weatherViewModel.retry()
                            "reset_statistics" -> driveViewModel.resetAllStatistics()
                            "reset_fuel_statistics" -> driveViewModel.resetAllStatistics()
                            "reset_trip_distance" -> driveViewModel.resetTripDistance()
                            "close_gemini" -> GeminiLiveForegroundService.stop(context)
                        }
                    }
                }

                fun handleGlobalVoiceCommand(rawCommandInput: String) {
                    handsFreeCommandInProgress = true
                    val rawCommand = rawCommandInput.lowercase(java.util.Locale("tr", "TR")).trim()
                    val heyCarEnabled = voicePrefs.getBoolean("enabled", true)
                    val command = normalizeGlobalVoiceCommand(rawCommand, heyCarEnabled)
                    val nowElapsed = android.os.SystemClock.elapsedRealtime()
                    if (rawCommand.isNotBlank() && followUpUntilElapsedMs > nowElapsed && !continuousCommandMode) {
                        val followUpSeconds = voicePrefs.getInt("follow_up_seconds", 25).coerceIn(5, 120)
                        followUpUntilElapsedMs = nowElapsed + followUpSeconds * 1_000L
                    }

                    fun goDrive() {
                        navController.navigate(ROUTE_DRIVE) { launchSingleTop = true }
                    }

                    var replyDelayMs = 0L
                    fun say(text: String) {
                        replyDelayMs = (text.length * 55L).coerceIn(1_200L, 10_000L)
                        VoiceCommandSpeaker.speak(context, text)
                    }
                    fun sayHybrid(clip: RecordedVoiceClip, dynamicText: String) {
                        replyDelayMs = ((dynamicText.length * 55L) + 1_500L).coerceIn(2_000L, 10_000L)
                        CentralVoiceAlertManager.getInstance(context).playRecorded(
                            type = VoiceAlertType.COMMAND,
                            priority = VoiceAlertPriority.NORMAL,
                            clip = clip,
                            cooldownKey = "voice_hybrid_${clip.name}_${System.nanoTime()}",
                            cooldownMs = 0L,
                            dynamicTextAfter = dynamicText
                        )
                    }
                    val driveNow = driveViewModel.uiState.value
                    val weatherNow = weatherViewModel.uiState.value

                    when {
                        command.isBlank() -> Unit

                        command == "__wake__" -> {
                            android.widget.Toast.makeText(
                                context,
                                "Hey Car hazır • komutu söyleyin",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }

                        pendingVoiceConfirmation != null && (command == "evet" || command.contains("onaylıyorum") || command.contains("onayliyorum")) -> {
                            when (pendingVoiceConfirmation) {
                                "RESET_STATISTICS" -> {
                                    driveViewModel.resetAllStatistics {
                                        say("Yakıt ve sürüş istatistikleri tamamen sıfırlandı.")
                                    }
                                }
                            }
                            pendingVoiceConfirmation = null
                        }

                        pendingVoiceConfirmation != null && (command == "hayır" || command == "hayir" || command.contains("iptal")) -> {
                            pendingVoiceConfirmation = null
                            say("İşlem iptal edildi.")
                        }

                        command.contains("istatistik") && (command.contains("sıfırla") || command.contains("sifirla") || command.contains("sil")) -> {
                            if (driveNow.speedKmh >= 5) {
                                say("İstatistik silme işlemi yalnız araç dururken kullanılabilir.")
                            } else {
                                pendingVoiceConfirmation = "RESET_STATISTICS"
                                say("Tüm sürüş istatistiklerini silmemi onaylıyor musunuz?")
                            }
                        }

                        command.contains("hava") && (command.contains("nasıl") || command.contains("nasil") || command.contains("özet") || command.contains("ozet") || command.contains("kaç derece") || command.contains("kac derece")) -> {
                            if (!weatherNow.hasData || weatherNow.temperatureCelsius == null) {
                                say("Güncel hava durumu henüz alınamadı.")
                            } else {
                                val city = weatherNow.cityName?.let { "$it için " }.orEmpty()
                                val feels = weatherNow.apparentTemperatureCelsius?.let { ", hissedilen ${it.roundToInt()} derece" }.orEmpty()
                                val humidity = weatherNow.relativeHumidityPercent?.let { ", nem yüzde $it" }.orEmpty()
                                val wind = weatherNow.windSpeedKmh?.let { ", rüzgar saatte ${it.roundToInt()} kilometre" }.orEmpty()
                                say("$city hava ${weatherConditionText(weatherNow.condition)}. Sıcaklık ${weatherNow.temperatureCelsius.roundToInt()} derece$feels$humidity$wind.")
                            }
                        }

                        command.contains("ses") && (command.contains("kapat") || command.contains("sessiz")) -> {
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
                            say("Medya sesi kapatıldı.")
                        }

                        command.contains("ses") && (command.contains("aç") || command.contains("ac")) -> {
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
                            say("Medya sesi açıldı.")
                        }

                        command.contains("ses") && (command.contains("artır") || command.contains("arttir") || command.contains("yükselt") || command.contains("yukselt")) -> {
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                        }

                        command.contains("ses") && (command.contains("azalt") || command.contains("kıs") || command.contains("kis")) -> {
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                        }

                        command.contains("ses") && Regex("(?:yüzde|%|ses)\\s*(\\d{1,3})").containsMatchIn(command) -> {
                            val percent = Regex("(?:yüzde|%|ses)\\s*(\\d{1,3})").find(command)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 50
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * percent / 100f).roundToInt(), android.media.AudioManager.FLAG_SHOW_UI)
                            say("Medya sesi yüzde $percent olarak ayarlandı.")
                        }

                        command.contains("sonraki") && (command.contains("şarkı") || command.contains("sarki") || command.contains("müzik") || command.contains("muzik")) -> MediaAppController.nextMedia(context)
                        command.contains("önceki") && (command.contains("şarkı") || command.contains("sarki") || command.contains("müzik") || command.contains("muzik")) -> MediaAppController.previousMedia(context)
                        (command.contains("müzik") || command.contains("muzik")) && (command.contains("devam") || command.contains("oynat")) -> MediaAppController.resumeMedia(context)

                        command.contains("kaç kilometre") || command.contains("kac kilometre") || command.contains("gün km") || command.contains("gun km") ->
                            sayHybrid(
                                RecordedVoiceClip.DAILY_DISTANCE_INTRO,
                                "${String.format(java.util.Locale("tr", "TR"), "%.1f", driveNow.todayTotalDistanceKm)} kilometre."
                            )

                        command.contains("ne kadar yaktım") || command.contains("ne kadar yaktim") || command.contains("yakıt harcama") || command.contains("yakit harcama") ->
                            sayHybrid(
                                RecordedVoiceClip.FUEL_CONSUMPTION_INTRO,
                                "${String.format(java.util.Locale("tr", "TR"), "%.2f", driveNow.tripEstimatedFuelConsumedLiters)} litre. Tahmini maliyet ${String.format(java.util.Locale("tr", "TR"), "%.2f", driveNow.tripEstimatedFuelCost)} lira."
                            )

                        command.contains("yakıt maliyet") || command.contains("yakit maliyet") || command.contains("kaç lira yaktım") || command.contains("kac lira yaktim") ->
                            sayHybrid(
                                RecordedVoiceClip.FUEL_COST_INTRO,
                                "${String.format(java.util.Locale("tr", "TR"), "%.2f", driveNow.tripEstimatedFuelCost)} lira."
                            )

                        command.contains("yakıt yüzde") || command.contains("yakit yuzde") || command.contains("menzil kaç") || command.contains("menzil kac") -> {
                            val fuel = driveNow.fuelPercentage?.roundToInt()
                            val range = driveNow.estimatedFuelRangeKm?.roundToInt()
                            say(if (fuel == null) "Yakıt bilgisi henüz ayarlanmadı." else "Yakıt yüzde $fuel. Tahmini menzil ${range ?: 0} kilometre.")
                        }

                        command.contains("ortalama hız") || command.contains("ortalama hiz") -> {
                            val average = if (driveNow.isSpeedCorridorActive) driveNow.speedCorridorAverageSpeedKmh else driveNow.tripAverageSpeedKmh
                            sayHybrid(RecordedVoiceClip.AVERAGE_SPEED_INTRO, "saatte ${average.roundToInt()} kilometre.")
                        }

                        command.contains("rakım") || command.contains("rakim") || command.contains("yükseklik kaç") || command.contains("yukseklik kac") ->
                            driveNow.altitudeMeters?.let {
                                sayHybrid(RecordedVoiceClip.ALTITUDE_INTRO, "$it metre.")
                            } ?: say("Rakım bilgisi henüz alınamadı.")

                        command.contains("hızım kaç") || command.contains("hizim kac") || command.contains("kaçla gidiyorum") || command.contains("kacla gidiyorum") ->
                            sayHybrid(RecordedVoiceClip.CURRENT_SPEED_INTRO, "saatte ${driveNow.speedKmh} kilometre.")

                        command.contains("park sürem") || command.contains("park surem") || command.contains("park süresi") || command.contains("park suresi") ->
                            sayHybrid(
                                RecordedVoiceClip.PARK_DURATION_INTRO,
                                "${driveNow.parkDurationSeconds / 60} dakika ${driveNow.parkDurationSeconds % 60} saniye."
                            )

                        command.contains("motor sıcaklığı") || command.contains("motor sicakligi") || command.contains("su sıcaklığı") || command.contains("su sicakligi") ->
                            obdUiState.liveData.coolantCelsius?.let {
                                sayHybrid(RecordedVoiceClip.ENGINE_TEMPERATURE_INTRO, "$it derece.")
                            } ?: say("Motor suyu sıcaklığı henüz alınamadı.")

                        command.contains("akü voltaj") || command.contains("aku voltaj") || command.contains("akü kaç") || command.contains("aku kac") ->
                            (obdUiState.liveData.batteryVoltage ?: obdUiState.liveData.controlModuleVoltage)?.let {
                                sayHybrid(
                                    RecordedVoiceClip.BATTERY_VOLTAGE_INTRO,
                                    "${String.format(java.util.Locale("tr", "TR"), "%.1f", it)} volt."
                                )
                            } ?: say("Akü voltajı henüz alınamadı.")

                        command.contains("sıradaki namaz") || command.contains("siradaki namaz") || command.contains("sonraki namaz") ->
                            if (prayerUiState.nextPrayerName == "--") {
                                say("Namaz vakti henüz alınamadı.")
                            } else {
                                sayHybrid(
                                    RecordedVoiceClip.NEXT_PRAYER_INTRO,
                                    "${prayerUiState.nextPrayerName}, saat ${prayerUiState.nextPrayerTime}. ${prayerUiState.remainingText}."
                                )
                            }

                        command.contains("trafik yoğunluğu") || command.contains("trafik yogunlugu") ->
                            sayHybrid(
                                RecordedVoiceClip.TRAFFIC_DENSITY_INTRO,
                                "10 üzerinden ${driveNow.trafficSeverity.coerceIn(0, 10)}. ${driveNow.trafficIncidentCount} olay bulunuyor."
                            )

                        command.contains("trafik ajan") && command.contains("hedef") -> {
                            trafficVoiceCommand = command
                            trafficVoiceCommandRequestId += 1
                            navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                        }

                        command.contains("dinlemeyi kapat") || command.contains("eller serbest kapat") || command.contains("mikrofonu kapat") -> {
                            voicePrefs.edit().putString("microphone_mode", "OFF").putBoolean("hands_free_enabled", false).apply()
                            followUpUntilElapsedMs = 0L
                            say("Eller serbest dinleme kapatıldı.")
                        }

                        command.contains("dinlemeyi aç") || command.contains("dinlemeyi ac") || command.contains("eller serbest aç") || command.contains("eller serbest ac") -> {
                            voicePrefs.edit().putString("microphone_mode", "AUTO").putBoolean("hands_free_enabled", false).apply()
                            continuousCommandMode = true
                            say("Eller serbest dinleme açıldı.")
                        }

                        command.contains("kesintisiz komut") && (command.contains("kapat") || command.contains("bitir") || command.contains("durdur")) -> {
                            continuousCommandMode = false
                            followUpUntilElapsedMs = 0L
                            say("Kesintisiz komut modu kapatıldı.")
                        }

                        command.contains("kesintisiz komut") && (command.contains("aç") || command.contains("ac") || command.contains("başlat") || command.contains("baslat")) -> {
                            if (!voicePrefs.getBoolean("continuous_command_enabled", true)) {
                                say("Kesintisiz komut modu ayarlardan kapatılmış.")
                            } else {
                                val minutes = voicePrefs.getInt("continuous_command_minutes", 30).coerceIn(5, 60)
                                continuousCommandMode = true
                                followUpUntilElapsedMs = android.os.SystemClock.elapsedRealtime() + minutes * 60L * 1_000L
                                say("Kesintisiz komut modu $minutes dakika için açıldı. Artık Hey Car demeden komut verebilirsiniz.")
                            }
                        }

                        (command.contains("mehmet") && (command.contains("seç") || command.contains("sec"))) -> {
                            if (driveNow.speedKmh >= 5) say("Sürücü seçimi yalnız araç dururken değiştirilebilir.")
                            else drivers.firstOrNull { it.id == "1" }?.let { selectDriver(it) }
                        }

                        (command.contains("nurdan") && (command.contains("seç") || command.contains("sec"))) -> {
                            if (driveNow.speedKmh >= 5) say("Sürücü seçimi yalnız araç dururken değiştirilebilir.")
                            else drivers.firstOrNull { it.id == "2" }?.let { selectDriver(it) }
                        }

                        command.contains("takip süresi") || command.contains("takip suresi") -> {
                            val seconds = Regex("(10|15|25|30|45|60)").find(command)?.value?.toIntOrNull()
                            if (seconds == null) say("Takip süresi 10, 15, 25, 30, 45 veya 60 saniye olabilir.")
                            else { voicePrefs.edit().putInt("follow_up_seconds", seconds).apply(); say("Takip süresi $seconds saniye olarak ayarlandı.") }
                        }

                        command.contains("gündüz modu") || command.contains("gunduz modu") -> appearanceScope.launch { appearanceRepository.setDayNightMode("GUNDUZ"); say("Gündüz modu açıldı.") }
                        command.contains("gece modu") -> appearanceScope.launch { appearanceRepository.setDayNightMode("GECE"); say("Gece modu açıldı.") }
                        command.contains("otomatik görünüm") || command.contains("otomatik gorunum") -> appearanceScope.launch { appearanceRepository.setDayNightMode("OTOMATIK"); say("Otomatik görünüm açıldı.") }

                        command.contains("parlaklık") || command.contains("parlaklik") -> {
                            val attrs = window.attributes
                            attrs.screenBrightness = when {
                                command.contains("artır") || command.contains("arttir") -> (attrs.screenBrightness.takeIf { it >= 0f } ?: .6f).plus(.1f).coerceAtMost(1f)
                                command.contains("azalt") || command.contains("kıs") || command.contains("kis") -> (attrs.screenBrightness.takeIf { it >= 0f } ?: .6f).minus(.1f).coerceAtLeast(.08f)
                                else -> attrs.screenBrightness
                            }
                            window.attributes = attrs
                        }

                        command.contains("youtube") -> launchYouTube(context)
                        command.contains("gemini") -> {
                            GeminiLiveForegroundService.start(context)
                        }
                        command.contains("chatgpt") || command.contains("chat gpt") -> {
                            continuousCommandMode = false; followUpUntilElapsedMs = 0L
                            voicePrefs.edit().putString("microphone_mode", "OFF").putBoolean("hands_free_enabled", false).apply()
                            launchNamedApp(context, listOf("com.openai.chatgpt"))
                        }
                        command.contains("chrome") || command.contains("tarayıcı") || command.contains("tarayici") -> launchNamedApp(context, listOf("com.android.chrome"))

                        command == "geri" || command.contains("geri dön") || command.contains("geri don") -> safeBack()

                        command.contains("ana ekran") || command.contains("ana sayfa") || command == "dashboard" -> goDrive()

                        command.contains("eve git") || command == "ev" -> {
                            if (!launchSavedHome(context)) {
                                navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                            }
                            Unit
                        }

                        command.contains("işe git") || command.contains("ise git") || command == "iş" -> {
                            if (!launchSavedWork(context)) {
                                navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                            }
                            Unit
                        }

                        Regex("^(.+?)(?:'?(?:ya|ye|a|e))?\\s+(?:git|gidelim|götür|gotur)$").matches(command) -> {
                            val destination = Regex("^(.+?)(?:'?(?:ya|ye|a|e))?\\s+(?:git|gidelim|götür|gotur)$")
                                .find(command)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                            if (destination.isNotBlank()) launchNavigationTo(context, destination)
                        }

                        command.contains("radyo") -> {
                            if (command.contains("kapat") || command.contains("dur") || command.contains("sus")) {
                                MediaAppController.stopRadio(context)
                            } else {
                                MediaAppController.openRadio(context)
                            }
                        }

                        command.contains("müzik") || command.contains("muzik") -> {
                            if (command.contains("kapat") || command.contains("dur") || command.contains("sus")) {
                                MediaAppController.stopMedia(context)
                            } else {
                                MediaAppController.openMusic(context)
                            }
                        }

                        command.contains("wifi") || command.contains("wi-fi") -> {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
                        }

                        command.contains("bluetooth") -> {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }

                        command.contains("harita") || command.contains("navigasyon") ->
                            navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }

                        command.contains("hava") ->
                            navController.navigate(ROUTE_WEATHER_DETAILS) { launchSingleTop = true }

                        command.contains("namaz") || command.contains("dua") || command.contains("kıble") || command.contains("kible") ->
                            navController.navigate(ROUTE_PRAYER) { launchSingleTop = true }

                        command.contains("yakıt") || command.contains("yakit") ->
                            navController.navigate(ROUTE_FUEL) { launchSingleTop = true }

                        command.contains("istatistik") ->
                            navController.navigate(ROUTE_STATISTICS) { launchSingleTop = true }

                        command.contains("görünüm") || command.contains("gorunum") || command.contains("tema") ->
                            navController.navigate(ROUTE_APPEARANCE_SETTINGS) { launchSingleTop = true }

                        command.contains("üst bar") || command.contains("ust bar") || command.contains("ikon düzen") || command.contains("ikon duzen") ->
                            navController.navigate(ROUTE_HEADER_LAYOUT_SETTINGS) { launchSingleTop = true }

                        command.contains("araç sistem") || command.contains("arac sistem") ->
                            navController.navigate(ROUTE_VEHICLE_SYSTEMS) { launchSingleTop = true }

                        command.contains("araç seç") || command.contains("arac sec") || command.contains("araç deposu") || command.contains("arac deposu") ->
                            navController.navigate(ROUTE_VEHICLE_SELECTION) { launchSingleTop = true }

                        command.contains("sedan") -> { driveViewModel.setSelectedVehicle("sedan_white"); goDrive() }
                        command.contains("hatchback") -> { driveViewModel.setSelectedVehicle("hatchback_white"); goDrive() }
                        command.contains("suv") -> { driveViewModel.setSelectedVehicle("suv_white"); goDrive() }
                        command.contains("coupe") || command.contains("kupe") -> { driveViewModel.setSelectedVehicle("coupe_white"); goDrive() }
                        command.contains("station wagon") || command.contains("station") -> { driveViewModel.setSelectedVehicle("station_wagon_white"); goDrive() }
                        command.contains("cabrio") || command.contains("kabrio") -> { driveViewModel.setSelectedVehicle("cabrio_white"); goDrive() }
                        command.contains("mini van") || command.contains("minivan") -> { driveViewModel.setSelectedVehicle("mini_van_white"); goDrive() }
                        command.contains("panelvan") || command.contains("panel van") -> { driveViewModel.setSelectedVehicle("panelvan_white"); goDrive() }
                        command.contains("pickup") || command.contains("pikap") -> { driveViewModel.setSelectedVehicle("pickup_white"); goDrive() }
                        command.contains("micro") || command.contains("mikro") -> { driveViewModel.setSelectedVehicle("micro_white"); goDrive() }
                        command.contains("camper") -> { driveViewModel.setSelectedVehicle("camper_van_white"); goDrive() }
                        command.contains("truck") || command.contains("kamyon") -> { driveViewModel.setSelectedVehicle("truck_white"); goDrive() }
                        command.contains("çekme karavan") || command.contains("cekme karavan") -> { driveViewModel.setSelectedVehicle("trailer_caravan_white"); goDrive() }

                        command.contains("trafik") || command.contains("ajan") -> {
                            val onlyAgentScreen = command == "trafik" || command == "ajan" ||
                                command == "trafik ajanı" || command == "trafik ajani"
                            if (!onlyAgentScreen) {
                                trafficVoiceCommand = command
                                trafficVoiceCommandRequestId += 1
                            }
                            navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                        }

                        command.contains("rota") -> {
                            goDrive()
                            if (!driveViewModel.uiState.value.isRouteSlopePanelVisible) driveViewModel.toggleRouteSlopePanel()
                            Unit
                        }

                        command.contains("canlı") || command.contains("canli") || command.contains("live") -> {
                            goDrive()
                            if (!driveViewModel.uiState.value.isLiveSlopePanelVisible) driveViewModel.toggleLiveSlopePanel()
                            Unit
                        }

                        command.contains("pusula") -> {
                            goDrive()
                            driveViewModel.toggleCompassPanel()
                        }

                        command.contains("saat") ->
                            navController.navigate(ROUTE_ANALOG_CLOCK) { launchSingleTop = true }

                        command.contains("ayar") ->
                            navController.navigate(ROUTE_PROGRAM_SETTINGS) { launchSingleTop = true }

                        command.contains("obd") ->
                            navController.navigate(ROUTE_OBD) { launchSingleTop = true }

                        command.contains("yolculuğu başlat") || command.contains("yolculugu baslat") || command.contains("seyahati başlat") || command.contains("seyahati baslat") -> {
                            driveViewModel.startTrip()
                            goDrive()
                        }

                        command.contains("yolculuğu durdur") || command.contains("yolculugu durdur") || command.contains("seyahati durdur") -> {
                            driveViewModel.stopTrip()
                            goDrive()
                        }

                        command.contains("koridor") -> driveViewModel.toggleSpeedCorridor()

                        command.contains("karavan") -> driveViewModel.setVehicleMode("Karavan")
                        command.contains("otomobil") || command.contains("araba") -> driveViewModel.setVehicleMode("Otomobil")

                        currentRoute == ROUTE_TRAFFIC_AGENT -> {
                            trafficVoiceCommand = command
                            trafficVoiceCommandRequestId += 1
                        }

                        command.contains("yardım") || command.contains("yardim") || command.contains("komutlar") -> {
                            android.widget.Toast.makeText(
                                context,
                                "Hey Car: ana ekran, geri, navigasyon, hava, namaz, yakıt, trafik, rota, canlı, OBD, koridor, radyo, müzik, yolculuk ve araç tipleri.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            say("Hava durumu, trafik hedefi, navigasyon, ses, medya, yakıt, sürüş bilgileri, görünüm, sürücü ve araç komutlarını kullanabilirsiniz.")
                        }

                        else -> {
                            replyDelayMs = 2_500L
                            CentralVoiceAlertManager.getInstance(context).playRecorded(
                                type = VoiceAlertType.COMMAND,
                                priority = VoiceAlertPriority.NORMAL,
                                clip = RecordedVoiceClip.COMMAND_NOT_UNDERSTOOD,
                                cooldownKey = "command_not_understood_${System.nanoTime()}",
                                cooldownMs = 0L
                            )
                        }
                    }

                    if (continuousCommandMode) {
                        followUpResumeDelayMs = replyDelayMs
                        followUpLaunchRequestId += 1
                    } else if (followUpUntilElapsedMs > android.os.SystemClock.elapsedRealtime()) {
                        followUpResumeDelayMs = replyDelayMs
                        followUpUntilElapsedMs += replyDelayMs
                        followUpLaunchRequestId += 1
                    } else {
                        followUpUntilElapsedMs = 0L
                        continuousCommandMode = false
                        handsFreeCommandInProgress = false
                        voiceRecognizerGeneration += 1
                    }
                }

                val googleSpeechLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val phrase = result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                    if (!phrase.isNullOrBlank()) handleGlobalVoiceCommand(phrase)
                }

                DisposableEffect(
                    microphoneMode,
                    handsFreeEnabled,
                    hasAudioPermission,
                    handsFreeCommandInProgress,
                    followUpUntilElapsedMs,
                    manualCommandListening,
                    voiceRecognizerGeneration,
                    currentRoute,
                    driverWelcomeInProgress,
                    geminiLiveState.status
                ) {
                    // 82: Arka plandaki eski sürekli ses tanıma devre dışı.
                    // Mikrofonu yalnız Gemini Live oturumu kullanır.
                    val canListen = false
                    val requireWake = false
                    val voiceController = if (canListen) {
                        HandsFreeWakeWordController(
                            context = context,
                            requireWakeWord = requireWake,
                            onReady = { manualCommandListening = true },
                            onUnavailable = { errorCode ->
                                manualCommandListening = false
                                continuousCommandMode = false
                                followUpUntilElapsedMs = 0L
                                if (microphoneMode == "AUTO") {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "ALDANMAZ DRIVE komutunu söyleyin")
                                    }
                                    runCatching { googleSpeechLauncher.launch(intent) }
                                        .onFailure { android.widget.Toast.makeText(context, "Ses tanıma servisi kullanılamıyor ($errorCode)", android.widget.Toast.LENGTH_LONG).show() }
                                }
                            }
                        ) { recognizedCommand ->
                            manualCommandListening = false
                            android.widget.Toast.makeText(
                                context,
                                "ALGILANDI: $recognizedCommand",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            handleGlobalVoiceCommand(recognizedCommand)
                        }.also { it.start() }
                    } else null
                    onDispose { voiceController?.stop() }
                }

                LaunchedEffect(followUpLaunchRequestId) {
                    if (followUpLaunchRequestId == 0) return@LaunchedEffect
                    delay(followUpResumeDelayMs + 500L)
                    followUpResumeDelayMs = 0L
                    if (android.os.SystemClock.elapsedRealtime() >= followUpUntilElapsedMs) {
                        followUpUntilElapsedMs = 0L
                        continuousCommandMode = false
                        handsFreeCommandInProgress = false
                        voiceRecognizerGeneration += 1
                        return@LaunchedEffect
                    }
                    handsFreeCommandInProgress = false
                    voiceRecognizerGeneration += 1
                }

                val driveUiState by
                driveViewModel
                    .uiState
                    .collectAsStateWithLifecycle()

                val weatherUiState by
                weatherViewModel
                    .uiState
                    .collectAsStateWithLifecycle()

                val automaticDay = weatherUiState.hasData && weatherUiState.isDay
                val effectiveDay = when (appearance.dayNightMode) {
                    "GUNDUZ" -> true
                    "GECE" -> false
                    else -> if (weatherUiState.hasData) automaticDay else {
                        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        hour in 7..18
                    }
                }
                val sunlightActive = when (appearance.sunlightMode) {
                    "ACIK" -> true
                    "KAPALI" -> false
                    else -> effectiveDay
                }
                SideEffect {
                    DashboardPaletteRuntime.isDay = effectiveDay
                    DashboardPaletteRuntime.isSunlight = sunlightActive
                }
                val targetBrightness = when {
                    sunlightActive -> 1f
                    effectiveDay -> appearance.dayBrightness
                    else -> appearance.nightBrightness
                }
                LaunchedEffect(targetBrightness) {
                    val attrs = window.attributes
                    val start = if (attrs.screenBrightness > 0f) attrs.screenBrightness else targetBrightness
                    repeat(12) { step ->
                        attrs.screenBrightness = start + (targetBrightness - start) * ((step + 1) / 12f)
                        window.attributes = attrs
                        delay(70L)
                    }
                }

                androidx.compose.runtime.LaunchedEffect(driveUiState.latitude, driveUiState.longitude) {
                    prayerViewModel.refresh(driveUiState.latitude, driveUiState.longitude)
                }

                // 10 inç sınıfı yatay multimedya ekranlarında tüm uygulama boyunca
                // yazı/rakamları büyüt, dp tabanlı çizgi ve çerçeveleri de fiziksel olarak güçlendir.
                // Telefonlarda (Galaxy A25 gibi sw < 600dp) mevcut görünüm aynen korunur.
                val configuration = LocalConfiguration.current
                val baseDensity = LocalDensity.current
                val isLargeVehicleDisplay =
                    configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                            configuration.smallestScreenWidthDp >= 600
                val vehicleUiDensityScale = if (isLargeVehicleDisplay) 1.10f else 1.0f
                val vehicleTextScale = if (isLargeVehicleDisplay) 1.14f else 1.0f
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density * vehicleUiDensityScale,
                        fontScale = baseDensity.fontScale * appearance.textScale * vehicleTextScale
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF02070D))) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SPLASH
                    ) {

                    composable(ROUTE_SPLASH) {
                        SplashScreen(
                            onFinished = {
                                navController.navigate(ROUTE_DRIVE) {
                                    popUpTo(ROUTE_SPLASH) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(ROUTE_DRIVE) {
                        val obdVisualAlert = when {
                            !obdUiState.errorMessage.isNullOrBlank() ->
                                "OBD • ${obdUiState.errorMessage!!.take(54)}"
                            obdUiState.liveData.milOn == true ->
                                "OBD • MOTOR ARIZA LAMBASI AKTİF"
                            obdUiState.liveData.dtcCodes.isNotEmpty() ->
                                "OBD • ARIZA KODU ${obdUiState.liveData.dtcCodes.take(2).joinToString(", ")}"
                            (obdUiState.liveData.coolantCelsius ?: 0) >= 105 ->
                                "OBD • MOTOR SICAKLIĞI YÜKSEK ${obdUiState.liveData.coolantCelsius}°C"
                            (obdUiState.liveData.batteryVoltage ?: obdUiState.liveData.controlModuleVoltage)?.let { it < 11.5 || it > 15.2 } == true ->
                                "OBD • AKÜ/ŞARJ VOLTAJI UYARISI"
                            (obdUiState.liveData.engineOilCelsius ?: 0) >= 125 ->
                                "OBD • MOTOR YAĞI SICAKLIĞI YÜKSEK"
                            obdUiState.liveData.egrErrorPercent?.let { kotlin.math.abs(it) >= 20 } == true ->
                                "OBD • EGR SAPMASI YÜKSEK"
                            else -> null
                        }

                        DriveDashboardRoute(
                            viewModel = driveViewModel,
                            weatherViewModel = weatherViewModel,
                            selectedDriverName = selectedDriver?.name ?: "---",

                            onFuelClick = {
                                navController.navigate(
                                    ROUTE_FUEL
                                ) {
                                    launchSingleTop = true
                                }
                            },

                            onStatisticsClick = {
                                navController.navigate(
                                    ROUTE_STATISTICS
                                ) { launchSingleTop = true }
                            },

                            onSpeedCorridorClick = driveViewModel::toggleSpeedCorridor,

                            onPrayerClick = {
                                navController.navigate(ROUTE_PRAYER) { launchSingleTop = true }
                            },

                            isObdConnected = obdUiState.isConnected,
                            obdAlertMessage = obdVisualAlert,
                            obdShortName = obdUiState.devices
                                .firstOrNull { it.address == obdUiState.connectedAddress }
                                ?.name
                                ?.filter { it.isLetterOrDigit() }
                                ?.take(3)
                                ?.uppercase()
                                ?.ifBlank { "OBD" }
                                ?: obdUiState.connectionInfo?.adapterIdentity
                                    ?.filter { it.isLetterOrDigit() }
                                    ?.take(3)
                                    ?.uppercase()
                                    ?.ifBlank { "OBD" }
                                ?: "OBD",
                            onObdClick = {
                                navController.navigate(ROUTE_OBD) { launchSingleTop = true }
                            },

                            onTrafficAgentClick = {
                                navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true }
                            },

                            onNavigationClick = { showNavigationChooser = true },
                            onHomeClick = {
                                if (!launchSavedHome(context)) {
                                    navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                                }
                            },
                            onWorkClick = {
                                if (!launchSavedWork(context)) {
                                    navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true }
                                }
                            },
                            onClockClick = { navController.navigate(ROUTE_ANALOG_CLOCK) { launchSingleTop = true } },
                            onSettingsClick = { navController.navigate(ROUTE_PROGRAM_SETTINGS) { launchSingleTop = true } },

                            onWeatherDetailsClick = {
                                navController.navigate(
                                    ROUTE_WEATHER_DETAILS
                                ) { launchSingleTop = true }
                            },

                            onVehicleSelectionClick = {
                                navController.navigate(
                                    ROUTE_VEHICLE_SELECTION
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(ROUTE_FUEL) {
                        FuelRoute(
                            fuelDataSource = driveUiState.fuelDataSource,
                            externalFuelPercentage = driveUiState.fuelPercentage,
                            externalRemainingFuelLiters = driveUiState.remainingFuelLiters,
                            externalEstimatedRangeKm = driveUiState.estimatedFuelRangeKm,
                            externalIsLowFuel = driveUiState.isLowFuel,
                            onBack = {
                                safeBack()
                            },
                            onResetAllStatistics = driveViewModel::resetAllStatistics
                        )
                    }

                    composable(ROUTE_STATISTICS) {
                        StatisticsRoute(
                            activeTrip = if (driveUiState.isTripActive && driveUiState.tripStartedAtEpochMillis != null) {
                                TripEntity(
                                    startedAtEpochMillis = driveUiState.tripStartedAtEpochMillis ?: System.currentTimeMillis(),
                                    endedAtEpochMillis = System.currentTimeMillis(),
                                    distanceKm = driveUiState.tripDistanceKm,
                                    totalDurationSeconds = driveUiState.tripDurationSeconds,
                                    movingDurationSeconds = driveUiState.tripMovingDurationSeconds,
                                    parkDurationSeconds = driveUiState.tripParkDurationSeconds,
                                    averageSpeedKmh = driveUiState.tripAverageSpeedKmh,
                                    maxSpeedKmh = driveUiState.tripMaxSpeedKmh,
                                    vehicleMode = driveUiState.vehicleMode,
                                    vehicleId = driveUiState.selectedVehicleId,
                                    estimatedFuelConsumedLiters = driveUiState.tripEstimatedFuelConsumedLiters,
                                    estimatedFuelCost = driveUiState.tripEstimatedFuelCost,
                                    driverId = selectedDriver?.id ?: "1",
                                    driverName = selectedDriver?.name ?: "Mehmet",
                                    startLatitude = driveUiState.tripStartLatitude,
                                    startLongitude = driveUiState.tripStartLongitude,
                                    endLatitude = driveUiState.latitude,
                                    endLongitude = driveUiState.longitude,
                                    startAddress = if (driveUiState.tripStartLatitude != null && driveUiState.tripStartLongitude != null) {
                                        String.format(java.util.Locale.US, "%.5f, %.5f", driveUiState.tripStartLatitude, driveUiState.tripStartLongitude)
                                    } else "Konum bekleniyor",
                                    endAddress = if (driveUiState.latitude != null && driveUiState.longitude != null) {
                                        String.format(java.util.Locale.US, "Devam ediyor • %.5f, %.5f", driveUiState.latitude, driveUiState.longitude)
                                    } else "Devam eden sürüş • konum bekleniyor",
                                    speed0To30Seconds = driveUiState.tripSpeed0To30Seconds,
                                    speed31To50Seconds = driveUiState.tripSpeed31To50Seconds,
                                    speed51To70Seconds = driveUiState.tripSpeed51To70Seconds,
                                    speed71To90Seconds = driveUiState.tripSpeed71To90Seconds,
                                    speed91To120Seconds = driveUiState.tripSpeed91To120Seconds,
                                    speedOver120Seconds = driveUiState.tripSpeedOver120Seconds
                                )
                            } else null,
                            onResetAllStatistics = driveViewModel::resetAllStatistics,
                            onSpeedCorridorClick = { navController.navigate(ROUTE_SPEED_CORRIDOR) },
                            onBack = {
                                safeBack()
                            }
                        )
                    }


                    composable(ROUTE_WEATHER_DETAILS) {
                        WeatherDetailScreen(
                            uiState = weatherUiState,
                            onRefresh = weatherViewModel::retry,
                            onBack = { safeBack() }
                        )
                    }

                    composable(ROUTE_PRAYER) {
                        androidx.compose.runtime.LaunchedEffect(driveUiState.latitude, driveUiState.longitude) {
                            prayerViewModel.refresh(driveUiState.latitude, driveUiState.longitude)
                        }
                        PrayerScreen(
                            uiState = prayerUiState,
                            onPrayerEnabled = prayerViewModel::setPrayerEnabled,
                            onVoiceTypeEnabled = prayerViewModel::setVoiceTypeEnabled,
                            onSpeechRate = prayerViewModel::setSpeechRate,
                            onVolume = prayerViewModel::setVolume,
                            onRefresh = prayerViewModel::retry,
                            onBack = { safeBack() }
                        )
                    }

                    composable(ROUTE_OBD) {
                        ObdScreen(
                            viewModel = obdViewModel,
                            onBack = { safeBack() },
                            onWarningHistory = { navController.navigate(ROUTE_WARNING_HISTORY) { launchSingleTop = true } },
                        )
                    }

                    composable(ROUTE_WARNING_HISTORY) {
                        WarningHistoryScreen(
                            repository = warningHistoryRepository,
                            onBack = { safeBack() },
                        )
                    }

                    composable(ROUTE_SPEED_CORRIDOR) {
                        SpeedCorridorScreen(
                            uiState = driveUiState,
                            history = driveViewModel.getSpeedCorridorHistory(),
                            onBack = { safeBack() }
                        )
                    }

                    composable(ROUTE_TRAFFIC_AGENT) {
                        TrafficAgentHostScreen(
                            onBack = { safeBack() },
                            onHome = {
                                navController.navigate(ROUTE_DRIVE) {
                                    launchSingleTop = true
                                    popUpTo(ROUTE_DRIVE) { inclusive = false }
                                }
                            },
                            onSettings = {
                                navController.navigate(ROUTE_PROGRAM_SETTINGS) { launchSingleTop = true }
                            },
                            voiceCommand = trafficVoiceCommand,
                            voiceCommandRequestId = trafficVoiceCommandRequestId,
                            onVoiceCommandConsumed = { trafficVoiceCommand = "" }
                        )
                    }

                    composable(ROUTE_NAVIGATION) {
                        val safetyPrefs = remember { context.getSharedPreferences("driving_safety_settings", android.content.Context.MODE_PRIVATE) }
                        val locked = safetyPrefs.getBoolean("enabled", true) && driveUiState.speedKmh >= 5
                        NavigationScreen(onBack = { safeBack() }, isDrivingLocked = locked)
                    }

                    composable(ROUTE_ANALOG_CLOCK) {
                        AnalogClockScreen(
                            weather = weatherUiState,
                            prayer = prayerUiState,
                            speedKmh = driveUiState.speedKmh,
                            onBack = { safeBack() }
                        )
                    }

                    composable(ROUTE_PROGRAM_SETTINGS) {
                        val locked = context
                            .getSharedPreferences("driving_safety_settings", android.content.Context.MODE_PRIVATE)
                            .getBoolean("enabled", true) && driveUiState.speedKmh >= 5
                        ProgramSettingsScreen(
                            isDrivingLocked = locked,
                            onAppearance = { navController.navigate(ROUTE_APPEARANCE_SETTINGS) { launchSingleTop = true } },
                            onHeaderLayout = { navController.navigate(ROUTE_HEADER_LAYOUT_SETTINGS) { launchSingleTop = true } },
                            onVehicleSystems = { navController.navigate(ROUTE_VEHICLE_SYSTEMS) { launchSingleTop = true } },
                            onFuel = { navController.navigate(ROUTE_FUEL) { launchSingleTop = true } },
                            onStatistics = { navController.navigate(ROUTE_STATISTICS) { launchSingleTop = true } },
                            onPrayer = { navController.navigate(ROUTE_PRAYER) { launchSingleTop = true } },
                            onObd = { navController.navigate(ROUTE_OBD) { launchSingleTop = true } },
                            onNavigation = { navController.navigate(ROUTE_NAVIGATION) { launchSingleTop = true } },
                            onTrafficAgent = { navController.navigate(ROUTE_TRAFFIC_AGENT) { launchSingleTop = true } },
                            onHelp = { navController.navigate(ROUTE_HELP) { launchSingleTop = true } },
                            onBack = { safeBack() }
                        )
                    }


                    composable(ROUTE_HELP) {
                        HelpScreen(onBack = { safeBack() })
                    }

                    composable(ROUTE_APPEARANCE_SETTINGS) {
                        AppearanceSettingsScreen(
                            settings = appearance,
                            onTheme = { value -> appearanceScope.launch { appearanceRepository.setTheme(value) } },
                            onTextScale = { value -> appearanceScope.launch { appearanceRepository.setTextScale(value) } },
                            onDayNightMode = { value -> appearanceScope.launch { appearanceRepository.setDayNightMode(value) } },
                            onDayBrightness = { value -> appearanceScope.launch { appearanceRepository.setDayBrightness(value) } },
                            onNightBrightness = { value -> appearanceScope.launch { appearanceRepository.setNightBrightness(value) } },
                            onSunlightMode = { value -> appearanceScope.launch { appearanceRepository.setSunlightMode(value) } },
                            onAccentColor = { value -> appearanceScope.launch { appearanceRepository.setAccentColor(value) } },
                            onBack = { safeBack() }
                        )
                    }

                    composable(ROUTE_HEADER_LAYOUT_SETTINGS) {
                        HeaderLayoutSettingsScreen(onBack = { safeBack() })
                    }

                    composable(ROUTE_VEHICLE_SYSTEMS) {
                        val locked = context.getSharedPreferences("driving_safety_settings", android.content.Context.MODE_PRIVATE).getBoolean("enabled", true) && driveUiState.speedKmh >= 5
                        VehicleSystemSettingsScreen(
                            currentOdometerKm = driveUiState.todayTotalDistanceKm.toInt(),
                            isDriving = locked,
                            onMicrophoneModeSelected = {
                                navController.navigate(ROUTE_DRIVE) {
                                    popUpTo(ROUTE_DRIVE) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            onBack = { safeBack() }
                        )
                    }

                    composable(
                        ROUTE_VEHICLE_SELECTION
                    ) {
                        VehicleSelectionScreen(
                            selectedVehicleId =
                                driveUiState
                                    .selectedVehicleId,

                            customCarImageUri =
                                driveUiState
                                    .customCarImageUri,

                            customCaravanImageUri =
                                driveUiState
                                    .customCaravanImageUri,

                            onVehicleSelected = {
                                    vehicleId ->

                                driveViewModel
                                    .setSelectedVehicle(
                                        vehicleId
                                    )

                                safeBack()
                            },

                            onCustomImageChanged = {
                                    vehicleType,
                                    uri ->

                                driveViewModel
                                    .setCustomVehicleImageUri(
                                        vehicleType =
                                            vehicleType,
                                        uri = uri
                                    )
                            },

                            onBack = {
                                safeBack()
                            }
                        )
                    }
                    }

                    if (showNavigationChooser) {
                        AlertDialog(
                            onDismissRequest = { showNavigationChooser = false },
                            title = { Text("NAVİGASYON") },
                            text = { Text("Bir navigasyon uygulaması seçin.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showNavigationChooser = false
                                        launchNavigationApp(context, "google")
                                    }
                                ) { Text("GOOGLE") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showNavigationChooser = false
                                        launchNavigationApp(context, "yandex")
                                    }
                                ) { Text("YANDEX") }
                            }
                        )
                    }

                    if (incomingCallState == IncomingCallController.State.RINGING) {
                        IncomingCallOverlay(
                            onAnswer = { incomingCallController.answer() },
                            onReject = { incomingCallController.reject() }
                        )
                    }
                    if (currentRoute != ROUTE_SPLASH && driverSelectionEnabled && selectedDriver == null) {
                        Box(Modifier.fillMaxSize().zIndex(1000f)) {
                            DriverSelectionOverlay(
                                drivers = drivers,
                                remainingSeconds = driverSelectionSeconds,
                                // 139: Ekrandaki uyarı metni, 138'deki gerçek zaman aşımı
                                // davranışıyla eşleşsin diye 2. kişi (Nurdan) gösterilir.
                                defaultDriverName = (drivers.firstOrNull { it.id == "2" } ?: configuredDefaultDriver).name
                            ) { driver ->
                                selectDriver(driver)
                            }
                        }
                    }
                    }
                }
                }
            }
        }


    private fun normalizeGlobalVoiceCommand(rawCommand: String, heyCarEnabled: Boolean): String {
        val cleaned = rawCommand.trim().lowercase(java.util.Locale("tr", "TR"))
        if (cleaned.isBlank()) return ""
        if (!heyCarEnabled) return cleaned
        val wakeVariants = listOf("hey car", "hey kar", "heycar", "ey car", "ey kar")
        if (wakeVariants.any { cleaned == it }) return "__wake__"
        wakeVariants.forEach { wake ->
            if (cleaned.startsWith(wake + " ")) return cleaned.removePrefix(wake).trim()
        }
        return cleaned
    }

    private fun weatherConditionText(condition: com.aldanmaz.drivedashboard.data.weather.WeatherCondition): String =
        when (condition) {
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.CLEAR -> "açık"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.PARTLY_CLOUDY -> "parçalı bulutlu"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.CLOUDY -> "bulutlu"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.FOG -> "sisli"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.DRIZZLE -> "çiseli"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN -> "yağmurlu"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.FREEZING_RAIN -> "donan yağmurlu"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.SNOW -> "karlı"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN_SHOWERS -> "sağanak yağışlı"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.SNOW_SHOWERS -> "kar sağanaklı"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.THUNDERSTORM -> "gök gürültülü"
            com.aldanmaz.drivedashboard.data.weather.WeatherCondition.UNKNOWN -> "belirsiz"
        }

    private fun launchNamedApp(context: android.content.Context, packages: List<String>) {
        val launched = packages.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                runCatching {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrNull()
            }
        } == true
        if (!launched) VoiceCommandSpeaker.speak(context, "İstenen uygulama bu cihazda bulunamadı.")
    }

    private fun launchYouTube(context: android.content.Context) {
        val packages = listOf(
            "com.google.android.youtube",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.googletv"
        )
        val launchedApp = packages.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                runCatching {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrNull()
            }
        } == true
        if (launchedApp) return

        val openedWeb = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
        if (!openedWeb) VoiceCommandSpeaker.speak(context, "YouTube açılamadı.")
    }

    private fun launchNavigationApp(context: android.content.Context, provider: String): Boolean {
        val packages = if (provider.equals("yandex", ignoreCase = true)) {
            listOf("ru.yandex.yandexnavi", "ru.yandex.yandexmaps")
        } else {
            listOf("com.google.android.apps.maps")
        }
        val launched = packages.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                runCatching {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrNull()
            }
        } == true
        if (!launched) {
            android.widget.Toast.makeText(
                context,
                if (provider.equals("yandex", ignoreCase = true)) "Yandex Navigasyon bulunamadı." else "Google Maps bulunamadı.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        return launched
    }

    private fun launchNavigationTo(context: android.content.Context, destination: String) {
        val uri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(destination)}&mode=d")
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(mapsIntent); true }.getOrDefault(false)
        if (!opened) {
            val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(destination)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
                .onFailure { VoiceCommandSpeaker.speak(context, "Navigasyon uygulaması açılamadı.") }
        }
    }

    private fun launchMapSearch(context: android.content.Context, provider: String, query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val intent = if (provider.equals("yandex", ignoreCase = true)) {
            val uri = android.net.Uri.parse("yandexnavi://map_search").buildUpon()
                .appendQueryParameter("text", clean)
                .build()
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("ru.yandex.yandexnavi")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(clean)}")
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
        if (!opened && provider.equals("yandex", ignoreCase = true)) {
            val mapsUri = android.net.Uri.parse("yandexmaps://maps.yandex.com/").buildUpon()
                .appendQueryParameter("text", clean)
                .build()
            val fallback = Intent(Intent.ACTION_VIEW, mapsUri).apply {
                setPackage("ru.yandex.yandexmaps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
                .onFailure { VoiceCommandSpeaker.speak(context, "Yandex harita araması açılamadı.") }
        } else if (!opened) {
            VoiceCommandSpeaker.speak(context, "Google Maps araması açılamadı.")
        }
    }

    private fun launchMapRoute(context: android.content.Context, provider: String, destination: String) {
        val clean = destination.trim()
        if (clean.isBlank()) return
        if (!provider.equals("yandex", ignoreCase = true)) {
            launchNavigationTo(context, clean)
            return
        }

        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            launchMapSearch(context, "yandex", clean)
            return
        }

        val locationClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(context)
        val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
        locationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            tokenSource.token,
        ).addOnSuccessListener { location ->
            if (location == null) {
                launchMapSearch(context, "yandex", clean)
                return@addOnSuccessListener
            }
            com.aldanmaz.drivedashboard.trafficagent.TomTomSearchClient.hedefAra(
                aramaMetni = clean,
                mevcutEnlem = location.latitude,
                mevcutBoylam = location.longitude,
                basarili = { results ->
                    val target = results.firstOrNull()
                    if (target == null) {
                        launchMapSearch(context, "yandex", clean)
                    } else {
                        val uri = android.net.Uri.parse("yandexnavi://build_route_on_map").buildUpon()
                            .appendQueryParameter("lat_to", target.enlem.toString())
                            .appendQueryParameter("lon_to", target.boylam.toString())
                            .build()
                        val yandexIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("ru.yandex.yandexnavi")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val opened = runCatching { context.startActivity(yandexIntent); true }.getOrDefault(false)
                        if (!opened) launchMapSearch(context, "yandex", clean)
                    }
                },
                hata = { launchMapSearch(context, "yandex", clean) },
            )
        }.addOnFailureListener {
            launchMapSearch(context, "yandex", clean)
        }
    }

    private fun bringAldanmazToFront(context: android.content.Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * 94: Gemini'nin uygulama içindeki temel aç/kapat ayarlarını tek bir güvenli
     * eşleme üzerinden değiştirmesi için kullanılır. value örneği: driver_selection:on
     */
    private fun applyGeminiProgramToggle(
        context: android.content.Context,
        rawValue: String?,
        prayerViewModel: PrayerViewModel,
    ) {
        val raw = rawValue?.trim().orEmpty()
        if (raw.isBlank()) return
        val split = raw.split(Regex("[:=|]"), limit = 2)
        val key = split.getOrNull(0).orEmpty().trim()
            .lowercase(java.util.Locale("tr", "TR"))
            .replace(' ', '_')
        val stateText = split.getOrNull(1).orEmpty().trim()
            .lowercase(java.util.Locale("tr", "TR"))
        val enabled = when (stateText) {
            "on", "true", "1", "aç", "ac", "açık", "acik", "aktif", "etkin" -> true
            "off", "false", "0", "kapat", "kapalı", "kapali", "pasif", "devre_dışı", "devre_disi" -> false
            else -> return
        }

        when (key) {
            "driver_selection", "surucu_secimi", "sürücü_seçimi" ->
                context.getSharedPreferences("driver_profiles", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("selection_enabled", enabled).apply()
            "safety_reminder", "guvenlik_hatirlatmasi", "güvenlik_hatırlatması" ->
                context.getSharedPreferences("drive_start_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("safety_enabled", enabled).apply()
            "trip_prayer", "yolculuk_duasi", "yolculuk_duası" ->
                context.getSharedPreferences("drive_start_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("trip_prayer_enabled", enabled).apply()
            "rest_reminder", "dinlenme_hatirlatici", "dinlenme_hatırlatıcı" ->
                context.getSharedPreferences("drive_rest_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", enabled).apply()
            "night_stars", "gece_yildizlari", "gece_yıldızları" ->
                context.getSharedPreferences("night_visual_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("stars_enabled", enabled).apply()
            "safety_lock", "surus_guvenlik_kilidi", "sürüş_güvenlik_kilidi" ->
                context.getSharedPreferences("driving_safety_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", enabled).apply()
            "road_sign_test", "trafik_levha_test" ->
                context.getSharedPreferences("road_sign_test_mode", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", enabled).apply()
            "traffic_voice", "trafik_sesi", "trafik_tts_mp3" ->
                AjanAyarlari.sesliUyariyiAyarla(context, enabled)
            "prayer_sabah", "namaz_sabah" -> prayerViewModel.setPrayerEnabled("Sabah", enabled)
            "prayer_ogle", "namaz_ogle", "namaz_öğle" -> prayerViewModel.setPrayerEnabled("Öğle", enabled)
            "prayer_ikindi", "namaz_ikindi" -> prayerViewModel.setPrayerEnabled("İkindi", enabled)
            "prayer_aksam", "namaz_aksam", "namaz_akşam" -> prayerViewModel.setPrayerEnabled("Akşam", enabled)
            "prayer_yatsi", "namaz_yatsi", "namaz_yatsı" -> prayerViewModel.setPrayerEnabled("Yatsı", enabled)
            else -> android.widget.Toast.makeText(context, "Bu ayar henüz sesli değiştirilemiyor: $key", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 94: Kaydırıcı/sayısal program ayarları. value örneği: rest_minutes:120
     */
    private fun applyGeminiProgramValue(
        context: android.content.Context,
        rawValue: String?,
        prayerViewModel: PrayerViewModel,
    ) {
        val raw = rawValue?.trim().orEmpty()
        val split = raw.split(Regex("[:=|]"), limit = 2)
        if (split.size < 2) return
        val key = split[0].trim().lowercase(java.util.Locale("tr", "TR")).replace(' ', '_')
        val number = split[1].replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull() ?: return
        when (key) {
            "rest_minutes", "dinlenme_uyari_dakika" -> context.getSharedPreferences("drive_rest_settings", android.content.Context.MODE_PRIVATE)
                .edit().putInt("reminder_minutes", number.roundToInt().coerceIn(60, 240)).apply()
            "break_minutes", "mola_dakika" -> context.getSharedPreferences("drive_rest_settings", android.content.Context.MODE_PRIVATE)
                .edit().putInt("real_break_minutes", number.roundToInt().coerceIn(10, 30)).apply()
            "speed_offset", "speed_offset_kmh", "hiz_duzeltme", "hız_düzeltme" -> context.getSharedPreferences("drive_calibration", android.content.Context.MODE_PRIVATE)
                .edit().putInt("speed_offset_kmh", number.roundToInt().coerceIn(-10, 10)).apply()
            "altitude_offset", "altitude_offset_m", "rakim_duzeltme", "rakım_düzeltme" -> context.getSharedPreferences("drive_calibration", android.content.Context.MODE_PRIVATE)
                .edit().putInt("altitude_offset_m", number.roundToInt().coerceIn(-100, 100)).apply()
            "prayer_volume", "namaz_ses" -> prayerViewModel.setVolume((if (number > 1f) number / 100f else number).coerceIn(0f, 1f))
            "prayer_speech_rate", "namaz_konusma_hizi" -> prayerViewModel.setSpeechRate((if (number > 3f) number / 100f else number).coerceIn(.5f, 1.5f))
            else -> android.widget.Toast.makeText(context, "Bu değer henüz sesli değiştirilemiyor: $key", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun deviceHasTelephony(context: android.content.Context): Boolean {
        val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.phoneType != null && tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    @Suppress("DEPRECATION")
    private fun resolveCurrentAddress(
        context: android.content.Context,
        latitude: Double,
        longitude: Double,
    ): String? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        val address = Geocoder(context, java.util.Locale("tr", "TR"))
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?: return@runCatching null
        address.getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                address.featureName,
                address.thoroughfare,
                address.subLocality,
                address.locality,
                address.adminArea,
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun buildSevereWeatherAlert(
        weather: com.aldanmaz.drivedashboard.ui.screen.weather.WeatherUiState,
    ): String? {
        if (!weather.hasData) return null
        val city = weather.cityName?.takeIf { it.isNotBlank() } ?: "bulunduğunuz bölge"
        val apparent = weather.apparentTemperatureCelsius ?: weather.temperatureCelsius
        val gust = weather.windGustKmh ?: weather.windSpeedKmh
        val localNow = java.time.LocalDateTime.now().minusMinutes(30)
        val nextHours = weather.hourlyForecast
            .filter { hour ->
                runCatching { java.time.LocalDateTime.parse(hour.time) }
                    .getOrNull()
                    ?.let { !it.isBefore(localNow) }
                    ?: true
            }
            .take(4)
            .ifEmpty { weather.hourlyForecast.take(4) }
        val maxRainProbability = nextHours.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull() ?: 0
        val hasThunder = weather.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.THUNDERSTORM ||
            nextHours.any { it.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.THUNDERSTORM }
        val heavyRain = maxRainProbability >= 80 && (
            weather.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN ||
                weather.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN_SHOWERS ||
                nextHours.any { it.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN || it.condition == com.aldanmaz.drivedashboard.data.weather.WeatherCondition.RAIN_SHOWERS }
            )
        return when {
            hasThunder && maxRainProbability >= 70 -> "$city için gök gürültülü kuvvetli yağış, fırtına ve yerel dolu riski var."
            (gust ?: 0.0) >= 70.0 -> "$city için çok kuvvetli rüzgar veya fırtına riski var. Rüzgar hamlesi yaklaşık ${gust?.roundToInt()} kilometre/saat."
            heavyRain -> "$city için şiddetli yağmur veya sağanak riski yüksek. Yağış olasılığı yaklaşık yüzde $maxRainProbability."
            (apparent ?: -99.0) >= 40.0 -> "$city için aşırı sıcak uyarısı var. Hissedilen sıcaklık yaklaşık ${apparent?.roundToInt()} derece."
            else -> null
        }
    }

    private fun launchEmailNote(context: android.content.Context, payload: String) {
        val parts = payload.split("|||", limit = 3)
        val recipient = parts.getOrNull(0)?.trim().orEmpty()
        val subject = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "ALD Drive notu" }
        val body = parts.getOrNull(2)?.trim().orEmpty().ifBlank { payload }
        val uri = if (recipient.isBlank()) android.net.Uri.parse("mailto:")
        else android.net.Uri.parse("mailto:${android.net.Uri.encode(recipient)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { android.widget.Toast.makeText(context, "E-posta uygulaması açılamadı.", android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun launchYouTubeSearch(context: android.content.Context, query: String) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { android.widget.Toast.makeText(context, "Medya araması açılamadı.", android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun launchEmergencyAssist(context: android.content.Context, address: String?) {
        val text = address?.let { "Acil durum • Konum: $it" } ?: "Acil durum • Konum bilgisi hazır değil"
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        val dial = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(dial) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 94: Rotation Activity/Live oturumunu yeniden oluşturmaz. Sistem çubukları
        // bazı araç tabletlerinde dönüşte tekrar göründüğü için yeniden gizle.
        hideSystemBars()
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat
            .setDecorFitsSystemWindows(
                window,
                false
            )

        WindowCompat
            .getInsetsController(
                window,
                window.decorView
            )
            .apply {
                hide(WindowInsetsCompat.Type.systemBars())

                systemBarsBehavior =
                    WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
    }

    private companion object {
        const val ROUTE_SPLASH =
            "splash"

        const val ROUTE_DRIVE =
            "drive"

        const val ROUTE_FUEL =
            "fuel"

        const val ROUTE_STATISTICS =
            "statistics"

        const val ROUTE_SPEED_CORRIDOR =
            "speed_corridor"

        const val ROUTE_PRAYER =
            "prayer"

        const val ROUTE_WEATHER_DETAILS =
            "weather_details"

        const val ROUTE_OBD =
            "obd"

        const val ROUTE_WARNING_HISTORY =
            "warning_history"

        const val ROUTE_TRAFFIC_AGENT =
            "traffic_agent"

        const val ROUTE_NAVIGATION =
            "navigation"

        const val ROUTE_ANALOG_CLOCK =
            "analog_clock"

        const val ROUTE_PROGRAM_SETTINGS =
            "program_settings"

        const val ROUTE_APPEARANCE_SETTINGS =
            "appearance_settings"

        const val ROUTE_HEADER_LAYOUT_SETTINGS =
            "header_layout_settings"

        const val ROUTE_VEHICLE_SYSTEMS =
            "vehicle_systems"

        const val ROUTE_VEHICLE_SELECTION =
            "vehicle_selection"

        const val ROUTE_HELP =
            "help"

    }
}
