package com.aldanmaz.webestenheater.ble

import com.aldanmaz.webestenheater.protocol.HeatGenieProtocol

object HeatGenieStatusParser {
    fun parse(frame: ByteArray): HeaterLiveStatus? {
        if (frame.size < 12 || (frame[0].toInt() and 0xFF) != 0xAA) return null
        if (!HeatGenieProtocol.isValidFrame(frame)) return null
        if (((frame.getOrNull(7)?.toInt() ?: -1) and 0xFF) != 0xF2) return null

        val wordsLength = 4 * (((frame[1].toInt() and 0xFF) + 1))
        val payloadEnd = (8 + wordsLength).coerceAtMost(frame.size - 2)
        if (payloadEnd <= 8) return null
        val p = frame.copyOfRange(8, payloadEnd)
        if (p.size < 22) return null

        fun u16(offset: Int): Int {
            if (offset + 1 >= p.size) return 0
            return (p[offset].toInt() and 0xFF) or ((p[offset + 1].toInt() and 0xFF) shl 8)
        }
        fun s16(offset: Int): Int {
            val v = u16(offset)
            return if (v and 0x8000 != 0) v - 0x10000 else v
        }

        val machineState = p[0].toInt() and 0x0F
        val targetGear = if (p.size > 32) p[32].toInt() and 0xFF else 0
        val targetTemp = if (p.size > 33) p[33].toInt() and 0xFF else 0

        return HeaterLiveStatus(
            machineState = machineState,
            machineStateText = machineStateText(machineState),
            batteryVoltage = u16(2) / 10f,
            altitudeM = u16(4),
            ambientC = s16(6) / 10f,
            heaterShellC = s16(8) / 10f,
            pumpHz = u16(10) / 10f,
            ignitionPowerW = u16(12) / 10f,
            fanRpm = u16(14),
            intakeC = s16(16) / 10f,
            outletC = s16(18) / 10f,
            errorMask = u16(20),
            targetGear = targetGear,
            targetTemperatureC = targetTemp
        )
    }

    private fun machineStateText(state: Int): String = when (state) {
        0 -> "AÇILIŞ / RESET"
        1 -> "ATEŞLEME"
        2 -> "OTOMATİK ÇALIŞMA"
        3 -> "MANUEL ÇALIŞMA"
        4 -> "ARTIK YAKIT / SOĞUTMA"
        5 -> "BEKLEME"
        6 -> "HATA"
        7 -> "MANUEL POMPA"
        8 -> "HAVALANDIRMA"
        9 -> "START-STOP ÇALIŞMA"
        10 -> "START-STOP DURUMU"
        else -> "BİLİNMEYEN ($state)"
    }
}