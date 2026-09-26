package com.aldanmaz.drivedashboard.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aldanmaz.drivedashboard.BuildConfig
import com.aldanmaz.drivedashboard.data.speedlimit.RoadSpeedLimitRepository
import com.aldanmaz.drivedashboard.data.speedlimit.TomTomDiagnosticResult
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProgramSettingsScreen(
    isDrivingLocked: Boolean,
    onAppearance: () -> Unit,
    onHeaderLayout: () -> Unit,
    onVehicleSystems: () -> Unit,
    onFuel: () -> Unit,
    onStatistics: () -> Unit,
    onPrayer: () -> Unit,
    onObd: () -> Unit,
    onNavigation: () -> Unit,
    onTrafficAgent: () -> Unit,
    onHelp: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pinPrefs = remember {
        context.getSharedPreferences("settings_pin", android.content.Context.MODE_PRIVATE)
    }
    var pinEnabled by remember { mutableStateOf(pinPrefs.getBoolean("enabled", false)) }
    var unlocked by rememberSaveable { mutableStateOf(!pinEnabled) }

    fun leaveSettings() {
        unlocked = !pinEnabled
        onBack()
    }

    if (pinEnabled && !unlocked) {
        SettingsPinUnlock(
            verify = { verifySettingsPin(pinPrefs, it) },
            onUnlocked = { unlocked = true },
            onBack = ::leaveSettings,
        )
        return
    }

    val accent = DashboardPaletteRuntime.accent
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF02070D))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹  GERİ",
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = ::leaveSettings)
                    .padding(8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "PROGRAM AYARLARI",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text("⚙", color = accent, fontSize = 26.sp)
        }

        if (isDrivingLocked) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF351C10)),
                border = BorderStroke(1.dp, Color(0xFFFFC857)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Sürüş güvenlik kilidi aktif. Ayarlar araç durduğunda değiştirilebilir.",
                    color = Color(0xFFFFD982),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        SettingsHubCard(
            icon = "◷",
            title = "GÖRÜNÜM & SAAT",
            subtitle = "Tema, gündüz/gece, parlaklık, yazı boyutu, renk paleti ve saat görünümü",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onAppearance
        )
        SettingsHubCard(
            icon = "▤",
            title = "ÜST BAR DÜZENİ",
            subtitle = "İkonları sırala, göster/gizle ve varsayılan düzene dön",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onHeaderLayout
        )
        SettingsHubCard(
            icon = "⚙",
            title = "ARAÇ & PROGRAM SİSTEMLERİ",
            subtitle = "Sürüş başlangıcı, mola, bakım, yedekleme, gece yıldızları, bas-konuş ve güvenlik kilidi",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onVehicleSystems
        )
        SettingsHubCard(
            icon = "⛽",
            title = "YAKIT AYARLARI",
            subtitle = "Depo kapasitesi, mevcut yakıt, tüketim, düşük yakıt eşiği ve maliyet",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onFuel
        )
        SettingsHubCard(
            icon = "▥",
            title = "YOLCULUK & İSTATİSTİKLER",
            subtitle = "Gün, hafta, ay ve yıl mesafeleri; yakıt ve hız aralığı kayıtları",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onStatistics
        )
        SettingsHubCard(
            icon = "☾",
            title = "NAMAZ & SES AYARLARI",
            subtitle = "Namaz bildirimleri, ses türleri, konuşma hızı ve ses seviyesi",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onPrayer
        )
        SettingsHubCard(
            icon = "OBD",
            title = "OBD AYARLARI",
            subtitle = "Araç bağlantısı ve OBD sistemi",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onObd
        )
        SettingsHubCard(
            icon = "⌖",
            title = "ROTA & EV ADRESİ",
            subtitle = "Navigasyon, kayıtlı ev adresi ve rota seçenekleri",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onNavigation
        )
        SettingsHubCard(
            icon = "◎",
            title = "TRAFİK AJANI",
            subtitle = "Hedef, trafik tarama ve sesli trafik uyarısı seçenekleri",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onTrafficAgent
        )

        TomTomDiagnosticsCard()
        SettingsHubCard(
            icon = "?",
            title = "YARDIM & KULLANIM KILAVUZU",
            subtitle = "Özellikleri kendi ikonlarıyla tanıyın ve nasıl kullanılacağını öğrenin",
            accent = accent,
            enabled = !isDrivingLocked,
            onClick = onHelp
        )

        FirebaseAppCheckDiagnosticsCard()

        SettingsPinCard(
            enabled = pinEnabled,
            onEnabledChanged = { enabled ->
                pinEnabled = enabled
                if (!enabled) unlocked = true
            },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF08111D)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = .55f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "HAKKINDA",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Sürüm / Proje Dosyası: AldanmazDrive_${BuildConfig.VERSION_NAME.substringBefore('-')}.zip",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Araç testinde hangi tam proje sürümünün kurulu olduğunu buradan kontrol edebilirsiniz.",
                    color = Color(0xFF8FA6BA),
                    fontSize = 10.sp
                )
            }
        }

        Text(
            text = "Analog saat üzerindeki AYARLAR bağlantısı kaldırıldı. Programla ilgili ayarlara artık üst bardaki dişli simgesinden ulaşılır.",
            color = Color(0xFF8FA6BA),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )
    }
}

