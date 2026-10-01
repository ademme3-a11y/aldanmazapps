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
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
    }
    suspend fun diagnose(latitude: Double, longitude: Double): ProviderDiagnosticResult =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json][timeout:8];
                way(around:60,$latitude,$longitude)[highway][maxspeed];
                out tags center;
            """.trimIndent()
            val url = "https://overpass-api.de/api/interpreter?data=" +
                URLEncoder.encode(query, Charsets.UTF_8.name())
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
                    return@withContext ProviderDiagnosticResult("OSM", true, code, message = "OSM HTTP $code$suffix")
                }
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val elements = root.optJSONArray("elements")
                if (elements == null || elements.length() == 0) {
                    return@withContext ProviderDiagnosticResult("OSM", true, code, message = "OSM yanıt verdi ancak yakında maxspeed verisi bulunamadı")
                }
                var bestSpeed: Int? = null
                var bestRoad: String? = null
                for (i in 0 until elements.length()) {
                    val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                    val parsed = parseSpeed(tags.optString("maxspeed").trim()) ?: continue
                    if (bestSpeed == null) {
                        bestSpeed = parsed
                        bestRoad = tags.optString("name").takeIf { it.isNotBlank() }
                    }
                }
                ProviderDiagnosticResult("OSM", true, code, bestSpeed, bestRoad, message = if (bestSpeed != null) "OSM bağlantısı başarılı" else "OSM yol verisi verdi ancak maxspeed ayrıştırılamadı")
            } catch (e: Exception) {
                ProviderDiagnosticResult("OSM", true, message = "OSM bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                connection?.disconnect()
            }
        }
    private fun parseSpeed(raw: String): Int? {
        if (raw.isBlank()) return null
        if (raw.equals("none", true) || raw.equals("signals", true) || raw.equals("variable", true)) return null
        val value = Regex("""[0-9]+(?:[.,][0-9]+)?""").find(raw)?.value?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (raw.contains("mph", true)) (value * 1.609344).roundToInt() else value.roundToInt()
    }
}
