package com.aldanmaz.drivedashboard.trafficagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standalone Trafik Ajanı uygulamasının ekranını ALDANMAZ Drive navigasyonu içinde barındırır.
 * Trafik motoru ve ekran akışı orijinal uygulamadaki haliyle korunur; yalnızca geri dönüş çubuğu eklenir.
 */
@Composable
fun TrafficAgentHostScreen(
    onBack: () -> Unit,
    onHome: () -> Unit = onBack,
    onSettings: () -> Unit = {},
    voiceCommand: String = "",
    voiceCommandRequestId: Int = 0,
    onVoiceCommandConsumed: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AjanArkaPlan)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹ GERİ",
                    color = AjanCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "ALDANMAZ DRIVE • TRAFİK AJANI",
                color = AjanYazi,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            TrafikAjaniEkrani(
                disSesliKomut = voiceCommand,
                disSesliKomutSayaci = voiceCommandRequestId,
                onDisSesliKomutIslendi = onVoiceCommandConsumed,
                onBack = onBack,
                onHome = onHome,
                onSettings = onSettings
            )
        }
    }
}
