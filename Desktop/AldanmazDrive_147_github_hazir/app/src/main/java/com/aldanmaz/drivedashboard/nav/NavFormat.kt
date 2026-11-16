package com.aldanmaz.drivedashboard.nav

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Ekranda gosterilen mesafe/sure/varis bicimleri (saf, test edilir). */
object NavFormat {
    private val tr: Locale = Locale.forLanguageTag("tr-TR")
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun distance(m: Double): String = when {
        m < 0 -> "—"
        m < 200 -> "${((m / 10).roundToInt() * 10)} m"
        m < 1000 -> "${((m / 50).roundToInt() * 50)} m"
        else -> String.format(tr, "%.1f km", m / 1000.0)
    }

    fun duration(s: Double): String {
        val mins = (s / 60.0).roundToInt().coerceAtLeast(1)
        return if (mins < 60) "$mins dk" else "${mins / 60} sa ${mins % 60} dk"
    }

    fun eta(nowTime: LocalTime, remainingS: Double): String = nowTime.plusSeconds(remainingS.toLong()).format(hhmm)
}
