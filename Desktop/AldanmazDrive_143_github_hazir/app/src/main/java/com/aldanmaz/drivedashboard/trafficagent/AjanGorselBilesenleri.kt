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
    val gecis =
        rememberInfiniteTransition(
            label = "ajan_radar_gecisi"
        )

    val donusAcisi by gecis.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = 3_200,
                        easing = LinearEasing
                    )
            ),
        label = "ajan_radar_acisi"
    )

    val guvenliTrafikPuani =
        trafikPuani?.coerceIn(0, 10)

    val puanBilgisi =
        guvenliTrafikPuani?.let { puan ->
            TrafikPuanlayici
                .puanBilgisiOlustur(puan)
        }

    val radarRengi: Color =
        when {
            !takipAktif ->
                AjanSolukYazi

            guvenliTrafikPuani == null ->
                AjanCyan

            else ->
                ajanTrafikRengi(
                    guvenliTrafikPuani
                )
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),

        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val yaricap =
                size.minDimension / 2f

            val merkez =
                Offset(
                    x = size.width / 2f,
                    y = size.height / 2f
                )

            drawCircle(
                color = AjanPanelRengi,
                radius = yaricap,
                center = merkez
            )

            for (halka in 1..4) {
                drawCircle(
                    color =
                        radarRengi.copy(
                            alpha = 0.25f
                        ),

                    radius =
                        yaricap * halka / 4f,

                    center = merkez,

                    style =
                        Stroke(
                            width = 1.dp.toPx()
                        )
                )
            }

            drawLine(
                color =
                    radarRengi.copy(
                        alpha = 0.18f
                    ),

                start =
                    Offset(
                        x = merkez.x,
                        y = 0f
                    ),

                end =
                    Offset(
                        x = merkez.x,
                        y = size.height
                    ),

                strokeWidth = 1.dp.toPx()
            )

            drawLine(
                color =
                    radarRengi.copy(
                        alpha = 0.18f
                    ),

                start =
                    Offset(
                        x = 0f,
                        y = merkez.y
                    ),

                end =
                    Offset(
                        x = size.width,
                        y = merkez.y
                    ),

                strokeWidth = 1.dp.toPx()
            )

            val gosterilenAci =
                if (takipAktif) {
                    donusAcisi
                } else {
                    -90f
                }

            rotate(
                degrees = gosterilenAci,
                pivot = merkez
            ) {
                drawArc(
                    color =
                        radarRengi.copy(
                            alpha =
                                if (takipAktif) {
                                    0.14f
                                } else {
                                    0.05f
                                }
                        ),

                    startAngle = -38f,
                    sweepAngle = 38f,
                    useCenter = true
                )

                drawLine(
                    color =
                        radarRengi.copy(
                            alpha =
                                if (takipAktif) {
                                    0.95f
                                } else {
                                    0.35f
                                }
                        ),

                    start = merkez,

                    end =
                        Offset(
                            x = merkez.x + yaricap,
                            y = merkez.y
                        ),

                    strokeWidth = 2.dp.toPx()
                )
            }

            drawCircle(
                color = radarRengi,
                radius = 5.dp.toPx(),
                center = merkez
            )
        }

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text =
                    when {
                        guvenliTrafikPuani != null ->
                            "$guvenliTrafikPuani/10"

                        takipAktif ->
                            "TARANIYOR"

                        else ->
                            "--"
                    },

                color = radarRengi,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    puanBilgisi
                        ?.durum
                        ?.uppercase()
                        ?: if (takipAktif) {
                            "TRAFİK VERİSİ BEKLENİYOR"
                        } else {
                            "AJAN BEKLEMEDE"
                        },

                color = radarRengi,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = if (takipAktif) {
                    "ÖNÜMÜZDEKİ 20 KM"
                } else {
                    "BAŞLATMAYA HAZIR"
                },

                color = AjanSolukYazi,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AjanDurumRozeti(
    takipAktif: Boolean,
    modifier: Modifier = Modifier
) {
    val durumRengi =
        if (takipAktif) {
            AjanYesil
        } else {
            AjanKirmizi
        }

    Surface(
        modifier = modifier,

        color =
            durumRengi.copy(
                alpha = 0.12f
            ),

        shape =
            RoundedCornerShape(50),

        border =
            BorderStroke(
                width = 1.dp,
                color =
                    durumRengi.copy(
                        alpha = 0.7f
                    )
            )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 7.dp
                ),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = durumRengi,
                        shape = CircleShape
                    )
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = if (takipAktif) {
                    "AJAN AKTİF"
                } else {
                    "AJAN KAPALI"
                },

                color = durumRengi,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AjanPanel(
    baslik: String,
    modifier: Modifier = Modifier,
    vurguRengi: Color = AjanCyan,
    icerik: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),

        color = AjanPanelRengi,

        shape =
            RoundedCornerShape(18.dp),

        border =
            BorderStroke(
                width = 1.dp,
                color =
                    vurguRengi.copy(
                        alpha = 0.28f
                    )
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .background(
                            color = vurguRengi,
                            shape =
                                RoundedCornerShape(4.dp)
                        )
                )

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Text(
                    text = baslik.uppercase(),
                    color = AjanYazi,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            icerik()
        }
    }
}

@Composable
fun AjanMesafeDurumSatiri(
    mesafe: String,
    durum: String,
    durumRengi: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),

        color = AjanPanelIkinciRenk,

        shape =
            RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(
                        color = durumRengi,
                        shape = CircleShape
                    )
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Text(
                text = mesafe,
                modifier = Modifier.weight(1f),
                color = AjanYazi,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = durum,
                color = durumRengi,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
        }
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