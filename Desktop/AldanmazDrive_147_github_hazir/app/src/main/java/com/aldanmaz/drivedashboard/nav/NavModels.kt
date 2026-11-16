package com.aldanmaz.drivedashboard.nav

/** Basit WGS84 koordinati. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Arama sonucu: koordinatlari geocoder'dan gelir, uydurulmaz. */
data class PlaceResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val kind: String = ""
)

/** Manevra ikonu icin sadelestirilmis tur; angleDeg okun yonunu belirtir (0 = duz, + = saga). */
enum class ManeuverKind(val angleDeg: Float) {
    DEPART(0f), STRAIGHT(0f),
    SLIGHT_RIGHT(45f), RIGHT(90f), SHARP_RIGHT(135f),
    UTURN(180f),
    SHARP_LEFT(-135f), LEFT(-90f), SLIGHT_LEFT(-45f),
    EXIT_RIGHT(45f), EXIT_LEFT(-45f),
    MERGE(0f), ROUNDABOUT(0f), ARRIVE(0f)
}

/**
 * Tek bir donus talimati. [beginShapeIndex] manevranin yapilacagi rota noktasidir.
 */
data class RouteStep(
    val kind: ManeuverKind,
    val instruction: String,
    val streetName: String,
    val lengthM: Double,
    val timeS: Double,
    val beginShapeIndex: Int
)

/** Gercek yol agindan donen rota. points bos olamaz (en az 2 nokta). */
class NavRoute(
    val points: List<GeoPoint>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
    val provider: String
) {
    /** Her rota noktasina kadar birikimli mesafe (metre). */
    val cumDist: DoubleArray = NavGeo.cumulativeDistances(points)
    val geometryLengthM: Double get() = if (cumDist.isEmpty()) 0.0 else cumDist.last()
}

/** Tum ag/arama/rota hatalari kullaniciya Turkce mesaj verecek sekilde tiplenmistir. */
sealed class NavException(message: String) : Exception(message) {
    class Offline : NavException("offline")
    class Timeout : NavException("timeout")
    class Http(val code: Int, val body: String) : NavException("http $code")
    class Network(val detail: String?) : NavException("network ${detail ?: ""}")
    class Parse(val detail: String) : NavException("parse $detail")
    class NoRoute(val detail: String) : NavException("noroute $detail")

    fun userMessage(): String = when (this) {
        is Offline -> "İnternet bağlantısı yok veya sunucuya ulaşılamıyor."
        is Timeout -> "Sunucu zamanında yanıt vermedi."
        is Http -> when (code) {
            429 -> "Servis kullanım sınırına ulaştı (HTTP 429). Biraz sonra tekrar deneyin."
            in 500..599 -> "Servis geçici olarak çalışmıyor (HTTP $code)."
            else -> "Servis isteği reddetti (HTTP $code)."
        }
        is Network -> "Ağ hatası: ${detail ?: "bilinmeyen"}"
        is Parse -> "Servis yanıtı okunamadı."
        is NoRoute -> "Bu konuma yol ağı üzerinden rota bulunamadı."
    }
}
