package com.aldanmaz.drivedashboard.data.trip

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TripRepository(
    private val tripDao: TripDao
) {

    fun observeTrips(): Flow<List<TripEntity>> =
        tripDao.observeTrips()

    suspend fun saveTrip(
        trip: TripEntity
    ): Long {
        // Sürüşün kimliği başlangıç zamanıdır; sonradan aktif sürücü değişse bile
        // aynı sürüş başka bir sürücüye ikinci kez yazılmamalı.
        val existing = tripDao.getTripByStartTime(trip.startedAtEpochMillis)
        val normalized = if (existing != null) {
            trip.copy(
                id = existing.id,
                driverId = existing.driverId,
                driverName = existing.driverName
            )
        } else trip

        val id = tripDao.insertTrip(normalized)
        tripDao.deleteDuplicateTripsByStartTime(
            startedAtEpochMillis = trip.startedAtEpochMillis,
            keepId = id
        )
        return id
    }

    suspend fun getTripByLogicalKey(
        driverId: String,
        startedAtEpochMillis: Long
    ): TripEntity? =
        tripDao.getTripByLogicalKey(driverId, startedAtEpochMillis)

    suspend fun updateDriverForTrips(
        tripIds: List<Long>,
        driverId: String,
        driverName: String
    ) {
        if (tripIds.isEmpty()) return
        tripDao.updateDriverForTrips(tripIds, driverId, driverName)
    }

    /**
     * Aynı başlangıç zamanındaki kayıtlar tek fiziksel sürüştür. Önce oluşturulan
     * kayıt sürücüsünü koru; aynı sürücüdeki kopyalarda en eksiksiz kayıt kalsın.
     */
    suspend fun removeDuplicateTrips() {
        val trips = tripDao.getAllTrips()
        trips.groupBy { it.startedAtEpochMillis }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val hasDifferentDrivers = group.map { it.driverId }.distinct().size > 1
                val keep = if (hasDifferentDrivers) {
                    group.minByOrNull { it.id }
                } else {
                    group.maxWithOrNull(
                        compareBy<TripEntity> { it.endedAtEpochMillis }
                            .thenBy { it.distanceKm }
                            .thenBy { it.totalDurationSeconds }
                            .thenBy { it.id }
                    )
                } ?: return@forEach
                group.asSequence()
                    .filter { it.id != keep.id }
                    .forEach { duplicate -> tripDao.deleteTripById(duplicate.id) }
            }
    }

    suspend fun getLatestTrip(): TripEntity? =
        tripDao.getLatestTrip()

    suspend fun getAllTimeTotalDistanceKm(): Double =
        tripDao.getAllTrips().sumOf { it.distanceKm }.coerceAtLeast(0.0)

    suspend fun getTodayTotalDistanceKm(
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Double {

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis

            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayStartEpochMillis =
            calendar.timeInMillis

        calendar.add(
            Calendar.DAY_OF_MONTH,
            1
        )

        val nextDayStartEpochMillis =
            calendar.timeInMillis

        return tripDao.getTotalDistanceForDay(
            dayStartEpochMillis = dayStartEpochMillis,
            nextDayStartEpochMillis = nextDayStartEpochMillis
        )
    }

    suspend fun getStatisticsForPeriod(
        startEpochMillis: Long,
        endEpochMillis: Long,
        vehicleMode: String? = null,
        driverId: String? = null
    ): TripStatisticsSummary =
        tripDao.getStatisticsForPeriod(
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            vehicleMode = vehicleMode,
            driverId = driverId
        )


    suspend fun getCurrentPeriodStatistics(
        period: TripStatisticsPeriod,
        vehicleMode: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
        driverId: String? = null
    ): TripStatisticsSummary {

        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (period) {
            TripStatisticsPeriod.DAY -> Unit

            TripStatisticsPeriod.WEEK -> {
                startCalendar.firstDayOfWeek = Calendar.MONDAY
                startCalendar.set(
                    Calendar.DAY_OF_WEEK,
                    Calendar.MONDAY
                )
            }

            TripStatisticsPeriod.MONTH -> {
                startCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
                )
            }

            TripStatisticsPeriod.YEAR -> {
                startCalendar.set(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }
        }

        val endCalendar =
            startCalendar.clone() as Calendar

        when (period) {
            TripStatisticsPeriod.DAY ->
                endCalendar.add(
                    Calendar.DAY_OF_MONTH,
                    1
                )

            TripStatisticsPeriod.WEEK ->
                endCalendar.add(
                    Calendar.WEEK_OF_YEAR,
                    1
                )

            TripStatisticsPeriod.MONTH ->
                endCalendar.add(
                    Calendar.MONTH,
                    1
                )

            TripStatisticsPeriod.YEAR ->
                endCalendar.add(
                    Calendar.YEAR,
                    1
                )
        }

        return getStatisticsForPeriod(
            startEpochMillis =
                startCalendar.timeInMillis,
            endEpochMillis =
                endCalendar.timeInMillis,
            vehicleMode = vehicleMode,
            driverId = driverId
        )
    }

    suspend fun getCurrentPeriodTrips(
        period: TripStatisticsPeriod,
        vehicleMode: String? = null,
        driverId: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): List<TripEntity> {
        val start = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            when (period) {
                TripStatisticsPeriod.DAY -> Unit
                TripStatisticsPeriod.WEEK -> { firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                TripStatisticsPeriod.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                TripStatisticsPeriod.YEAR -> set(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val end = (start.clone() as Calendar).apply {
            when (period) {
                TripStatisticsPeriod.DAY -> add(Calendar.DAY_OF_MONTH, 1)
                TripStatisticsPeriod.WEEK -> add(Calendar.WEEK_OF_YEAR, 1)
                TripStatisticsPeriod.MONTH -> add(Calendar.MONTH, 1)
                TripStatisticsPeriod.YEAR -> add(Calendar.YEAR, 1)
            }
        }
        return tripDao.getTripsForPeriod(start.timeInMillis, end.timeInMillis, vehicleMode, driverId)
    }

    suspend fun clearAllTrips() {
        tripDao.deleteAllTrips()
    }


}
