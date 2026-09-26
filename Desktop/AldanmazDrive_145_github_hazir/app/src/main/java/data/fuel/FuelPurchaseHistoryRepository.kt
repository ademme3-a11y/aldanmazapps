package com.aldanmaz.drivedashboard.data.fuel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FuelPurchaseRecord(
    val id: String,
    val purchasedAtEpochMillis: Long,
    val driverId: String,
    val driverName: String,
    val liters: Double,
    val costTl: Double,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
) {
    val pricePerLiter: Double
        get() = if (liters > 0.0) costTl / liters else 0.0
}

class FuelPurchaseHistoryRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("fuel_purchase_history", Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<FuelPurchaseRecord> {
        val source = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(source)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        FuelPurchaseRecord(
                            id = item.optString("id"),
                            purchasedAtEpochMillis = item.optLong("time"),
                            driverId = item.optString("driverId", "1"),
                            driverName = item.optString("driverName", "Mehmet"),
                            liters = item.optDouble("liters", 0.0).coerceAtLeast(0.0),
                            costTl = item.optDouble("costTl", 0.0).coerceAtLeast(0.0),
                            latitude = item.optNullableDouble("latitude"),
                            longitude = item.optNullableDouble("longitude"),
                            address = item.optNullableString("address"),
                        )
                    )
                }
            }.sortedByDescending { it.purchasedAtEpochMillis }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(
        driverId: String,
        driverName: String,
        liters: Double,
        costTl: Double,
        latitude: Double?,
        longitude: Double?,
        address: String?,
    ): FuelPurchaseRecord {
        val record = FuelPurchaseRecord(
            id = UUID.randomUUID().toString(),
            purchasedAtEpochMillis = System.currentTimeMillis(),
            driverId = driverId,
            driverName = driverName,
            liters = liters.coerceAtLeast(0.0),
            costTl = costTl.coerceAtLeast(0.0),
            latitude = latitude,
            longitude = longitude,
            address = address?.trim()?.takeIf(String::isNotBlank),
        )
        val records = listOf(record) + getAll()
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("time", item.purchasedAtEpochMillis)
                put("driverId", item.driverId)
                put("driverName", item.driverName)
                put("liters", item.liters)
                put("costTl", item.costTl)
                put("latitude", item.latitude ?: JSONObject.NULL)
                put("longitude", item.longitude ?: JSONObject.NULL)
                put("address", item.address ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
        return record
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).trim().takeIf(String::isNotBlank)

    private companion object {
        const val KEY_RECORDS = "records_json"
        const val MAX_RECORDS = 1_000
    }
}
