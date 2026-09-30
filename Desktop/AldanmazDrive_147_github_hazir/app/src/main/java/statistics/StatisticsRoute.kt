package com.aldanmaz.drivedashboard.ui.screen.statistics

import androidx.compose.runtime.Composable

@Composable
fun StatisticsRoute(
    onBack: () -> Unit,
    onSpeedCorridorClick: () -> Unit,
    activeTrip: com.aldanmaz.drivedashboard.data.trip.TripEntity? = null,
    onResetAllStatistics: ((() -> Unit) -> Unit)? = null
) {
    HistoryStatisticsScreen(onBack = onBack)
}
