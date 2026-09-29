package com.aldanmaz.webestenheater.ble

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int,
    val likelyHeater: Boolean
)

data class HeaterLiveStatus(
    val machineState: Int,
    val machineStateText: String,
    val batteryVoltage: Float,
    val altitudeM: Int,
    val ambientC: Float,
    val heaterShellC: Float,
    val pumpHz: Float,
    val ignitionPowerW: Float,
    val fanRpm: Int,
    val intakeC: Float,
    val outletC: Float,
    val errorMask: Int,
    val targetGear: Int,
    val targetTemperatureC: Int
)

data class BleUiState(
    val permissionGranted: Boolean = false,
    val adapterAvailable: Boolean = false,
    val adapterEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val connectionState: String = "BAĞLI DEĞİL",
    val connectedName: String = "",
    val connectedAddress: String = "",
    val service181aFound: Boolean = false,
    val writeCharacteristicFound: Boolean = false,
    val notifyCharacteristicCount: Int = 0,
    val negotiatedMtu: Int = 23,
    val devices: List<BleDeviceItem> = emptyList(),
    val lastRxHex: String = "",
    val rxLog: List<String> = emptyList(),
    val liveStatus: HeaterLiveStatus? = null,
    val autoReconnect: Boolean = true,
    val commandLockEnabled: Boolean = true,
    val message: String = "Bluetooth testi hazır"
)