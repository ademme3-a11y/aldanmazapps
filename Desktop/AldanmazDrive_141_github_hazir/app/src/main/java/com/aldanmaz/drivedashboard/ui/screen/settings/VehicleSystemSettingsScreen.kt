package com.aldanmaz.drivedashboard.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime
import com.aldanmaz.drivedashboard.ui.screen.driver.DriverAvatar
import com.aldanmaz.drivedashboard.data.trip.AldanmazDriveDatabase
import com.aldanmaz.drivedashboard.data.trip.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val PREF_DRIVE_START = "drive_start_settings"
private const val PREF_DRIVE_REST = "drive_rest_settings"
private const val PREF_MAINTENANCE = "maintenance_settings"
private const val PREF_SAFETY = "driving_safety_settings"
private const val PREF_VISUAL = "night_visual_settings"
private const val PREF_HEY_CAR = "hey_car_voice"
private const val PREF_ROAD_SIGN_TEST = "road_sign_test_mode"
private const val PREF_DRIVERS = "driver_profiles"
private const val PREF_CALIBRATION = "drive_calibration"

private data class MaintenanceItem(
    val id: String,
    val title: String,
    val dueKm: Int,
    val dueDate: String
)

@Composable
fun VehicleSystemSettingsScreen(
    currentOdometerKm: Int,
    isDriving: Boolean,
    onMicrophoneModeSelected: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = DashboardPaletteRuntime.accent
    val startPrefs = remember { context.getSharedPreferences(PREF_DRIVE_START, Context.MODE_PRIVATE) }
    val restPrefs = remember { context.getSharedPreferences(PREF_DRIVE_REST, Context.MODE_PRIVATE) }
    val maintenancePrefs = remember { context.getSharedPreferences(PREF_MAINTENANCE, Context.MODE_PRIVATE) }
    val safetyPrefs = remember { context.getSharedPreferences(PREF_SAFETY, Context.MODE_PRIVATE) }
    val visualPrefs = remember { context.getSharedPreferences(PREF_VISUAL, Context.MODE_PRIVATE) }
    val roadSignTestPrefs = remember { context.getSharedPreferences(PREF_ROAD_SIGN_TEST, Context.MODE_PRIVATE) }
    val driverPrefs = remember { context.getSharedPreferences(PREF_DRIVERS, Context.MODE_PRIVATE) }
    val calibrationPrefs = remember { context.getSharedPreferences(PREF_CALIBRATION, Context.MODE_PRIVATE) }
    val heyCarPrefs = remember {
        val current = context.getSharedPreferences(PREF_HEY_CAR, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences("hey_sandero_voice", Context.MODE_PRIVATE)
        if (!current.contains("enabled") && legacy.contains("enabled")) {
            current.edit().putBoolean("enabled", legacy.getBoolean("enabled", true)).apply()
        }
        current
    }

    var startSafetyEnabled by remember { mutableStateOf(startPrefs.getBoolean("safety_enabled", true)) }
    var tripPrayerEnabled by remember { mutableStateOf(startPrefs.getBoolean("trip_prayer_enabled", true)) }
    var restEnabled by remember { mutableStateOf(restPrefs.getBoolean("enabled", true)) }
    var restMinutes by remember { mutableFloatStateOf(restPrefs.getInt("reminder_minutes", 120).toFloat()) }
    var breakMinutes by remember { mutableFloatStateOf(restPrefs.getInt("real_break_minutes", 15).toFloat()) }
    var safetyLock by remember { mutableStateOf(safetyPrefs.getBoolean("enabled", true)) }
    var nightStars by remember { mutableStateOf(visualPrefs.getBoolean("stars_enabled", true)) }
    var roadSignTestEnabled by remember { mutableStateOf(roadSignTestPrefs.getBoolean("enabled", false)) }
    var roadSignTestMode by remember { mutableStateOf(roadSignTestPrefs.getString("mode", "sequence") ?: "sequence") }
    var speedOffsetKmh by remember { mutableIntStateOf(calibrationPrefs.getInt("speed_offset_kmh", 4)) }
    var altitudeOffsetMeters by remember { mutableIntStateOf(calibrationPrefs.getInt("altitude_offset_m", -25)) }
    var microphoneMode by remember {
        mutableStateOf(
            heyCarPrefs.getString(
                "microphone_mode",
                "AUTO"
            ).let { if (it == "HEY_CAR" || it == "GOOGLE") "AUTO" else it ?: "AUTO" }
        )
    }
    var heyCarFollowUpSeconds by remember { mutableIntStateOf(heyCarPrefs.getInt("follow_up_seconds", 25)) }
    var continuousCommandEnabled by remember { mutableStateOf(heyCarPrefs.getBoolean("continuous_command_enabled", true)) }
    var continuousCommandMinutes by remember { mutableIntStateOf(heyCarPrefs.getInt("continuous_command_minutes", 30)) }
    var items by remember { mutableStateOf(loadMaintenance(maintenancePrefs)) }
    var newTitle by remember { mutableStateOf("") }
    var newKm by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf("") }
    var driverSelectionEnabled by remember { mutableStateOf(driverPrefs.getBoolean("selection_enabled", true)) }
    var driver1Name by remember { mutableStateOf(driverPrefs.getString("driver_1_name", "Mehmet") ?: "Mehmet") }
    var driver2Name by remember { mutableStateOf(driverPrefs.getString("driver_2_name", "Nurdan") ?: "Nurdan") }
    var driver1Photo by remember { mutableStateOf(driverPrefs.getString("driver_1_photo_uri", null)) }
    var driver2Photo by remember { mutableStateOf(driverPrefs.getString("driver_2_photo_uri", null)) }
    var defaultDriverId by remember { mutableStateOf(driverPrefs.getString("default_driver_id", "1") ?: "1") }

    fun persistDriverPhoto(id: String, uri: Uri?) {
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        driverPrefs.edit().putString("driver_${id}_photo_uri", uri?.toString()).apply()
        if (id == "1") driver1Photo = uri?.toString() else driver2Photo = uri?.toString()
    }
    val driver1PhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { persistDriverPhoto("1", it) }
    val driver2PhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { persistDriverPhoto("2", it) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching { writeBackup(context, uri) }
                .onSuccess { Toast.makeText(context, "Yedek oluşturuldu", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Yedek oluşturulamadı", Toast.LENGTH_LONG).show() }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { restoreBackup(context, uri) }
                .onSuccess {
                    restEnabled = restPrefs.getBoolean("enabled", true)
                    restMinutes = restPrefs.getInt("reminder_minutes", 120).toFloat()
                    breakMinutes = restPrefs.getInt("real_break_minutes", 15).toFloat()
                    safetyLock = safetyPrefs.getBoolean("enabled", true)
                    nightStars = visualPrefs.getBoolean("stars_enabled", true)
                    items = loadMaintenance(maintenancePrefs)
                    Toast.makeText(context, "Yedek geri yüklendi", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Yedek okunamadı", Toast.LENGTH_LONG).show() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF02070D)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ GERİ", color = accent, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                Spacer(Modifier.weight(1f))
                Text("ARAÇ SİSTEMLERİ", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
            }

            if (isDriving) {
                SystemCard("SÜRÜŞ GÜVENLİK KİLİDİ", "Araç hareket halinde. Uzun ayarlar ve veri işlemleri kilitli.", accent) {
                    Text("Temel sürüş işlevleri ve sesli komutlar kullanılabilir.", color = Color(0xFFFFC857), fontSize = 12.sp)
                }
            }

            SystemCard("45 • FOTOĞRAFLI SÜRÜCÜ PROFİLLERİ", "Fotoğraf cihazda tutulur; yolculuklar seçilen sürücüye ayrı kaydedilir", accent) {
                SettingSwitch("Açılışta sürücü seçimi", driverSelectionEnabled, !isDriving) {
                    driverSelectionEnabled = it
                    driverPrefs.edit().putBoolean("selection_enabled", it).apply()
                }
                OutlinedTextField(
                    value = driver1Name,
                    onValueChange = {
                        driver1Name = it.take(24)
                        driverPrefs.edit().putString("driver_1_name", driver1Name.trim()).apply()
                    },
                    label = { Text("1. sürücü adı") }, enabled = !isDriving,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = driver2Name,
                    onValueChange = {
                        driver2Name = it.take(24)
                        driverPrefs.edit().putString("driver_2_name", driver2Name.trim()).apply()
                    },
                    label = { Text("2. sürücü adı") }, enabled = !isDriving,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple("1", driver1Name.ifBlank { "Mehmet" }, driver1Photo),
                        Triple("2", driver2Name.ifBlank { "Nurdan" }, driver2Photo)
                    ).forEach { (id, name, photo) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            DriverAvatar(photo, name, Modifier.fillMaxWidth().height(110.dp))
                            Row {
                                TextButton(onClick = { if (id == "1") driver1PhotoPicker.launch(arrayOf("image/*")) else driver2PhotoPicker.launch(arrayOf("image/*")) }, enabled = !isDriving) { Text("FOTOĞRAF SEÇ") }
                                if (photo != null) TextButton(onClick = { persistDriverPhoto(id, null) }, enabled = !isDriving) { Text("KALDIR") }
                            }
                        }
                    }
                }
                Text("Seçim kapalıysa kullanılacak sürücü", color = Color(0xFF8FA6BA), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("1" to driver1Name.ifBlank { "Mehmet" }, "2" to driver2Name.ifBlank { "Nurdan" }).forEach { (id, name) ->
                        FilterChip(
                            selected = defaultDriverId == id,
                            onClick = { if (!isDriving) { defaultDriverId = id; driverPrefs.edit().putString("default_driver_id", id).apply() } },
                            label = { Text(name) }, enabled = !isDriving
                        )
                    }
                }
            }

            SystemCard("28–29 • SÜRÜŞ BAŞLANGICI", "Güvenlik hatırlatması ve yolculuk duası; yeni yolculukta yalnız bir kez", accent) {
                SettingSwitch("Güvenlik hatırlatması", startSafetyEnabled, !isDriving) {
                    startSafetyEnabled = it
                    startPrefs.edit().putBoolean("safety_enabled", it).apply()
                }
                SettingSwitch("Yolculuk duası", tripPrayerEnabled, !isDriving) {
                    tripPrayerEnabled = it
                    startPrefs.edit().putBoolean("trip_prayer_enabled", it).apply()
                }
            }

            SystemCard("30 • SÜRÜŞ / DİNLENME", "Kesintisiz sürüş sonrası mola hatırlatması", accent) {
                SettingSwitch("Hatırlatıcı", restEnabled, !isDriving) {
                    restEnabled = it; restPrefs.edit().putBoolean("enabled", it).apply()
                }
                Text("Uyarı: ${restMinutes.toInt()} dakika", color = Color.White, fontSize = 12.sp)
                Slider(value = restMinutes, onValueChange = { restMinutes = it; restPrefs.edit().putInt("reminder_minutes", it.toInt()).apply() }, valueRange = 60f..240f, enabled = !isDriving)
                Text("Gerçek mola: ${breakMinutes.toInt()} dakika", color = Color.White, fontSize = 12.sp)
                Slider(value = breakMinutes, onValueChange = { breakMinutes = it; restPrefs.edit().putInt("real_break_minutes", it.toInt()).apply() }, valueRange = 10f..30f, enabled = !isDriving)
            }

            SystemCard("31 • ARAÇ BAKIMI", "Tarih veya kilometreye göre bakım takibi", accent) {
                Text("Mevcut referans km: $currentOdometerKm", color = Color(0xFF8FA6BA), fontSize = 11.sp)
                items.forEach { item ->
                    val kmLeft = item.dueKm.takeIf { it > 0 }?.minus(currentOdometerKm)
                    val stateColor = when {
                        kmLeft != null && kmLeft <= 0 -> Color(0xFFFF4D5A)
                        kmLeft != null && kmLeft <= 1000 -> Color(0xFFFFC857)
                        else -> Color(0xFF31E39A)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            val sub = buildString {
                                if (item.dueKm > 0) append("${item.dueKm} km")
                                if (item.dueDate.isNotBlank()) { if (isNotBlank()) append(" • "); append(item.dueDate) }
                            }
                            Text(sub.ifBlank { "Takip bilgisi yok" }, color = stateColor, fontSize = 10.sp)
                        }
                        if (!isDriving) Text("SİL", color = Color(0xFFFF6B6B), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                items = items.filterNot { it.id == item.id }; saveMaintenance(maintenancePrefs, items)
                            }.padding(8.dp))
                    }
                    HorizontalDivider(color = Color(0xFF183044))
                }
                if (!isDriving) {
                    OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Bakım adı") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newKm, { newKm = it.filter(Char::isDigit) }, label = { Text("Sonraki km") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(newDate, { newDate = it }, label = { Text("Tarih (GG.AA.YYYY)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        if (newTitle.isNotBlank()) {
                            items = items + MaintenanceItem(System.currentTimeMillis().toString(), newTitle.trim(), newKm.toIntOrNull() ?: 0, newDate.trim())
                            saveMaintenance(maintenancePrefs, items)
                            newTitle = ""; newKm = ""; newDate = ""
                        }
                    }) { Text("BAKIM EKLE") }
                }
            }

            SystemCard("32 • YEDEKLEME / GERİ YÜKLEME", "Ayarlar ve yerel takip verileri JSON dosyasına aktarılır. API anahtarları ve geçici GPS bilgileri eklenmez.", accent) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !isDriving, onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        createBackup.launch("ALD_DRIVE_yedek_$stamp.json")
                    }, modifier = Modifier.weight(1f)) { Text("YEDEK AL") }
                    OutlinedButton(enabled = !isDriving, onClick = { restoreBackup.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("GERİ YÜKLE") }
                }
            }

            SystemCard("33 • GECE YILDIZLARI", "Meteor ve hareketli uzay efekti yok; yalnız gece hafif sabit yıldızlar", accent) {
                SettingSwitch("Gece yıldızları", nightStars, !isDriving) {
                    nightStars = it; visualPrefs.edit().putBoolean("stars_enabled", it).apply()
                }
            }

            SystemCard("50 • HIZ / RAKIM KALİBRASYONU", "Ekran değeri düzeltilir; ham GPS mesafe, park ve güvenlik hesaplarında korunur.", accent) {
                Text("Hız göstergesi düzeltmesi: ${if (speedOffsetKmh >= 0) "+" else ""}$speedOffsetKmh km/sa", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = speedOffsetKmh.toFloat(),
                    onValueChange = {
                        speedOffsetKmh = it.roundToInt().coerceIn(-10, 10)
                        calibrationPrefs.edit().putInt("speed_offset_kmh", speedOffsetKmh).apply()
                    },
                    valueRange = -10f..10f,
                    steps = 19,
                    enabled = !isDriving
                )
                Text("Örnek: GPS 78, araç göstergesi 82 ise +4 seçilir.", color = Color(0xFF8FA6BA), fontSize = 10.sp)

                Text("Rakım düzeltmesi: ${if (altitudeOffsetMeters >= 0) "+" else ""}$altitudeOffsetMeters m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = altitudeOffsetMeters.toFloat(),
                    onValueChange = {
                        altitudeOffsetMeters = it.roundToInt().coerceIn(-100, 100)
                        calibrationPrefs.edit().putInt("altitude_offset_m", altitudeOffsetMeters).apply()
                    },
                    valueRange = -100f..100f,
                    steps = 199,
                    enabled = !isDriving
                )
                Text("Bilinen 5 m yükseklikte GPS 30 m gösteriyorsa −25 m seçilir.", color = Color(0xFF8FA6BA), fontSize = 10.sp)
            }

            SystemCard("50 • TEK MİKROFON / SESLİ KONTROL", "Komutlar, gerçek ses girişleri ve TTS tek merkezi ses kuyruğundan yönetilir.", accent) {
                Text("Sesli kontrol", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "OFF" to "KAPALI",
                        "AUTO" to "OTOMATİK",
                        "IN_APP" to "UYGULAMA İÇİ",
                        "GOOGLE_COMPAT" to "GOOGLE UYUMLU"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = microphoneMode == mode,
                            onClick = {
                                if (!isDriving) {
                                    microphoneMode = mode
                                    heyCarPrefs.edit()
                                        .putString("microphone_mode", mode)
                                        .putBoolean("hands_free_enabled", false)
                                        .putBoolean("enabled", false)
                                        .apply()
                                    onMicrophoneModeSelected()
                                }
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            enabled = !isDriving,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    if (microphoneMode != "OFF") "Tek dokunma dinler; çift dokunma kapatır. OTOMATİK mod araçta gerekirse Google uyumlu girişe geçer."
                    else "Bütün mikrofon dinlemeleri kapalıdır.",
                    color = Color(0xFFFFD982),
                    fontSize = 10.sp
                )
                Text("Örnek: hava durumu, eve git, trafik ajanı hedef Antalya, rota, canlı yükseklik, OBD, radyo kapat.", color = Color(0xFF8FA6BA), fontSize = 11.sp)
            }

            SystemCard("46 • ÇEVRİMDIŞI HARİTALAR", "Harita alanını Google Haritalar içinde indirerek internet kesildiğinde otomobil rotasını kullanın.", accent) {
                Button(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                    if (launch != null) context.startActivity(launch)
                    else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))) }
                    Toast.makeText(context, "Profil resminiz › Çevrimdışı haritalar › Kendi haritanızı seçin", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("GOOGLE HARİTALAR'I AÇ") }
                Text("Haritalar açılınca: Profil resminiz → Çevrimdışı haritalar → Kendi haritanızı seçin → İndir. Çevrimdışıyken canlı trafik ve alternatif güzergâh alınamaz.", color = Color(0xFFFFD982), fontSize = 10.sp)
            }

            SystemCard("36 • TRAFİK LEVHA TEST MODU", "Gerçek TomTom verisini beklemeden 500 metre levha ekranını park halinde sınar. Araç 5 km/sa üzerine çıkınca sahte levhalar gösterilmez.", accent) {
                SettingSwitch("Test modunu aç", roadSignTestEnabled, !isDriving) { enabled ->
                    roadSignTestEnabled = enabled
                    roadSignTestPrefs.edit()
                        .putBoolean("enabled", enabled)
                        .putString("mode", roadSignTestMode)
                        .putLong("started_at", System.currentTimeMillis())
                        .apply()
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !isDriving,
                        onClick = {
                            roadSignTestEnabled = true
                            roadSignTestMode = "sequence"
                            roadSignTestPrefs.edit()
                                .putBoolean("enabled", true)
                                .putString("mode", "sequence")
                                .putLong("started_at", System.currentTimeMillis())
                                .apply()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("SIRALI TEST") }

                    OutlinedButton(
                        enabled = !isDriving,
                        onClick = {
                            roadSignTestEnabled = true
                            roadSignTestMode = "dual"
                            roadSignTestPrefs.edit()
                                .putBoolean("enabled", true)
                                .putString("mode", "dual")
                                .putLong("started_at", System.currentTimeMillis())
                                .apply()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("İKİLİ TEST") }
                }

                OutlinedButton(
                    enabled = roadSignTestEnabled && !isDriving,
                    onClick = {
                        roadSignTestEnabled = false
                        roadSignTestPrefs.edit().putBoolean("enabled", false).apply()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("TESTİ KAPAT") }

                Text(
                    if (roadSignTestEnabled) {
                        if (roadSignTestMode == "dual") "TEST AKTİF • İki levha aynı anda gösterilecek."
                        else "TEST AKTİF • Levhalar 500 m'den 50 m'ye yaklaşarak sırayla değişecek."
                    } else {
                        "Test kapalı • Normalde yalnız gerçek TomTom levha/uyarı verisi kullanılır."
                    },
                    color = if (roadSignTestEnabled) Color(0xFFFFC857) else Color(0xFF8FA6BA),
                    fontSize = 11.sp
                )
            }

            SystemCard("35 • SÜRÜŞ GÜVENLİK KİLİDİ", "Hareket halinde elle adres, tema/düzen, bakım ve yedekleme kısıtlanır", accent) {
                SettingSwitch("Güvenlik kilidi", safetyLock, !isDriving) {
                    safetyLock = it; safetyPrefs.edit().putBoolean("enabled", it).apply()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SystemCard(title: String, subtitle: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, accent.copy(alpha = .42f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color(0xFF8FA6BA), fontSize = 11.sp)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun loadMaintenance(prefs: android.content.SharedPreferences): List<MaintenanceItem> {
    val raw = prefs.getString("items", null)
    if (raw.isNullOrBlank()) {
        return listOf("Motor yağı", "Yağ filtresi", "Hava filtresi", "Polen filtresi", "Triger", "Lastikler", "Akü", "Muayene", "Sigorta", "Kasko")
            .mapIndexed { index, title -> MaintenanceItem("default_$index", title, 0, "") }
    }
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(MaintenanceItem(o.optString("id"), o.optString("title"), o.optInt("dueKm"), o.optString("dueDate")))
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveMaintenance(prefs: android.content.SharedPreferences, items: List<MaintenanceItem>) {
    val arr = JSONArray()
    items.forEach { item -> arr.put(JSONObject().put("id", item.id).put("title", item.title).put("dueKm", item.dueKm).put("dueDate", item.dueDate)) }
    prefs.edit().putString("items", arr.toString()).apply()
}

private val backupPreferenceNames = listOf(
    "drive_start_settings", "drive_rest_settings", "maintenance_settings", "driving_safety_settings",
    "night_visual_settings", "ald_drive_header", "navigation_settings", "obd_settings", "prayer_settings",
    "speed_corridor", "central_voice_alerts", "driver_profiles", "hey_car_voice", "drive_calibration"
)

private fun writeBackup(context: Context, uri: Uri) {
    val root = JSONObject().put("format", "ALD_DRIVE_BACKUP_V1").put("createdAt", System.currentTimeMillis())
    val prefsRoot = JSONObject()
    backupPreferenceNames.forEach { name ->
        val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        val obj = JSONObject()
        all.forEach { (k, v) ->
            when (v) {
                is String, is Boolean, is Int, is Long, is Float, is Double -> obj.put(k, v)
                is Set<*> -> obj.put(k, JSONArray(v.filterIsInstance<String>()))
            }
        }
        prefsRoot.put(name, obj)
    }
    root.put("preferences", prefsRoot)
    val trips = runBlocking(Dispatchers.IO) { AldanmazDriveDatabase.getInstance(context).tripDao().getAllTrips() }
    val tripArray = JSONArray()
    trips.forEach { t ->
        tripArray.put(JSONObject()
            .put("id", t.id).put("startedAtEpochMillis", t.startedAtEpochMillis).put("endedAtEpochMillis", t.endedAtEpochMillis)
            .put("distanceKm", t.distanceKm).put("totalDurationSeconds", t.totalDurationSeconds).put("movingDurationSeconds", t.movingDurationSeconds)
            .put("parkDurationSeconds", t.parkDurationSeconds).put("averageSpeedKmh", t.averageSpeedKmh).put("maxSpeedKmh", t.maxSpeedKmh)
            .put("vehicleMode", t.vehicleMode).put("vehicleId", t.vehicleId).put("estimatedFuelConsumedLiters", t.estimatedFuelConsumedLiters)
            .put("driverId", t.driverId).put("driverName", t.driverName)
            .put("startLatitude", t.startLatitude).put("startLongitude", t.startLongitude)
            .put("endLatitude", t.endLatitude).put("endLongitude", t.endLongitude)
            .put("startAddress", t.startAddress).put("endAddress", t.endAddress).put("stopEventsJson", t.stopEventsJson)
            .put("estimatedFuelCost", t.estimatedFuelCost).put("speed0To30Seconds", t.speed0To30Seconds).put("speed31To50Seconds", t.speed31To50Seconds)
            .put("speed51To70Seconds", t.speed51To70Seconds).put("speed71To90Seconds", t.speed71To90Seconds).put("speed91To120Seconds", t.speed91To120Seconds)
            .put("speedOver120Seconds", t.speedOver120Seconds))
    }
    root.put("trips", tripArray)
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
        ?: error("Output açılamadı")
}

private fun restoreBackup(context: Context, uri: Uri) {
    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Dosya okunamadı")
    val root = JSONObject(text)
    require(root.optString("format") == "ALD_DRIVE_BACKUP_V1") { "Geçersiz yedek" }
    val prefsRoot = root.getJSONObject("preferences")
    backupPreferenceNames.forEach { name ->
        if (!prefsRoot.has(name)) return@forEach
        val obj = prefsRoot.getJSONObject(name)
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        obj.keys().forEach { key ->
            when (val value = obj.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is JSONArray -> editor.putStringSet(key, buildSet { for (i in 0 until value.length()) add(value.getString(i)) })
            }
        }
        editor.apply()
    }
    if (root.has("trips")) {
        val arr = root.getJSONArray("trips")
        val trips = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(TripEntity(
                    id = o.optLong("id", 0L),
                    startedAtEpochMillis = o.getLong("startedAtEpochMillis"),
                    endedAtEpochMillis = o.getLong("endedAtEpochMillis"),
                    distanceKm = o.getDouble("distanceKm"),
                    totalDurationSeconds = o.getLong("totalDurationSeconds"),
                    movingDurationSeconds = o.getLong("movingDurationSeconds"),
                    parkDurationSeconds = o.getLong("parkDurationSeconds"),
                    averageSpeedKmh = o.getDouble("averageSpeedKmh"),
                    maxSpeedKmh = o.getInt("maxSpeedKmh"),
                    vehicleMode = o.getString("vehicleMode"),
                    vehicleId = o.getString("vehicleId"),
                    estimatedFuelConsumedLiters = o.getDouble("estimatedFuelConsumedLiters"),
                    estimatedFuelCost = o.optDouble("estimatedFuelCost", 0.0),
                    driverId = o.optString("driverId", "1"),
                    driverName = o.optString("driverName", "Mehmet"),
                    startLatitude = o.optDouble("startLatitude").takeIf { o.has("startLatitude") && !o.isNull("startLatitude") },
                    startLongitude = o.optDouble("startLongitude").takeIf { o.has("startLongitude") && !o.isNull("startLongitude") },
                    endLatitude = o.optDouble("endLatitude").takeIf { o.has("endLatitude") && !o.isNull("endLatitude") },
                    endLongitude = o.optDouble("endLongitude").takeIf { o.has("endLongitude") && !o.isNull("endLongitude") },
                    startAddress = o.optString("startAddress", ""),
                    endAddress = o.optString("endAddress", ""),
                    stopEventsJson = o.optString("stopEventsJson", "[]"),
                    speed0To30Seconds = o.optLong("speed0To30Seconds", 0L),
                    speed31To50Seconds = o.optLong("speed31To50Seconds", 0L),
                    speed51To70Seconds = o.optLong("speed51To70Seconds", 0L),
                    speed71To90Seconds = o.optLong("speed71To90Seconds", 0L),
                    speed91To120Seconds = o.optLong("speed91To120Seconds", 0L),
                    speedOver120Seconds = o.optLong("speedOver120Seconds", 0L)
                ))
            }
        }
        runBlocking(Dispatchers.IO) {
            val dao = AldanmazDriveDatabase.getInstance(context).tripDao()
            dao.deleteAllTrips()
            trips.forEach { dao.insertTrip(it) }
        }
    }
}
