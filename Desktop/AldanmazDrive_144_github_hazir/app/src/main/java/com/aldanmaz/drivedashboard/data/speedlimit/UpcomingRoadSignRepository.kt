package com.aldanmaz.drivedashboard.data.speedlimit

import com.aldanmaz.drivedashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class UpcomingRoadSignKind {
    SPEED_LIMIT,
    ROAD_WORK,
    ROAD_CLOSURE,
    TRAFFIC_JAM,
    TUNNEL
}

data class UpcomingRoadSignInfo(
    val kind: UpcomingRoadSignKind,
    val distanceMeters: Int,
    val speedLimitKmh: Int? = null,
    val label: String,
    val roadName: String? = null
)

/**
 * ALDANMAZ Drive 21 - önümüzdeki 500 m için TomTom tabanlı yol/uyarı levhası katmanı.
 *
 * REST Routing yanıtında mevcutsa speedLimit/traffic/tunnel section'ları kullanılır.
 * Bazı TomTom REST hesaplarında speedLimit section dönmediği için hız limiti değişimini
 * ayrıca rota doğrultusunda 80/180/320/500 m ileride Reverse Geocoding ile doğrular.
 */
class UpcomingRoadSignRepository {

    companion object {
        private const val LOOK_AHEAD_METERS = 650.0
        private const val DISPLAY_MAX_METERS = 500
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val READ_TIMEOUT_MS = 7_000
    }

    suspend fun getUpcomingSigns(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float?,
        currentRoadSpeedLimitKmh: Int?
    ): List<UpcomingRoadSignInfo> = withContext(Dispatchers.IO) {
        val key = BuildConfig.TOMTOM_API_KEY.trim()
        val heading = headingDegrees?.takeIf { it.isFinite() } ?: return@withContext emptyList()
        if (key.isBlank()) return@withContext emptyList()

        val target = destinationPoint(latitude, longitude, heading.toDouble(), LOOK_AHEAD_METERS)

        val routeSigns =
            fetchRouteSigns(
                key = key,
                startLat = latitude,
                startLon = longitude,
                targetLat = target.first,
                targetLon = target.second,
                headingDegrees = heading.toDouble(),
                includeSpeedLimit = true
            ) ?: fetchRouteSigns(
                key = key,
                startLat = latitude,
                startLon = longitude,
                targetLat = target.first,
                targetLon = target.second,
                headingDegrees = heading.toDouble(),
                includeSpeedLimit = false
            ).orEmpty()

        val usefulRouteSigns = routeSigns.filterNot { sign ->
            sign.kind == UpcomingRoadSignKind.SPEED_LIMIT &&
                currentRoadSpeedLimitKmh != null &&
                sign.speedLimitKmh == currentRoadSpeedLimitKmh
        }

        val hasRouteSpeedLimit = usefulRouteSigns.any { it.kind == UpcomingRoadSignKind.SPEED_LIMIT }

        // 128: Freemium Reverse Geocoding kotasını tüketen 4 noktalı
        // ileri hız limiti taraması kaldırıldı. Hız levhası artık Routing API
        // speedLimit section'ından gelirse gösterilir; trafik/tünel/yol çalışması
        // işlevleri aynen devam eder. Böylece tek levha kontrolü 4 ek Reverse
        // Geocoding çağrısı üretmez.
        val speedSigns = emptyList<UpcomingRoadSignInfo>()

        (usefulRouteSigns + speedSigns)
            .filter { it.distanceMeters in 25..DISPLAY_MAX_METERS }
            .distinctBy { sign ->
                when (sign.kind) {
                    UpcomingRoadSignKind.SPEED_LIMIT -> "${sign.kind}_${sign.speedLimitKmh}"
                    else -> sign.kind.name
                }
            }
            .sortedWith(
                compareBy<UpcomingRoadSignInfo> { it.distanceMeters }
                    .thenBy { signPriority(it.kind) }
            )
            .take(2)
    }

    private fun signPriority(kind: UpcomingRoadSignKind): Int = when (kind) {
        UpcomingRoadSignKind.ROAD_CLOSURE -> 0
        UpcomingRoadSignKind.ROAD_WORK -> 1
        UpcomingRoadSignKind.TRAFFIC_JAM -> 2
        UpcomingRoadSignKind.SPEED_LIMIT -> 3
        UpcomingRoadSignKind.TUNNEL -> 4
    }

