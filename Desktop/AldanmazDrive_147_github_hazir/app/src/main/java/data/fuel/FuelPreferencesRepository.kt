package com.aldanmaz.drivedashboard.data.fuel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.fuelDataStore by preferencesDataStore(name = "fuel_preferences")

data class FuelSettings(
    val tankCapacityLiters: Double = 50.0,
    val currentFuelLiters: Double = 0.0,
    val averageConsumptionLitersPer100Km: Double = 5.5,
    val lowFuelThresholdLiters: Double = 8.0,
    val totalFuelCost: Double = 0.0,
    val totalPurchasedLiters: Double = 0.0,
    val lastFuelPricePerLiter: Double = 0.0,
    val fuelType: String = "Dizel",
    val isConfigured: Boolean = false,
)

data class FuelCalculation(
    val consumedFuelLiters: Double,
    val remainingFuelLiters: Double,
    val estimatedRangeKm: Double,
    val isLowFuel: Boolean,
)

fun FuelSettings.calculateForDistance(distanceKm: Double): FuelCalculation {
    val safeDistanceKm = distanceKm.coerceAtLeast(0.0)
    val consumedFuel =
        safeDistanceKm * averageConsumptionLitersPer100Km.coerceAtLeast(0.0) / 100.0
    val remainingFuel = (currentFuelLiters - consumedFuel).coerceAtLeast(0.0)
    val estimatedRange =
        if (averageConsumptionLitersPer100Km > 0.0) {
            remainingFuel * 100.0 / averageConsumptionLitersPer100Km
        } else {
            0.0
        }

    return FuelCalculation(
        consumedFuelLiters = consumedFuel,
        remainingFuelLiters = remainingFuel,
        estimatedRangeKm = estimatedRange,
        isLowFuel = isConfigured && remainingFuel <= lowFuelThresholdLiters,
    )
}

class FuelPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.fuelDataStore

    val settings: Flow<FuelSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                FuelSettings(
                    tankCapacityLiters =
                        preferences[Keys.TANK_CAPACITY_LITERS] ?: DEFAULT_TANK_CAPACITY_LITERS,
                    currentFuelLiters =
                        preferences[Keys.CURRENT_FUEL_LITERS] ?: DEFAULT_CURRENT_FUEL_LITERS,
                    averageConsumptionLitersPer100Km =
                        preferences[Keys.AVERAGE_CONSUMPTION_LITERS_PER_100_KM]
                            ?: DEFAULT_AVERAGE_CONSUMPTION_LITERS_PER_100_KM,
                    lowFuelThresholdLiters =
                        preferences[Keys.LOW_FUEL_THRESHOLD_LITERS]
                            ?: DEFAULT_LOW_FUEL_THRESHOLD_LITERS,
                    totalFuelCost =
                        preferences[Keys.TOTAL_FUEL_COST] ?: DEFAULT_TOTAL_FUEL_COST,
                    totalPurchasedLiters =
                        preferences[Keys.TOTAL_PURCHASED_LITERS] ?: 0.0,
                    lastFuelPricePerLiter =
                        preferences[Keys.LAST_FUEL_PRICE_PER_LITER] ?: 0.0,
                    fuelType = preferences[Keys.FUEL_TYPE] ?: "Dizel",
                    isConfigured = preferences[Keys.IS_CONFIGURED] ?: false,
                )
            }

    suspend fun saveInitialSettings(
        tankCapacityLiters: Double,
        startingFuelLiters: Double,
        averageConsumptionLitersPer100Km: Double,
        lowFuelThresholdLiters: Double,
        fuelType: String,
    ) {
        require(tankCapacityLiters > 0.0) {
            "Depo kapasitesi sıfırdan büyük olmalıdır."
        }
        require(startingFuelLiters in 0.0..tankCapacityLiters) {
            "Başlangıç yakıtı depo kapasitesi aralığında olmalıdır."
        }
        require(averageConsumptionLitersPer100Km > 0.0) {
            "Ortalama tüketim sıfırdan büyük olmalıdır."
        }
        require(lowFuelThresholdLiters in 0.0..tankCapacityLiters) {
            "Düşük yakıt sınırı depo kapasitesi aralığında olmalıdır."
        }

        dataStore.edit { preferences ->
            preferences[Keys.TANK_CAPACITY_LITERS] = tankCapacityLiters
            preferences[Keys.CURRENT_FUEL_LITERS] = startingFuelLiters
            preferences[Keys.AVERAGE_CONSUMPTION_LITERS_PER_100_KM] =
                averageConsumptionLitersPer100Km
            preferences[Keys.LOW_FUEL_THRESHOLD_LITERS] = lowFuelThresholdLiters
            preferences[Keys.FUEL_TYPE] = fuelType.takeIf { it in setOf("Benzin", "Dizel", "Gaz") } ?: "Dizel"
            preferences[Keys.IS_CONFIGURED] = true
        }
    }

    suspend fun addFuel(
        addedLiters: Double,
        totalCost: Double,
        fillTank: Boolean,
    ) {
        require(fillTank || addedLiters > 0.0) {
            "Eklenen yakıt sıfırdan büyük olmalıdır."
        }
        require(totalCost >= 0.0) {
            "Yakıt ücreti negatif olamaz."
        }

        dataStore.edit { preferences ->
            val tankCapacity =
                preferences[Keys.TANK_CAPACITY_LITERS] ?: DEFAULT_TANK_CAPACITY_LITERS
            val currentFuel =
                preferences[Keys.CURRENT_FUEL_LITERS] ?: DEFAULT_CURRENT_FUEL_LITERS
            val currentTotalFuelCost =
                preferences[Keys.TOTAL_FUEL_COST] ?: DEFAULT_TOTAL_FUEL_COST

            // "Depoyu doldur" seçiminde litre alanı boş/0 olsa bile gerçekten
            // depoya eklenen litreyi hesapla. Aksi halde toplam maliyet kaydolup
            // alınan litre 0 kaldığı için Park ekranındaki TL hesabı sıfır oluyordu.
            val effectiveAddedLiters =
                if (fillTank) {
                    (tankCapacity - currentFuel).coerceAtLeast(0.0)
                } else {
                    addedLiters.coerceAtLeast(0.0)
                }

            preferences[Keys.CURRENT_FUEL_LITERS] =
                if (fillTank) {
                    tankCapacity
                } else {
                    (currentFuel + effectiveAddedLiters).coerceAtMost(tankCapacity)
                }

            preferences[Keys.TOTAL_FUEL_COST] = currentTotalFuelCost + totalCost
            if (effectiveAddedLiters > 0.0) {
                val currentPurchased = preferences[Keys.TOTAL_PURCHASED_LITERS] ?: 0.0
                preferences[Keys.TOTAL_PURCHASED_LITERS] = currentPurchased + effectiveAddedLiters
                if (totalCost > 0.0) {
                    preferences[Keys.LAST_FUEL_PRICE_PER_LITER] = totalCost / effectiveAddedLiters
                }
            }
        }
    }

    suspend fun saveObdFuelLevel(fuelLiters: Double) {
        dataStore.edit { preferences ->
            val tankCapacity =
                preferences[Keys.TANK_CAPACITY_LITERS] ?: DEFAULT_TANK_CAPACITY_LITERS
            preferences[Keys.CURRENT_FUEL_LITERS] = fuelLiters.coerceIn(0.0, tankCapacity)
        }
    }

    suspend fun commitConsumedDistance(
        distanceKm: Double,
        averageConsumptionLitersPer100Km: Double? = null,
    ) {
        if (distanceKm <= 0.0) return

        dataStore.edit { preferences ->
            val currentFuel =
                preferences[Keys.CURRENT_FUEL_LITERS] ?: DEFAULT_CURRENT_FUEL_LITERS

            val averageConsumption =
                averageConsumptionLitersPer100Km
                    ?.takeIf { it > 0.0 }
                    ?: preferences[Keys.AVERAGE_CONSUMPTION_LITERS_PER_100_KM]
                    ?: DEFAULT_AVERAGE_CONSUMPTION_LITERS_PER_100_KM

            val consumedFuel =
                distanceKm * averageConsumption / 100.0

            preferences[Keys.CURRENT_FUEL_LITERS] =
                (currentFuel - consumedFuel).coerceAtLeast(0.0)
        }
    }

    suspend fun resetFuelStatistics() {
        dataStore.edit { preferences ->
            preferences[Keys.TOTAL_FUEL_COST] = 0.0
            preferences[Keys.TOTAL_PURCHASED_LITERS] = 0.0
        }
    }

    private object Keys {
        val TANK_CAPACITY_LITERS = doublePreferencesKey("tank_capacity_liters")
        val CURRENT_FUEL_LITERS = doublePreferencesKey("current_fuel_liters")
        val AVERAGE_CONSUMPTION_LITERS_PER_100_KM =
            doublePreferencesKey("average_consumption_liters_per_100_km")
        val LOW_FUEL_THRESHOLD_LITERS = doublePreferencesKey("low_fuel_threshold_liters")
        val TOTAL_FUEL_COST = doublePreferencesKey("total_fuel_cost")
        val TOTAL_PURCHASED_LITERS = doublePreferencesKey("total_purchased_liters")
        val LAST_FUEL_PRICE_PER_LITER = doublePreferencesKey("last_fuel_price_per_liter")
        val FUEL_TYPE = stringPreferencesKey("fuel_type")
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
    }

    private companion object {
        const val DEFAULT_TANK_CAPACITY_LITERS = 50.0
        const val DEFAULT_CURRENT_FUEL_LITERS = 0.0
        const val DEFAULT_AVERAGE_CONSUMPTION_LITERS_PER_100_KM = 5.5
        const val DEFAULT_LOW_FUEL_THRESHOLD_LITERS = 8.0
        const val DEFAULT_TOTAL_FUEL_COST = 0.0
    }
}
