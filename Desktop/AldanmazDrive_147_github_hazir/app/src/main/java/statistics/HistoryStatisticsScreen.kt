package com.aldanmaz.drivedashboard.ui.screen.statistics

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseHistoryRepository
import com.aldanmaz.drivedashboard.data.fuel.FuelPreferencesRepository
import kotlinx.coroutines.flow.first
import com.aldanmaz.drivedashboard.data.fuel.FuelPurchaseRecord
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripEntity
import com.aldanmaz.drivedashboard.data.trip.VehicleOdometerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 152 FINAL: 180 günlük sürücü bazlı geçmiş + yedek/geri yükleme + doğrulanmış APK imzası
private const val HISTORY_DAYS = 180L
private val HBg = Color(0xFF020812)
private val HCard = Color(0xFF0B1929)
private val HBorder = Color(0xFF19324A)
private val HCyan = Color(0xFF4DD7FF)
private val HGreen = Color(0xFF67E88B)
private val HText = Color(0xFFF4F7FB)
private val HMuted = Color(0xFF9CB0C5)

data class HistoryDayRow(
    val dateKey: String,
    val dateLabel: String,
    val driverId: String,
    val driverName: String,
    val tripCount: Int,
    val distanceKm: Double,
    val movingSeconds: Long,
    val parkSeconds: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Int,
    val estimatedFuelLiters: Double,
    val fuelPurchaseLiters: Double,
    val fuelPurchaseTl: Double,
    val estimatedFuelTl: Double
)

data class HistoryMonthTotal(
    val monthKey: String,
    val monthLabel: String,
    val distanceKm: Double,
    val tripCount: Int,
    val estimatedFuelLiters: Double,
    val fuelPurchaseLiters: Double,
    val fuelPurchaseTl: Double,
    val estimatedFuelTl: Double,
    val movingSeconds: Long,
    val parkSeconds: Long,
    val revisionKm: Double
)

data class HistoryUiState(
    val selectedDriverId: String? = "1",
    val driverNames: List<Pair<String, String>> = listOf("1" to "Mehmet", "2" to "Nurdan"),
    val rows: List<HistoryDayRow> = emptyList(),
    val months: List<HistoryMonthTotal> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val vehicleRealKm: Double = 0.0,
    val vehicleGpsKm: Double = 0.0
)

class HistoryStatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AldanmazDriveDatabase.getInstance(application)
    private val tripDao = db.tripDao()
    private val fuelRepo = FuelPurchaseHistoryRepository(application)
    private val fuelPreferencesRepository = FuelPreferencesRepository(application)
    private val vehicleOdometerRepository = VehicleOdometerRepository(application)
    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()

    init {
        val prefs = application.getSharedPreferences("driver_profiles", Context.MODE_PRIVATE)
        _state.value = _state.value.copy(
            driverNames = listOf(
                "1" to (prefs.getString("driver_1_name", "Mehmet") ?: "Mehmet"),
                "2" to (prefs.getString("driver_2_name", "Nurdan") ?: "Nurdan")
            )
        )
        refresh()
    }

    fun selectDriver(id: String?) {
        _state.value = _state.value.copy(selectedDriverId = id)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching {
                val cutoff = cutoffMillis()
                tripDao.deleteTripsBefore(cutoff)
                fuelRepo.deleteBefore(cutoff)
                vehicleOdometerRepository.recordCurrentMonthRevision(
                    tripDao.getAllTrips().sumOf { it.distanceKm }.coerceAtLeast(0.0)
                )
                buildHistory(cutoff)
            }.onSuccess { result ->
                _state.value = _state.value.copy(rows = result.first, months = result.second, vehicleRealKm = vehicleOdometerRepository.enteredRealKm(), vehicleGpsKm = vehicleOdometerRepository.currentGpsKm(tripDao.getAllTrips().sumOf { it.distanceKm }.coerceAtLeast(0.0)), isLoading = false)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Geçmiş veriler okunamadı.")
            }
        }
    }

    private suspend fun buildHistory(cutoff: Long): Pair<List<HistoryDayRow>, List<HistoryMonthTotal>> = withContext(Dispatchers.Default) {
        val selected = _state.value.selectedDriverId
        val allGpsTotalKm = tripDao.getAllTrips().sumOf { it.distanceKm }.coerceAtLeast(0.0)
        val trips = tripDao.getAllTrips().filter { it.startedAtEpochMillis >= cutoff && (selected == null || it.driverId == selected) }
        val fuel = fuelRepo.getAll().filter { it.purchasedAtEpochMillis >= cutoff && (selected == null || it.driverId == selected) }
        val currentFuelSettings = fuelPreferencesRepository.settings.first()
        val latestRecordedFuelPricePerLiter = fuel.asSequence()
            .filter { it.liters > 0.0 && it.costTl > 0.0 }
            .maxByOrNull { it.purchasedAtEpochMillis }
            ?.let { it.costTl / it.liters }
            ?.takeIf { it > 0.0 }
        val currentFuelPricePerLiter = currentFuelSettings.lastFuelPricePerLiter.takeIf { it > 0.0 }
            ?: latestRecordedFuelPricePerLiter
            ?: if (currentFuelSettings.totalPurchasedLiters > 0.0) {
                (currentFuelSettings.totalFuelCost / currentFuelSettings.totalPurchasedLiters).takeIf { it > 0.0 }
            } else null

        val tripGroups = trips.groupBy { dateKey(it.startedAtEpochMillis) to it.driverId }
        val fuelGroups = fuel.groupBy { dateKey(it.purchasedAtEpochMillis) to it.driverId }
        val keys = (tripGroups.keys + fuelGroups.keys).distinct().sortedWith(compareByDescending<Pair<String, String>> { it.first }.thenByDescending { it.second })

        val rows = keys.map { (date, driverId) ->
            val dayTrips = tripGroups[date to driverId].orEmpty()
            val dayFuel = fuelGroups[date to driverId].orEmpty()
            val name = dayTrips.firstOrNull()?.driverName
                ?: dayFuel.firstOrNull()?.driverName
                ?: _state.value.driverNames.firstOrNull { it.first == driverId }?.second
                ?: driverId
            val moving = dayTrips.sumOf { it.movingDurationSeconds }
            HistoryDayRow(
                dateKey = date,
                dateLabel = displayDate(date),
                driverId = driverId,
                driverName = name,
                tripCount = dayTrips.size,
                distanceKm = dayTrips.sumOf { it.distanceKm },
                movingSeconds = moving,
                parkSeconds = dayTrips.sumOf { it.parkDurationSeconds },
                averageSpeedKmh = if (moving > 0) dayTrips.sumOf { it.distanceKm } * 3600.0 / moving else 0.0,
                maxSpeedKmh = dayTrips.maxOfOrNull { it.maxSpeedKmh } ?: 0,
                estimatedFuelLiters = dayTrips.sumOf { it.estimatedFuelConsumedLiters },
                fuelPurchaseLiters = dayFuel.sumOf { it.liters },
                fuelPurchaseTl = dayFuel.sumOf { it.costTl },
                estimatedFuelTl = dayTrips.sumOf { trip ->
                    val storedCost = trip.estimatedFuelCost
                    if (storedCost > 0.0) {
                        storedCost
                    } else {
                        trip.estimatedFuelConsumedLiters * (currentFuelPricePerLiter ?: 0.0)
                    }
                }
            )
        }

        val months = rows.groupBy { it.dateKey.substring(0, 7) }
            .toSortedMap(compareByDescending { it })
            .map { (month, items) ->
                HistoryMonthTotal(
                    monthKey = month,
                    monthLabel = displayMonth(month),
                    distanceKm = items.sumOf { it.distanceKm },
                    tripCount = items.sumOf { it.tripCount },
                    estimatedFuelLiters = items.sumOf { it.estimatedFuelLiters },
                    fuelPurchaseLiters = items.sumOf { it.fuelPurchaseLiters },
                    fuelPurchaseTl = items.sumOf { it.fuelPurchaseTl },
                    estimatedFuelTl = items.sumOf { it.estimatedFuelTl },
                    movingSeconds = items.sumOf { it.movingSeconds },
                    parkSeconds = items.sumOf { it.parkSeconds },
                    revisionKm = if (month == SimpleDateFormat("yyyy-MM", Locale.US).format(Date())) { vehicleOdometerRepository.enteredRealKm() - vehicleOdometerRepository.currentGpsKm(allGpsTotalKm) } else { vehicleOdometerRepository.revisionForMonth(month, allGpsTotalKm) }
                )
            }
        rows to months
    }

    fun setVehicleRealKm(value: Double) {
        viewModelScope.launch {
            val gpsTotal = tripDao.getAllTrips().sumOf { it.distanceKm }.coerceAtLeast(0.0)
            vehicleOdometerRepository.setInitialOrCurrentRealKm(value, gpsTotal)
            _state.value = _state.value.copy(
                vehicleRealKm = vehicleOdometerRepository.currentRealKm(gpsTotal),
                vehicleGpsKm = vehicleOdometerRepository.currentGpsKm(gpsTotal),
                message = "ARAÇ KM kaydedildi."
            )
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cutoff = cutoffMillis()
                val trips = tripDao.getAllTrips().filter { it.startedAtEpochMillis >= cutoff }
                val fuel = fuelRepo.getAll().filter { it.purchasedAtEpochMillis >= cutoff }
                val root = JSONObject()
                root.put("format", "AldanmazDriveHistoryBackup")
                root.put("version", 1)
                root.put("exportedAt", System.currentTimeMillis())
                root.put("historyDays", HISTORY_DAYS)
                root.put("trips", JSONArray().apply { trips.forEach { put(tripToJson(it)) } })
                root.put("fuelPurchases", JSONArray().apply { fuel.forEach { put(fuelToJson(it)) } })
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: error("Yedek dosyası açılamadı.")
            }.onSuccess {
                _state.value = _state.value.copy(message = "180 günlük yedek oluşturuldu.")
            }.onFailure {
                _state.value = _state.value.copy(error = "Yedek oluşturulamadı: ${it.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Yedek dosyası okunamadı.")
                val root = JSONObject(text)
                require(root.optString("format") == "AldanmazDriveHistoryBackup") { "Bu dosya Aldanmaz Drive yedeği değil." }
                val trips = buildList {
                    val a = root.optJSONArray("trips") ?: JSONArray()
                    for (i in 0 until a.length()) add(tripFromJson(a.getJSONObject(i)))
                }
                val fuel = buildList {
                    val a = root.optJSONArray("fuelPurchases") ?: JSONArray()
                    for (i in 0 until a.length()) add(fuelFromJson(a.getJSONObject(i)))
                }
                tripDao.deleteAllTrips()
                if (trips.isNotEmpty()) tripDao.insertTrips(trips)
                fuelRepo.replaceAll(fuel)
                tripDao.deleteTripsBefore(cutoffMillis())
                fuelRepo.deleteBefore(cutoffMillis())
            }.onSuccess {
                refresh()
                _state.value = _state.value.copy(message = "Yedek geri yüklendi.")
            }.onFailure {
                _state.value = _state.value.copy(error = "Yedek geri yüklenemedi: ${it.message}")
            }
        }
    }

    private fun cutoffMillis(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, -HISTORY_DAYS.toInt() + 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun clearMessage() { _state.value = _state.value.copy(message = null, error = null) }
}

