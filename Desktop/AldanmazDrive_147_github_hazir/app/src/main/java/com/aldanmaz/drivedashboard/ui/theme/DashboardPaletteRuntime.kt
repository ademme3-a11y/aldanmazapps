package com.aldanmaz.drivedashboard.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object DashboardPaletteRuntime {
    var accent: Color by mutableStateOf(Color(0xFF22D7F3))
    var isDay: Boolean by mutableStateOf(true)
    var isOled: Boolean by mutableStateOf(false)
    var isSunlight: Boolean by mutableStateOf(false)

    // Gündüz teması (kum / çöl): gündüz modu açıkken devrede olur.
    // Kullanıcı OLED temasını seçtiyse saf siyah tercih korunur, gündüz teması devre dışı kalır.
    // Güneş ışığı modu otomatik olarak gündüzle birlikte açıldığı için ona bakılmaz;
    // güneş ışığı modunun yüksek parlaklığı ve kalın çizgileri gündüz temasında da geçerlidir.
    val isDayTheme: Boolean get() = isDay && !isOled

    // Gündüz teması renkleri
    val dayInk = Color(0xFF2E2620)         // ana yazı
    val dayInkSoft = Color(0xFF6A4A2C)     // ikincil yazı
    val dayFrame = Color(0xFF456252)       // yeşil-zeytin çerçeve
    val dayTileTop = Color(0xFFB07B49)     // düğme üst tonu
    val dayTileBottom = Color(0xFF9A6637)  // düğme alt tonu
    val dayTileBorder = Color(0xFF6B4527)  // düğme çerçevesi

    // Gece metni, ana ekrandaki bağlantısız BT etiketinin görünen gri tonuyla aynıdır.
    val primaryText: Color get() = when {
        isSunlight -> Color.White
        isDay -> Color(0xFFF7F9FC)
        else -> Color(0xFF9AA6B3).copy(alpha = .65f)
    }
    val secondaryText: Color get() = when {
        isSunlight -> Color(0xFFD7E0E8)
        isDay -> Color(0xFF8FA6BA)
        else -> Color(0xFF9AA6B3)
    }
}
