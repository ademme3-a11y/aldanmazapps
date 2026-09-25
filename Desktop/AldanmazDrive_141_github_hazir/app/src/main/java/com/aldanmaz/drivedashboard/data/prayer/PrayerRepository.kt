package com.aldanmaz.drivedashboard.data.prayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class PrayerTimes(
    val date: String,
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val isCached: Boolean = false
)

class PrayerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("prayer_times", Context.MODE_PRIVATE)

    suspend fun getToday(latitude: Double, longitude: Double): PrayerTimes = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        runCatching { fetch(latitude, longitude, today) }
            .onSuccess { save(it) }
            .getOrElse { load(today) ?: throw it }
    }

    private fun fetch(latitude: Double, longitude: Double, date: String): PrayerTimes {
        // method=13: Türkiye Diyanet İşleri Başkanlığı hesaplama yöntemi.
        // Koordinata göre istek: şehir sabitlemesi yapılmaz.
        val coordinateUrl = URL("https://api.aladhan.com/v1/timings/${System.currentTimeMillis() / 1000}?latitude=$latitude&longitude=$longitude&method=13")
        val connection = (coordinateUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("Namaz vakti servisi: ${connection.responseCode}")
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val timings = root.getJSONObject("data").getJSONObject("timings")
            return PrayerTimes(
                date = date,
                fajr = clean(timings.getString("Fajr")),
                dhuhr = clean(timings.getString("Dhuhr")),
                asr = clean(timings.getString("Asr")),
                maghrib = clean(timings.getString("Maghrib")),
                isha = clean(timings.getString("Isha"))
            )
        } finally { connection.disconnect() }
    }

    private fun clean(value: String) = value.take(5)

    private fun save(times: PrayerTimes) {
        prefs.edit()
            .putString("date", times.date)
            .putString("fajr", times.fajr).putString("dhuhr", times.dhuhr)
            .putString("asr", times.asr).putString("maghrib", times.maghrib)
            .putString("isha", times.isha).apply()
    }

    private fun load(date: String): PrayerTimes? {
        if (prefs.getString("date", null) != date) return null
        return PrayerTimes(
            date = date,
            fajr = prefs.getString("fajr", null) ?: return null,
            dhuhr = prefs.getString("dhuhr", null) ?: return null,
            asr = prefs.getString("asr", null) ?: return null,
            maghrib = prefs.getString("maghrib", null) ?: return null,
            isha = prefs.getString("isha", null) ?: return null,
            isCached = true
        )
    }
}
