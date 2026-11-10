package com.aldanmaz.drivedashboard.ui.screen.specialdays

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class SpecialDay(val id: String, val title: String, val dayMonth: String, val message: String)

object SpecialDayRepository {
    private const val PREFS = "aldanmaz_special_days"
    private const val ENTRIES = "entries"
    private const val SHOWN_PREFIX = "shown_"

    fun load(context: Context): List<SpecialDay> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENTRIES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                SpecialDay(o.optString("id"), o.optString("title"), o.optString("dayMonth"), o.optString("message"))
            }.filter { it.title.isNotBlank() && it.message.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, items: List<SpecialDay>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title).put("dayMonth", item.dayMonth).put("message", item.message))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ENTRIES, array.toString()).apply()
    }

    fun matching(context: Context, date: LocalDate): List<SpecialDay> {
        val key = date.toString()
        val national = when (date.monthValue to date.dayOfMonth) {
            4 to 23 -> listOf(SpecialDay("national_23april", "23 Nisan Ulusal Egemenlik ve Çocuk Bayramı", "23/04", "23 Nisan Ulusal Egemenlik ve Çocuk Bayramımız kutlu olsun."))
            5 to 19 -> listOf(SpecialDay("national_19may", "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı", "19/05", "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramımız kutlu olsun."))
            8 to 30 -> listOf(SpecialDay("national_30august", "30 Ağustos Zafer Bayramı", "30/08", "30 Ağustos Zafer Bayramımız kutlu olsun."))
            10 to 29 -> listOf(SpecialDay("national_29october", "29 Ekim Cumhuriyet Bayramı", "29/10", "29 Ekim Cumhuriyet Bayramımız kutlu olsun."))
            else -> emptyList()
        }
        val religiousDates = mapOf(
            "2026-03-20" to "Ramazan Bayramı", "2026-05-27" to "Kurban Bayramı",
            "2027-03-10" to "Ramazan Bayramı", "2027-05-17" to "Kurban Bayramı",
            "2028-02-27" to "Ramazan Bayramı", "2028-05-06" to "Kurban Bayramı",
            "2029-02-15" to "Ramazan Bayramı", "2029-04-25" to "Kurban Bayramı",
            "2030-02-05" to "Ramazan Bayramı", "2030-04-14" to "Kurban Bayramı"
        )
        val religious = religiousDates[key]?.let { holiday ->
            listOf(SpecialDay("religious_$key", holiday, date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), "$holiday'mız mübarek olsun."))
        }.orEmpty()
        val personal = load(context).filter { it.dayMonth == date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) }
        return national + religious + personal
    }

    fun wasShown(context: Context, date: LocalDate): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SHOWN_PREFIX + date, false)

    fun markShown(context: Context, date: LocalDate) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SHOWN_PREFIX + date, true).apply()
    }
}

@Composable
fun SpecialDaysSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = Color(0xFF38CFFF)
    var items by remember { mutableStateOf(SpecialDayRepository.load(context)) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF02070D)).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ GERİ", color = accent, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("ÖZEL GÜNLER", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Text("Doğum günü, evlilik yıldönümü ve diğer özel günleri sınırsız sayıda ekleyebilirsin. Tarih her yıl tekrarlanır.", color = Color(0xFF9CB0C5), fontSize = 12.sp)
        OutlinedTextField(title, { title = it; error = "" }, Modifier.fillMaxWidth(), label = { Text("Kişi / özel gün adı") }, singleLine = true)
        OutlinedTextField(date, { input ->
            val digits = input.filter { ch -> ch.isDigit() }.take(4)
            date = if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits
            error = ""
        }, Modifier.fillMaxWidth(), label = { Text("Tarih (GG/AA) – sadece 4 rakam yaz") }, placeholder = { Text("Örn. 0505") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(message, { message = it; error = "" }, Modifier.fillMaxWidth(), label = { Text("Park ekranında gösterilecek mesaj") }, minLines = 2)
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), fontSize = 12.sp)
        Button(
            onClick = {
                val parts = date.split("/")
                val day = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val valid = parts.size == 2 && day in 1..31 && month in 1..12 &&
                    runCatching { LocalDate.of(2024, month, day) }.isSuccess
                when {
                    title.isBlank() -> error = "İsim veya özel gün adını yaz."
                    !valid -> error = "Tarihi GG/AA biçiminde gir. Örnek: 14/06"
                    message.isBlank() -> error = "Gösterilecek mesajı yaz."
                    else -> {
                        val entry = SpecialDay(editingId ?: UUID.randomUUID().toString(), title.trim(), "%02d/%02d".format(day, month), message.trim())
                        items = if (editingId == null) items + entry else items.map { if (it.id == editingId) entry else it }
                        SpecialDayRepository.save(context, items)
                        title = ""; date = ""; message = ""; error = ""; editingId = null
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text(if (editingId == null) "ÖZEL GÜN EKLE" else "DEĞİŞİKLİKLERİ KAYDET") }

        Text("KAYITLI ÖZEL GÜNLER (${items.size})", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp)
        items.forEach { item ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF081522)),
                border = BorderStroke(1.dp, accent.copy(alpha = .4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(item.dayMonth, color = accent, fontSize = 12.sp)
                        Text(item.message, color = Color(0xFF9CB0C5), fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            editingId = item.id
                            title = item.title
                            date = item.dayMonth
                            message = item.message
                            error = ""
                        }) { Text("DÜZENLE", color = accent) }
                        TextButton(onClick = {
                            items = items.filterNot { it.id == item.id }
                            SpecialDayRepository.save(context, items)
                            if (editingId == item.id) { editingId = null; title = ""; date = ""; message = "" }
                        }) { Text("SİL", color = Color(0xFFFF7777)) }
                    }
                }
            }
        }
        if (items.isEmpty()) Text("Henüz kişisel özel gün eklenmedi.", color = Color(0xFF71879A), fontSize = 12.sp)
    }
}
