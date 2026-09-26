package com.aldanmaz.drivedashboard.data.traffic

import com.aldanmaz.drivedashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TrafficAgentResult(
    val distanceMeters: Int = 0,
    val travelTimeSeconds: Int = 0,
    val noTrafficTravelTimeSeconds: Int = 0,
    val delaySeconds: Int = 0,
    val affectedDistanceMeters: Int = 0,
    val incidentCount: Int = 0,
    val severity: Int = 0,
    val first5DelaySeconds: Int = 0,
    val fiveTo10DelaySeconds: Int = 0,
    val tenTo20DelaySeconds: Int = 0,
    val roadNames: List<String> = emptyList()
)

private data class RoutePoint(val lat: Double, val lon: Double)

class TrafficAgentRepository {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 18_000
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    suspend fun checkAhead(latitude: Double, longitude: Double, headingDegrees: Float, lookAheadKm: Double = 20.0): TrafficAgentResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TOMTOM_API_KEY.trim()
        if (apiKey.isBlank()) return@withContext null
        val distances = listOf(lookAheadKm, minOf(12.0, lookAheadKm), minOf(7.0, lookAheadKm)).distinct()
        for (distanceKm in distances) {
            val end = destinationPoint(latitude, longitude, headingDegrees.toDouble(), distanceKm * 1000.0)
            val result = requestRoute(latitude, longitude, end.first, end.second, headingDegrees, apiKey)
            if (result != null) return@withContext result
        }
        null
    }

    private fun requestRoute(lat: Double, lon: Double, endLat: Double, endLon: Double, heading: Float, apiKey: String): TrafficAgentResult? {
        var connection: HttpURLConnection? = null
        return try {
            val route = "$lat,$lon:$endLat,$endLon"
            val key = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
            val url = URL(
                "https://api.tomtom.com/routing/1/calculateRoute/$route/json" +
                    "?key=$key&traffic=true&travelMode=car&routeType=fastest&departAt=now" +
                    "&computeTravelTimeFor=all&routeRepresentation=polyline" +
                    "&sectionType=traffic&sectionType=importantRoadStretch" +
                    "&vehicleHeading=${((heading.toInt() % 360) + 360) % 360}&language=tr-TR"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "ALD-DRIVE/1.0 Android"); setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return null
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) { null } finally { connection?.disconnect() }
    }

    private fun parse(jsonText: String): TrafficAgentResult? {
        val route = JSONObject(jsonText).optJSONArray("routes")?.optJSONObject(0) ?: return null
        val summary = route.optJSONObject("summary") ?: JSONObject()
        val points = readRoutePoints(route)
        val cumulative = cumulativeDistances(points)
        val sections = route.optJSONArray("sections") ?: JSONArray()
        val distanceMeters = summary.optInt("lengthInMeters", cumulative.lastOrNull()?.toInt() ?: 0)
        val travel = summary.optInt("travelTimeInSeconds", 0)
        val noTraffic = summary.optInt("noTrafficTravelTimeInSeconds", travel)
        val summaryDelay = maxOf(summary.optInt("trafficDelayInSeconds", 0), travel - noTraffic, 0)

        var affected = 0
        var incidents = 0
        var severity = 0
        var d0 = 0; var d1 = 0; var d2 = 0
        val roads = linkedSetOf<String>()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            if (!section.optString("sectionType").equals("TRAFFIC", true)) continue
            incidents++
            val startIndex = section.optInt("startPointIndex", 0).coerceIn(0, (cumulative.size - 1).coerceAtLeast(0))
            val endIndex = section.optInt("endPointIndex", startIndex).coerceIn(startIndex, (cumulative.size - 1).coerceAtLeast(startIndex))
            val startM = cumulative.getOrElse(startIndex) { 0.0 }
            val endM = cumulative.getOrElse(endIndex) { startM }
            affected += (endM - startM).coerceAtLeast(0.0).toInt()
            val delay = section.optInt("delayInSeconds", 0).coerceAtLeast(0)
            val magnitude = section.optInt("magnitudeOfDelay", 0).coerceIn(0, 4)
            val effective = section.optDouble("effectiveSpeedInKmh", -1.0)
            severity = maxOf(severity, magnitude, if (effective in 0.0..10.0) 3 else 0)
            val road = section.optString("streetName").ifBlank { section.optString("roadNumber") }
            if (road.isNotBlank()) roads += road
            if (startM < 5_000 && endM >= 0) d0 += delay
            if (startM < 10_000 && endM >= 5_000) d1 += delay
            if (startM < 20_000 && endM >= 10_000) d2 += delay
        }
        if (severity == 0) severity = when { summaryDelay >= 600 -> 4; summaryDelay >= 300 -> 3; summaryDelay >= 120 -> 2; summaryDelay > 0 -> 1; else -> 0 }
        // Bazı TomTom yanıtlarında bölüm gecikmesi yoktur; toplam gecikmeyi ilk 20 km'ye yine yansıt.
        if (d0 + d1 + d2 == 0 && summaryDelay > 0) d0 = summaryDelay
        return TrafficAgentResult(distanceMeters, travel, noTraffic, summaryDelay, affected, incidents, severity, d0, d1, d2, roads.toList())
    }

    private fun readRoutePoints(route: JSONObject): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        val legs = route.optJSONArray("legs") ?: return points
        for (i in 0 until legs.length()) {
            val legPoints = legs.optJSONObject(i)?.optJSONArray("points") ?: continue
            for (j in 0 until legPoints.length()) {
                val p = legPoints.optJSONObject(j) ?: continue
                points += RoutePoint(p.optDouble("latitude"), p.optDouble("longitude"))
            }
        }
        return points
    }

    private fun cumulativeDistances(points: List<RoutePoint>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val out = MutableList(points.size) { 0.0 }
        for (i in 1 until points.size) out[i] = out[i - 1] + haversineMeters(points[i - 1], points[i])
        return out
    }

    private fun haversineMeters(a: RoutePoint, b: RoutePoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val x = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(x), sqrt(1 - x))
    }

    private fun destinationPoint(latitude: Double, longitude: Double, bearingDegrees: Double, distanceMeters: Double): Pair<Double, Double> {
        val angular = distanceMeters / EARTH_RADIUS_METERS; val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(latitude); val lon1 = Math.toRadians(longitude)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}
