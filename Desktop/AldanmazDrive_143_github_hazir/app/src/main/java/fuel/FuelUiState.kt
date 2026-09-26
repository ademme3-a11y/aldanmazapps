package com.aldanmaz.drivedashboard.ui.screen.fuel

import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseRecord

data class FuelUiState(
    val isLoading: Boolean = true,
    val isConfigured: Boolean = false,
    val tankCapacityText: String = "50",
    val startingFuelText: String = "",
    val averageConsumptionText: String = "5,5",
    val lowFuelThresholdText: String = "8",
    val currentFuelLiters: Double = 0.0,
    val consumedFuelLiters: Double = 0.0,
    val todayConsumedFuelLiters: Double = 0.0,
    val monthlyDistanceKm: Double = 0.0,
    val monthlyConsumedFuelLiters: Double = 0.0,
    val monthlyEstimatedFuelCost: Double = 0.0,
    val estimatedRangeKm: Double = 0.0,
    val tripDistanceKm: Double = 0.0,
    val isLowFuel: Boolean = false,
    val showRefuelDialog: Boolean = false,
    val refuelLitersText: String = "",
    val refuelCostText: String = "",
    val fillTank: Boolean = false,
    val totalFuelCost: Double = 0.0,
    val purchaseHistory: List<FuelPurchaseRecord> = emptyList(),
    val fuelType: String = "Dizel",
    val shellFuelPricePerLiter: Double? = null,
    val shellFuelPriceCity: String? = null,
    val shellFuelPriceUpdatedDate: String? = null,
    val isLoadingShellFuelPrice: Boolean = false,
    val shellFuelPriceError: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val confirmationMessage: String? = null,
) {
    val fuelPercentage: Double
        get() =
            if (tankCapacityLiters > 0.0) {
                (currentFuelLiters / tankCapacityLiters * 100.0).coerceIn(0.0, 100.0)
            } else {
                0.0
            }

    val tankCapacityLiters: Double
        get() = tankCapacityText.toFuelNumberOrNull() ?: 0.0

    val canSaveInitialSettings: Boolean
        get() {
            val tankCapacity = tankCapacityText.toFuelNumberOrNull() ?: return false
            val startingFuel = startingFuelText.toFuelNumberOrNull() ?: return false
            val averageConsumption =
                averageConsumptionText.toFuelNumberOrNull() ?: return false
            val lowFuelThreshold =
                lowFuelThresholdText.toFuelNumberOrNull() ?: return false

            return tankCapacity > 0.0 &&
                    startingFuel in 0.0..tankCapacity &&
                    averageConsumption > 0.0 &&
                    lowFuelThreshold in 0.0..tankCapacity &&
                    !isSaving
        }

    val canSaveRefuel: Boolean
        get() {
            if (!isConfigured || isSaving) return false

            val cost = refuelCostText.toFuelNumberOrNull() ?: return false
            if (cost < 0.0) return false

            if (fillTank) return true

            val liters = refuelLitersText.toFuelNumberOrNull() ?: return false
            return liters > 0.0
        }
}

internal fun String.toFuelNumberOrNull(): Double? =
    trim()
        .replace(',', '.')
        .toDoubleOrNull()