    private fun fetchRouteSigns(
        key: String,
        startLat: Double,
        startLon: Double,
        targetLat: Double,
        targetLon: Double,
        headingDegrees: Double,
        includeSpeedLimit: Boolean
    ): List<UpcomingRoadSignInfo>? {
        var connection: HttpURLConnection? = null
        return try {
            val start = String.format(Locale.US, "%.6f,%.6f", startLat, startLon)
            val target = String.format(Locale.US, "%.6f,%.6f", targetLat, targetLon)
            val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())

            val url = buildString {
                append("https://api.tomtom.com/routing/1/calculateRoute/")
                append(start)
                append(":")
                append(target)
                append("/json?key=")
                append(encodedKey)
                append("&traffic=true&travelMode=car&routeType=fastest")
                append("&vehicleHeading=")
                append(headingDegrees.roundToInt().coerceIn(0, 359))
                append("&routeRepresentation=polyline&computeTravelTimeFor=all")
                append("&sectionType=traffic&sectionType=tunnel")
                if (includeSpeedLimit) append("&sectionType=speedLimit")
                append("&language=tr-TR")
            }

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "AldanmazDrive/21")
            }

            if (connection.responseCode !in 200..299) return null

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            parseRouteSigns(JSONObject(text))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseRouteSigns(root: JSONObject): List<UpcomingRoadSignInfo> {
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return emptyList()
        val points = readRoutePoints(route)
        if (points.size < 2) return emptyList()
        val cumulative = cumulativeDistances(points)
        val sections = route.optJSONArray("sections") ?: return emptyList()

        return buildList {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val type = section.optString("sectionType").lowercase(Locale.US)
                val index = section.optInt("startPointIndex", -1)
                val distance = cumulative.getOrNull(index)?.roundToInt() ?: continue
                if (distance !in 0..DISPLAY_MAX_METERS) continue

                when (type) {
                    "traffic" -> {
                        val category = section.optString("simpleCategory").uppercase(Locale.US)
                        when (category) {
                            "ROAD_CLOSURE" -> add(
                                UpcomingRoadSignInfo(
                                    kind = UpcomingRoadSignKind.ROAD_CLOSURE,
                                    distanceMeters = distance,
                                    label = "Yol kapalı"
                                )
                            )
                            "ROAD_WORK" -> add(
                                UpcomingRoadSignInfo(
                                    kind = UpcomingRoadSignKind.ROAD_WORK,
                                    distanceMeters = distance,
                                    label = "Yol çalışması"
                                )
                            )
                            "JAM" -> add(
                                UpcomingRoadSignInfo(
                                    kind = UpcomingRoadSignKind.TRAFFIC_JAM,
                                    distanceMeters = distance,
                                    label = "Trafik yoğunluğu"
                                )
                            )
                        }
                    }

                    "tunnel" -> add(
                        UpcomingRoadSignInfo(
                            kind = UpcomingRoadSignKind.TUNNEL,
                            distanceMeters = distance,
                            label = "Tünel"
                        )
                    )

                    "speedlimit", "speed_limit" -> {
                        readSpeedLimitFromSection(section)?.let { limit ->
                            if (distance >= 25) {
                                add(
                                    UpcomingRoadSignInfo(
                                        kind = UpcomingRoadSignKind.SPEED_LIMIT,
                                        distanceMeters = distance,
                                        speedLimitKmh = limit,
                                        label = "Hız sınırı $limit"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readSpeedLimitFromSection(section: JSONObject): Int? {
        val direct = section.optInt("maxSpeedLimitInKmh", -1)
            .takeIf { it in 10..160 }
            ?: section.optInt("speedLimitInKmh", -1).takeIf { it in 10..160 }
        if (direct != null) return direct

        val raw = section.opt("speedLimit") ?: return null
        val numeric = when (raw) {
            is Number -> raw.toDouble()
            is String -> Regex("[0-9.]+").find(raw)?.value?.toDoubleOrNull()
            is JSONObject -> {
                val candidates = listOf("value", "kmph", "speedInKmh", "speedLimitInKmh")
                candidates.firstNotNullOfOrNull { key ->
                    raw.opt(key)?.toString()?.let { Regex("[0-9.]+").find(it)?.value?.toDoubleOrNull() }
                }
            }
            else -> null
        } ?: return null

        return numeric.roundToInt().takeIf { it in 10..160 }
    }

    private fun probeUpcomingSpeedLimitChanges(
        key: String,
        latitude: Double,
        longitude: Double,
        headingDegrees: Double,
        currentRoadSpeedLimitKmh: Int?
    ): List<UpcomingRoadSignInfo> {
        val probes = listOf(80, 180, 320, 500)
        val sampled = probes.mapNotNull { distance ->
            val point = destinationPoint(latitude, longitude, headingDegrees, distance.toDouble())
            reverseGeocodeSpeedLimit(key, point.first, point.second, headingDegrees.toFloat())
                ?.let { result -> Triple(distance, result.speedLimitKmh, result.roadName) }
        }

        if (sampled.isEmpty()) return emptyList()

        var baseline = currentRoadSpeedLimitKmh
        if (baseline == null) baseline = sampled.firstOrNull()?.second

        val result = mutableListOf<UpcomingRoadSignInfo>()
        var lastLimit = baseline

        for ((distance, limit, roadName) in sampled) {
            if (limit == null) continue
            if (lastLimit != null && limit != lastLimit) {
                result += UpcomingRoadSignInfo(
                    kind = UpcomingRoadSignKind.SPEED_LIMIT,
                    distanceMeters = distance,
                    speedLimitKmh = limit,
                    label = "Hız sınırı $limit",
                    roadName = roadName
                )
                lastLimit = limit
            } else if (lastLimit == null) {
                lastLimit = limit
            }
        }
        return result
    }

    private fun reverseGeocodeSpeedLimit(
        key: String,
        latitude: Double,
        longitude: Double,
        headingDegrees: Float
    ): RoadSpeedLimitResult? {
        var connection: HttpURLConnection? = null
        return try {
            val coordinate = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
            val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())
            val url =
                "https://api.tomtom.com/search/2/reverseGeocode/$coordinate.json" +
                    "?key=$encodedKey&returnSpeedLimit=true&radius=35&language=tr-TR&heading=$headingDegrees"

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "AldanmazDrive/21")
            }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val address = root.optJSONArray("addresses")
                ?.optJSONObject(0)
                ?.optJSONObject("address")
                ?: return null

            val speedText = address.optString("speedLimit").uppercase(Locale.US)
            val numeric = Regex("[0-9.]+").find(speedText)?.value?.toDoubleOrNull()
            val speedKmh = numeric?.let {
                if (speedText.contains("MPH")) (it * 1.609344).roundToInt() else it.roundToInt()
            }?.takeIf { it in 10..160 }

            RoadSpeedLimitResult(
                speedLimitKmh = speedKmh,
                roadName = address.optString("streetName").takeIf { it.isNotBlank() },
                routeNumbers = emptyList()
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private data class Point(val lat: Double, val lon: Double)

    private fun readRoutePoints(route: JSONObject): List<Point> {
        val result = mutableListOf<Point>()
        val legs = route.optJSONArray("legs") ?: return emptyList()
        for (legIndex in 0 until legs.length()) {
            val points = legs.optJSONObject(legIndex)?.optJSONArray("points") ?: continue
            for (i in 0 until points.length()) {
                val point = points.optJSONObject(i) ?: continue
                val lat = point.optDouble("latitude", Double.NaN)
                val lon = point.optDouble("longitude", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) {
                    if (result.lastOrNull()?.let { it.lat == lat && it.lon == lon } != true) {
                        result += Point(lat, lon)
                    }
                }
            }
        }
        return result
    }

    private fun cumulativeDistances(points: List<Point>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val out = MutableList(points.size) { 0.0 }
        for (i in 1 until points.size) {
            out[i] = out[i - 1] + distanceMeters(points[i - 1], points[i])
        }
        return out
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val earth = 6_371_000.0
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * earth * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun destinationPoint(
        latitude: Double,
        longitude: Double,
        headingDegrees: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val earthRadius = 6_371_000.0
        val angular = distanceMeters / earthRadius
        val bearing = Math.toRadians(headingDegrees)
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)

        val lat2 = asin(
            sin(lat1) * cos(angular) +
                cos(lat1) * sin(angular) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2)
        )

        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}
