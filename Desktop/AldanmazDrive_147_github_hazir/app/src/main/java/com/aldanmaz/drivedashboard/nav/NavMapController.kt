package com.aldanmaz.drivedashboard.nav

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

/** Harita durumu: arayuz bunu gosterir; secenekli katmanlar temel haritayi asla engellemez. */
sealed class NavMapState {
    object Loading : NavMapState()
    data class Ready(val epoch: Int, val buildings: Boolean) : NavMapState()
    data class Degraded(val epoch: Int, val reason: String) : NavMapState()
    data class Failed(val reason: String) : NavMapState()
}

class StyleInstallResult(val coreOk: Boolean, val buildingsOk: Boolean)

/**
 * Yerel MapLibre haritasi uzerindeki tum katman/kamera islemleri (yalnizca ana is parcacigi).
 * Cekirdek katmanlar (rota, hedef, arac) ile secenekli katmanlar (3D bina, koyu tema) ayri try/catch
 * bloklarinda kurulur; birinin hatasi digerini ve temel haritayi engellemez.
 */
internal class NavMapController {
    private val tag = "NavMap"
    var map: MapLibreMap? = null
        private set
    private var routeSrc: GeoJsonSource? = null
    private var destSrc: GeoJsonSource? = null
    private var trailSrc: GeoJsonSource? = null
    private var vehicleSrc: GeoJsonSource? = null
    private var epoch = 0
    private var viewW = 0
    private var viewH = 0
    private var vehicleCleared = false
    private var lastRoadQueryAt = 0L
    private var lastRoadName: String? = null
    var styleReady = false
        private set

    /** Gunduz (acik, Tesla tarzi) / gece (koyu) harita temasi. */
    var dayTheme = false
        private set

    /** Arac ekranda yaklasik bu kadar piksel uzunlukta gorunur. */
    var carPx: Double = 300.0
        private set

