package com.aldanmaz.drivedashboard.data.trip

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips"
)
data class TripEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val startedAtEpochMillis: Long,

    val endedAtEpochMillis: Long,

    val distanceKm: Double,

    val totalDurationSeconds: Long,

    val movingDurationSeconds: Long,

    val parkDurationSeconds: Long,

    val averageSpeedKmh: Double,

    val maxSpeedKmh: Int,

    val vehicleMode: String,

    val vehicleId: String,

    val estimatedFuelConsumedLiters: Double,

    val estimatedFuelCost: Double = 0.0,

    val driverId: String = "1",
    val driverName: String = "Mehmet",
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val startAddress: String = "",
    val endAddress: String = "",

    val stopEventsJson: String = "[]",

    val speed0To30Seconds: Long = 0L,
    val speed31To50Seconds: Long = 0L,
    val speed51To70Seconds: Long = 0L,
    val speed71To90Seconds: Long = 0L,
    val speed91To120Seconds: Long = 0L,
    val speedOver120Seconds: Long = 0L
)
