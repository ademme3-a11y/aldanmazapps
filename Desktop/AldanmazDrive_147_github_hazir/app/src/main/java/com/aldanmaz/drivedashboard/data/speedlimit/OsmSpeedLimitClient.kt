package com.aldanmaz.drivedashboard.data.speedlimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class OsmSpeedLimitClient {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 2_500
        private const val READ_TIMEOUT_MS = 4_500
        private val ENDPOINTS = listOf(
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }

    suspend fun diagnose(latitude: Double, longitude: Double): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val query = "[out:json][timeout:4];way(around:35,$latitude,$longitude)[highway][maxspeed];out tags;"
        val results = coroutineScope {
            ENDPOINTS.map { endpoint -> async { request(endpoint, query) } }.awaitAll()
        }
        results.firstOrNull { it.speedLimitKmh != null }?.let { return@withContext it }
        results.firstOrNull { it.responseCode in 200..299 }?.let { return@withContext it }
        results.firstOrNull { it.message.isNotBlank() } ?: ProviderDiagnosticResult("OSM", true, message = "OSM hız sınırı alınamadı")
    }

    private fun request(endpoint: String, query: String): ProviderDiagnosticResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("User-Agent", "AldanmazDrive/1.0")
            }
            val body = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) return ProviderDiagnosticResult("OSM", true, code, message = "OSM HTTP $code")
            val text = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val elements = JSONObject(text).optJSONArray("elements")
            if (elements == null || elements.length() == 0) return ProviderDiagnosticResult("OSM", true, code, message = "OSM yakında maxspeed verisi bulamadı")
            for (i in 0 until elements.length()) {
                val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                val speed = parseSpeed(tags.optString("maxspeed")) ?: continue
                return ProviderDiagnosticResult("OSM", true, code, speed, tags.optString("name").takeIf { it.isNotBlank() }, message = "OSM bağlantısı başarılı")
            }
            ProviderDiagnosticResult("OSM", true, code, message = "OSM yol verisi bulundu ancak maxspeed ayrıştırılamadı")
        } catch (e: Exception) {
            ProviderDiagnosticResult("OSM", true, message = "OSM bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
        } finally { connection?.disconnect() }
    }

    private fun parseSpeed(raw: String): Int? {
        if (raw.isBlank()) return null
        if (raw.equals("none", true) || raw.equals("signals", true) || raw.equals("variable", true)) return null
        val first = raw.split(';', '/', '|').firstOrNull()?.trim().orEmpty()
        val value = Regex("""[0-9]+(?:[.,][0-9]+)?""").find(first)?.value?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (first.contains("mph", true)) (value * 1.609344).roundToInt() else value.roundToInt()
    }
}
