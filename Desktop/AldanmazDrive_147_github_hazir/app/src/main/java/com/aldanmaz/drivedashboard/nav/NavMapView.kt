package com.aldanmaz.drivedashboard.nav

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.coroutines.resume

private const val TAG = "NavMapView"
private const val STYLE_TIMEOUT_MS = 15_000L

/** Internet/CDN olmadan da calisan, yalnizca koyu zemin iceren son care stil: GPS, arac ve rota yine gorunur. */
private const val OFFLINE_DARK_STYLE =
    "{\"version\":8,\"name\":\"aldanmaz-offline-dark\",\"sources\":{}," +
        "\"layers\":[{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"#0B1118\"}}]}"

private class StyleWaiter {
    var pending: CompletableDeferred<Style?>? = null
}

/**
 * Yerel MapLibre MapView'i Compose'a baglar. Yasam dongusu LifecycleOwner'a baglidir.
 * Baslatma sirasi deterministiktir: MapView -> getMapAsync -> stil (uzak, 15 sn zaman asimi) -> hata olursa
 * cevrimdisi koyu stil -> cekirdek katmanlar -> (secenekli) 3B binalar. retryToken degisince bastan dener.
 */
@Composable
internal fun NavMapView(
    controller: NavMapController,
    styleUrl: String,
    retryToken: Int,
    onState: (NavMapState) -> Unit,
    onUserGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnState by rememberUpdatedState(onState)
    val currentOnGesture by rememberUpdatedState(onUserGesture)
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context)
    }
    val waiter = remember { StyleWaiter() }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        val failListener = MapView.OnDidFailLoadingMapListener { msg ->
            Log.w(TAG, "harita yuklenemedi: $msg")
            waiter.pending?.complete(null)
        }
        mapView.addOnDidFailLoadingMapListener(failListener)
        val observer = LifecycleEventObserver { _, event ->
            if (destroyed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failListener)
            if (!destroyed) {
                // Ekran kapandi ama Activity yasiyor: harita kaynaklarini serbest birak.
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
                destroyed = true
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.onSizeChanged { controller.onViewport(it.width, it.height) }
    )

    // 1) Harita nesnesi
    LaunchedEffect(mapView) {
        val m = suspendCancellableCoroutine<MapLibreMap> { cont ->
            mapView.getMapAsync { cont.resume(it) }
        }
        controller.attach(m) { currentOnGesture() }
        map = m
    }

    // 2) Stil: yalnizca harita nesnesi hazir olduktan sonra (baslatma yarisi yok)
    LaunchedEffect(map, retryToken, styleUrl) {
        val m = map ?: return@LaunchedEffect
        currentOnState(NavMapState.Loading)
        val attempts: List<Pair<String, () -> Style.Builder>> = listOf(
            "remote" to { Style.Builder().fromUri(styleUrl) },
            "offline" to { Style.Builder().fromJson(OFFLINE_DARK_STYLE) }
        )
        for ((index, attempt) in attempts.withIndex()) {
            val style = loadStyle(m, waiter, attempt.second())
            if (style == null) {
                Log.w(TAG, "stil yuklenemedi: ${attempt.first}")
                continue
            }
            val result = controller.installStyle(style, recolor = index == 0)
            if (result.coreOk) {
                val epoch = controller.bumpEpoch()
                currentOnState(
                    if (index == 0) NavMapState.Ready(epoch, result.buildingsOk)
                    else NavMapState.Degraded(epoch, "Harita altlığı yüklenemedi (internet/sunucu). Çevrimdışı modda: araç, GPS ve rota çalışır.")
                )
                return@LaunchedEffect
            }
        }
        currentOnState(NavMapState.Failed("Harita başlatılamadı."))
    }
}

private suspend fun loadStyle(map: MapLibreMap, waiter: StyleWaiter, builder: Style.Builder): Style? {
    val deferred = CompletableDeferred<Style?>()
    waiter.pending = deferred
    map.setStyle(builder) { style -> deferred.complete(style) }
    val style = withTimeoutOrNull(STYLE_TIMEOUT_MS) { deferred.await() }
    waiter.pending = null
    return style
}
