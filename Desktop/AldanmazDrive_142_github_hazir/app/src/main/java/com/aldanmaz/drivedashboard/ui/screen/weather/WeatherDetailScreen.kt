package com.aldanmaz.drivedashboard.ui.screen.weather

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeatherDetailScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        WeatherTemperatureScene(
            temperatureCelsius = uiState.temperatureCelsius,
            condition = uiState.condition,
            isDay = uiState.isDay,
            windSpeedKmh = uiState.windSpeedKmh,
            windGustKmh = uiState.windGustKmh,
            modifier = Modifier.fillMaxSize()
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            val isWide = maxWidth > maxHeight

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    WeatherActionButton(
                        text = "‹ GERİ",
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(92.dp)
                    )
                    Text(
                        text = "AYRINTILI HAVA",
                        color = Color.White,
                        fontSize = if (isWide) 25.sp else 21.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    WeatherActionButton(
                        text = "YENİLE",
                        onClick = onRefresh,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(92.dp)
                    )
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(0.82f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            WeatherGlassCard(Modifier.fillMaxWidth().weight(1f)) {
                                Text(
                                    uiState.cityName ?: "Konum",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    uiState.temperatureText,
                                    color = Color.White,
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "Hissedilen ${uiState.apparentTemperatureText}",
                                    color = Color(0xFFBDEFFF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                val firstHour = uiState.hourlyForecast.firstOrNull()
                                val visibilityKm = firstHour?.visibilityMeters?.div(1000.0)
                                val today = uiState.dailyForecast.firstOrNull()
                                Text(
                                    "Nem: ${uiState.humidityText}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Rüzgâr: ${uiState.windDirectionText} ${uiState.windSpeedText} km/sa",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Hamle: ${uiState.windGustText} km/sa",
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Yağış: ${firstHour?.precipitationProbabilityPercent ?: 0}%  •  Görüş: ${visibilityKm?.let { String.format(Locale.getDefault(), "%.1f km", it) } ?: "--"}",
                                    color = Color(0xFFBDEFFF),
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Gün doğumu: ${clockOnly(today?.sunrise)}  •  Gün batımı: ${clockOnly(today?.sunset)}",
                                    color = Color(0xFFFFDA7A),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val status = weatherStatus(uiState)
                            Text(
                                status,
                                color = Color.White.copy(alpha = .82f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1.65f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("SAATLİK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.hourlyForecast.take(12).forEach { hour ->
                                    WeatherGlassCard(Modifier.width(108.dp)) {
                                        Text(clockOnly(hour.time), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            hour.temperatureCelsius?.let { String.format(Locale.getDefault(), "%.0f°", it) } ?: "--°",
                                            color = Color(0xFFBDEFFF),
                                            fontSize = 25.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text("Yağış ${hour.precipitationProbabilityPercent ?: 0}%", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }

                            Text("5 GÜNLÜK TAHMİN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.dailyForecast.take(5).forEach { day ->
                                    WeatherGlassCard(Modifier.weight(1f)) {
                                        Text(dayLabel(day.date), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${day.maxTemperatureCelsius?.let { String.format(Locale.getDefault(), "%.0f°", it) } ?: "--°"} / ${day.minTemperatureCelsius?.let { String.format(Locale.getDefault(), "%.0f°", it) } ?: "--°"}",
                                            color = Color(0xFFBDEFFF),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text("Yağış ${day.precipitationProbabilityPercent ?: 0}%", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    WeatherGlassCard(Modifier.fillMaxWidth()) {
                        Text(uiState.cityName ?: "Konum", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(uiState.temperatureText, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black)
                        Text("Hissedilen ${uiState.apparentTemperatureText}", color = Color(0xFFBDEFFF), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun weatherStatus(uiState: WeatherUiState): String = when {
    uiState.isLoading -> "Güncelleniyor…"
    uiState.errorMessage != null -> "Hava verisi alınamadı"
    uiState.lastUpdatedEpochMillis != null -> "Son güncelleme: ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(uiState.lastUpdatedEpochMillis))}"
    else -> "Konum bekleniyor"
}

@Composable
private fun WeatherGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(22.dp)
    Card(
        modifier = modifier.clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xB3121E29)),
        border = BorderStroke(1.dp, Color(0x663ED7F2))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun WeatherActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xB3121E29)),
        border = BorderStroke(1.dp, Color(0xFF22D7F3)),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun clockOnly(value: String?): String =
    value?.substringAfter("T")?.take(5) ?: "--:--"

private fun dayLabel(value: String): String =
    runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE", Locale("tr", "TR"))).uppercase(Locale("tr", "TR"))
    }.getOrDefault(value.takeLast(5))
