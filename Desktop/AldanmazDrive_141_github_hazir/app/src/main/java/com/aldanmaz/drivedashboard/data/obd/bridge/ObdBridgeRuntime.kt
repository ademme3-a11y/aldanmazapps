package com.aldanmaz.drivedashboard.data.obd.bridge

import com.aldanmaz.drivedashboard.data.obd.ObdConnectionInfo
import com.aldanmaz.drivedashboard.data.obd.ObdLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ObdPhoneBridgeState(
    val running: Boolean = false,
    val obdConnected: Boolean = false,
    val selectedAddress: String? = null,
    val connectionInfo: ObdConnectionInfo? = null,
    val liveData: ObdLiveData = ObdLiveData(),
    val lastUpdateEpochMs: Long? = null,
    val clientCount: Int = 0,
    val errorMessage: String? = null
)

object ObdBridgeRuntime {
    private val _phoneState = MutableStateFlow(ObdPhoneBridgeState())
    val phoneState: StateFlow<ObdPhoneBridgeState> = _phoneState.asStateFlow()

    fun update(transform: (ObdPhoneBridgeState) -> ObdPhoneBridgeState) {
        _phoneState.value = transform(_phoneState.value)
    }

    fun reset() {
        _phoneState.value = ObdPhoneBridgeState()
    }
}
