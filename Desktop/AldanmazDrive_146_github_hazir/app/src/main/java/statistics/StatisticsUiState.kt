package com.aldanmaz.drivedashboard.ui.screen.statistics

import com.aldanmaz.drivedashboard.data.trip.TripStatisticsPeriod
import com.aldanmaz.drivedashboard.data.trip.TripStatisticsSummary
import com.aldanmaz.drivedashboard.data.trip.TripEntity

data class StatisticsUiState(
    val selectedPeriod: TripStatisticsPeriod =
        TripStatisticsPeriod.DAY,
    val selectedVehicleMode: String? = null,
    val selectedDriverId: String? = null,
    val driverNames: List<Pair<String, String>> = listOf("1" to "Mehmet", "2" to "Nurdan"),
    val trips: List<TripEntity> = emptyList(),
    val summary: TripStatisticsSummary =
        TripStatisticsSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

