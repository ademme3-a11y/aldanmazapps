package com.aldanmaz.drivedashboard.ui.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aldanmaz Drive açıkken gelen GSM aramasını izler.
 * Sistem telefon uygulamasının yerini almaz; sadece sürüş ekranında büyük bir kontrol kartı gösterir.
 */
class IncomingCallController(
    private val context: Context
) {
    enum class State {
        IDLE,
        RINGING,
        OFFHOOK
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private var started = false

    private val callback31 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                updateState(state)
            }
        }
    } else {
        null
    }

    @Suppress("DEPRECATION")
    private val legacyListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            updateState(state)
        }
    }

    fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasAnswerPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started || !hasPhoneStatePermission()) return
        started = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    callback31!!
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    legacyListener,
                    PhoneStateListener.LISTEN_CALL_STATE
                )
            }

            @Suppress("DEPRECATION")
            updateState(telephonyManager.callState)
        } catch (_: SecurityException) {
            started = false
            _state.value = State.IDLE
        }
    }

    fun stop() {
        if (!started) return
        started = false

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.unregisterTelephonyCallback(callback31!!)
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    legacyListener,
                    PhoneStateListener.LISTEN_NONE
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    fun answer(): Boolean {
        if (!hasAnswerPermission()) return false
        return runCatching {
            telecomManager.acceptRingingCall()
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    fun reject(): Boolean {
        if (!hasAnswerPermission()) return false
        return runCatching {
            telecomManager.endCall()
        }.getOrDefault(false)
    }

    private fun updateState(callState: Int) {
        _state.value = when (callState) {
            TelephonyManager.CALL_STATE_RINGING -> State.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> State.OFFHOOK
            else -> State.IDLE
        }
    }
}
