package com.aldanmaz.drivedashboard.nav

/** Gidilen yolun izi (bellekte); 3B ekran kapatilip acilsa da, yeni hedef secilene kadar korunur. */
object NavTrailStore {
    const val MAX_POINTS = 3000
    const val MIN_STEP_M = 10.0
    val points = ArrayList<GeoPoint>()

    /** Nokta eklendiyse true. */
    @Synchronized
    fun add(p: GeoPoint): Boolean {
        if (points.isNotEmpty() && NavGeo.haversine(points.last(), p) < MIN_STEP_M) return false
        points.add(p)
        if (points.size > MAX_POINTS) points.removeAt(0)
        return true
    }

    @Synchronized
    fun clear() { points.clear() }

    @Synchronized
    fun snapshot(): List<GeoPoint> = ArrayList(points)
}
