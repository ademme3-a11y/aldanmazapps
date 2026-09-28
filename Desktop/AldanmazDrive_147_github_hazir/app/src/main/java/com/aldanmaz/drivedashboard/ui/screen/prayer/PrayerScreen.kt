package com.aldanmaz.drivedashboard.ui.screen.prayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime

private val PrayerBg = Color(0xFF02070D)
private val PrayerCard = Color(0xFF0A1925)
private val PrayerBorder = Color(0xFF245569)

@Composable
fun PrayerScreen(uiState: PrayerUiState, onPrayerEnabled: (String, Boolean) -> Unit,
    onVoiceTypeEnabled: (VoiceAlertType, Boolean) -> Unit, onSpeechRate: (Float) -> Unit,
    onVolume: (Float) -> Unit, onRefresh: () -> Unit, onBack: () -> Unit) {
    val accent = DashboardPaletteRuntime.accent
    val primary = DashboardPaletteRuntime.primaryText
    Column(Modifier.fillMaxSize().background(PrayerBg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(42.dp)) {
            Text("‹ GERİ", color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(9.dp))
            Text("NAMAZ VAKİTLERİ", color = primary, fontSize = 21.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center))
            Text("YENİLE", color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onRefresh).padding(9.dp))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrayerPanel(Modifier.weight(.38f).fillMaxHeight()) {
                Text("SIRADAKİ", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(uiState.nextPrayerName, color = primary, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(uiState.nextPrayerTime, color = accent, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text(uiState.remainingText, color = Color(0xFFFFC84A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(prayerStatus(uiState), color = DashboardPaletteRuntime.secondaryText, fontSize = 10.sp)
            }
            Column(Modifier.weight(.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val t = uiState.times
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerTimeCard("Sabah", t?.fajr, "Sabah" in uiState.enabledPrayers, { onPrayerEnabled("Sabah", it) }, Modifier.weight(1f))
                    PrayerTimeCard("Öğle", t?.dhuhr, "Öğle" in uiState.enabledPrayers, { onPrayerEnabled("Öğle", it) }, Modifier.weight(1f))
                    PrayerTimeCard("İkindi", t?.asr, "İkindi" in uiState.enabledPrayers, { onPrayerEnabled("İkindi", it) }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerTimeCard("Akşam", t?.maghrib, "Akşam" in uiState.enabledPrayers, { onPrayerEnabled("Akşam", it) }, Modifier.weight(1f))
                    PrayerTimeCard("Yatsı", t?.isha, "Yatsı" in uiState.enabledPrayers, { onPrayerEnabled("Yatsı", it) }, Modifier.weight(1f))
                    PrayerPanel(Modifier.weight(1f).fillMaxHeight()) {
                        Text("SESLİ UYARI", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = VoiceAlertType.PRAYER in uiState.enabledVoiceTypes,
                            onCheckedChange = { onVoiceTypeEnabled(VoiceAlertType.PRAYER, it) })
                        Text("Hız ve seviye ayarları merkezi ses ayarlarındadır.",
                            color = DashboardPaletteRuntime.secondaryText, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerTimeCard(name: String, time: String?, enabled: Boolean, onEnabled: (Boolean) -> Unit, modifier: Modifier) {
    PrayerPanel(modifier.fillMaxHeight()) {
        Text(name.uppercase(), color = DashboardPaletteRuntime.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(time ?: "--:--", color = DashboardPaletteRuntime.accent, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Switch(checked = enabled, onCheckedChange = onEnabled)
    }
}

@Composable
private fun PrayerPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PrayerCard),
        border = BorderStroke(1.dp, PrayerBorder)) {
        Column(Modifier.fillMaxSize().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

private fun prayerStatus(state: PrayerUiState): String = when {
    state.isLoading -> "Vakitler güncelleniyor…"
    state.errorMessage != null -> state.errorMessage
    state.times?.isCached == true -> "Çevrimdışı • bugünün kayıtlı vakitleri"
    state.times != null -> "Konuma göre güncel vakitler"
    else -> "GPS konumu bekleniyor"
} ?: ""
