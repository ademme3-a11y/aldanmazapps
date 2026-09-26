package com.aldanmaz.drivedashboard.data.ai

/** Gemini Live açıkken ana iş parçacığı ve disk yükünü sınırlayan saf kurallar. */
internal object GeminiLivePerformancePolicy {
    // Araç ekranında gereksiz SharedPreferences/disk trafiği ses akışını bölmesin.
    const val VEHICLE_HEALTH_PERSIST_INTERVAL_MS = 30_000L
    const val ALTITUDE_STABILITY_MS = 5_000L
    const val ALTITUDE_HYSTERESIS_METERS = 35

    val ALTITUDE_MILESTONES_METERS = listOf(
        500,
        1_000,
        1_200,
        1_400,
        1_600,
        1_800,
        2_000,
        2_200,
        2_400,
        2_600,
    )

    fun shouldPersistVehicleHealth(
        obdConnected: Boolean,
        lastPersistAtElapsed: Long,
        nowElapsed: Long,
    ): Boolean {
        if (!obdConnected) return false
        if (lastPersistAtElapsed <= 0L) return true
        return nowElapsed - lastPersistAtElapsed >= VEHICLE_HEALTH_PERSIST_INTERVAL_MS
    }

    fun crossedAltitudeMilestone(
        previousAltitudeMeters: Int,
        currentAltitudeMeters: Int,
        alreadyAnnounced: Set<Int>,
    ): Int? {
        if (currentAltitudeMeters <= previousAltitudeMeters) return null
        return ALTITUDE_MILESTONES_METERS.lastOrNull { milestone ->
            milestone !in alreadyAnnounced &&
                previousAltitudeMeters < milestone &&
                currentAltitudeMeters >= milestone
        }
    }

    fun isDailyFinanceWindow(hour: Int, minute: Int): Boolean {
        val minuteOfDay = hour * 60 + minute
        return minuteOfDay in (11 * 60)..(17 * 60 + 30)
    }
}
