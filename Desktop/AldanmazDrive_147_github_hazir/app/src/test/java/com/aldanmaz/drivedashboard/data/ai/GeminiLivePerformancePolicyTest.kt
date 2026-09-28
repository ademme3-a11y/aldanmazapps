package com.aldanmaz.drivedashboard.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLivePerformancePolicyTest {

    @Test
    fun `first connected obd sample is persisted`() {
        assertTrue(
            GeminiLivePerformancePolicy.shouldPersistVehicleHealth(
                obdConnected = true,
                lastPersistAtElapsed = 0L,
                nowElapsed = 1_000L,
            )
        )
    }

    @Test
    fun `rapid samples are skipped until thirty seconds pass`() {
        assertFalse(
            GeminiLivePerformancePolicy.shouldPersistVehicleHealth(
                obdConnected = true,
                lastPersistAtElapsed = 10_000L,
                nowElapsed = 39_999L,
            )
        )
        assertTrue(
            GeminiLivePerformancePolicy.shouldPersistVehicleHealth(
                obdConnected = true,
                lastPersistAtElapsed = 10_000L,
                nowElapsed = 40_000L,
            )
        )
    }

    @Test
    fun `altitude milestone is emitted only while ascending`() {
        assertEquals(
            1_200,
            GeminiLivePerformancePolicy.crossedAltitudeMilestone(
                previousAltitudeMeters = 1_180,
                currentAltitudeMeters = 1_205,
                alreadyAnnounced = emptySet(),
            )
        )
        assertNull(
            GeminiLivePerformancePolicy.crossedAltitudeMilestone(
                previousAltitudeMeters = 1_205,
                currentAltitudeMeters = 1_180,
                alreadyAnnounced = emptySet(),
            )
        )
    }

    @Test
    fun `already announced altitude milestone is not repeated`() {
        assertNull(
            GeminiLivePerformancePolicy.crossedAltitudeMilestone(
                previousAltitudeMeters = 1_190,
                currentAltitudeMeters = 1_210,
                alreadyAnnounced = setOf(1_200),
            )
        )
    }

    @Test
    fun `finance window includes requested boundaries`() {
        assertTrue(GeminiLivePerformancePolicy.isDailyFinanceWindow(11, 0))
        assertTrue(GeminiLivePerformancePolicy.isDailyFinanceWindow(17, 30))
        assertFalse(GeminiLivePerformancePolicy.isDailyFinanceWindow(10, 59))
        assertFalse(GeminiLivePerformancePolicy.isDailyFinanceWindow(17, 31))
    }
}
