package com.aldanmaz.drivedashboard.nav

import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Degistirilebilir arama saglayicisi. */
interface GeocodingProvider {
    val name: String
    suspend fun search(query: String, bias: GeoPoint?, limit: Int = 8): List<PlaceResult>
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/**
 * Photon (komoot): yazarken-arama (autocomplete) icin tasarlanmistir, konum onceligi (lat/lon) destekler.
 * lang parametresi bilerek gonderilmez: Photon yalnizca birkac dili destekler ve Turkce isteyince hata verebilir;
 * varsayilan yerel (Turkce) adlar donduruldugu icin ihtiyac yoktur.
 * Genel sunucu (photon.komoot.io) "makul kullanim" sinirlidir; baseUrl yapilandirilabilir.
 */
class PhotonGeocoder(private val baseUrl: String, private val http: NavHttpClient) : GeocodingProvider {
    override val name = "Photon"

    override suspend fun search(query: String, bias: GeoPoint?, limit: Int): List<PlaceResult> {
        val sb = StringBuilder(baseUrl).append(if (baseUrl.contains('?')) '&' else '?')
        sb.append("q=").append(enc(query)).append("&limit=").append(limit)
        if (bias != null) sb.append("&lat=").append(String.format(Locale.US, "%.5f", bias.lat))
            .append("&lon=").append(String.format(Locale.US, "%.5f", bias.lon))
        return parse(http.get(sb.toString()))
    }

    companion object {
        fun parse(json: String): List<PlaceResult> {
            val features = parseJson(json).obj()?.a("features") ?: return emptyList()
            val out = ArrayList<PlaceResult>()
            for (f in features) {
                val fo = f.obj() ?: continue
                val coords = fo.o("geometry")?.a("coordinates") ?: continue
                val lon = coords.getOrNull(0)?.dbl() ?: continue
                val lat = coords.getOrNull(1)?.dbl() ?: continue
                val p = fo.o("properties") ?: continue
                val street = p.str("street")
                val house = p.str("housenumber")
                val name = p.str("name")
                    ?: listOfNotNull(street, house).joinToString(" ").ifBlank { null }
                    ?: p.str("city") ?: p.str("district") ?: p.str("state") ?: continue
                val sub = listOfNotNull(
                    if (p.str("name") != null) listOfNotNull(street, house).joinToString(" ").ifBlank { null } else null,
                    p.str("district") ?: p.str("locality"), p.str("city") ?: p.str("county"),
                    p.str("state"), p.str("country")
                ).filter { it != name }.distinct().joinToString(", ")
                val kind = listOfNotNull(p.str("osm_key"), p.str("osm_value")).joinToString(":")
                out.add(PlaceResult("${p.str("osm_type").orEmpty()}${p.str("osm_id").orEmpty()}:$lat,$lon", name, sub, lat, lon, kind))
            }
            return out
        }
    }
}

/**
 * Nominatim: yalnizca ACIK (kullanici butona basinca) aramada yedek olarak kullanilir;
 * Nominatim politikasi yazarken-otomatik tamamlamayi yasaklar.
 */
class NominatimGeocoder(private val baseUrl: String, private val http: NavHttpClient) : GeocodingProvider {
    override val name = "Nominatim"

    override suspend fun search(query: String, bias: GeoPoint?, limit: Int): List<PlaceResult> {
        val sb = StringBuilder(baseUrl).append(if (baseUrl.contains('?')) '&' else '?')
        sb.append("format=jsonv2&addressdetails=1&accept-language=tr&limit=").append(limit)
        sb.append("&q=").append(enc(query))
        if (bias != null) {
            // Yakini tercih et (bounded=0: sonuclari kisitlamaz, yalnizca siralamayi etkiler).
            val d = 2.0
            sb.append("&viewbox=").append(String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                bias.lon - d, bias.lat + d, bias.lon + d, bias.lat - d))
        }
        return parse(http.get(sb.toString()))
    }