@Composable
private fun TomTomDiagnosticsCard() {
    val context = LocalContext.current
    val accent = DashboardPaletteRuntime.accent
    val scope = rememberCoroutineScope()
    val repository = remember { RoadSpeedLimitRepository() }
    var running by remember { mutableStateOf(false) }
    var result by remember {
        mutableStateOf<TomTomDiagnosticResult?>(
            if (BuildConfig.TOMTOM_API_KEY.isBlank()) {
                TomTomDiagnosticResult(
                    apiKeyPresent = false,
                    message = "TomTom API anahtarı bulunamadı"
                )
            } else {
                null
            }
        )
    }

    fun lastKnownLocation(): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "TOMTOM HIZ SINIRI TESTİ",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "API anahtarı: ${if (BuildConfig.TOMTOM_API_KEY.isBlank()) "YOK" else "VAR"}",
                color = if (BuildConfig.TOMTOM_API_KEY.isBlank()) Color(0xFFFF6B6B) else Color(0xFF77E6A6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            result?.let { diagnostic ->
                Text(
                    text = diagnostic.message,
                    color = if ((diagnostic.responseCode?.let { it in 200..299 } == true) || (diagnostic.responseCode == null && diagnostic.apiKeyPresent)) Color.White else Color(0xFFFF9A7A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                diagnostic.responseCode?.let {
                    Text("HTTP: $it", color = Color(0xFFB7C9D8), fontSize = 11.sp)
                }
                if (!diagnostic.roadName.isNullOrBlank()) {
                    Text(
                        text = "Yol: ${diagnostic.roadName}${diagnostic.routeNumbers.takeIf { it.isNotEmpty() }?.joinToString(prefix = " • ") ?: ""}",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Hız sınırı: ${diagnostic.speedLimitKmh?.let { "$it km/h" } ?: "GELMEDİ"}",
                    color = if (diagnostic.speedLimitKmh != null) Color(0xFF77E6A6) else Color(0xFFFFD982),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            } ?: Text(
                text = "TEST ET düğmesine basınca mevcut/son GPS konumu TomTom'a gönderilir.",
                color = Color(0xFF8FA6BA),
                fontSize = 10.sp
            )

            Button(
                enabled = !running,
                onClick = {
                    val location = lastKnownLocation()
                    if (location == null) {
                        result = TomTomDiagnosticResult(
                            apiKeyPresent = BuildConfig.TOMTOM_API_KEY.isNotBlank(),
                            message = "Konum alınamadı. Konum iznini/GPS'i kontrol edin."
                        )
                    } else {
                        running = true
                        result = TomTomDiagnosticResult(
                            apiKeyPresent = BuildConfig.TOMTOM_API_KEY.isNotBlank(),
                            message = "TomTom sorgulanıyor…"
                        )
                        scope.launch {
                            result = repository.diagnose(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                headingDegrees = location.bearing.takeIf { location.hasBearing() }
                            )
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "TEST EDİLİYOR…" else "TOMTOM TEST ET")
            }
        }
    }
}

@Composable
private fun FirebaseAppCheckDiagnosticsCard() {
    val context = LocalContext.current
    val accent = DashboardPaletteRuntime.accent
    var debugSecret by remember { mutableStateOf(readFirebaseDebugSecret(context)) }
    var status by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) "Cihaz kodu hazırlanıyor…" else "Release APK: Play Integrity etkin"
        )
    }

    fun refresh() {
        if (!BuildConfig.DEBUG) return
        status = "Cihaz kodu yenileniyor…"
        runCatching {
            FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.getAppCheckToken(false).addOnCompleteListener {
                debugSecret = readFirebaseDebugSecret(context)
                status = if (debugSecret != null) {
                    "Kod hazır. Firebase Console > App Check > Debug tokenlar bölümüne ekleyin."
                } else {
                    "Kod henüz oluşmadı. Gemini'yi bir kez açıp YENİLE'ye basın."
                }
            }
        }.onFailure {
            status = "App Check başlatılamadı: ${it.message ?: "bilinmeyen hata"}"
        }
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG && debugSecret == null) refresh()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .45f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GEMINI / FIREBASE APP CHECK",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            if (BuildConfig.DEBUG) {
                Text(
                    text = "Bu cihazın debug kodu",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = debugSecret ?: "—",
                    color = Color(0xFF7CE8FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::refresh) { Text("YENİLE") }
                    Button(
                        enabled = debugSecret != null,
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("Firebase App Check debug kodu", debugSecret)
                            )
                            status = "Kod panoya kopyalandı."
                        }
                    ) { Text("KOPYALA") }
                }
            }
            Text(status, color = Color(0xFF8FA6BA), fontSize = 10.sp)
            Text(
                text = if (BuildConfig.DEBUG) {
                    "Uygulama verisi silinir veya başka cihaza kurulursa kod değişebilir."
                } else {
                    "Release sürümünde debug kodu kullanılmaz; Firebase App Check Play Integrity ile doğrulanır."
                },
                color = Color(0xFF8FA6BA),
                fontSize = 10.sp
            )
        }
    }
}