    fun attach(m: MapLibreMap, onUserGesture: () -> Unit) {
        map = m
        m.uiSettings.isCompassEnabled = false
        m.uiSettings.isAttributionEnabled = true     // OSM / OpenFreeMap atfi (zorunlu) gorunur kalir
        m.uiSettings.isLogoEnabled = false
        m.uiSettings.isRotateGesturesEnabled = true
        m.uiSettings.isTiltGesturesEnabled = true
        m.uiSettings.isDoubleTapGesturesEnabled = false   // cift dokunus panelleri acar, haritayi yakinlastirmaz
        m.setMaxZoomPreference(20.0)
        m.setMinZoomPreference(3.0)
        m.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserGesture()
        }
        // Gercek GPS gelene kadar ulke gorunumu (uydurma konum yok).
        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(39.0, 35.0)).zoom(5.0).tilt(0.0).bearing(0.0).build()))
    }

    fun onViewport(w: Int, h: Int) {
        viewW = w; viewH = h
        carPx = (0.17 * minOf(w, h)).coerceIn(90.0, 230.0)
        applyFollowPadding()
    }

    /** Aracin ekranin alt-orta kismina oturmasi icin gorunum merkezini asagi kaydirir. */
    fun applyFollowPadding() {
        val m = map ?: return
        if (viewH > 0) m.setPadding(0, (viewH * 0.42).toInt(), 0, 0)
    }

    fun bumpEpoch(): Int { epoch += 1; return epoch }

    /** Kameranin gercek zoom'u (kullanici kamerayi elle oynattiysa arac olcegi bununla hesaplanir). */
    fun currentZoom(): Double? = map?.cameraPosition?.zoom

    fun installStyle(style: Style, recolor: Boolean): StyleInstallResult {
        styleReady = false
        routeSrc = null; destSrc = null; trailSrc = null; vehicleSrc = null
        var core = false
        var buildings = false
        try { applyTheme(style, recolor) } catch (t: Throwable) { Log.w(tag, "tema uygulanamadi", t) }
        try {
            val below = style.layers.firstOrNull { it is SymbolLayer }?.id
            val empty = "{\"type\":\"FeatureCollection\",\"features\":[]}"
            val rs = GeoJsonSource(ROUTE_SRC).also { it.setGeoJson(empty); style.addSource(it) }
            val ds = GeoJsonSource(DEST_SRC).also { it.setGeoJson(empty); style.addSource(it) }
            val ts = GeoJsonSource(TRAIL_SRC).also { it.setGeoJson(empty); style.addSource(it) }
            val vs = GeoJsonSource(VEH_SRC).also { it.setGeoJson(empty); style.addSource(it) }

            val widthCasing = Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(8, 5f), Expression.stop(14, 12f), Expression.stop(19, 30f))
            val widthLine = Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(8, 3f), Expression.stop(14, 7f), Expression.stop(19, 18f))
            val trail = LineLayer("nav-trail", TRAIL_SRC).withProperties(
                PropertyFactory.lineColor("#3AA0FF"), PropertyFactory.lineOpacity(0.45f), PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
            val glow = LineLayer("nav-route-glow", ROUTE_SRC).withProperties(
                PropertyFactory.lineColor("#28D7FF"), PropertyFactory.lineOpacity(0.20f), PropertyFactory.lineBlur(6f),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                    Expression.stop(8, 10f), Expression.stop(14, 26f), Expression.stop(19, 64f))),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
            val casing = LineLayer("nav-route-casing", ROUTE_SRC).withProperties(
                PropertyFactory.lineColor("#06324A"), PropertyFactory.lineWidth(widthCasing),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
            val line = LineLayer("nav-route-line", ROUTE_SRC).withProperties(
                PropertyFactory.lineColor("#28D7FF"), PropertyFactory.lineWidth(widthLine),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
            val dest = CircleLayer("nav-dest", DEST_SRC).withProperties(
                PropertyFactory.circleRadius(10f), PropertyFactory.circleColor("#FF4D5A"),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f))
            val car = FillExtrusionLayer("nav-vehicle-3d", VEH_SRC).withProperties(
                PropertyFactory.fillExtrusionColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.fillExtrusionHeight(Expression.toNumber(Expression.get("h"))),
                PropertyFactory.fillExtrusionBase(Expression.toNumber(Expression.get("b"))),
                PropertyFactory.fillExtrusionOpacity(1f),
                PropertyFactory.fillExtrusionVerticalGradient(true))

            // Rota/iz etiketlerin altina, arac en uste.
            for (l in listOf(trail, glow, casing, line)) {
                if (below != null) style.addLayerBelow(l, below) else style.addLayer(l)
            }
            style.addLayer(dest)
            style.addLayer(car)
            routeSrc = rs; destSrc = ds; trailSrc = ts; vehicleSrc = vs
            core = true
        } catch (t: Throwable) {
            Log.e(tag, "cekirdek katmanlar kurulamadi", t)
        }

        // 3B binalar secenekli: hata verirse harita, arama ve rota yine calisir.
        try {
            val existing = style.layers.firstOrNull { it is FillExtrusionLayer && it.id != "nav-vehicle-3d" }
            if (existing != null) {
                buildings = true
            } else if (style.getSource("openmaptiles") != null) {
                val below = style.layers.firstOrNull { it is SymbolLayer }?.id
                val b = FillExtrusionLayer("nav-buildings-3d", "openmaptiles").withProperties(
                    PropertyFactory.fillExtrusionColor("#34424F"),
                    PropertyFactory.fillExtrusionHeight(Expression.coalesce(Expression.toNumber(Expression.get("render_height")), Expression.literal(5.0))),
                    PropertyFactory.fillExtrusionBase(Expression.coalesce(Expression.toNumber(Expression.get("render_min_height")), Expression.literal(0.0))),
                    PropertyFactory.fillExtrusionOpacity(0.88f))
                b.setSourceLayer("building")
                b.setMinZoom(14f)
                if (below != null) style.addLayerBelow(b, below) else style.addLayer(b)
                buildings = true
            }
        } catch (t: Throwable) {
            Log.w(tag, "3B binalar eklenemedi (secenekli)", t)
        }
        styleReady = core
        if (core) try { applyTheme(style, recolor) } catch (t: Throwable) { Log.w(tag, "tema (rota katmanlari) uygulanamadi", t) }
        return StyleInstallResult(core, buildings)
    }

    /** Calisma aninda gece/gunduz temasini degistirir (stili yeniden yuklemeden). */
    fun setTheme(day: Boolean) {
        if (day == dayTheme) return
        dayTheme = day
        val st = map?.style ?: return
        if (!styleReady) return
        try { applyTheme(st, full = true) } catch (t: Throwable) { Log.w(tag, "tema degistirilemedi", t) }
    }

    /** Hazir stili Tesla tarzi gece (koyu) veya gunduz (acik gri-beyaz) temaya cevirir; her katman ayri korunur. */
    private fun applyTheme(style: Style, full: Boolean) {
        val d = dayTheme
        val bg = if (d) "#E8EBEF" else "#0D141B"
        for (layer in style.layers) {
            try {
                val id = layer.id.lowercase(Locale.ROOT)
                if (id.startsWith("nav-")) { themeNavLayer(layer, id, d); continue }
                when (layer) {
                    is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(bg))
                    else -> if (!full) Unit else when (layer) {
                        is FillLayer -> layer.setProperties(PropertyFactory.fillColor(when {
                            id.contains("water") -> if (d) "#B9D3E8" else "#10222F"
                            id.contains("park") || id.contains("wood") || id.contains("grass") || id.contains("forest") ->
                                if (d) "#D3E2D0" else "#12201B"
                            id.contains("building") -> if (d) "#D5D9DE" else "#1B2630"
                            id.contains("aeroway") -> if (d) "#DADFE4" else "#1B2730"
                            else -> if (d) "#EEF0F3" else "#141C24"
                        }))
                        is LineLayer -> {
                            val color = when {
                                id.contains("waterway") -> if (d) "#A9C8E0" else "#16303F"
                                id.contains("boundary") -> if (d) "#B4BCC5" else "#3A4650"
                                id.contains("casing") -> if (d) "#C3C9D0" else "#05080B"
                                id.contains("rail") || id.contains("transit") -> if (d) "#C9CFD6" else "#2A343D"
                                id.contains("motorway") || id.contains("trunk") -> if (d) "#FFFFFF" else "#9DB7D0"
                                id.contains("primary") -> if (d) "#FFFFFF" else "#7F98B0"
                                id.contains("secondary") || id.contains("tertiary") -> if (d) "#FCFCFD" else "#667C91"
                                id.contains("road") || id.contains("street") || id.contains("minor") ||
                                    id.contains("service") || id.contains("bridge") || id.contains("tunnel") ->
                                    if (d) "#F8F9FA" else "#4A5D6E"
                                else -> null
                            }
                            if (color != null) layer.setProperties(PropertyFactory.lineColor(color))
                        }
                        is SymbolLayer -> layer.setProperties(
                            PropertyFactory.textColor(if (d) "#3A434D" else "#CFD9E2"),
                            PropertyFactory.textHaloColor(if (d) "#FFFFFF" else "#0D141B"))
                        is FillExtrusionLayer -> layer.setProperties(
                            PropertyFactory.fillExtrusionColor(if (d) "#CDD2D9" else "#34424F"),
                            PropertyFactory.fillExtrusionOpacity(0.9f))
                        else -> Unit
                    }
                }
            } catch (t: Throwable) {
                Log.w(tag, "katman temalanamadi: ${layer.id}", t)
            }
        }
    }

    /** Rota/iz/bina katmanlarinin temaya gore renkleri (arac modeli ve hedef noktasi sabit). */
    private fun themeNavLayer(layer: org.maplibre.android.style.layers.Layer, id: String, d: Boolean) {
        when (id) {
            "nav-route-line" -> layer.setProperties(PropertyFactory.lineColor(if (d) "#2F7BFF" else "#28D7FF"))
            "nav-route-glow" -> layer.setProperties(PropertyFactory.lineColor(if (d) "#2F7BFF" else "#28D7FF"))
            "nav-route-casing" -> layer.setProperties(PropertyFactory.lineColor(if (d) "#16479E" else "#06324A"))
            "nav-trail" -> layer.setProperties(PropertyFactory.lineColor(if (d) "#2F7BFF" else "#3AA0FF"))
            "nav-buildings-3d" -> layer.setProperties(PropertyFactory.fillExtrusionColor(if (d) "#CDD2D9" else "#34424F"))
        }
    }

    // ---- veri ----
    fun setRoute(points: List<GeoPoint>?) {
        val src = routeSrc ?: return
        if (points == null || points.size < 2) { src.setGeoJson(EMPTY_FC); return }
        val sb = StringBuilder(points.size * 24 + 96)
        sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        points.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('[').append(String.format(Locale.US, "%.6f", p.lon)).append(',')
                .append(String.format(Locale.US, "%.6f", p.lat)).append(']')
        }
        sb.append("]}}")
        src.setGeoJson(sb.toString())
    }

    fun setDestination(p: GeoPoint?) {
        val src = destSrc ?: return
        if (p == null) { src.setGeoJson(EMPTY_FC); return }
        src.setGeoJson(String.format(Locale.US,
            "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]}}", p.lon, p.lat))
    }

    fun setTrail(points: List<GeoPoint>) {
        val src = trailSrc ?: return
        if (points.size < 2) { src.setGeoJson(EMPTY_FC); return }
        val sb = StringBuilder(points.size * 24 + 96)
        sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        points.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('[').append(String.format(Locale.US, "%.6f", p.lon)).append(',')
                .append(String.format(Locale.US, "%.6f", p.lat)).append(']')
        }
        sb.append("]}}")
        src.setGeoJson(sb.toString())
    }

    /** Aracin 3B modelini konuma/yone yerlestirir; olcek kamera zoom'una gore sabit ekran boyutu verir. */
    fun setVehicle(lat: Double, lon: Double, headingDeg: Double, zoom: Double) {
        val src = vehicleSrc ?: return
        vehicleCleared = false
        val mpp = NavGeo.metersPerPixel(lat, zoom)
        val scale = (carPx * mpp / VehicleModel.LENGTH_M).coerceIn(1.0, 14.0)
        src.setGeoJson(VehicleModel.featureCollection(lat, lon, headingDeg, scale))
    }

    /** Surus kamerasinda arac ekrana foto olarak cizilir; haritadaki 3B blok model gizlenir. */
    fun clearVehicle() {
        if (vehicleCleared) return
        vehicleSrc?.setGeoJson(EMPTY_FC)
        vehicleCleared = true
    }

    // ---- kamera ----
    fun moveFollow(lat: Double, lon: Double, bearing: Double, zoom: Double) {
        val m = map ?: return
        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).bearing(bearing).tilt(60.0).build()))
    }

    /** Rotanin tamamini gosterir (sonra arayuz suruse kamerasina doner). */
    fun fitRoute(points: List<GeoPoint>, padLeftPx: Int, padTopPx: Int, padRightPx: Int, padBottomPx: Int, durationMs: Int = 1100) {
        val m = map ?: return
        if (points.size < 2) return
        val step = maxOf(1, points.size / 1500)
        val b = LatLngBounds.Builder()
        var i = 0
        while (i < points.size) { b.include(LatLng(points[i].lat, points[i].lon)); i += step }
        b.include(LatLng(points.last().lat, points.last().lon))
        m.setPadding(0, 0, 0, 0)
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), padLeftPx, padTopPx, padRightPx, padBottomPx), durationMs)
    }

    /** Aracin altindaki yolun adi (vektor karolarindan); yoksa null. En fazla ~1 sn'de bir sorgulanir. */
    fun roadNameAt(lat: Double, lon: Double, nowMs: Long): String? {
        val m = map ?: return lastRoadName
        if (!styleReady || nowMs - lastRoadQueryAt < 1200) return lastRoadName
        lastRoadQueryAt = nowMs
        try {
            val pt: PointF = m.projection.toScreenLocation(LatLng(lat, lon))
            val r = 28f
            val feats = m.queryRenderedFeatures(RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r))
            for (f in feats) {
                val type = f.geometry()?.type() ?: continue
                if ((type == "LineString" || type == "MultiLineString") && f.hasProperty("name") && f.hasProperty("class")) {
                    val n = f.getStringProperty("name")
                    if (!n.isNullOrBlank()) { lastRoadName = n; return n }
                }
            }
        } catch (t: Throwable) {
            Log.w(tag, "yol adi sorgulanamadi", t)
        }
        return lastRoadName
    }

    companion object {
        const val ROUTE_SRC = "nav-route-src"
        const val DEST_SRC = "nav-dest-src"
        const val TRAIL_SRC = "nav-trail-src"
        const val VEH_SRC = "nav-vehicle-src"
        const val EMPTY_FC = "{\"type\":\"FeatureCollection\",\"features\":[]}"
    }
}
