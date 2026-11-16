package com.aldanmaz.drivedashboard.nav

import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Degistirilebilir rota saglayicisi. */
interface RoutingProvider {
    val name: String
    suspend fun route(from: GeoPoint, to: GeoPoint): NavRoute
}

/**
 * Valhalla /route (POST). Gercek yol agi geometrisi (polyline6) ve Turkce (tr-TR) donus talimatlari dondurur.
 * Genel ornekler (ör. valhalla1.openstreetmap.de) adil kullanim sinirlidir; baseUrl yapilandirilabilir.
 */
class ValhallaRouter(private val url: String, private val http: NavHttpClient) : RoutingProvider {
    override val name = "Valhalla"

    override suspend fun route(from: GeoPoint, to: GeoPoint): NavRoute {
        val body = buildString {
            append("{\"locations\":[{\"lat\":").append(num(from.lat)).append(",\"lon\":").append(num(from.lon))
            append("},{\"lat\":").append(num(to.lat)).append(",\"lon\":").append(num(to.lon)).append("}],")
            append("\"costing\":\"auto\",\"units\":\"kilometers\",\"language\":\"tr-TR\"}")
        }
        val text = try {
            http.postJson(url, body)
        } catch (e: NavException.Http) {
            // Valhalla "konuma yakin yol yok" gibi durumlarda 400 + error alani doner.
            if (e.code == 400) throw NavException.NoRoute(e.body.take(160)) else throw e
        }
        return parse(text)
    }

    private fun num(v: Double) = String.format(Locale.US, "%.6f", v)

    companion object {
        fun parse(json: String): NavRoute {
            val trip = parseJson(json).obj()?.o("trip") ?: throw NavException.NoRoute("trip yok")
            val legs = trip.a("legs") ?: throw NavException.NoRoute("leg yok")
            val points = ArrayList<GeoPoint>()
            val steps = ArrayList<RouteStep>()
            for (legEl in legs) {
                val leg = legEl.obj() ?: continue
                val shape = leg.str("shape") ?: throw NavException.Parse("shape yok")
                val offset = if (points.isEmpty()) 0 else points.size - 1
                val legPts = NavGeo.decodePolyline(shape, 6)
                if (points.isNotEmpty() && legPts.isNotEmpty()) points.addAll(legPts.drop(1)) else points.addAll(legPts)
                val maneuvers = leg.a("maneuvers") ?: continue
                for (mEl in maneuvers) {
                    val m = mEl.obj() ?: continue
                    val street = m.a("street_names")?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: m.a("begin_street_names")?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: ""
                    steps.add(
                        RouteStep(
                            kind = kindForType(m.int("type") ?: 0),
                            instruction = m.str("instruction").orEmpty(),
                            streetName = street,
                            lengthM = (m.dbl("length") ?: 0.0) * 1000.0,
                            timeS = m.dbl("time") ?: 0.0,
                            beginShapeIndex = (m.int("begin_shape_index") ?: 0) + offset
                        )
                    )
                }
            }
            if (points.size < 2) throw NavException.NoRoute("geometri boş")
            val summary = trip.o("summary")
            val dist = (summary?.dbl("length")?.times(1000.0)) ?: NavGeo.cumulativeDistances(points).last()
            val time = summary?.dbl("time") ?: steps.sumOf { it.timeS }
            return NavRoute(points, dist, time, steps, "Valhalla")
        }

        /** Valhalla manevra tipi (docs: 0..38) -> sadelestirilmis tur. Rampa/cikis/birlesme/donel kavsak dahil. */
        fun kindForType(t: Int): ManeuverKind = when (t) {
            1, 2, 3 -> ManeuverKind.DEPART
            4, 5, 6 -> ManeuverKind.ARRIVE
            9 -> ManeuverKind.SLIGHT_RIGHT
            10 -> ManeuverKind.RIGHT
            11 -> ManeuverKind.SHARP_RIGHT
            12, 13 -> ManeuverKind.UTURN
            14 -> ManeuverKind.SHARP_LEFT
            15 -> ManeuverKind.LEFT
            16 -> ManeuverKind.SLIGHT_LEFT
            18, 20 -> ManeuverKind.EXIT_RIGHT
            19, 21 -> ManeuverKind.EXIT_LEFT
            23 -> ManeuverKind.SLIGHT_RIGHT
            24 -> ManeuverKind.SLIGHT_LEFT
            25, 37, 38 -> ManeuverKind.MERGE
            26, 27 -> ManeuverKind.ROUNDABOUT
            else -> ManeuverKind.STRAIGHT
        }
    }
}

/**
 * OSRM /route/v1/driving yedegi (steps=true, polyline6). Genel demo sunucusu SLA'siz ve adil kullanim sinirlidir,
 * bu yuzden yalnizca birincil saglayici ulasilamazken devreye girer.
 */
class OsrmRouter(private val baseUrl: String, private val http: NavHttpClient) : RoutingProvider {
    override val name = "OSRM"

    override suspend fun route(from: GeoPoint, to: GeoPoint): NavRoute {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "${baseUrl.trimEnd('/')}/route/v1/driving/$coords?overview=full&geometries=polyline6&steps=true"
        return parse(http.get(url))
    }

