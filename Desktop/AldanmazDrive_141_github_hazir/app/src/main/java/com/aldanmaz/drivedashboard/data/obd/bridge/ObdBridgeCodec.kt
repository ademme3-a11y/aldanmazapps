package com.aldanmaz.drivedashboard.data.obd.bridge

import com.aldanmaz.drivedashboard.data.obd.DpfData
import com.aldanmaz.drivedashboard.data.obd.ObdConnectionInfo
import com.aldanmaz.drivedashboard.data.obd.ObdLiveData
import org.json.JSONArray
import org.json.JSONObject

data class ObdBridgeSnapshot(
    val timestampEpochMs: Long,
    val connectionInfo: ObdConnectionInfo,
    val liveData: ObdLiveData
)

object ObdBridgeCodec {
    const val PROTOCOL_VERSION = 1
    const val PORT = 35066

    fun encode(snapshot: ObdBridgeSnapshot): String {
        val root = JSONObject()
        root.put("version", PROTOCOL_VERSION)
        root.put("type", "snapshot")
        root.put("timestamp", snapshot.timestampEpochMs)
        root.put("connection", JSONObject().apply {
            put("adapterIdentity", snapshot.connectionInfo.adapterIdentity)
            put("protocol", snapshot.connectionInfo.protocol)
            put("ecuResponded", snapshot.connectionInfo.ecuResponded)
        })
        root.put("data", encodeLiveData(snapshot.liveData))
        return root.toString()
    }

