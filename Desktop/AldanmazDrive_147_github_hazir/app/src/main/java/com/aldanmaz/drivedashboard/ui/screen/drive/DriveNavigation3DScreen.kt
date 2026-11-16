package com.aldanmaz.drivedashboard.ui.screen.drive

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aldanmaz.drivedashboard.BuildConfig
import com.aldanmaz.drivedashboard.nav.FilteredFix
import com.aldanmaz.drivedashboard.nav.GeoPoint
import com.aldanmaz.drivedashboard.nav.GpsFilter
import com.aldanmaz.drivedashboard.nav.ManeuverKind
import com.aldanmaz.drivedashboard.nav.NavFormat
import com.aldanmaz.drivedashboard.nav.NavGeo
import com.aldanmaz.drivedashboard.nav.NavMapController
import com.aldanmaz.drivedashboard.nav.NavMapState
import com.aldanmaz.drivedashboard.nav.NavMapView
import com.aldanmaz.drivedashboard.nav.NavViewModel
import com.aldanmaz.drivedashboard.nav.PoseSmoother
import com.aldanmaz.drivedashboard.nav.RouteState
import com.aldanmaz.drivedashboard.nav.SearchState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale

private enum class CamMode { FOLLOW, OVERVIEW, FREE }

private val PanelColor = Color(0xE60E151C)
private val ChipColor = Color(0xE61B2630)
private val Accent = Color(0xFF28D7FF)
private val SoftText = Color(0xFFCAD6E0)
private val Good = Color(0xFF52E69A)
private val Warn = Color(0xFFFFC45C)
private val Bad = Color(0xFFFF6B6B)

/**
 * Tesla'dan esinlenen (ozgun tasarim) 3B surus ve navigasyon ekrani.
 *
 * Yerel MapLibre haritasi + gercek GPS + Photon/Nominatim arama + Valhalla/OSRM rota.
 * Eski WebView/CDN surumunun yerini alir; imza ve cagiran taraf degismedi.
 * Hiz yalnizca km/sa; sahte konum gosterilmez.
 */
