package com.aldanmaz.drivedashboard.ui.screen.fuel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.fuel.FuelPreferencesRepository
import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseHistoryRepository
import com.aldanmaz.drivedashboard.data.fuel.FuelSettings
import com.aldanmaz.drivedashboard.data.fuel.calculateForDistance
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripRepository
import com.aldanmaz.drivedashboard.data.trip.TripStatisticsPeriod
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class FuelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FuelPreferencesRepository(application)
    private val purchaseHistoryRepository = FuelPurchaseHistoryRepository(application)
    private val tripRepository = TripRepository(
        AldanmazDriveDatabase.getInstance(application).tripDao()
    )
    private val dailyTripPrefs = application.getSharedPreferences("daily_trip_state", Context.MODE_PRIVATE)
    private val dailyDistancePrefs = application.getSharedPreferences("daily_distance_ledger", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FuelUiState())
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    private var latestSettings = FuelSettings()
    private var settingsLoadedOnce = false

    init {
        _uiState.update { it.copy(purchaseHistory = purchaseHistoryRepository.getAll()) }
        observeFuelSettings()
        startFuelStatisticsRefresh()
    }

    fun onTankCapacityChanged(value: String) {
        _uiState.update {
            it.copy(
                tankCapacityText = value.filterFuelInput(),
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun onStartingFuelChanged(value: String) {
        _uiState.update {
            it.copy(
                startingFuelText = value.filterFuelInput(),
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun onAverageConsumptionChanged(value: String) {
        _uiState.update {
            it.copy(
                averageConsumptionText = value.filterFuelInput(),
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun onLowFuelThresholdChanged(value: String) {
        _uiState.update {
            it.copy(
                lowFuelThresholdText = value.filterFuelInput(),
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun onFuelTypeSelected(value: String) {
        if (value !in setOf("Benzin", "Dizel", "Gaz")) return
        _uiState.update {
            it.copy(
                fuelType = value,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun showRefuelDialog() {
        if (!_uiState.value.isConfigured) return

        _uiState.update {
            it.copy(
                showRefuelDialog = true,
                refuelLitersText = "",
                refuelCostText = "",
                fillTank = false,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun hideRefuelDialog() {
        if (_uiState.value.isSaving) return

        _uiState.update {
            it.copy(
                showRefuelDialog = false,
                refuelLitersText = "",
                refuelCostText = "",
                fillTank = false,
                errorMessage = null,
            )
        }
    }

    fun onRefuelLitersChanged(value: String) {
        _uiState.update {
            it.copy(
                refuelLitersText = value.filterFuelInput(),
                errorMessage = null,
            )
        }
    }

    fun onRefuelCostChanged(value: String) {
        _uiState.update {
            it.copy(
                refuelCostText = value.filterFuelInput(),
                errorMessage = null,
            )
        }
    }

    fun onFillTankChanged(value: Boolean) {
        _uiState.update {
            it.copy(
                fillTank = value,
                errorMessage = null,
            )
        }
    }

    fun saveRefuel() {
        val state = _uiState.value

        if (!state.isConfigured) {
            showError("Önce yakıt ayarlarını kaydedin.")
            return
        }

        val cost = state.refuelCostText.toFuelNumberOrNull()
        if (cost == null || cost < 0.0) {
            showError("Yakıt ücretini geçerli bir sayı olarak girin.")
            return
        }

        val liters =
            if (state.fillTank) {
                0.0
            } else {
                state.refuelLitersText.toFuelNumberOrNull() ?: 0.0
            }

        if (!state.fillTank && liters <= 0.0) {
            showError("Alınan yakıt litresini sıfırdan büyük girin.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    confirmationMessage = null,
                )
            }

            runCatching {
                val effectiveLiters = if (state.fillTank) {
                    (state.tankCapacityLiters - state.currentFuelLiters).coerceAtLeast(0.0)
                } else {
                    liters
                }
                val freshLocation = runCatching { findCurrentLocation() }.getOrNull()
                val lastLocation = freshLocation ?: findBestLastKnownLocation()
                val storedLatitude = dailyTripPrefs.takeIf { it.contains("lastLatitudeBits") }
                    ?.let { java.lang.Double.longBitsToDouble(it.getLong("lastLatitudeBits", 0L)) }
                val storedLongitude = dailyTripPrefs.takeIf { it.contains("lastLongitudeBits") }
                    ?.let { java.lang.Double.longBitsToDouble(it.getLong("lastLongitudeBits", 0L)) }
                val latitude = lastLocation?.latitude ?: storedLatitude
                val longitude = lastLocation?.longitude ?: storedLongitude
                val address = if (latitude != null && longitude != null) {
                    runCatching { reverseGeocode(latitude, longitude) }.getOrNull()
                } else {
                    null
                }

                repository.addFuel(
                    addedLiters = liters,
                    totalCost = cost,
                    fillTank = state.fillTank,
                )
                val driverPrefs = getApplication<Application>()
                    .getSharedPreferences("driver_profiles", Context.MODE_PRIVATE)
                val driverId = driverPrefs.getString("active_driver_id", null)
                    ?: driverPrefs.getString("default_driver_id", "1")
                    ?: "1"
                val defaultName = if (driverId == "2") "Nurdan" else "Mehmet"
                val driverName = driverPrefs.getString("driver_${driverId}_name", defaultName)
                    ?.trim().orEmpty().ifBlank { defaultName }
                purchaseHistoryRepository.add(
                    driverId = driverId,
                    driverName = driverName,
                    liters = effectiveLiters,
                    costTl = cost,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showRefuelDialog = false,
                        refuelLitersText = "",
                        refuelCostText = "",
                        fillTank = false,
                        purchaseHistory = purchaseHistoryRepository.getAll(),
                        confirmationMessage =
                            if (state.fillTank) {
                                "Yakıt alımı kaydedildi. Depo dolu olarak işaretlendi."
                            } else {
                                "Yakıt alımı kaydedildi: ${liters.toInputText()} L."
                            },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Yakıt alımı kaydedilemedi.",
                    )
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val application = getApplication<Application>()
        return ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun findCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val application = getApplication<Application>()
        val manager = application.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        application.mainExecutor,
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { manager.removeUpdates(this) }
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                    continuation.invokeOnCancellation {
                        runCatching { manager.removeUpdates(listener) }
                    }
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun findBestLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val application = getApplication<Application>()
        val manager = application.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return manager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val application = getApplication<Application>()
        val geocoder = Geocoder(application, Locale("tr", "TR"))
        val address = withTimeoutOrNull(5_000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }
                        .getOrNull()
                }
            }
        } ?: return null

        return (0..address.maxAddressLineIndex)
            .mapNotNull(address::getAddressLine)
            .joinToString(", ")
            .trim()
            .takeIf(String::isNotBlank)
    }

    fun saveInitialSettings() {
        val state = _uiState.value
        val tankCapacity = state.tankCapacityText.toFuelNumberOrNull()
        val startingFuel = state.startingFuelText.toFuelNumberOrNull()
        val averageConsumption = state.averageConsumptionText.toFuelNumberOrNull()
        val lowFuelThreshold = state.lowFuelThresholdText.toFuelNumberOrNull()

        if (
            tankCapacity == null ||
            startingFuel == null ||
            averageConsumption == null ||
            lowFuelThreshold == null
        ) {
            showError("Lütfen bütün yakıt alanlarına geçerli bir sayı girin.")
            return
        }

        if (tankCapacity <= 0.0) {
            showError("Depo kapasitesi sıfırdan büyük olmalıdır.")
            return
        }

        if (startingFuel !in 0.0..tankCapacity) {
            showError("Başlangıç yakıtı 0 ile depo kapasitesi arasında olmalıdır.")
            return
        }

        if (averageConsumption <= 0.0) {
            showError("Ortalama tüketim sıfırdan büyük olmalıdır.")
            return
        }

        if (lowFuelThreshold !in 0.0..tankCapacity) {
            showError("Düşük yakıt sınırı 0 ile depo kapasitesi arasında olmalıdır.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    confirmationMessage = null,
                )
            }

            runCatching {
                repository.saveInitialSettings(
                    tankCapacityLiters = tankCapacity,
                    startingFuelLiters = startingFuel,
                    averageConsumptionLitersPer100Km = averageConsumption,
                    lowFuelThresholdLiters = lowFuelThreshold,
                    fuelType = state.fuelType,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        confirmationMessage = "Yakıt ayarları kaydedildi.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Yakıt ayarları kaydedilemedi.",
                    )
                }
            }
        }
    }

    fun updateTripDistance(distanceKm: Double) {
        _uiState.update { currentState ->
            applyCalculatedValues(
                state = currentState.copy(tripDistanceKm = distanceKm.coerceAtLeast(0.0)),
                settings = latestSettings,
            )
        }
    }

    fun dismissMessage() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    private fun observeFuelSettings() {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                latestSettings = settings

                _uiState.update { currentState ->
                    val stateWithSettings =
                        if (!settingsLoadedOnce) {
                            currentState.copy(
                                isLoading = false,
                                isConfigured = settings.isConfigured,
                                tankCapacityText = settings.tankCapacityLiters.toInputText(),
                                startingFuelText =
                                    if (settings.isConfigured) {
                                        settings.currentFuelLiters.toInputText()
                                    } else {
                                        ""
                                    },
                                averageConsumptionText =
                                    settings.averageConsumptionLitersPer100Km.toInputText(),
                                lowFuelThresholdText =
                                    settings.lowFuelThresholdLiters.toInputText(),
                                totalFuelCost = settings.totalFuelCost,
                                fuelType = settings.fuelType,
                            )
                        } else {
                            currentState.copy(
                                isLoading = false,
                                isConfigured = settings.isConfigured,
                                totalFuelCost = settings.totalFuelCost,
                                fuelType = settings.fuelType,
                            )
                        }

                    applyCalculatedValues(
                        state = stateWithSettings,
                        settings = settings,
                    )
                }

                settingsLoadedOnce = true
            }
        }
    }

    fun resetFuelStatistics() {
        viewModelScope.launch {
            runCatching {
                repository.resetFuelStatistics()
                purchaseHistoryRepository.clearAll()
                tripRepository.clearAllTrips()
                val today = java.time.LocalDate.now().toString()
                dailyTripPrefs.edit().clear().putString("day", today).apply()
                dailyDistancePrefs.edit()
                    .clear()
                    .putString("day", today)
                    .putLong("totalBits", java.lang.Double.doubleToRawLongBits(0.0))
                    .apply()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        totalFuelCost = 0.0,
                        purchaseHistory = emptyList(),
                        todayConsumedFuelLiters = 0.0,
                        monthlyDistanceKm = 0.0,
                        monthlyConsumedFuelLiters = 0.0,
                        monthlyEstimatedFuelCost = 0.0,
                        tripDistanceKm = 0.0,
                        consumedFuelLiters = 0.0,
                        confirmationMessage = "Yakıt ve yolculuk istatistikleri sıfırlandı.",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                showError(error.message ?: "Yakıt istatistikleri sıfırlanamadı.")
            }
        }
    }

    fun refreshAfterExternalReset() {
        _uiState.update {
            it.copy(
                totalFuelCost = 0.0,
                purchaseHistory = purchaseHistoryRepository.getAll(),
                todayConsumedFuelLiters = 0.0,
                monthlyDistanceKm = 0.0,
                monthlyConsumedFuelLiters = 0.0,
                monthlyEstimatedFuelCost = 0.0,
                tripDistanceKm = 0.0,
                consumedFuelLiters = 0.0,
                confirmationMessage = "Yakıt ve yolculuk istatistikleri sıfırlandı.",
                errorMessage = null,
            )
        }
    }

    private fun startFuelStatisticsRefresh() {
        viewModelScope.launch {
            while (true) {
                val activeFuel = java.lang.Double.longBitsToDouble(
                    dailyTripPrefs.getLong(
                        "fuelBits",
                        java.lang.Double.doubleToRawLongBits(0.0)
                    )
                ).coerceAtLeast(0.0)

                val completedToday = runCatching {
                    tripRepository.getCurrentPeriodStatistics(
                        period = TripStatisticsPeriod.DAY,
                        vehicleMode = null
                    )
                }.getOrDefault(com.aldanmaz.drivedashboard.data.trip.TripStatisticsSummary())

                val completedMonth = runCatching {
                    tripRepository.getCurrentPeriodStatistics(
                        period = TripStatisticsPeriod.MONTH,
                        vehicleMode = null
                    )
                }.getOrDefault(com.aldanmaz.drivedashboard.data.trip.TripStatisticsSummary())

                val activeFuelCost = activeFuel * latestSettings.lastFuelPricePerLiter.coerceAtLeast(0.0)

                _uiState.update {
                    it.copy(
                        todayConsumedFuelLiters =
                            (completedToday.estimatedFuelConsumedLiters + activeFuel).coerceAtLeast(0.0),
                        monthlyDistanceKm =
                            (completedMonth.totalDistanceKm + it.tripDistanceKm).coerceAtLeast(0.0),
                        monthlyConsumedFuelLiters =
                            (completedMonth.estimatedFuelConsumedLiters + activeFuel).coerceAtLeast(0.0),
                        monthlyEstimatedFuelCost =
                            (completedMonth.estimatedFuelCost + activeFuelCost).coerceAtLeast(0.0),
                    )
                }
                delay(2_000L)
            }
        }
    }

    private fun applyCalculatedValues(
        state: FuelUiState,
        settings: FuelSettings,
    ): FuelUiState {
        val calculation = settings.calculateForDistance(state.tripDistanceKm)

        return state.copy(
            currentFuelLiters = calculation.remainingFuelLiters,
            consumedFuelLiters = calculation.consumedFuelLiters,
            estimatedRangeKm = calculation.estimatedRangeKm,
            isLowFuel = calculation.isLowFuel,
        )
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                confirmationMessage = null,
            )
        }
    }
}

private fun String.filterFuelInput(): String {
    var decimalSeparatorUsed = false

    return buildString {
        this@filterFuelInput.forEach { character ->
            when {
                character.isDigit() -> append(character)
                (character == ',' || character == '.') && !decimalSeparatorUsed -> {
                    append(character)
                    decimalSeparatorUsed = true
                }
            }
        }
    }
}

private fun Double.toInputText(): String {
    val text =
        if (this % 1.0 == 0.0) {
            toLong().toString()
        } else {
            toString()
        }

    return text.replace('.', ',')
}
