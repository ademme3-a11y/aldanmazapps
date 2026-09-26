package com.aldanmaz.drivedashboard.ui.screen.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveDashboard106PolicyTest {

    @Test
    fun fuelSummary_keepsPercentageAndRangeSeparated() {
        assertEquals(
            "%18 • 164 KM",
            formatFuelSummaryText(
                percentage = 18.0,
                estimatedFuelRangeKm = 164.0
            )
        )
    }
}