    fun decode(line: String): ObdBridgeSnapshot {
        val root = JSONObject(line)
        require(root.optInt("version", 0) == PROTOCOL_VERSION) { "OBD köprü protokolü uyumsuz" }
        val connection = root.getJSONObject("connection")
        return ObdBridgeSnapshot(
            timestampEpochMs = root.optLong("timestamp", System.currentTimeMillis()),
            connectionInfo = ObdConnectionInfo(
                adapterIdentity = connection.optString("adapterIdentity", "Telefon OBD Köprüsü"),
                protocol = connection.optString("protocol", "OBD-II"),
                ecuResponded = connection.optBoolean("ecuResponded", true)
            ),
            liveData = decodeLiveData(root.getJSONObject("data"))
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun encodeLiveData(d: ObdLiveData) = JSONObject().apply {
        putNullable("rpm", d.rpm)
        putNullable("coolantCelsius", d.coolantCelsius)
        putNullable("batteryVoltage", d.batteryVoltage)
        putNullable("engineLoadPercent", d.engineLoadPercent)
        putNullable("throttlePercent", d.throttlePercent)
        putNullable("intakeAirCelsius", d.intakeAirCelsius)
        putNullable("mafGramsPerSecond", d.mafGramsPerSecond)
        putNullable("engineRuntimeSeconds", d.engineRuntimeSeconds)
        putNullable("vehicleSpeedKmh", d.vehicleSpeedKmh)
        putNullable("manifoldPressureKpa", d.manifoldPressureKpa)
        putNullable("barometricPressureKpa", d.barometricPressureKpa)
        putNullable("boostPressureKpa", d.boostPressureKpa)
        putNullable("fuelRailPressureKpa", d.fuelRailPressureKpa)
        putNullable("fuelLevelPercent", d.fuelLevelPercent)
        putNullable("ambientAirCelsius", d.ambientAirCelsius)
        putNullable("controlModuleVoltage", d.controlModuleVoltage)
        putNullable("acceleratorPedalPercent", d.acceleratorPedalPercent)
        putNullable("engineOilCelsius", d.engineOilCelsius)
        putNullable("engineFuelRateLitersHour", d.engineFuelRateLitersHour)
        putNullable("engineFuelRateSource", d.engineFuelRateSource)
        putNullable("commandedEgrPercent", d.commandedEgrPercent)
        putNullable("egrErrorPercent", d.egrErrorPercent)
        putNullable("distanceWithMilKm", d.distanceWithMilKm)
        putNullable("warmUpsSinceClear", d.warmUpsSinceClear)
        putNullable("distanceSinceClearKm", d.distanceSinceClearKm)
        putNullable("fuelInjectionTimingDegrees", d.fuelInjectionTimingDegrees)
        putNullable("absoluteFuelRailPressureKpa", d.absoluteFuelRailPressureKpa)
        putNullable("actualEngineTorquePercent", d.actualEngineTorquePercent)
        putNullable("milOn", d.milOn)
        put("confirmedDtcCodes", JSONArray(d.confirmedDtcCodes))
        put("pendingDtcCodes", JSONArray(d.pendingDtcCodes))
        put("permanentDtcCodes", JSONArray(d.permanentDtcCodes))
        put("supportedPids", JSONArray(d.supportedPids.toList()))
        put("dpf", JSONObject().apply {
            put("supported", d.dpf.supported)
            putNullable("differentialPressureRaw", d.dpf.differentialPressureRaw)
            putNullable("temperatureRaw", d.dpf.temperatureRaw)
        })
    }

    private fun decodeLiveData(o: JSONObject): ObdLiveData = ObdLiveData(
        rpm = o.optNullableInt("rpm"),
        coolantCelsius = o.optNullableInt("coolantCelsius"),
        batteryVoltage = o.optNullableDouble("batteryVoltage"),
        engineLoadPercent = o.optNullableInt("engineLoadPercent"),
        throttlePercent = o.optNullableInt("throttlePercent"),
        intakeAirCelsius = o.optNullableInt("intakeAirCelsius"),
        mafGramsPerSecond = o.optNullableDouble("mafGramsPerSecond"),
        engineRuntimeSeconds = o.optNullableInt("engineRuntimeSeconds"),
        vehicleSpeedKmh = o.optNullableInt("vehicleSpeedKmh"),
        manifoldPressureKpa = o.optNullableInt("manifoldPressureKpa"),
        barometricPressureKpa = o.optNullableInt("barometricPressureKpa"),
        boostPressureKpa = o.optNullableInt("boostPressureKpa"),
        fuelRailPressureKpa = o.optNullableInt("fuelRailPressureKpa"),
        fuelLevelPercent = o.optNullableInt("fuelLevelPercent"),
        ambientAirCelsius = o.optNullableInt("ambientAirCelsius"),
        controlModuleVoltage = o.optNullableDouble("controlModuleVoltage"),
        acceleratorPedalPercent = o.optNullableInt("acceleratorPedalPercent"),
        engineOilCelsius = o.optNullableInt("engineOilCelsius"),
        engineFuelRateLitersHour = o.optNullableDouble("engineFuelRateLitersHour"),
        engineFuelRateSource = o.optNullableString("engineFuelRateSource"),
        commandedEgrPercent = o.optNullableInt("commandedEgrPercent"),
        egrErrorPercent = o.optNullableInt("egrErrorPercent"),
        distanceWithMilKm = o.optNullableInt("distanceWithMilKm"),
        warmUpsSinceClear = o.optNullableInt("warmUpsSinceClear"),
        distanceSinceClearKm = o.optNullableInt("distanceSinceClearKm"),
        fuelInjectionTimingDegrees = o.optNullableDouble("fuelInjectionTimingDegrees"),
        absoluteFuelRailPressureKpa = o.optNullableInt("absoluteFuelRailPressureKpa"),
        actualEngineTorquePercent = o.optNullableInt("actualEngineTorquePercent"),
        milOn = o.optNullableBoolean("milOn"),
        confirmedDtcCodes = o.optStringList("confirmedDtcCodes"),
        pendingDtcCodes = o.optStringList("pendingDtcCodes"),
        permanentDtcCodes = o.optStringList("permanentDtcCodes"),
        supportedPids = o.optIntSet("supportedPids"),
        dpf = o.optJSONObject("dpf")?.let {
            DpfData(
                supported = it.optBoolean("supported", false),
                differentialPressureRaw = it.optNullableString("differentialPressureRaw"),
                temperatureRaw = it.optNullableString("temperatureRaw")
            )
        } ?: DpfData()
    )

    private fun JSONObject.isNullOrMissing(key: String) = !has(key) || isNull(key)
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNullOrMissing(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNullOrMissing(key)) null else optDouble(key)
    private fun JSONObject.optNullableBoolean(key: String): Boolean? = if (isNullOrMissing(key)) null else optBoolean(key)
    private fun JSONObject.optNullableString(key: String): String? = if (isNullOrMissing(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optStringList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return buildList { for (i in 0 until a.length()) add(a.optString(i)) }.filter { it.isNotBlank() }
    }

    private fun JSONObject.optIntSet(key: String): Set<Int> {
        val a = optJSONArray(key) ?: return emptySet()
        return buildSet { for (i in 0 until a.length()) add(a.optInt(i)) }
    }
}
