package com.aldanmaz.drivedashboard.ui.screen.drive

import com.aldanmaz.drivedashboard.BuildConfig
import com.aldanmaz.drivedashboard.R

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val SlopeBg = Color(0xFF02070D)
private val SlopePanel = Color(0xFF07111C)
private val SlopeGrid = Color(0xFF314152)
private val SlopeYellow = Color(0xFFFFD51A)
private val SlopeGreen = Color(0xFF38E65B)
private val SlopeCyan = Color(0xFF3DDCFF)
private val SlopeText = Color(0xFFE8EEF5)

data class ElevationSample(
    val distanceKm: Double,
    val elevationM: Double,
    val slopePct: Double = 0.0
)

data class RouteProfileResult(
    val destinationName: String,
    val points: List<ElevationSample>,
    val totalDistanceKm: Double,
    val totalClimbM: Int,
    val etaMinutes: Int,
    val maxSlopePct: Double,
    val maxElevationM: Double,
    val originName: String = "Başlangıç",
    val waypointName: String? = null,
    val waypointDistanceKm: Double? = null,
    val destinationDistanceKm: Double = totalDistanceKm,
    // 136: GPS konumu rotadaki en yakın örnek noktayla eşleştirilir.
    // Araç böylece başlangıçta sabit kalmaz; gerçek sürüş ilerledikçe profil üzerinde ilerler.
    val routeCoordinates: List<Pair<Double, Double>> = emptyList()
) {
    val routeTitle: String
        get() = buildString {
            append(shortPlaceName(originName))
            if (!waypointName.isNullOrBlank()) {
                append(" → ")
                append(shortPlaceName(waypointName))
            }
            append(" → ")
            append(shortPlaceName(destinationName))
        }
}

@Composable
fun RoutePlannerScreen(
    currentLat: Double?,
    currentLon: Double?,
    isCaravan: Boolean,
    onBack: () -> Unit,
    onShowOnDashboard: (RouteProfileResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routePrefs = remember(context) {
        context.getSharedPreferences("aldanmaz_route_planner", Context.MODE_PRIVATE)
    }

    var destination by remember {
        mutableStateOf(routePrefs.getString("last_destination", "").orEmpty())
    }
    var waypoint by remember {
        mutableStateOf(routePrefs.getString("last_waypoint", "").orEmpty())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RouteProfileResult?>(null) }
    var showFullScreen by remember { mutableStateOf(false) }
    var halfScreenAfterCalculate by remember { mutableStateOf(false) }
    var destinationSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var waypointSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var voiceInputTarget by remember { mutableStateOf("destination") }
    val scope = rememberCoroutineScope()

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val spokenText = activityResult.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (spokenText.isNotBlank()) {
            if (voiceInputTarget == "waypoint") {
                waypoint = spokenText
                routePrefs.edit().putString("last_waypoint", spokenText).apply()
                waypointSuggestions = emptyList()
            } else {
                destination = spokenText
                routePrefs.edit().putString("last_destination", spokenText).apply()
                destinationSuggestions = emptyList()
            }
        }
    }

    fun startRouteVoiceInput(target: String) {
        voiceInputTarget = target
        val prompt = if (target == "waypoint") {
            "Ara durağı söyleyin"
        } else {
            "Hedef şehir veya adresi söyleyin"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        runCatching { voiceInputLauncher.launch(intent) }
            .onFailure { error = "Sesli giriş açılamadı" }
    }

    LaunchedEffect(destination) {
        delay(700L)
        destinationSuggestions = if (destination.trim().length >= 3) {
            runCatching { withContext(Dispatchers.IO) { searchPlaceNames(destination.trim()) } }.getOrDefault(emptyList())
        } else emptyList()
    }
    LaunchedEffect(waypoint) {
        delay(700L)
        waypointSuggestions = if (waypoint.trim().length >= 3) {
            runCatching { withContext(Dispatchers.IO) { searchPlaceNames(waypoint.trim()) } }.getOrDefault(emptyList())
        } else emptyList()
    }

    // 136: Tam ekran rota görünümü açıldığında alttaki planlayıcıyı aynı anda compose etme.
    // 10" araç ünitelerinde alttaki ikinci profil/konum dinleyicisi ile tam ekran profilin
    // eşzamanlı çizilmesi bellek/render yükünü ikiye katlayıp uygulamanın kapanmasına yol açabiliyordu.
    // Tam ekran artık tek başına render edilir; yarım ekran -> tam ekran ve doğrudan tam ekran aynı yolu kullanır.
    if (showFullScreen && result != null) {
        RouteFullScreenOverlay(
            result = result!!,
            isCaravan = isCaravan,
            onBack = { showFullScreen = false },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().background(SlopeBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← GERİ",
                color = SlopeCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text("ROTA PLANLAYICI", color = SlopeYellow, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { value ->
                            destination = value
                            routePrefs.edit().putString("last_destination", value).apply()
                        },
                        label = { Text("HEDEF şehir veya adres") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { startRouteVoiceInput("destination") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123A4D))
                    ) {
                        Text("🎙", color = SlopeCyan, fontSize = 20.sp)
                    }
                }
                if (destinationSuggestions.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFF0D1B2A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
                    ) {
                        destinationSuggestions.take(4).forEach { name ->
                            Text(name, color = Color(0xFFBBCCDD), fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    destination = name
                                    routePrefs.edit().putString("last_destination", name).apply()
                                    destinationSuggestions = emptyList()
                                }.padding(7.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = waypoint,
                        onValueChange = { value ->
                            waypoint = value
                            routePrefs.edit().putString("last_waypoint", value).apply()
                        },
                        label = { Text("ARA DURAK (isteğe bağlı)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { startRouteVoiceInput("waypoint") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3310))
                    ) {
                        Text("🎙", color = SlopeYellow, fontSize = 20.sp)
                    }
                }
                if (waypointSuggestions.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFF0D1B2A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
                    ) {
                        waypointSuggestions.take(4).forEach { name ->
                            Text(name, color = Color(0xFFFFAA00), fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    waypoint = name
                                    routePrefs.edit().putString("last_waypoint", name).apply()
                                    waypointSuggestions = emptyList()
                                }.padding(7.dp))
                        }
                    }
                }
            }
            Button(
                onClick = { halfScreenAfterCalculate = !halfScreenAfterCalculate },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (halfScreenAfterCalculate) Color(0xFF0D5535) else Color(0xFF17314A)
                )
            ) {
                Text(
                    if (halfScreenAfterCalculate) "YARIM\nEKRAN ✓" else "YARIM\nEKRAN",
                    color = if (halfScreenAfterCalculate) Color(0xFF63FFA0) else SlopeCyan,
                    fontWeight = FontWeight.Black
                )
            }
            Button(
                onClick = {
                    val lat = currentLat
                    val lon = currentLon
                    if (lat == null || lon == null) {
                        error = "GPS konumu bekleniyor"
                    } else if (destination.trim().length < 3) {
                        error = "Hedef şehir veya adres gir"
                    } else {
                        loading = true
                        error = ""
                        result = null
                        scope.launch {
                            val r = runCatching {
                                withContext(Dispatchers.IO) {
                                    buildRouteProfile(lat, lon, destination.trim(), waypoint.trim())
                                }
                            }
                            loading = false
                            r.onSuccess { route ->
                                result = route
                                if (halfScreenAfterCalculate) {
                                    onShowOnDashboard(route)
                                }
                            }.onFailure { error = it.message ?: "Rota hesaplanamadı" }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66D34E))
            ) {
                Text("ROTA\nHESAPLA", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Rota ve yükseklik profili hesaplanıyor...", color = SlopeCyan, fontSize = 16.sp)
            }
        } else if (result != null) {
            val r = result!!
            Text("📍 ${r.destinationName}", color = Color(0xFF6688AA), fontSize = 12.sp, maxLines = 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatText("MESAFE", String.format(Locale.getDefault(), "%.1f km", r.totalDistanceKm), SlopeCyan)
                StatText("TIRMANIŞ", "${r.totalClimbM} m", SlopeGreen)
                StatText("SÜRE", "${r.etaMinutes / 60}s ${r.etaMinutes % 60}dk", SlopeYellow)
                StatText("MAX EĞİM", String.format(Locale.getDefault(), "%.1f%%", r.maxSlopePct), if (r.maxSlopePct > 10.0) Color(0xFFFF5555) else SlopeGreen)
            }
            if (r.maxSlopePct > 10.0) {
                Text(
                    "⚠ Rotada maksimum %${String.format(Locale.getDefault(), "%.1f", r.maxSlopePct)} eğimli rampa var",
                    color = Color(0xFFFF7777), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(Color(0x331F0505), RoundedCornerShape(8.dp)).padding(7.dp)
                )
            }
            val liveRouteDistanceKm = rememberLiveRouteDistanceKm(r)
            ElevationProfileChart(
                points = r.points,
                currentDistanceKm = liveRouteDistanceKm,
                showVehicle = true,
                isCaravan = isCaravan,
                vehicleScale = 2f,
                showLiveAltitudeArrow = true,
                showPassedRouteColor = true,
                altitudeArrowLength = 190f,
                reserveAltitudeLabelHeadroom = true,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onShowOnDashboard(r) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123A22))
                ) { Text("▣  YARIM EKRAN", color = Color(0xFF44FF77), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { showFullScreen = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132D50))
                ) { Text("📊  TAM EKRAN", color = Color(0xFF66A7FF), fontWeight = FontWeight.Bold) }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (error.isBlank()) "Hedefi girip ROTA HESAPLA'ya dokun." else error,
                    color = if (error.isBlank()) Color(0xFF7E94AA) else Color(0xFFFF6B6B),
                    fontSize = 14.sp
                )
            }
        }
    }

}

