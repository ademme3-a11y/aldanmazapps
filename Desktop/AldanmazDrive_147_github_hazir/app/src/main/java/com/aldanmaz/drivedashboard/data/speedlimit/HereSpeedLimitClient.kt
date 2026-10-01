package com.aldanmaz.drivedashboard.data.speedlimit
import com.aldanmaz.drivedashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

data class ProviderDiagnosticResult(
    val provider: String,
    val apiKeyPresent: Boolean,
    val responseCode: Int? = null,
    val speedLimitKmh: Int? = null,
    val roadName: String? = null,
    val message: String
)

class HereSpeedLimitClient {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
    }

    suspend fun diagnose(latitude: Double, longitude: Double, headingDegrees: Float?): ProviderDiagnosticResult =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.HERE_API_KEY.trim()
            if (apiKey.isBlank()) {
                return@withContext ProviderDiagnosticResult("HERE", false, message = "HERE API anahtarı bulunamadı")
            }
            val heading = headingDegrees?.takeIf { !it.isNaN() } ?: 0f
            val distanceMeters = 80.0
            val bearingRad = Math.toRadians(heading.toDouble())
            val dLat = distanceMeters * cos(bearingRad) / 111_320.0
            val dLon = distanceMeters * sin(bearingRad) / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
            val destinationLat = latitude + dLat
            val destinationLon = longitude + dLon
            val url = "https://router.hereapi.com/v8/routes?" +
                "origin=$latitude,$longitude&destination=$destinationLat,$destinationLon" +
                "&transportMode=car&return=summary,polyline&spans=maxSpeed" +
                "&apiKey=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}"

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "AldanmazDrive/1.0")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()?.take(220)
                    val suffix = if (detail.isNullOrBlank()) "" else ": $detail"
                    return@withContext ProviderDiagnosticResult("HERE", true, code, message = "HERE HTTP $code$suffix")
                }
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val span = root.optJSONArray("routes")?.optJSONObject(0)?.optJSONArray("sections")?.optJSONObject(0)?.optJSONArray("spans")?.optJSONObject(0)
                val maxSpeedMps = span?.optDouble("maxSpeed", Double.NaN) ?: Double.NaN
                if (!maxSpeedMps.isFinite() || maxSpeedMps <= 0.0) {
                    return@withContext ProviderDiagnosticResult("HERE", true, code, message = "HERE yanıt verdi ancak hız sınırı bulunamadı")
                }
                ProviderDiagnosticResult("HERE", true, code, (maxSpeedMps * 3.6).roundToInt(), message = "HERE bağlantısı başarılı")
            } catch (e: Exception) {
                ProviderDiagnosticResult("HERE", true, message = "HERE bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                connection?.disconnect()
            }
        }
}
