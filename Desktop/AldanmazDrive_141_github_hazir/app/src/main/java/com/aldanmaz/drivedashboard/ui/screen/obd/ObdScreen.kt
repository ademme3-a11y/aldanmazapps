package com.aldanmaz.drivedashboard.ui.screen.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import com.aldanmaz.drivedashboard.BuildConfig

private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun ObdScreen(viewModel: ObdViewModel, onBack: () -> Unit, onWarningHistory: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshDevices() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshDevices() }

    fun ensurePermissionsForMode(mode: ObdConnectionMode = state.connectionMode) {
        val required = buildList {
            if (mode != ObdConnectionMode.MULTIMEDIA_RECEIVER && Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.BLUETOOTH_CONNECT)

            if (Build.VERSION.SDK_INT >= 37 &&
                ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
            ) add(ACCESS_LOCAL_NETWORK_PERMISSION)

            if (mode == ObdConnectionMode.PHONE_BRIDGE && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (required.isNotEmpty()) permissionLauncher.launch(required.toTypedArray())
        else viewModel.refreshDevices()
    }

    LaunchedEffect(state.connectionMode) { ensurePermissionsForMode() }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF02070D))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ObdButton("‹ GERİ", onBack)
            Spacer(Modifier.width(12.dp))
            Text("OBD / BAĞLANTI • ${BuildConfig.VERSION_NAME.substringBefore('-')}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (state.isConnected) "● BAĞLI" else if (state.isConnecting) "◌ BAĞLANIYOR" else "○ PASİF",
                color = if (state.isConnected) Color(0xFF35E67A) else Color(0xFF6FCFE8),
                fontWeight = FontWeight.Bold
            )
        }

        ObdCard {
            Text("OBD BAĞLANTI YÖNTEMİ", color = Color.White, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObdModeButton("DOĞRUDAN BT", state.connectionMode == ObdConnectionMode.DIRECT_BLUETOOTH, Modifier.weight(1f)) {
                    viewModel.setConnectionMode(ObdConnectionMode.DIRECT_BLUETOOTH)
                }
                ObdModeButton("GALAXY A25 KÖPRÜ", state.connectionMode == ObdConnectionMode.PHONE_BRIDGE, Modifier.weight(1f)) {
                    viewModel.setConnectionMode(ObdConnectionMode.PHONE_BRIDGE)
                }
                ObdModeButton("MULTİMEDYA ALICI", state.connectionMode == ObdConnectionMode.MULTIMEDIA_RECEIVER, Modifier.weight(1f)) {
                    viewModel.setConnectionMode(ObdConnectionMode.MULTIMEDIA_RECEIVER)
                }
            }
            Text(
                when (state.connectionMode) {
                    ObdConnectionMode.DIRECT_BLUETOOTH -> "65'teki eski yöntem: OBD doğrudan bu cihaza Bluetooth ile bağlanır."
                    ObdConnectionMode.PHONE_BRIDGE -> "Bu modu Galaxy A25'te kullanın: OBD → Bluetooth → telefon → hotspot → multimedya."
                    ObdConnectionMode.MULTIMEDIA_RECEIVER -> "Bu modu 10 inç multimedya cihazında kullanın: telefon hotspotundan hem internet hem OBD verisi alınır."
                },
                color = Color(0xFFBDEFFF), fontSize = 10.sp
            )
        }

        when (state.connectionMode) {
            ObdConnectionMode.DIRECT_BLUETOOTH -> DirectBluetoothPanel(state, viewModel, enableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }, ensurePermission = { ensurePermissionsForMode() })
            ObdConnectionMode.PHONE_BRIDGE -> PhoneBridgePanel(state, viewModel, enableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }, ensurePermission = { ensurePermissionsForMode() })
            ObdConnectionMode.MULTIMEDIA_RECEIVER -> MultimediaReceiverPanel(state, viewModel)
        }

        state.errorMessage?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }

        ObdReportCard(state = state, onWarningHistory = onWarningHistory)

    }
}


private enum class ObdRowStatus { NORMAL, ATTENTION, NO_DATA }

