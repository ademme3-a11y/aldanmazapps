package com.aldanmaz.drivedashboard.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore("appearance_settings")

data class AppearanceSettings(
    val theme: String = "GECE",
    val textScale: Float = 1f,
    val dayNightMode: String = "OTOMATIK",
    val dayBrightness: Float = 0.85f,
    val nightBrightness: Float = 0.28f,
    val sunlightMode: String = "OTOMATIK",
    val accentColorArgb: Long = 0xFF22D7F3
)

class AppearanceSettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val textScaleKey = floatPreferencesKey("text_scale")
    private val dayNightKey = stringPreferencesKey("day_night_mode")
    private val dayBrightnessKey = floatPreferencesKey("day_brightness")
    private val nightBrightnessKey = floatPreferencesKey("night_brightness")
    private val sunlightModeKey = stringPreferencesKey("sunlight_mode")
    private val accentColorKey = longPreferencesKey("accent_color_argb")

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map {
        AppearanceSettings(
            theme = it[themeKey] ?: "GECE",
            textScale = (it[textScaleKey] ?: 1f).coerceIn(.9f, 1.25f),
            dayNightMode = it[dayNightKey] ?: "OTOMATIK",
            dayBrightness = (it[dayBrightnessKey] ?: .85f).coerceIn(.35f, 1f),
            nightBrightness = (it[nightBrightnessKey] ?: .28f).coerceIn(.08f, .6f),
            sunlightMode = it[sunlightModeKey] ?: "OTOMATIK",
            accentColorArgb = it[accentColorKey] ?: 0xFF22D7F3
        )
    }
    suspend fun setTheme(v: String) { context.appearanceDataStore.edit { it[themeKey] = v } }
    suspend fun setTextScale(v: Float) { context.appearanceDataStore.edit { it[textScaleKey] = v } }
    suspend fun setDayNightMode(v: String) { context.appearanceDataStore.edit { it[dayNightKey] = v } }
    suspend fun setDayBrightness(v: Float) { context.appearanceDataStore.edit { it[dayBrightnessKey] = v } }
    suspend fun setNightBrightness(v: Float) { context.appearanceDataStore.edit { it[nightBrightnessKey] = v } }
    suspend fun setSunlightMode(v: String) { context.appearanceDataStore.edit { it[sunlightModeKey] = v } }
    suspend fun setAccentColor(v: Long) { context.appearanceDataStore.edit { it[accentColorKey] = v } }
}

@Composable
fun AppearanceSettingsScreen(
    settings: AppearanceSettings,
    onTheme: (String) -> Unit,
    onTextScale: (Float) -> Unit,
    onDayNightMode: (String) -> Unit = {},
    onDayBrightness: (Float) -> Unit = {},
    onNightBrightness: (Float) -> Unit = {},
    onSunlightMode: (String) -> Unit = {},
    onAccentColor: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val bg = if (settings.theme == "OLED") Color.Black else Color(0xFF02070D)
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹  GERİ", color = Color(0xFF22D7F3), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Spacer(Modifier.weight(1f)); Text("GÖRÜNÜM & SAAT AYARLARI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f))
        }
        SettingCard("GÜNDÜZ / GECE", "Otomatik mod hava verisindeki gün/gece bilgisini kullanır") {
            Choice("OTOMATİK", settings.dayNightMode == "OTOMATIK") { onDayNightMode("OTOMATIK") }
            Choice("GÜNDÜZ", settings.dayNightMode == "GUNDUZ") { onDayNightMode("GUNDUZ") }
            Choice("GECE", settings.dayNightMode == "GECE") { onDayNightMode("GECE") }
        }
        SettingCard("GÜNEŞ MODU", "Işık sensörü olmadan otomatikte gündüz etkinleşir; Ay ikonuna uzun basarak hızlıca değiştirilebilir") {
            Choice("OTOMATİK", settings.sunlightMode == "OTOMATIK") { onSunlightMode("OTOMATIK") }
            Choice("AÇIK", settings.sunlightMode == "ACIK") { onSunlightMode("ACIK") }
            Choice("KAPALI", settings.sunlightMode == "KAPALI") { onSunlightMode("KAPALI") }
        }
        SettingCard("GÜNDÜZ PARLAKLIĞI", "%${(settings.dayBrightness * 100).toInt()}") {
            Slider(value = settings.dayBrightness, onValueChange = onDayBrightness, valueRange = .35f..1f, modifier = Modifier.weight(1f))
        }
        SettingCard("GECE PARLAKLIĞI", "%${(settings.nightBrightness * 100).toInt()}") {
            Slider(value = settings.nightBrightness, onValueChange = onNightBrightness, valueRange = .08f..6f/10f, modifier = Modifier.weight(1f))
        }
        SettingCard("TEMA", "Araç ekranı için koyu görünüm") { Choice("GECE", settings.theme == "GECE") { onTheme("GECE") }; Choice("OLED SİYAH", settings.theme == "OLED") { onTheme("OLED") } }
        SettingCard("YAZI BOYUTU", "Ana ekran ve bilgi ekranlarının okunabilirliği") { Choice("NORMAL", settings.textScale == 1f) { onTextScale(1f) }; Choice("BÜYÜK", settings.textScale == 1.12f) { onTextScale(1.12f) }; Choice("ÇOK BÜYÜK", settings.textScale == 1.25f) { onTextScale(1.25f) } }
        SettingCard("RENK PALETİ", "Saat, üst bar, çerçeveler ve vurgu yazıları") {
            ColorChoice("TURKUAZ", 0xFF22D7F3, settings.accentColorArgb, onAccentColor)
            ColorChoice("TURUNCU", 0xFFFF8A2A, settings.accentColorArgb, onAccentColor)
            ColorChoice("YEŞİL", 0xFF31E39A, settings.accentColorArgb, onAccentColor)
            ColorChoice("MAVİ", 0xFF4C8DFF, settings.accentColorArgb, onAccentColor)
            ColorChoice("BEYAZ", 0xFFF2F5F7, settings.accentColorArgb, onAccentColor)
        }
        Text("Parlaklık yalnız ALD DRIVE penceresine uygulanır; telefonun sistem parlaklığı değiştirilmez.", color = Color(0xFF8FA6BA), fontSize = 12.sp)
    }
}

@Composable private fun SettingCard(title: String, subtitle: String, content: @Composable RowScope.() -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF1D3447))) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, color = Color(0xFF8EF5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF8FA6BA), fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content) } }
@Composable private fun RowScope.Choice(text: String, selected: Boolean, onClick: () -> Unit) { Surface(Modifier.weight(1f).clickable(onClick = onClick), RoundedCornerShape(12.dp), if (selected) Color(0xFF0E6B4E) else Color(0xFF0B1A28), border = BorderStroke(1.dp, if (selected) Color(0xFF31E39A) else Color(0xFF294B63))) { Box(Modifier.padding(vertical = 13.dp), contentAlignment = Alignment.Center) { Text(text, color = if (selected) Color(0xFF31E39A) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }


@Composable
private fun RowScope.ColorChoice(label: String, value: Long, selected: Long, onClick: (Long) -> Unit) {
    val c = Color(value)
    Surface(
        modifier = Modifier.weight(1f).clickable { onClick(value) },
        shape = RoundedCornerShape(12.dp),
        color = if (selected == value) c.copy(alpha = .22f) else Color(0xFF0B1A28),
        border = BorderStroke(if (selected == value) 2.dp else 1.dp, c)
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(18.dp), CircleShape, color = c) {}
            Spacer(Modifier.height(5.dp))
            Text(label, color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