@Composable
fun HistoryStatisticsScreen(
    onBack: () -> Unit,
    onDetailedClick: () -> Unit,
    viewModel: HistoryStatisticsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var vehicleKmText by remember(state.vehicleRealKm) { mutableStateOf(if (state.vehicleRealKm > 0) String.format(Locale.US, "%.0f", state.vehicleRealKm) else "") }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(Modifier.fillMaxSize().background(HBg)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← SÜRÜŞ", color = HCyan, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f)) {
                    Text("ALD DRIVE", color = HCyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("GEÇMİŞ • SON 180 GÜN", color = HMuted, fontSize = 11.sp)
                }
                TextButton(onClick = { viewModel.refresh() }) { Text("YENİLE", color = HCyan, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDetailedClick) { Text("DETAYLI", color = HMuted, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(6.dp))

            // Sürücü/yedek seçimleri tek ve sabit satırda kalır.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DriverButton("MEHMET", state.selectedDriverId == "1", { viewModel.selectDriver("1") }, Modifier.weight(1f))
                DriverButton("NURDAN", state.selectedDriverId == "2", { viewModel.selectDriver("2") }, Modifier.weight(1f))
                DriverButton("TÜM SÜRÜCÜ", state.selectedDriverId == null, { viewModel.selectDriver(null) }, Modifier.weight(1f))
                Button(
                    onClick = { exportLauncher.launch("AldanmazDrive_180Gun_Yedek.json") },
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HCyan.copy(alpha = .14f), contentColor = HCyan),
                    border = BorderStroke(1.dp, HCyan),
                    shape = RoundedCornerShape(9.dp)
                ) { Text("YEDEK", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(7.dp))

            // Araç KM ve ilgili kontroller ikinci satırda; KAYDET artık alanın içinde değil,
            // aynı satırda üst hizalı ayrı bir düğmedir.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = vehicleKmText,
                    onValueChange = { vehicleKmText = it.filter(Char::isDigit) },
                    label = { Text("ARAÇ KM", fontSize = 5.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(180.dp).height(58.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        color = HText,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HText,
                        unfocusedTextColor = HText,
                        cursorColor = HText,
                        focusedBorderColor = HCyan,
                        unfocusedBorderColor = HBorder,
                        focusedLabelColor = HCyan,
                        unfocusedLabelColor = HMuted
                    )
                )
                TextButton(
                    onClick = { vehicleKmText.toDoubleOrNull()?.let(viewModel::setVehicleRealKm) },
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("KAYDET", color = HGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = if (state.vehicleGpsKm > 0.0) String.format(Locale.US, "%.1f", state.vehicleGpsKm) else "0,0",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("GPS KM", fontSize = 9.sp) },
                    singleLine = true,
                    modifier = Modifier.width(112.dp).height(42.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = HText)
                )
                OutlinedTextField(
                    value = String.format(Locale("tr", "TR"), "%+.1f", state.vehicleRealKm - state.vehicleGpsKm),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("REVİZE KM", fontSize = 9.sp) },
                    singleLine = true,
                    modifier = Modifier.width(112.dp).height(42.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = HText)
                )
                Button(
                    onClick = { showRestoreWarning = true },
                    modifier = Modifier.height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HGreen.copy(alpha = .12f), contentColor = HGreen),
                    border = BorderStroke(1.dp, HGreen),
                    shape = RoundedCornerShape(9.dp)
                ) { Text("GERİ YÜKLE", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HCyan) }
            } else if (state.error != null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HCard), border = BorderStroke(1.dp, Color.Red)) {
                    Text(state.error!!, color = Color.Red, modifier = Modifier.padding(14.dp))
                }
            } else {
                HistoryTable(state)
            }
        }
    }

    val message = state.message
    if (message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            containerColor = HCard,
            title = { Text("ALD DRIVE", color = HCyan, fontWeight = FontWeight.Black) },
            text = { Text(message, color = HText) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("TAMAM", color = HCyan) } }
        )
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreWarning = false },
            containerColor = HCard,
            title = { Text("YEDEK GERİ YÜKLENSİN Mİ?", color = HCyan, fontWeight = FontWeight.Black) },
            text = { Text("Yedekteki 180 günlük yolculuk ve yakıt kayıtları mevcut geçmişin yerine alınacaktır. 180 gün dışındaki kayıtlar geri yüklenmez.", color = HText) },
            dismissButton = { TextButton(onClick = { showRestoreWarning = false }) { Text("İPTAL", color = HMuted) } },
            confirmButton = {
                Button(onClick = { showRestoreWarning = false; importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Text("GERİ YÜKLE")
                }
            }
        )
    }
}

