package com.aldanmaz.drivedashboard.ui.screen.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripRepository
import com.aldanmaz.drivedashboard.data.trip.TripStatisticsPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tripRepository =
        TripRepository(
            AldanmazDriveDatabase
                .getInstance(application)
                .tripDao()
        )

    private val _uiState =
        MutableStateFlow(StatisticsUiState())

    val uiState: StateFlow<StatisticsUiState> =
        _uiState.asStateFlow()

    init {
        val prefs = application.getSharedPreferences("driver_profiles", android.content.Context.MODE_PRIVATE)
        _uiState.value = _uiState.value.copy(driverNames = listOf(
            "1" to (prefs.getString("driver_1_name", "Mehmet") ?: "Mehmet"),
            "2" to (prefs.getString("driver_2_name", "Nurdan") ?: "Nurdan")
        ))
        viewModelScope.launch {
            tripRepository.removeDuplicateTrips()
            refreshStatistics()
        }
    }

    fun setDriver(driverId: String?) {
        _uiState.value = _uiState.value.copy(selectedDriverId = driverId)
        refreshStatistics()
    }

    fun setPeriod(
        period: TripStatisticsPeriod
    ) {
        if (_uiState.value.selectedPeriod == period) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                selectedPeriod = period
            )

        refreshStatistics()
    }

    fun setVehicleMode(
        vehicleMode: String?
    ) {
        if (_uiState.value.selectedVehicleMode == vehicleMode) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                selectedVehicleMode = vehicleMode
            )

        refreshStatistics()
    }

    fun refreshStatistics() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )

            try {
                val currentState =
                    _uiState.value

                val summary =
                    tripRepository.getCurrentPeriodStatistics(
                        period =
                            currentState.selectedPeriod,
                        vehicleMode =
                            currentState.selectedVehicleMode,
                        driverId = currentState.selectedDriverId
                    )
                val trips = tripRepository.getCurrentPeriodTrips(
                    period = currentState.selectedPeriod,
                    vehicleMode = currentState.selectedVehicleMode,
                    driverId = currentState.selectedDriverId
                )

                _uiState.value =
                    _uiState.value.copy(
                        summary = summary,
                        trips = trips,
                        isLoading = false,
                        errorMessage = null
                    )
            } catch (error: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            error.message
                                ?: "İstatistik verisi alınamadı"
                    )
            }
        }
    }
    fun resetStatistics() {
        viewModelScope.launch {
            try {
                tripRepository.clearAllTrips()
                refreshStatistics()
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "İstatistikler sıfırlanamadı"
                )
            }
        }
    }


}

