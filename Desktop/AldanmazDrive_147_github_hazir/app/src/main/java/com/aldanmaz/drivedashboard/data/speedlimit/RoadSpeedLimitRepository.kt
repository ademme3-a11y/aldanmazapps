package com.aldanmaz.drivedashboard.data.speedlimit

import android.content.Context
import com.aldanmaz.drivedashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class RoadSpeedLimitResult(
    val speedLimitKmh: Int?,
    val roadName: String?,
    val routeNumbers: List<String> = emptyList()
)

data class TomTomDiagnosticResult(
    val apiKeyPresent: Boolean,
    val responseCode: Int? = null,
    val speedLimitKmh: Int? = null,
    val roadName: String? = null,
    val routeNumbers: List<String> = emptyList(),
    val message: String
)

class RoadSpeedLimitRepository(context: Context? = null) {

    private val cachePrefs = context?.applicationContext?.getSharedPreferences("tomtom_speed_cache_131", Context.MODE_PRIVATE)

    @Volatile
    var lastRequestQuotaBlocked: Boolean = false
        private set

    @Volatile
    var lastResponseCode: Int? = null
        private set

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val CACHE_MAX_ENTRIES = 240
        private const val CACHE_MAX_AGE_MS = 45L * 24L * 60L * 60L * 1000L
    }

    suspend fun getSpeedLimit(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float? = null
    ): RoadSpeedLimitResult? =
        withContext(Dispatchers.IO) {

            readCached(latitude, longitude, headingDegrees)?.let { cached ->
                lastResponseCode = null
                lastRequestQuotaBlocked = false
                return@withContext cached
            }

            val apiKey = BuildConfig.TOMTOM_API_KEY.trim()

            if (apiKey.isBlank()) {
                lastResponseCode = null
                lastRequestQuotaBlocked = false
                return@withContext null
            }

            var connection: HttpURLConnection? = null

            try {
                val coordinate = "$latitude,$longitude"

                val urlBuilder =
                    StringBuilder(
                        "https://api.tomtom.com/search/2/" +
                                "reverseGeocode/" +
                                "$coordinate.json"
                    )

                urlBuilder.append(
                    "?key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}"
                )

                urlBuilder.append("&returnSpeedLimit=true")
                urlBuilder.append("&returnRoadUse=true")
                urlBuilder.append("&radius=25")
                urlBuilder.append("&language=tr-TR")

                if (headingDegrees != null) {
                    urlBuilder.append(
                        "&heading=${headingDegrees.coerceIn(-360f, 360f)}"
                    )
                }

                connection =
                    (URL(urlBuilder.toString())
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        setRequestProperty(
                            "User-Agent",
                            "AldanmazDrive/1.0"
                        )
                    }

                val responseCode = connection.responseCode
                lastResponseCode = responseCode

                if (
                    responseCode !in
                    HttpURLConnection.HTTP_OK until
                    HttpURLConnection.HTTP_MULT_CHOICE
                ) {
                    val errorText = runCatching {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    lastRequestQuotaBlocked =
                        responseCode == HttpURLConnection.HTTP_FORBIDDEN &&
                            errorText.contains("InsufficientFunds", ignoreCase = true)
                    return@withContext null
                }

                lastRequestQuotaBlocked = false

                val responseText =
                    connection.inputStream
                        .bufferedReader()
                        .use { reader ->
                            reader.readText()
                        }

                parseResponse(responseText)?.also { result ->
                    if (result.speedLimitKmh != null) {
                        writeCache(latitude, longitude, headingDegrees, result)
                    }
                }

            } catch (_: Exception) {
                lastResponseCode = null
                lastRequestQuotaBlocked = false
                null
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun diagnose(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float? = null
    ): TomTomDiagnosticResult =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TOMTOM_API_KEY.trim()
            if (apiKey.isBlank()) {
                return@withContext TomTomDiagnosticResult(
                    apiKeyPresent = false,
                    message = "TomTom API anahtarı bulunamadı"
                )
            }

            var connection: HttpURLConnection? = null
            try {
                val coordinate = "$latitude,$longitude"
                val urlBuilder = StringBuilder(
                    "https://api.tomtom.com/search/2/reverseGeocode/$coordinate.json"
                )
                urlBuilder.append("?key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}")
                urlBuilder.append("&returnSpeedLimit=true")
                urlBuilder.append("&returnRoadUse=true")
                urlBuilder.append("&radius=25")
                urlBuilder.append("&language=tr-TR")
                if (headingDegrees != null) {
                    urlBuilder.append("&heading=${headingDegrees.coerceIn(-360f, 360f)}")
                }

                connection = (URL(urlBuilder.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "AldanmazDrive/1.0")
                }

                val code = connection.responseCode
                if (code !in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) {
                    val detail = runCatching {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()?.take(220)
                    return@withContext TomTomDiagnosticResult(
                        apiKeyPresent = true,
                        responseCode = code,
                        message = buildString {
                            append("TomTom HTTP ")
                            append(code)
                            if (!detail.isNullOrBlank()) {
                                append(": ")
                                append(detail)
                            }
                        }
                    )
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseResponse(responseText)
                if (parsed == null) {
                    TomTomDiagnosticResult(
                        apiKeyPresent = true,
                        responseCode = code,
                        message = "TomTom yanıt verdi ancak yol verisi ayrıştırılamadı"
                    )
                } else {
                    TomTomDiagnosticResult(
                        apiKeyPresent = true,
                        responseCode = code,
                        speedLimitKmh = parsed.speedLimitKmh,
                        roadName = parsed.roadName,
                        routeNumbers = parsed.routeNumbers,
                        message = if (parsed.speedLimitKmh != null) {
                            "TomTom bağlantısı başarılı"
                        } else {
                            "TomTom yol bilgisini verdi; bu noktada hız sınırı alanı yok"
                        }
                    )
                }
            } catch (e: Exception) {
                TomTomDiagnosticResult(
                    apiKeyPresent = true,
                    message = "TomTom bağlantı hatası: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                connection?.disconnect()
            }
        }


    private fun cacheCell(latitude: Double, longitude: Double): Pair<Int, Int> =
        (latitude * 1000.0).roundToInt() to (longitude * 1000.0).roundToInt()

    private fun headingBucket(headingDegrees: Float?): Int {
        if (headingDegrees == null || headingDegrees.isNaN()) return -1
        val normalized = ((headingDegrees % 360f) + 360f) % 360f
        return ((normalized + 22.5f) / 45f).toInt() % 8
    }

    private fun readCached(latitude: Double, longitude: Double, headingDegrees: Float?): RoadSpeedLimitResult? {
        val prefs = cachePrefs ?: return null
        val raw = prefs.getString("entries", null).orEmpty()
        if (raw.isBlank()) return null
        val (latCell, lonCell) = cacheCell(latitude, longitude)
        val heading = headingBucket(headingDegrees)
        val now = System.currentTimeMillis()
        return runCatching {
            val array = JSONArray(raw)
            var best: JSONObject? = null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val age = now - item.optLong("timestamp", 0L)
                if (age !in 0..CACHE_MAX_AGE_MS) continue
                if (item.optInt("latCell") != latCell || item.optInt("lonCell") != lonCell) continue
                val cachedHeading = item.optInt("heading", -1)
                if (heading >= 0 && cachedHeading >= 0 && heading != cachedHeading) continue
                if (best == null || item.optLong("timestamp") > best!!.optLong("timestamp")) best = item
            }
            best?.let { item ->
                RoadSpeedLimitResult(
                    speedLimitKmh = item.optInt("speed", 0).takeIf { it > 0 },
                    roadName = item.optString("road").takeIf { it.isNotBlank() },
                    routeNumbers = buildList {
                        val routes = item.optJSONArray("routes")
                        if (routes != null) for (index in 0 until routes.length()) routes.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    },
                )
            }
        }.getOrNull()
    }

    private fun writeCache(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float?,
        result: RoadSpeedLimitResult,
    ) {
        val prefs = cachePrefs ?: return
        val speed = result.speedLimitKmh ?: return
        val (latCell, lonCell) = cacheCell(latitude, longitude)
        val heading = headingBucket(headingDegrees)
        val now = System.currentTimeMillis()
        val items = runCatching {
            val old = JSONArray(prefs.getString("entries", "[]"))
            buildList<JSONObject> {
                for (i in 0 until old.length()) {
                    val item = old.optJSONObject(i) ?: continue
                    val age = now - item.optLong("timestamp", 0L)
                    val same = item.optInt("latCell") == latCell && item.optInt("lonCell") == lonCell && item.optInt("heading", -1) == heading
                    if (age in 0..CACHE_MAX_AGE_MS && !same) add(item)
                }
            }
        }.getOrDefault(emptyList()).toMutableList()

        items.add(0, JSONObject().apply {
            put("latCell", latCell)
            put("lonCell", lonCell)
            put("heading", heading)
            put("timestamp", now)
            put("speed", speed)
            put("road", result.roadName.orEmpty())
            put("routes", JSONArray(result.routeNumbers))
        })
        val array = JSONArray()
        items.sortedByDescending { it.optLong("timestamp") }.take(CACHE_MAX_ENTRIES).forEach(array::put)
        prefs.edit().putString("entries", array.toString()).apply()
    }

    private fun parseResponse(
        jsonText: String
    ): RoadSpeedLimitResult? {

        val root = JSONObject(jsonText)

        val addresses =
            root.optJSONArray("addresses")
                ?: return null

        if (addresses.length() == 0) {
            return null
        }

        val firstResult =
            addresses.optJSONObject(0)
                ?: return null

        val address =
            firstResult.optJSONObject("address")
                ?: return null

        val roadName =
            address
                .optString("streetName")
                .takeIf { it.isNotBlank() }

        /*
         * TomTom Reverse Geocoding REST yanıtında speedLimit,
         * addresses[0] nesnesinin içinde değil,
         * addresses[0].address nesnesinin içindedir.
         *
         * Örnek:
         * addresses[0].address.speedLimit = "50.00KPH"
         */
        val speedLimitKmh =
            readSpeedLimitKmh(address)

        /*
         * routeNumbers da aynı şekilde address nesnesindedir.
         */
        val routeNumbers =
            readRouteNumbers(address)

        return RoadSpeedLimitResult(
            speedLimitKmh = speedLimitKmh,
            roadName = roadName,
            routeNumbers = routeNumbers
        )
    }

    private fun readSpeedLimitKmh(
        address: JSONObject
    ): Int? {

        val speedLimitText =
            address
                .optString("speedLimit")
                .trim()

        if (speedLimitText.isBlank()) {
            return null
        }

        val normalized =
            speedLimitText.uppercase()

        val numericValue =
            Regex("""[\d.]+""")
                .find(normalized)
                ?.value
                ?.toDoubleOrNull()
                ?: return null

        return when {
            normalized.contains("MPH") ->
                (numericValue * 1.609344)
                    .roundToInt()

            normalized.contains("KPH") ->
                numericValue.roundToInt()

            else ->
                numericValue.roundToInt()
        }
    }

    private fun readRouteNumbers(
        address: JSONObject
    ): List<String> {

        val array =
            address.optJSONArray("routeNumbers")
                ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val value =
                    array.optString(index)

                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }
}
