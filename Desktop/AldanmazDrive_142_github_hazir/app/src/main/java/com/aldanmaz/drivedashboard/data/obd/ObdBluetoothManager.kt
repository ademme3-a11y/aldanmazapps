package com.aldanmaz.drivedashboard.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class ObdDevice(val name: String, val address: String)

data class ObdConnectionInfo(
    val adapterIdentity: String,
    val protocol: String,
    val ecuResponded: Boolean
)

data class DpfData(
    val supported: Boolean = false,
    val differentialPressureRaw: String? = null,
    val temperatureRaw: String? = null
)

data class ObdLiveData(
    val rpm: Int? = null,
    val coolantCelsius: Int? = null,
    val batteryVoltage: Double? = null,
    val engineLoadPercent: Int? = null,
    val throttlePercent: Int? = null,
    val intakeAirCelsius: Int? = null,
    val mafGramsPerSecond: Double? = null,
    val engineRuntimeSeconds: Int? = null,
    val vehicleSpeedKmh: Int? = null,
    val manifoldPressureKpa: Int? = null,
    val barometricPressureKpa: Int? = null,
    val boostPressureKpa: Int? = null,
    val fuelRailPressureKpa: Int? = null,
    val fuelLevelPercent: Int? = null,
    val ambientAirCelsius: Int? = null,
    val controlModuleVoltage: Double? = null,
    val acceleratorPedalPercent: Int? = null,
    val engineOilCelsius: Int? = null,
    val engineFuelRateLitersHour: Double? = null,
    /** 015E doğrudan ise OBD, MAF/MAP tabanlı türetildiyse OBD-HESAP. */
    val engineFuelRateSource: String? = null,
    val commandedEgrPercent: Int? = null,
    val egrErrorPercent: Int? = null,
    val distanceWithMilKm: Int? = null,
    val warmUpsSinceClear: Int? = null,
    val distanceSinceClearKm: Int? = null,
    val fuelInjectionTimingDegrees: Double? = null,
    val absoluteFuelRailPressureKpa: Int? = null,
    val actualEngineTorquePercent: Int? = null,
    val milOn: Boolean? = null,
    val confirmedDtcCodes: List<String> = emptyList(),
    val pendingDtcCodes: List<String> = emptyList(),
    val permanentDtcCodes: List<String> = emptyList(),
    val supportedPids: Set<Int> = emptySet(),
    val dpf: DpfData = DpfData()
) { val dtcCodes: List<String> get() = (confirmedDtcCodes + pendingDtcCodes + permanentDtcCodes).distinct() }

