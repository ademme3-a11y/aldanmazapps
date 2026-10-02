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
    // Gündüz teması: referans ekranındaki kum/çöl taşını temel alır.
    // Gece çalışma mantığına dokunulmaz; yalnızca isDay=true iken görsel palet değişir.
    val primaryText: Color get() = when {
        isDay -> Color(0xFF2B2118)
        else -> Color(0xFF9AA6B3).copy(alpha = .65f)
    }
    val secondaryText: Color get() = when {
        isDay -> Color(0xFF5B4938)
        else -> Color(0xFF9AA6B3)
    }
}