@Composable
internal fun DriveNavigation3DScreen(
    latitude: Double?, longitude: Double?, speedKmh: Int, currentTime: String,
    vehicleKm: Double, headingDegrees: Float?, direction: String,
    locationStatus: String, speedLimitKmh: Int?, isGpsActive: Boolean, onBack: () -> Unit
) {
    val vm: NavViewModel = viewModel()
    val controller = remember { NavMapController() }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current.density

    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val routeState by vm.routeState.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val rerouting by vm.rerouting.collectAsStateWithLifecycle()

    var mapState by remember { mutableStateOf<NavMapState>(NavMapState.Loading) }
    var retryToken by remember { mutableIntStateOf(0) }
    var camMode by remember { mutableStateOf(CamMode.FOLLOW) }
    var gestureAtMs by remember { mutableStateOf(0L) }
    var fix by remember { mutableStateOf<FilteredFix?>(null) }
    var lastFixAtMs by remember { mutableStateOf(0L) }
    var gpsStale by remember { mutableStateOf(false) }
    var roadName by remember { mutableStateOf<String?>(null) }

    val mapEpoch: Int? = when (val s = mapState) {
        is NavMapState.Ready -> s.epoch
        is NavMapState.Degraded -> s.epoch
        else -> null
    }

    val fixNow by rememberUpdatedState(fix)
    val progressNow by rememberUpdatedState(progress)
    val camNow by rememberUpdatedState(camMode)
    val gestureNow by rememberUpdatedState(gestureAtMs)

    // ---------- GPS: filtrele, ViewModel'e ve haritaya ilet ----------
    val filter = remember { GpsFilter() }
    val trail = remember { ArrayList<GeoPoint>() }
    val lastRaw = remember { doubleArrayOf(Double.NaN, Double.NaN) }
    LaunchedEffect(latitude, longitude, speedKmh, headingDegrees) {
        val la = latitude ?: return@LaunchedEffect
        val lo = longitude ?: return@LaunchedEffect
        val f = filter.accept(la, lo, speedKmh, headingDegrees, SystemClock.elapsedRealtime()) ?: return@LaunchedEffect
        fix = f
        if (la != lastRaw[0] || lo != lastRaw[1]) {
            lastRaw[0] = la; lastRaw[1] = lo
            lastFixAtMs = SystemClock.elapsedRealtime()
            gpsStale = false
            vm.onPosition(GeoPoint(f.lat, f.lon))
            val p = GeoPoint(f.lat, f.lon)
            if (trail.isEmpty() || NavGeo.haversine(trail.last(), p) >= 6.0) {
                trail.add(p)
                if (trail.size > 500) trail.removeAt(0)
                controller.setTrail(trail)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            gpsStale = lastFixAtMs != 0L && SystemClock.elapsedRealtime() - lastFixAtMs > 10_000
        }
    }

    // ---------- harita katmanlarini veriyle senkronla (stil her kuruldugunda) ----------
    LaunchedEffect(mapEpoch, routeState) {
        if (mapEpoch == null) return@LaunchedEffect
        val rs = routeState
        controller.setRoute(if (rs is RouteState.Ready) rs.route.points else null)
    }
    LaunchedEffect(mapEpoch, destination) {
        if (mapEpoch == null) return@LaunchedEffect
        controller.setDestination(destination?.let { GeoPoint(it.lat, it.lon) })
    }
    LaunchedEffect(mapEpoch) {
        if (mapEpoch != null) controller.setTrail(trail)
    }

    // ---------- yeni rota: once tum rotayi goster, sonra surus kamerasina don ----------
    val readyEpoch = (routeState as? RouteState.Ready)?.epoch
    LaunchedEffect(readyEpoch, mapEpoch) {
        val rs = routeState as? RouteState.Ready ?: return@LaunchedEffect
        if (mapEpoch == null) return@LaunchedEffect
        camMode = CamMode.OVERVIEW
        val left = (24 * density).toInt()
        controller.fitRoute(rs.route.points, left, (250 * density).toInt(), (24 * density).toInt(), (210 * density).toInt())
        delay(3_800)
        if (camMode == CamMode.OVERVIEW) {
            controller.applyFollowPadding()
            camMode = CamMode.FOLLOW
        }
    }

    // ---------- kare dongusu: arac + kamera (yaklasik 30 fps) ----------
    LaunchedEffect(mapEpoch) {
        if (mapEpoch == null) return@LaunchedEffect
        val smoother = PoseSmoother()
        var lastNs = 0L
        var lastDrawNs = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val f = fixNow
                if (f != null) {
                    val dt = if (lastNs == 0L) 0.016 else (now - lastNs) / 1_000_000_000.0
                    lastNs = now
                    val targetZoom = PoseSmoother.zoomForSpeed(f.speedKmh, progressNow?.distToNextM)
                    smoother.step(dt, f.lat, f.lon, f.headingDeg, targetZoom)
                    if (now - lastDrawNs >= 33_000_000L) {
                        lastDrawNs = now
                        val mode = camNow
                        val zoomForScale = if (mode == CamMode.FOLLOW) smoother.zoom else (controller.currentZoom() ?: smoother.zoom)
                        controller.setVehicle(smoother.lat, smoother.lon, smoother.heading, zoomForScale)
                        if (mode == CamMode.FOLLOW) {
                            controller.moveFollow(smoother.lat, smoother.lon, smoother.heading, smoother.zoom)
                        } else if (mode == CamMode.FREE && SystemClock.elapsedRealtime() - gestureNow > 15_000L) {
                            controller.applyFollowPadding()
                            camMode = CamMode.FOLLOW   // Tesla gibi: bir sure sonra otomatik geri don
                        }
                    }
                }
            }
        }
    }

    // ---------- yol adi: rota varsa rotadan, yoksa harita karolarindan ----------
    LaunchedEffect(mapEpoch) {
        if (mapEpoch == null) return@LaunchedEffect
        while (true) {
            delay(1_300)
            val f = fixNow ?: continue
            val fromRoute = progressNow?.currentStreet?.takeIf { it.isNotBlank() }
            val n = fromRoute ?: controller.roadNameAt(f.lat, f.lon, SystemClock.elapsedRealtime())
            if (n != roadName) roadName = n
        }
    }

    // ---------- panel gorunurlugu ----------
    // Hedef girilirken / arama-rota islemi sürerken paneller bekler (gizlenmez).
    // Aksi halde ekrana dokunulunca paneller acilir ve 3 sn sonra gizlenir; geriye yalnizca hiz ve saat kalir.
    var panelsRevealed by remember { mutableStateOf(true) }
    var touchTick by remember { mutableIntStateOf(0) }
    var searchFocused by remember { mutableStateOf(false) }
    val holdPanels = searchFocused ||
        search !is SearchState.Idle ||
        routeState is RouteState.Loading || routeState is RouteState.WaitingForGps || routeState is RouteState.Error ||
        mapState is NavMapState.Failed ||
        (destination == null && query.isNotBlank())
    LaunchedEffect(touchTick, holdPanels, panelsRevealed) {
        if (panelsRevealed && !holdPanels) {
            delay(3_000)
            panelsRevealed = false
        }
    }
    val panelsVisible = holdPanels || panelsRevealed

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF0B1118)).pointerInput(Unit) {
            // Dokunusu tuketmeden izler: harita jestleri aynen calisir.
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.type == PointerEventType.Press) {
                        panelsRevealed = true
                        touchTick++
                    }
                }
            }
        }
    ) {
        val landscape = maxWidth > maxHeight * 1.15f
        val panelModifier = if (landscape) Modifier.width(440.dp) else Modifier.fillMaxWidth()

        NavMapView(
            controller = controller,
            styleUrl = BuildConfig.NAV_STYLE_URL,
            retryToken = retryToken,
            onState = { mapState = it },
            onUserGesture = {
                if (camMode == CamMode.FOLLOW || camMode == CamMode.OVERVIEW) camMode = CamMode.FREE
                gestureAtMs = SystemClock.elapsedRealtime()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ======== ust panel ========
        AnimatedVisibility(
            visible = panelsVisible, modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(), exit = fadeOut()
        ) {
        Column(
            panelModifier.background(PanelColor).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
                Column(Modifier.weight(1f)) {
                    Text("3D SÜRÜŞ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Beyaz Dacia Sandero Stepway • 07 MTU 93", color = SoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currentTime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val gpsOk = isGpsActive && latitude != null && !gpsStale
                    Text(
                        when {
                            latitude == null -> "GPS BEKLENİYOR"
                            gpsStale -> "GPS ZAYIF"
                            gpsOk -> "GPS AKTİF"
                            else -> "GPS BEKLENİYOR"
                        },
                        color = if (gpsOk) Good else Warn, fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.setQuery(it) },
                    modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused },
                    singleLine = true,
                    placeholder = { Text("Şehir, ilçe, sokak, adres veya yer…", color = Color.LightGray, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchNow(); focus.clearFocus() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { vm.setQuery(""); vm.dismissSearch() }) { Text("×", color = SoftText, fontSize = 20.sp) }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF728493), cursorColor = Color.White
                    )
                )
                Button(
                    onClick = { vm.searchNow(); focus.clearFocus() },
                    enabled = query.trim().length >= 2 && search !is SearchState.Loading
                ) { Text("ARA", fontWeight = FontWeight.Bold) }
            }

            when (val s = search) {
                is SearchState.Idle -> Unit
                is SearchState.Loading -> StatusRow(loading = true, text = "Aranıyor: ${s.query}", color = SoftText)
                is SearchState.Empty -> StatusRow(
                    loading = false, color = Warn,
                    text = "“${s.query}” için sonuç bulunamadı. Yazımı kontrol edin veya daha genel bir ad deneyin."
                )
                is SearchState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(loading = false, text = s.message, color = Bad)
                    TextButton(onClick = { vm.retrySearch() }) { Text("TEKRAR DENE", color = Accent, fontWeight = FontWeight.Bold) }
                }
                is SearchState.Results -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = if (landscape) 220.dp else 240.dp)
                        .background(ChipColor, RoundedCornerShape(10.dp))
                ) {
                    items(s.places) { place ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { focus.clearFocus(); vm.selectPlace(place) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(place.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.subtitle.isNotBlank()) {
                                Text(place.subtitle, color = SoftText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            when (val r = routeState) {
                is RouteState.Loading -> StatusRow(loading = true, text = "Rota hesaplanıyor…", color = SoftText)
                is RouteState.WaitingForGps -> StatusRow(
                    loading = true, color = Warn,
                    text = "GPS konumu bekleniyor — konum alınınca rota hesaplanacak. ${if (latitude == null) locationStatus else ""}".trim()
                )
                is RouteState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(loading = false, text = r.message, color = Bad)
                    Row {
                        TextButton(onClick = { vm.retryRoute() }) { Text("TEKRAR DENE", color = Accent, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { vm.cancelRoute() }) { Text("İPTAL", color = SoftText) }
                    }
                }
                else -> Unit
            }

            val prog = progress
            if (routeState is RouteState.Ready && prog != null) {
                val step = prog.nextStep
                Row(
                    Modifier.fillMaxWidth().background(ChipColor, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ManeuverIcon(step?.kind ?: ManeuverKind.STRAIGHT, Modifier.size(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (prog.arrived) "Hedefe ulaştınız" else NavFormat.distance(prog.distToNextM),
                            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black
                        )
                        if (!prog.arrived) {
                            Text(step?.instruction.orEmpty(), color = SoftText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        }

        // ======== yeniden ortala ========
        if (panelsVisible && camMode != CamMode.FOLLOW && fix != null) {
            Button(
                onClick = { controller.applyFollowPadding(); camMode = CamMode.FOLLOW },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = if (landscape) 24.dp else 230.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF06222B))
            ) { Text("ORTALA", fontWeight = FontWeight.Bold) }
        }

        // ======== alt panel ========
        AnimatedVisibility(
            visible = panelsVisible, modifier = Modifier.align(Alignment.BottomStart),
            enter = fadeIn(), exit = fadeOut()
        ) {
        Column(
            panelModifier.padding(10.dp)
                .background(PanelColor, RoundedCornerShape(12.dp)).padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.weight(1f), color = ChipColor, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(speedKmh.coerceAtLeast(0).toString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Text(" km/h", color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                if (speedLimitKmh != null && speedLimitKmh > 0) InfoChip("SINIR", speedLimitKmh.toString())
                InfoChip("YÖN", direction.ifBlank { "--" })
                InfoChip("ARAÇ KM", if (vehicleKm > 0) String.format(Locale.forLanguageTag("tr-TR"), "%,.0f", vehicleKm) else "—")
            }

            val road = roadName
            if (!road.isNullOrBlank() || rerouting) {
                Text(
                    if (rerouting) "Rota yeniden hesaplanıyor…" else road.orEmpty(),
                    color = if (rerouting) Warn else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            val prog = progress
            val rs = routeState
            if (rs is RouteState.Ready && prog != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${NavFormat.distance(prog.remainingM)} • ${NavFormat.duration(prog.remainingS)}",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Varış ${NavFormat.eta(LocalTime.now(), prog.remainingS)} • ${destination?.name.orEmpty()}",
                            color = SoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { vm.cancelRoute() }) {
                        Text(if (prog.arrived) "TAMAM" else "İPTAL", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (val ms = mapState) {
                is NavMapState.Loading -> StatusRow(loading = true, text = "Harita yükleniyor…", color = Warn)
                is NavMapState.Ready -> Text(
                    if (ms.buildings) "Harita hazır • 3D binalar açık" else "Harita hazır • 3D bina verisi bu stilde yok",
                    color = Good, fontSize = 10.sp
                )
                is NavMapState.Degraded -> Column {
                    Text(ms.reason, color = Warn, fontSize = 10.sp)
                    TextButton(onClick = { retryToken++ }) { Text("HARİTAYI TEKRAR DENE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                is NavMapState.Failed -> Column {
                    Text(ms.reason, color = Bad, fontSize = 10.sp)
                    TextButton(onClick = { retryToken++ }) { Text("TEKRAR DENE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                when {
                    latitude == null -> locationStatus
                    gpsStale -> "GPS sinyali zayıf veya kayıp — son bilinen konum gösteriliyor"
                    else -> String.format(Locale.US, "Canlı GPS %.5f, %.5f", latitude, longitude ?: 0.0)
                },
                color = if (latitude == null || gpsStale) Warn else SoftText, fontSize = 10.sp
            )
            Text("© OpenStreetMap katkıcıları • OpenFreeMap • MapLibre", color = Color(0xFF8DA0B0), fontSize = 9.sp)
        }
        }

        // ======== sadece hiz + saat (paneller gizliyken) ========
        AnimatedVisibility(
            visible = !panelsVisible,
            modifier = Modifier.align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.padding(14.dp).background(PanelColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(speedKmh.coerceAtLeast(0).toString(), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
                    Text(" km/h", color = SoftText, fontSize = 14.sp, modifier = Modifier.padding(bottom = 9.dp))
                }
                Text(currentTime, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusRow(loading: Boolean, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(color = ChipColor, shape = RoundedCornerShape(9.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = SoftText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Manevra oku: font/ikon paketine bagimli degil, Canvas ile cizilir. */
@Composable
private fun ManeuverIcon(kind: ManeuverKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        if (kind == ManeuverKind.ARRIVE) {
            drawCircle(Color(0xFFFF4D5A), radius = w * 0.26f, center = c)
            drawCircle(Color.White, radius = w * 0.10f, center = c)
        } else {
            if (kind == ManeuverKind.ROUNDABOUT) {
                drawCircle(Accent, radius = w * 0.24f, center = c, style = Stroke(width = w * 0.07f))
            }
            rotate(kind.angleDeg, pivot = c) {
                val shaft = Path().apply { moveTo(w * 0.5f, h * 0.88f); lineTo(w * 0.5f, h * 0.38f) }
                drawPath(shaft, Color.White, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
                val head = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.82f, h * 0.46f); lineTo(w * 0.18f, h * 0.46f); close()
                }
                drawPath(head, Color.White)
            }
        }
    }
}
