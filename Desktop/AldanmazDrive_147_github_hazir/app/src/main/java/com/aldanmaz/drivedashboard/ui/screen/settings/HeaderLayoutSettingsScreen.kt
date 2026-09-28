package com.aldanmaz.drivedashboard.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val defaultHeaderOrder = listOf(
    "COMPASS", "HOME", "WORK", "MAP", "AGENT", "WIFI",
    "BLUETOOTH", "ROUTE", "LIVE", "MOON", "OBD", "SETTINGS"
)

private fun headerName(id: String) = when (id) {
    "COMPASS" -> "Pusula"; "HOME" -> "Eve Git"; "WORK" -> "İşe Git"; "WIFI" -> "Wi-Fi"; "BLUETOOTH" -> "Bluetooth"
    "MUSIC" -> "Araç Müzik Çalar"; "RADIO" -> "Radyo"; "OBD" -> "OBD"; "ROUTE" -> "Rota Eğimi"
    "LIVE" -> "Canlı Eğim"; "MOON" -> "Ay Evresi"; "AGENT" -> "Trafik Ajanı"; "MAP" -> "Harita"; "SETTINGS" -> "Program Ayarları"; else -> id
}

@Composable
fun HeaderLayoutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ald_drive_header", Context.MODE_PRIVATE) }
    val initialOrder = remember {
        prefs.getString("order", null)?.split(",")
            ?.map { when (it) { "CORRIDOR" -> "MUSIC"; "PRAYER" -> "RADIO"; else -> it } }
            ?.filter { it in defaultHeaderOrder }
            ?.let { saved ->
                val completed = saved + defaultHeaderOrder.filterNot(saved::contains)
                if ("MOON" in saved) completed else completed.filterNot { it == "MOON" }.toMutableList().apply {
                    add((indexOf("LIVE") + 1).coerceAtLeast(0), "MOON")
                }
            }
            ?: defaultHeaderOrder
    }
    var order by remember { mutableStateOf(initialOrder) }
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden", emptySet())?.map { when (it) { "CORRIDOR" -> "MUSIC"; "PRAYER" -> "RADIO"; else -> it } }?.filter { it in defaultHeaderOrder }?.toSet() ?: emptySet()) }

    fun save(newOrder: List<String> = order, newHidden: Set<String> = hidden) {
        prefs.edit().putString("order", newOrder.joinToString(",")).putStringSet("hidden", newHidden).apply()
    }
    fun move(id: String, step: Int) {
        val list = order.toMutableList(); val from = list.indexOf(id); val to = (from + step).coerceIn(0, list.lastIndex)
        if (from >= 0 && from != to) { list.removeAt(from); list.add(to, id); order = list; save(newOrder = list) }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF02070D)).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  GERİ", color = Color(0xFF22D7F3), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("ÜST BAR DÜZENİ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(72.dp))
        }
        Text("Sağdaki ≡ tutamacını yukarı/aşağı sürükleyerek sırala. Anahtar ile öğeyi göster veya gizle.", color = Color(0xFF8FA6BA), fontSize = 12.sp)

        order.forEach { id ->
            var drag by remember(id) { mutableFloatStateOf(0f) }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)),
                shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1D3447))
            ) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(headerName(id), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = id !in hidden,
                        onCheckedChange = { show ->
                            val updated = if (show) hidden - id else hidden + id
                            hidden = updated; save(newHidden = updated)
                        }
                    )
                    Text(
                        "  ≡  ", color = Color(0xFF22D7F3), fontSize = 24.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.pointerInput(id, order) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume(); drag += amount
                                    if (drag > 28f) { move(id, 1); drag = 0f }
                                    else if (drag < -28f) { move(id, -1); drag = 0f }
                                },
                                onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }
                            )
                        }
                    )
                }
            }
        }
        OutlinedButton(
            onClick = {
                order = defaultHeaderOrder; hidden = emptySet()
                prefs.edit().remove("order").remove("hidden").apply()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        ) { Text("VARSAYILANA DÖN", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold) }
        Text("Değişiklikler anında kaydedilir.", color = Color(0xFF8FA6BA), fontSize = 11.sp)
    }
}
