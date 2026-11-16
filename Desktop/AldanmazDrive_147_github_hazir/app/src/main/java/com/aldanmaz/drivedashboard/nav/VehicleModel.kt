package com.aldanmaz.drivedashboard.nav

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beyaz 2017 Dacia Sandero Stepway (4.09 m x 1.76 m) icin, harita uzerinde gercek 3 boyutlu
 * (fill-extrusion) arac gorunumu. Parcalar GeoJSON poligonlari olarak uretilir; boylece arac
 * kamera egimi/donusuyle birlikte gercek perspektifle cizilir ve haritayla ayni yonde doner.
 * Telifli model/varlik kullanilmaz; geometri bu dosyada ozgun olarak tanimlidir.
 */
object VehicleModel {
    const val LENGTH_M = 4.09
    const val WIDTH_M = 1.76

    private class Part(val pts: List<Pair<Double, Double>>, val base: Double, val height: Double, val color: String)

    private fun box(x0: Double, x1: Double, y0: Double, y1: Double, base: Double, h: Double, color: String) =
        Part(listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1), base, h, color)

    private fun chamfered(hw: Double, hl: Double, c: Double, base: Double, h: Double, color: String) = Part(
        listOf(
            -hw + c to -hl, hw - c to -hl, hw to -hl + c, hw to hl - c,
            hw - c to hl, -hw + c to hl, -hw to hl - c, -hw to -hl + c
        ), base, h, color
    )

    // x: sag (+), y: ileri (+), metre; arac merkezi (0,0).
    private val parts: List<Part> = listOf(
        box(-0.95, 0.95, -2.12, 2.12, 0.0, 0.04, "#05080B"),                    // zemin golgesi
        chamfered(0.86, 1.95, 0.40, 0.0, 0.30, "#2B2F33"),                      // plastik alt koruma (Stepway)
        chamfered(0.88, 2.04, 0.45, 0.28, 0.98, "#F7F8FA"),                     // beyaz govde
        box(-0.78, 0.78, -1.25, 0.55, 0.98, 1.46, "#1C2A36"),                   // camlar
        box(-0.72, 0.72, -1.12, 0.42, 1.44, 1.60, "#F7F8FA"),                   // tavan
        box(-0.64, -0.56, -1.0, 0.30, 1.60, 1.67, "#3A4047"),                   // tavan barlari
        box(0.56, 0.64, -1.0, 0.30, 1.60, 1.67, "#3A4047"),
        box(-0.98, -0.80, 0.90, 1.62, 0.0, 0.62, "#15181B"),                    // tekerlekler
        box(0.80, 0.98, 0.90, 1.62, 0.0, 0.62, "#15181B"),
        box(-0.98, -0.80, -1.68, -0.96, 0.0, 0.62, "#15181B"),
        box(0.80, 0.98, -1.68, -0.96, 0.0, 0.62, "#15181B"),
        box(-0.80, -0.42, 2.00, 2.08, 0.62, 0.84, "#FFF3B0"),                   // on farlar
        box(0.42, 0.80, 2.00, 2.08, 0.62, 0.84, "#FFF3B0"),
        box(-0.82, -0.40, -2.08, -2.00, 0.66, 0.84, "#E53935"),                 // arka stoplar
        box(0.40, 0.82, -2.08, -2.00, 0.66, 0.84, "#E53935"),
        box(-0.26, 0.26, -2.10, -2.02, 0.50, 0.62, "#FFFFFF")                   // plaka (07 MTU 93)
    )

    /** Arac yerel koordinatini (x sag, y ileri; metre) cografi koordinata cevirir. heading: kuzeyden saat yonu. */
    fun localToGeo(lat: Double, lon: Double, headingDeg: Double, x: Double, y: Double, scale: Double): GeoPoint {
        val th = Math.toRadians(headingDeg)
        val east = (x * cos(th) + y * sin(th)) * scale
        val north = (-x * sin(th) + y * cos(th)) * scale
        return GeoPoint(lat + NavGeo.metersToDegLat(north), lon + NavGeo.metersToDegLon(east, lat))
    }

    /** scale > 1: ekranda gorunur kalmasi icin arac gercek olcusunun uzerine buyutulur (yukseklikler dahil). */
    fun featureCollection(lat: Double, lon: Double, headingDeg: Double, scale: Double): String {
        val sb = StringBuilder(4096)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        parts.forEachIndexed { i, part ->
            if (i > 0) sb.append(',')
            sb.append("{\"type\":\"Feature\",\"properties\":{\"color\":\"").append(part.color)
            sb.append("\",\"b\":").append(fmt(part.base * scale))
            sb.append(",\"h\":").append(fmt(part.height * scale))
            sb.append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
            val ring = part.pts + part.pts.first()
            ring.forEachIndexed { j, (x, y) ->
                if (j > 0) sb.append(',')
                val g = localToGeo(lat, lon, headingDeg, x, y, scale)
                sb.append('[').append(String.format(Locale.US, "%.8f", g.lon)).append(',')
                    .append(String.format(Locale.US, "%.8f", g.lat)).append(']')
            }
            sb.append("]]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.3f", v)

    val partCount: Int get() = parts.size
}