@Composable
private fun DriverButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.height(42.dp),
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) HGreen.copy(alpha = .14f) else HCard),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HGreen else HBorder)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) HGreen else HMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryTable(state: HistoryUiState) {
    val scrollX = rememberScrollState()
    val scrollY = rememberScrollState()
    var expandedMonths by remember { mutableStateOf(emptySet<String>()) }

    val hasDriverColumn = state.selectedDriverId == null
    val dateW = 88.dp
    val driverW = if (hasDriverColumn) 100.dp else 0.dp
    val yolW = 55.dp
    val kmW = 72.dp
    val surusW = 80.dp
    val parkW = 70.dp
    val avgW = 78.dp
    val maxW = 65.dp
    val tukLtW = 65.dp
    val tukTlW = 70.dp
    val alimLW = 75.dp
    val alimTlW = 92.dp
    val tlLw = 70.dp
    val width = dateW + driverW + yolW + kmW + surusW + parkW + avgW + maxW + tukLtW + tukTlW + alimLW + alimTlW + tlLw

    fun monthTitle(month: HistoryMonthTotal): String {
        val monthName = runCatching {
            SimpleDateFormat("MMMM", Locale("tr", "TR"))
                .format(SimpleDateFormat("yyyy-MM", Locale.US).parse(month.monthKey) ?: Date())
        }.getOrDefault(month.monthLabel.substringBefore(" "))
        return monthName.replaceFirstChar { it.uppercaseChar() } + " Top"
    }

    val yearTotals = state.months
        .groupBy { it.monthKey.substring(0, 4) }
        .toSortedMap(compareByDescending { it })
        .map { (year, items) ->
            HistoryMonthTotal(
                monthKey = year,
                monthLabel = year + " Top",
                distanceKm = items.sumOf { it.distanceKm },
                tripCount = items.sumOf { it.tripCount },
                estimatedFuelLiters = items.sumOf { it.estimatedFuelLiters },
                fuelPurchaseLiters = items.sumOf { it.fuelPurchaseLiters },
                fuelPurchaseTl = items.sumOf { it.fuelPurchaseTl },
                estimatedFuelTl = items.sumOf { it.estimatedFuelTl },
                movingSeconds = items.sumOf { it.movingSeconds },
                parkSeconds = items.sumOf { it.parkSeconds },
                revisionKm = items.sumOf { it.revisionKm }
            )
        }

    Column(
        Modifier.fillMaxSize().horizontalScroll(scrollX).verticalScroll(scrollY)
    ) {
        Row(
            Modifier.width(width).background(HCard).padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCell("TARİH", dateW)
            if (hasDriverColumn) HeaderCell("SÜRÜCÜ", driverW)
            HeaderCell("YOL", yolW)
            HeaderCell("KM", kmW)
            HeaderCell("SÜRÜŞ", surusW)
            HeaderCell("PARK", parkW)
            HeaderCell("ORT.HIZ", avgW)
            HeaderCell("MAX", maxW)
            HeaderCell("TÜK LT", tukLtW)
            HeaderCellRed("TÜK TL", tukTlW)
            HeaderCell("ALIM L", alimLW)
            HeaderCell("ALIM TL", alimTlW)
            HeaderCell("TL/L", tlLw)
        }

        if (state.rows.isEmpty() && state.months.isEmpty()) {
            Text("Son 180 günde kayıt bulunmuyor.", color = HMuted, modifier = Modifier.padding(18.dp))
        } else {
            state.months.forEach { month ->
                val expanded = expandedMonths.contains(month.monthKey)

                Row(
                    Modifier.width(width).background(HCard).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            expandedMonths = if (expanded) expandedMonths - month.monthKey
                            else expandedMonths + month.monthKey
                        },
                        modifier = Modifier.width(dateW).height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            if (expanded) "- " + monthTitle(month) else "+ " + monthTitle(month),
                            color = HCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (hasDriverColumn) BodyCell("—", driverW)
                    BodyCell(month.tripCount.toString(), yolW)
                    BodyCell(month.distanceKm.one(), kmW)
                    BodyCell(duration(month.movingSeconds), surusW)
                    BodyCell(duration(month.parkSeconds), parkW)
                    BodyCell("—", avgW)
                    BodyCell("—", maxW)
                    BodyCell(month.estimatedFuelLiters.two(), tukLtW)
                    BodyCellRed(month.estimatedFuelTl.two(), tukTlW)
                    BodyCell(month.fuelPurchaseLiters.two(), alimLW)
                    BodyCell(month.fuelPurchaseTl.two(), alimTlW)
                    BodyCell(
                        if (month.fuelPurchaseLiters > 0.0) (month.fuelPurchaseTl / month.fuelPurchaseLiters).two() else "—",
                        tlLw
                    )
                }

                if (expanded) {
                    state.rows.filter { it.dateKey.startsWith(month.monthKey) }
                        .forEachIndexed { index, row ->
                            Row(
                                Modifier.width(width)
                                    .background(if (index % 2 == 0) HBg else HCard.copy(alpha = .72f))
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BodyCell(row.dateLabel, dateW)
                                if (hasDriverColumn) BodyCell(row.driverName, driverW)
                                BodyCell(row.tripCount.toString(), yolW)
                                BodyCell(row.distanceKm.one(), kmW)
                                BodyCell(duration(row.movingSeconds), surusW)
                                BodyCell(duration(row.parkSeconds), parkW)
                                BodyCell(row.averageSpeedKmh.one(), avgW)
                                BodyCell(row.maxSpeedKmh.toString(), maxW)
                                BodyCell(row.estimatedFuelLiters.two(), tukLtW)
                                BodyCellRed(row.estimatedFuelTl.two(), tukTlW)
                                BodyCell(row.fuelPurchaseLiters.two(), alimLW)
                                BodyCell(row.fuelPurchaseTl.two(), alimTlW)
                                BodyCell(
                                    if (row.fuelPurchaseLiters > 0.0) (row.fuelPurchaseTl / row.fuelPurchaseLiters).two() else "—",
                                    tlLw
                                )
                            }
                        }

                    Row(
                        Modifier.width(width).background(HBg).padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyCell("DİP TOPLAM", dateW)
                        if (hasDriverColumn) BodyCell("—", driverW)
                        BodyCell(month.tripCount.toString(), yolW)
                        BodyCell(month.distanceKm.one(), kmW)
                        BodyCell(duration(month.movingSeconds), surusW)
                        BodyCell(duration(month.parkSeconds), parkW)
                        BodyCell("", avgW)
                        BodyCell("", maxW)
                        BodyCell(month.estimatedFuelLiters.two(), tukLtW)
                        BodyCellRed(month.estimatedFuelTl.two(), tukTlW)
                        BodyCell(month.fuelPurchaseLiters.two(), alimLW)
                        BodyCell(month.fuelPurchaseTl.two(), alimTlW)
                        BodyCell(
                            if (month.fuelPurchaseLiters > 0.0) (month.fuelPurchaseTl / month.fuelPurchaseLiters).two() else "—",
                            tlLw
                        )
                    }
                    Row(
                        Modifier.width(width).background(HBg).padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyCell("REVİZE KM", 188.dp)
                        BodyCell("Gerçek KM − GPS KM", 185.dp)
                        BodyCell(
                            if (month.revisionKm == 0.0) "0,0 km" else "${month.revisionKm.one()} km",
                            150.dp
                        )
                    }
                }
            }

            yearTotals.forEach { year ->
                Row(
                    Modifier.width(width).background(HCard).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BodyCell(year.monthLabel, dateW)
                    if (hasDriverColumn) BodyCell("—", driverW)
                    BodyCell(year.tripCount.toString(), yolW)
                    BodyCell(year.distanceKm.one(), kmW)
                    BodyCell(duration(year.movingSeconds), surusW)
                    BodyCell(duration(year.parkSeconds), parkW)
                    BodyCell("—", avgW)
                    BodyCell("—", maxW)
                    BodyCell(year.estimatedFuelLiters.two(), tukLtW)
                    BodyCellRed(year.estimatedFuelTl.two(), tukTlW)
                    BodyCell(year.fuelPurchaseLiters.two(), alimLW)
                    BodyCell(year.fuelPurchaseTl.two(), alimTlW)
                    BodyCell(
                        if (year.fuelPurchaseLiters > 0.0) (year.fuelPurchaseTl / year.fuelPurchaseLiters).two() else "—",
                        tlLw
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = HCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(width).padding(horizontal = 3.dp))
}

@Composable
private fun HeaderCellRed(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(width).padding(horizontal = 3.dp))
}

@Composable
private fun BodyCellRed(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(width).padding(horizontal = 3.dp))
}

@Composable
private fun BodyCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = HText, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(width).padding(horizontal = 3.dp))
}

