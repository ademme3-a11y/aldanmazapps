package com.aldanmaz.drivedashboard.nav

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Saf (Android'siz) cografi yardimcilar; JVM'de birim test edilir. */
object NavGeo {
    private const val EARTH_R = 6_371_008.8
    const val M_PER_DEG_LAT = 111_320.0

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    fun haversine(a: GeoPoint, b: GeoPoint): Double = haversine(a.lat, a.lon, b.lat, b.lon)

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_R * asin(min(1.0, sqrt(h)))
    }

    /** a'dan b'ye baslangic yonu, 0..360 derece (kuzey = 0, saat yonu). */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return normalizeDeg(deg(atan2(y, x)))
    }

    fun normalizeDeg(d: Double): Double {
        val r = d % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** b - a, en kisa yoldan, -180..180. */
    fun angleDiff(a: Double, b: Double): Double {
        var d = (b - a) % 360.0
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return d
    }

    fun lerpAngle(a: Double, b: Double, t: Double): Double =
        normalizeDeg(a + angleDiff(a, b) * t.coerceIn(0.0, 1.0))

    fun cumulativeDistances(points: List<GeoPoint>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + haversine(points[i - 1], points[i])
        return out
    }

    /** Encoded polyline (Google algoritmasi). Valhalla/OSRM polyline6 -> precision = 6. */
    fun decodePolyline(encoded: String, precision: Int = 6): List<GeoPoint> {
        val factor = 10.0.pow(precision)
        val out = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out.add(GeoPoint(lat / factor, lon / factor))
        }
        return out
    }

    /** 512 px'lik MapLibre zoom seviyesinde bir piksel kac metre. */
    fun metersPerPixel(lat: Double, zoom: Double): Double =
        78_271.51696 * cos(rad(lat)) / 2.0.pow(zoom)

    fun metersToDegLat(m: Double) = m / M_PER_DEG_LAT
    fun metersToDegLon(m: Double, lat: Double) = m / (M_PER_DEG_LAT * max(0.01, cos(rad(lat))))

    /** Rota uzerinde baslangictan d metre sonraki nokta (rota disina tasmaz). */
    fun pointAtDistance(points: List<GeoPoint>, cum: DoubleArray, d: Double): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        if (points.size == 1 || d <= 0.0) return points.first()
        if (d >= cum.last()) return points.last()
        var lo = 0
        var hi = cum.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (cum[mid] <= d) lo = mid else hi = mid
        }
        val seg = cum[hi] - cum[lo]
        val t = if (seg < 1e-9) 0.0 else (d - cum[lo]) / seg
        return GeoPoint(
            points[lo].lat + (points[hi].lat - points[lo].lat) * t,
            points[lo].lon + (points[hi].lon - points[lo].lon) * t
        )
    }

    class Snap(val distAlongM: Double, val offsetM: Double, val segIndex: Int)

    /**
     * p noktasini rota cizgisine izdusumler. hintSeg etrafinda once dar pencere denenir
     * (paralel/dongulu yollarda sicramayi onler), yetersizse tum rota taranir.
     */
    fun snapToRoute(points: List<GeoPoint>, cum: DoubleArray, p: GeoPoint, hintSeg: Int): Snap {
        val segs = points.size - 1
        if (segs < 1) return Snap(0.0, haversine(points.first(), p), 0)
        val from = max(0, hintSeg - 20)
        val to = min(segs - 1, hintSeg + 400)
        val local = scan(points, cum, p, from, to)
        if (local.offsetM <= 60.0 || (from == 0 && to == segs - 1)) return local
        return scan(points, cum, p, 0, segs - 1)
    }

    private fun scan(points: List<GeoPoint>, cum: DoubleArray, p: GeoPoint, from: Int, to: Int): Snap {
        val cosLat = cos(rad(p.lat))
        var bestOff = Double.MAX_VALUE
        var bestAlong = 0.0
        var bestSeg = from
        for (i in from..to) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - p.lon) * cosLat * M_PER_DEG_LAT
            val ay = (a.lat - p.lat) * M_PER_DEG_LAT
            val bx = (b.lon - p.lon) * cosLat * M_PER_DEG_LAT
            val by = (b.lat - p.lat) * M_PER_DEG_LAT
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val off = sqrt(px * px + py * py)
            if (off < bestOff) {
                bestOff = off
                bestSeg = i
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
            }
        }
        return Snap(bestAlong, bestOff, bestSeg)
    }
}