private val firebaseDebugSecretPattern =
    Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")

private fun readFirebaseDebugSecret(context: android.content.Context): String? {
    if (!BuildConfig.DEBUG) return null
    val directory = File(context.applicationInfo.dataDir, "shared_prefs")
    return directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == "xml" }
        .sortedByDescending { it.lastModified() }
        .mapNotNull { file ->
            val content = runCatching { file.readText() }.getOrNull().orEmpty()
            val isFirebaseStore =
                file.name.contains("firebase", ignoreCase = true) ||
                    content.contains("appcheck", ignoreCase = true) ||
                    content.contains("debug", ignoreCase = true)
            if (isFirebaseStore) firebaseDebugSecretPattern.find(content)?.value else null
        }
        .firstOrNull()
}

@Composable
private fun SettingsPinUnlock(
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onBack: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF02070D)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "AYARLAR KİLİDİ",
            color = DashboardPaletteRuntime.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Text("Dört haneli PIN'inizi girin", color = Color(0xFF9CB0C5), fontSize = 13.sp)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4); error = false },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text("PIN") },
        )
        if (error) Text("PIN hatalı", color = Color(0xFFFF6B6B))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("GERİ") }
            Button(onClick = {
                if (pin.length == 4 && verify(pin)) onUnlocked() else error = true
            }) {
                Text("AYARLARI AÇ")
            }
        }
    }
}

