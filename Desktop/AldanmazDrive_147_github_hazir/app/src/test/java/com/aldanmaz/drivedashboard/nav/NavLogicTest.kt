package com.aldanmaz.drivedashboard.nav

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** Saf mantik (GPS filtresi, rota ilerlemesi, ayristiricilar, arama/rota yedekleme) testleri; ag kullanmaz. */
class NavLogicTest {

    // ---------- yardimcilar ----------
    private fun encode(points: List<GeoPoint>, precision: Int = 6): String {
        val f = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var pLat = 0L
        var pLon = 0L
        fun put(v: Long) {
            var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
            sb.append((x.toInt() + 63).toChar())
        }
        for (p in points) {
            val la = Math.round(p.lat * f); val lo = Math.round(p.lon * f)
            put(la - pLat); put(lo - pLon); pLat = la; pLon = lo
        }
        return sb.toString()
    }

    /** Kuzeye dogru 10 parcalik duz rota (yaklasik 5 km) + 3 adim. */
    private fun straightRoute(): NavRoute {
        val pts = (0..10).map { GeoPoint(36.90 + it * 0.0045, 30.70) }
        val steps = listOf(
            RouteStep(ManeuverKind.DEPART, "Yola çıkın", "A Cd.", 0.0, 0.0, 0),
            RouteStep(ManeuverKind.RIGHT, "Sağa dönün", "B Sk.", 0.0, 0.0, 5),
            RouteStep(ManeuverKind.ARRIVE, "Hedefe ulaştınız", "", 0.0, 0.0, 10)
        )
        val cum = NavGeo.cumulativeDistances(pts)
        val s2 = steps.mapIndexed { i, s ->
            val begin = cum[s.beginShapeIndex]
            val end = if (i + 1 < steps.size) cum[steps[i + 1].beginShapeIndex] else begin
            s.copy(lengthM = end - begin, timeS = (end - begin) / 15.0)
        }
        return NavRoute(pts, cum.last(), s2.sumOf { it.timeS }, s2, "test")
    }

    private class FakeHttp(val onGet: (String) -> String = { error("get beklenmiyordu") },
                           val onPost: (String, String) -> String = { _, _ -> error("post beklenmiyordu") }) : NavHttpClient {
        val urls = ArrayList<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String { urls.add(url); return onGet(url) }
        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String { urls.add(url + " " + body); return onPost(url, body) }
    }

