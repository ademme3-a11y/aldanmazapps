package com.aldanmaz.drivedashboard.ui.screen.fuel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FuelRoute(
    fuelDataSource: String = "HESAP",
    externalFuelPercentage: Double? = null,
    externalRemainingFuelLiters: Double? = null,
    externalEstimatedRangeKm: Double? = null,
    externalIsLowFuel: Boolean? = null,
    onBack: () -> Unit,
    onResetAllStatistics: ((() -> Unit) -> Unit)? = null,
    viewModel: FuelViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FuelScreen(
        uiState = uiState,
        fuelDataSource = fuelDataSource,
        externalFuelPercentage = externalFuelPercentage,
        externalRemainingFuelLiters = externalRemainingFuelLiters,
        externalEstimatedRangeKm = externalEstimatedRangeKm,
        externalIsLowFuel = externalIsLowFuel,
        onBack = onBack,
        onTankCapacityChanged = viewModel::onTankCapacityChanged,
        onStartingFuelChanged = viewModel::onStartingFuelChanged,
        onAverageConsumptionChanged = viewModel::onAverageConsumptionChanged,
        onLowFuelThresholdChanged = viewModel::onLowFuelThresholdChanged,
        onFuelTypeSelected = viewModel::onFuelTypeSelected,
        onSaveSettings = viewModel::saveInitialSettings,
        onShowRefuelDialog = viewModel::showRefuelDialog,
        onHideRefuelDialog = viewModel::hideRefuelDialog,
        onRefuelLitersChanged = viewModel::onRefuelLitersChanged,
        onRefuelCostChanged = viewModel::onRefuelCostChanged,
        onFillTankChanged = viewModel::onFillTankChanged,
        onSaveRefuel = viewModel::saveRefuel,
        onResetStatistics = {
            if (onResetAllStatistics != null) {
                onResetAllStatistics { viewModel.refreshAfterExternalReset() }
            } else {
                viewModel.resetFuelStatistics()
            }
        },
    )
}
