package com.aldfinans.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.aldfinans.app.data.AlDFinansDatabase
import com.aldfinans.app.data.IncomeEntity
import com.aldfinans.app.data.TransactionEntity
import com.aldfinans.app.data.sms.MessageImportResult
import com.aldfinans.app.data.sms.MessageImportService
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen(val route: String, val label: String) {
    HOME("home", "Ana Sayfa"), DETAILS("details", "İşlemler"),
    INCOME("income", "Gelir"), SUMMARY("summary", "Özet"), SETTINGS("settings", "Ayarlar")
}
private val currency = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))

private fun money(kurus: Long): String = currency.format(kurus / 100.0)

private fun parseKurus(input: String): Long? {
    val raw = input.trim().replace("₺", "").replace("TL", "", ignoreCase = true).replace(" ", "")
    if (raw.isBlank()) return null
    val normalized = when {
        raw.contains(",") && raw.contains(".") ->
            if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) raw.replace(".", "").replace(",", ".")
            else raw.replace(",", "")
        raw.contains(",") -> raw.replace(",", ".")
        raw.contains(".") && raw.substringAfterLast(".").length == 3 -> raw.replace(".", "")
        else -> raw
    }
    return runCatching { BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it > 0 } }.getOrNull()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ALDFinansApp() }
    }
}

@Composable
private fun ALDFinansApp() {
    val context = LocalContext.current
    val db = remember { AlDFinansDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val transactions by db.financialDao().observeTransactions().collectAsState(initial = emptyList())
    val incomes by db.financialDao().observeIncomes().collectAsState(initial = emptyList())
    var scanResult by remember { mutableStateOf<MessageImportResult?>(null) }
    var scanMessage by remember { mutableStateOf("SMS taraması henüz yapılmadı.") }
    var showExpenseDialog by remember { mutableStateOf(false) }

    fun scanSms() {
        scope.launch {
            scanMessage = "SMS mesajları taranıyor…"
            scanResult = runCatching { MessageImportService.importInbox(context) }.getOrElse {
                scanMessage = "Tarama başarısız: SMS iznini ve cihaz ayarlarını kontrol edin."
                null
            }
            scanResult?.let {
                scanMessage = "Tarandı: ${it.scanned} SMS • ${it.matched} işlem eşleşti • ${it.inserted} yeni kayıt"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_SMS] == true) scanSms()
        else scanMessage = "SMS okuma izni verilmedi. Manuel kayıtlar kullanılabilir."
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val expenseTypes = setOf("EXPENSE", "CASH_WITHDRAWAL", "BILL")
    val grossExpenses = transactions.filter { it.transactionType in expenseTypes }.sumOf { it.amountKurus }
    val refunds = transactions.filter { it.transactionType == "REFUND" }.sumOf { it.amountKurus }
    val totalExpenses = (grossExpenses - refunds).coerceAtLeast(0L)
    val totalIncome = incomes.sumOf { it.amountKurus }
    val net = totalIncome - totalExpenses

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF155EEF),
        secondary = androidx.compose.ui.graphics.Color(0xFF0F766E),
        surface = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
    )) {
        Scaffold(
            floatingActionButton = {
                if (currentRoute == Screen.HOME.route || currentRoute == Screen.DETAILS.route) {
                    FloatingActionButton(onClick = { showExpenseDialog = true }) { Text("+") }
                }
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                nav.navigate(screen.route) {
                                    popUpTo(Screen.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(screen.label.take(1)) },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = Screen.HOME.route, modifier = Modifier.padding(padding)) {
                composable(Screen.HOME.route) {
                    HomeScreen(transactions, totalIncome, totalExpenses, net, onScan = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scanSms()
                        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }, scanMessage = scanMessage)
                }
                composable(Screen.DETAILS.route) {
                    DetailsScreen(transactions, onDelete = { id -> scope.launch { db.financialDao().deleteTransaction(id) } })
                }
                composable(Screen.INCOME.route) {
                    IncomeScreen(onSave = { amount, type, bank, description ->
                        scope.launch {
                            db.financialDao().insertIncome(IncomeEntity(
                                dateTime = System.currentTimeMillis(),
                                amountKurus = amount,
                                incomeType = type,
                                bank = bank.ifBlank { null },
                                description = description.ifBlank { null }
                            ))
                        }
                    }, incomes = incomes)
                }
                composable(Screen.SUMMARY.route) {
                    SummaryScreen(totalIncome, totalExpenses, refunds, net, transactions.size, incomes.size)
                }
                composable(Screen.SETTINGS.route) {
                    SettingsScreen(scanMessage, onScan = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scanSms()
                        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    })
                }
            }
        }
    }

    if (showExpenseDialog) {
        ManualExpenseDialog(
            onDismiss = { showExpenseDialog = false },
            onSave = { amount, merchant, category, note ->
                scope.launch {
                    db.financialDao().insertTransaction(TransactionEntity(
                        dateTime = System.currentTimeMillis(),
                        bank = "Manuel",
                        amountKurus = amount,
                        category = category,
                        merchant = merchant.ifBlank { "Diğer Harcama" },
                        cardLast4 = null,
                        source = "Manuel",
                        rawData = null,
                        transactionType = "EXPENSE",
                        note = note.ifBlank { null },
                        fingerprint = null
                    ))
                    showExpenseDialog = false
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    transactions: List<TransactionEntity>, income: Long, expenses: Long, net: Long,
    onScan: () -> Unit, scanMessage: String
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("ALDFİNANS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Kişisel finans merkezi", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Toplam Gelir", money(income), Modifier.weight(1f))
                MetricCard("Toplam Harcama", money(expenses), Modifier.weight(1f))
            }
        }
        item { MetricCard("Net Durum", money(net), Modifier.fillMaxWidth()) }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SMS banka işlemleri", fontWeight = FontWeight.Bold)
                    Text(scanMessage, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("SMS'LERİ TARA") }
                }
            }
        }
        item { Text("Son İşlemler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (transactions.isEmpty()) {
            item { EmptyState("Henüz işlem yok. SMS'leri tara veya + düğmesiyle harcama ekle.") }
        } else {
            items(transactions.take(8), key = { it.id }) { TransactionRow(it) }
        }
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
private fun DetailsScreen(transactions: List<TransactionEntity>, onDelete: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text("İşlem Geçmişi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
        if (transactions.isEmpty()) EmptyState("Kayıtlı işlem yok.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.id }) { item ->
                TransactionRow(item)
                TextButton(onClick = { onDelete(item.id) }) { Text("Kaydı sil") }
            }
        }
    }
}

