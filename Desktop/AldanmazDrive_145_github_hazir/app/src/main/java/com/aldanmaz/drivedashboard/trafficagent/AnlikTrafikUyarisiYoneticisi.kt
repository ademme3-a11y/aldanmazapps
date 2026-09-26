package com.aldanmaz.drivedashboard.trafficagent
import com.aldanmaz.drivedashboard.MainActivity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

object AnlikTrafikUyarisiYoneticisi {

    private const val UYARI_KANALI =
        "anlik_trafik_uyarisi_v1"

    private const val UYARI_BILDIRIM_NO =
        2002

    private var sonUyariImzasi: String? = null

    fun rotaSonucunuKontrolEt(
        context: Context,
        hedefAdi: String,
        sonuc: RotaTrafikSonucu
    ) {
        val kontrolSiniri =
            minOf(
                20_000.0,
                sonuc.toplamMesafeMetre.toDouble()
            )

        val kritikOlaylar =
            sonuc.olaylar.filter { olay ->

                val trafikHizi =
                    olay.etkiliHizKmSaat

                val kritikDurum =
                    olay.kategori.equals(
                        "ROAD_CLOSURE",
                        ignoreCase = true
                    ) ||
                            olay.kategori.equals(
                                "ROAD_WORK",
                                ignoreCase = true
                            ) ||
                            olay.gecikmeBuyuklugu >= 2 ||
                            (
                                    trafikHizi != null &&
                                            trafikHizi <= 30
                                    )

                olay.baslangicMetre < kontrolSiniri &&
                        olay.bitisMetre >= 0.0 &&
                        kritikDurum
            }

        val enYakinOlay =
            kritikOlaylar.minWithOrNull(
                compareBy<RotaTrafikOlayi> {
                    it.baslangicMetre
                }.thenByDescending {
                    it.gecikmeBuyuklugu
                }
            )

        if (enYakinOlay == null) {
            sonUyariImzasi = null
            TrafikGeminiDurumu.temizle(context)
            return
        }

        TrafikGeminiDurumu.kaydet(context, hedefAdi, enYakinOlay)

        val uyariImzasi =
            buildString {
                append(
                    enYakinOlay.yolAdi
                        ?: "bilinmeyen_yol"
                )

                append(":")
                append(enYakinOlay.kategori)

                append(":")
                append(enYakinOlay.gecikmeBuyuklugu)

                append(":")
                append(
                    (
                            enYakinOlay.baslangicMetre /
                                    500.0
                            ).roundToInt()
                )

                append(":")
                append(
                    enYakinOlay
                        .etkiliHizKmSaat
                        ?.div(5)
                )

                append(":")
                append(
                    enYakinOlay.gecikmeSaniye /
                            300
                )
            }

        if (uyariImzasi == sonUyariImzasi) {
            return
        }

        sonUyariImzasi = uyariImzasi

        uyariGoster(
            context = context,
            hedefAdi = hedefAdi,
            olay = enYakinOlay
        )
    }

    private fun uyariGoster(
        context: Context,
        hedefAdi: String,
        olay: RotaTrafikOlayi
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            val bildirimIzniVar =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (!bildirimIzniVar) {
                return
            }
        }

        bildirimKanaliOlustur(context)

        val yolAdi =
            olay.yolAdi
                ?.takeIf { it.isNotBlank() }
                ?: "İlerideki yol"

        val baslik =
            "$yolAdi • $hedefAdi istikameti"

        val uzaklikMetni =
            uzaklikMetniOlustur(
                olay.baslangicMetre
            )

        val durumMetni =
            trafikDurumuOlustur(olay)

        val ayrintiliMetin =
            buildString {
                append("$uzaklikMetni ileride ")
                append(durumMetni)

                olay.etkiliHizKmSaat
                    ?.let { trafikHizi ->
                        append(
                            " Trafik hızı " +
                                    "$trafikHizi km/sa."
                        )
                    }

                if (olay.gecikmeSaniye >= 60) {
                    val gecikmeDakika =
                        (
                                olay.gecikmeSaniye +
                                        59
                                ) / 60

                    append(
                        " Yaklaşık " +
                                "$gecikmeDakika dakika " +
                                "gecikme."
                    )
                }
            }

        val uygulamayiAcIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val bekleyenIntent =
            PendingIntent.getActivity(
                context,
                UYARI_BILDIRIM_NO,
                uygulamayiAcIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val bildirim =
            NotificationCompat.Builder(
                context,
                UYARI_KANALI
            )
                .setSmallIcon(
                    android.R.drawable
                        .ic_dialog_alert
                )
                .setContentTitle(baslik)
                .setContentText(ayrintiliMetin)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(ayrintiliMetin)
                )
                .setContentIntent(bekleyenIntent)
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_STATUS
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(10_000L)
                .build()

        try {
            NotificationManagerCompat
                .from(context)
                .notify(
                    UYARI_BILDIRIM_NO,
                    bildirim
                )
        } catch (_: SecurityException) {
            // Bildirim izni kapatılmış olabilir.
        }
    }

    private fun trafikDurumuOlustur(
        olay: RotaTrafikOlayi
    ): String {
        val trafikHizi =
            olay.etkiliHizKmSaat

        return when {
            olay.kategori.equals(
                "ROAD_CLOSURE",
                ignoreCase = true
            ) ->
                "yol trafiğe kapalı."

            olay.kategori.equals(
                "ROAD_WORK",
                ignoreCase = true
            ) ->
                "yol çalışması nedeniyle trafik yavaş."

            trafikHizi != null &&
                    trafikHizi <= 5 ->
                "trafik tamamen tıkalı."

            trafikHizi != null &&
                    trafikHizi <= 15 ->
                "trafik hızı çok yavaş."

            trafikHizi != null &&
                    trafikHizi <= 30 ->
                "yoğun ve yavaş trafik var."

            else ->
                "ciddi trafik yoğunluğu var."
        }
    }

    private fun uzaklikMetniOlustur(
        metre: Double
    ): String {
        return if (metre < 1_000.0) {
            "${metre.roundToInt()} metre"
        } else {
            String.format(
                Locale.forLanguageTag("tr-TR"),
                "%.1f km",
                metre / 1_000.0
            )
        }
    }

    private fun bildirimKanaliOlustur(
        context: Context
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val kanal =
                NotificationChannel(
                    UYARI_KANALI,
                    "Anlık trafik uyarıları",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        "İlerideki ciddi trafik sorunlarını açılır bildirimle gösterir."

                    enableVibration(true)
                    setShowBadge(false)

                    lockscreenVisibility =
                        Notification.VISIBILITY_PUBLIC
                }

            val bildirimYoneticisi =
                context.getSystemService(
                    NotificationManager::class.java
                )

            bildirimYoneticisi
                .createNotificationChannel(kanal)
        }
    }
}
