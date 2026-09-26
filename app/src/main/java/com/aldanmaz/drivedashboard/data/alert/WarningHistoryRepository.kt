package com.aldanmaz.drivedashboard.data.alert

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 131: Araç Modu alanında kısa süre görünen önemli teknik/hız uyarılarının kalıcı hafızası.
 * Eğim/rampa, genel güvenlik ve hava uyarıları özellikle bu geçmişe dahil edilmez.
 */
data class WarningHistoryEntry(
    val id: Long,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val source: String,
    val message: String,
    val technicalVehicleAlert: Boolean,
    val repeatCount: Int = 1,
)

class WarningHistoryRepository private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<WarningHistoryEntry>> = _entries.asStateFlow()

    @Synchronized
    fun record(
        source: String,
        message: String,
        technicalVehicleAlert: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val cleanMessage = message.trim()
        if (cleanMessage.isBlank()) return

        val current = _entries.value.toMutableList()
        val existingIndex = current.indexOfFirst {
            it.source.equals(source, ignoreCase = true) &&
                it.message.equals(cleanMessage, ignoreCase = true) &&
                nowEpochMs - it.lastSeenEpochMs <= DEDUPE_WINDOW_MS
        }

        if (existingIndex >= 0) {
            val old = current.removeAt(existingIndex)
            current.add(
                0,
                old.copy(
                    lastSeenEpochMs = nowEpochMs,
                    repeatCount = old.repeatCount + 1,
                    technicalVehicleAlert = old.technicalVehicleAlert || technicalVehicleAlert,
                ),
            )
        } else {
            current.add(
                0,
                WarningHistoryEntry(
                    id = nowEpochMs,
                    firstSeenEpochMs = nowEpochMs,
                    lastSeenEpochMs = nowEpochMs,
                    source = source,
                    message = cleanMessage,
                    technicalVehicleAlert = technicalVehicleAlert,
                ),
            )
        }

        val limited = current.sortedByDescending { it.lastSeenEpochMs }.take(MAX_ENTRIES)
        persist(limited)
        _entries.value = limited
    }

    fun todayEntries(nowEpochMs: Long = System.currentTimeMillis()): List<WarningHistoryEntry> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        return _entries.value.filter {
            Instant.ofEpochMilli(it.lastSeenEpochMs).atZone(zone).toLocalDate() == today
        }
    }

    fun todayTechnicalAlerts(nowEpochMs: Long = System.currentTimeMillis()): List<WarningHistoryEntry> =
        todayEntries(nowEpochMs).filter { it.technicalVehicleAlert }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
        _entries.value = emptyList()
    }

    private fun persist(values: List<WarningHistoryEntry>) {
        val array = JSONArray()
        values.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("first", item.firstSeenEpochMs)
                put("last", item.lastSeenEpochMs)
                put("source", item.source)
                put("message", item.message)
                put("technical", item.technicalVehicleAlert)
                put("repeat", item.repeatCount)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun load(): List<WarningHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        WarningHistoryEntry(
                            id = item.optLong("id", 0L),
                            firstSeenEpochMs = item.optLong("first", 0L),
                            lastSeenEpochMs = item.optLong("last", 0L),
                            source = item.optString("source", "SİSTEM"),
                            message = item.optString("message", ""),
                            technicalVehicleAlert = item.optBoolean("technical", false),
                            repeatCount = item.optInt("repeat", 1).coerceAtLeast(1),
                        ),
                    )
                }
            }.filter { it.message.isNotBlank() }
                .sortedByDescending { it.lastSeenEpochMs }
                .take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS = "warning_history_131"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 50
        private const val DEDUPE_WINDOW_MS = 5 * 60 * 1000L

        @Volatile private var instance: WarningHistoryRepository? = null

        fun getInstance(context: Context): WarningHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: WarningHistoryRepository(context).also { instance = it }
            }
    }
}
