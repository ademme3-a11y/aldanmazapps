package com.aldanmaz.drivedashboard.ui.screen.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.data.alert.WarningHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WarningHistoryScreen(
    repository: WarningHistoryRepository,
    onBack: () -> Unit,
) {
    val entries by repository.entries.collectAsState()
    var filter by remember { mutableStateOf("TÜMÜ") }
    val visible = remember(entries, filter) {
        when (filter) {
            "OBD" -> entries.filter { it.source == "OBD" }
            "HIZ" -> entries.filter { it.source == "HIZ" || it.source == "TOMTOM" }
            "SİSTEM" -> entries.filter { it.source == "SİSTEM" }
            else -> entries
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF02070D))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123445))) {
                Text("‹ GERİ", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("UYARI GEÇMİŞİ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Son 50 önemli OBD / hız / sistem uyarısı", color = Color(0xFF9BC7D2), fontSize = 10.sp)
            }
            if (entries.isNotEmpty()) {
                OutlinedButton(onClick = repository::clear, border = BorderStroke(1.dp, Color(0xFF7A3131))) {
                    Text("TEMİZLE", color = Color(0xFFFF8A80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("TÜMÜ", "OBD", "HIZ", "SİSTEM").forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                )
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kayıtlı uyarı yok.", color = Color(0xFF9BC7D2), fontSize = 16.sp)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                visible.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF07111C)),
                        border = BorderStroke(1.dp, if (item.technicalVehicleAlert) Color(0xFF7A3131) else Color(0xFF245569)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR")).format(Date(item.lastSeenEpochMs)),
                                color = Color(0xFF6FCFE8), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(86.dp),
                            )
                            Text(item.source, color = Color(0xFFFFD27A), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(62.dp))
                            Text(item.message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (item.repeatCount > 1) {
                                Text("×${item.repeatCount}", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
