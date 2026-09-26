package com.aldanmaz.drivedashboard.ui.screen.driver

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import java.util.Locale

const val DRIVER_PREFS = "driver_profiles"

data class DriverProfile(val id: String, val name: String, val honorific: String, val photoUri: String? = null)

fun loadDrivers(context: Context): List<DriverProfile> {
    val p = context.getSharedPreferences(DRIVER_PREFS, Context.MODE_PRIVATE)
    return listOf(
        DriverProfile("1", p.getString("driver_1_name", "Mehmet")?.trim().orEmpty().ifBlank { "Mehmet" }, "Bey", p.getString("driver_1_photo_uri", null)),
        DriverProfile("2", p.getString("driver_2_name", "Nurdan")?.trim().orEmpty().ifBlank { "Nurdan" }, "Hanım", p.getString("driver_2_photo_uri", null))
    )
}

fun welcomeDriver(context: Context, driver: DriverProfile) {
    CentralVoiceAlertManager.getInstance(context).playRecordedExclusive(
        type = VoiceAlertType.COMMAND,
        priority = VoiceAlertPriority.HIGH,
        clip = if (driver.id == "1") RecordedVoiceClip.WELCOME_MEHMET else RecordedVoiceClip.WELCOME_NURDAN
    )
}

@Composable
fun DriverSelectionOverlay(
    drivers: List<DriverProfile>,
    remainingSeconds: Int = 10,
    defaultDriverName: String = drivers.firstOrNull()?.name ?: "Sürücü",
    onSelected: (DriverProfile) -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Color(0xF7020812)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(.72f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF081725)),
                border = BorderStroke(2.dp, Color(0xFF26D9FF)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SÜRÜCÜYÜ SEÇİN", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Yolculuk bu sürücünün istatistiklerine kaydedilecek", color = Color(0xFF9CB0C5), fontSize = 13.sp)
                    Text(
                        "Seçim yapılmazsa ${remainingSeconds.coerceAtLeast(0)} saniye sonra $defaultDriverName ile devam edilecek",
                        color = Color(0xFFFFB52E), fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        drivers.forEachIndexed { index, driver ->
                            Card(
                                modifier = Modifier.weight(1f).height(180.dp).clickable { onSelected(driver) },
                                colors = CardDefaults.cardColors(containerColor = if (index == 0) Color(0xFF0B3550) else Color(0xFF173248)),
                                border = BorderStroke(1.5.dp, Color(0xFF26D9FF)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DriverAvatar(driver.photoUri, driver.name, Modifier.weight(1f).fillMaxWidth())
                                    Text(driver.name.uppercase(Locale("tr", "TR")), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverAvatar(photoUri: String?, name: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(photoUri) {
        photoUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }
    Surface(
        modifier = modifier,
        color = Color(0xFF06111B),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, Color(0xFF26D9FF))
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "$name profil fotoğrafı", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(Locale("tr", "TR")), color = Color(0xFF26D9FF), fontSize = 42.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