@Composable
private fun SettingsPinCard(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("settings_pin", android.content.Context.MODE_PRIVATE)
    }
    var mode by remember { mutableStateOf<String?>(null) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07121E)),
        border = BorderStroke(1.dp, DashboardPaletteRuntime.accent.copy(alpha = .45f)),
        shape = RoundedCornerShape(17.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("AYARLAR PIN KİLİDİ", color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    if (enabled) "4 haneli PIN etkin" else "Şifresiz kullanım",
                    color = Color(0xFF8FA6BA),
                    fontSize = 10.sp,
                )
            }
            if (enabled) {
                TextButton(onClick = { mode = "change" }) { Text("DEĞİŞTİR") }
                TextButton(onClick = { mode = "disable" }) { Text("KAPAT") }
            } else {
                Button(onClick = { mode = "create" }) { Text("PIN OLUŞTUR") }
            }
        }
    }

    mode?.let { selectedMode ->
        SettingsPinEditor(
            mode = selectedMode,
            verify = { verifySettingsPin(prefs, it) },
            onSave = { current, newPin ->
                when (selectedMode) {
                    "create" -> {
                        saveSettingsPin(prefs, newPin)
                        onEnabledChanged(true)
                        mode = null
                        true
                    }
                    "change" -> if (verifySettingsPin(prefs, current)) {
                        saveSettingsPin(prefs, newPin)
                        mode = null
                        true
                    } else false
                    else -> if (verifySettingsPin(prefs, current)) {
                        prefs.edit().clear().apply()
                        onEnabledChanged(false)
                        mode = null
                        true
                    } else false
                }
            },
            onDismiss = { mode = null },
        )
    }
}

@Composable
private fun SettingsPinEditor(
    mode: String,
    verify: (String) -> Boolean,
    onSave: (String, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val needsNew = mode != "disable"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF07121E),
        title = {
            Text(
                when (mode) {
                    "create" -> "PIN OLUŞTUR"
                    "change" -> "PIN DEĞİŞTİR"
                    else -> "PIN KİLİDİNİ KAPAT"
                },
                color = Color.White,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != "create") PinField("Mevcut PIN", current) { current = it }
                if (needsNew) {
                    PinField("Yeni 4 haneli PIN", first) { first = it }
                    PinField("Yeni PIN tekrar", second) { second = it }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when {
                    mode != "create" && !verify(current) -> "Mevcut PIN hatalı"
                    needsNew && first.length != 4 -> "PIN dört haneli olmalı"
                    needsNew && first != second -> "Yeni PIN'ler eşleşmiyor"
                    !onSave(current, first) -> "İşlem tamamlanamadı"
                    else -> ""
                }
            }) {
                Text(if (mode == "disable") "KİLİDİ KAPAT" else "KAYDET")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İPTAL") } },
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

private fun saveSettingsPin(prefs: android.content.SharedPreferences, pin: String) {
    val salt = java.util.UUID.randomUUID().toString()
    prefs.edit()
        .putBoolean("enabled", true)
        .putString("salt", salt)
        .putString("hash", settingsPinHash(pin, salt))
        .apply()
}

private fun verifySettingsPin(prefs: android.content.SharedPreferences, pin: String): Boolean {
    val salt = prefs.getString("salt", null) ?: return false
    val expected = prefs.getString("hash", null) ?: return false
    return settingsPinHash(pin, salt) == expected
}

private fun settingsPinHash(pin: String, salt: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

@Composable
private fun SettingsHubCard(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF07121E) else Color(0xFF07121E).copy(alpha = .55f)
        ),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = .45f) else Color(0xFF294052))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.padding(end = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    color = if (enabled) accent else Color(0xFF607584),
                    fontSize = if (icon.length <= 2) 28.sp else 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color(0xFF748896),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitle,
                    color = if (enabled) Color(0xFF8FA6BA) else Color(0xFF536875),
                    fontSize = 10.sp
                )
            }
            Text(
                text = if (enabled) "›" else "•",
                color = if (enabled) accent else Color(0xFF536875),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