private data class ObdReportRow(
    val name: String,
    val value: String?,
    val status: ObdRowStatus,
    val normalText: String,
    val attentionText: String = normalText,
)

@Composable
private fun ObdReportCard(state: ObdUiState, onWarningHistory: () -> Unit) {
    val d = state.liveData
    val voltage = d.batteryVoltage ?: d.controlModuleVoltage
    val dtcText = d.dtcCodes.takeIf { it.isNotEmpty() }?.joinToString(", ")
    val rows = listOf(
        ObdReportRow(
            "ARIZA / DTC",
            dtcText ?: d.milOn?.let { if (it) "MIL AÇIK" else "Kod yok" },
            when { d.milOn == true || d.dtcCodes.isNotEmpty() -> ObdRowStatus.ATTENTION; d.milOn == null && d.dtcCodes.isEmpty() -> ObdRowStatus.NO_DATA; else -> ObdRowStatus.NORMAL },
            "Motor ECU arıza hafızasını ve motor arıza lambasını izler.",
            "Arıza kodlarını not edin; OBD sayfasındaki kodlarla uygun zamanda servis/teşhis kontrolü yapın."
        ),
        ObdReportRow("MOTOR SUYU", d.coolantCelsius?.let { "$it °C" }, when { d.coolantCelsius == null -> ObdRowStatus.NO_DATA; d.coolantCelsius >= 105 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Motorun çalışma sıcaklığını izler.", "Sıcaklık yüksek. Güvenli yerde yükü azaltın/durun; soğutma sıvısı, fan ve kaçak kontrolü yaptırın."),
        ObdReportRow("AKÜ / ŞARJ", voltage?.let { "%.2f V".format(it) }, when { voltage == null -> ObdRowStatus.NO_DATA; voltage < 11.5 || voltage > 15.2 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Akü ve alternatör/şarj sisteminin gerilimini gösterir.", "Voltaj normal aralık dışında. Akü kutupları, alternatör ve şarj sistemi kontrol edilmeli."),
        ObdReportRow("MOTOR YAĞI", d.engineOilCelsius?.let { "$it °C" }, when { d.engineOilCelsius == null -> ObdRowStatus.NO_DATA; d.engineOilCelsius >= 125 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Motor yağı sıcaklığını izler.", "Yağ sıcaklığı yüksek. Motor yükünü azaltın; yağ seviyesi/kalitesi ve soğutma sistemi kontrol edilmeli."),
        ObdReportRow("YAKIT RAYI", (d.absoluteFuelRailPressureKpa ?: d.fuelRailPressureKpa)?.let { "${it / 100} bar" }, if ((d.absoluteFuelRailPressureKpa ?: d.fuelRailPressureKpa) == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Common-rail yakıt basıncını gösterir; yükle birlikte değişmesi normaldir."),
        ObdReportRow("EGR KOMUTU", d.commandedEgrPercent?.let { "%$it" }, if (d.commandedEgrPercent == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "ECU'nun EGR valfinden istediği açılma oranıdır."),
        ObdReportRow("EGR HATASI", d.egrErrorPercent?.let { "%$it" }, when { d.egrErrorPercent == null -> ObdRowStatus.NO_DATA; kotlin.math.abs(d.egrErrorPercent) >= 20 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "İstenen ve gerçekleşen EGR davranışı arasındaki sapmayı gösterir.", "EGR sapması yüksek. EGR valfi, kurum birikimi, vakum/elektrik bağlantısı ve emme hattı kontrol edilmeli."),
        ObdReportRow("MOTOR YÜKÜ", d.engineLoadPercent?.let { "%$it" }, if (d.engineLoadPercent == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Motorun o anda ne kadar yüklendiğini gösterir."),
        ObdReportRow("DEVİR", d.rpm?.let { "$it rpm" }, when { d.rpm == null -> ObdRowStatus.NO_DATA; d.rpm > 5200 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Motorun dakikadaki dönüş hızıdır.", "Devir olağandışı yüksek. Vites ve sürüş koşullarını kontrol edin."),
        ObdReportRow("ARAÇ HIZI • 010D", d.vehicleSpeedKmh?.let { "$it km/sa" }, if (d.vehicleSpeedKmh == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "ECU/CAN üzerinden gelen gerçek araç hızıdır."),
        ObdReportRow("MAF", d.mafGramsPerSecond?.let { "%.1f g/s".format(it) }, when { d.mafGramsPerSecond == null -> ObdRowStatus.NO_DATA; d.rpm != null && d.rpm > 700 && d.mafGramsPerSecond <= 0.1 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Motora giren hava kütlesini ölçer.", "Motor çalışırken hava akışı yok/çok düşük. MAF sensörü, soket ve emme hattı kontrol edilmeli."),
        ObdReportRow("MAP / EMME", d.manifoldPressureKpa?.let { "$it kPa" }, if (d.manifoldPressureKpa == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Emme manifoldu mutlak basıncını gösterir; turbo hesabında kullanılır."),
        ObdReportRow("TURBO", d.boostPressureKpa?.let { "$it kPa" }, if (d.boostPressureKpa == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Atmosfer basıncı çıkarıldıktan sonraki yaklaşık turbo basıncıdır."),
        ObdReportRow("EMME HAVASI", d.intakeAirCelsius?.let { "$it °C" }, when { d.intakeAirCelsius == null -> ObdRowStatus.NO_DATA; d.intakeAirCelsius > 85 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Motora giren havanın sıcaklığıdır.", "Emme havası olağandışı sıcak. Hava yolu/intercooler ve sensör kontrol edilmeli."),
        ObdReportRow("YAKIT SEVİYESİ • 012F", d.fuelLevelPercent?.let { "%$it" }, when { d.fuelLevelPercent == null -> ObdRowStatus.NO_DATA; d.fuelLevelPercent <= 12 -> ObdRowStatus.ATTENTION; else -> ObdRowStatus.NORMAL }, "Depodaki yakıt seviyesini ECU standart PID ile veriyorsa gösterir.", "Yakıt düşük. Uygun ilk fırsatta yakıt alın."),
        ObdReportRow("YAKIT DEBİSİ • 015E", d.engineFuelRateLitersHour?.let { "%.2f L/sa • %s".format(it, d.engineFuelRateSource ?: "OBD") }, if (d.engineFuelRateLitersHour == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Motorun anlık yakıt debisidir. OBD-HESAP yazıyorsa hava verilerinden yaklaşık hesaplanmıştır."),
        ObdReportRow("GAZ PEDALI", d.acceleratorPedalPercent?.let { "%$it" }, if (d.acceleratorPedalPercent == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Gaz pedalı konumunu gösterir."),
        ObdReportRow("ECU VOLTAJI", d.controlModuleVoltage?.let { "%.2f V".format(it) }, if (d.controlModuleVoltage == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Motor kontrol ünitesinin gördüğü besleme gerilimidir."),
        ObdReportRow("DTC SONRASI KM", d.distanceSinceClearKm?.let { "$it km" }, if (d.distanceSinceClearKm == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Arıza kodları silindikten sonra gidilen mesafedir."),
        ObdReportRow("MIL İLE KM", d.distanceWithMilKm?.let { "$it km" }, if (d.distanceWithMilKm == null) ObdRowStatus.NO_DATA else if (d.distanceWithMilKm > 0 && d.milOn == true) ObdRowStatus.ATTENTION else ObdRowStatus.NORMAL, "Motor arıza lambası açıkken gidilen mesafedir.", "MIL aktif. Arıza kodlarını inceleyin ve geciktirmeden kontrol yaptırın."),
        ObdReportRow("BAROMETRİK", d.barometricPressureKpa?.let { "$it kPa" }, if (d.barometricPressureKpa == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Atmosfer basıncıdır; turbo basıncının doğru yorumlanmasına yardım eder."),
        ObdReportRow("ORTAM SICAKLIĞI", d.ambientAirCelsius?.let { "$it °C" }, if (d.ambientAirCelsius == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "ECU'nun gördüğü dış ortam sıcaklığıdır."),
        ObdReportRow("MOTOR ÇALIŞMA", d.engineRuntimeSeconds?.let { "${it / 60} dk" }, if (d.engineRuntimeSeconds == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Motorun bu çalıştırmadan beri geçen çalışma süresidir."),
        ObdReportRow("GERÇEK TORK", d.actualEngineTorquePercent?.let { "%$it" }, if (d.actualEngineTorquePercent == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "ECU'nun bildirdiği gerçek motor tork yüzdesidir."),
        ObdReportRow("ENJEKSİYON ZAMANI", d.fuelInjectionTimingDegrees?.let { "%.1f°".format(it) }, if (d.fuelInjectionTimingDegrees == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "Yakıt enjeksiyon zamanlamasını gösterir; üretici verisiyle birlikte yorumlanır."),
        ObdReportRow("DPF FARK BASINCI", d.dpf.differentialPressureRaw, if (d.dpf.differentialPressureRaw == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "DPF fark basıncı ham verisidir; üreticiye özel dönüşüm gerekebilir."),
        ObdReportRow("DPF SICAKLIĞI", d.dpf.temperatureRaw, if (d.dpf.temperatureRaw == null) ObdRowStatus.NO_DATA else ObdRowStatus.NORMAL, "DPF sıcaklığı ham verisidir; üreticiye özel dönüşüm gerekebilir."),
    )
    val attentionCount = rows.count { it.status == ObdRowStatus.ATTENTION }

    ObdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OBD DURUM RAPORU", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    if (!state.isConnected) "SON ALINAN VERİ • ${state.lastLiveDataTime ?: "veri yok"}"
                    else if (attentionCount == 0) "TÜM ALINAN DEĞERLER NORMAL" else "$attentionCount DEĞER DİKKAT GEREKTİRİYOR",
                    color = if (attentionCount == 0 && state.isConnected) Color(0xFF35E67A) else if (attentionCount > 0) Color(0xFFFF6B6B) else Color(0xFFFFD27A),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
            ObdButton("UYARI GEÇMİŞİ", onWarningHistory)
        }
        ObdReportHeader()
        rows.forEach { ObdReportLine(it) }
        Text("NORMAL = bilinen güvenlik/arıza eşiği aşılmadı; servis teşhisi değildir. Veri yok, araç arızası anlamına gelmez; ilgili PID ECU tarafından verilmiyor olabilir.", color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
    }
}

@Composable
private fun ObdReportHeader() {
    Row(Modifier.fillMaxWidth().background(Color(0xFF0C2633)).padding(horizontal = 8.dp, vertical = 7.dp)) {
        Text("OBD VERİSİ", color = Color(0xFF6FCFE8), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1.25f))
        Text("ANLIK DEĞER", color = Color(0xFF6FCFE8), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1.15f))
        Text("DURUM", color = Color(0xFF6FCFE8), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(.75f))
        Text("NE İŞE YARAR / YAPILACAK", color = Color(0xFF6FCFE8), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(3.1f))
    }
}

@Composable
private fun ObdReportLine(row: ObdReportRow) {
    val statusText = when (row.status) { ObdRowStatus.NORMAL -> "NORMAL"; ObdRowStatus.ATTENTION -> "DİKKAT"; ObdRowStatus.NO_DATA -> "VERİ YOK" }
    val statusColor = when (row.status) { ObdRowStatus.NORMAL -> Color(0xFF35E67A); ObdRowStatus.ATTENTION -> Color(0xFFFF5B5B); ObdRowStatus.NO_DATA -> Color(0xFF91A6B3) }
    val detail = if (row.status == ObdRowStatus.ATTENTION) row.attentionText else row.normalText
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF081722)).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.25f))
        Text(row.value ?: "--", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.15f))
        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(.75f))
        Text(detail, color = if (row.status == ObdRowStatus.ATTENTION) Color(0xFFFFC1C1) else Color(0xFFBDEFFF), fontSize = 9.sp, modifier = Modifier.weight(3.1f))
    }
}

@Composable
private fun DirectBluetoothPanel(
    state: ObdUiState,
    viewModel: ObdViewModel,
    enableBluetooth: () -> Unit,
    ensurePermission: () -> Unit
) {
    ObdCard {
        Text("DOĞRUDAN BLUETOOTH", color = Color.White, fontWeight = FontWeight.Bold)
        Text(if (state.bluetoothEnabled) "Bluetooth açık" else "Bluetooth kapalı", color = Color(0xFFBDEFFF))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.bluetoothEnabled) ObdButton("BLUETOOTH AÇ", enableBluetooth)
            ObdButton("CİHAZLARI YENİLE", ensurePermission)
        }
        ObdDeviceList(state, viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isConnected) ObdButton("BAĞLANTIYI KES", viewModel::disconnect)
            else ObdButton(if (state.isConnecting) "BAĞLANIYOR…" else "BAĞLAN", viewModel::connectSelected)
        }
        ConnectionInfo(state)
    }
}

@Composable
private fun PhoneBridgePanel(
    state: ObdUiState,
    viewModel: ObdViewModel,
    enableBluetooth: () -> Unit,
    ensurePermission: () -> Unit
) {
    val context = LocalContext.current
    ObdCard {
        Text("GALAXY A25 • OBD KÖPRÜ", color = Color.White, fontWeight = FontWeight.Bold)
        Text("OBD → BT → Galaxy A25 → Hotspot/Wi-Fi → Multimedya OBD sayfası", color = Color(0xFF35E67A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Telefonun multimedya ile Bluetooth telefon görüşmesi / ses bağlantısına dokunulmaz. Mobil veri açık kalır; hotspot aynı anda multimedya internetini ve OBD yerel veri kanalını taşır.", color = Color(0xFFBDEFFF), fontSize = 10.sp)

        NetworkLine("MOBİL İNTERNET", state.networkStatus.cellularInternet, if (state.networkStatus.cellularInternet) "Aktif" else "Kontrol edin")
        NetworkLine("HOTSPOT / YEREL AĞ", state.networkStatus.privateIpv4.isNotEmpty(), state.networkStatus.privateIpv4.joinToString().ifBlank { "Hotspot'u açın" })
        NetworkLine("MULTİMEDYA OBD İSTEMCİSİ", state.phoneBridge.clientCount > 0, "${state.phoneBridge.clientCount} cihaz bağlı")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObdButton("HOTSPOT AYARLARI") {
                runCatching { context.startActivity(Intent("android.settings.TETHER_SETTINGS")) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            }
            ObdButton("AĞI YENİLE", viewModel::refreshDevices)
        }

        Text(if (state.bluetoothEnabled) "Bluetooth açık" else "Bluetooth kapalı", color = Color(0xFFBDEFFF))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.bluetoothEnabled) ObdButton("BLUETOOTH AÇ", enableBluetooth)
            ObdButton("OBD CİHAZLARINI YENİLE", ensurePermission)
        }
        ObdDeviceList(state, viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.phoneBridge.running) ObdButton("KÖPRÜYÜ DURDUR", viewModel::stopPhoneBridge)
            else ObdButton(if (state.isConnecting) "KÖPRÜ BAŞLIYOR…" else "KÖPRÜYÜ BAŞLAT", viewModel::connectSelected)
        }
        if (state.phoneBridge.running) {
            Text(
                if (state.phoneBridge.obdConnected) "● OBD telefona bağlı • TCP ${com.aldanmaz.drivedashboard.data.obd.bridge.ObdBridgeCodec.PORT} hazır"
                else "◌ OBD yeniden bağlanıyor…",
                color = if (state.phoneBridge.obdConnected) Color(0xFF35E67A) else Color(0xFFFFD27A),
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
        ConnectionInfo(state)
    }
}

@Composable
private fun MultimediaReceiverPanel(state: ObdUiState, viewModel: ObdViewModel) {
    ObdCard {
        Text("10 İNÇ MULTİMEDYA • TELEFON ALICISI", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Multimedya Galaxy A25'in hotspotuna bağlı olmalı. Aynı Wi-Fi bağlantısı normal interneti ve OBD verisini birlikte taşır.", color = Color(0xFFBDEFFF), fontSize = 10.sp)
        NetworkLine("TELEFON HOTSPOT WI-FI", state.networkStatus.wifiConnected, if (state.networkStatus.wifiConnected) "Bağlı" else "Bağlı değil")
        NetworkLine("MULTİMEDYA İNTERNETİ", state.networkStatus.validatedInternet, if (state.networkStatus.validatedInternet) "İnternet var" else "İnternet doğrulanmadı")
        NetworkLine("TELEFON AĞ GEÇİDİ", state.networkStatus.wifiGatewayIp != null, state.networkStatus.wifiGatewayIp ?: "Otomatik bulunamadı")
        NetworkLine("OBD KÖPRÜSÜ", state.connectedAddress == "PHONE_HOTSPOT_BRIDGE", state.receiverConnectedHost ?: "Bekleniyor")

        OutlinedTextField(
            value = state.receiverHost,
            onValueChange = viewModel::setReceiverHost,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Telefon IP (boş bırakırsanız otomatik)") },
            supportingText = { Text("Normalde hotspot ağ geçidi otomatik bulunur. Manuel IP sadece gerekirse kullanılır.") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF22D7F3), unfocusedBorderColor = Color(0xFF245569),
                focusedLabelColor = Color(0xFF6FCFE8), unfocusedLabelColor = Color(0xFF91A6B3),
                focusedSupportingTextColor = Color(0xFF91A6B3), unfocusedSupportingTextColor = Color(0xFF91A6B3)
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObdButton("AĞI YENİLE", viewModel::refreshDevices)
            if (state.receiverDesired) ObdButton("OTOMATİK ALICI AÇIK • DURDUR", viewModel::disconnect)
            else ObdButton("ALICIYI ŞİMDİ BAŞLAT", viewModel::connectSelected)
        }
        ConnectionInfo(state)
        Text("${BuildConfig.VERSION_NAME.substringBefore('-')}: MULTİMEDYA ALICI modu seçiliyken telefon köprüsü otomatik aranır; bağlantı düşerse yeniden bağlanır. Telefonun görüşme/ses Bluetooth bağlantısı bağımsız kalır.", color = Color.White.copy(alpha = .65f), fontSize = 9.sp)
    }
}

@Composable
private fun ObdDeviceList(state: ObdUiState, viewModel: ObdViewModel) {
    Text("Eşleşmiş cihazlar", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
    if (state.devices.isEmpty()) {
        Text("Eşleşmiş Bluetooth cihazı bulunamadı. Önce Android Bluetooth ayarlarından ELM327 / OBD cihazını eşleştirin.", color = Color(0xFFFFD27A), fontSize = 11.sp)
    }
    state.devices.forEach { device ->
        val selected = device.address == state.selectedAddress
        Card(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectDevice(device.address) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (selected) Color(0x3322D7F3) else Color(0xFF081722)),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF22D7F3) else Color(0x553ED7F2))
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(device.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(device.address, color = Color(0xFF9BC7D2), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ConnectionInfo(state: ObdUiState) {
    state.connectionInfo?.let { info ->
        Text("✓ ${info.adapterIdentity} • ${info.protocol}", color = Color(0xFF35E67A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
    state.lastEvaluation?.let { evaluation ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081722)),
            border = BorderStroke(1.dp, Color(0xFF245569))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SON OBD DEĞERLENDİRMESİ", color = Color(0xFF6FCFE8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(evaluation, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(state.lastEvaluationTime.orEmpty(), color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun NetworkLine(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "●" else "○", color = if (ok) Color(0xFF35E67A) else Color(0xFFFFD27A), fontSize = 12.sp)
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color(0xFF6FCFE8), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun ObdCard(content: @Composable ColumnScope.() -> Unit) = Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF07111C)),
    border = BorderStroke(1.dp, Color(0xFF1A4455))
) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun ObdValue(label: String, value: String?, modifier: Modifier = Modifier) = Card(
    modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF081722))
) {
    Column(Modifier.padding(10.dp)) {
        Text(label, color = Color(0xFF6FCFE8), fontSize = 9.sp)
        Text(value ?: "Desteklenmiyor", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ObdButton(text: String, onClick: () -> Unit) = Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123445)),
    shape = RoundedCornerShape(12.dp)
) {
    Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ObdModeButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = modifier,
    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFF0C6275) else Color(0xFF123445)),
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF22D7F3) else Color(0xFF245569)),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp)
) {
    Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}
