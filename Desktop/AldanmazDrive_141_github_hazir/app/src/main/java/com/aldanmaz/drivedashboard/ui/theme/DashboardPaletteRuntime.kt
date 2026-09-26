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
