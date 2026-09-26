package com.aldanmaz.drivedashboard.ui.call

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IncomingCallOverlay(
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(250.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF071520)
            ),
            border = BorderStroke(2.dp, Color(0xFF22D7F3))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GELEN ARAMA",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Telefon çalıyor",
                    color = Color(0xFF9BB3C7),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallActionButton(
                        text = "CEVAPLA",
                        background = Color(0xFF168A55),
                        onClick = onAnswer
                    )
                    Spacer(Modifier.width(28.dp))
                    CallActionButton(
                        text = "REDDET",
                        background = Color(0xFFB72F3E),
                        onClick = onReject
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    text: String,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(190.dp)
            .height(72.dp)
            .background(background, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
    }
}
