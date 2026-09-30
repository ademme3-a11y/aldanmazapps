package com.aldanmaz.drivedashboard.ui.screen.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun StatisticsRoute(
    onBack: () -> Unit,
    onSpeedCorridorClick: () -> Unit,
    activeTrip: com.aldanmaz.drivedashboard.data.trip.TripEntity? = null,
    onResetAllStatistics: ((() -> Unit) -> Unit)? = null
) {
    var showDetailed by remember { mutableStateOf(false) }

    if (showDetailed) {
        StatisticsScreen(
            uiState = StatisticsUiState(),
            onBack = { showDetailed = false },
            onSpeedCorridorClick = onSpeedCorridorClick,
            onPeriodSelected = {},
            onVehicleModeSelected = {},
            onDriverSelected = {},
            onRefresh = {},
            onResetStatistics = {}
        )
    } else {
        HistoryStatisticsScreen(
            onBack = onBack,
            onDetailedClick = { showDetailed = true }
        )
    }
}
