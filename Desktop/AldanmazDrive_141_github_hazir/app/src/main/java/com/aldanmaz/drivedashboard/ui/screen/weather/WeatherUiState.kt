package com.aldanmaz.drivedashboard.ui.screen.weather

import com.aldanmaz.drivedashboard.data.weather.WeatherCondition
import com.aldanmaz.drivedashboard.data.weather.HourlyWeatherForecast
import com.aldanmaz.drivedashboard.data.weather.DailyWeatherForecast
import java.util.Locale

data class WeatherUiState(
    val isLoading: Boolean = false,
    val hasData: Boolean = false,

    val cityName: String? = null,

    val temperatureCelsius: Double? = null,
    val apparentTemperatureCelsius: Double? = null,
    val relativeHumidityPercent: Int? = null,

    val windSpeedKmh: Double? = null,
    val windDirectionDegrees: Int? = null,
    val windGustKmh: Double? = null,

    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val isDay: Boolean = true,

    val observationTime: String? = null,
    val lastUpdatedEpochMillis: Long? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    val errorMessage: String? = null,
    val hourlyForecast: List<HourlyWeatherForecast> = emptyList(),
    val dailyForecast: List<DailyWeatherForecast> = emptyList(),

    // 4-C:
    // true ise ekranda canlı internet verisi yerine
    // cihazda saklanan son başarılı hava verisi gösteriliyor.
    val isUsingCachedData: Boolean = false
) {

    val windDirectionText: String
        get() {
            val degrees = windDirectionDegrees ?: return "--"

            val normalized =
                ((degrees % 360) + 360) % 360

            return when {
                normalized >= 338 || normalized < 23 -> "K"
                normalized < 68 -> "KD"
                normalized < 113 -> "D"
                normalized < 158 -> "GD"
                normalized < 203 -> "G"
                normalized < 248 -> "GB"
                normalized < 293 -> "B"
                else -> "KB"
            }
        }

    val temperatureText: String
        get() =
            temperatureCelsius?.let {
                String.format(
                    Locale.getDefault(),
                    "%.0f°",
                    it
                )
            } ?: "--°"

    val apparentTemperatureText: String
        get() =
            apparentTemperatureCelsius?.let {
                String.format(
                    Locale.getDefault(),
                    "%.0f°",
                    it
                )
            } ?: "--°"

    val humidityText: String
        get() =
            relativeHumidityPercent?.let {
                "%$it"
            } ?: "--"

    val windSpeedText: String
        get() =
            windSpeedKmh?.let {
                String.format(
                    Locale.getDefault(),
                    "%.0f",
                    it
                )
            } ?: "--"

    val windGustText: String
        get() =
            windGustKmh?.let {
                String.format(
                    Locale.getDefault(),
                    "%.0f",
                    it
                )
            } ?: "--"
}