class ObdBluetoothManager(context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var socket: BluetoothSocket? = null
    private var supportedMode01Pids: Set<Int> = emptySet()
    private val forcedPidAvailability = mutableMapOf<Int, Boolean>()

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    fun isBluetoothAvailable(): Boolean = adapter != null
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<ObdDevice> =
        adapter?.bondedDevices.orEmpty()
            .sortedWith(compareBy<android.bluetooth.BluetoothDevice> { obdNameScore(it.name) }.thenBy { it.name ?: it.address })
            .map { ObdDevice(it.name ?: "OBD cihazı", it.address) }

    private fun obdNameScore(name: String?): Int {
        val n = name.orEmpty().lowercase()
        return if (listOf("obd", "elm", "vlink", "vgate", "fobd", "fnirsi").any(n::contains)) 0 else 1
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Result<ObdConnectionInfo> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()

            val btAdapter = adapter ?: error("Bluetooth kullanılamıyor")
            if (!btAdapter.isEnabled) error("Bluetooth kapalı")

            val device = btAdapter.getRemoteDevice(address)
            btAdapter.cancelDiscovery()

            val attempts = buildList<() -> BluetoothSocket> {
                add { device.createRfcommSocketToServiceRecord(SPP_UUID) }
                add { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }
                add {
                    @Suppress("UNCHECKED_CAST")
                    val method = device.javaClass.getMethod("createRfcommSocket", Integer.TYPE)
                    method.invoke(device, 1) as BluetoothSocket
                }
            }

            var lastError: Throwable? = null
            var connectedSocket: BluetoothSocket? = null

            for (factory in attempts) {
                val candidate: BluetoothSocket = try {
                    factory()
                } catch (t: Throwable) {
                    lastError = t
                    continue
                }

                try {
                    candidate.connect()
                    connectedSocket = candidate
                    break
                } catch (t: Throwable) {
                    lastError = t
                    runCatching { candidate.close() }
                }
            }

            socket = connectedSocket ?: error(
                "OBD Bluetooth bağlantısı kurulamadı: ${lastError?.localizedMessage ?: "RFCOMM bağlantı hatası"}"
            )

            initializeElm327()
        }.onFailure {
            disconnectInternal()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        runCatching { socket?.inputStream?.close() }
        runCatching { socket?.outputStream?.close() }
        runCatching { socket?.close() }
        socket = null
        forcedPidAvailability.clear()
    }

    suspend fun readLiveData(): ObdLiveData = withContext(Dispatchers.IO) {
        if (socket?.isConnected != true) error("OBD bağlantısı yok")

        val map = pidIfSupported(0x0B, 1)?.firstOrNull()
        val baro = pidIfSupported(0x33, 1)?.firstOrNull()
        val rpmBytes = pidIfSupported(0x0C, 2)
        val rpm = rpmBytes?.let { ((it[0] * 256) + it[1]) / 4 }
        val maf = pidIfSupported(0x10, 2)?.let { ((it[0] * 256) + it[1]) / 100.0 }
        val intakeC = pidIfSupported(0x0F, 1)?.firstOrNull()?.minus(40)

        // 131: Hız, depo seviyesi ve yakıt debisi destek bitmapinde görünmese bile
        // bir kez doğrudan denenir. Dacia/Renault'ta birden fazla ECU cevabı nedeniyle
        // ilk bitmap bu PID'leri saklayabiliyordu.
        val speed = criticalPid(0x0D, 1)?.firstOrNull()
        val fuelLevel = criticalPid(0x2F, 1)?.firstOrNull()?.let { it * 100 / 255 }
        val directFuelRate = criticalPid(0x5E, 2)?.let { ((it[0] * 256) + it[1]) / 20.0 }
        val calculatedFuelRate = if (directFuelRate == null) {
            estimateDieselFuelRateLitersHour(
                mafGramsPerSecond = maf,
                manifoldPressureKpa = map,
                rpm = rpm,
                intakeAirCelsius = intakeC,
            )
        } else null

        ObdLiveData(
            rpm = rpm,
            coolantCelsius = pidIfSupported(0x05, 1)?.firstOrNull()?.minus(40),
            batteryVoltage = command("ATRV", 2_500L).firstNumber(),
            engineLoadPercent = pidIfSupported(0x04, 1)?.firstOrNull()?.let { it * 100 / 255 },
            throttlePercent = pidIfSupported(0x11, 1)?.firstOrNull()?.let { it * 100 / 255 },
            intakeAirCelsius = intakeC,
            mafGramsPerSecond = maf,
            engineRuntimeSeconds = pidIfSupported(0x1F, 2)?.let { (it[0] * 256) + it[1] },
            vehicleSpeedKmh = speed,
            manifoldPressureKpa = map,
            barometricPressureKpa = baro,
            boostPressureKpa = if (map != null && baro != null) (map - baro).coerceAtLeast(0) else null,
            fuelRailPressureKpa = pidIfSupported(0x23, 2)?.let { ((it[0] * 256) + it[1]) * 10 },
            fuelLevelPercent = fuelLevel,
            ambientAirCelsius = pidIfSupported(0x46, 1)?.firstOrNull()?.minus(40),
            controlModuleVoltage = pidIfSupported(0x42, 2)?.let { ((it[0] * 256) + it[1]) / 1000.0 },
            acceleratorPedalPercent = pidIfSupported(0x49, 1)?.firstOrNull()?.let { it * 100 / 255 },
            engineOilCelsius = pidIfSupported(0x5C, 1)?.firstOrNull()?.minus(40),
            engineFuelRateLitersHour = directFuelRate ?: calculatedFuelRate,
            engineFuelRateSource = when {
                directFuelRate != null -> "OBD"
                calculatedFuelRate != null -> "OBD-HESAP"
                else -> null
            },
            commandedEgrPercent = pidIfSupported(0x2C, 1)?.firstOrNull()?.let { it * 100 / 255 },
            egrErrorPercent = pidIfSupported(0x2D, 1)?.firstOrNull()?.let { ((it * 100.0 / 128.0) - 100.0).toInt() },
            distanceWithMilKm = pidIfSupported(0x21, 2)?.let { (it[0] * 256) + it[1] },
            warmUpsSinceClear = pidIfSupported(0x30, 1)?.firstOrNull(),
            distanceSinceClearKm = pidIfSupported(0x31, 2)?.let { (it[0] * 256) + it[1] },
            fuelInjectionTimingDegrees = pidIfSupported(0x5D, 2)?.let { (((it[0] * 256) + it[1]) / 128.0) - 210.0 },
            absoluteFuelRailPressureKpa = pidIfSupported(0x59, 2)?.let { ((it[0] * 256) + it[1]) * 10 },
            actualEngineTorquePercent = pidIfSupported(0x62, 1)?.firstOrNull()?.minus(125),
            milOn = pidIfSupported(0x01, 4)?.firstOrNull()?.let { it and 0x80 != 0 },
            confirmedDtcCodes = readDtcCodes("03", "43"),
            pendingDtcCodes = readDtcCodes("07", "47"),
            permanentDtcCodes = readDtcCodes("0A", "4A"),
            supportedPids = supportedMode01Pids,
            dpf = readDpfData()
        )
    }

    private fun estimateDieselFuelRateLitersHour(
        mafGramsPerSecond: Double?,
        manifoldPressureKpa: Int?,
        rpm: Int?,
        intakeAirCelsius: Int?,
    ): Double? {
        // Standart PID yoksa yalnız yaklaşık değer üret. Bu değer ekranda OBD-HESAP olarak
        // açıkça işaretlenir; doğrudan ECU yakıt debisi gibi sunulmaz.
        if (mafGramsPerSecond != null && mafGramsPerSecond > 0.1) {
            // 1.5 dCi için pratik dizel hava/yakıt oranı yaklaşımı. Rölanti/yük değişiminde
            // kesin tüketim değildir; ALD hesaplamasına kıyasla OBD hava akışına dayanır.
            val assumedAfr = 32.0
            val dieselDensityGramsPerLiter = 832.0
            return (mafGramsPerSecond * 3600.0 / assumedAfr / dieselDensityGramsPerLiter)
                .takeIf { it in 0.05..40.0 }
        }
        if (manifoldPressureKpa != null && rpm != null && intakeAirCelsius != null && rpm > 0) {
            // MAF yoksa MAP/RPM/IAT ile yaklaşık hava kütlesi. 1.461 L K9K hacmi ve
            // muhafazakâr %80 volumetrik verim kullanılır.
            val displacementLiters = 1.461
            val volumetricEfficiency = 0.80
            val kelvin = intakeAirCelsius + 273.15
            if (kelvin <= 0.0) return null
            val airGramsPerSecond =
                (manifoldPressureKpa * 1000.0 * displacementLiters / 1000.0 * volumetricEfficiency * rpm / 2.0) /
                    (287.05 * kelvin) * 1000.0 / 60.0
            val assumedAfr = 32.0
            val litersHour = airGramsPerSecond * 3600.0 / assumedAfr / 832.0
            return litersHour.takeIf { it in 0.05..40.0 }
        }
        return null
    }

    private fun initializeElm327(): ObdConnectionInfo {
        val reset = commandBlocking("ATZ", timeoutMs = 5_000L)
        if (reset.isBlank()) error("OBD adaptörü ATZ komutuna yanıt vermedi")

        Thread.sleep(250L)
        commandBlocking("ATE0")
        commandBlocking("ATL0")
        commandBlocking("ATS0")
        commandBlocking("ATH1")
        commandBlocking("ATAT1")
        commandBlocking("ATSP0", timeoutMs = 4_000L)

        val identity = cleanText(commandBlocking("ATI", timeoutMs = 3_000L))
            .ifBlank { "ELM327 / OBD-II" }

        // Otomatik protokol seçimi bazı klonlarda ilk ECU sorgusunda birkaç saniye sürebilir.
        val ecuProbe = commandBlocking("0100", timeoutMs = 9_000L)
        val cleanProbe = normalizeHexResponse(ecuProbe)
        val ecuResponded = cleanProbe.contains("4100")
        if (ecuResponded) supportedMode01Pids = readSupportedPids(cleanProbe)

        val protocol = cleanText(commandBlocking("ATDP", timeoutMs = 3_000L))
            .ifBlank { if (ecuResponded) "Otomatik protokol" else "Protokol belirlenemedi" }

        if (!ecuResponded) {
            val upper = ecuProbe.uppercase()
            val detail = when {
                upper.contains("UNABLE TO CONNECT") -> "Araç ECU'suna bağlanılamadı"
                upper.contains("NO DATA") -> "Araç ECU veri vermedi"
                upper.contains("BUS INIT") -> "OBD veri yolu başlatılamadı"
                else -> "Araç ECU'sundan Mode 01 yanıtı alınamadı"
            }
            error("ELM adaptörü bağlı ancak $detail. Kontağı açık tutun ve doğru OBD cihazını seçin.")
        }

        return ObdConnectionInfo(
            adapterIdentity = identity,
            protocol = protocol,
            ecuResponded = true
        )
    }

    private fun pid0104() = pid("0104", "4104", 1)
    private fun pid0105() = pid("0105", "4105", 1)
    private fun pid010C() = pid("010C", "410C", 2)
    private fun pid010F() = pid("010F", "410F", 1)
    private fun pid0110() = pid("0110", "4110", 2)
    private fun pid0111() = pid("0111", "4111", 1)
    private fun pid011F() = pid("011F", "411F", 2)

    private fun pidIfSupported(pid: Int, count: Int): List<Int>? {
        if (supportedMode01Pids.isNotEmpty() && pid !in supportedMode01Pids) return null
        val hex = pid.toString(16).uppercase().padStart(2, '0')
        return pid("01$hex", "41$hex", count)
    }

    private fun criticalPid(pid: Int, count: Int): List<Int>? {
        if (pid in supportedMode01Pids) return pidIfSupported(pid, count)
        val known = forcedPidAvailability[pid]
        if (known == false) return null
        val hex = pid.toString(16).uppercase().padStart(2, '0')
        val value = pid("01$hex", "41$hex", count)
        forcedPidAvailability[pid] = value != null
        return value
    }

    private fun readSupportedPids(firstResponse: String): Set<Int> {
        val result = linkedSetOf<Int>()
        var base = 0
        var response = firstResponse
        while (base <= 0x80) {
            val header = "41" + base.toString(16).uppercase().padStart(2, '0')
            // 131: Aynı PID destek sorgusuna cevap veren tüm ECU'ların bitmaplerini OR'la.
            // Böylece ilk ECU'da görünmeyen 010D/012F gibi PID'ler yanlışlıkla elenmez.
            var searchFrom = 0
            var unionMask = 0L
            var found = false
            while (true) {
                val start = response.indexOf(header, searchFrom)
                if (start < 0) break
                val maskStart = start + header.length
                if (response.length >= maskStart + 8) {
                    response.substring(maskStart, maskStart + 8).toLongOrNull(16)?.let {
                        unionMask = unionMask or it
                        found = true
                    }
                }
                searchFrom = start + header.length
            }
            if (!found) break
            for (bit in 0 until 32) if ((unionMask and (1L shl (31 - bit))) != 0L) result += base + bit + 1
            if (base + 0x20 !in result) break
            base += 0x20
            response = normalizeHexResponse(commandBlocking("01" + base.toString(16).uppercase().padStart(2, '0'), 3_500L))
        }
        return result
    }

    private fun pid(command: String, header: String, count: Int): List<Int>? {
        val clean = normalizeHexResponse(commandBlocking(command, timeoutMs = 3_500L))
        val start = clean.indexOf(header)
        if (start < 0) return null
        val payload = clean.substring(start + header.length)
        if (payload.length < count * 2) return null
        return runCatching {
            (0 until count).map { index ->
                payload.substring(index * 2, index * 2 + 2).toInt(16)
            }
        }.getOrNull()
    }

    private fun readDpfData(): DpfData {
        fun raw(pid: Int): String? {
            if (supportedMode01Pids.isNotEmpty() && pid !in supportedMode01Pids) return null
            val hex = pid.toString(16).uppercase().padStart(2, '0')
            val response = commandBlocking("01$hex", timeoutMs = 3_500L)
            val clean = normalizeHexResponse(response)
            val header = "41$hex"
            val start = clean.indexOf(header)
            if (start < 0 || response.uppercase().contains("NO DATA")) return null
            return clean.substring(start + header.length).takeWhile { it in "0123456789ABCDEF" }.take(24).ifBlank { null }
        }
        val pressure = raw(0x7A)
        val temperature = raw(0x7C)
        return DpfData(pressure != null || temperature != null, pressure, temperature)
    }

    private fun readDtcCodes(command: String, responseHeader: String): List<String> {
        val clean = normalizeHexResponse(commandBlocking(command, timeoutMs = 4_000L))
        val start = clean.indexOf(responseHeader)
        if (start < 0) return emptyList()
        val payload = clean.substring(start + responseHeader.length).takeWhile { it in "0123456789ABCDEF" }
        return payload.chunked(4).mapNotNull { raw ->
            if (raw.length == 4 && raw != "0000") decodeDtc(raw) else null
        }
    }

    private fun decodeDtc(raw: String): String {
        val first = raw.substring(0, 2).toInt(16)
        val family = "PCBU"[(first shr 6) and 0x03]
        val digit = (first shr 4) and 0x03
        return "$family$digit${raw.substring(1)}"
    }

    private suspend fun command(value: String, timeoutMs: Long): String =
        withContext(Dispatchers.IO) { commandBlocking(value, timeoutMs) }

    private fun commandBlocking(value: String, timeoutMs: Long = 2_800L): String {
        val activeSocket = socket?.takeIf { it.isConnected } ?: error("OBD bağlantısı yok")
        val output = activeSocket.outputStream
        val input = activeSocket.inputStream

        // Önce önceki komuttan kalmış birkaç byte varsa temizle.
        while (input.available() > 0) input.read()

        output.write("$value\r".toByteArray(Charsets.US_ASCII))
        output.flush()

        val result = StringBuilder()
        val started = System.currentTimeMillis()
        var promptSeen = false

        while (System.currentTimeMillis() - started < timeoutMs) {
            if (input.available() > 0) {
                val read = input.read()
                if (read < 0) break
                val ch = read.toChar()
                if (ch == '>') {
                    promptSeen = true
                    break
                }
                result.append(ch)
            } else {
                Thread.sleep(18L)
            }
        }

        if (!promptSeen && result.isBlank()) {
            error("OBD komut zaman aşımı: $value")
        }
        return result.toString()
    }

    private fun normalizeHexResponse(value: String): String =
        value.uppercase()
            .replace("SEARCHING...", "")
            .replace("SEARCHING", "")
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .filter { it in "0123456789ABCDEF" }

    private fun cleanText(value: String): String =
        value.replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.firstNumber(): Double? =
        Regex("([0-9]+(?:\\.[0-9]+)?)")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
}
