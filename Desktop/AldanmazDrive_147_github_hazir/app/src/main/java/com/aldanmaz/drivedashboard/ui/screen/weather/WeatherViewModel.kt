package com.aldanmaz.drivedashboard.ui.screen.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.weather.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeatherViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        repository.loadLastSuccessfulWeather()?.let { weather ->
            _uiState.value = _uiState.value.copy(
                hasData = true, cityName = weather.cityName,
                temperatureCelsius = weather.temperatureCelsius,
                apparentTemperatureCelsius = weather.apparentTemperatureCelsius,
                relativeHumidityPercent = weather.relativeHumidityPercent,
                windSpeedKmh = weather.windSpeedKmh,
                windDirectionDegrees = weather.windDirectionDegrees,
                windGustKmh = weather.windGustKmh, condition = weather.condition,
                isDay = weather.isDay, observationTime = weather.observationTime,
                lastUpdatedEpochMillis = weather.fetchedAtEpochMillis,
                latitude = weather.latitude, longitude = weather.longitude
            )
        }
    }

    /**
     * Konum burada dinlenmez. Enlem/boylam ana sürüş ekranındaki merkezi GPS
     * kaynağından bu metoda aktarılır.
     */
    fun refreshWeather(
        latitude: Double,
        longitude: Double,
        force: Boolean = false,
    ) {
        if (refreshJob?.isActive == true) return

        val current = _uiState.value
        val now = System.currentTimeMillis()

        val sameLocation =
            current.latitude?.let { kotlin.math.abs(it - latitude) < 0.02 } == true &&
                current.longitude?.let { kotlin.math.abs(it - longitude) < 0.02 } == true

        val recentlyUpdated =
            current.lastUpdatedEpochMillis?.let {
                now - it < MIN_REFRESH_INTERVAL_MS
            } == true

        if (!force && current.hasData && sameLocation && recentlyUpdated) {
            return
        }

        refreshJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        latitude = latitude,
                        longitude = longitude,
                    )
                }

                runCatching {
                    repository.getCurrentWeather(
                        latitude = latitude,
                        longitude = longitude,
                    )
                }.onSuccess { weather ->
                    repository.saveLastSuccessfulWeather(weather)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasData = true,
                            cityName = weather.cityName,
                            temperatureCelsius = weather.temperatureCelsius,
                            apparentTemperatureCelsius =
                                weather.apparentTemperatureCelsius,
                            relativeHumidityPercent =
                                weather.relativeHumidityPercent,
                            windSpeedKmh = weather.windSpeedKmh,
                            windDirectionDegrees = weather.windDirectionDegrees,
                            windGustKmh = weather.windGustKmh,
                            condition = weather.condition,
                            isDay = weather.isDay,
                            observationTime = weather.observationTime,
                            lastUpdatedEpochMillis = weather.fetchedAtEpochMillis,
                            latitude = weather.latitude,
                            longitude = weather.longitude,
                            errorMessage = null,
                            hourlyForecast = weather.hourlyForecast,
                            dailyForecast = weather.dailyForecast,
                        )
                    }
                }.onFailure { error ->
                    val cached = repository.loadLastSuccessfulWeather()
                    _uiState.update { state ->
                        if (!state.hasData && cached != null) {
                            state.copy(
                                isLoading = false, hasData = true, cityName = cached.cityName,
                                temperatureCelsius = cached.temperatureCelsius,
                                apparentTemperatureCelsius = cached.apparentTemperatureCelsius,
                                relativeHumidityPercent = cached.relativeHumidityPercent,
                                windSpeedKmh = cached.windSpeedKmh,
                                windDirectionDegrees = cached.windDirectionDegrees,
                                windGustKmh = cached.windGustKmh, condition = cached.condition,
                                isDay = cached.isDay, observationTime = cached.observationTime,
                                lastUpdatedEpochMillis = cached.fetchedAtEpochMillis,
                                latitude = latitude, longitude = longitude,
                                errorMessage = "İnternet verisi alınamadı • son kayıt gösteriliyor"
                            )
                        } else {
                            state.copy(
                                isLoading = false, latitude = latitude, longitude = longitude,
                                errorMessage = error.message ?: "Hava bilgisi alınamadı."
                            )
                        }
                    }
                }

                refreshJob = null
            }
    }

    fun retry() {
        val state = _uiState.value
        val latitude = state.latitude ?: return
        val longitude = state.longitude ?: return

        refreshWeather(
            latitude = latitude,
            longitude = longitude,
            force = true,
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val MIN_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
}