    companion object {
        fun parse(json: String): List<PlaceResult> {
            val arr = parseJson(json).arr() ?: return emptyList()
            val out = ArrayList<PlaceResult>()
            for (e in arr) {
                val o = e.obj() ?: continue
                val lat = o.str("lat")?.toDoubleOrNull() ?: continue
                val lon = o.str("lon")?.toDoubleOrNull() ?: continue
                val display = o.str("display_name").orEmpty()
                val name = o.str("name")?.takeIf { it.isNotBlank() } ?: display.substringBefore(',').trim()
                if (name.isBlank()) continue
                val sub = display.substringAfter(',', "").trim()
                val kind = listOfNotNull(o.str("category"), o.str("type")).joinToString(":")
                out.add(PlaceResult("${o.str("osm_type").orEmpty()}${o.str("osm_id").orEmpty()}", name, sub, lat, lon, kind))
            }
            return out
        }
    }
}

/**
 * Birincil saglayici + (acik aramada) yedek saglayici, kucuk bir bellek onbellegi ve Turkce karakter yedegi.
 * Sonuc bos ise "bulunamadi", tum saglayicilar hata verirse hata firlatilir; ikisi asla karistirilmaz.
 */
class GeocodingService(
    private val primary: GeocodingProvider,
    private val secondary: GeocodingProvider?
) {
    private val cache = object : LinkedHashMap<String, List<PlaceResult>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlaceResult>>?) = size > 48
    }

    suspend fun search(query: String, bias: GeoPoint?, explicit: Boolean): List<PlaceResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val key = cacheKey(q, bias)
        synchronized(cache) { cache[key] }?.let { return it }

        var firstError: NavException? = null
        var anySuccess = false

        suspend fun attempt(p: GeocodingProvider, text: String): List<PlaceResult> = try {
            p.search(text, bias).also { anySuccess = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NavException) {
            if (firstError == null) firstError = e
            emptyList()
        } catch (e: Exception) {
            if (firstError == null) firstError = NavException.Parse(e.message ?: "")
            emptyList()
        }

        var res = dedupe(attempt(primary, q))
        if (res.isEmpty()) {
            val folded = foldTurkish(q)
            if (folded != q) res = dedupe(attempt(primary, folded))
        }
        if (res.isEmpty() && explicit && secondary != null) res = dedupe(attempt(secondary, q))

        if (res.isEmpty()) {
            if (!anySuccess) throw (firstError ?: NavException.Network("arama yanıt vermedi"))
            return emptyList()
        }
        synchronized(cache) { cache[key] = res }
        return res
    }

    private fun cacheKey(q: String, bias: GeoPoint?): String {
        val b = bias?.let { "${Math.round(it.lat * 10)}:${Math.round(it.lon * 10)}" } ?: "-"
        return q.lowercase(Locale("tr", "TR")) + "|" + b
    }

    private fun dedupe(list: List<PlaceResult>): List<PlaceResult> {
        val seen = HashSet<String>()
        return list.filter { seen.add("${it.name.lowercase()}|${Math.round(it.lat * 1000)}|${Math.round(it.lon * 1000)}") }
    }

    companion object {
        /** ı->i, ş->s, ğ->g, ü->u, ö->o, ç->c (sunucu Turkce karakterleri eslemezse yedek sorgu). */
        fun foldTurkish(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) sb.append(
                when (c) {
                    'ı' -> 'i'; 'İ' -> 'I'; 'ş' -> 's'; 'Ş' -> 'S'; 'ğ' -> 'g'; 'Ğ' -> 'G'
                    'ü' -> 'u'; 'Ü' -> 'U'; 'ö' -> 'o'; 'Ö' -> 'O'; 'ç' -> 'c'; 'Ç' -> 'C'
                    else -> c
                }
            )
            return sb.toString()
        }
    }
}
