package com.aldanmaz.drivedashboard.ui.screen.statistics

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.aldanmaz.drivedashboard.data.trip.TripStatisticsPeriod
import com.aldanmaz.drivedashboard.ui.screen.driver.DriverAvatar
import com.aldanmaz.drivedashboard.ui.screen.driver.loadDrivers
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlin.math.roundToLong
import org.json.JSONArray

private val StatisticsBackground = Color(0xFF020812)
private val StatisticsCard = Color(0xFF0B1929)
private val StatisticsCardBorder = Color(0xFF19324A)
private val StatisticsCyan = Color(0xFF4DD7FF)
private val StatisticsGreen = Color(0xFF67E88B)
private val StatisticsOrange = Color(0xFFFFB74D)
private val StatisticsRed = Color(0xFFFF6B6B)
private val StatisticsPrimaryText = Color(0xFFF4F7FB)
private val StatisticsSecondaryText = Color(0xFF9CB0C5)

@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onBack: () -> Unit,
    onSpeedCorridorClick: () -> Unit,
    onPeriodSelected: (TripStatisticsPeriod) -> Unit,
    onVehicleModeSelected: (String?) -> Unit,
    onDriverSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onResetStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showResetConfirmation by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = StatisticsBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            StatisticsHeader(
                onBack = onBack,
                onRefresh = onRefresh
            )

            Spacer(modifier = Modifier.height(10.dp))

            PeriodSelector(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = onPeriodSelected
            )

            Spacer(modifier = Modifier.height(8.dp))

            VehicleModeSelector(
                selectedVehicleMode = uiState.selectedVehicleMode,
                onVehicleModeSelected = onVehicleModeSelected
            )

            Spacer(modifier = Modifier.height(8.dp))
            DriverSelector(uiState.selectedDriverId, uiState.driverNames, onDriverSelected)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onSpeedCorridorClick),
                colors = CardDefaults.cardColors(containerColor = StatisticsCyan.copy(alpha = 0.13f)),
                border = BorderStroke(1.dp, StatisticsCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("HIZ KORİDORU  ›", color = StatisticsCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = StatisticsCyan
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    ErrorCard(
                        message = uiState.errorMessage
                    )
                }

                else -> {
                    StatisticsContent(uiState = uiState)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { showResetConfirmation = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatisticsRed.copy(alpha = .18f),
                        contentColor = StatisticsRed
                    ),
                    border = BorderStroke(1.dp, StatisticsRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("İSTATİSTİKLERİ SIFIRLA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            containerColor = StatisticsCard,
            title = {
                Text("İSTATİSTİKLER SIFIRLANSIN MI?", color = StatisticsPrimaryText, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "Yakıt ve yolculuk istatistikleri ile günlük KM/LT/TL değerleri tamamen sıfırlanacak. Depo ve tüketim ayarları korunacak. Bu işlem geri alınamaz.",
                    color = StatisticsSecondaryText
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("İPTAL", color = StatisticsSecondaryText)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        onResetStatistics()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatisticsRed)
                ) {
                    Text("EVET, SIFIRLA", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun StatisticsHeader(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "← SÜRÜŞ",
                color = StatisticsCyan,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ALD DRIVE",
                color = StatisticsCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Yolculuk ve sürüş istatistikleri",
                color = StatisticsSecondaryText,
                fontSize = 11.sp
            )
        }

        TextButton(onClick = onRefresh) {
            Text(
                text = "YENİLE",
                color = StatisticsCyan,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "İSTATİSTİK",
            color = StatisticsPrimaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: TripStatisticsPeriod,
    onPeriodSelected: (TripStatisticsPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PeriodButton(
            text = "GÜN",
            selected = selectedPeriod == TripStatisticsPeriod.DAY,
            onClick = { onPeriodSelected(TripStatisticsPeriod.DAY) },
            modifier = Modifier.weight(1f)
        )
        PeriodButton(
            text = "HAFTA",
            selected = selectedPeriod == TripStatisticsPeriod.WEEK,
            onClick = { onPeriodSelected(TripStatisticsPeriod.WEEK) },
            modifier = Modifier.weight(1f)
        )
        PeriodButton(
            text = "AY",
            selected = selectedPeriod == TripStatisticsPeriod.MONTH,
            onClick = { onPeriodSelected(TripStatisticsPeriod.MONTH) },
            modifier = Modifier.weight(1f)
        )
        PeriodButton(
            text = "YIL",
            selected = selectedPeriod == TripStatisticsPeriod.YEAR,
            onClick = { onPeriodSelected(TripStatisticsPeriod.YEAR) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PeriodButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected) {
                    StatisticsCyan.copy(alpha = 0.16f)
                } else {
                    StatisticsCard
                }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color =
                if (selected) {
                    StatisticsCyan
                } else {
                    StatisticsCardBorder
                }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color =
                    if (selected) {
                        StatisticsCyan
                    } else {
                        StatisticsSecondaryText
                    },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VehicleModeSelector(
    selectedVehicleMode: String?,
    onVehicleModeSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VehicleFilterButton(
            text = "TÜMÜ",
            selected = selectedVehicleMode == null,
            onClick = { onVehicleModeSelected(null) },
            modifier = Modifier.weight(1f)
        )
        VehicleFilterButton(
            text = "OTOMOBİL",
            selected = selectedVehicleMode == "Otomobil",
            onClick = { onVehicleModeSelected("Otomobil") },
            modifier = Modifier.weight(1f)
        )
        VehicleFilterButton(
            text = "KARAVAN",
            selected = selectedVehicleMode == "Karavan",
            onClick = { onVehicleModeSelected("Karavan") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DriverSelector(selected: String?, drivers: List<Pair<String, String>>, onSelected: (String?) -> Unit) {
    val context = LocalContext.current
    val profiles = remember(drivers) { loadDrivers(context) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleFilterButton("TÜM SÜRÜCÜLER", selected == null, { onSelected(null) }, Modifier.weight(1f))
        drivers.forEach { (id, name) ->
            val profile = profiles.firstOrNull { it.id == id }
            Card(
                modifier = Modifier.weight(1f).height(76.dp).clickable { onSelected(id) },
                shape = RoundedCornerShape(11.dp),
                colors = CardDefaults.cardColors(containerColor = if (selected == id) StatisticsGreen.copy(alpha = .12f) else StatisticsCard),
                border = BorderStroke(1.dp, if (selected == id) StatisticsGreen else StatisticsCardBorder)
            ) {
                Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DriverAvatar(profile?.photoUri, name, Modifier.size(58.dp))
                    Text(name.uppercase(Locale("tr", "TR")), color = if (selected == id) StatisticsGreen else StatisticsSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VehicleFilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected) {
                    StatisticsGreen.copy(alpha = 0.12f)
                } else {
                    StatisticsCard
                }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) {
                StatisticsGreen
            } else {
                StatisticsCardBorder
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color =
                    if (selected) {
                        StatisticsGreen
                    } else {
                        StatisticsSecondaryText
                    },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

}

@Composable
private fun StatisticsContent(
    uiState: StatisticsUiState
) {
    val context = LocalContext.current
    val summary = uiState.summary
    var expandedTripId by remember { mutableStateOf<Long?>(null) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val wideLayout = maxWidth >= 720.dp

        if (wideLayout) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "TOPLAM MESAFE",
                        value = "${summary.totalDistanceKm.oneDecimal()} km",
                        valueColor = StatisticsCyan,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "YOLCULUK",
                        value = summary.tripCount.toString(),
                        valueColor = StatisticsGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "ORT. HIZ",
                        value = "${summary.averageSpeedKmh.oneDecimal()} km/s",
                        valueColor = StatisticsOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "EN YÜKSEK HIZ",
                        value = "${summary.maxSpeedKmh} km/s",
                        valueColor = StatisticsRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "TOPLAM SÜRE",
                        value = summary.totalDurationSeconds.durationText(),
                        valueColor = StatisticsPrimaryText,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "HAREKET SÜRESİ",
                        value = summary.movingDurationSeconds.durationText(),
                        valueColor = StatisticsGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "PARK SÜRESİ",
                        value = summary.parkDurationSeconds.durationText(),
                        valueColor = StatisticsOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "TAHMİNİ YAKIT",
                        value = "${summary.estimatedFuelConsumedLiters.twoDecimals()} L",
                        valueColor = StatisticsCyan,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "TAHMİNİ TL",
                        value = "${summary.estimatedFuelCost.twoDecimals()} TL",
                        valueColor = StatisticsOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("TOPLAM MESAFE", "${summary.totalDistanceKm.oneDecimal()} km", StatisticsCyan),
                    Triple("YOLCULUK", summary.tripCount.toString(), StatisticsGreen),
                    Triple("ORT. HIZ", "${summary.averageSpeedKmh.oneDecimal()} km/s", StatisticsOrange),
                    Triple("EN YÜKSEK HIZ", "${summary.maxSpeedKmh} km/s", StatisticsRed),
                    Triple("TOPLAM SÜRE", summary.totalDurationSeconds.durationText(), StatisticsPrimaryText),
                    Triple("HAREKET SÜRESİ", summary.movingDurationSeconds.durationText(), StatisticsGreen),
                    Triple("PARK SÜRESİ", summary.parkDurationSeconds.durationText(), StatisticsOrange),
                    Triple("TAHMİNİ YAKIT", "${summary.estimatedFuelConsumedLiters.twoDecimals()} L", StatisticsCyan),
                    Triple("TAHMİNİ TL", "${summary.estimatedFuelCost.twoDecimals()} TL", StatisticsOrange)
                ).forEach { item ->
                    MetricCard(
                        label = item.first,
                        value = item.second,
                        valueColor = item.third,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatisticsCard
        ),
        border = BorderStroke(
            1.dp,
            StatisticsCardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "HIZ ARALIKLARI",
                color = StatisticsPrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text("5–30 km/s  •  ${summary.speed0To30Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text("31–50 km/s •  ${summary.speed31To50Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text("51–70 km/s •  ${summary.speed51To70Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text("71–90 km/s •  ${summary.speed71To90Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text("91–120 km/s • ${summary.speed91To120Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text(">120 km/s  •  ${summary.speedOver120Seconds.durationText()}", color = StatisticsSecondaryText, fontSize = 11.sp)
            Text(
                text = "Hız aralıkları ve maliyet yeni tamamlanan yolculuklardan itibaren birikir.",
                color = StatisticsSecondaryText.copy(alpha = 0.75f),
                fontSize = 9.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text("SÜRÜŞ KAYITLARI", color = StatisticsPrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))
    if (uiState.trips.isEmpty()) {
        Text("Bu dönem için kayıt yok.", color = StatisticsSecondaryText, fontSize = 11.sp)
    } else uiState.trips.forEach { trip ->
        Card(
            Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable {
                expandedTripId = if (expandedTripId == trip.id) null else trip.id
            },
            colors = CardDefaults.cardColors(containerColor = StatisticsCard),
            border = BorderStroke(1.dp, StatisticsCardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val date = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("tr", "TR")).format(Date(trip.startedAtEpochMillis))
                val end = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(trip.endedAtEpochMillis))
                Text("${trip.driverName}  •  $date–$end  ›", color = StatisticsCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${trip.distanceKm.oneDecimal()} km  •  ${trip.totalDurationSeconds.durationText()}  •  ${trip.estimatedFuelConsumedLiters.twoDecimals()} L  •  ${trip.estimatedFuelCost.twoDecimals()} TL", color = StatisticsPrimaryText, fontSize = 11.sp)
                Text("Ort. ${trip.averageSpeedKmh.oneDecimal()} km/s  •  En yüksek ${trip.maxSpeedKmh} km/s", color = StatisticsOrange, fontSize = 10.sp)
                Text(
                    "${tripLocationText(trip.startAddress, trip.startLatitude, trip.startLongitude)}  →  ${tripLocationText(trip.endAddress, trip.endLatitude, trip.endLongitude)}",
                    color = StatisticsSecondaryText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (expandedTripId == trip.id) {
                    Spacer(Modifier.height(5.dp))
                    Text("BAŞLANGIÇ • ${fullDateTime(trip.startedAtEpochMillis)}", color = StatisticsGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val startLatitude = trip.startLatitude
                    val startLongitude = trip.startLongitude
                    Text(
                        "📍 ${tripLocationText(trip.startAddress, trip.startLatitude, trip.startLongitude)}",
                        color = StatisticsCyan, fontSize = 10.sp,
                        modifier = Modifier.clickable(enabled = startLatitude != null && startLongitude != null) {
                            openMapPoint(context, startLatitude, startLongitude)
                        }
                    )
                    val stops = parseTripStops(trip.stopEventsJson)
                    stops.forEachIndexed { index, stop ->
                        val stopLatitude = stop.latitude
                        val stopLongitude = stop.longitude
                        Text("DURUŞ ${index + 1} • ${fullDateTime(stop.startedAt)}–${timeOnly(stop.endedAt)}", color = StatisticsOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "📍 ${stop.address.ifBlank { tripLocationText("", stop.latitude, stop.longitude) }}",
                            color = StatisticsCyan, fontSize = 10.sp,
                            modifier = Modifier.clickable(enabled = stopLatitude != null && stopLongitude != null) {
                                openMapPoint(context, stopLatitude, stopLongitude)
                            }
                        )
                    }
                    Text("BİTİŞ • ${fullDateTime(trip.endedAtEpochMillis)}", color = StatisticsRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val endLatitude = trip.endLatitude
                    val endLongitude = trip.endLongitude
                    Text(
                        "📍 ${tripLocationText(trip.endAddress, trip.endLatitude, trip.endLongitude)}",
                        color = StatisticsCyan, fontSize = 10.sp,
                        modifier = Modifier.clickable(enabled = endLatitude != null && endLongitude != null) {
                            openMapPoint(context, endLatitude, endLongitude)
                        }
                    )
                    val routeStartLat = startLatitude
                    val routeStartLon = startLongitude
                    val routeEndLat = endLatitude
                    val routeEndLon = endLongitude
                    if (routeStartLat != null && routeStartLon != null && routeEndLat != null && routeEndLon != null) {
                        Button(
                            onClick = { openTripRoute(context, routeStartLat, routeStartLon, routeEndLat, routeEndLon, stops) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12445A)),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) { Text("ROTAYI GOOGLE HARİTALAR'DA GÖSTER", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Text("Ayrıntılar için dokunun", color = StatisticsSecondaryText, fontSize = 9.sp)
                }
            }
        }
    }
}

private fun fullDateTime(epochMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMillis))

private fun timeOnly(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(epochMillis))

private fun tripLocationText(address: String, latitude: Double?, longitude: Double?): String =
    address.takeIf { it.isNotBlank() }
        ?: if (latitude != null && longitude != null) {
            String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        } else {
            "Konum alınamadı"
        }

private data class TripStopUi(val startedAt: Long, val endedAt: Long, val address: String, val latitude: Double?, val longitude: Double?)

private fun parseTripStops(json: String): List<TripStopUi> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        TripStopUi(
            item.optLong("start"), item.optLong("end"), item.optString("address"),
            item.optDouble("lat").takeUnless { item.isNull("lat") || it.isNaN() },
            item.optDouble("lon").takeUnless { item.isNull("lon") || it.isNaN() }
        )
    }
}.getOrDefault(emptyList())

private fun openMapPoint(context: android.content.Context, latitude: Double?, longitude: Double?) {
    if (latitude == null || longitude == null) return
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")))
    }
}

private fun openTripRoute(
    context: android.content.Context,
    startLat: Double, startLon: Double, endLat: Double, endLon: Double,
    stops: List<TripStopUi>
) {
    val waypoints = stops.mapNotNull { stop ->
        if (stop.latitude != null && stop.longitude != null) "${stop.latitude},${stop.longitude}" else null
    }.take(8).joinToString("|")
    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1&travelmode=driving")
        append("&origin=$startLat,$startLon&destination=$endLat,$endLon")
        if (waypoints.isNotBlank()) append("&waypoints=${Uri.encode(waypoints, "|,")}")
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatisticsCard
        ),
        border = BorderStroke(
            1.dp,
            StatisticsCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = StatisticsSecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatisticsCard
        ),
        border = BorderStroke(
            1.dp,
            StatisticsRed
        )
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            color = StatisticsRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun Double.oneDecimal(): String =
    String.format(Locale("tr", "TR"), "%.1f", this)

private fun Double.twoDecimals(): String =
    String.format(Locale("tr", "TR"), "%.2f", this)

private fun Long.durationText(): String {
    val safeSeconds = coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L

    return if (hours > 0L) {
        String.format(
            Locale("tr", "TR"),
            "%d sa %02d dk",
            hours,
            minutes
        )
    } else {
        String.format(
            Locale("tr", "TR"),
            "%d dk",
            (safeSeconds / 60.0).roundToLong()
        )
    }
}
