package com.aldanmaz.shoppingagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProductResult(
    val store: String, val title: String, val price: Double,
    val shipping: Double, val inStock: Boolean, val rating: Double,
    val detail: String, val url: String
) { val total: Double get() = price + shipping }

private val stores = listOf("Amazon", "Trendyol", "Hepsiburada", "Pazarama", "n11", "Migros", "A101", "ŞOK", "BİM", "MediaMarkt", "Teknosa")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShoppingAgentApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingAgentApp() {
    var query by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var minRating by remember { mutableStateOf(4f) }
    var onlyStock by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<ProductResult>>(emptyList()) }
    val context = LocalContext.current

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1D4ED8), secondary = Color(0xFF0F766E))) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ALIŞVERİŞ AJANI", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text("Akıllı fiyat karşılaştırma", fontSize = 11.sp)
                    }},
                    navigationIcon = { Icon(Icons.Default.ShoppingCart, null, Modifier.padding(start = 16.dp)) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(padding),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Ne arıyorsunuz?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Örn. Brita Maxtra Pro 6'lı filtre") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = maxPrice, onValueChange = { maxPrice = it.filter { c -> c.isDigit() || c == ',' } },
                                    modifier = Modifier.weight(1f), label = { Text("Maks. TL") }, singleLine = true
                                )
                                Button(
                                    onClick = { results = buildDemoResults(query, minRating, onlyStock, maxPrice.replace(",", ".").toDoubleOrNull()) },
                                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(16.dp),
                                    enabled = query.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("ARA", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp))
                                Text("Ajan filtreleri", fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = onlyStock, onCheckedChange = { onlyStock = it })
                                Text("Sadece stokta olanlar")
                            }
                            Text("Minimum puan: " + "%.1f".format(minRating))
                            Slider(value = minRating, onValueChange = { minRating = it }, valueRange = 0f..5f, steps = 9)
                        }
                    }
                }
                item {
                    Text("Tarama kapsamı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stores.size.toString() + " mağaza hedefleniyor • ürün, stok, kargo ve birim fiyat analizi",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (results.isNotEmpty()) {
                    item { Text("En uygun 3 sonuç", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
                    items(results.take(3)) { result ->
                        ResultCard(result) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                        }
                    }
                } else {
                    item {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(38.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Ajan hazır", fontWeight = FontWeight.Bold)
                                Text("Ürünü yazıp ARA'ya basın. Bu ilk APK, arayüz ve karşılaştırma çekirdeğinin test sürümüdür; canlı mağaza bağlayıcıları sonraki katmanda eklenecek.",
                                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ProductResult, onOpen: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("MAĞAZA  " + result.store, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text(if (result.inStock) "● STOKTA" else "○ STOK YOK",
                    color = if (result.inStock) Color(0xFF15803D) else Color.Gray,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(result.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(result.detail, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(money(result.total) + " TL", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp)); Text("kargo dahil", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f)); Text("★ " + "%.1f".format(result.rating), fontWeight = FontWeight.Bold)
            }
            Button(onClick = onOpen, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("MAĞAZAYI AÇ", fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.OpenInNew, null)
            }
        }
    }
}

private fun money(v: Double) = "%,.2f".format(v).replace(",", "X").replace(".", ",").replace("X", ".")

private fun buildDemoResults(q: String, rating: Float, stock: Boolean, max: Double?): List<ProductResult> {
    val base = listOf(
        ProductResult("Trendyol", q, 799.90, 0.0, true, 4.7, "Örnek karşılaştırma sonucu", "https://www.trendyol.com/"),
        ProductResult("Hepsiburada", q, 829.90, 0.0, true, 4.6, "Örnek karşılaştırma sonucu", "https://www.hepsiburada.com/"),
        ProductResult("Amazon", q, 849.90, 39.90, true, 4.8, "Örnek karşılaştırma sonucu", "https://www.amazon.com.tr/"),
        ProductResult("n11", q, 879.90, 0.0, true, 4.5, "Örnek karşılaştırma sonucu", "https://www.n11.com/")
    )
    return base.filter { (!stock || it.inStock) && it.rating >= rating && (max == null || it.total <= max) }.sortedBy { it.total }
}
