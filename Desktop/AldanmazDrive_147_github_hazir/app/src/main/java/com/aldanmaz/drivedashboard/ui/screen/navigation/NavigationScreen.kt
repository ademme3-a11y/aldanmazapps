package com.aldanmaz.drivedashboard.ui.screen.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun NavigationScreen(onBack: () -> Unit, isDrivingLocked: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE) }
    var destination by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(prefs.getString("home_address", "") ?: "") }
    var work by remember { mutableStateOf(prefs.getString("work_address", "") ?: "") }
    var message by remember { mutableStateOf<String?>(null) }

    // 91: Gemini ev/iş adresini SharedPreferences üzerinden değiştirdiğinde
    // açık olan bu ekran da alanları anında günceller.
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            when (key) {
                "home_address" -> home = shared.getString("home_address", "") ?: ""
                "work_address" -> work = shared.getString("work_address", "") ?: ""
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var voiceTarget by remember { mutableStateOf("destination") }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) {
            when (voiceTarget) {
                "home" -> home = text
                "work" -> work = text
                else -> destination = text
            }
        }
    }
    fun startVoice(target: String) {
        voiceTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, when (target) {
                "home" -> "Ev adresini söyleyin"
                "work" -> "İş adresini söyleyin"
                else -> "Hedefi söyleyin"
            })
        }
        runCatching { voiceLauncher.launch(intent) }.onFailure { message = "Sesli giriş açılamadı" }
    }
    val cyan = Color(0xFF22D7F3)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF02070D)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ GERİ", color=cyan, modifier=Modifier.clickable(onClick=onBack).padding(10.dp))
                Spacer(Modifier.width(12.dp)); Text("HARİTA • NAVİGASYON", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
            }
            if (isDrivingLocked) {
                Text("SÜRÜŞ GÜVENLİK KİLİDİ • Elle yazma kapalı, mikrofonla hedef girebilirsiniz.", color=Color(0xFFFFC857), fontSize=12.sp, fontWeight=FontWeight.Bold)
            }
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07121E)), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp, Color(0xFF1D3447))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("HEDEF", color=cyan, fontWeight=FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(destination, { destination=it }, enabled = !isDrivingLocked, modifier=Modifier.weight(1f), label={Text("Adres veya yer adı")}, singleLine=true)
                        Button(onClick = { startVoice("destination") }) { Text("🎙", fontSize = 18.sp) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        NavButton("GOOGLE MAPS") { if(destination.isNotBlank()) launchNavigation(context,destination,"google") }
                        NavButton("YANDEX") { if(destination.isNotBlank()) launchNavigation(context,destination,"yandex") }
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07121E)), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp, Color(0xFF1D3447))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("EVE GİT", color=cyan, fontWeight=FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(home, { home=it }, enabled = !isDrivingLocked, modifier=Modifier.weight(1f), label={Text("Ev adresi")}, singleLine=true)
                        Button(onClick = { startVoice("home") }) { Text("🎙", fontSize = 18.sp) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        NavButton("EVİ KAYDET", enabled = !isDrivingLocked) { prefs.edit().putString("home_address",home.trim()).apply(); message="Ev adresi kaydedildi" }
                        NavButton("EVE GİT") { if(home.isNotBlank()) launchNavigation(context,home,"google") }
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF07121E)), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp, Color(0xFF1D3447))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("İŞE GİT", color=cyan, fontWeight=FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(work, { work=it }, enabled = !isDrivingLocked, modifier=Modifier.weight(1f), label={Text("İş adresi")}, singleLine=true)
                        Button(onClick = { startVoice("work") }) { Text("🎙", fontSize = 18.sp) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        NavButton("İŞİ KAYDET", enabled = !isDrivingLocked) { prefs.edit().putString("work_address",work.trim()).apply(); message="İş adresi kaydedildi" }
                        NavButton("İŞE GİT") { if(work.isNotBlank()) launchNavigation(context,work,"google") }
                    }
                }
            }
            Text("Adres alanlarına mikrofonla giriş yapabilirsiniz.", color=Color(0xFF8FA6BA), fontSize=12.sp)
            message?.let { Text(it, color=Color(0xFF31E39A)) }
        }
    }
}

@Composable private fun NavButton(text:String, enabled:Boolean = true, onClick:()->Unit) { Button(onClick=onClick, enabled=enabled) { Text(text, fontSize=11.sp, fontWeight=FontWeight.Bold) } }

private fun launchNavigation(context: Context, destination: String, provider: String) {
    val encoded = Uri.encode(destination.trim())
    val uri = if(provider=="yandex") Uri.parse("yandexnavi://build_route_on_map?lat_to=&lon_to=&text=$encoded") else Uri.parse("google.navigation:q=$encoded&mode=d")
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.getOrElse {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun launchSavedHome(context: Context): Boolean {
    val prefs = context.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
    val home = prefs.getString("home_address", "")?.trim().orEmpty()
    if (home.isBlank()) return false
    launchNavigation(context, home, "google")
    return true
}

fun launchSavedWork(context: Context): Boolean {
    val prefs = context.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
    val work = prefs.getString("work_address", "")?.trim().orEmpty()
    if (work.isBlank()) return false
    launchNavigation(context, work, "google")
    return true
}
