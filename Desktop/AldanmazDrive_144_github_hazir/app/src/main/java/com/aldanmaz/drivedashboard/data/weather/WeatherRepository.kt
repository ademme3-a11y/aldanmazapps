package com.aldanmaz.drivedashboard.data.weather

import android.content.Context
import android.location.Geocoder
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
    ): WeatherData =
        withContext(Dispatchers.IO) {
            require(latitude in -90.0..90.0) {
                "Geçersiz enlem değeri."
            }

            require(longitude in -180.0..180.0) {
                "Geçersiz boylam değeri."
            }

            val query =
                buildString {
                    append("?latitude=")
                    append(latitude)
                    append("&longitude=")
                    append(longitude)
                    append("&current=")
                    append("temperature_2m,")
                    append("apparent_temperature,")
                    append("relative_humidity_2m,")
                    append("weather_code,")
                    append("wind_speed_10m,")
                    append("wind_direction_10m,")
                    append("wind_gusts_10m,")
                    append("is_day")
                    append("&hourly=")
                    append("temperature_2m,")
                    append("precipitation_probability,")
                    append("visibility,")
                    append("weather_code,")
                    append("wind_speed_10m")
                    append("&daily=")
                    append("weather_code,")
                    append("temperature_2m_max,")
                    append("temperature_2m_min,")
                    append("sunrise,")
                    append("sunset,")
                    append("precipitation_probability_max")
                    append("&forecast_days=5")
                    append("&timezone=auto")
                }

            val compactQuery =
                buildString {
                    append("?latitude=")
                    append(latitude)
                    append("&longitude=")
                    append(longitude)
                    append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day")
                    append("&timezone=auto")
                }

            val urls = listOf(
                "https://api.open-meteo.com/v1/forecast$query",
                "https://api.open-meteo.com/v1/forecast$compactQuery",
                // Bazı araç multimedya Android'lerinde eski sertifika deposu/TLS el sıkışması sorun çıkarabiliyor.
                // Yalnızca hava verisi için, HTTPS denemeleri başarısız olursa domain-sınırlı HTTP yedeği kullanılır.
                "http://api.open-meteo.com/v1/forecast$compactQuery"
            )

            var lastError: Throwable? = null
            var responseText: String? = null

            for (requestUrl in urls) {
                for (attempt in 0..2) {
                    if (!responseText.isNullOrBlank()) break
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            instanceFollowRedirects = true
                            useCaches = false
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Accept-Encoding", "identity")
                            setRequestProperty("Connection", "close")
                            setRequestProperty("User-Agent", "AldanmazDrive/1.0 Android")
                        }
                        val responseCode = connection.responseCode
                        if (responseCode !in 200..299) {
                            throw WeatherException("Hava servisi HTTP $responseCode hatası döndürdü.")
                        }
                        responseText = connection.inputStream
                            .bufferedReader(Charsets.UTF_8)
                            .use { it.readText() }
                    } catch (error: Throwable) {
                        lastError = error
                        if (attempt < 2) Thread.sleep(800L + attempt * 700L)
                    } finally {
                        connection?.disconnect()
                    }
                }
                if (!responseText.isNullOrBlank()) break
            }

            if (responseText.isNullOrBlank()) {
                // Eski ALD uygulamasındaki URL.readText yaklaşımını araç üniteleri için son yedek olarak dene.
                responseText = runCatching {
                    URL("https://api.open-meteo.com/v1/forecast$compactQuery").readText(Charsets.UTF_8)
                }.getOrElse { error ->
                    lastError = error
                    null
                }
            }

            val cityName = runCatching {
                resolveCityName(latitude = latitude, longitude = longitude)
            }.getOrNull()

            if (!responseText.isNullOrBlank()) {
                parseWeather(
                    jsonText = responseText,
                    latitude = latitude,
                    longitude = longitude,
                    cityName = cityName
                )
            } else {
                // Open-Meteo belirli bir araç ağı/DNS'inde erişilemiyorsa ikinci bağımsız servis.
                runCatching {
                    fetchWttrCurrentWeather(latitude, longitude, cityName)
                }.getOrElse { fallbackError ->
                    throw WeatherException(
                        message = fallbackError.message
                            ?: lastError?.message
                            ?: "Hava bilgisi alınamadı.",
                        cause = fallbackError
                    )
                }
            }
        }

    fun saveLastSuccessfulWeather(
        weather: WeatherData
    ) {
        val json =
            JSONObject().apply {

                put(
                    "latitude",
                    weather.latitude
                )

                put(
                    "longitude",
                    weather.longitude
                )

                put(
                    "cityName",
                    weather.cityName
                )

                put(
                    "temperatureCelsius",
                    weather.temperatureCelsius
                )

                put(
                    "apparentTemperatureCelsius",
                    weather.apparentTemperatureCelsius
                )

                put(
                    "relativeHumidityPercent",
                    weather.relativeHumidityPercent
                )

                put(
                    "windSpeedKmh",
                    weather.windSpeedKmh
                )

                put(
                    "windDirectionDegrees",
                    weather.windDirectionDegrees
                )

                put(
                    "windGustKmh",
                    weather.windGustKmh
                )

                put(
                    "weatherCode",
                    weather.weatherCode
                )

                put(
                    "condition",
                    weather.condition.name
                )

                put(
                    "isDay",
                    weather.isDay
                )

                put(
                    "observationTime",
                    weather.observationTime
                )

                put(
                    "fetchedAtEpochMillis",
                    weather.fetchedAtEpochMillis
                )
            }

        appContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LAST_WEATHER,
                json.toString()
            )
            .apply()
    }

    fun loadLastSuccessfulWeather(): WeatherData? {

        val text =
            appContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_LAST_WEATHER,
                    null
                )
                ?: return null

        return runCatching {

            val json =
                JSONObject(text)

            val weatherCode =
                json.getInt(
                    "weatherCode"
                )

            WeatherData(
                latitude =
                    json.getDouble(
                        "latitude"
                    ),

                longitude =
                    json.getDouble(
                        "longitude"
                    ),

                cityName =
                    if (
                        json.isNull(
                            "cityName"
                        )
                    ) {
                        null
                    } else {
                        json
                            .optString(
                                "cityName"
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                    },

                temperatureCelsius =
                    json.getDouble(
                        "temperatureCelsius"
                    ),

                apparentTemperatureCelsius =
                    json.getDouble(
                        "apparentTemperatureCelsius"
                    ),

                relativeHumidityPercent =
                    json.getInt(
                        "relativeHumidityPercent"
                    ),

                windSpeedKmh =
                    json.getDouble(
                        "windSpeedKmh"
                    ),

                windDirectionDegrees =
                    json.getInt(
                        "windDirectionDegrees"
                    ),

                windGustKmh =
                    json.getDouble(
                        "windGustKmh"
                    ),

                weatherCode =
                    weatherCode,

                condition =
                    runCatching {
                        WeatherCondition.valueOf(
                            json.optString(
                                "condition"
                            )
                        )
                    }.getOrElse {
                        WeatherCondition
                            .fromWmoCode(
                                weatherCode
                            )
                    },

                isDay =
                    json.optBoolean(
                        "isDay",
                        true
                    ),

                observationTime =
                    if (
                        json.isNull(
                            "observationTime"
                        )
                    ) {
                        null
                    } else {
                        json
                            .optString(
                                "observationTime"
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                    },

                fetchedAtEpochMillis =
                    json.getLong(
                        "fetchedAtEpochMillis"
                    )
            )

        }.getOrNull()
    }

    private fun fetchWttrCurrentWeather(
        latitude: Double,
        longitude: Double,
        cityName: String?
    ): WeatherData {
        val endpoints = listOf(
            "https://wttr.in/$latitude,$longitude?format=j1",
            "http://wttr.in/$latitude,$longitude?format=j1"
        )
        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", "close")
                    setRequestProperty("User-Agent", "AldanmazDrive/1.0 Android")
                }
                if (connection.responseCode !in 200..299) {
                    throw WeatherException("Yedek hava servisi HTTP ${connection.responseCode} hatası döndürdü.")
                }
                val root = JSONObject(
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                )
                val current = root.optJSONArray("current_condition")?.optJSONObject(0)
                    ?: throw WeatherException("Yedek hava servisi güncel veri döndürmedi.")
                val description = current.optJSONArray("weatherDesc")
                    ?.optJSONObject(0)
                    ?.optString("value")
                    .orEmpty()
                val condition = conditionFromDescription(description)
                return WeatherData(
                    latitude = latitude,
                    longitude = longitude,
                    cityName = cityName,
                    temperatureCelsius = current.optDouble("temp_C", 0.0),
                    apparentTemperatureCelsius = current.optDouble("FeelsLikeC", current.optDouble("temp_C", 0.0)),
                    relativeHumidityPercent = current.optInt("humidity", 0),
                    windSpeedKmh = current.optDouble("windspeedKmph", 0.0),
                    windDirectionDegrees = current.optInt("winddirDegree", 0),
                    windGustKmh = current.optDouble("WindGustKmph", current.optDouble("windspeedKmph", 0.0)),
                    weatherCode = -1,
                    condition = condition,
                    isDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 7..18,
                    observationTime = current.optString("localObsDateTime").takeIf { it.isNotBlank() },
                    fetchedAtEpochMillis = System.currentTimeMillis()
                )
            } catch (error: Throwable) {
                lastError = error
            } finally {
                connection?.disconnect()
            }
        }
        throw WeatherException(lastError?.message ?: "Yedek hava servisine ulaşılamadı.", lastError)
    }

    private fun conditionFromDescription(description: String): WeatherCondition {
        val value = description.lowercase(Locale.ROOT)
        return when {
            "thunder" in value || "storm" in value -> WeatherCondition.THUNDERSTORM
            "snow" in value || "sleet" in value -> WeatherCondition.SNOW
            "rain" in value || "drizzle" in value || "shower" in value -> WeatherCondition.RAIN
            "fog" in value || "mist" in value -> WeatherCondition.FOG
            "cloud" in value || "overcast" in value -> WeatherCondition.CLOUDY
            "clear" in value || "sunny" in value -> WeatherCondition.CLEAR
            else -> WeatherCondition.UNKNOWN
        }
    }

    private fun parseWeather(
        jsonText: String,
        latitude: Double,
        longitude: Double,
        cityName: String?,
    ): WeatherData {

        val root =
            JSONObject(
                jsonText
            )

        val current =
            root.optJSONObject(
                "current"
            )
                ?: throw WeatherException(
                    "Hava servisi güncel veri döndürmedi."
                )

        val weatherCode =
            current.requiredInt(
                "weather_code"
            )

        return WeatherData(
            latitude =
                latitude,

            longitude =
                longitude,

            cityName =
                cityName,

            temperatureCelsius =
                current.requiredDouble(
                    "temperature_2m"
                ),

            apparentTemperatureCelsius =
                current.requiredDouble(
                    "apparent_temperature"
                ),

            relativeHumidityPercent =
                current.requiredInt(
                    "relative_humidity_2m"
                ),

            windSpeedKmh =
                current.requiredDouble(
                    "wind_speed_10m"
                ),

            windDirectionDegrees =
                current.requiredInt(
                    "wind_direction_10m"
                ),

            windGustKmh =
                current.requiredDouble(
                    "wind_gusts_10m"
                ),

            weatherCode =
                weatherCode,

            condition =
                WeatherCondition
                    .fromWmoCode(
                        weatherCode
                    ),

            isDay =
                current.optInt(
                    "is_day",
                    1
                ) == 1,

            observationTime =
                current
                    .optString(
                        "time"
                    )
                    .takeIf {
                        it.isNotBlank()
                    },

            fetchedAtEpochMillis =
                System.currentTimeMillis(),

            hourlyForecast = parseHourlyForecast(root),
            dailyForecast = parseDailyForecast(root)
        )
    }


    private fun parseHourlyForecast(root: JSONObject): List<HourlyWeatherForecast> {
        val hourly = root.optJSONObject("hourly") ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temperatures = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val precipitation = hourly.optJSONArray("precipitation_probability")
        val visibility = hourly.optJSONArray("visibility")
        val codes = hourly.optJSONArray("weather_code")
        val winds = hourly.optJSONArray("wind_speed_10m")
        val count = minOf(times.length(), temperatures.length(), 24)
        return (0 until count).map { index ->
            HourlyWeatherForecast(
                time = times.optString(index),
                temperatureCelsius = temperatures.optDouble(index, Double.NaN).takeUnless { it.isNaN() },
                precipitationProbabilityPercent = precipitation?.optInt(index),
                visibilityMeters = visibility?.optDouble(index, Double.NaN)?.takeUnless { it.isNaN() },
                condition = WeatherCondition.fromWmoCode(codes?.optInt(index, -1) ?: -1),
                windSpeedKmh = winds?.optDouble(index, Double.NaN)?.takeUnless { it.isNaN() }
            )
        }
    }

    private fun parseDailyForecast(root: JSONObject): List<DailyWeatherForecast> {
        val daily = root.optJSONObject("daily") ?: return emptyList()
        val dates = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val maxTemps = daily.optJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")
        val precipitation = daily.optJSONArray("precipitation_probability_max")
        val count = minOf(dates.length(), 5)
        return (0 until count).map { index ->
            DailyWeatherForecast(
                date = dates.optString(index),
                condition = WeatherCondition.fromWmoCode(codes?.optInt(index, -1) ?: -1),
                maxTemperatureCelsius = maxTemps?.optDouble(index, Double.NaN)?.takeUnless { it.isNaN() },
                minTemperatureCelsius = minTemps?.optDouble(index, Double.NaN)?.takeUnless { it.isNaN() },
                sunrise = sunrises?.optString(index)?.takeIf { it.isNotBlank() },
                sunset = sunsets?.optString(index)?.takeIf { it.isNotBlank() },
                precipitationProbabilityPercent = precipitation?.optInt(index)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveCityName(
        latitude: Double,
        longitude: Double,
    ): String? =
        runCatching {

            if (!Geocoder.isPresent()) {
                return@runCatching null
            }

            val geocoder =
                Geocoder(
                    appContext,
                    Locale(
                        "tr",
                        "TR"
                    )
                )

            val address =
                geocoder
                    .getFromLocation(
                        latitude,
                        longitude,
                        1
                    )
                    ?.firstOrNull()

            address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea

        }.getOrNull()

    private fun JSONObject.requiredDouble(
        key: String
    ): Double {

        if (
            !has(key) ||
            isNull(key)
        ) {
            throw WeatherException(
                "Hava verisinde '$key' alanı eksik."
            )
        }

        return getDouble(
            key
        )
    }

    private fun JSONObject.requiredInt(
        key: String
    ): Int {

        if (
            !has(key) ||
            isNull(key)
        ) {
            throw WeatherException(
                "Hava verisinde '$key' alanı eksik."
            )
        }

        return getInt(
            key
        )
    }

    private companion object {

        const val CONNECT_TIMEOUT_MS =
            10_000

        const val READ_TIMEOUT_MS =
            12_000

        const val PREFS_NAME =
            "weather_cache"

        const val KEY_LAST_WEATHER =
            "last_successful_weather"
    }
}

data class WeatherData(
    val latitude: Double,
    val longitude: Double,
    val cityName: String?,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val relativeHumidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val windGustKmh: Double,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val observationTime: String?,
    val fetchedAtEpochMillis: Long,
    val hourlyForecast: List<HourlyWeatherForecast> = emptyList(),
    val dailyForecast: List<DailyWeatherForecast> = emptyList(),
)

data class HourlyWeatherForecast(
    val time: String,
    val temperatureCelsius: Double?,
    val precipitationProbabilityPercent: Int?,
    val visibilityMeters: Double?,
    val condition: WeatherCondition,
    val windSpeedKmh: Double?
)

data class DailyWeatherForecast(
    val date: String,
    val condition: WeatherCondition,
    val maxTemperatureCelsius: Double?,
    val minTemperatureCelsius: Double?,
    val sunrise: String?,
    val sunset: String?,
    val precipitationProbabilityPercent: Int?
)

enum class WeatherCondition {

    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN;

    companion object {

        fun fromWmoCode(
            code: Int
        ): WeatherCondition =
            when (code) {

                0 ->
                    CLEAR

                1, 2 ->
                    PARTLY_CLOUDY

                3 ->
                    CLOUDY

                45, 48 ->
                    FOG

                51, 53, 55 ->
                    DRIZZLE

                56, 57 ->
                    FREEZING_RAIN

                61, 63, 65 ->
                    RAIN

                66, 67 ->
                    FREEZING_RAIN

                71, 73, 75, 77 ->
                    SNOW

                80, 81, 82 ->
                    RAIN_SHOWERS

                85, 86 ->
                    SNOW_SHOWERS

                95, 96, 99 ->
                    THUNDERSTORM

                else ->
                    UNKNOWN
            }
    }
}

class WeatherException(
    message: String,
    cause: Throwable? = null,
) : Exception(
    message,
    cause
)