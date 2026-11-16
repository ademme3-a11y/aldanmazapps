package com.aldanmaz.drivedashboard.nav

import kotlin.math.max
import kotlin.math.min

/** Rota uzerindeki anlik ilerleme. */
data class NavProgress(
    val distAlongM: Double,
    val offsetM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val nextStep: RouteStep?,
    val distToNextM: Double,
    val currentStreet: String,
    val arrived: Boolean,
    val offRoute: Boolean
)

/**
 * Konumu rotaya oturtur, siradaki manevrayi, kalan mesafeyi/sureyi ve rotadan sapmayi hesaplar.
 * Tek is parcacigindan (ViewModel) cagrilir.
 */
class NavEngine(val route: NavRoute) {
    private val stepBegin: DoubleArray = DoubleArray(route.steps.size) { i ->
        val idx = route.steps[i].beginShapeIndex.coerceIn(0, route.points.size - 1)
        route.cumDist[idx]
    }
    private val stepTimeBegin: DoubleArray = DoubleArray(route.steps.size).also { arr ->
        var t = 0.0
        for (i in route.steps.indices) {
            arr[i] = t
            t += route.steps[i].timeS
        }
    }
    private var hintSeg = 0
    private var consecutiveOff = 0

    fun update(p: GeoPoint): NavProgress {
        val snap = NavGeo.snapToRoute(route.points, route.cumDist, p, hintSeg)
        hintSeg = snap.segIndex
        val total = route.geometryLengthM
        val along = snap.distAlongM
        val remaining = max(0.0, total - along)

        if (snap.offsetM > OFF_ROUTE_M) consecutiveOff++ else consecutiveOff = 0
        val offRoute = consecutiveOff >= OFF_ROUTE_CONFIRMATIONS
        val arrived = remaining < ARRIVE_M && snap.offsetM < 3 * OFF_ROUTE_M

        // Siradaki manevra: gecilmemis ilk adim (adim 0 = kalkis, atlanir).
        var nextIdx = -1
        for (i in 1 until route.steps.size) {
            if (stepBegin[i] > along + PASSED_BUFFER_M) {
                nextIdx = i
                break
            }
        }
        if (nextIdx == -1 && route.steps.isNotEmpty()) nextIdx = route.steps.size - 1
        val next = route.steps.getOrNull(nextIdx)
        val distToNext = if (next == null) remaining else max(0.0, stepBegin[nextIdx] - along)
        val curIdx = max(0, nextIdx - 1)
        val street = route.steps.getOrNull(curIdx)?.streetName.orEmpty()

        val remainingS = if (route.steps.isEmpty()) {
            if (total > 0) route.durationS * remaining / total else 0.0
        } else {
            val curLen = (stepBegin.getOrElse(curIdx + 1) { total } - stepBegin[curIdx]).coerceAtLeast(1.0)
            val frac = ((along - stepBegin[curIdx]) / curLen).coerceIn(0.0, 1.0)
            val elapsed = stepTimeBegin[curIdx] + route.steps[curIdx].timeS * frac
            max(0.0, route.durationS - elapsed)
        }

        return NavProgress(
            distAlongM = along,
            offsetM = snap.offsetM,
            remainingM = remaining,
            remainingS = min(remainingS, route.durationS),
            nextStep = next,
            distToNextM = distToNext,
            currentStreet = street,
            arrived = arrived,
            offRoute = offRoute
        )
    }

    companion object {
        const val OFF_ROUTE_M = 40.0
        const val OFF_ROUTE_CONFIRMATIONS = 3
        const val ARRIVE_M = 25.0
        const val PASSED_BUFFER_M = 8.0
    }
}
