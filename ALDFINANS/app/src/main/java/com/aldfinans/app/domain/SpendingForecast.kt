package com.aldfinans.app.domain

import kotlin.math.max

data class SpendingForecast(
    val actual: Double,
    val estimatedMonthEnd: Double,
    val remaining: Double,
    val differenceVsAverage: Double
)

fun estimateMonthEnd(
    actualToDate: Double,
    elapsedDays: Int,
    daysInMonth: Int,
    historicalMonthlyAverage: Double? = null
): SpendingForecast {
    val safeElapsed = elapsedDays.coerceIn(1, daysInMonth.coerceAtLeast(1))
    val dailyPace = actualToDate / safeElapsed
    val paceProjection = dailyPace * daysInMonth
    val estimate = if (historicalMonthlyAverage != null && historicalMonthlyAverage > 0) {
        (paceProjection * 0.60) + (historicalMonthlyAverage * 0.40)
    } else {
        paceProjection
    }
    return SpendingForecast(
        actual = actualToDate,
        estimatedMonthEnd = max(0.0, estimate),
        remaining = max(0.0, estimate - actualToDate),
        differenceVsAverage = historicalMonthlyAverage?.let { estimate - it } ?: 0.0
    )
}
