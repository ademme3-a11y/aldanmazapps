package com.aldanmaz.drivedashboard.ui.screen.drive

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aldanmaz.drivedashboard.BuildConfig
import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.nav.FilteredFix
import com.aldanmaz.drivedashboard.nav.GeoPoint
import com.aldanmaz.drivedashboard.nav.GpsFilter
import com.aldanmaz.drivedashboard.nav.ManeuverKind
import com.aldanmaz.drivedashboard.nav.NavFormat
import com.aldanmaz.drivedashboard.nav.NavGeo
import com.aldanmaz.drivedashboard.nav.NavMapController
import com.aldanmaz.drivedashboard.nav.NavMapState
import com.aldanmaz.drivedashboard.nav.NavMapView
import com.aldanmaz.drivedashboard.nav.NavVehiclePhoto
import com.aldanmaz.drivedashboard.nav.NavViewModel
import com.aldanmaz.drivedashboard.nav.PoseSmoother
import com.aldanmaz.drivedashboard.nav.RouteState
import com.aldanmaz.drivedashboard.nav.SunCalc
import com.aldanmaz.drivedashboard.nav.SearchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

private enum class CamMode { FOLLOW, OVERVIEW, FREE }

/** Gece (koyu) ve gunduz (acik, Tesla tarzi) arayuz renkleri. */
private class NavColors(
    val day: Boolean, val panel: Color, val chip: Color, val text: Color, val soft: Color,
    val accent: Color, val onAccent: Color, val edge: Color, val muted: Color,
    val good: Color, val warn: Color, val bad: Color, val hudShadow: Shadow, val mapBg: Color
)

private val NightColors = NavColors(
    day = false, panel = Color(0xF50E151C), chip = Color(0xF21B2630), text = Color.White, soft = Color(0xFFDCE6EE),
    accent = Color(0xFF28D7FF), onAccent = Color(0xFF06222B), edge = Color(0xFF728493), muted = Color(0xFF8DA0B0),
    good = Color(0xFF52E69A), warn = Color(0xFFFFC45C), bad = Color(0xFFFF6B6B),
    // Hiz/saat arkasinda kutu yok: harita gorunur kalir, okunurluk golge ile saglanir.
    hudShadow = Shadow(Color(0xE6000000), Offset(0f, 4f), 16f), mapBg = Color(0xFF0B1118)
)

