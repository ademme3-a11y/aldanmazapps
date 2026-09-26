package com.aldanmaz.drivedashboard.ui.screen.obd

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.data.obd.ObdBluetoothManager
import com.aldanmaz.drivedashboard.data.obd.ObdConnectionInfo
import com.aldanmaz.drivedashboard.data.obd.ObdDevice
import com.aldanmaz.drivedashboard.data.obd.ObdLiveData
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdBridgeRuntime
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdHotspotClient
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdNetworkInspector
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdNetworkStatus
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdPhoneBridgeService
import com.aldanmaz.drivedashboard.data.obd.bridge.ObdPhoneBridgeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ObdConnectionMode {
    DIRECT_BLUETOOTH,
    PHONE_BRIDGE,
    MULTIMEDIA_RECEIVER
}

data class ObdUiState(
    val connectionMode: ObdConnectionMode = ObdConnectionMode.DIRECT_BLUETOOTH,
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val devices: List<ObdDevice> = emptyList(),
    val selectedAddress: String? = null,
    val connectedAddress: String? = null,
    val isConnecting: Boolean = false,
    val connectionInfo: ObdConnectionInfo? = null,
    val liveData: ObdLiveData = ObdLiveData(),
    val lastLiveDataTime: String? = null,
    val lastLiveDataEpochMs: Long? = null,
    val lastEvaluation: String? = null,
    val lastEvaluationTime: String? = null,
    val errorMessage: String? = null,
    val phoneBridge: ObdPhoneBridgeState = ObdPhoneBridgeState(),
    val receiverDesired: Boolean = false,
    val receiverHost: String = "",
    val receiverConnectedHost: String? = null,
    val networkStatus: ObdNetworkStatus = ObdNetworkStatus(false, false, false, null, emptyList())
) {
    val isConnected: Boolean
        get() = when (connectionMode) {
            ObdConnectionMode.PHONE_BRIDGE -> phoneBridge.obdConnected
            else -> connectedAddress != null
        }
}

class ObdViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = ObdBluetoothManager(application)
    private val hotspotClient = ObdHotspotClient(application)
    private val prefs = application.getSharedPreferences("obd_settings", 0)
    private val voice = CentralVoiceAlertManager.getInstance(application)
    private val _uiState = MutableStateFlow(ObdUiState())
    val uiState: StateFlow<ObdUiState> = _uiState.asStateFlow()
    private var pollingJob: Job? = null
    private var receiverJob: Job? = null
    private var networkJob: Job? = null
    private var directReconnectJob: Job? = null
    private var lastEvaluationSavedAtElapsed = 0L

    init {
        val savedMode = runCatching {
            ObdConnectionMode.valueOf(
                prefs.getString("connection_mode", ObdConnectionMode.DIRECT_BLUETOOTH.name)
                    ?: ObdConnectionMode.DIRECT_BLUETOOTH.name
            )
        }.getOrDefault(ObdConnectionMode.DIRECT_BLUETOOTH)

        _uiState.value = _uiState.value.copy(
            connectionMode = savedMode,
            lastEvaluation = prefs.getString("last_evaluation", null),
            lastEvaluationTime = prefs.getString("last_evaluation_time", null),
            liveData = restoreLastLiveData(),
            lastLiveDataTime = prefs.getString("last_live_data_time", null),
            lastLiveDataEpochMs = prefs.getLong("last_live_data_epoch", 0L).takeIf { it > 0L },
            receiverHost = prefs.getString("receiver_host", "").orEmpty()
        )
        refreshDevices()

        viewModelScope.launch {
            ObdBridgeRuntime.phoneState.collect { state ->
                val current = _uiState.value
                if (current.connectionMode == ObdConnectionMode.PHONE_BRIDGE) {
                    val time = state.lastUpdateEpochMs?.let(::formatLiveTime)
                    state.lastUpdateEpochMs?.let { saveLastLiveData(state.liveData, time ?: formatLiveTime(it), it) }
                    _uiState.value = current.copy(
                        phoneBridge = state,
                        connectedAddress = if (state.obdConnected) state.selectedAddress else null,
                        connectionInfo = state.connectionInfo,
                        liveData = if (state.lastUpdateEpochMs != null) mergeLiveData(current.liveData, state.liveData) else current.liveData,
                        lastLiveDataTime = time ?: current.lastLiveDataTime,
                        lastLiveDataEpochMs = state.lastUpdateEpochMs ?: current.lastLiveDataEpochMs,
                        errorMessage = state.errorMessage
                    )
                } else {
                    _uiState.value = current.copy(phoneBridge = state)
                }
            }
        }

        startNetworkMonitor()
    }

    fun setConnectionMode(mode: ObdConnectionMode) {
        val oldMode = _uiState.value.connectionMode
        if (oldMode == mode) return
        pollingJob?.cancel()
        directReconnectJob?.cancel()
        if (oldMode == ObdConnectionMode.DIRECT_BLUETOOTH) {
            viewModelScope.launch { runCatching { manager.disconnect() } }
        }
        if (oldMode == ObdConnectionMode.PHONE_BRIDGE) {
            stopPhoneBridgeInternal()
        }
        stopReceiverInternal(updateState = false)
        prefs.edit()
            .putString("connection_mode", mode.name)
            .putBoolean("manual_disconnect", false)
            .apply()
        val phone = ObdBridgeRuntime.phoneState.value
        _uiState.value = _uiState.value.copy(
            connectionMode = mode,
            connectedAddress = if (mode == ObdConnectionMode.PHONE_BRIDGE && phone.obdConnected) phone.selectedAddress else null,
            connectionInfo = if (mode == ObdConnectionMode.PHONE_BRIDGE) phone.connectionInfo else null,
            liveData = if (mode == ObdConnectionMode.PHONE_BRIDGE && phone.lastUpdateEpochMs != null) phone.liveData else restoreLastLiveData(),
            lastLiveDataEpochMs = if (mode == ObdConnectionMode.PHONE_BRIDGE) phone.lastUpdateEpochMs else prefs.getLong("last_live_data_epoch", 0L).takeIf { it > 0L },
            phoneBridge = phone,
            isConnecting = false,
            receiverDesired = false,
            receiverConnectedHost = null,
            errorMessage = null
        )
        refreshDevices()
    }

    fun setReceiverHost(host: String) {
        prefs.edit().putString("receiver_host", host.trim()).apply()
        _uiState.value = _uiState.value.copy(receiverHost = host)
    }

    fun refreshDevices() {
        refreshDeviceState()
        startAutomaticConnectionIfNeeded()
    }

    private fun refreshDeviceState() {
        val devices = runCatching { manager.bondedDevices() }.getOrDefault(emptyList())
        val savedAddress = prefs.getString("selected_address", null)
        val selected = savedAddress?.takeIf { address -> devices.any { it.address == address } }
            ?: devices.firstOrNull { device ->
                val name = device.name.lowercase(Locale.ROOT)
                listOf("obd", "elm", "vlink", "vgate", "fobd", "fnirsi")
                    .any(name::contains)
            }?.address
        if (selected != null && selected != savedAddress) {
            prefs.edit().putString("selected_address", selected).apply()
        }
        _uiState.value = _uiState.value.copy(
            bluetoothAvailable = manager.isBluetoothAvailable(),
            bluetoothEnabled = manager.isBluetoothEnabled(),
            devices = devices,
            selectedAddress = selected,
            networkStatus = ObdNetworkInspector.read(getApplication())
        )
    }

    fun selectDevice(address: String) {
        prefs.edit()
            .putString("selected_address", address)
            .putBoolean("manual_disconnect", false)
            .apply()
        _uiState.value = _uiState.value.copy(selectedAddress = address)
        when (_uiState.value.connectionMode) {
            ObdConnectionMode.DIRECT_BLUETOOTH -> scheduleDirectReconnect(immediate = true)
            ObdConnectionMode.PHONE_BRIDGE -> startPhoneBridge()
            ObdConnectionMode.MULTIMEDIA_RECEIVER -> startReceiver()
        }
    }

    fun connectSelected() {
        prefs.edit().putBoolean("manual_disconnect", false).apply()
        when (_uiState.value.connectionMode) {
            ObdConnectionMode.DIRECT_BLUETOOTH -> connectDirect(fromAutomaticRetry = false)
            ObdConnectionMode.PHONE_BRIDGE -> startPhoneBridge()
            ObdConnectionMode.MULTIMEDIA_RECEIVER -> startReceiver()
        }
    }

    private fun connectDirect(fromAutomaticRetry: Boolean) {
        val address = _uiState.value.selectedAddress ?: return
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, errorMessage = null)
            val result = manager.connect(address)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    connectedAddress = address,
                    connectionInfo = result.getOrNull(),
                    errorMessage = null
                )
                val info = result.getOrNull()
                saveLastEvaluation("${info?.adapterIdentity ?: "OBD-II"} • ${info?.protocol ?: "Protokol hazır"} • ECU bağlantısı doğrulandı")
                playConnectedVoice("direct_$address")
                startDirectPolling()
            } else {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    connectedAddress = null,
                    connectionInfo = null,
                    errorMessage = result.exceptionOrNull()?.message ?: "OBD bağlantısı kurulamadı"
                )
                playWarningVoice("obd_connect_warning")
                if (!fromAutomaticRetry) scheduleDirectReconnect()
            }
        }
    }

    private fun startPhoneBridge() {
        val address = _uiState.value.selectedAddress
        if (address.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Önce Galaxy A25 üzerinde OBD cihazını seçin.")
            return
        }
        val intent = Intent(getApplication(), ObdPhoneBridgeService::class.java).apply {
            action = ObdPhoneBridgeService.ACTION_START
            putExtra(ObdPhoneBridgeService.EXTRA_ADDRESS, address)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
        _uiState.value = _uiState.value.copy(isConnecting = true, errorMessage = null)
        viewModelScope.launch {
            repeat(20) {
                delay(500)
                val bridge = ObdBridgeRuntime.phoneState.value
                if (bridge.obdConnected) {
                    _uiState.value = _uiState.value.copy(isConnecting = false)
                    playConnectedVoice("phone_bridge_$address")
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(isConnecting = false)
        }
    }

    fun stopPhoneBridge() {
        prefs.edit().putBoolean("manual_disconnect", true).apply()
        stopPhoneBridgeInternal()
    }

    private fun stopPhoneBridgeInternal() {
        val intent = Intent(getApplication(), ObdPhoneBridgeService::class.java).apply {
            action = ObdPhoneBridgeService.ACTION_STOP
        }
        runCatching { getApplication<Application>().startService(intent) }
        _uiState.value = _uiState.value.copy(isConnecting = false)
    }

    private fun startReceiver() {
        if (receiverJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(receiverDesired = true, isConnecting = true, errorMessage = null)
        receiverJob = viewModelScope.launch {
            var announced = false
            while (_uiState.value.receiverDesired) {
                val result = hotspotClient.connect(_uiState.value.receiverHost.takeIf { it.isNotBlank() })
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        connectedAddress = null,
                        connectionInfo = null,
                        receiverConnectedHost = null,
                        isConnecting = true,
                        errorMessage = result.exceptionOrNull()?.message ?: "Telefon OBD köprüsü bulunamadı"
                    )
                    delay(3_000)
                    continue
                }

                val first = result.getOrThrow()
                applyReceiverSnapshot(first)
                _uiState.value = _uiState.value.copy(
                    connectedAddress = "PHONE_HOTSPOT_BRIDGE",
                    connectionInfo = first.connectionInfo,
                    receiverConnectedHost = hotspotClient.connectedHost,
                    isConnecting = false,
                    errorMessage = null
                )
                if (!announced) {
                    announced = true
                    playConnectedVoice("hotspot_receiver")
                }

                try {
                    while (_uiState.value.receiverDesired) {
                        val snapshot = hotspotClient.readSnapshot()
                        applyReceiverSnapshot(snapshot)
                    }
                } catch (t: Throwable) {
                    hotspotClient.disconnect()
                    _uiState.value = _uiState.value.copy(
                        connectedAddress = null,
                        connectionInfo = null,
                        receiverConnectedHost = null,
                        isConnecting = true,
                        errorMessage = "Telefon köprüsü kesildi; yeniden bağlanılıyor…"
                    )
                    delay(2_000)
                }
            }
        }
    }

    private fun applyReceiverSnapshot(snapshot: com.aldanmaz.drivedashboard.data.obd.bridge.ObdBridgeSnapshot) {
        val merged = mergeLiveData(_uiState.value.liveData, snapshot.liveData)
        val time = formatLiveTime(snapshot.timestampEpochMs)
        saveLastLiveData(merged, time, snapshot.timestampEpochMs)
        _uiState.value = _uiState.value.copy(
            liveData = merged,
            lastLiveDataTime = time,
            lastLiveDataEpochMs = snapshot.timestampEpochMs,
            connectionInfo = snapshot.connectionInfo,
            connectedAddress = "PHONE_HOTSPOT_BRIDGE",
            receiverConnectedHost = hotspotClient.connectedHost,
            isConnecting = false,
            errorMessage = null
        )
        saveEvaluationFrom(snapshot.liveData)
    }

    fun disconnect() {
        prefs.edit().putBoolean("manual_disconnect", true).apply()
        directReconnectJob?.cancel()
        when (_uiState.value.connectionMode) {
            ObdConnectionMode.DIRECT_BLUETOOTH -> disconnectDirect()
            ObdConnectionMode.PHONE_BRIDGE -> stopPhoneBridgeInternal()
            ObdConnectionMode.MULTIMEDIA_RECEIVER -> stopReceiverInternal(updateState = true)
        }
    }

    private fun disconnectDirect() {
        pollingJob?.cancel()
        viewModelScope.launch {
            manager.disconnect()
            _uiState.value = _uiState.value.copy(connectedAddress = null, connectionInfo = null)
            playDisconnectedVoice("manual_${System.currentTimeMillis()}", 0L)
        }
    }

    private fun stopReceiverInternal(updateState: Boolean) {
        if (updateState) _uiState.value = _uiState.value.copy(receiverDesired = false)
        receiverJob?.cancel(); receiverJob = null
        viewModelScope.launch { hotspotClient.disconnect() }
        if (updateState) {
            _uiState.value = _uiState.value.copy(
                connectedAddress = null,
                connectionInfo = null,
                receiverConnectedHost = null,
                isConnecting = false,
                receiverDesired = false,
                errorMessage = null
            )
        }
    }

    private fun startDirectPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                runCatching { manager.readLiveData() }
                    .onSuccess {
                        val merged = mergeLiveData(_uiState.value.liveData, it)
                        val epoch = System.currentTimeMillis()
                        val time = formatLiveTime(epoch)
                        saveLastLiveData(merged, time, epoch)
                        _uiState.value = _uiState.value.copy(liveData = merged, lastLiveDataTime = time, lastLiveDataEpochMs = epoch, errorMessage = null)
                        saveEvaluationFrom(it)
                    }
                    .onFailure {
                        manager.disconnect()
                        _uiState.value = _uiState.value.copy(connectedAddress = null, connectionInfo = null, errorMessage = "OBD bağlantısı kesildi")
                        playDisconnectedVoice("unexpected", 30_000L)
                        scheduleDirectReconnect()
                        return@launch
                    }
                delay(1_500)
            }
        }
    }

    private fun startAutomaticConnectionIfNeeded() {
        if (prefs.getBoolean("manual_disconnect", false)) return
        when (_uiState.value.connectionMode) {
            ObdConnectionMode.DIRECT_BLUETOOTH -> {
                if (_uiState.value.bluetoothEnabled && _uiState.value.selectedAddress != null) {
                    scheduleDirectReconnect(immediate = true)
                }
            }
            ObdConnectionMode.PHONE_BRIDGE -> {
                if (
                    _uiState.value.bluetoothEnabled &&
                    _uiState.value.selectedAddress != null &&
                    !ObdBridgeRuntime.phoneState.value.running
                ) {
                    startPhoneBridge()
                }
            }
            ObdConnectionMode.MULTIMEDIA_RECEIVER -> startReceiver()
        }
    }

    private fun scheduleDirectReconnect(immediate: Boolean = false) {
        if (
            _uiState.value.connectionMode != ObdConnectionMode.DIRECT_BLUETOOTH ||
            prefs.getBoolean("manual_disconnect", false) ||
            _uiState.value.isConnected ||
            directReconnectJob?.isActive == true
        ) return

        directReconnectJob = viewModelScope.launch {
            if (!immediate) delay(2_500L)
            var attempt = 0
            while (
                _uiState.value.connectionMode == ObdConnectionMode.DIRECT_BLUETOOTH &&
                !prefs.getBoolean("manual_disconnect", false) &&
                !_uiState.value.isConnected
            ) {
                refreshDeviceState()
                if (_uiState.value.bluetoothEnabled && _uiState.value.selectedAddress != null) {
                    connectDirect(fromAutomaticRetry = true)
                    delay(5_000L)
                    if (_uiState.value.isConnected) return@launch
                }
                val waitMs = (4_000L + attempt * 2_000L).coerceAtMost(15_000L)
                attempt += 1
                delay(waitMs)
            }
        }
    }

    private fun saveEvaluationFrom(data: ObdLiveData) {
        val summary = buildString {
            append("Akü ").append(data.batteryVoltage?.let { value -> "%.1f V".format(value) } ?: "--")
            append(" • Devir ").append(data.rpm?.let { value -> "$value rpm" } ?: "--")
            append(" • Su ").append(data.coolantCelsius?.let { value -> "$value °C" } ?: "--")
            if (data.dtcCodes.isNotEmpty()) append(" • DTC ${data.dtcCodes.joinToString()}")
            else append(" • Arıza kodu yok")
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastEvaluationSavedAtElapsed >= 30_000L) {
            lastEvaluationSavedAtElapsed = now
            saveLastEvaluation(summary)
        }
    }

    private fun startNetworkMonitor() {
        networkJob?.cancel()
        networkJob = viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(networkStatus = ObdNetworkInspector.read(getApplication()))
                delay(2_000)
            }
        }
    }

    private fun playConnectedVoice(key: String) {
        voice.playRecorded(
            type = VoiceAlertType.OBD,
            priority = VoiceAlertPriority.NORMAL,
            clip = RecordedVoiceClip.OBD_CONNECTED,
            cooldownKey = "obd_connected_$key",
            cooldownMs = 5_000L
        )
    }

    private fun playWarningVoice(key: String) {
        voice.playRecorded(
            type = VoiceAlertType.OBD,
            priority = VoiceAlertPriority.HIGH,
            clip = RecordedVoiceClip.OBD_WARNING,
            cooldownKey = key,
            cooldownMs = 30_000L
        )
    }

    private fun playDisconnectedVoice(key: String, cooldown: Long) {
        voice.playRecorded(
            type = VoiceAlertType.OBD,
            priority = VoiceAlertPriority.NORMAL,
            clip = RecordedVoiceClip.OBD_DISCONNECTED,
            cooldownKey = "obd_disconnected_$key",
            cooldownMs = cooldown
        )
    }

    override fun onCleared() {
        pollingJob?.cancel()
        receiverJob?.cancel()
        networkJob?.cancel()
        directReconnectJob?.cancel()
        super.onCleared()
    }

    private fun saveLastEvaluation(text: String) {
        val time = SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr", "TR")).format(Date())
        prefs.edit().putString("last_evaluation", text).putString("last_evaluation_time", time).apply()
        _uiState.value = _uiState.value.copy(lastEvaluation = text, lastEvaluationTime = time)
    }

    private fun formatLiveTime(epochMs: Long): String =
        SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(epochMs))

    // 131: Yeni örnek geldiyse alanları eski örnekle doldurma.
    // Özellikle hız/yakıt gibi kritik alanlarda eski değer yeni zaman damgasıyla
    // "taze" görünmemeli; kaynak yöneticisi ancak gerçekten gelen veriyi kullanır.
    private fun mergeLiveData(@Suppress("UNUSED_PARAMETER") old: ObdLiveData, fresh: ObdLiveData): ObdLiveData = fresh

    private fun saveLastLiveData(data: ObdLiveData, time: String, epochMs: Long = System.currentTimeMillis()) {
        prefs.edit().apply {
            fun intValue(key: String, value: Int?) { if (value == null) remove(key) else putInt(key, value) }
            fun doubleValue(key: String, value: Double?) { if (value == null) remove(key) else putLong(key, java.lang.Double.doubleToRawLongBits(value)) }
            intValue("last_rpm", data.rpm); intValue("last_coolant", data.coolantCelsius)
            doubleValue("last_battery", data.batteryVoltage); intValue("last_load", data.engineLoadPercent)
            intValue("last_throttle", data.throttlePercent); intValue("last_intake", data.intakeAirCelsius)
            doubleValue("last_maf", data.mafGramsPerSecond); intValue("last_runtime", data.engineRuntimeSeconds)
            intValue("last_speed", data.vehicleSpeedKmh); intValue("last_map", data.manifoldPressureKpa)
            intValue("last_baro", data.barometricPressureKpa); intValue("last_boost", data.boostPressureKpa)
            intValue("last_rail", data.fuelRailPressureKpa); intValue("last_fuel_level", data.fuelLevelPercent)
            intValue("last_ambient", data.ambientAirCelsius); doubleValue("last_ecu_voltage", data.controlModuleVoltage)
            intValue("last_pedal", data.acceleratorPedalPercent); intValue("last_oil", data.engineOilCelsius)
            doubleValue("last_fuel_rate", data.engineFuelRateLitersHour)
            if (data.engineFuelRateSource == null) remove("last_fuel_rate_source") else putString("last_fuel_rate_source", data.engineFuelRateSource)
            intValue("last_egr_cmd", data.commandedEgrPercent); intValue("last_egr_error", data.egrErrorPercent)
            intValue("last_mil_distance", data.distanceWithMilKm); intValue("last_warmups", data.warmUpsSinceClear); intValue("last_clear_distance", data.distanceSinceClearKm)
            doubleValue("last_injection_timing", data.fuelInjectionTimingDegrees); intValue("last_abs_rail", data.absoluteFuelRailPressureKpa)
            intValue("last_torque", data.actualEngineTorquePercent)
            if (data.milOn == null) remove("last_mil") else putBoolean("last_mil", data.milOn)
            putString("last_confirmed_dtc", data.confirmedDtcCodes.joinToString(","))
            putString("last_pending_dtc", data.pendingDtcCodes.joinToString(","))
            putString("last_permanent_dtc", data.permanentDtcCodes.joinToString(","))
            putString("last_live_data_time", time)
            putLong("last_live_data_epoch", epochMs)
        }.apply()
    }

    private fun restoreLastLiveData(): ObdLiveData {
        fun intValue(key: String) = if (prefs.contains(key)) prefs.getInt(key, 0) else null
        fun doubleValue(key: String) = if (prefs.contains(key)) java.lang.Double.longBitsToDouble(prefs.getLong(key, 0L)) else null
        fun codes(key: String) = prefs.getString(key, "").orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        return ObdLiveData(
            rpm = intValue("last_rpm"), coolantCelsius = intValue("last_coolant"), batteryVoltage = doubleValue("last_battery"),
            engineLoadPercent = intValue("last_load"), throttlePercent = intValue("last_throttle"), intakeAirCelsius = intValue("last_intake"),
            mafGramsPerSecond = doubleValue("last_maf"), engineRuntimeSeconds = intValue("last_runtime"), vehicleSpeedKmh = intValue("last_speed"),
            manifoldPressureKpa = intValue("last_map"), barometricPressureKpa = intValue("last_baro"), boostPressureKpa = intValue("last_boost"),
            fuelRailPressureKpa = intValue("last_rail"), fuelLevelPercent = intValue("last_fuel_level"), ambientAirCelsius = intValue("last_ambient"),
            controlModuleVoltage = doubleValue("last_ecu_voltage"), acceleratorPedalPercent = intValue("last_pedal"), engineOilCelsius = intValue("last_oil"),
            engineFuelRateLitersHour = doubleValue("last_fuel_rate"), engineFuelRateSource = prefs.getString("last_fuel_rate_source", null),
            commandedEgrPercent = intValue("last_egr_cmd"), egrErrorPercent = intValue("last_egr_error"),
            distanceWithMilKm = intValue("last_mil_distance"), warmUpsSinceClear = intValue("last_warmups"), distanceSinceClearKm = intValue("last_clear_distance"),
            fuelInjectionTimingDegrees = doubleValue("last_injection_timing"), absoluteFuelRailPressureKpa = intValue("last_abs_rail"),
            actualEngineTorquePercent = intValue("last_torque"),
            milOn = if (prefs.contains("last_mil")) prefs.getBoolean("last_mil", false) else null,
            confirmedDtcCodes = codes("last_confirmed_dtc"), pendingDtcCodes = codes("last_pending_dtc"), permanentDtcCodes = codes("last_permanent_dtc")
        )
    }
}
