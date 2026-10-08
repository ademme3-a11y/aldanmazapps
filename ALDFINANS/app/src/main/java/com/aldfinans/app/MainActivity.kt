package com.aldfinans.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.aldfinans.app.domain.estimateMonthEnd
import java.text.NumberFormat
import java.util.Locale

private enum class Screen(val route: String, val label: String) {
    HOME("home", "Ana Sayfa"), DETAILS("details", "Detay"),
    INCOME("income", "Gelir"), SUMMARY("summary", "Özet"), SETTINGS("settings", "Ayarlar")
}

private data class DemoTransaction(
    val date: String, val time: String, val bank: String, val merchant: String,
    val category: String, val amount: Double, val source: String, val type: String = "EXPENSE"
)

private val demoTransactions = listOf(
    DemoTransaction("08.10.2026", "14:32", "Yapı Kredi", "Migros", "Gıda", -850.0, "SMS"),
    DemoTransaction("08.10.2026", "16:10", "Akbank", "Shell", "Akaryakıt", -1200.0, "Push"),
    DemoTransaction("08.10.2026", "18:05", "Manuel", "Diğer Harcama", "Diğer", -300.0, "Manuel", "EXTRA"),
    DemoTransaction("08.10.2026", "17:45", "İş Bankası", "Maaş", "Gelir", 45000.0, "Manuel", "INCOME")
)

private val currency = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ALDFinansApp() }
    }
}

@Composable
private fun ALDFinansApp() {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF155EEF),
        secondary = Color(0xFF0F766E),
        surface = Color(0xFFF8FAFC)
    )) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        Scaffold(bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = currentRoute == s.route,
                        onClick = {
                            nav.navigate(s.route) {
                                popUpTo(Screen.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(s.label.take(1)) },
                        label = { Text(s.label) }
                    )
                }
            }
        }) { padding ->
            NavHost(nav, startDestination = Screen.HOME.route, modifier = Modifier.padding(padding)) {
                composable(Screen.HOME.route) { HomeScreen() }
                composable(Screen.DETAILS.route) { DetailsScreen() }
                composable(Screen.INCOME.route) { IncomeScreen() }
                composable(Screen.SUMMARY.route) { SummaryScreen() }
                composable(Screen.SETTINGS.route) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val income = 45000.0
    val bankExpenses = 2050.0
    val extraExpenses = 300.0
    val totalExpense = bankExpenses + extraExpenses
    val net = income - totalExpense
    val forecast = estimateMonthEnd(totalExpense, 8, 31, 11800.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ALDFİNANS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Kişisel finans merkezi", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Toplam Gelir", currency.format(income), Modifier.weight(1f))
                MetricCard("Toplam Harcama", currency.format(totalExpense), Modifier.weight(1f))
            }
        }
        item { MetricCard("Net Durum", currency.format(net), Modifier.fillMaxWidth()) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ekim 2026 • Tahmini Harcama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Şu ana kadar: " + currency.format(forecast.actual))
                    Text("Ay sonu tahmini: " + currency.format(forecast.estimatedMonthEnd), fontWeight = FontWeight.Bold)
                    Text("Kalan tahmini: " + currency.format(forecast.remaining))
                    Text("Geçmiş ortalamaya fark: " + currency.format(forecast.differenceVsAverage),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Son İşlemler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(demoTransactions) { TransactionRow(it) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailsScreen() {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text("Detaylı İşlemler", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(demoTransactions) { TransactionRow(it) }
        }
    }
}

@Composable
private fun TransactionRow(t: DemoTransaction) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant, fontWeight = FontWeight.Bold)
                Text(t.date + " • " + t.time + " • " + t.bank, style = MaterialTheme.typography.bodySmall)
                Text(t.category + " • " + t.source, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(currency.format(t.amount), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IncomeScreen() {
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Gelir Ekle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(amount, { amount = it }, label = { Text("Tutar (TL)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Geliri Kaydet") }
        Spacer(Modifier.height(24.dp))
        Text("Gelir kaydı bir sonraki adımda Room'a bağlanacak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Finans Özeti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SummaryRow("Banka harcamaları", 2050.0)
        SummaryRow("Extra / Diğer harcama", 300.0)
        SummaryRow("Toplam harcama", 2350.0)
        SummaryRow("Gelir", 45000.0)
        SummaryRow("Net", 42650.0)
        Spacer(Modifier.height(20.dp))
        Text("Tahmini Harcama", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Geçmiş ay ortalaması ve mevcut ay temposu birlikte değerlendirilecek.")
    }
}

@Composable
private fun SummaryRow(title: String, amount: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(currency.format(amount), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Bankalar: Yapı Kredi • İş Bankası • Akbank")
        Spacer(Modifier.height(8.dp))
        Text("Kaynaklar: SMS • Push • E-posta • Ekstre")
        Spacer(Modifier.height(8.dp))
        Text("Dışa aktarma: Excel • PDF • CSV")
    }
}
