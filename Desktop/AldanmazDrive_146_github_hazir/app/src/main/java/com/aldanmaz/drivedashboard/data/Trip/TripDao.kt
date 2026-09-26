package com.aldanmaz.drivedashboard.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insertTrip(
        trip: TripEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM trips
        ORDER BY startedAtEpochMillis DESC
        """
    )
    fun observeTrips(): Flow<List<TripEntity>>

    @Query(
        """
        SELECT *
        FROM trips
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatestTrip(): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startedAtEpochMillis ASC")
    suspend fun getAllTrips(): List<TripEntity>

    @Query(
        """
        SELECT * FROM trips
        WHERE driverId = :driverId
          AND startedAtEpochMillis = :startedAtEpochMillis
        ORDER BY endedAtEpochMillis DESC, distanceKm DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getTripByLogicalKey(
        driverId: String,
        startedAtEpochMillis: Long
    ): TripEntity?

    @Query(
        """
        DELETE FROM trips
        WHERE driverId = :driverId
          AND startedAtEpochMillis = :startedAtEpochMillis
          AND id != :keepId
        """
    )
    suspend fun deleteDuplicateLogicalTrips(
        driverId: String,
        startedAtEpochMillis: Long,
        keepId: Long
    )

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Long)

    @Query("""
        SELECT * FROM trips
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
          AND (:vehicleMode IS NULL OR vehicleMode = :vehicleMode)
          AND (:driverId IS NULL OR driverId = :driverId)
        ORDER BY startedAtEpochMillis DESC
    """)
    suspend fun getTripsForPeriod(startEpochMillis: Long, endEpochMillis: Long, vehicleMode: String?, driverId: String?): List<TripEntity>

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query(
        """
        SELECT COALESCE(SUM(distanceKm), 0.0)
        FROM trips
        WHERE startedAtEpochMillis >= :dayStartEpochMillis
          AND startedAtEpochMillis < :nextDayStartEpochMillis
        """
    )
    suspend fun getTotalDistanceForDay(
        dayStartEpochMillis: Long,
        nextDayStartEpochMillis: Long
    ): Double

    @Query(
        """
        SELECT
            COALESCE(SUM(distanceKm), 0.0) AS totalDistanceKm,
            COALESCE(SUM(totalDurationSeconds), 0) AS totalDurationSeconds,
            COALESCE(SUM(movingDurationSeconds), 0) AS movingDurationSeconds,
            COALESCE(SUM(parkDurationSeconds), 0) AS parkDurationSeconds,
            COUNT(*) AS tripCount,
            CASE
                WHEN COALESCE(SUM(movingDurationSeconds), 0) > 0
                THEN COALESCE(SUM(distanceKm), 0.0) * 3600.0 /
                     SUM(movingDurationSeconds)
                ELSE 0.0
            END AS averageSpeedKmh,
            COALESCE(MAX(maxSpeedKmh), 0) AS maxSpeedKmh,
            COALESCE(SUM(estimatedFuelConsumedLiters), 0.0) AS estimatedFuelConsumedLiters,
            COALESCE(SUM(estimatedFuelCost), 0.0) AS estimatedFuelCost,
            COALESCE(SUM(speed0To30Seconds), 0) AS speed0To30Seconds,
            COALESCE(SUM(speed31To50Seconds), 0) AS speed31To50Seconds,
            COALESCE(SUM(speed51To70Seconds), 0) AS speed51To70Seconds,
            COALESCE(SUM(speed71To90Seconds), 0) AS speed71To90Seconds,
            COALESCE(SUM(speed91To120Seconds), 0) AS speed91To120Seconds,
            COALESCE(SUM(speedOver120Seconds), 0) AS speedOver120Seconds
        FROM trips
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
          AND (
              :vehicleMode IS NULL
              OR vehicleMode = :vehicleMode
          )
          AND (:driverId IS NULL OR driverId = :driverId)
        """
    )
    suspend fun getStatisticsForPeriod(
        startEpochMillis: Long,
        endEpochMillis: Long,
        vehicleMode: String? = null,
        driverId: String? = null
    ): TripStatisticsSummary
}