    companion object {
        fun parse(json: String): NavRoute {
            val root = parseJson(json).obj() ?: throw NavException.Parse("kök yok")
            val route = root.a("routes")?.firstOrNull().obj() ?: throw NavException.NoRoute(root.str("code") ?: "rota yok")
            val points = NavGeo.decodePolyline(route.str("geometry") ?: throw NavException.Parse("geometry yok"), 6)
            if (points.size < 2) throw NavException.NoRoute("geometri boş")
            val steps = ArrayList<RouteStep>()
            var searchFrom = 0
            for (legEl in route.a("legs").orEmpty()) {
                for (sEl in legEl.obj()?.a("steps").orEmpty()) {
                    val s = sEl.obj() ?: continue
                    val man = s.o("maneuver") ?: continue
                    val loc = man.a("location")
                    val lon = loc?.getOrNull(0)?.dbl()
                    val lat = loc?.getOrNull(1)?.dbl()
                    val idx = if (lat != null && lon != null) nearestIndexFrom(points, GeoPoint(lat, lon), searchFrom) else searchFrom
                    searchFrom = idx
                    val type = man.str("type").orEmpty()
                    val modifier = man.str("modifier").orEmpty()
                    val name = s.str("name").orEmpty()
                    steps.add(
                        RouteStep(
                            kind = kindFor(type, modifier),
                            instruction = instructionTr(type, modifier, name, man.int("exit")),
                            streetName = name,
                            lengthM = s.dbl("distance") ?: 0.0,
                            timeS = s.dbl("duration") ?: 0.0,
                            beginShapeIndex = idx
                        )
                    )
                }
            }
            return NavRoute(points, route.dbl("distance") ?: NavGeo.cumulativeDistances(points).last(),
                route.dbl("duration") ?: steps.sumOf { it.timeS }, steps, "OSRM")
        }

        private fun nearestIndexFrom(points: List<GeoPoint>, p: GeoPoint, from: Int): Int {
            var best = from
            var bestD = Double.MAX_VALUE
            for (i in from until points.size) {
                val d = NavGeo.haversine(points[i], p)
                if (d < bestD) { bestD = d; best = i }
                if (bestD < 1.0) break
            }
            return best
        }

        fun kindFor(type: String, modifier: String): ManeuverKind = when {
            type == "depart" -> ManeuverKind.DEPART
            type == "arrive" -> ManeuverKind.ARRIVE
            type == "roundabout" || type == "rotary" || type == "roundabout turn" ||
                type == "exit roundabout" || type == "exit rotary" -> ManeuverKind.ROUNDABOUT
            type == "merge" -> ManeuverKind.MERGE
            type == "off ramp" -> if (modifier.contains("left")) ManeuverKind.EXIT_LEFT else ManeuverKind.EXIT_RIGHT
            else -> when (modifier) {
                "uturn" -> ManeuverKind.UTURN
                "sharp right" -> ManeuverKind.SHARP_RIGHT
                "right" -> ManeuverKind.RIGHT
                "slight right" -> ManeuverKind.SLIGHT_RIGHT
                "sharp left" -> ManeuverKind.SHARP_LEFT
                "left" -> ManeuverKind.LEFT
                "slight left" -> ManeuverKind.SLIGHT_LEFT
                else -> ManeuverKind.STRAIGHT
            }
        }

        fun instructionTr(type: String, modifier: String, name: String, exit: Int?): String {
            val onto = if (name.isBlank()) "" else " – $name"
            val dir = when (modifier) {
                "uturn" -> "U dönüşü yapın"
                "sharp right" -> "Keskin sağa dönün"
                "right" -> "Sağa dönün"
                "slight right" -> "Hafif sağa dönün"
                "sharp left" -> "Keskin sola dönün"
                "left" -> "Sola dönün"
                "slight left" -> "Hafif sola dönün"
                else -> "Düz devam edin"
            }
            return when (type) {
                "depart" -> "Yola çıkın$onto"
                "arrive" -> "Hedefe ulaştınız"
                "roundabout", "rotary", "roundabout turn" ->
                    if (exit != null) "Kavşaktan $exit. çıkışı alın$onto" else "Kavşağa girin$onto"
                "exit roundabout", "exit rotary" -> "Kavşaktan çıkın$onto"
                "merge" -> "Yola katılın$onto"
                "off ramp" -> (if (modifier.contains("left")) "Soldaki çıkışı alın" else "Sağdaki çıkışı alın") + onto
                "fork" -> (if (modifier.contains("left")) "Çatalda sola tutun" else "Çatalda sağa tutun") + onto
                "end of road", "turn", "new name", "continue", "on ramp" -> dir + onto
                else -> dir + onto
            }
        }
    }
}

/** Birincil rota saglayicisi hata verirse (ag/sunucu) yedege gecer; "rota yok" yanitinda gecmez. */
class RoutingService(private val primary: RoutingProvider, private val fallback: RoutingProvider?) {
    suspend fun route(from: GeoPoint, to: GeoPoint): NavRoute {
        try {
            return primary.route(from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NavException.NoRoute) {
            throw e
        } catch (e: NavException) {
            if (fallback == null) throw e
            return try {
                fallback.route(from, to)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: NavException.NoRoute) {
                throw e2
            } catch (e2: NavException) {
                throw e   // kullaniciya birincil hatanin nedenini goster
            }
        }
    }
}
