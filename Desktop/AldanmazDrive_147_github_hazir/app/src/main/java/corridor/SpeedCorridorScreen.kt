package com.aldanmaz.drivedashboard.ui.screen.corridor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.ui.screen.drive.DriveDashboardUiState
import com.aldanmaz.drivedashboard.ui.screen.drive.SpeedCorridorRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SpeedCorridorScreen(
    uiState: DriveDashboardUiState,
    history: List<SpeedCorridorRecord>,
    onBack: () -> Unit
) {
    val bg = Color(0xFF020812); val card = Color(0xFF0B1929); val cyan = Color(0xFF4DD7FF)
    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← SÜRÜŞ", color = cyan, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("HIZ KORİDORU", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = card), border = BorderStroke(1.dp, Color(0xFF19324A)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (uiState.isSpeedCorridorActive) "ÖLÇÜM AKTİF" else "ÖLÇÜM HAZIR", color = if (uiState.isSpeedCorridorActive) Color(0xFF67E88B) else cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(String.format(Locale.getDefault(), "%.2f km", uiState.speedCorridorDistanceKm), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Süre ${duration(uiState.speedCorridorDurationSeconds)}  •  Ortalama ${String.format(Locale.getDefault(), "%.1f", uiState.speedCorridorAverageSpeedKmh)} km/s", color = Color(0xFF9CB0C5))
                    Spacer(Modifier.height(12.dp))
                    Text("Başlat / durdur: ana ekrandaki KOR butonu", color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("GEÇMİŞ ÖLÇÜMLER", color = cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (history.isEmpty()) Text("Henüz tamamlanmış hız koridoru yok.", color = Color(0xFF9CB0C5))
            history.forEach { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = card)) {
                    Text(
                        text = "${clock(r.startedAtEpochMillis)} → ${clock(r.endedAtEpochMillis)}   •   ${String.format(Locale.getDefault(), "%.2f km", r.distanceKm)}   •   ${duration(r.durationSeconds)}   •   Ort. ${String.format(Locale.getDefault(), "%.1f", r.averageSpeedKmh)} km/s",
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun duration(s: Long) = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
private fun clock(ms: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms))