private fun dateKey(epoch: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epoch))
private fun displayDate(key: String): String = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key) ?: Date())
private fun displayMonth(key: String): String = SimpleDateFormat("MMMM yyyy", Locale("tr", "TR")).format(SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: Date()).uppercase(Locale("tr", "TR"))
private fun duration(seconds: Long): String = if (seconds >= 3600) String.format(Locale.US, "%d:%02d", seconds / 3600, (seconds % 3600) / 60) else String.format(Locale.US, "%d dk", seconds / 60)
private fun Double.one() = String.format(Locale("tr", "TR"), "%.1f", this)
private fun Double.two() = String.format(Locale("tr", "TR"), "%.2f", this)

private fun tripToJson(t: TripEntity) = JSONObject().apply {
    put("id", t.id); put("startedAt", t.startedAtEpochMillis); put("endedAt", t.endedAtEpochMillis)
    put("distanceKm", t.distanceKm); put("totalDurationSeconds", t.totalDurationSeconds); put("movingDurationSeconds", t.movingDurationSeconds); put("parkDurationSeconds", t.parkDurationSeconds)
    put("averageSpeedKmh", t.averageSpeedKmh); put("maxSpeedKmh", t.maxSpeedKmh); put("vehicleMode", t.vehicleMode); put("vehicleId", t.vehicleId)
    put("estimatedFuelConsumedLiters", t.estimatedFuelConsumedLiters); put("estimatedFuelCost", t.estimatedFuelCost)
    put("driverId", t.driverId); put("driverName", t.driverName)
    put("startLatitude", t.startLatitude ?: JSONObject.NULL); put("startLongitude", t.startLongitude ?: JSONObject.NULL); put("endLatitude", t.endLatitude ?: JSONObject.NULL); put("endLongitude", t.endLongitude ?: JSONObject.NULL)
    put("startAddress", t.startAddress); put("endAddress", t.endAddress); put("stopEventsJson", t.stopEventsJson)
    put("speed0To30Seconds", t.speed0To30Seconds); put("speed31To50Seconds", t.speed31To50Seconds); put("speed51To70Seconds", t.speed51To70Seconds); put("speed71To90Seconds", t.speed71To90Seconds); put("speed91To120Seconds", t.speed91To120Seconds); put("speedOver120Seconds", t.speedOver120Seconds)
}
private fun tripFromJson(o: JSONObject) = TripEntity(
    id=o.optLong("id"), startedAtEpochMillis=o.optLong("startedAt"), endedAtEpochMillis=o.optLong("endedAt"), distanceKm=o.optDouble("distanceKm"),
    totalDurationSeconds=o.optLong("totalDurationSeconds"), movingDurationSeconds=o.optLong("movingDurationSeconds"), parkDurationSeconds=o.optLong("parkDurationSeconds"),
    averageSpeedKmh=o.optDouble("averageSpeedKmh"), maxSpeedKmh=o.optInt("maxSpeedKmh"), vehicleMode=o.optString("vehicleMode"), vehicleId=o.optString("vehicleId"),
    estimatedFuelConsumedLiters=o.optDouble("estimatedFuelConsumedLiters"), estimatedFuelCost=o.optDouble("estimatedFuelCost"),
    driverId=o.optString("driverId","1"), driverName=o.optString("driverName","Mehmet"),
    startLatitude=o.optNullableDouble("startLatitude"), startLongitude=o.optNullableDouble("startLongitude"), endLatitude=o.optNullableDouble("endLatitude"), endLongitude=o.optNullableDouble("endLongitude"),
    startAddress=o.optString("startAddress"), endAddress=o.optString("endAddress"), stopEventsJson=o.optString("stopEventsJson","[]"),
    speed0To30Seconds=o.optLong("speed0To30Seconds"), speed31To50Seconds=o.optLong("speed31To50Seconds"), speed51To70Seconds=o.optLong("speed51To70Seconds"), speed71To90Seconds=o.optLong("speed71To90Seconds"), speed91To120Seconds=o.optLong("speed91To120Seconds"), speedOver120Seconds=o.optLong("speedOver120Seconds")
)
private fun fuelToJson(f: FuelPurchaseRecord) = JSONObject().apply {
    put("id", f.id); put("time", f.purchasedAtEpochMillis); put("driverId", f.driverId); put("driverName", f.driverName); put("liters", f.liters); put("costTl", f.costTl)
    put("latitude", f.latitude ?: JSONObject.NULL); put("longitude", f.longitude ?: JSONObject.NULL); put("address", f.address ?: JSONObject.NULL)
}
private fun fuelFromJson(o: JSONObject) = FuelPurchaseRecord(
    id=o.optString("id"), purchasedAtEpochMillis=o.optLong("time"), driverId=o.optString("driverId","1"), driverName=o.optString("driverName","Mehmet"),
    liters=o.optDouble("liters").coerceAtLeast(0.0), costTl=o.optDouble("costTl").coerceAtLeast(0.0),
    latitude=o.optNullableDouble("latitude"), longitude=o.optNullableDouble("longitude"), address=o.optString("address").takeIf { it.isNotBlank() }
)
private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }
