package com.aldanmaz.drivedashboard.nav

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Filtrelenmis konum; heading derece cinsinden aracin gidis yonudur. */
data class FilteredFix(val lat: Double, val lon: Double, val headingDeg: Double, val speedKmh: Int)

/**
 * Gercek GPS verisini temizler (uydurma konum URETMEZ):
 *  - gecersiz / (0,0) / aralik disi konumlari atar,
 *  - fiziksel olarak imkansiz ani sicramalari atar (art arda 5 kez gelirse yeni gercek kabul eder),
 *  - duran aracin GPS titremesini bastirir,
 *  - yonu, hareket yonunden (ardisik konumlar) alcak geciren filtreyle hesaplar;
 *    arac durunca son yon korunur, hic yon yoksa pusula degeri kullanilir.
 */
class GpsFilter {
    private var last: GeoPoint? = null
    private var lastTimeMs = 0L
    private var courseAnchor: GeoPoint? = null
    private var course: Double? = null
    private var rejectedInRow = 0

    fun reset() {
        last = null; lastTimeMs = 0; courseAnchor = null; course = null; rejectedInRow = 0
    }

    fun accept(lat: Double, lon: Double, speedKmh: Int, compassDeg: Float?, nowMs: Long): FilteredFix? {
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        val p = GeoPoint(lat, lon)
        val prev = last
        var pos = p

        if (prev != null) {
            val dt = max(0.001, (nowMs - lastTimeMs) / 1000.0)
            val d = NavGeo.haversine(prev, p)
            val impliedSpeed = d / dt
            if (d > 100.0 && impliedSpeed > MAX_SPEED_MS && rejectedInRow < MAX_REJECTS) {
                rejectedInRow++
                return null
            }
            rejectedInRow = 0
            // Duran aracta GPS titremesini bastir.
            if (speedKmh < 2 && d < JITTER_M) pos = prev
        }
        last = pos
        lastTimeMs = nowMs

        if (speedKmh >= COURSE_MIN_KMH) {
            val anchor = courseAnchor
            if (anchor == null) {
                courseAnchor = pos
            } else if (NavGeo.haversine(anchor, pos) >= COURSE_STEP_M) {
                val raw = NavGeo.bearing(anchor, pos)
                course = course?.let { NavGeo.lerpAngle(it, raw, 0.5) } ?: raw
                courseAnchor = pos
            }
        } else {
            courseAnchor = null
        }

        val heading = course ?: compassDeg?.toDouble()?.let { NavGeo.normalizeDeg(it) } ?: 0.0
        return FilteredFix(pos.lat, pos.lon, heading, max(0, speedKmh))
    }

    companion object {
        const val MAX_SPEED_MS = 83.0      // ~300 km/h
        const val MAX_REJECTS = 5
        const val JITTER_M = 6.0
        const val COURSE_MIN_KMH = 4
        const val COURSE_STEP_M = 4.0
    }
}

/**
 * Kare basina yumusatma: arac konumu, yonu ve zoom degeri hedefe ustel olarak yaklasir.
 * Boylece 1 Hz GPS akisi bile ekranda akici gorunur.
 */
class PoseSmoother {
    var lat = 0.0
        private set
    var lon = 0.0
        private set
    var heading = 0.0
        private set
    var zoom = 17.0
        private set
    var initialized = false
        private set

    fun step(dtSec: Double, tLat: Double, tLon: Double, tHeading: Double, tZoom: Double) {
        if (!initialized) {
            lat = tLat; lon = tLon; heading = tHeading; zoom = tZoom; initialized = true
            return
        }
        val dt = min(0.1, max(0.0, dtSec))
        val aPos = 1 - exp(-dt / POS_TAU)
        val aHead = 1 - exp(-dt / HEAD_TAU)
        val aZoom = 1 - exp(-dt / ZOOM_TAU)
        lat += (tLat - lat) * aPos
        lon += (tLon - lon) * aPos
        heading = NavGeo.lerpAngle(heading, tHeading, aHead)
        zoom += (tZoom - zoom) * aZoom
    }

    fun reset() { initialized = false }

    companion object {
        const val POS_TAU = 0.45
        const val HEAD_TAU = 0.55
        const val ZOOM_TAU = 1.2

        /** Hiza gore kamera zoom'u: yavasken yakin, hizliyken genis. */
        fun zoomForSpeed(speedKmh: Int, distToManeuverM: Double?): Double {
            val base = 18.2 - 2.1 * (speedKmh / 110.0).coerceIn(0.0, 1.0)
            val boost = if (distToManeuverM != null && distToManeuverM < 150.0) 0.5 else 0.0
            return base + boost
        }
    }
}
