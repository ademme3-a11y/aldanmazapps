package com.aldanmaz.drivedashboard.nav

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gun dogumu / batimi hesabi (NOAA / "sunrise equation"). Ag veya ek kutuphane gerektirmez.
 * Gorsel gece/gunduz temasi icin kullanilir; dakika hassasiyeti yeterlidir.
 */
object SunCalc {
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val MS_PER_DAY = 86_400_000.0

    private fun rad(d: Double) = d * PI / 180.0

    /** Bir gunluk dilimin olaylari: (dogus, batis) UTC epoch ms; kutup gecesi/gunesi icin null. polarDay: gun boyu aydinlik mi. */
    class Events(val riseMs: Long?, val setMs: Long?, val polarDay: Boolean)

    private fun eventsForDay(n: Double, lat: Double, lon: Double): Events {
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360.0
        val mr = rad(m)
        val c = 1.9148 * sin(mr) + 0.02 * sin(2 * mr) + 0.0003 * sin(3 * mr)
        val lambda = (m + c + 180.0 + 102.9372) % 360.0
        val lr = rad(lambda)
        val jTransit = J2000 + jStar + 0.0053 * sin(mr) - 0.0069 * sin(2 * lr)
        val sinDec = sin(lr) * sin(rad(23.4397))
        val cosDec = cos(kotlin.math.asin(sinDec))
        val cosW = (sin(rad(-0.833)) - sin(rad(lat)) * sinDec) / (cos(rad(lat)) * cosDec)
        if (cosW > 1.0) return Events(null, null, false)
        if (cosW < -1.0) return Events(null, null, true)
        val w = acos(cosW) * 180.0 / PI
        val rise = jTransit - w / 360.0
        val set = jTransit + w / 360.0
        return Events(((rise - UNIX_EPOCH_JD) * MS_PER_DAY).toLong(), ((set - UNIX_EPOCH_JD) * MS_PER_DAY).toLong(), false)
    }

    private fun dayNumber(nowMs: Long): Double {
        val jd = nowMs / MS_PER_DAY + UNIX_EPOCH_JD
        return ceil(jd - J2000 + 0.0008 + 69.184 / 86400.0)
    }

    /** Verilen anin hemen cevresindeki (dun, bugun, yarin) dogus/batis olaylari, zamana gore sirali. */
    fun events(nowMs: Long, lat: Double, lon: Double): List<Pair<Long, Boolean>> {
        val n = dayNumber(nowMs)
        val out = ArrayList<Pair<Long, Boolean>>()
        for (k in -1..1) {
            val e = eventsForDay(n + k, lat, lon)
            e.riseMs?.let { out.add(it to true) }
            e.setMs?.let { out.add(it to false) }
        }
        return out.sortedBy { it.first }
    }

    /** Gunes ufkun ustunde mi (gunduz)? Konum yoksa [fallbackHour] ile yerel saate gore 06-18 varsayilir. */
    fun isDaylight(nowMs: Long, lat: Double?, lon: Double?, fallbackHour: Int): Boolean {
        if (lat == null || lon == null) return fallbackHour in 6..17
        val n = dayNumber(nowMs)
        val today = eventsForDay(n, lat, lon)
        if (today.riseMs == null && today.setMs == null) return today.polarDay
        val last = events(nowMs, lat, lon).lastOrNull { it.first <= nowMs } ?: return fallbackHour in 6..17
        return last.second
    }
}
