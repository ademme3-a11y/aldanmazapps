package com.aldanmaz.drivedashboard.ui.screen.fuel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseRecord
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FuelBackground = Color(0xFF020812)
private val FuelCard = Color(0xFF0B1929)
private val FuelCardBorder = Color(0xFF19324A)
private val FuelCyan = Color(0xFF4DD7FF)
private val FuelGreen = Color(0xFF67E88B)
private val FuelOrange = Color(0xFFFFB74D)
private val FuelRed = Color(0xFFFF6B6B)
private val FuelPrimaryText = Color(0xFFF4F7FB)
private val FuelSecondaryText = Color(0xFF9CB0C5)

@Composable
fun FuelScreen(
    uiState: FuelUiState,
    fuelDataSource: String = "HESAP",
    externalFuelPercentage: Double? = null,
    externalRemainingFuelLiters: Double? = null,
    externalEstimatedRangeKm: Double? = null,
    externalIsLowFuel: Boolean? = null,
    onBack: () -> Unit,
    onTankCapacityChanged: (String) -> Unit,
    onStartingFuelChanged: (String) -> Unit,
    onAverageConsumptionChanged: (String) -> Unit,
    onLowFuelThresholdChanged: (String) -> Unit,
    onFuelTypeSelected: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onShowRefuelDialog: () -> Unit,
    onHideRefuelDialog: () -> Unit,
    onRefuelLitersChanged: (String) -> Unit,
    onRefuelCostChanged: (String) -> Unit,
    onFillTankChanged: (Boolean) -> Unit,
    onSaveRefuel: () -> Unit,
    onRefreshShellFuelPrice: () -> Unit,
    onResetStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FuelBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            FuelHeader(onBack = onBack, fuelDataSource = fuelDataSource)
            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = FuelCyan)
                }
            } else {
                MonthlyFuelStatisticsPanel(uiState = uiState)
                Spacer(modifier = Modifier.height(12.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val wideLayout = maxWidth >= 720.dp

                    if (wideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            FuelSummaryPanel(
                                uiState = uiState,
                                fuelDataSource = fuelDataSource,
                                externalFuelPercentage = externalFuelPercentage,
                                externalRemainingFuelLiters = externalRemainingFuelLiters,
                                externalEstimatedRangeKm = externalEstimatedRangeKm,
                                externalIsLowFuel = externalIsLowFuel,
                                onShowRefuelDialog = onShowRefuelDialog,
                                modifier = Modifier.weight(1.05f),
                            )
                            FuelSettingsPanel(
                                uiState = uiState,
                                onTankCapacityChanged = onTankCapacityChanged,
                                onStartingFuelChanged = onStartingFuelChanged,
                                onAverageConsumptionChanged = onAverageConsumptionChanged,
                                onLowFuelThresholdChanged = onLowFuelThresholdChanged,
                                onFuelTypeSelected = onFuelTypeSelected,
                                onSaveSettings = onSaveSettings,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FuelSummaryPanel(
                                uiState = uiState,
                                fuelDataSource = fuelDataSource,
                                externalFuelPercentage = externalFuelPercentage,
                                externalRemainingFuelLiters = externalRemainingFuelLiters,
                                externalEstimatedRangeKm = externalEstimatedRangeKm,
                                externalIsLowFuel = externalIsLowFuel,
                                onShowRefuelDialog = onShowRefuelDialog,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FuelSettingsPanel(
                                uiState = uiState,
                                onTankCapacityChanged = onTankCapacityChanged,
                                onStartingFuelChanged = onStartingFuelChanged,
                                onAverageConsumptionChanged = onAverageConsumptionChanged,
                                onLowFuelThresholdChanged = onLowFuelThresholdChanged,
                                onFuelTypeSelected = onFuelTypeSelected,
                                onSaveSettings = onSaveSettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (!uiState.isLoading) {
                FuelPurchaseHistoryPanel(uiState.purchaseHistory)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { showResetConfirmation = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FuelRed.copy(alpha = .18f),
                        contentColor = FuelRed
                    ),
                    border = BorderStroke(1.dp, FuelRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("YAKIT İSTATİSTİĞİNİ SIFIRLA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (uiState.showRefuelDialog) {
        RefuelDialog(
            uiState = uiState,
            onDismiss = onHideRefuelDialog,
            onLitersChanged = onRefuelLitersChanged,
            onCostChanged = onRefuelCostChanged,
            onFillTankChanged = onFillTankChanged,
            onSave = onSaveRefuel,
            onRefreshShellFuelPrice = onRefreshShellFuelPrice,
        )
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            containerColor = FuelCard,
            title = {
                Text("YAKIT İSTATİSTİKLERİ SIFIRLANSIN MI?", color = FuelPrimaryText, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "Yakıt ve yolculuk istatistikleri, günlük KM/LT/TL değerleri ve yakıt alım geçmişi tamamen sıfırlanacak. Depo seviyesi, depo kapasitesi, yakıt tipi ve tüketim ayarları korunacak.",
                    color = FuelSecondaryText
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("İPTAL", color = FuelSecondaryText)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        onResetStatistics()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FuelRed)
                ) {
                    Text("EVET, SIFIRLA", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun MonthlyFuelStatisticsPanel(uiState: FuelUiState) {
    val zone = remember { ZoneId.systemDefault() }
    val currentMonth = remember { YearMonth.now(zone) }
    val monthlyPurchases = uiState.purchaseHistory.filter { record ->
        YearMonth.from(
            Instant.ofEpochMilli(record.purchasedAtEpochMillis).atZone(zone)
        ) == currentMonth
    }
    val purchasedLiters = monthlyPurchases.sumOf { it.liters }
    val purchaseCost = monthlyPurchases.sumOf { it.costTl }
    val monthName = currentMonth.format(
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale("tr", "TR"))
    ).uppercase(Locale("tr", "TR"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FuelCard),
        border = BorderStroke(1.dp, FuelCyan),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BU AY YAKIT İSTATİSTİĞİ",
                    color = FuelCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    monthName,
                    color = FuelSecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthlyFuelMetric(
                    label = "AY KM",
                    value = "${uiState.monthlyDistanceKm.oneDecimal()} km",
                    color = FuelPrimaryText,
                    modifier = Modifier.weight(1f),
                )
                MonthlyFuelMetric(
                    label = "TÜKETİLEN",
                    value = "${uiState.monthlyConsumedFuelLiters.oneDecimal()} L",
                    color = FuelOrange,
                    modifier = Modifier.weight(1f),
                )
                MonthlyFuelMetric(
                    label = "TAHMİNİ MALİYET",
                    value = "${uiState.monthlyEstimatedFuelCost.twoDecimals()} ₺",
                    color = FuelOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthlyFuelMetric(
                    label = "ALINAN YAKIT",
                    value = "${purchasedLiters.oneDecimal()} L",
                    color = FuelGreen,
                    modifier = Modifier.weight(1f),
                )
                MonthlyFuelMetric(
                    label = "ÖDENEN",
                    value = "${purchaseCost.twoDecimals()} ₺",
                    color = FuelGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonthlyFuelMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(FuelBackground, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = FuelSecondaryText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FuelPurchaseHistoryPanel(records: List<FuelPurchaseRecord>) {
    val context = LocalContext.current
    var selectedDriverId by remember { mutableStateOf<String?>(null) }
    var expandedMonth by remember { mutableStateOf<YearMonth?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val trLocale = remember { Locale("tr", "TR") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", trLocale) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm", trLocale) }
    val visibleRecords = records.filter { selectedDriverId == null || it.driverId == selectedDriverId }
    val byYear = visibleRecords.groupBy {
        Instant.ofEpochMilli(it.purchasedAtEpochMillis).atZone(zone).year
    }.toSortedMap(compareByDescending { it })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FuelCard),
        border = BorderStroke(1.dp, FuelCardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("YAKIT ALIM GEÇMİŞİ", color = FuelCyan, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelHistoryFilter("TÜMÜ", selectedDriverId == null, Modifier.weight(1f)) {
                    selectedDriverId = null
                    expandedMonth = null
                }
                listOf("1" to "MEHMET", "2" to "NURDAN").forEach { (id, label) ->
                    FuelHistoryFilter(label, selectedDriverId == id, Modifier.weight(1f)) {
                        selectedDriverId = id
                        expandedMonth = null
                    }
                }
            }

            if (visibleRecords.isEmpty()) {
                Text("Henüz yakıt alım kaydı yok.", color = FuelSecondaryText, fontSize = 12.sp)
            } else {
                byYear.forEach { (year, yearRecords) ->
                    val yearLiters = yearRecords.sumOf { it.liters }
                    val yearCost = yearRecords.sumOf { it.costTl }
                    Text(
                        "$year YILI • ${yearLiters.twoDecimals()} L • ${yearCost.twoDecimals()} ₺",
                        color = FuelOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    yearRecords.groupBy {
                        YearMonth.from(Instant.ofEpochMilli(it.purchasedAtEpochMillis).atZone(zone))
                    }.toSortedMap(compareByDescending { it }).forEach { (month, monthRecords) ->
                        val monthLiters = monthRecords.sumOf { it.liters }
                        val monthCost = monthRecords.sumOf { it.costTl }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedMonth = if (expandedMonth == month) null else month
                            },
                            colors = CardDefaults.cardColors(containerColor = FuelBackground),
                            border = BorderStroke(
                                1.dp,
                                if (expandedMonth == month) FuelCyan else FuelCardBorder,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        month.format(monthFormatter).uppercase(trLocale),
                                        color = FuelPrimaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${monthLiters.twoDecimals()} L • ${monthCost.twoDecimals()} ₺",
                                        color = FuelGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (expandedMonth == month) {
                                    monthRecords.sortedByDescending { it.purchasedAtEpochMillis }.forEach { record ->
                                        FuelPurchaseHistoryRow(record, zone, dateFormatter) {
                                            openFuelPurchaseLocation(
                                                context,
                                                requireNotNull(record.latitude),
                                                requireNotNull(record.longitude),
                                            )
                                        }
                                    }
                                    Text(
                                        "AY TOPLAMI • ${monthLiters.twoDecimals()} L • ${monthCost.twoDecimals()} ₺",
                                        color = FuelCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelPurchaseHistoryRow(
    record: FuelPurchaseRecord,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter,
    onOpenMap: () -> Unit,
) {
    val date = Instant.ofEpochMilli(record.purchasedAtEpochMillis).atZone(zone).format(dateFormatter)
    val hasLocation = record.latitude != null && record.longitude != null
    Column(
        Modifier.fillMaxWidth().background(FuelCard, RoundedCornerShape(9.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "$date • ${record.driverName}",
            color = FuelPrimaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${record.liters.twoDecimals()} L • ${record.costTl.twoDecimals()} ₺ • ${record.pricePerLiter.twoDecimals()} ₺/L",
            color = FuelOrange,
            fontSize = 11.sp,
        )
        val locationText = when {
            !hasLocation -> "📍 Konum alınamadı"
            !record.address.isNullOrBlank() -> "📍 ${record.address} • HARİTADA AÇ"
            else -> "📍 ${String.format(Locale.US, "%.5f, %.5f", record.latitude, record.longitude)} • HARİTADA AÇ"
        }
        Text(
            text = locationText,
            color = if (hasLocation) FuelCyan else FuelSecondaryText,
            fontSize = 10.sp,
            modifier = Modifier.clickable(enabled = hasLocation, onClick = onOpenMap),
        )
    }
}

@Composable
private fun FuelHistoryFilter(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(38.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) FuelCyan.copy(alpha = .18f) else FuelBackground,
        ),
        border = BorderStroke(1.dp, if (selected) FuelCyan else FuelCardBorder),
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) FuelCyan else FuelSecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun openFuelPurchaseLocation(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
) {
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    val googleIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    runCatching { context.startActivity(googleIntent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

@Composable
private fun FuelHeader(onBack: () -> Unit, fuelDataSource: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "← SÜRÜŞ",
                color = FuelCyan,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ALD DRIVE",
                color = FuelCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Yakıt yönetimi ve tahmini menzil",
                color = FuelSecondaryText,
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "YAKIT",
                color = FuelPrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "KAYNAK: $fuelDataSource",
                color = if (fuelDataSource == "OBD") FuelGreen else FuelOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun FuelSummaryPanel(
    uiState: FuelUiState,
    fuelDataSource: String,
    externalFuelPercentage: Double?,
    externalRemainingFuelLiters: Double?,
    externalEstimatedRangeKm: Double?,
    externalIsLowFuel: Boolean?,
    onShowRefuelDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useObd = fuelDataSource == "OBD" && externalFuelPercentage != null
    val effectiveFuelPercentage = if (useObd) externalFuelPercentage!!.coerceIn(0.0, 100.0) else uiState.fuelPercentage
    val effectiveFuelLiters = if (useObd) externalRemainingFuelLiters ?: uiState.currentFuelLiters else uiState.currentFuelLiters
    val effectiveRangeKm = if (useObd) externalEstimatedRangeKm ?: uiState.estimatedRangeKm else uiState.estimatedRangeKm
    val effectiveLowFuel = if (useObd) externalIsLowFuel ?: (effectiveFuelPercentage <= 18.0) else uiState.isLowFuel
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FuelCard),
        border = BorderStroke(1.dp, FuelCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (uiState.isConfigured) "YAKIT DURUMU" else "İLK YAKIT AYARI",
                color = FuelCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!uiState.isConfigured) {
                Text(
                    text = "Hesaplamayı başlatmak için depo kapasitesini, başlangıç yakıtını ve ortalama tüketimi girin.",
                    color = FuelPrimaryText,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Girilen değerler telefonda kalıcı olarak saklanır.",
                    color = FuelSecondaryText,
                    fontSize = 12.sp,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelMetricCard(
                    label = "KALAN YAKIT",
                    value = "${effectiveFuelLiters.oneDecimal()} L",
                    valueColor = if (effectiveLowFuel) FuelRed else FuelGreen,
                    modifier = Modifier.weight(1f),
                )
                FuelMetricCard(
                    label = "TAHMİNİ MENZİL",
                    value = "${effectiveRangeKm.zeroDecimal()} km",
                    valueColor = FuelCyan,
                    modifier = Modifier.weight(1f),
                )
                FuelMetricCard(
                    label = "TÜKETİLEN",
                    value = "${uiState.todayConsumedFuelLiters.oneDecimal()} L",
                    valueColor = FuelOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Depo seviyesi",
                    color = FuelSecondaryText,
                    fontSize = 12.sp,
                )
                Text(
                    text = "%${effectiveFuelPercentage.zeroDecimal()}",
                    color = if (effectiveLowFuel) FuelRed else FuelGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(FuelCardBorder, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth((effectiveFuelPercentage / 100.0).toFloat())
                            .height(12.dp)
                            .background(
                                color = if (effectiveLowFuel) FuelRed else FuelGreen,
                                shape = RoundedCornerShape(50),
                            ),
                )
            }

            if (effectiveLowFuel) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Düşük yakıt: Yakıt almanız önerilir.",
                    color = FuelRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "TOPLAM YAKIT HARCAMASI",
                        color = FuelSecondaryText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${uiState.totalFuelCost.twoDecimals()} ₺",
                        color = FuelPrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                Button(
                    onClick = onShowRefuelDialog,
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = FuelCyan,
                            contentColor = FuelBackground,
                            disabledContainerColor = FuelCardBorder,
                            disabledContentColor = FuelSecondaryText,
                        ),
                ) {
                    Text(
                        text = "YAKIT ALDIM",
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = FuelBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = FuelSecondaryText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FuelSettingsPanel(
    uiState: FuelUiState,
    onTankCapacityChanged: (String) -> Unit,
    onStartingFuelChanged: (String) -> Unit,
    onAverageConsumptionChanged: (String) -> Unit,
    onLowFuelThresholdChanged: (String) -> Unit,
    onFuelTypeSelected: (String) -> Unit,
    onSaveSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FuelCard),
        border = BorderStroke(1.dp, FuelCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (uiState.isConfigured) "YAKIT AYARLARINI GÜNCELLE" else "YAKIT BİLGİLERİNİ GİR",
                color = FuelCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelInputField(
                    value = uiState.tankCapacityText,
                    onValueChange = onTankCapacityChanged,
                    label = "Depo kapasitesi (L)",
                    modifier = Modifier.weight(1f),
                )
                FuelInputField(
                    value = uiState.startingFuelText,
                    onValueChange = onStartingFuelChanged,
                    label = "Mevcut yakıt (L)",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelInputField(
                    value = uiState.averageConsumptionText,
                    onValueChange = onAverageConsumptionChanged,
                    label = "Ortalama (L/100 km)",
                    modifier = Modifier.weight(1f),
                )
                FuelInputField(
                    value = uiState.lowFuelThresholdText,
                    onValueChange = onLowFuelThresholdChanged,
                    label = "Düşük yakıt sınırı (L)",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "YAKIT TİPİ",
                color = FuelSecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Benzin", "Dizel", "Gaz").forEach { type ->
                    val selected = uiState.fuelType == type
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable(enabled = !uiState.isSaving) { onFuelTypeSelected(type) },
                        shape = RoundedCornerShape(11.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) FuelCyan.copy(alpha = .16f) else FuelBackground
                        ),
                        border = BorderStroke(1.dp, if (selected) FuelCyan else FuelCardBorder),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                type.uppercase(Locale("tr", "TR")),
                                color = if (selected) FuelCyan else FuelSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = FuelRed,
                    fontSize = 12.sp,
                )
            }

            uiState.confirmationMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = FuelGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSaveSettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = uiState.canSaveInitialSettings,
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = FuelGreen,
                        contentColor = FuelBackground,
                        disabledContainerColor = FuelCardBorder,
                        disabledContentColor = FuelSecondaryText,
                    ),
            ) {
                Text(
                    text =
                        when {
                            uiState.isSaving -> "KAYDEDİLİYOR..."
                            uiState.isConfigured -> "AYARLARI GÜNCELLE"
                            else -> "YAKIT HESABINI BAŞLAT"
                        },
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun RefuelDialog(
    uiState: FuelUiState,
    onDismiss: () -> Unit,
    onLitersChanged: (String) -> Unit,
    onCostChanged: (String) -> Unit,
    onFillTankChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRefreshShellFuelPrice: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelCard,
        titleContentColor = FuelPrimaryText,
        textContentColor = FuelPrimaryText,
        title = {
            Text(
                text = "YAKIT ALIMI",
                color = FuelCyan,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Aldığınız yakıtı ve ödediğiniz toplam ücreti girin.",
                    color = FuelSecondaryText,
                    fontSize = 12.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FuelBackground),
                    border = BorderStroke(1.dp, FuelCyan),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isLoadingShellFuelPrice) "SHELL FİYATI ALINIYOR..."
                                else if (uiState.shellFuelPricePerLiter != null) "SHELL ${uiState.shellFuelPriceCity ?: ""} • ${uiState.shellFuelPricePerLiter.twoDecimals()} ₺/L"
                                else "SHELL FİYATI ALINAMADI",
                                color = FuelCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            )
                            uiState.shellFuelPriceUpdatedDate?.let {
                                Text("Güncelleme: $it", color = FuelSecondaryText, fontSize = 9.sp)
                            }
                            uiState.shellFuelPriceError?.let {
                                Text(it, color = FuelRed, fontSize = 9.sp)
                            }
                        }
                        TextButton(onClick = onRefreshShellFuelPrice, enabled = !uiState.isLoadingShellFuelPrice && !uiState.isSaving) {
                            Text("YENİLE", color = FuelCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FuelInputField(
                    value = uiState.refuelLitersText,
                    onValueChange = onLitersChanged,
                    label = "Alınan yakıt (L)",
                    enabled = !uiState.fillTank,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                FuelInputField(
                    value = uiState.refuelCostText,
                    onValueChange = onCostChanged,
                    label = if (uiState.shellFuelPricePerLiter != null) "Toplam ücret (Shell otomatik)" else "Toplam ücret (₺)",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.shellFuelPricePerLiter == null && !uiState.isSaving,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isSaving) {
                                onFillTankChanged(!uiState.fillTank)
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = uiState.fillTank,
                        onCheckedChange = onFillTankChanged,
                        enabled = !uiState.isSaving,
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = FuelCyan,
                                uncheckedColor = FuelSecondaryText,
                                checkmarkColor = FuelBackground,
                            ),
                    )
                    Text(
                        text = "Depoyu doldurdum",
                        color = FuelPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (uiState.fillTank) {
                    Text(
                        text = "Depo seviyesi otomatik olarak %100 yapılacak.",
                        color = FuelSecondaryText,
                        fontSize = 11.sp,
                    )
                }

                uiState.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = FuelRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !uiState.isSaving,
            ) {
                Text(
                    text = "İPTAL",
                    color = FuelSecondaryText,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = uiState.canSaveRefuel,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = FuelGreen,
                        contentColor = FuelBackground,
                        disabledContainerColor = FuelCardBorder,
                        disabledContentColor = FuelSecondaryText,
                    ),
            ) {
                Text(
                    text = if (uiState.isSaving) "KAYDEDİLİYOR..." else "KAYDET",
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
    )
}

@Composable
private fun FuelInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = FuelPrimaryText,
                unfocusedTextColor = FuelPrimaryText,
                disabledTextColor = FuelSecondaryText,
                focusedBorderColor = FuelCyan,
                unfocusedBorderColor = FuelCardBorder,
                disabledBorderColor = FuelCardBorder,
                focusedLabelColor = FuelCyan,
                unfocusedLabelColor = FuelSecondaryText,
                disabledLabelColor = FuelSecondaryText,
                cursorColor = FuelCyan,
            ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
    )
}

private fun Double.oneDecimal(): String =
    String.format(Locale("tr", "TR"), "%.1f", this)

private fun Double.zeroDecimal(): String =
    String.format(Locale("tr", "TR"), "%.0f", this)

private fun Double.twoDecimals(): String =
    String.format(Locale("tr", "TR"), "%.2f", this)