@Composable
fun RouteDashboardHalfProfile(
    result: RouteProfileResult,
    isCaravan: Boolean,
    onClose: () -> Unit,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xF202070D), RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFF1E3A5F), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📍 ${result.destinationName}", color = SlopeCyan, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("▲ ${result.totalClimbM}m", color = SlopeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("   ${String.format(Locale.getDefault(), "%.1f km", result.totalDistanceKm)}", color = SlopeYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "TAM EKRAN",
                color = Color(0xFF8FC3FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(Color(0xFF132D50), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFull)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
            Text("✕", color = Color(0xFFB6C4D0), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose).padding(7.dp))
        }
        val liveRouteDistanceKm = rememberLiveRouteDistanceKm(result)
        ElevationProfileChart(
            points = result.points,
            currentDistanceKm = liveRouteDistanceKm,
            showVehicle = true,
            isCaravan = isCaravan,
            vehicleScale = 2f,
            showLiveAltitudeArrow = true,
            showPassedRouteColor = true,
            altitudeArrowLength = 190f,
            reserveAltitudeLabelHeadroom = true,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun LiveElevationScreen(
    currentLat: Double?,
    currentLon: Double?,
    currentAltitudeM: Int?,
    headingDegrees: Float?,
    pastDistancesKm: List<Double>,
    pastElevationsM: List<Double>,
    currentSlopePct: Double,
    isCaravan: Boolean,
    onBack: () -> Unit,
    onShowHalfScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var future by remember { mutableStateOf<List<ElevationSample>>(emptyList()) }
    var futureError by remember { mutableStateOf("") }

    val latKey = currentLat?.let { (it * 10000).toInt() }
    val lonKey = currentLon?.let { (it * 10000).toInt() }
    val headingKey = headingDegrees?.toInt()

    LaunchedEffect(latKey, lonKey, headingKey) {
        val lat = currentLat
        val lon = currentLon
        val heading = headingDegrees
        if (lat != null && lon != null && heading != null) {
            val fetched = runCatching {
                withContext(Dispatchers.IO) { fetchAheadElevation(lat, lon, heading.toDouble(), 5.0) }
            }
            fetched.onSuccess { future = it; futureError = "" }
                .onFailure { futureError = "Öndeki rakım verisi alınamadı" }
        }
    }

    val past = remember(pastDistancesKm, pastElevationsM) {
        if (pastDistancesKm.isEmpty() || pastElevationsM.isEmpty()) emptyList() else {
            val n = min(pastDistancesKm.size, pastElevationsM.size)
            val d = pastDistancesKm.takeLast(n)
            val e = pastElevationsM.takeLast(n)
            val last = d.lastOrNull() ?: 0.0
            d.indices.mapNotNull { i ->
                val rel = d[i] - last
                if (rel >= -5.0) ElevationSample(rel, e[i]) else null
            }
        }
    }
    // GPS rakımındaki küçük sıçramaları eski uygulamadaki gibi ağırlıklı hareketli ortalama ile yumuşat.
    val smoothedPast = remember(past) { smoothElevationSamples(past, radius = 3) }
    val currentBase = (smoothedPast.takeLast(5).map { it.elevationM } + listOfNotNull(currentAltitudeM?.toDouble()))
    val smoothCurrentAltitude = currentBase.takeIf { it.isNotEmpty() }?.average()
    val current = smoothCurrentAltitude?.let { listOf(ElevationSample(0.0, it, currentSlopePct)) } ?: emptyList()
    val combined = smoothElevationSamples(
        (smoothedPast + current + future.filter { it.distanceKm > 0.01 }).sortedBy { it.distanceKm },
        radius = 2
    )

    Column(
        modifier = modifier.fillMaxSize().background(SlopeBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← GERİ", color = SlopeCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
            Spacer(Modifier.weight(1f))
            Text("CANLI YÜKSEKLİK", color = SlopeYellow, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(
                "YARIM EKRAN",
                color = Color(0xFF63FFA0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(Color(0xFF103C2A), RoundedCornerShape(9.dp))
                    .border(1.dp, Color(0xFF2E8C61), RoundedCornerShape(9.dp))
                    .clickable(onClick = onShowHalfScreen)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        ElevationProfileChart(
            points = if (combined.size >= 2) combined else listOf(
                ElevationSample(-5.0, (currentAltitudeM ?: 0).toDouble()),
                ElevationSample(0.0, (currentAltitudeM ?: 0).toDouble()),
                ElevationSample(5.0, (currentAltitudeM ?: 0).toDouble())
            ),
            currentDistanceKm = 0.0,
            showVehicle = true,
            isCaravan = isCaravan,
            fixedMinDistanceKm = -5.0,
            fixedMaxDistanceKm = 5.0,
            stabilizeVehicleY = true,
            vehicleScale = 2f,
            showLiveAltitudeArrow = true,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF090D12), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF26313B), RoundedCornerShape(10.dp))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatText("RAKIM", "${currentAltitudeM ?: 0} m")
            StatText("GERİ", "-5.0 km")
            StatText("EĞİM", String.format(Locale.getDefault(), "%+.1f%%", currentSlopePct), SlopeGreen)
            StatText("ÖNDEKİ", "+5.0 km")
            val maxElev = combined.maxOfOrNull { it.elevationM }
            StatText("MAKS RAKIM", if (maxElev == null) "--" else "${maxElev.toInt()} m")
        }
        if (futureError.isNotBlank()) {
            Text(futureError, color = Color(0xFFFFB84D), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun LiveElevationDashboardHalfPanel(
    currentLat: Double?,
    currentLon: Double?,
    currentAltitudeM: Int?,
    headingDegrees: Float?,
    pastDistancesKm: List<Double>,
    pastElevationsM: List<Double>,
    currentSlopePct: Double,
    isCaravan: Boolean,
    onClose: () -> Unit,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    var future by remember { mutableStateOf<List<ElevationSample>>(emptyList()) }
    var futureError by remember { mutableStateOf("") }

    val latKey = currentLat?.let { (it * 10000).toInt() }
    val lonKey = currentLon?.let { (it * 10000).toInt() }
    val headingKey = headingDegrees?.toInt()

    LaunchedEffect(latKey, lonKey, headingKey) {
        val lat = currentLat
        val lon = currentLon
        val heading = headingDegrees
        if (lat != null && lon != null && heading != null) {
            runCatching {
                withContext(Dispatchers.IO) { fetchAheadElevation(lat, lon, heading.toDouble(), 5.0) }
            }.onSuccess {
                future = it
                futureError = ""
            }.onFailure {
                futureError = "Öndeki rakım verisi alınamadı"
            }
        }
    }

    val past = remember(pastDistancesKm, pastElevationsM) {
        if (pastDistancesKm.isEmpty() || pastElevationsM.isEmpty()) {
            emptyList()
        } else {
            val n = min(pastDistancesKm.size, pastElevationsM.size)
            val d = pastDistancesKm.takeLast(n)
            val e = pastElevationsM.takeLast(n)
            val last = d.lastOrNull() ?: 0.0
            d.indices.mapNotNull { i ->
                val rel = d[i] - last
                if (rel >= -5.0) ElevationSample(rel, e[i]) else null
            }
        }
    }
    val smoothedPast = remember(past) { smoothElevationSamples(past, radius = 3) }
    val currentBase = smoothedPast.takeLast(5).map { it.elevationM } + listOfNotNull(currentAltitudeM?.toDouble())
    val smoothCurrentAltitude = currentBase.takeIf { it.isNotEmpty() }?.average()
    val current = smoothCurrentAltitude?.let { listOf(ElevationSample(0.0, it, currentSlopePct)) } ?: emptyList()
    val combined = smoothElevationSamples(
        (smoothedPast + current + future.filter { it.distanceKm > 0.01 }).sortedBy { it.distanceKm },
        radius = 2
    )

    Column(
        modifier = modifier
            .background(Color(0xF202070D), RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFF1E3A5F), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("CANLI YÜKSEKLİK", color = SlopeYellow, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(
                "TAM EKRAN",
                color = Color(0xFF8FC3FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(Color(0xFF132D50), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFull)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
            Text("✕", color = Color(0xFFB6C4D0), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose).padding(7.dp))
        }

        ElevationProfileChart(
            points = if (combined.size >= 2) combined else listOf(
                ElevationSample(-5.0, (currentAltitudeM ?: 0).toDouble()),
                ElevationSample(0.0, (currentAltitudeM ?: 0).toDouble()),
                ElevationSample(5.0, (currentAltitudeM ?: 0).toDouble())
            ),
            currentDistanceKm = 0.0,
            showVehicle = true,
            isCaravan = isCaravan,
            fixedMinDistanceKm = -5.0,
            fixedMaxDistanceKm = 5.0,
            stabilizeVehicleY = true,
            vehicleScale = 2f,
            showLiveAltitudeArrow = true,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF090D12), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF26313B), RoundedCornerShape(10.dp))
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatText("RAKIM", "${currentAltitudeM ?: 0} m")
            StatText("EĞİM", String.format(Locale.getDefault(), "%+.1f%%", currentSlopePct), SlopeGreen)
            val maxElev = combined.maxOfOrNull { it.elevationM }
            StatText("MAKS", if (maxElev == null) "--" else "${maxElev.toInt()} m")
        }
        if (futureError.isNotBlank()) {
            Text(futureError, color = Color(0xFFFFB84D), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun StatText(label: String, value: String, valueColor: Color = SlopeText) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF9DA7B2), fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ElevationProfileChart(
    points: List<ElevationSample>,
    currentDistanceKm: Double,
    showVehicle: Boolean,
    isCaravan: Boolean,
    modifier: Modifier = Modifier,
    fixedMinDistanceKm: Double? = null,
    fixedMaxDistanceKm: Double? = null,
    stabilizeVehicleY: Boolean = false,
    vehicleScale: Float = 1f,
    selectedDistanceKm: Double? = null,
    dragEnabled: Boolean = false,
    onSelectedDistanceChange: ((Double) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    startLabel: String? = null,
    waypointLabel: String? = null,
    waypointDistanceKm: Double? = null,
    destinationLabel: String? = null,
    highlightMaxPoint: Boolean = false,
    showCurrentGuide: Boolean = false,
    showBlinkingCurrentDot: Boolean = false,
    showLiveAltitudeArrow: Boolean = false,
    showPassedRouteColor: Boolean = false,
    altitudeArrowLength: Float = 126f,
    reserveAltitudeLabelHeadroom: Boolean = false
) {
    if (points.size < 2) return
    val context = LocalContext.current
    val carPhoto = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.vehicle_sandero_2017_photo).asImageBitmap()
    }
    val caravanPhoto = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.vehicle_sandero_2017_caravan_photo).asImageBitmap()
    }
    val minD = fixedMinDistanceKm ?: points.minOf { it.distanceKm }
    val maxD = fixedMaxDistanceKm ?: points.maxOf { it.distanceKm }
    val minE0 = points.minOf { it.elevationM }
    val maxE0 = points.maxOf { it.elevationM }
    val pad = max(30.0, (maxE0 - minE0) * 0.12)
    val minE = minE0 - pad
    val maxE = maxE0 + pad

    val activeDistance = (selectedDistanceKm ?: currentDistanceKm).coerceIn(minD, maxD)
    val infiniteTransition = rememberInfiniteTransition(label = "routePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .background(Color(0xFF050B15), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1D2D40), RoundedCornerShape(12.dp))
            .pointerInput(points, dragEnabled) {
                if (!dragEnabled || onSelectedDistanceChange == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val leftPad = 58f
                        val rightPad = size.width.toFloat() - 12f
                        val clamped = offset.x.coerceIn(leftPad, rightPad)
                        val fraction = ((clamped - leftPad) / (rightPad - leftPad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSelectedDistanceChange(minD + (maxD - minD) * fraction)
                    },
                    onDrag = { change, _ ->
                        val leftPad = 58f
                        val rightPad = size.width.toFloat() - 12f
                        val clamped = change.position.x.coerceIn(leftPad, rightPad)
                        val fraction = ((clamped - leftPad) / (rightPad - leftPad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSelectedDistanceChange(minD + (maxD - minD) * fraction)
                    },
                    onDragEnd = { onDragEnd?.invoke() },
                    onDragCancel = { onDragEnd?.invoke() }
                )
            }
            .padding(8.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 58f
            val right = size.width - 12f
            // Rota Planlayıcıda araç yüksek rakıma çıktığında üstte rakım etiketi için
            // ayrı bir baş boşluğu bırakılır. Canlı Yükseklik mevcut yerleşimini korur.
            val top = if (reserveAltitudeLabelHeadroom) 72f else 14f
            val bottom = size.height - 34f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            fun x(d: Double): Float = left + (((d - minD) / (maxD - minD).coerceAtLeast(0.001)) * width).toFloat()
            fun y(e: Double): Float = bottom - (((e - minE) / (maxE - minE).coerceAtLeast(1.0)) * height).toFloat()

            repeat(5) { i ->
                val yy = top + height * i / 4f
                drawLine(SlopeGrid.copy(alpha = 0.65f), Offset(left, yy), Offset(right, yy), 1f)
            }

            val fill = Path().apply {
                moveTo(x(points.first().distanceKm), bottom)
                points.forEachIndexed { index, p ->
                    if (index == 0) lineTo(x(p.distanceKm), y(p.elevationM))
                    else lineTo(x(p.distanceKm), y(p.elevationM))
                }
                lineTo(x(points.last().distanceKm), bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFB4A38C).copy(alpha = 0.9f), Color(0xFF2A241D).copy(alpha = 0.85f))))

            for (i in 1 until points.size) {
                drawLine(
                    SlopeGreen,
                    Offset(x(points[i - 1].distanceKm), y(points[i - 1].elevationM)),
                    Offset(x(points[i].distanceKm), y(points[i].elevationM)),
                    4f,
                    cap = StrokeCap.Round
                )
            }

            // Geçilen rota: aracın gerçek konumunun arkasında kalan profil mavi/cyan görünür.
            // İleri taraf yeşil kalır; böylece sürüş ilerlemesi tek bakışta anlaşılır.
            if (showPassedRouteColor) {
                val passedDistance = currentDistanceKm.coerceIn(minD, maxD)
                val passedColor = Color(0xFF38A8FF)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    if (p0.distanceKm >= passedDistance) break

                    if (p1.distanceKm <= passedDistance) {
                        drawLine(
                            color = passedColor,
                            start = Offset(x(p0.distanceKm), y(p0.elevationM)),
                            end = Offset(x(p1.distanceKm), y(p1.elevationM)),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                    } else {
                        val span = (p1.distanceKm - p0.distanceKm).coerceAtLeast(0.000001)
                        val t = ((passedDistance - p0.distanceKm) / span).coerceIn(0.0, 1.0)
                        val endElevation = p0.elevationM + (p1.elevationM - p0.elevationM) * t
                        drawLine(
                            color = passedColor,
                            start = Offset(x(p0.distanceKm), y(p0.elevationM)),
                            end = Offset(x(passedDistance), y(endElevation)),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                        break
                    }
                }
            }

            val nearestCurrent = points.minByOrNull { abs(it.distanceKm - activeDistance) } ?: points.first()
            val cx = x(activeDistance)
            val cy = y(nearestCurrent.elevationM)
            if (showCurrentGuide) {
                drawLine(Color(0xFFE43B3B), Offset(cx, top), Offset(cx, bottom), 4f)
            }
            val dotColor = Color(0xFF3C96FF).copy(alpha = if (showBlinkingCurrentDot) pulseAlpha else 1f)
            drawCircle(dotColor, 16f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = if (showBlinkingCurrentDot) pulseAlpha else 1f), 16f, Offset(cx, cy), style = Stroke(3f))

            waypointDistanceKm?.let { wd ->
                if (wd in minD..maxD) {
                    val wx = x(wd)
                    val wp = points.minByOrNull { abs(it.distanceKm - wd) } ?: points.first()
                    val wy = y(wp.elevationM)
                    drawCircle(Color(0xFF4C92FF), 7f, Offset(wx, wy))
                    drawCircle(Color.White, 7f, Offset(wx, wy), style = Stroke(2f))
                    drawLine(Color(0x665A7FFF), Offset(wx, top), Offset(wx, bottom), 1f)
                }
            }
            if (highlightMaxPoint) {
                val maxPoint = points.maxByOrNull { it.elevationM } ?: points.first()
                val mx = x(maxPoint.distanceKm)
                val my = y(maxPoint.elevationM)
                drawCircle(Color(0xFFFFC638), 7f, Offset(mx, my))
                drawCircle(Color.White, 7f, Offset(mx, my), style = Stroke(2f))
            }

            // Araç ve rakım oku aynı görsel Y referansını kullanır.
            // Böylece araç yükselip/alçaldığında ok ve rakım etiketi de onunla birlikte hareket eder.
            val vehicleGroundY = if (stabilizeVehicleY) {
                (cy * 0.25f) + ((top + height * 0.42f) * 0.75f)
            } else {
                cy
            }
            val vehicleImage = if (isCaravan) caravanPhoto else carPhoto
            val vehicleTargetWidth = (if (isCaravan) 148f else 86f) * vehicleScale
            val vehicleAspect = vehicleImage.height.toFloat() / vehicleImage.width.toFloat().coerceAtLeast(1f)
            val vehicleTargetHeight = vehicleTargetWidth * vehicleAspect
            val rawVehicleTopY = vehicleGroundY - vehicleTargetHeight + 8f * vehicleScale
            // Rota planlayıcıda rakım etiketi aracın/karavanın yaklaşık 10 mm üstünde sabit kalsın.
            // Araç çok yüksek bir profile çıktığında etiketi aracın üstüne sıkıştırmak yerine
            // araç görselini yalnız gerektiği kadar aşağıda tutuyoruz; böylece ikisi birlikte hareket ediyor.
            val routeLabelHeight = 28f
            val routeLabelGap = 40f
            val minimumRouteVehicleTop = 4f + routeLabelHeight + routeLabelGap
            val vehicleTopY = if (reserveAltitudeLabelHeadroom) {
                rawVehicleTopY.coerceAtLeast(minimumRouteVehicleTop)
            } else {
                rawVehicleTopY
            }
            val leftExtent = if (isCaravan) vehicleTargetWidth * 0.68f else vehicleTargetWidth * 0.48f
            val rightExtent = if (isCaravan) vehicleTargetWidth * 0.32f else vehicleTargetWidth * 0.52f
            // Rota planlayıcıda başlangıç noktası ekranın en solunda olduğu için araç ekrana sığmak üzere sağa kaydırılır.
            // Ok da aynı X referansını kullanmalı; aksi halde ok mavi noktada kalıp araçtan ayrılır.
            val vehicleCenterX = cx.coerceIn(
                left + leftExtent + 4f,
                (right - rightExtent - 4f).coerceAtLeast(left + leftExtent + 4f)
            )

            if (showVehicle) {
                val dstLeft = vehicleCenterX - leftExtent
                drawImage(
                    image = vehicleImage,
                    dstOffset = IntOffset(dstLeft.toInt(), vehicleTopY.toInt()),
                    dstSize = IntSize(
                        vehicleTargetWidth.toInt().coerceAtLeast(1),
                        vehicleTargetHeight.toInt().coerceAtLeast(1)
                    ),
                    alpha = 1f
                )
            }

            if (showLiveAltitudeArrow) {
                // Ok hem Y hem X ekseninde aracın gerçek çizildiği konuma bağlıdır.
                // Böylece özellikle rota planlayıcıda başlangıçta sağa kaydırılan araçla birlikte hareket eder.
                val arrowX = if (showVehicle) vehicleCenterX else cx
                val arrowStartY = if (showVehicle) vehicleTopY - 6f else vehicleGroundY - 10f
                val arrowStart = Offset(arrowX, arrowStartY)
                val desiredTipY = arrowStartY - altitudeArrowLength
                // Rota planlayıcıda ok ucu ve rakım etiketi doğrudan aracın üst kenarına bağlanır.
                // Etiket alt kenarı araç üst kenarından yaklaşık 10 mm (40 px) yukarıdadır.
                val routeLabelTop = vehicleTopY - routeLabelGap - routeLabelHeight
                val arrowTipY = if (reserveAltitudeLabelHeadroom && showVehicle) {
                    routeLabelTop + routeLabelHeight + 3f
                } else if (reserveAltitudeLabelHeadroom) {
                    desiredTipY.coerceAtLeast(40f)
                } else {
                    desiredTipY.coerceAtLeast(top + 54f)
                }
                val arrowEnd = Offset(arrowX, arrowTipY)
                drawLine(
                    color = Color(0xFFE43B3B),
                    start = arrowStart,
                    end = arrowEnd,
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    Color(0xFFE43B3B),
                    arrowEnd,
                    Offset(arrowX - 10f, arrowTipY + 15f),
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    Color(0xFFE43B3B),
                    arrowEnd,
                    Offset(arrowX + 10f, arrowTipY + 15f),
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )

                val label = "Canlı Rakım ${nearestCurrent.elevationM.toInt()} m"
                val labelPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                val desiredWidth = labelPaint.measureText(label) + 18f
                val bgWidth = desiredWidth.coerceAtMost((right - left) * .62f)
                val bgLeft = (arrowX - bgWidth / 2f).coerceIn(left + 4f, right - bgWidth - 4f)
                val bgTop = if (reserveAltitudeLabelHeadroom && showVehicle) {
                    routeLabelTop.coerceAtLeast(4f)
                } else {
                    (arrowTipY - 34f).coerceAtLeast(top + 4f)
                }
                drawRoundRect(
                    color = Color(0xD9141A22),
                    topLeft = Offset(bgLeft, bgTop),
                    size = Size(bgWidth, 28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = Color(0xFFE43B3B),
                    topLeft = Offset(bgLeft, bgTop),
                    size = Size(bgWidth, 28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 2f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    bgLeft + 9f,
                    bgTop + 19f,
                    labelPaint
                )
            }
        }

        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { idx ->
                val frac = (4 - idx) / 4.0
                val elev = minE + (maxE - minE) * frac
                Text("${elev.toInt()}m", color = SlopeYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (idx < 4) Spacer(Modifier.height(18.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 55.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val ticks = if (fixedMinDistanceKm != null && fixedMaxDistanceKm != null) {
                listOf(fixedMinDistanceKm, -2.5, 0.0, 2.5, fixedMaxDistanceKm)
            } else {
                listOf(minD, minD + (maxD-minD)*.25, minD + (maxD-minD)*.5, minD + (maxD-minD)*.75, maxD)
            }
            ticks.forEach { d ->
                val label = if (fixedMinDistanceKm != null) String.format(Locale.getDefault(), "%+.1fkm", d).replace("+0.0", "0")
                else "${d.toInt()}km"
                Text(label, color = SlopeYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (startLabel != null) {
            MarkerChip(startLabel, Color(0xFF1AC94C), Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 46.dp))
        }
        if (waypointLabel != null && waypointDistanceKm != null) {
            MarkerChip(waypointLabel, Color(0xFF4C92FF), Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        }
        if (destinationLabel != null) {
            MarkerChip(destinationLabel, Color(0xFFFF4C5B), Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        }
    }
}

@Composable
private fun MarkerChip(label: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xE6101723), RoundedCornerShape(12.dp))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RouteReferenceProfileChart(
    result: RouteProfileResult,
    selectedDistanceKm: Double,
    isCaravan: Boolean,
    onSelectedDistanceChange: (Double) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val points = result.points
    if (points.size < 2) return

    val context = LocalContext.current
    val carPhoto = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.vehicle_sandero_2017_photo).asImageBitmap()
    }
    val caravanPhoto = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.vehicle_sandero_2017_caravan_photo).asImageBitmap()
    }

    val minD = points.minOf { it.distanceKm }
    val maxD = points.maxOf { it.distanceKm }.coerceAtLeast(minD + 0.001)
    val minE0 = points.minOf { it.elevationM }
    val maxE0 = points.maxOf { it.elevationM }
    val rangeE = (maxE0 - minE0).coerceAtLeast(120.0)

    // Referans ekrandaki gibi profilin üstünde araç, rakım etiketi ve önemli nokta
    // etiketleri için gerçek bir görsel boşluk bırakılır. Böylece grafik asla ince bir
    // çizgiye dönüşmez ve araç/etiketler birbirinin üstüne binmez.
    val minE = minE0 - max(45.0, rangeE * 0.08)
    val maxE = maxE0 + max(170.0, rangeE * 0.32)

    val activeDistance = selectedDistanceKm.coerceIn(minD, maxD)
    val selectedPoint = points.minByOrNull { abs(it.distanceKm - activeDistance) } ?: points.first()

    val infiniteTransition = rememberInfiniteTransition(label = "routeFullPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "routeFullPulseAlpha"
    )

    Box(
        modifier = modifier
            .background(Color(0xFF030912), RoundedCornerShape(18.dp))
            .border(1.3.dp, Color(0xFF1C3147), RoundedCornerShape(18.dp))
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val leftPad = 92f
                        val rightPad = size.width.toFloat() - 24f
                        val clamped = offset.x.coerceIn(leftPad, rightPad)
                        val fraction = ((clamped - leftPad) / (rightPad - leftPad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSelectedDistanceChange(minD + (maxD - minD) * fraction)
                    },
                    onDrag = { change, _ ->
                        val leftPad = 92f
                        val rightPad = size.width.toFloat() - 24f
                        val clamped = change.position.x.coerceIn(leftPad, rightPad)
                        val fraction = ((clamped - leftPad) / (rightPad - leftPad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSelectedDistanceChange(minD + (maxD - minD) * fraction)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 92f
            val right = size.width - 24f
            val top = 24f
            val bottom = size.height - 58f
            val graphWidth = (right - left).coerceAtLeast(1f)
            val graphHeight = (bottom - top).coerceAtLeast(1f)

            fun x(distanceKm: Double): Float =
                left + (((distanceKm - minD) / (maxD - minD)) * graphWidth).toFloat()

            fun y(elevationM: Double): Float =
                bottom - (((elevationM - minE) / (maxE - minE)) * graphHeight).toFloat()

            val axisTextSize = (size.height * 0.052f).coerceIn(18f, 30f)
            val smallTextSize = (size.height * 0.036f).coerceIn(15f, 22f)
            val badgeTextSize = (size.height * 0.033f).coerceIn(14f, 20f)

            val axisPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = SlopeYellow.toArgb()
                textSize = axisTextSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val whitePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = badgeTextSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val waypointPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(255, 168, 20)
                textSize = smallTextSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            // Y ekseni ve 5 yatay kılavuz çizgisi.
            repeat(5) { index ->
                val fractionFromBottom = index / 4.0
                val elevation = minE + (maxE - minE) * fractionFromBottom
                val yy = bottom - graphHeight * index / 4f
                drawLine(
                    color = SlopeGrid.copy(alpha = 0.45f),
                    start = Offset(left, yy),
                    end = Offset(right, yy),
                    strokeWidth = 1.2f
                )
                val label = "${elevation.toInt()}m"
                val labelWidth = axisPaint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    (left - labelWidth - 14f).coerceAtLeast(4f),
                    yy + axisTextSize * 0.34f,
                    axisPaint
                )
            }

            // Ana dolgu ve yeşil rota profili.
            val fillPath = Path().apply {
                moveTo(x(points.first().distanceKm), bottom)
                points.forEach { p -> lineTo(x(p.distanceKm), y(p.elevationM)) }
                lineTo(x(points.last().distanceKm), bottom)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFC7B49A).copy(alpha = 0.88f),
                        Color(0xFF5A4D40).copy(alpha = 0.78f),
                        Color(0xFF221E1A).copy(alpha = 0.92f)
                    ),
                    startY = top,
                    endY = bottom
                )
            )

            for (i in 1 until points.size) {
                drawLine(
                    color = SlopeGreen,
                    start = Offset(x(points[i - 1].distanceKm), y(points[i - 1].elevationM)),
                    end = Offset(x(points[i].distanceKm), y(points[i].elevationM)),
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )
            }

            // Geçilen rota aracın arkasında mavi/cyan; gidilecek rota yeşil kalır.
            // Sürükleyerek inceleme yapılırsa renk geçici olarak araçla birlikte gider; bırakınca canlı GPS konumuna döner.
            val passedColor = Color(0xFF38A8FF)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                if (p0.distanceKm >= activeDistance) break

                if (p1.distanceKm <= activeDistance) {
                    drawLine(
                        color = passedColor,
                        start = Offset(x(p0.distanceKm), y(p0.elevationM)),
                        end = Offset(x(p1.distanceKm), y(p1.elevationM)),
                        strokeWidth = 5.8f,
                        cap = StrokeCap.Round
                    )
                } else {
                    val span = (p1.distanceKm - p0.distanceKm).coerceAtLeast(0.000001)
                    val t = ((activeDistance - p0.distanceKm) / span).coerceIn(0.0, 1.0)
                    val endElevation = p0.elevationM + (p1.elevationM - p0.elevationM) * t
                    drawLine(
                        color = passedColor,
                        start = Offset(x(p0.distanceKm), y(p0.elevationM)),
                        end = Offset(x(activeDistance), y(endElevation)),
                        strokeWidth = 5.8f,
                        cap = StrokeCap.Round
                    )
                    break
                }
            }

            // X ekseni değerleri doğrudan Canvas'a çizilir; Row taşması/çakışması olmaz.
            val tickDistances = listOf(
                minD,
                minD + (maxD - minD) * 0.25,
                minD + (maxD - minD) * 0.50,
                minD + (maxD - minD) * 0.75,
                maxD
            )
            tickDistances.forEachIndexed { index, distance ->
                val xx = x(distance)
                val text = "${distance.toInt()}km"
                val tw = axisPaint.measureText(text)
                val tx = when (index) {
                    0 -> xx
                    tickDistances.lastIndex -> xx - tw
                    else -> xx - tw / 2f
                }.coerceIn(left - 2f, right - tw + 2f)
                drawContext.canvas.nativeCanvas.drawText(text, tx, size.height - 14f, axisPaint)
            }

            // Seçili konum noktası ve dikey kılavuz.
            val currentX = x(activeDistance)
            val profileY = y(selectedPoint.elevationM)
            drawLine(
                color = Color(0xFFE64A4A).copy(alpha = 0.78f),
                start = Offset(currentX, profileY + 10f),
                end = Offset(currentX, bottom),
                strokeWidth = 2.2f
            )
            drawCircle(
                color = Color(0xFF406A9C).copy(alpha = pulseAlpha),
                radius = 13f,
                center = Offset(currentX, profileY)
            )
            drawCircle(
                color = Color.White,
                radius = 13f,
                center = Offset(currentX, profileY),
                style = Stroke(2.5f)
            )

            // Araç, seçili profil noktasının üzerinde gerçek fotoğraf olarak ilerler.
            val vehicleImage = if (isCaravan) caravanPhoto else carPhoto
            val vehicleWidth = if (isCaravan) 205f else 142f
            val vehicleAspect = vehicleImage.height.toFloat() / vehicleImage.width.toFloat().coerceAtLeast(1f)
            val vehicleHeight = vehicleWidth * vehicleAspect
            val leftExtent = if (isCaravan) vehicleWidth * 0.68f else vehicleWidth * 0.48f
            val rightExtent = if (isCaravan) vehicleWidth * 0.32f else vehicleWidth * 0.52f
            val vehicleCenterX = currentX.coerceIn(
                left + leftExtent + 8f,
                (right - rightExtent - 8f).coerceAtLeast(left + leftExtent + 8f)
            )
            val vehicleTop = profileY - vehicleHeight + 7f
            val vehicleLeft = vehicleCenterX - leftExtent

            drawImage(
                image = vehicleImage,
                dstOffset = IntOffset(vehicleLeft.toInt(), vehicleTop.toInt()),
                dstSize = IntSize(vehicleWidth.toInt(), vehicleHeight.toInt()),
                alpha = 1f
            )

            // Canlı Rakım etiketi her zaman aracın yaklaşık 10 mm üstündedir.
            // Profilin üst boşluğu bu mesafeyi koruyacak şekilde yukarıda ayrılmıştır.
            val liveLabel = "Canlı Rakım ${selectedPoint.elevationM.toInt()} m"
            val liveBadgeHeight = 30f
            val vehicleLabelGap = 42f
            val liveBadgeWidth = (whitePaint.measureText(liveLabel) + 22f).coerceAtMost(graphWidth * 0.38f)
            val liveBadgeLeft = (vehicleCenterX - liveBadgeWidth / 2f)
                .coerceIn(left + 6f, right - liveBadgeWidth - 6f)
            val liveBadgeTop = (vehicleTop - vehicleLabelGap - liveBadgeHeight).coerceAtLeast(top + 4f)
            val liveArrowX = vehicleCenterX
            val liveArrowBottom = vehicleTop - 5f
            val liveArrowTop = liveBadgeTop + liveBadgeHeight + 3f

            if (liveArrowBottom > liveArrowTop) {
                drawLine(
                    color = Color(0xFFE64A4A),
                    start = Offset(liveArrowX, liveArrowBottom),
                    end = Offset(liveArrowX, liveArrowTop),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFE64A4A),
                    start = Offset(liveArrowX, liveArrowTop),
                    end = Offset(liveArrowX - 7f, liveArrowTop + 10f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFE64A4A),
                    start = Offset(liveArrowX, liveArrowTop),
                    end = Offset(liveArrowX + 7f, liveArrowTop + 10f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            drawRoundRect(
                color = Color(0xEE05080D),
                topLeft = Offset(liveBadgeLeft, liveBadgeTop),
                size = Size(liveBadgeWidth, liveBadgeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFE64A4A),
                topLeft = Offset(liveBadgeLeft, liveBadgeTop),
                size = Size(liveBadgeWidth, liveBadgeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(1.8f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                liveLabel,
                liveBadgeLeft + 11f,
                liveBadgeTop + liveBadgeHeight * 0.69f,
                whitePaint
            )

            // Ara durak etiketi gerçek konumunda gösterilir; uzun şehir adı tek satırda kısaltılır.
            result.waypointDistanceKm?.takeIf { it in minD..maxD }?.let { waypointDistance ->
                val waypointPoint = points.minByOrNull { abs(it.distanceKm - waypointDistance) } ?: points.first()
                val wx = x(waypointDistance)
                val wy = y(waypointPoint.elevationM)
                drawLine(
                    color = Color(0xFFFFB52E).copy(alpha = 0.55f),
                    start = Offset(wx, wy + 8f),
                    end = Offset(wx, bottom),
                    strokeWidth = 1.6f
                )
                drawCircle(Color(0xFFFFA31A), 6f, Offset(wx, wy))
                drawCircle(Color.White, 6f, Offset(wx, wy), style = Stroke(1.6f))

                val rawName = result.waypointName?.let(::shortPlaceName).orEmpty()
                val visibleName = if (rawName.length > 15) rawName.take(14) + "…" else rawName
                if (visibleName.isNotBlank()) {
                    val wpTextWidth = waypointPaint.measureText(visibleName)
                    val wpBadgeWidth = (wpTextWidth + 22f).coerceAtMost(graphWidth * 0.28f)
                    val wpLeft = (wx - wpBadgeWidth / 2f).coerceIn(left + 6f, right - wpBadgeWidth - 6f)
                    val wpTop = (wy - 54f).coerceAtLeast(top + 4f)
                    drawRoundRect(
                        color = Color(0xEE030507),
                        topLeft = Offset(wpLeft, wpTop),
                        size = Size(wpBadgeWidth, 34f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        visibleName,
                        wpLeft + 11f,
                        wpTop + 24f,
                        waypointPaint
                    )
                }
            }

            // Hedef etiketi sağ uçta profilin üzerine yerleşir, eksen değerleriyle çakışmaz.
            val destinationPoint = points.last()
            val dx = x(destinationPoint.distanceKm)
            val dy = y(destinationPoint.elevationM)
            val destinationName = shortPlaceName(result.destinationName)
            val destinationText = if (destinationName.length > 13) destinationName.take(12) + "…" else destinationName
            val destinationWidth = (whitePaint.measureText(destinationText) + 22f).coerceAtMost(graphWidth * 0.22f)
            val destinationLeft = (dx - destinationWidth).coerceIn(left + 6f, right - destinationWidth)
            val destinationTop = (dy - 46f).coerceIn(top + 4f, bottom - 52f)
            drawRoundRect(
                color = Color(0xEE05080D),
                topLeft = Offset(destinationLeft, destinationTop),
                size = Size(destinationWidth, 32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFFF4C5B),
                topLeft = Offset(destinationLeft, destinationTop),
                size = Size(destinationWidth, 32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(1.6f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                destinationText,
                destinationLeft + 11f,
                destinationTop + 22f,
                whitePaint
            )
        }
    }
}

@Composable
fun RouteFullScreenOverlay(
    result: RouteProfileResult,
    isCaravan: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveRouteDistanceKm = rememberLiveRouteDistanceKm(result)
    var inspectedDistanceKm by remember(result) { mutableStateOf<Double?>(null) }
    val selectedDistanceKm = (inspectedDistanceKm ?: liveRouteDistanceKm)
        .coerceIn(0.0, result.totalDistanceKm.coerceAtLeast(0.0))
    val selectedPoint = remember(result, selectedDistanceKm) {
        result.points.minByOrNull { abs(it.distanceKm - selectedDistanceKm) } ?: result.points.first()
    }
    val oneKmSlope = remember(result, selectedDistanceKm) {
        val endKm = (selectedDistanceKm + 1.0).coerceAtMost(result.totalDistanceKm)
        val oneKmPoints = result.points.filter { it.distanceKm in selectedDistanceKm..endKm }
        if (oneKmPoints.size > 1) oneKmPoints.drop(1).map { it.slopePct }.average() else selectedPoint.slopePct
    }

    Box(modifier.fillMaxSize().background(Color(0xFF02070D))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Üst başlık: referans ekrandaki gibi rota solda, özet değerler sağda.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "← GERİ",
                    color = Color(0xFF75B9F2),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier
                        .background(Color(0xFF0B1E35), RoundedCornerShape(14.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                )

                Text(
                    result.routeTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.55f)
                )

                Row(
                    modifier = Modifier.weight(1.35f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniHeaderStat(
                        label = "Maks. Rakım",
                        value = "${result.maxElevationM.toInt()} m",
                        valueColor = SlopeYellow,
                        modifier = Modifier.weight(1f)
                    )
                    MiniHeaderStat(
                        label = "Ortalama Eğim",
                        value = "%${String.format(Locale.getDefault(), "%.1f", result.maxSlopePct)}",
                        valueColor = SlopeYellow,
                        modifier = Modifier.weight(1f)
                    )
                    MiniHeaderStat(
                        label = "Araç Modu",
                        value = if (isCaravan) "Karavan" else "Otomobil",
                        valueColor = SlopeYellow,
                        modifier = Modifier.weight(1.12f)
                    )
                }
            }

            // Referans ekrandaki ikinci bilgi satırı.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    "→1km: ${if (oneKmSlope >= 0) "▲" else "▼"}${String.format(Locale.getDefault(), "%.1f", abs(oneKmSlope))}%",
                    color = if (abs(oneKmSlope) > 8.0) Color(0xFFFF6666) else SlopeGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                InlineRouteStat("İLERLEME", "${selectedDistanceKm.toInt()} km", Color(0xFF7FD7FF))
                InlineRouteStat("Rakım", "${selectedPoint.elevationM.toInt()} m", SlopeYellow)
                InlineRouteStat(
                    "Eğim",
                    "${if (selectedPoint.slopePct >= 0) "+" else ""}${String.format(Locale.getDefault(), "%.1f", selectedPoint.slopePct)}%",
                    if (abs(selectedPoint.slopePct) > 8.0) Color(0xFFFF6B6B) else SlopeGreen
                )
            }

            RouteReferenceProfileChart(
                result = result,
                selectedDistanceKm = selectedDistanceKm,
                isCaravan = isCaravan,
                onSelectedDistanceChange = { inspectedDistanceKm = it },
                onDragEnd = { inspectedDistanceKm = null },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun InlineRouteStat(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            color = Color(0xFFA9B8C7),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            value,
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniHeaderStat(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            color = Color(0xFFC5D0DD),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomInfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier
            .background(Color(0xFF08111D), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF1C3147), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = Color(0xFF9FB2C4), fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun smoothElevationSamples(points: List<ElevationSample>, radius: Int): List<ElevationSample> {
    if (points.size < 3 || radius <= 0) return points
    val weights = (0..radius).map { (radius + 1 - it).toDouble() }
    return points.mapIndexed { i, point ->
        var totalWeight = 0.0
        var elevation = 0.0
        for (j in -radius..radius) {
            val idx = (i + j).coerceIn(0, points.lastIndex)
            val w = weights[abs(j)]
            elevation += points[idx].elevationM * w
            totalWeight += w
        }
        point.copy(elevationM = elevation / totalWeight)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVehicleFacingRight(
    cx: Float,
    groundY: Float,
    caravan: Boolean,
    scale: Float = 1f
) {
    val s = scale.coerceAtLeast(0.5f)

    if (caravan) {
        val trailerLeft = cx - 82f * s
        val trailerTop = groundY - 28f * s
        drawRoundRect(
            color = Color(0xFFF5F7F8),
            topLeft = Offset(trailerLeft, trailerTop),
            size = Size(46f * s, 24f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * s, 8f * s)
        )
        drawRoundRect(
            color = Color(0xFF4E6B79),
            topLeft = Offset(trailerLeft + 8f * s, trailerTop + 5f * s),
            size = Size(14f * s, 9f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s, 3f * s)
        )
        drawRect(Color(0xFF8FA7B7), Offset(trailerLeft + 29f * s, trailerTop + 5f * s), Size(8f * s, 13f * s))
        drawLine(Color(0xFFD8DEE3), Offset(cx - 35f * s, groundY - 8f * s), Offset(cx - 23f * s, groundY - 8f * s), strokeWidth = 2.5f * s, cap = StrokeCap.Round)
        drawCircle(Color(0xFF111418), 5.7f * s, Offset(trailerLeft + 12f * s, groundY - 2f * s))
        drawCircle(Color(0xFF111418), 5.7f * s, Offset(trailerLeft + 35f * s, groundY - 2f * s))
        drawCircle(Color(0xFFC7D0D6), 2.4f * s, Offset(trailerLeft + 12f * s, groundY - 2f * s))
        drawCircle(Color(0xFFC7D0D6), 2.4f * s, Offset(trailerLeft + 35f * s, groundY - 2f * s))
    }

    val body = Path().apply {
        moveTo(cx - 18f * s, groundY - 18f * s)
        lineTo(cx - 7f * s, groundY - 30f * s)
        lineTo(cx + 18f * s, groundY - 30f * s)
        quadraticBezierTo(cx + 29f * s, groundY - 30f * s, cx + 36f * s, groundY - 21f * s)
        lineTo(cx + 40f * s, groundY - 15f * s)
        lineTo(cx + 40f * s, groundY - 5f * s)
        lineTo(cx + 34f * s, groundY - 1f * s)
        lineTo(cx - 13f * s, groundY - 1f * s)
        lineTo(cx - 22f * s, groundY - 7f * s)
        close()
    }
    drawPath(body, Color(0xFFF2F5F7))
    val rocker = Path().apply {
        moveTo(cx - 10f * s, groundY - 4f * s)
        lineTo(cx + 28f * s, groundY - 4f * s)
        lineTo(cx + 22f * s, groundY - 1f * s)
        lineTo(cx - 4f * s, groundY - 1f * s)
        close()
    }
    drawPath(rocker, Color(0xFFD1D9DE))

    drawRoundRect(
        color = Color(0xFF4F6D7D),
        topLeft = Offset(cx - 3f * s, groundY - 27f * s),
        size = Size(12f * s, 9f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s, 3f * s)
    )
    drawRoundRect(
        color = Color(0xFF4F6D7D),
        topLeft = Offset(cx + 11f * s, groundY - 27f * s),
        size = Size(13f * s, 9f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s, 3f * s)
    )
    drawLine(Color(0xFFB8C4CB), Offset(cx + 10f * s, groundY - 28f * s), Offset(cx + 10f * s, groundY - 18f * s), strokeWidth = 2f * s)
    drawRect(Color(0xFF2A3138), Offset(cx + 35f * s, groundY - 13f * s), Size(5.5f * s, 6f * s))
    drawCircle(Color(0xFFFFD463), 2.1f * s, Offset(cx + 40f * s, groundY - 12f * s))

    drawCircle(Color(0xFF0C1014), 6.2f * s, Offset(cx - 6f * s, groundY - 1f * s))
    drawCircle(Color(0xFF0C1014), 6.2f * s, Offset(cx + 25f * s, groundY - 1f * s))
    drawCircle(Color(0xFFBFC9D0), 2.5f * s, Offset(cx - 6f * s, groundY - 1f * s))
    drawCircle(Color(0xFFBFC9D0), 2.5f * s, Offset(cx + 25f * s, groundY - 1f * s))
}


/**
 * Rota ilerlemesini doğrudan GPS konumundan bulur.
 *
 * Rota hesaplanırken saklanan yol koordinatları arasından araca en yakın noktayı seçer
 * ve o noktanın ElevationSample.distanceKm değerini gerçek rota ilerlemesi olarak kullanır.
 * Böylece Kepez'den 100 km ilerlediğinizde araç da profil üzerinde yaklaşık 100. km'ye gelir.
 */
@Composable
private fun rememberLiveRouteDistanceKm(result: RouteProfileResult): Double {
    val context = LocalContext.current
    var progressKm by remember(result.routeTitle, result.totalDistanceKm) { mutableStateOf(0.0) }
    var hasRouteFix by remember(result.routeTitle, result.totalDistanceKm) { mutableStateOf(false) }

    DisposableEffect(context, result) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        fun applyLocation(location: Location?) {
            val loc = location ?: return
            val isMockTestLocation = loc.isMockRouteTestLocation()
            val nearest = nearestRouteDistanceKm(
                result = result,
                latitude = loc.latitude,
                longitude = loc.longitude,
                allowOffRouteSnap = isMockTestLocation
            ) ?: return

            // Test için geliştirici seçeneklerinden sahte/mock konum kullanılıyorsa
            // kilometre sıçrama korumasını bilinçli olarak atla. Böylece kullanıcı
            // rota üzerinde 100. km civarına sahte konum verdiğinde araç da anında
            // o kilometreye gider; geriye alınırsa test amacıyla geriye de dönebilir.
            if (isMockTestLocation) {
                progressKm = nearest.coerceIn(0.0, result.totalDistanceKm)
                hasRouteFix = true
                return
            }

            // Gerçek GPS'te ilk eşleşmede mevcut kilometreye gelebilir; sonraki fixlerde
            // büyük ileri sıçramaları ve küçük geri GPS oynamalarını engelle.
            val plausibleForwardJump = !hasRouteFix || nearest <= progressKm + 15.0
            if (plausibleForwardJump && nearest >= progressKm - 0.35) {
                progressKm = max(progressKm, nearest).coerceAtMost(result.totalDistanceKm)
                hasRouteFix = true
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = applyLocation(location)
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (locationManager != null && (hasFine || hasCoarse)) {
            runCatching {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
                    .forEach { provider ->
                        runCatching { locationManager.getLastKnownLocation(provider) }
                            .getOrNull()
                            ?.let(::applyLocation)
                        runCatching {
                            locationManager.requestLocationUpdates(
                                provider,
                                1_000L,
                                3f,
                                listener
                            )
                        }
                    }
            }
        }

        onDispose {
            runCatching { locationManager?.removeUpdates(listener) }
        }
    }

    return progressKm
}

private fun nearestRouteDistanceKm(
    result: RouteProfileResult,
    latitude: Double,
    longitude: Double,
    allowOffRouteSnap: Boolean = false
): Double? {
    val coordinates = result.routeCoordinates
    val points = result.points
    val count = min(coordinates.size, points.size)
    if (count == 0) return null
    if (count == 1) {
        val only = coordinates.first()
        val offRouteKm = haversineKm(latitude, longitude, only.first, only.second)
        return points.first().distanceKm.takeIf { offRouteKm <= 12.0 }
    }

    // GPS noktasını rota polylines'ındaki en yakın segment üzerine izdüşür.
    // Böylece araç 120 örnek nokta arasında 3-5 km'lik sıçramalarla değil,
    // sürekli ve yumuşak biçimde ilerler.
    var bestOffRouteKm = Double.MAX_VALUE
    var bestProgressKm = 0.0

    for (i in 0 until count - 1) {
        val a = coordinates[i]
        val b = coordinates[i + 1]
        val meanLatRad = Math.toRadians((a.first + b.first + latitude) / 3.0)
        val kmPerLatDegree = 111.32
        val kmPerLonDegree = 111.32 * cos(meanLatRad).coerceAtLeast(0.15)

        val ax = (a.second - longitude) * kmPerLonDegree
        val ay = (a.first - latitude) * kmPerLatDegree
        val bx = (b.second - longitude) * kmPerLonDegree
        val by = (b.first - latitude) * kmPerLatDegree

        val vx = bx - ax
        val vy = by - ay
        val lengthSq = vx * vx + vy * vy
        val t = if (lengthSq > 1e-9) {
            (-(ax * vx + ay * vy) / lengthSq).coerceIn(0.0, 1.0)
        } else 0.0

        val px = ax + vx * t
        val py = ay + vy * t
        val offRouteKm = sqrt(px * px + py * py)

        if (offRouteKm < bestOffRouteKm) {
            bestOffRouteKm = offRouteKm
            val d0 = points[i].distanceKm
            val d1 = points[i + 1].distanceKm
            bestProgressKm = d0 + (d1 - d0) * t
        }
    }

    // Gerçek GPS rota dışındaysa yanlış bir noktaya atlamasın.
    // Sahte konum testinde ise seçilen konumu en yakın rota segmentine snap et;
    // böylece test uygulamasında yolun tam üstüne dokunmak zorunda kalınmaz.
    if (!allowOffRouteSnap && bestOffRouteKm > 12.0) return null
    return bestProgressKm.coerceIn(0.0, result.totalDistanceKm)
}

@Suppress("DEPRECATION")
private fun Location.isMockRouteTestLocation(): Boolean = isFromMockProvider

private fun buildRouteProfile(
    currentLat: Double,
    currentLon: Double,
    destinationText: String,
    waypointText: String
): RouteProfileResult {
    val destination = geocode(destinationText)
    val waypoint = if (waypointText.isBlank()) null else geocode(waypointText)
    val originName = reverseGeocodeShortName(currentLat, currentLon)
    val destinationName = shortPlaceName(destinationText)
    val waypointName = waypointText.takeIf { it.isNotBlank() }?.let(::shortPlaceName)
    val routeData = fetchRoadRoute(
        currentLat = currentLat,
        currentLon = currentLon,
        waypoint = waypoint,
        destination = destination
    )
    val totalKm = routeData.totalDistanceKm
    val etaMin = routeData.etaMinutes
    val waypointDistanceKm = routeData.waypointDistanceKm
    val allPts = routeData.points
    if (allPts.size < 2) error("Rota geometrisi alınamadı")

    val step = (allPts.size / 120).coerceAtLeast(1)
    val sampledPts = allPts.indices.filter { it % step == 0 || it == allPts.size - 1 }.map { allPts[it] }
    val allElevsRaw = fetchBestRoadElevations(sampledPts)
    if (allElevsRaw.isEmpty()) error("Rakım verisi alınamadı")
    // DEM verisi yol tünelden geçtiğinde dağın üst yüzeyini okuyabilir.
    // Önce kısa sıçramaları temizle, sonra yol yüzeyine yakın alt-zarf filtresi uygula.
    val allElevs = roadSurfaceElevationProfile(allElevsRaw)

    val cumulativeKm = mutableListOf(0.0)
    for (i in 1 until sampledPts.size) {
        cumulativeKm += cumulativeKm.last() + haversineKm(
            sampledPts[i - 1].first,
            sampledPts[i - 1].second,
            sampledPts[i].first,
            sampledPts[i].second
        )
    }
    val sampledTotal = cumulativeKm.last().coerceAtLeast(0.001)
    val scale = totalKm / sampledTotal
    val scaledKm = cumulativeKm.map { it * scale }

    val rawSlopes = MutableList(allElevs.size) { 0.0 }
    for (i in 1 until allElevs.size) {
        val rise = allElevs[i] - allElevs[i - 1]
        val segKm = (scaledKm[i] - scaledKm[i - 1]).coerceAtLeast(0.001)
        rawSlopes[i] = ((rise / (segKm * 1000.0)) * 100.0).coerceIn(-20.0, 20.0)
    }
    val smoothedSlopes = MutableList(allElevs.size) { 0.0 }
    for (i in rawSlopes.indices) {
        val from = (i - 2).coerceAtLeast(0)
        val to = (i + 2).coerceAtMost(rawSlopes.size - 1)
        smoothedSlopes[i] = rawSlopes.subList(from, to + 1).average()
    }

    var climb = 0.0
    var maxSlope = 0.0
    val pts = mutableListOf(ElevationSample(0.0, allElevs.first(), 0.0))
    for (i in 1 until allElevs.size) {
        val rise = allElevs[i] - allElevs[i - 1]
        val slope = smoothedSlopes[i].coerceIn(-20.0, 20.0)
        if (rise > 0) climb += rise
        maxSlope = max(maxSlope, abs(slope))
        pts += ElevationSample(scaledKm[i], allElevs[i], slope)
    }
    return RouteProfileResult(
        destinationName = destinationName,
        points = pts,
        totalDistanceKm = totalKm,
        totalClimbM = climb.toInt(),
        etaMinutes = etaMin,
        maxSlopePct = maxSlope,
        maxElevationM = allElevs.maxOrNull() ?: 0.0,
        originName = originName,
        waypointName = waypointName,
        waypointDistanceKm = waypointDistanceKm,
        destinationDistanceKm = totalKm,
        routeCoordinates = sampledPts
    )
}

private data class RoadRouteData(
    val points: List<Pair<Double, Double>>,
    val totalDistanceKm: Double,
    val etaMinutes: Int,
    val waypointDistanceKm: Double?
)

private fun fetchRoadRoute(
    currentLat: Double,
    currentLon: Double,
    waypoint: Pair<Double, Double>?,
    destination: Pair<Double, Double>
): RoadRouteData {
    val tomTomKey = BuildConfig.TOMTOM_API_KEY.trim()
    if (tomTomKey.isNotBlank()) {
        runCatching {
            val locations = buildList {
                add(String.format(Locale.US, "%.6f,%.6f", currentLat, currentLon))
                waypoint?.let { add(String.format(Locale.US, "%.6f,%.6f", it.first, it.second)) }
                add(String.format(Locale.US, "%.6f,%.6f", destination.first, destination.second))
            }.joinToString(":")
            val key = URLEncoder.encode(tomTomKey, "UTF-8")
            val raw = httpGet(
                "https://api.tomtom.com/routing/1/calculateRoute/$locations/json" +
                    "?key=$key&traffic=false&travelMode=car&routeType=fastest&routeRepresentation=polyline"
            )
            val root = JSONObject(raw)
            val route = root.getJSONArray("routes").getJSONObject(0)
            val summary = route.getJSONObject("summary")
            val legs = route.getJSONArray("legs")
            val points = mutableListOf<Pair<Double, Double>>()
            for (li in 0 until legs.length()) {
                val leg = legs.getJSONObject(li)
                val arr = leg.optJSONArray("points") ?: continue
                for (pi in 0 until arr.length()) {
                    val o = arr.getJSONObject(pi)
                    val pt = o.getDouble("latitude") to o.getDouble("longitude")
                    if (points.lastOrNull() != pt) points += pt
                }
            }
            if (points.size < 2) error("TomTom rota noktaları eksik")
            RoadRouteData(
                points = points,
                totalDistanceKm = summary.getDouble("lengthInMeters") / 1000.0,
                etaMinutes = (summary.getDouble("travelTimeInSeconds") / 60.0).toInt(),
                waypointDistanceKm = if (waypoint != null && legs.length() >= 2) {
                    legs.getJSONObject(0).optJSONObject("summary")?.optDouble("lengthInMeters", 0.0)?.div(1000.0)
                } else null
            )
        }.getOrNull()?.let { return it }
    }

    val wpPart = waypoint?.let { ";${it.second},${it.first}" } ?: ""
    val routeUrl = "https://router.project-osrm.org/route/v1/driving/$currentLon,$currentLat$wpPart;${destination.second},${destination.first}?overview=full&geometries=polyline&steps=false"
    val root = JSONObject(httpGet(routeUrl))
    if (root.optString("code") != "Ok") error("Rota bulunamadı: ${root.optString("code")}")
    val route = root.getJSONArray("routes").getJSONObject(0)
    val legs = route.optJSONArray("legs")
    return RoadRouteData(
        points = decodePolyline(route.getString("geometry")),
        totalDistanceKm = route.getDouble("distance") / 1000.0,
        etaMinutes = (route.getDouble("duration") / 60.0).toInt(),
        waypointDistanceKm = if (waypoint != null && legs != null && legs.length() >= 2) {
            legs.getJSONObject(0).optDouble("distance", 0.0) / 1000.0
        } else null
    )
}

private fun fetchBestRoadElevations(coords: List<Pair<Double, Double>>): List<Double> {
    val srtm = runCatching { fetchElevations(coords) }.getOrNull()
    val copernicus = runCatching { fetchOpenMeteoElevations(coords) }.getOrNull()

    return when {
        srtm != null && copernicus != null && srtm.size == copernicus.size ->
            srtm.indices.map { i -> min(srtm[i], copernicus[i]) }
        copernicus != null -> copernicus
        srtm != null -> srtm
        else -> error("Rakım verisi alınamadı")
    }
}

private fun fetchOpenMeteoElevations(coords: List<Pair<Double, Double>>): List<Double> {
    val out = mutableListOf<Double>()
    for (batch in coords.chunked(100)) {
        val latitudes = batch.joinToString(",") { String.format(Locale.US, "%.6f", it.first) }
        val longitudes = batch.joinToString(",") { String.format(Locale.US, "%.6f", it.second) }
        val root = JSONObject(
            httpGet("https://api.open-meteo.com/v1/elevation?latitude=$latitudes&longitude=$longitudes")
        )
        val arr = root.optJSONArray("elevation") ?: error("Copernicus rakım verisi alınamadı")
        for (i in 0 until arr.length()) out += arr.optDouble(i, Double.NaN)
    }
    if (out.size != coords.size || out.any { it.isNaN() }) error("Copernicus rakım verisi eksik geldi")
    return out
}

private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val poly = mutableListOf<Pair<Double, Double>>()
    var index = 0; var lat = 0; var lng = 0
    while (index < encoded.length) {
        var b: Int; var shift = 0; var result = 0
        do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0; result = 0
        do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        poly += lat / 1e5 to lng / 1e5
    }
    return poly
}

private fun searchPlaceNames(query: String): List<String> {
    val enc = URLEncoder.encode(query, "UTF-8")
    val raw = httpGet("https://nominatim.openstreetmap.org/search?q=$enc&format=json&countrycodes=tr&limit=5&accept-language=tr&addressdetails=0")
    val arr = JSONArray(raw)
    return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("display_name")?.takeIf { it.isNotBlank() } }
}

private fun geocode(query: String): Pair<Double, Double> {
    val enc = URLEncoder.encode(query, "UTF-8")
    val raw = httpGet("https://nominatim.openstreetmap.org/search?q=$enc&format=json&countrycodes=tr&limit=1&accept-language=tr")
    val arr = JSONArray(raw)
    if (arr.length() == 0) error("Adres bulunamadı: $query")
    val o = arr.getJSONObject(0)
    return o.getDouble("lat") to o.getDouble("lon")
}

private fun fetchAheadElevation(lat: Double, lon: Double, bearingDeg: Double, maxKm: Double): List<ElevationSample> {
    val coords = (0..10).map { i -> destinationPoint(lat, lon, bearingDeg, maxKm * i / 10.0) }
    val elev = fetchElevations(coords)
    return coords.indices.map { i ->
        val d = maxKm * i / 10.0
        val slope = if (i == 0) 0.0 else {
            val seg = maxKm / 10.0
            ((elev[i] - elev[i - 1]) / (seg * 1000.0) * 100.0).coerceIn(-30.0, 30.0)
        }
        ElevationSample(d, elev[i], slope)
    }
}

private fun fetchElevations(coords: List<Pair<Double, Double>>): List<Double> {
    val out = mutableListOf<Double>()
    for (batch in coords.chunked(100)) {
        val locations = batch.joinToString("|") { "${it.first},${it.second}" }
        val enc = URLEncoder.encode(locations, "UTF-8")
        val root = JSONObject(httpGet("https://api.opentopodata.org/v1/srtm30m?locations=$enc"))
        if (root.optString("status") != "OK") error("Rakım verisi alınamadı")
        val results = root.getJSONArray("results")
        for (i in 0 until results.length()) {
            out += results.getJSONObject(i).optDouble("elevation", 0.0)
        }
    }
    if (out.size != coords.size) error("Rakım verisi eksik geldi")
    return out
}

private fun despikeElevations(values: List<Double>): List<Double> {
    if (values.size < 3) return values
    var out = values.toMutableList()
    repeat(2) {
        val next = out.toMutableList()
        for (i in out.indices) {
            val from = (i - 2).coerceAtLeast(0)
            val to = (i + 2).coerceAtMost(out.lastIndex)
            val window = out.subList(from, to + 1).sorted()
            val median = window[window.size / 2]
            if (kotlin.math.abs(out[i] - median) > 120.0) {
                next[i] = median
            }
        }
        out = next
    }
    return out
}

private fun smoothElevationLine(values: List<Double>, radius: Int): List<Double> {
    if (values.size < 3 || radius <= 0) return values
    return values.indices.map { i ->
        val from = (i - radius).coerceAtLeast(0)
        val to = (i + radius).coerceAtMost(values.lastIndex)
        values.subList(from, to + 1).average()
    }
}

private fun roadSurfaceElevationProfile(values: List<Double>): List<Double> {
    if (values.size < 5) return smoothElevationLine(despikeElevations(values), radius = 1)

    val base = smoothElevationLine(despikeElevations(values), radius = 2)
    val corrected = base.toMutableList()

    // Uzun yol profillerinde tünel/dağ yüzeyi gibi geniş DEM kabarmalarını yerel alt-zarfa çek.
    val radius = 11
    val allowanceM = 190.0
    for (i in base.indices) {
        val from = (i - radius).coerceAtLeast(0)
        val to = (i + radius).coerceAtMost(base.lastIndex)
        val window = base.subList(from, to + 1).sorted()
        val qIndex = ((window.lastIndex) * 0.30).toInt().coerceIn(0, window.lastIndex)
        val lowerRoadEnvelope = window[qIndex]
        corrected[i] = min(base[i], lowerRoadEnvelope + allowanceM)
    }

    // Tekil/sınırlı yüksek DEM bölgeleri gerçek yol geçidi değilse global dağılımdan kopar.
    // Yalnız üst %20'den çok kopan zirveleri kısar; gerçekten uzun süre yüksek rakımda giden rotaları korur.
    val sorted = corrected.sorted()
    val p80 = sorted[((sorted.lastIndex) * 0.80).toInt().coerceIn(0, sorted.lastIndex)]
    val robustCeiling = p80 + 150.0
    val maxValue = corrected.maxOrNull() ?: robustCeiling
    if (maxValue > robustCeiling + 180.0) {
        for (i in corrected.indices) {
            corrected[i] = min(corrected[i], robustCeiling)
        }
    }

    return smoothElevationLine(corrected, radius = 2)
}

private fun reverseGeocodeShortName(lat: Double, lon: Double): String {
    return runCatching {
        val encLat = String.format(Locale.US, "%.6f", lat)
        val encLon = String.format(Locale.US, "%.6f", lon)
        val raw = httpGet("https://nominatim.openstreetmap.org/reverse?lat=$encLat&lon=$encLon&format=json&accept-language=tr")
        val root = JSONObject(raw)
        val address = root.optJSONObject("address")
        listOf(
            address?.optString("city"),
            address?.optString("town"),
            address?.optString("county"),
            root.optString("display_name")
        ).firstOrNull { !it.isNullOrBlank() }?.let(::shortPlaceName) ?: "Başlangıç"
    }.getOrDefault("Başlangıç")
}

private fun shortPlaceName(raw: String?): String {
    val value = raw?.substringBefore(',')?.trim().orEmpty()
    return value.ifBlank { "Konum" }
}

private fun formatEtaFromNow(totalEtaMinutes: Int, totalDistanceKm: Double, selectedDistanceKm: Double): String {
    val remainingRatio = (1.0 - (selectedDistanceKm / totalDistanceKm.coerceAtLeast(0.001))).coerceIn(0.0, 1.0)
    val remainingMinutes = (totalEtaMinutes * remainingRatio).toLong()
    val arrival = java.time.LocalTime.now().plusMinutes(remainingMinutes)
    return arrival.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

private fun httpGet(url: String): String {
    var lastError: Throwable? = null
    repeat(4) { attempt ->
        var c: HttpURLConnection? = null
        try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 22000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "ALD-DRIVE/1.0 Android")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Sunucu hatası $code")
            return text
        } catch (error: Throwable) {
            lastError = error
            if (attempt < 3) Thread.sleep(700L + attempt * 500L)
        } finally {
            c?.disconnect()
        }
    }
    // Eski ALD uygulamasında sahada çalışan en sade URL.readText yolunu son kez dene.
    runCatching { return URL(url).readText(Charsets.UTF_8) }
    val raw = lastError?.message.orEmpty()
    if (raw.contains("EPERM", ignoreCase = true) || raw.contains("Operation not permitted", ignoreCase = true)) {
        error("Araç Android'i ağ soketini reddetti (EPERM). İnternet/uygulama ağ iznini kontrol edip tekrar deneyin.")
    }
    throw lastError ?: IllegalStateException("Ağ bağlantısı kurulamadı")
}

private fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, km: Double): Pair<Double, Double> {
    val earthKm = 6371.0088
    val br = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lon)
    val ad = km / earthKm
    val lat2 = asin(sin(lat1) * cos(ad) + cos(lat1) * sin(ad) * cos(br))
    val lon2 = lon1 + atan2(sin(br) * sin(ad) * cos(lat1), cos(ad) - sin(lat1) * sin(lat2))
    return Math.toDegrees(lat2) to Math.toDegrees(lon2)
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0088
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
