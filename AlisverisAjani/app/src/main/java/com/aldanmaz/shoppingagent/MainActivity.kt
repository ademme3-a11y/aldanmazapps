package com.aldanmaz.shoppingagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

data class Qty(val value: Double, val unit: String, val normalized: Double, val raw: String)
data class Spec(val product: String, val brand: String?, val qty: Qty?, val max: Double?, val rating: Double)
data class Product(val store: String, val title: String, val amount: String, val price: Double, val rating: Double?, val stock: Boolean, val url: String)

data class Store(val name: String, val host: String, val search: (String) -> String)

private val stores = listOf(
    Store("Trendyol", "trendyol.com") { q -> "https://www.trendyol.com/sr?q=" + enc(q) },
    Store("Hepsiburada", "hepsiburada.com") { q -> "https://www.hepsiburada.com/ara?q=" + enc(q) },
    Store("Amazon TR", "amazon.com.tr") { q -> "https://www.amazon.com.tr/s?k=" + enc(q) },
    Store("n11", "n11.com") { q -> "https://www.n11.com/arama?q=" + enc(q) },
    Store("Pazarama", "pazarama.com") { q -> "https://www.pazarama.com/arama?q=" + enc(q) },
    Store("MediaMarkt", "mediamarkt.com.tr") { q -> "https://www.mediamarkt.com.tr/tr/search.html?query=" + enc(q) },
    Store("Teknosa", "teknosa.com") { q -> "https://www.teknosa.com/arama/?s=" + enc(q) },
    Store("Migros", "migros.com.tr") { q -> "https://www.migros.com.tr/arama?q=" + enc(q) },
    Store("A101", "a101.com.tr") { q -> "https://www.a101.com.tr/arama?q=" + enc(q) },
    Store("CarrefourSA", "carrefoursa.com") { q -> "https://www.carrefoursa.com/search?q=" + enc(q) },
    Store("SOK", "sokmarket.com.tr") { q -> "https://www.sokmarket.com.tr/arama?q=" + enc(q) }
)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { App() }
    }

    fun search(spec: Spec, done: (String, List<Product>) -> Unit) {
        lifecycleScope.launch {
            done("Mağazalar taranıyor…", emptyList())
            try {
                val result = withContext(Dispatchers.IO) { Engine.search(spec) }
                done(
                    if (result.isEmpty()) "Doğrulanmış uygun ürün bulunamadı."
                    else result.size.toString() + " doğrulanmış ürün bulundu.",
                    result
                )
            } catch (t: Throwable) {
                done("Arama sırasında hata oluştu: " + (t.message ?: "bilinmeyen hata"), emptyList())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val activity = LocalContext.current as MainActivity
    val context = LocalContext.current
    var product by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(4f) }
    var stockOnly by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Hazır") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Product>()) }
    var spec by remember { mutableStateOf<Spec?>(null) }

    MaterialTheme {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ALIŞVERİŞ AJANI", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("V5 • canlı ürün doğrulama", fontSize = 11.sp)
                }
            }, navigationIcon = {
                Icon(Icons.Default.ShoppingCart, null, Modifier.padding(start = 16.dp))
            })
        }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("ARAMA KRİTERLERİ", fontWeight = FontWeight.Black)
                            OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Ürün") }, singleLine = true)
                            OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("MARKA") }, singleLine = true)
                            OutlinedTextField(
                                qty, { qty = it }, Modifier.fillMaxWidth(),
                                label = { Text("EBAT / MİKTAR") },
                                placeholder = { Text("5 L • 500 gr • 1 kg • 2500 ml • 65 inç • 12 adet") },
                                singleLine = true
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    max,
                                    { max = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                    Modifier.weight(1f),
                                    label = { Text("Maks. TL") },
                                    singleLine = true
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    enabled = product.isNotBlank() && !busy,
                                    onClick = {
                                        val s = Engine.analyze(product, brand.trim().ifBlank { null }, qty.trim().ifBlank { null }, parseMoney(max), rating.toDouble())
                                        spec = s
                                        busy = true
                                        activity.search(s) { msg, data ->
                                            status = msg
                                            results = if (stockOnly) data.filter { it.stock } else data
                                            busy = false
                                        }
                                    },
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.Search, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (busy) "TARANIYOR" else "ARA")
                                }
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, null)
                                Spacer(Modifier.width(7.dp))
                                Text("FİLTRELER", fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(stockOnly, { stockOnly = it })
                                Text("Sadece stokta olanlar")
                            }
                            Text("Minimum puan: " + String.format(Locale.US, "%.1f", rating))
                            Slider(rating, { rating = it }, valueRange = 0f..5f, steps = 9)
                        }
                    }
                }
                spec?.let { s ->
                    item {
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(13.dp)) {
                                Text("AJANIN ÇÖZDÜĞÜ İSTEK", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Text("Ürün: " + s.product)
                                Text("Marka: " + (s.brand ?: "belirtilmedi"))
                                Text("Ebat / Miktar: " + (s.qty?.raw ?: "belirtilmedi"))
                                Text("Maksimum: " + (s.max?.let { formatMoney(it) + " TL" } ?: "belirtilmedi"))
                            }
                        }
                    }
                }
                item { Text(status, fontWeight = FontWeight.Bold) }
                if (results.isNotEmpty()) {
                    item { Text("A4 LİSTE • " + results.size.coerceAtMost(20) + " ÜRÜN", fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    itemsIndexed(results.take(20), key = { i, p -> p.url + i }) { index, p ->
                        ProductRow(index + 1, p) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(no: Int, p: Product, open: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("%02d".format(no), Modifier.width(30.dp), fontWeight = FontWeight.Black)
            Column(Modifier.width(260.dp)) {
                Text(p.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(p.amount.ifBlank { "Miktar doğrulanamadı" }, fontSize = 11.sp)
            }
            Column(Modifier.width(110.dp), horizontalAlignment = Alignment.End) {
                Text(formatMoney(p.price) + " TL", fontWeight = FontWeight.Black, fontSize = 16.sp)
                p.rating?.let { Text("★ " + String.format(Locale.US, "%.1f", it), fontSize = 11.sp) }
            }
            Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.store, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(if (p.stock) "STOK: VAR" else "STOK: YOK", fontSize = 10.sp)
            }
            Button(onClick = open, modifier = Modifier.width(135.dp)) {
                Icon(Icons.Default.OpenInNew, null)
                Spacer(Modifier.width(3.dp))
                Text("ÜRÜNÜ GÖR")
            }
        }
    }
}

private object Engine {
    private val qtyRx = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(kg|g|gr|gram|l|lt|litre|liter|ml|mg|adet|ad|paket|kutu|tb|gb|mb|inç|inch|inc|")""")
    private val priceRx = Regex("""(?i)(\d{1,3}(?:[. ]\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*(?:TL|₺)""")
    private val ratingRx = Regex("""(?<!\d)([0-5](?:[.,]\d))(?=\s*(?:\(|/|★|puan))""")
    private val badStockRx = Regex("""(?i)stok\s*yok|tükendi|tukendi|satışta değil|satis\s*ta degil|out of stock""")

    fun analyze(product: String, brand: String?, quantity: String?, max: Double?, rating: Double): Spec =
        Spec(product.trim(), brand, quantity?.let(::parseQty), max, rating)

    fun search(s: Spec): List<Product> {
        val candidates = mutableListOf<Product>()
        for (store in stores) {
            if (candidates.size >= 80) break
            val batch = runCatching { fetch(store, s) }.getOrDefault(emptyList())
            candidates.addAll(batch.take(20))
        }
        return candidates.asSequence()
            .filter { verify(it, s) }
            .distinctBy { it.url.substringBefore("#").substringBefore("?").trimEnd('/') }
            .sortedBy { it.price }
            .take(20)
            .toList()
    }

    private fun fetch(store: Store, s: Spec): List<Product> {
        val q = buildString {
            append(s.product)
            s.brand?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
            s.qty?.raw?.let { append(' ').append(it) }
        }
        val doc = Jsoup.connect(store.search(q))
            .userAgent(USER_AGENT)
            .timeout(10000)
            .maxBodySize(600_000)
            .followRedirects(true)
            .get()
        val out = ArrayList<Product>(32)
        doc.select("script[type=application/ld+json]").take(20).forEach { script ->
            if (out.size < 20) runCatching { parseJson(script.data(), store, out) }
        }
        if (out.size < 20) parseAnchors(doc, store, out)
        return out
    }

    private fun verify(p: Product, s: Spec): Boolean {
        val uri = Uri.parse(p.url)
        if (uri.scheme != "https") return false
        if (!sameHost(p.url, stores.firstOrNull { it.name == p.store }?.host)) return false
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (path.isBlank() || path == "/" || path.contains("arama") || path.contains("search")) return false
        val title = normalize(p.title)
        if (s.brand != null && !title.contains(normalize(s.brand))) return false
        val tokens = normalize(s.product).split(" ").filter { it.length >= 3 && it !in STOP }
        if (tokens.any { !title.contains(it) }) return false
        if (s.qty != null) {
            val actual = parseQty(p.amount.ifBlank { p.title }) ?: return false
            if (!sameQuantity(actual, s.qty)) return false
        }
        if (p.price <= 0.0 || (s.max != null && p.price > s.max)) return false
        if (p.rating == null || p.rating < s.rating) return false
        return true
    }

    private fun parseJson(raw: String, store: Store, out: MutableList<Product>) {
        val text = raw.trim()
        when {
            text.startsWith("{") -> runCatching { walk(JSONObject(text), store, out) }
            text.startsWith("[") -> runCatching {
                val a = JSONArray(text)
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { walk(it, store, out) }
            }
        }
    }

    private fun walk(o: JSONObject, store: Store, out: MutableList<Product>) {
        if (o.optString("@type").contains("Product", true)) {
            val title = o.optString("name").trim()
            val url = o.optString("url").trim()
            val offers = o.opt("offers")
            val offer = when (offers) {
                is JSONObject -> offers
                is JSONArray -> (0 until offers.length()).mapNotNull { offers.optJSONObject(it) }.firstOrNull()
                else -> null
            }
            val price = parsePrice(offer?.optString("price").orEmpty().ifBlank { offer?.optString("lowPrice").orEmpty() })
            val rating = o.optJSONObject("aggregateRating")?.optString("ratingValue")?.replace(",", ".")?.toDoubleOrNull()
            val availability = offer?.optString("availability").orEmpty()
            val stock = availability.isBlank() || availability.contains("InStock", true) || availability.contains("LimitedAvailability", true)
            if (title.isNotBlank() && url.isNotBlank() && price != null) {
                out += Product(store.name, title, extractAmount(title), price, rating, stock, absolute(store.host, url))
            }
        }
        val keys = o.keys()
        while (keys.hasNext()) {
            when (val v = o.opt(keys.next())) {
                is JSONObject -> walk(v, store, out)
                is JSONArray -> for (i in 0 until v.length()) v.optJSONObject(i)?.let { walk(it, store, out) }
            }
        }
    }

    private fun parseAnchors(doc: Document, store: Store, out: MutableList<Product>) {
        doc.select("a[href]").take(200).forEach { a ->
            val url = a.absUrl("href")
            val title = a.text().trim()
            if (title.length < 12 || url.isBlank() || !sameHost(url, store.host)) return@forEach
            val path = Uri.parse(url).path.orEmpty().lowercase(Locale.US)
            if (path.isBlank() || path == "/" || path.contains("arama") || path.contains("search")) return@forEach
            val parent = a.parent()?.parent()?.text().orEmpty().ifBlank { title }
            val pm = priceRx.find(parent) ?: return@forEach
            val price = parsePrice(pm.groupValues[1]) ?: return@forEach
            val rating = ratingRx.find(parent)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            val stock = !badStockRx.containsMatchIn(parent)
            if (out.size < 20) out += Product(store.name, title, extractAmount(parent), price, rating, stock, url)
        }
    }

    private fun parseQty(raw: String): Qty? {
        val m = qtyRx.find(raw) ?: return null
        val value = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        val unit = unit(m.groupValues[2])
        return Qty(value, unit, normalizeQty(value, unit), m.value.trim())
    }

    private fun sameQuantity(a: Qty, b: Qty): Boolean {
        if (a.unit == "inc" || b.unit == "inc") return a.unit == b.unit && abs(a.value - b.value) < 0.001
        val compatible = when {
            a.unit in setOf("g", "kg", "mg") && b.unit in setOf("g", "kg", "mg") -> true
            a.unit in setOf("ml", "l") && b.unit in setOf("ml", "l") -> true
            a.unit in setOf("mb", "gb", "tb") && b.unit in setOf("mb", "gb", "tb") -> true
            a.unit == "adet" && b.unit == "adet" -> true
            else -> false
        }
        return compatible && abs(a.normalized - b.normalized) < 0.001
    }

    private fun unit(raw: String): String = when (normalize(raw)) {
        "kg" -> "kg"
        "g", "gr", "gram" -> "g"
        "mg" -> "mg"
        "l", "lt", "litre", "liter" -> "l"
        "ml" -> "ml"
        "adet", "ad", "paket", "kutu" -> "adet"
        "tb" -> "tb"
        "gb" -> "gb"
        "mb" -> "mb"
        "inç", "inch", "inc", "\"" -> "inc"
        else -> normalize(raw)
    }

    private fun normalizeQty(v: Double, u: String): Double = when (u) {
        "kg" -> v * 1000.0
        "g" -> v
        "mg" -> v / 1000.0
        "l" -> v * 1000.0
        "ml" -> v
        "tb" -> v * 1048576.0
        "gb" -> v * 1024.0
        "mb" -> v
        else -> v
    }

    private fun extractAmount(text: String): String = qtyRx.find(text)?.value?.trim().orEmpty()

    private fun parsePrice(raw: String): Double? {
        val x = raw.trim().replace("TL", "", true).replace("₺", "").replace(" ", "")
        if (x.isBlank()) return null
        return when {
            x.contains(",") -> x.replace(".", "").replace(",", ".").toDoubleOrNull()
            x.count { it == '.' } == 1 && x.substringAfter(".").length == 3 -> x.replace(".", "").toDoubleOrNull()
            else -> x.toDoubleOrNull()
        }
    }
}

private val STOP = setOf("ve", "ile", "icin", "için", "bir", "adet", "paket", "urun", "ürün", "tv", "televizyon")
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16; SM-A256B) AppleWebKit/537.36 Chrome/150 Mobile Safari/537.36"

private fun normalize(s: String): String = s.lowercase(Locale("tr", "TR"))
    .replace("ı", "i").replace("ş", "s").replace("ğ", "g").replace("ü", "u").replace("ö", "o").replace("ç", "c")
    .replace(Regex("""\s+"""), " ").trim()

private fun sameHost(url: String, host: String?): Boolean =
    host != null && (Uri.parse(url).host?.lowercase(Locale.US)?.endsWith(host.lowercase(Locale.US)) == true)

private fun absolute(host: String, url: String): String =
    if (url.startsWith("http")) url else if (url.startsWith("/")) "https://" + host + url else "https://" + host + "/" + url

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun parseMoney(s: String): Double? {
    val x = s.trim().replace(" ", "")
    if (x.isBlank()) return null
    return if (x.contains(",")) x.replace(".", "").replace(",", ".").toDoubleOrNull()
    else if (x.count { it == '.' } == 1 && x.substringAfter(".").length == 3) x.replace(".", "").toDoubleOrNull()
    else x.toDoubleOrNull()
}

private fun formatMoney(v: Double): String = String.format(Locale("tr", "TR"), "%,.2f", v)
