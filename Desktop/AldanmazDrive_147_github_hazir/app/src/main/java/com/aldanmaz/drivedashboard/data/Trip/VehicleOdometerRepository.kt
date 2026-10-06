package com.aldanmaz.drivedashboard.data.trip

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class VehicleOdometerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vehicle_odometer", Context.MODE_PRIVATE)

    fun setInitialOrCurrentRealKm(realKm: Double, gpsTotalKm: Double) {
        if (realKm <= 0.0) return

        val oldReal = prefs.getDouble(KEY_REAL_KM)
        val oldGps = prefs.getDouble(KEY_BASE_GPS_KM)
        val oldMonth = prefs.getString(KEY_LAST_MONTH, null)
        val computedBefore = if (oldReal > 0.0) {
            oldReal + (gpsTotalKm - oldGps)
        } else {
            realKm
        }

        // İlk kalibrasyonda Araç KM ve GPS KM aynı referansa bağlanır.
        // Sonraki girişlerde yalnızca kullanıcı tarafından doğrulanan gerçek
        // odometre ile GPS'in hesapladığı değer arasındaki fark REVİZE KM olur.
        val correction = if (oldReal > 0.0) {
            realKm - computedBefore
        } else {
            0.0
        }

        val revisions = JSONObject(prefs.getString(KEY_REVISIONS, "{}") ?: "{}")
        if (oldReal > 0.0 && oldMonth != null && correction != 0.0) {
            revisions.put(oldMonth, revisions.optDouble(oldMonth, 0.0) + correction)
        } else if (oldReal <= 0.0) {
            revisions.put(monthKey(System.currentTimeMillis()), realKm - gpsTotalKm)
        }

        prefs.edit()
            .putDouble(KEY_REAL_KM, realKm)
            .putDouble(KEY_BASE_GPS_KM, gpsTotalKm)
            .putString(KEY_LAST_MONTH, monthKey(System.currentTimeMillis()))
            .putString(KEY_REVISIONS, revisions.toString())
            .apply()
    }

    fun currentRealKm(gpsTotalKm: Double): Double {
        val real = prefs.getDouble(KEY_REAL_KM)
        if (real <= 0.0) return 0.0
        return (real + (gpsTotalKm - prefs.getDouble(KEY_BASE_GPS_KM))).coerceAtLeast(0.0)
    }

    // GPS KM, ilk Araç Gerçek KM değerini başlangıç referansı kabul eder
    // ve yalnızca uygulamanın GPS ile takip ettiği mesafeyi bunun üzerine ekler.
    fun currentGpsKm(gpsTotalKm: Double): Double {
        val real = prefs.getDouble(KEY_REAL_KM)
        if (real <= 0.0) return 0.0
        return gpsTotalKm.coerceAtLeast(0.0)
    }

    fun recordCurrentMonthRevision(gpsTotalKm: Double) {
        val real = prefs.getDouble(KEY_REAL_KM)
        if (real <= 0.0) return
        val month = monthKey(System.currentTimeMillis())
        val revisions = JSONObject(prefs.getString(KEY_REVISIONS, "{}") ?: "{}")
        revisions.put(month, real + (gpsTotalKm - prefs.getDouble(KEY_BASE_GPS_KM)) - gpsTotalKm)
        prefs.edit().putString(KEY_REVISIONS, revisions.toString()).apply()
    }

    fun enteredRealKm(): Double = prefs.getDouble(KEY_REAL_KM)

    fun revisionForMonth(month: String, gpsTotalKm: Double): Double {
        val revisions = JSONObject(prefs.getString(KEY_REVISIONS, "{}") ?: "{}")
        // REVİZE KM yalnızca gerçek odometre ile GPS hesabı arasında
        // kullanıcı tarafından doğrulanmış bir fark oluştuğunda vardır.
        // GPS sürüşü tek başına negatif REVİZE oluşturmaz.
        if (month == monthKey(System.currentTimeMillis())) {
            val real = prefs.getDouble(KEY_REAL_KM)
            if (real > 0.0) {
                val currentGps = gpsTotalKm.coerceAtLeast(0.0)
                val currentReal = real + (gpsTotalKm - prefs.getDouble(KEY_BASE_GPS_KM))
                return currentReal - currentGps
            }
        }
        return revisions.optDouble(month, 0.0)
    }

    private fun monthKey(epoch: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(epoch))

    private fun android.content.SharedPreferences.getDouble(key: String): Double =
        java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(0.0)))

    private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double) =
        putLong(key, java.lang.Double.doubleToRawLongBits(value))

    companion object {
        private const val KEY_REAL_KM = "realKm"
        private const val KEY_BASE_GPS_KM = "baseGpsKm"
        private const val KEY_LAST_MONTH = "lastMonth"
        private const val KEY_REVISIONS = "monthlyRevisions"
    }
}
