package com.aldanmaz.drivedashboard.data.speedlimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class OsmSpeedLimitClient {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 7_000
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }
    suspend fun diagnose(latitude: Double, longitude: Double): ProviderDiagnosticResult =
        withContext(Dispatchers.IO) {
            val query = "[out:json][timeout:6];way(around:35,$latitude,$longitude)[highway][maxspeed];out tags center;"
            var connection: HttpURLConnection? = null
            var lastError: String? = null
            for (endpoint in ENDPOINTS) try {
                val url = endpoint + "?data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
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
                    lastError = "HTTP $code" + if (detail.isNullOrBlank()) "" else ": $detail"
                    continue
                }
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val elements = root.optJSONArray("elements")
                if (elements == null || elements.length() == 0) {
                    lastError = "yakında maxspeed verisi bulunamadı"
                    continue
                }
                var bestSpeed: Int? = null
                var bestRoad: String? = null
                var bestDistance = Double.MAX_VALUE
                for (i in 0 until elements.length()) {
                    val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                    val parsed = parseSpeed(tags.optString("maxspeed").trim()) ?: continue
                    val center = elements.optJSONObject(i)?.optJSONObject("center")
                    val clat = center?.optDouble("lat") ?: Double.NaN
                    val clon = center?.optDouble("lon") ?: Double.NaN
                    val dLat = clat - latitude
                    val dLon = clon - longitude
                    val distance = if (!clat.isNaN() && !clon.isNaN()) dLat * dLat + dLon * dLon else Double.MAX_VALUE
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestSpeed = parsed
                        bestRoad = tags.optString("name").takeIf { it.isNotBlank() }
                    }
                }
                if (bestSpeed != null) return@withContext ProviderDiagnosticResult("OSM", true, code, bestSpeed, bestRoad, message = "OSM bağlantısı başarılı")
                lastError = "yol verisi bulundu ancak maxspeed ayrıştırılamadı"
                continue
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            } finally {
                connection?.disconnect()
            }
            ProviderDiagnosticResult("OSM", true, message = "OSM hız sınırı alınamadı: ${lastError ?: "tüm sunucular başarısız"}")
        }
    private fun parseSpeed(raw: String): Int? {
        if (raw.isBlank()) return null
        if (raw.equals("none", true) || raw.equals("signals", true) || raw.equals("variable", true)) return null
        val first = raw.split(';', '/', '|').firstOrNull()?.trim().orEmpty()
        if (first.equals("none", true) || first.equals("signals", true) || first.equals("variable", true)) return null
        val value = Regex("""[0-9]+(?:[.,][0-9]+)?""").find(first)?.value?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (first.contains("mph", true)) (value * 1.609344).roundToInt() else value.roundToInt()
    }
}
