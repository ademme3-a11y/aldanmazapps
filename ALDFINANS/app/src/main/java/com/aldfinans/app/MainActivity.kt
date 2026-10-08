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
import java.text.NumberFormat
import java.util.Locale

private enum class Screen(val route: String, val label: String) {
    HOME("home", "Ana Sayfa"),
    DETAILS("details", "Detay"),
    INCOME("income", "Gelir"),
    SUMMARY("summary", "Özet"),
    SETTINGS("settings", "Ayarlar")
}

private data class DemoTransaction(
    val date: String, val time: String, val bank: String,
    val merchant: String, val category: String,
    val amount: Double, val source: String
)

private val demoTransactions = listOf(
    DemoTransaction("08.10.2026", "14:32", "Yapı Kredi", "Migros", "Gıda", -850.0, "SMS"),
    DemoTransaction("08.10.2026", "16:10", "Akbank", "Shell", "Akaryakıt", -1200.0, "Push"),
    DemoTransaction("08.10.2026", "17:45", "İş Bankası", "Maaş", "Gelir", 45000.0, "Manuel")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ALDFinansApp() }
    }
}

@Composable
private fun ALDFinansApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF155EEF),
            secondary = Color(0xFF0F766E),
            surface = Color(0xFFF8FAFC)
        )
    ) {
        val nav = rememberNavController()
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf(Screen.HOME, Screen.DETAILS, Screen.INCOME, Screen.SUMMARY, Screen.SETTINGS).forEach { s ->
                        NavigationBarItem(
                            selected = false,
                            onClick = { nav.navigate(s.route) },
                            icon = { Text(s.label.take(1)) },
                            label = { Text(s.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = Screen.HOME.route, modifier = Modifier.padding(padding)) {
                composable(Screen.HOME.route) { HomeScreen() }
                composable(Screen.DETAILS.route) { DetailsScreen() }
                composable(Screen.INCOME.route) { SimpleScreen("Gelirler", "Manuel gelir kayıtları burada yönetilecek.") }
                composable(Screen.SUMMARY.route) { SimpleScreen("Özet", "Aylık, yıllık, kategori ve banka özetleri burada gösterilecek.") }
                composable(Screen.SETTINGS.route) { SimpleScreen("Ayarlar", "Bankalar, kategoriler, içe aktarma ve dışa aktarma ayarları.") }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val currency = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("ALDFİNANS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Kişisel finans merkezi", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Toplam Gelir", currency.format(45000), Modifier.weight(1f))
            MetricCard("Harcama", currency.format(2050), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MetricCard("Net Durum", currency.format(42950), Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("Son İşlemler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        demoTransactions.forEach { TransactionRow(it) }
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
        Text("Detaylı İşlem Tablosu", style = MaterialTheme.typography.headlineSmall,
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
            Text(
                NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(t.amount),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SimpleScreen(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
