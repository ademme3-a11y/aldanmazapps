package com.aldanmaz.drivedashboard.trafficagent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AjanArkaPlan = Color(0xFF050A0D)
val AjanPanelRengi = Color(0xFF0B151B)
val AjanPanelIkinciRenk = Color(0xFF102129)
val AjanCyan = Color(0xFF27E6D1)
val AjanYesil = Color(0xFF43F58B)
val AjanSari = Color(0xFFFFC857)
val AjanTuruncu = Color(0xFFFF8A3D)
val AjanKirmizi = Color(0xFFFF4D5E)
val AjanYazi = Color(0xFFE8F6F4)
val AjanSolukYazi = Color(0xFF8BA5A7)

@Composable
fun AjanRadar(
    takipAktif: Boolean,
    trafikPuani: Int? = null,
    modifier: Modifier = Modifier
) {
    val gecis = rememberInfiniteTransition(label = "ajan_rakam_buyume")
    val rakamBuyume by gecis.animateFloat(
        initialValue = 0.12f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 6_500
                0.12f at 0
                1f at 1_500
                1f at 6_500
            }
        ),
        label = "ajan_rakam_buyume_degeri"
    )
    val guvenliTrafikPuani = trafikPuani?.coerceIn(1, 10)
    val radarRengi = when {
        guvenliTrafikPuani != null && guvenliTrafikPuani in 1..3 -> AjanYesil
        guvenliTrafikPuani != null && guvenliTrafikPuani in 4..7 -> AjanTuruncu
        guvenliTrafikPuani != null && guvenliTrafikPuani in 8..10 -> AjanKirmizi
        else -> if (takipAktif) AjanCyan else AjanSolukYazi
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val yaricap = size.minDimension / 2f
            val merkez = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = AjanPanelRengi, radius = yaricap, center = merkez)
            for (halka in 1..4) {
                drawCircle(
                    color = radarRengi.copy(alpha = 0.20f),
                    radius = yaricap * halka / 4f,
                    center = merkez,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawLine(radarRengi.copy(alpha = 0.12f), Offset(merkez.x, 0f), Offset(merkez.x, size.height), 1.dp.toPx())
            drawLine(radarRengi.copy(alpha = 0.12f), Offset(0f, merkez.y), Offset(size.width, merkez.y), 1.dp.toPx())
        }
        val rakam = guvenliTrafikPuani?.toString() ?: "--"
        val alan = minOf(maxWidth.value, maxHeight.value)
        val yaziBoyutu = alan * if (guvenliTrafikPuani == 10) 0.76f else 1.20f
        Text(
            text = rakam,
            color = radarRengi,
            fontSize = yaziBoyutu.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.graphicsLayer(scaleX = rakamBuyume, scaleY = rakamBuyume)
        )
    }
}

fun ajanTrafikRengi(
    puan: Int
): Color {

    return when (puan.coerceIn(0, 10)) {
        0, 1, 2 ->
            AjanYesil

        3, 4 ->
            AjanSari

        5, 6 ->
            AjanTuruncu

        7, 8 ->
            AjanKirmizi

        else ->
            Color(0xFFB41432)
    }
}