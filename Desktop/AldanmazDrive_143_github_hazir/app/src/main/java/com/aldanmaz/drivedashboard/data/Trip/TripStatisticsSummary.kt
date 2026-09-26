package com.aldanmaz.drivedashboard.data.trip

data class TripStatisticsSummary(
    val totalDistanceKm: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val movingDurationSeconds: Long = 0L,
    val parkDurationSeconds: Long = 0L,
    val tripCount: Int = 0,
    val averageSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Int = 0,
    val estimatedFuelConsumedLiters: Double = 0.0,
    val estimatedFuelCost: Double = 0.0,
    val speed0To30Seconds: Long = 0L,
    val speed31To50Seconds: Long = 0L,
    val speed51To70Seconds: Long = 0L,
    val speed71To90Seconds: Long = 0L,
    val speed91To120Seconds: Long = 0L,
    val speedOver120Seconds: Long = 0L
)