    // ---------- cografya ----------
    @Test fun polylineDecodesGoogleReferenceExample() {
        val pts = NavGeo.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-6); assertEquals(-120.2, pts[0].lon, 1e-6)
        assertEquals(40.7, pts[1].lat, 1e-6); assertEquals(-120.95, pts[1].lon, 1e-6)
        assertEquals(43.252, pts[2].lat, 1e-6); assertEquals(-126.453, pts[2].lon, 1e-6)
    }

    @Test fun polyline6RoundTrip() {
        val src = listOf(GeoPoint(36.896912, 30.713323), GeoPoint(36.902001, 30.70811), GeoPoint(36.91, 30.7))
        val back = NavGeo.decodePolyline(encode(src), 6)
        assertEquals(src.size, back.size)
        src.indices.forEach { assertEquals(src[it].lat, back[it].lat, 1e-6); assertEquals(src[it].lon, back[it].lon, 1e-6) }
    }

    @Test fun haversineAntalyaIstanbul() {
        val d = NavGeo.haversine(GeoPoint(36.8969, 30.7133), GeoPoint(41.0082, 28.9784)) / 1000
        assertTrue("d=$d", d in 475.0..495.0)
    }

    @Test fun bearingAndAngles() {
        assertEquals(0.0, NavGeo.bearing(GeoPoint(36.0, 30.0), GeoPoint(37.0, 30.0)), 0.01)
        assertEquals(90.0, NavGeo.bearing(GeoPoint(36.0, 30.0), GeoPoint(36.0, 31.0)), 0.5)
        assertEquals(20.0, NavGeo.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, NavGeo.angleDiff(10.0, 350.0), 1e-9)
        assertEquals(0.0, NavGeo.lerpAngle(350.0, 10.0, 0.5), 1e-9)
    }

    // ---------- rota ilerlemesi ----------
    @Test fun engineTracksProgressNextManeuverAndArrival() {
        val r = straightRoute()
        val e = NavEngine(r)
        val start = e.update(r.points[0])
        assertEquals(ManeuverKind.RIGHT, start.nextStep?.kind)
        assertEquals(r.cumDist[5], start.distToNextM, 2.0)
        assertEquals(r.cumDist.last(), start.remainingM, 2.0)
        assertFalse(start.offRoute); assertFalse(start.arrived)
        assertEquals("A Cd.", start.currentStreet)

        val mid = e.update(GeoPoint(36.90 + 7 * 0.0045, 30.70))   // donusu gecti
        assertEquals(ManeuverKind.ARRIVE, mid.nextStep?.kind)
        assertEquals("B Sk.", mid.currentStreet)
        assertTrue(mid.remainingS < r.durationS * 0.5)

        val end = e.update(r.points[10])
        assertTrue(end.arrived)
    }

    @Test fun engineFlagsOffRouteOnlyAfterThreeConsecutiveFixes() {
        val r = straightRoute()
        val e = NavEngine(r)
        val off = GeoPoint(36.92, 30.7015)   // ~135 m dogu
        assertFalse(e.update(off).offRoute)
        assertFalse(e.update(off).offRoute)
        assertTrue(e.update(off).offRoute)
        assertFalse(e.update(GeoPoint(36.92, 30.70)).offRoute)   // geri dondu
    }

    // ---------- GPS filtresi ----------
    @Test fun filterRejectsInvalidAndZeroFixes() {
        val f = GpsFilter()
        assertNull(f.accept(Double.NaN, 30.0, 10, null, 0))
        assertNull(f.accept(0.0, 0.0, 10, null, 0))
        assertNull(f.accept(95.0, 30.0, 10, null, 0))
    }

    @Test fun filterRejectsImpossibleJumpButAcceptsAfterFiveInARow() {
        val f = GpsFilter()
        assertNotNull(f.accept(36.9, 30.7, 50, null, 0))
        repeat(5) { assertNull("jump $it", f.accept(37.9, 31.7, 50, null, 1000L + it * 1000L)) }
        assertNotNull(f.accept(37.9, 31.7, 50, null, 7000L))   // gercek isinlanma/GPS kaybi sonrasi kabul
    }

    @Test fun filterHoldsPositionWhenStoppedAndJittering() {
        val f = GpsFilter()
        val a = f.accept(36.9, 30.7, 0, 123f, 0)!!
        val b = f.accept(36.90002, 30.70002, 0, 123f, 1000)!!
        assertEquals(a.lat, b.lat, 0.0); assertEquals(a.lon, b.lon, 0.0)
        assertEquals(123.0, b.headingDeg, 1e-9)                  // yon yoksa pusula
    }

    @Test fun filterDerivesCourseFromMovementAndKeepsItWhenStopped() {
        val f = GpsFilter()
        var t = 0L
        var lon = 30.70
        var last: FilteredFix? = null
        repeat(8) {
            last = f.accept(36.9, lon, 40, 10f, t); lon += 0.0002; t += 1000   // dogu, ~18 m/sn adim
        }
        assertTrue("heading=${last!!.headingDeg}", abs(NavGeo.angleDiff(last!!.headingDeg, 90.0)) < 5.0)
        val stopped = f.accept(36.9, lon, 0, 10f, t + 1000)!!
        assertTrue(abs(NavGeo.angleDiff(stopped.headingDeg, 90.0)) < 5.0)   // durunca son yon korunur
    }

    @Test fun poseSmootherConvergesWithoutOvershootAndWrapsHeading() {
        val s = PoseSmoother()
        s.step(0.016, 36.0, 30.0, 350.0, 17.0)
        repeat(300) { s.step(0.016, 36.001, 30.001, 10.0, 16.0) }
        assertEquals(36.001, s.lat, 1e-6); assertEquals(16.0, s.zoom, 0.02)
        assertTrue(abs(NavGeo.angleDiff(s.heading, 10.0)) < 0.5)
        assertTrue(PoseSmoother.zoomForSpeed(0, null) > PoseSmoother.zoomForSpeed(100, null))
    }

    // ---------- 3B arac ----------
    @Test fun vehicleOrientationFollowsHeading() {
        val north = VehicleModel.localToGeo(36.9, 30.7, 0.0, 0.0, 2.0, 1.0)
        assertTrue(north.lat > 36.9); assertEquals(30.7, north.lon, 1e-9)
        val east = VehicleModel.localToGeo(36.9, 30.7, 90.0, 0.0, 2.0, 1.0)
        assertTrue(east.lon > 30.7); assertEquals(36.9, east.lat, 1e-7)
        val right = VehicleModel.localToGeo(36.9, 30.7, 0.0, 1.0, 0.0, 1.0)
        assertTrue(right.lon > 30.7)
    }

    @Test fun vehicleGeoJsonIsWellFormedAndScaled() {
        val j = VehicleModel.featureCollection(36.9, 30.7, 45.0, 3.0)
        assertEquals(VehicleModel.partCount, Regex("\"type\":\"Feature\"").findAll(j).count())
        assertTrue(j.contains("#F7F8FA"))
        val obj = parseJson(j).obj()!!
        assertEquals(VehicleModel.partCount, obj.a("features")!!.size)
        val p = obj.a("features")!![2].obj()!!.o("properties")!!
        assertEquals(0.98 * 3.0, p.dbl("h")!!, 0.01)   // govde tepe yuksekligi olceklenmis
    }

    // ---------- ayristiricilar ----------
    @Test fun photonParsesTurkishNamesAndSubtitle() {
        val json = """{"features":[
          {"geometry":{"coordinates":[30.7133,36.8969]},"properties":{"name":"Antalya","state":"Antalya","country":"Türkiye","osm_key":"place","osm_value":"city","osm_id":1,"osm_type":"R"}},
          {"geometry":{"coordinates":[30.8,36.92]},"properties":{"name":"Ahatlı","district":"Kepez","city":"Antalya","country":"Türkiye","osm_key":"place","osm_value":"suburb"}}]}"""
        val r = PhotonGeocoder.parse(json)
        assertEquals(2, r.size)
        assertEquals("Antalya", r[0].name); assertEquals(36.8969, r[0].lat, 1e-9); assertEquals(30.7133, r[0].lon, 1e-9)
        assertEquals("Ahatlı", r[1].name); assertTrue(r[1].subtitle.contains("Kepez"))
    }

    @Test fun nominatimParsesStringCoordinates() {
        val json = """[{"lat":"36.9","lon":"30.8","name":"Konyaaltı Sahili","display_name":"Konyaaltı Sahili, Konyaaltı, Antalya, Türkiye","osm_type":"way","osm_id":5,"category":"natural","type":"beach"}]"""
        val r = NominatimGeocoder.parse(json)
        assertEquals(1, r.size); assertEquals("Konyaaltı Sahili", r[0].name); assertEquals(30.8, r[0].lon, 1e-9)
        assertEquals("Konyaaltı, Antalya, Türkiye", r[0].subtitle)
    }

    @Test fun valhallaParsesGeometryManeuversAndUnits() {
        val pts = listOf(GeoPoint(36.90, 30.70), GeoPoint(36.91, 30.70), GeoPoint(36.91, 30.71))
        val json = """{"trip":{"summary":{"length":2.1,"time":180.0},"legs":[{"shape":"${encode(pts).replace("\\", "\\\\")}",
          "maneuvers":[{"type":1,"instruction":"Kuzeye gidin.","street_names":["Atatürk Bulvarı"],"length":1.1,"time":90.0,"begin_shape_index":0},
                       {"type":10,"instruction":"Sağa dönün.","length":1.0,"time":90.0,"begin_shape_index":1},
                       {"type":26,"instruction":"Kavşağa girin.","begin_shape_index":2,"roundabout_exit_count":2},
                       {"type":20,"instruction":"Çıkış.","begin_shape_index":2},
                       {"type":4,"instruction":"Hedefe ulaştınız.","begin_shape_index":2}]}]}}"""
        val r = ValhallaRouter.parse(json)
        assertEquals(3, r.points.size); assertEquals(2100.0, r.distanceM, 1e-6); assertEquals(180.0, r.durationS, 1e-6)
        assertEquals(ManeuverKind.DEPART, r.steps[0].kind); assertEquals("Atatürk Bulvarı", r.steps[0].streetName)
        assertEquals(1100.0, r.steps[0].lengthM, 1e-6)
        assertEquals(ManeuverKind.RIGHT, r.steps[1].kind)
        assertEquals(ManeuverKind.ROUNDABOUT, r.steps[2].kind)
        assertEquals(ManeuverKind.EXIT_RIGHT, r.steps[3].kind)     // otoyol cikisi
        assertEquals(ManeuverKind.ARRIVE, r.steps[4].kind)
    }

    @Test fun osrmParsesStepsAndBuildsTurkishInstructions() {
        val pts = listOf(GeoPoint(36.90, 30.70), GeoPoint(36.91, 30.70), GeoPoint(36.91, 30.71))
        val json = """{"code":"Ok","routes":[{"distance":2100.0,"duration":200.0,"geometry":"${encode(pts).replace("\\", "\\\\")}",
          "legs":[{"steps":[
            {"name":"X Cd.","distance":1100.0,"duration":100.0,"maneuver":{"type":"depart","location":[30.70,36.90]}},
            {"name":"Y Sk.","distance":1000.0,"duration":100.0,"maneuver":{"type":"turn","modifier":"right","location":[30.70,36.91]}},
            {"name":"","distance":0.0,"duration":0.0,"maneuver":{"type":"arrive","location":[30.71,36.91]}}]}]}]}"""
        val r = OsrmRouter.parse(json)
        assertEquals(3, r.steps.size)
        assertEquals(1, r.steps[1].beginShapeIndex); assertEquals(2, r.steps[2].beginShapeIndex)
        assertEquals(ManeuverKind.RIGHT, r.steps[1].kind); assertEquals("Sağa dönün – Y Sk.", r.steps[1].instruction)
    }

    @Test fun valhallaEmptyOrErrorBodyBecomesTypedError() {
        try { ValhallaRouter.parse("{\"error\":\"x\"}"); fail() } catch (e: NavException.NoRoute) { }
        try { parseJson("<html>"); fail() } catch (e: NavException.Parse) { }
    }

    // ---------- arama servisi ----------
    private val photonOneHit = """{"features":[{"geometry":{"coordinates":[30.8,36.92]},"properties":{"name":"Ahatlı","city":"Antalya"}}]}"""

    @Test fun searchEncodesTurkishCharactersAndBiasesToGps() = runBlocking {
        val http = FakeHttp(onGet = { photonOneHit })
        val svc = GeocodingService(PhotonGeocoder("https://p.example/api/", http), null)
        val res = svc.search("Ahatlı", GeoPoint(36.9, 30.7), explicit = true)
        assertEquals(1, res.size)
        assertTrue(http.urls[0], http.urls[0].contains("q=Ahatl%C4%B1"))
        assertTrue(http.urls[0].contains("lat=36.90000") && http.urls[0].contains("lon=30.70000"))
        assertFalse(http.urls[0].contains("lang="))
    }

    @Test fun searchRetriesFoldedTurkishThenNominatimOnlyWhenExplicit() = runBlocking {
        val photon = FakeHttp(onGet = { """{"features":[]}""" })
        val nomi = FakeHttp(onGet = { """[{"lat":"36.9","lon":"30.8","display_name":"Antalya Havalimanı, Muratpaşa"}]""" })
        val svc = GeocodingService(PhotonGeocoder("https://p/api/", photon), NominatimGeocoder("https://n/search", nomi))
        assertTrue(svc.search("Antalya Havalimanı", null, explicit = false).isEmpty())   // otomatik: Nominatim kullanilmaz
        assertEquals(0, nomi.urls.size)
        assertTrue(photon.urls.any { it.contains("Havalimani") })                       // ı -> i yedegi denendi
        val res = svc.search("Antalya Havalimanı", null, explicit = true)
        assertEquals(1, res.size); assertEquals(1, nomi.urls.size)
    }

    @Test fun searchDistinguishesEmptyFromFailure() = runBlocking {
        val failing = GeocodingService(PhotonGeocoder("https://p/api/", FakeHttp(onGet = { throw NavException.Offline() })), null)
        try { failing.search("Kepez", null, true); fail() } catch (e: NavException.Offline) { }
        val empty = GeocodingService(PhotonGeocoder("https://p/api/", FakeHttp(onGet = { """{"features":[]}""" })), null)
        assertTrue(empty.search("zzzzqq", null, true).isEmpty())
    }

    @Test fun searchUsesSecondaryWhenPrimaryFailsOnExplicit() = runBlocking {
        val svc = GeocodingService(
            PhotonGeocoder("https://p/api/", FakeHttp(onGet = { throw NavException.Timeout() })),
            NominatimGeocoder("https://n/search", FakeHttp(onGet = { """[{"lat":"36.9","lon":"30.8","display_name":"Kepez, Antalya"}]""" }))
        )
        assertEquals("Kepez", svc.search("Kepez", null, true).single().name)
    }

    @Test fun searchCachesSuccessfulResults() = runBlocking {
        val http = FakeHttp(onGet = { photonOneHit })
        val svc = GeocodingService(PhotonGeocoder("https://p/api/", http), null)
        svc.search("Ahatlı", null, true); svc.search("ahatlı", null, true)
        assertEquals(1, http.urls.size)
    }

    // ---------- rota servisi ----------
    private val okRoute = NavRoute(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), 1100.0, 100.0, emptyList(), "ok")

    private fun provider(n: String, f: () -> NavRoute) = object : RoutingProvider {
        override val name = n
        override suspend fun route(from: GeoPoint, to: GeoPoint) = f()
    }

    @Test fun routingFallsBackOnNetworkErrorsButNotOnNoRoute() = runBlocking {
        val fb = RoutingService(provider("a") { throw NavException.Timeout() }, provider("b") { okRoute })
        assertEquals("ok", fb.route(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)).provider)
        val noFb = RoutingService(provider("a") { throw NavException.NoRoute("x") }, provider("b") { okRoute })
        try { noFb.route(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)); fail() } catch (e: NavException.NoRoute) { }
        val both = RoutingService(provider("a") { throw NavException.Offline() }, provider("b") { throw NavException.Timeout() })
        try { both.route(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)); fail() } catch (e: NavException.Offline) { }
    }

    @Test fun valhallaRequestIsPostedWithTurkishAndRealCoordinates() = runBlocking {
        val pts = listOf(GeoPoint(36.90, 30.70), GeoPoint(36.91, 30.70))
        val http = FakeHttp(onPost = { _, _ -> """{"trip":{"summary":{"length":1.1,"time":90.0},"legs":[{"shape":"${encode(pts).replace("\\", "\\\\")}","maneuvers":[]}]}}""" })
        val r = ValhallaRouter("https://v/route", http).route(GeoPoint(36.90, 30.70), GeoPoint(36.91, 30.70))
        assertEquals(2, r.points.size)
        assertTrue(http.urls[0], http.urls[0].contains("\"language\":\"tr-TR\"") && http.urls[0].contains("\"lat\":36.900000"))
    }

    @Test fun userMessagesAreMeaningful() {
        assertTrue(NavException.Offline().userMessage().contains("İnternet"))
        assertTrue(NavException.Http(429, "").userMessage().contains("429"))
        assertEquals(5, 5.4.roundToInt())
    }

    @Test fun formatsDistanceDurationAndEta() {
        assertEquals("80 m", NavFormat.distance(83.0))
        assertEquals("350 m", NavFormat.distance(347.0))
        assertEquals("1,2 km", NavFormat.distance(1234.0))
        assertEquals("18 dk", NavFormat.duration(1080.0))
        assertEquals("1 sa 5 dk", NavFormat.duration(3900.0))
        assertEquals("14:32", NavFormat.eta(java.time.LocalTime.of(14, 14), 1080.0))
    }

    // ---------- serit ortasi (rotaya kilitleme) ----------
    @Test fun progressSnapsLateralGpsErrorBackToRouteCentreLine() {
        val r = straightRoute()
        val e = NavEngine(r)
        val p = e.update(GeoPoint(36.9100, 30.70003))   // ~2.7 m dogu, serit disina sapmis GPS
        assertEquals(30.70, p.snapLon, 1e-6)             // rota cizgisine oturur
        assertEquals(36.9100, p.snapLat, 2e-4)
        assertEquals(0.0, p.routeBearing, 1.0)           // kuzey
    }

    @Test fun pointAtDistanceInterpolatesAndClamps() {
        val r = straightRoute()
        val mid = NavGeo.pointAtDistance(r.points, r.cumDist, r.cumDist[3] + 10.0)
        assertEquals(36.90 + 3 * 0.0045, mid.lat, 0.0002)
        assertEquals(r.points.first().lat, NavGeo.pointAtDistance(r.points, r.cumDist, -5.0).lat, 1e-12)
        assertEquals(r.points.last().lat, NavGeo.pointAtDistance(r.points, r.cumDist, 1e9).lat, 1e-12)
    }

    // ---------- gun dogumu / batimi ----------
    private fun utcMs(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        java.time.ZonedDateTime.of(y, mo, d, h, mi, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun minutesUtc(ms: Long): Int { val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC); return z.hour * 60 + z.minute }

    @Test fun sunTimesMatchKnownReferenceValues() {
        // Londra (51.5N, 0.1W), 16 Kasim: dogus ~07:12, batis ~16:07 (UTC) — bilinen referans
        val ev = SunCalc.events(utcMs(2026, 11, 16, 12, 0), 51.5, -0.1)
        val today = ev.filter { java.time.Instant.ofEpochMilli(it.first).atZone(java.time.ZoneOffset.UTC).dayOfMonth == 16 }
        assertTrue("dogus ${minutesUtc(today.first { it.second }.first)}", abs(minutesUtc(today.first { it.second }.first) - (7 * 60 + 12)) <= 8)
        assertTrue("batis ${minutesUtc(today.first { !it.second }.first)}", abs(minutesUtc(today.first { !it.second }.first) - (16 * 60 + 7)) <= 8)
    }

    @Test fun antalyaSunTimesAreInTurkeyTimeZonePlausibleRange() {
        // Antalya 36.9N 30.7E (Turkiye UTC+3, saat dilimi meridyeninin batisinda): kasimda dogus ~07:36, batis ~17:47 yerel
        val ev = SunCalc.events(utcMs(2026, 11, 16, 9, 0), 36.9, 30.7)
        val today = ev.filter { java.time.Instant.ofEpochMilli(it.first).atZone(java.time.ZoneOffset.UTC).dayOfMonth == 16 }
        val riseLocal = minutesUtc(today.first { it.second }.first) + 180
        val setLocal = minutesUtc(today.first { !it.second }.first) + 180
        assertTrue("dogus $riseLocal", riseLocal in (7 * 60 + 10)..(7 * 60 + 55))
        assertTrue("batis $setLocal", setLocal in (17 * 60 + 30)..(17 * 60 + 58))
    }

    @Test fun daylightFlagFollowsSunriseAndSunset() {
        val lat = 36.9; val lon = 30.7
        assertTrue(SunCalc.isDaylight(utcMs(2026, 11, 16, 9, 0), lat, lon, 12))     // 12:00 yerel
        assertFalse(SunCalc.isDaylight(utcMs(2026, 11, 16, 20, 0), lat, lon, 23))   // 23:00 yerel
        assertFalse(SunCalc.isDaylight(utcMs(2026, 11, 16, 2, 0), lat, lon, 5))     // 05:00 yerel (dogustan once)
        assertTrue(SunCalc.isDaylight(utcMs(2026, 6, 21, 17, 0), lat, lon, 20))     // yaz: 20:00 yerel hala gunduz
        assertTrue(SunCalc.isDaylight(0L, null, null, 12))                          // konum yoksa saate gore
        assertFalse(SunCalc.isDaylight(0L, null, null, 3))
    }
}