private val DayColors = NavColors(
    day = true, panel = Color(0xF5F6F8FA), chip = Color(0xFFE3E8ED), text = Color(0xFF171A20), soft = Color(0xFF4A5561),
    accent = Color(0xFF3E6AE1), onAccent = Color.White, edge = Color(0xFF8C98A4), muted = Color(0xFF6B7783),
    good = Color(0xFF1E8E57), warn = Color(0xFFB36B00), bad = Color(0xFFC62828),
    hudShadow = Shadow(Color(0xF2FFFFFF), Offset(0f, 0f), 22f), mapBg = Color(0xFFE8EBEF)
)

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

    // ---------- tema: otomatik (gun dogumu/batimi), gunduz veya gece ----------
    val appCtx = LocalContext.current
    val prefs = remember { appCtx.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableIntStateOf(runCatching { prefs.getInt("theme_mode", 0) }.getOrDefault(0)) }
    var arrowMode by remember { mutableStateOf(runCatching { prefs.getBoolean("arrow_mode", false) }.getOrDefault(false)) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); nowMs = System.currentTimeMillis() } }
    val daylight = SunCalc.isDaylight(nowMs, fix?.lat ?: latitude, fix?.lon ?: longitude, LocalTime.now().hour)
    val isDay = when (themeMode) { 1 -> true; 2 -> false; else -> daylight }
    val C = if (isDay) DayColors else NightColors
    LaunchedEffect(isDay, mapEpoch) { controller.setTheme(isDay) }

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
                    // Rotadayken arac/kamera seridin ortasina (rota cizgisine) ve yol yonune kilitlenir;
                    // GPS yanal sapmasi aracin saga/sola kaymis gorunmesine yol acmaz.
                    val pr = progressNow
                    val locked = pr != null && !pr.offRoute && !pr.arrived && pr.offsetM < 30.0 && !pr.snapLat.isNaN()
                    val tLat = if (locked) pr!!.snapLat else f.lat
                    val tLon = if (locked) pr!!.snapLon else f.lon
                    val tHead = if (locked && !pr!!.routeBearing.isNaN() && f.speedKmh >= 3) pr.routeBearing else f.headingDeg
                    smoother.step(dt, tLat, tLon, tHead, targetZoom)
                    if (now - lastDrawNs >= 33_000_000L) {
                        lastDrawNs = now
                        val mode = camNow
                        val zoomForScale = if (mode == CamMode.FOLLOW) smoother.zoom else (controller.currentZoom() ?: smoother.zoom)
                        if (mode == CamMode.FOLLOW) controller.clearVehicle()
                        else controller.setVehicle(smoother.lat, smoother.lon, smoother.heading, zoomForScale)
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

    // ---------- arac gorseli (kullanicinin kendi arka fotografi varsa o, yoksa varsayilan cizim) ----------
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var navPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(photoVersion) { navPhoto = NavVehiclePhoto.load(ctx) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoStatus = "Fotoğraf işleniyor…"
            NavVehiclePhoto.import(ctx, uri) { res ->
                scope.launch {
                    res.onSuccess { photoStatus = "Fotoğraf eklendi"; photoVersion++ }
                        .onFailure { photoStatus = "Fotoğraf işlenemedi: ${it.message ?: "bilinmeyen hata"}" }
                }
            }
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
        Modifier.fillMaxSize().background(C.mapBg).pointerInput(Unit) {
            // Dokunusu tuketmeden izler: harita jestleri aynen calisir.
            // Tek dokunus bilgi girisini ACMAZ; yalnizca CIFT dokunus acar. Paneller aciksken her dokunus gizleme sayacini uzatir.
            val slop = 56.dp.toPx()
            awaitPointerEventScope {
                var lastTapAt = 0L
                var lastX = 0f
                var lastY = 0f
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.type != PointerEventType.Press) continue
                    if (panelsRevealed) touchTick++
                    if (ev.changes.count { it.pressed } > 1) { lastTapAt = 0L; continue }
                    val ch = ev.changes.firstOrNull() ?: continue
                    val dx = ch.position.x - lastX
                    val dy = ch.position.y - lastY
                    if (lastTapAt != 0L && ch.uptimeMillis - lastTapAt <= 350L && dx * dx + dy * dy <= slop * slop) {
                        panelsRevealed = true
                        touchTick++
                        lastTapAt = 0L
                    } else {
                        lastTapAt = ch.uptimeMillis; lastX = ch.position.x; lastY = ch.position.y
                    }
                }
            }
        }
    ) {
        val landscape = maxWidth > maxHeight * 1.15f
        val screenH = maxHeight   // ic ice Column/LazyColumn kapsamlarinda dogrudan maxHeight kullanilamaz
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

        // ======== arac (takip kamerasinda arkadan gorunum); dokununca Sandero <-> kirmizi 3B ok ========
        if (camMode == CamMode.FOLLOW && fix != null && mapEpoch != null) {
            val photo = navPhoto
            val ratio = photo?.let { it.width.toFloat() / it.height } ?: (574f / 337f)
            val carW = (minOf(maxWidth, maxHeight).value * 0.30f).coerceIn(110f, 240f).dp
            val carH = minOf(carW / ratio, maxHeight * 0.30f)
            val boxW = if (arrowMode) carH * 1.15f else carW
            val boxH = if (arrowMode) carH * 1.3f else carH
            val boxY = if (arrowMode) maxHeight * 0.71f - boxH * 0.5f else maxHeight * 0.71f - carH * 0.45f
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset(x = (maxWidth - boxW) / 2, y = boxY)
                    .size(boxW, boxH)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        arrowMode = !arrowMode
                        runCatching { prefs.edit().putBoolean("arrow_mode", arrowMode).apply() }
                    }
            ) {
                if (arrowMode) {
                    NavArrow(Modifier.fillMaxSize())
                } else if (photo != null) {
                    Image(bitmap = photo, contentDescription = "Araç", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Image(
                        painter = painterResource(R.drawable.vehicle_nav_rear_default),
                        contentDescription = "Dacia Sandero Stepway 07 MTU 93",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ======== ust panel ========
        AnimatedVisibility(
            visible = panelsVisible, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding(),
            enter = fadeIn(), exit = fadeOut()
        ) {
        Column(
            panelModifier.background(C.panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", color = C.text, fontSize = 30.sp) }
                Column(Modifier.weight(1f).padding(end = if (landscape) 0.dp else 140.dp)) {
                    Text("3D SÜRÜŞ", color = C.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Beyaz Dacia Sandero Stepway 2017 Dizel • 07 MTU 93", color = C.soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.setQuery(it) },
                    modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused },
                    singleLine = true,
                    placeholder = { Text("Şehir, ilçe, sokak, adres veya yer…", color = C.soft, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchNow(); focus.clearFocus() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { vm.setQuery(""); vm.dismissSearch() }) { Text("×", color = C.soft, fontSize = 20.sp) }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.text, unfocusedTextColor = C.text,
                        focusedBorderColor = C.accent, unfocusedBorderColor = C.edge, cursorColor = C.text
                    )
                )
                Button(
                    onClick = { vm.searchNow(); focus.clearFocus() },
                    enabled = query.trim().length >= 2 && search !is SearchState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent)
                ) { Text("ARA", fontWeight = FontWeight.Bold) }
            }

            when (val s = search) {
                is SearchState.Idle -> Unit
                is SearchState.Loading -> StatusRow(C, loading = true, text = "Aranıyor: ${s.query}", color = C.soft)
                is SearchState.Empty -> StatusRow(
                    c = C, loading = false, color = C.warn,
                    text = "“${s.query}” için sonuç bulunamadı. Yazımı kontrol edin veya daha genel bir ad deneyin."
                )
                is SearchState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(C, loading = false, text = s.message, color = C.bad)
                    TextButton(onClick = { vm.retrySearch() }) { Text("TEKRAR DENE", color = C.accent, fontWeight = FontWeight.Bold) }
                }
                is SearchState.Results -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = if (landscape) (screenH - 260.dp).coerceAtLeast(120.dp) else minOf(240.dp, screenH * 0.3f))
                        .background(C.chip, RoundedCornerShape(10.dp))
                ) {
                    items(s.places) { place ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { focus.clearFocus(); vm.selectPlace(place) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(place.name, color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.subtitle.isNotBlank()) {
                                Text(place.subtitle, color = C.soft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            when (val r = routeState) {
                is RouteState.Loading -> StatusRow(C, loading = true, text = "Rota hesaplanıyor…", color = C.soft)
                is RouteState.WaitingForGps -> StatusRow(
                    c = C, loading = true, color = C.warn,
                    text = "GPS konumu bekleniyor — konum alınınca rota hesaplanacak. ${if (latitude == null) locationStatus else ""}".trim()
                )
                is RouteState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(C, loading = false, text = r.message, color = C.bad)
                    Row {
                        TextButton(onClick = { vm.retryRoute() }) { Text("TEKRAR DENE", color = C.accent, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { vm.cancelRoute() }) { Text("İPTAL", color = C.soft) }
                    }
                }
                else -> Unit
            }

            val prog = progress
            if (routeState is RouteState.Ready && prog != null) {
                val step = prog.nextStep
                Row(
                    Modifier.fillMaxWidth().background(C.chip, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ManeuverIcon(C, step?.kind ?: ManeuverKind.STRAIGHT, Modifier.size(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (prog.arrived) "Hedefe ulaştınız" else NavFormat.distance(prog.distToNextM),
                            color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Black
                        )
                        if (!prog.arrived) {
                            Text(step?.instruction.orEmpty(), color = C.soft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                modifier = Modifier.align(if (landscape) Alignment.BottomCenter else Alignment.BottomEnd).safeDrawingPadding().padding(end = 12.dp, bottom = if (landscape) 24.dp else 230.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent)
            ) { Text("ORTALA", fontWeight = FontWeight.Bold) }
        }

        // ======== alt panel ========
        AnimatedVisibility(
            visible = panelsVisible, modifier = Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomStart).safeDrawingPadding(),
            enter = fadeIn(), exit = fadeOut()
        ) {
        Column(
            (if (landscape) Modifier.width(400.dp) else Modifier.fillMaxWidth()).padding(10.dp)
                .background(C.panel, RoundedCornerShape(12.dp)).padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.weight(1f), color = C.chip, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(speedKmh.coerceAtLeast(0).toString(), color = C.text, fontSize = 68.sp, fontWeight = FontWeight.Black)
                        Text(" km/h", color = C.soft, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                if (speedLimitKmh != null && speedLimitKmh > 0) InfoChip(C, "SINIR", speedLimitKmh.toString())
                InfoChip(C, "YÖN", direction.ifBlank { "--" })
                InfoChip(C, "ARAÇ KM", if (vehicleKm > 0) String.format(Locale.forLanguageTag("tr-TR"), "%,.0f", vehicleKm) else "—")
            }

            val road = roadName
            if (!road.isNullOrBlank() || rerouting) {
                Text(
                    if (rerouting) "Rota yeniden hesaplanıyor…" else road.orEmpty(),
                    color = if (rerouting) C.warn else C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
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
                            color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Varış ${NavFormat.eta(LocalTime.now(), prog.remainingS)} • ${destination?.name.orEmpty()}",
                            color = C.soft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { vm.cancelRoute() }) {
                        Text(if (prog.arrived) "TAMAM" else "İPTAL", color = C.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    photoStatus ?: if (arrowMode) "Araç: kırmızı ok (araca dokunun)" else if (navPhoto != null) "Araç: kendi fotoğrafınız (araca dokunun: ok)" else "Araç: Sandero (araca dokunun: ok)",
                    color = C.soft, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = {
                    themeMode = (themeMode + 1) % 3
                    runCatching { prefs.edit().putInt("theme_mode", themeMode).apply() }
                }) {
                    Text(
                        when (themeMode) { 1 -> "TEMA: GÜNDÜZ"; 2 -> "TEMA: GECE"; else -> if (isDay) "TEMA: OTOMATİK ☀" else "TEMA: OTOMATİK ☾" },
                        color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = { photoPicker.launch("image/*") }) {
                    Text("FOTOĞRAF SEÇ", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (navPhoto != null) {
                    TextButton(onClick = { NavVehiclePhoto.reset(ctx); photoStatus = null; photoVersion++ }) {
                        Text("SIFIRLA", color = C.soft, fontSize = 11.sp)
                    }
                }
            }

            when (val ms = mapState) {
                is NavMapState.Loading -> StatusRow(C, loading = true, text = "Harita yükleniyor…", color = C.warn)
                is NavMapState.Ready -> Text(
                    if (ms.buildings) "Harita hazır • 3D binalar açık" else "Harita hazır • 3D bina verisi bu stilde yok",
                    color = C.good, fontSize = 10.sp
                )
                is NavMapState.Degraded -> Column {
                    Text(ms.reason, color = C.warn, fontSize = 10.sp)
                    TextButton(onClick = { retryToken++ }) { Text("HARİTAYI TEKRAR DENE", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                is NavMapState.Failed -> Column {
                    Text(ms.reason, color = C.bad, fontSize = 10.sp)
                    TextButton(onClick = { retryToken++ }) { Text("TEKRAR DENE", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                when {
                    latitude == null -> locationStatus
                    gpsStale -> "GPS sinyali zayıf veya kayıp — son bilinen konum gösteriliyor"
                    else -> String.format(Locale.US, "Canlı GPS %.5f, %.5f", latitude, longitude ?: 0.0)
                },
                color = if (latitude == null || gpsStale) C.warn else C.soft, fontSize = 10.sp
            )
            Text("© OpenStreetMap katkıcıları • OpenFreeMap • MapLibre", color = C.muted, fontSize = 9.sp)
        }
        }

        // ======== saat: sag ust kose, her zaman gorunur (arkasi seffaf) ========
        Text(
            currentTime, color = C.text, fontSize = 40.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = C.hudShadow),
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(horizontal = 18.dp, vertical = 8.dp)
        )

        // ======== sadece hiz (paneller gizliyken; arkasi seffaf) ========
        AnimatedVisibility(
            visible = !panelsVisible,
            modifier = Modifier.align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter).safeDrawingPadding(),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Text(speedKmh.coerceAtLeast(0).toString(), color = C.text, fontSize = 104.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = C.hudShadow))
                Text(" km/h", color = C.soft, fontSize = 28.sp, style = TextStyle(shadow = C.hudShadow), modifier = Modifier.padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
private fun StatusRow(c: NavColors, loading: Boolean, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoChip(c: NavColors, label: String, value: String) {
    Surface(color = c.chip, shape = RoundedCornerShape(9.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = c.soft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Manevra oku: font/ikon paketine bagimli degil, Canvas ile cizilir. */
@Composable
private fun ManeuverIcon(c: NavColors, kind: ManeuverKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ctr = Offset(w / 2f, h / 2f)
        if (kind == ManeuverKind.ARRIVE) {
            drawCircle(Color(0xFFFF4D5A), radius = w * 0.26f, center = ctr)
            drawCircle(c.panel, radius = w * 0.10f, center = ctr)
        } else {
            if (kind == ManeuverKind.ROUNDABOUT) {
                drawCircle(c.accent, radius = w * 0.24f, center = ctr, style = Stroke(width = w * 0.07f))
            }
            rotate(kind.angleDeg, pivot = ctr) {
                val shaft = Path().apply { moveTo(w * 0.5f, h * 0.88f); lineTo(w * 0.5f, h * 0.38f) }
                drawPath(shaft, c.text, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
                val head = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.82f, h * 0.46f); lineTo(w * 0.18f, h * 0.46f); close()
                }
                drawPath(head, c.text)
            }
        }
    }
}

/** Kirmizi, 3B gorunumlu navigasyon oku (Canvas): yan yuzler, kalinlik, parlama ve zemin golgesi. */
@Composable
private fun NavArrow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun pt(x: Float, y: Float) = Offset(w * x, h * y)
        val tip = pt(0.5f, 0.04f)
        val left = pt(0.10f, 0.80f)
        val notch = pt(0.5f, 0.60f)
        val right = pt(0.90f, 0.80f)
        val depth = h * 0.08f
        drawOval(Color(0x73000000), topLeft = pt(0.08f, 0.80f), size = Size(w * 0.84f, h * 0.17f))
        val side = Path().apply {
            moveTo(left.x, left.y); lineTo(notch.x, notch.y); lineTo(right.x, right.y)
            lineTo(right.x, right.y + depth); lineTo(notch.x, notch.y + depth); lineTo(left.x, left.y + depth); close()
        }
        drawPath(side, Color(0xFF6B0A12))
        val leftFace = Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(notch.x, notch.y); close() }
        val rightFace = Path().apply { moveTo(tip.x, tip.y); lineTo(notch.x, notch.y); lineTo(right.x, right.y); close() }
        drawPath(leftFace, Color(0xFFB5121E))
        drawPath(rightFace, Color(0xFFFF3B47))
        drawLine(Color(0xA6FFFFFF), tip, notch, strokeWidth = w * 0.014f, cap = StrokeCap.Round)
        val outline = Path().apply {
            moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(notch.x, notch.y); lineTo(right.x, right.y); close()
        }
        drawPath(outline, Color(0x80FFFFFF), style = Stroke(width = w * 0.016f, join = StrokeJoin.Round))
    }
}