@Composable
private fun TransactionRow(t: TransactionEntity) {
    val sign = when (t.transactionType) {
        "REFUND" -> "+"
        "CANCELLED" -> "±"
        else -> "−"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant ?: t.category, fontWeight = FontWeight.Bold)
                Text("${dateFormat.format(Date(t.dateTime))} • ${t.bank}", style = MaterialTheme.typography.bodySmall)
                Text("${t.category} • ${t.source} • ${typeLabel(t.transactionType)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$sign ${money(t.amountKurus)}", fontWeight = FontWeight.Bold)
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    "REFUND" -> "İade"
    "CASH_WITHDRAWAL" -> "Nakit çekim"
    "BILL" -> "Fatura"
    "CANCELLED" -> "İptal"
    else -> "Harcama"
}

@Composable
private fun IncomeScreen(onSave: (Long, String, String, String) -> Unit, incomes: List<IncomeEntity>) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Maaş") }
    var bank by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gelir Ekle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(amount, { amount = it }, label = { Text("Tutar (TL)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(type, { type = it }, label = { Text("Gelir türü") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bank, { bank = it }, label = { Text("Banka (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Açıklama") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val parsed = parseKurus(amount)
            if (parsed != null) { onSave(parsed, type.ifBlank { "Diğer Gelir" }, bank, description); amount = ""; description = "" }
        }, modifier = Modifier.fillMaxWidth()) { Text("GELİRİ KAYDET") }
        Text("Gelir geçmişi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        incomes.take(30).forEach { Text("${dateFormat.format(Date(it.dateTime))} • ${it.incomeType}: ${money(it.amountKurus)}") }
    }
}

@Composable
private fun SummaryScreen(income: Long, expenses: Long, refunds: Long, net: Long, transactionCount: Int, incomeCount: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Finans Özeti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SummaryRow("Banka ve manuel harcamalar", money(expenses + refunds))
        SummaryRow("İadeler", money(refunds))
        SummaryRow("Net harcama", money(expenses))
        SummaryRow("Gelir", money(income))
        SummaryRow("Net durum", money(net))
        Divider()
        SummaryRow("İşlem kaydı", transactionCount.toString())
        SummaryRow("Gelir kaydı", incomeCount.toString())
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsScreen(scanMessage: String, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("SMS banka mesajları cihazda işlenir. Belirsiz veya tamamlanmamış işlemler otomatik kaydedilmez.")
        Text("Bankalar: Yapı Kredi • İş Bankası • Akbank • Garanti BBVA • diğer tanınan bankalar")
        Text("Kaynak: SMS geçmişi ve gelen SMS")
        Text(scanMessage, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("ESKİ SMS'LERİ TEKRAR TARA") }
    }
}

@Composable
private fun ManualExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Diğer") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni harcama") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Tutar (TL)") })
                OutlinedTextField(merchant, { merchant = it }, label = { Text("İşyeri / açıklama") })
                OutlinedTextField(category, { category = it }, label = { Text("Kategori") })
                OutlinedTextField(note, { note = it }, label = { Text("Not (isteğe bağlı)") })
            }
        },
        confirmButton = {
            TextButton(onClick = { parseKurus(amount)?.let { onSave(it, merchant, category, note) } }) { Text("KAYDET") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İPTAL") } }
    )
}

@Composable
private fun EmptyState(text: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
