package com.aldanmaz.drivedashboard.ui.screen.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aldanmaz.drivedashboard.data.trip.TripEntity
import kotlin.math.max
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun StatisticsRoute(
    onBack: () -> Unit,
    onSpeedCorridorClick: () -> Unit,
    activeTrip: TripEntity? = null,
    onResetAllStatistics: ((() -> Unit) -> Unit)? = null,
    viewModel: StatisticsViewModel = viewModel()
) {
    val uiState by
        viewModel.uiState.collectAsStateWithLifecycle()

    // Sayfa gece yarısında açık kalsa bile GÜN filtresi yeni güne otomatik geçer.
    LaunchedEffect(viewModel) {
        while (true) {
            val now = System.currentTimeMillis()
            val nextMidnight = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 250)
            }.timeInMillis
            delay((nextMidnight - now).coerceAtLeast(1_000L))
            viewModel.refreshStatistics()
        }
    }

    val visibleActiveTrip = activeTrip?.takeIf { trip ->
        (uiState.selectedDriverId == null || uiState.selectedDriverId == trip.driverId) &&
            (uiState.selectedVehicleMode == null || uiState.selectedVehicleMode == trip.vehicleMode)
    }
    val displayedState = if (visibleActiveTrip == null) uiState else {
        val base = uiState.summary
        val movingSeconds = base.movingDurationSeconds + visibleActiveTrip.movingDurationSeconds
        val distance = base.totalDistanceKm + visibleActiveTrip.distanceKm
        uiState.copy(
            trips = listOf(visibleActiveTrip) + uiState.trips,
            summary = base.copy(
                totalDistanceKm = distance,
                totalDurationSeconds = base.totalDurationSeconds + visibleActiveTrip.totalDurationSeconds,
                movingDurationSeconds = movingSeconds,
                parkDurationSeconds = base.parkDurationSeconds + visibleActiveTrip.parkDurationSeconds,
                tripCount = base.tripCount + 1,
                averageSpeedKmh = if (movingSeconds > 0L) distance * 3_600.0 / movingSeconds else 0.0,
                maxSpeedKmh = max(base.maxSpeedKmh, visibleActiveTrip.maxSpeedKmh),
                estimatedFuelConsumedLiters = base.estimatedFuelConsumedLiters + visibleActiveTrip.estimatedFuelConsumedLiters,
                estimatedFuelCost = base.estimatedFuelCost + visibleActiveTrip.estimatedFuelCost,
                speed0To30Seconds = base.speed0To30Seconds + visibleActiveTrip.speed0To30Seconds,
                speed31To50Seconds = base.speed31To50Seconds + visibleActiveTrip.speed31To50Seconds,
                speed51To70Seconds = base.speed51To70Seconds + visibleActiveTrip.speed51To70Seconds,
                speed71To90Seconds = base.speed71To90Seconds + visibleActiveTrip.speed71To90Seconds,
                speed91To120Seconds = base.speed91To120Seconds + visibleActiveTrip.speed91To120Seconds,
                speedOver120Seconds = base.speedOver120Seconds + visibleActiveTrip.speedOver120Seconds
            )
        )
    }

    StatisticsScreen(
        uiState = displayedState,
        onBack = onBack,
        onSpeedCorridorClick = onSpeedCorridorClick,
        onPeriodSelected = viewModel::setPeriod,
        onVehicleModeSelected = viewModel::setVehicleMode,
        onDriverSelected = viewModel::setDriver,
        onRefresh = viewModel::refreshStatistics,
        onResetStatistics = {
            if (onResetAllStatistics != null) {
                onResetAllStatistics { viewModel.refreshStatistics() }
            } else {
                viewModel.resetStatistics()
            }
        }
    )
}
