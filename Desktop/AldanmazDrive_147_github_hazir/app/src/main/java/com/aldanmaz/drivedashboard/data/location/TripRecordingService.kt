package com.aldanmaz.drivedashboard.data.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aldanmaz.drivedashboard.MainActivity

/**
 * Sürüş kaydı hizmeti: uygulama arka plana alındığında veya ekran kapandığında Android'in
 * konum akışını ve uygulamayı durdurmasını engeller; böylece km / yolculuk / harcama kaydı kesilmez.
 * Konumu kendisi okumaz; yalnızca DriveDashboardViewModel'in konum akışını canlı tutan bir "ön plan hizmeti"dir.
 * Görev (uygulama) kapatılınca hizmet de kapanır (stopWithTask).
 */
class TripRecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sürüş kaydı", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("AldDrive sürüş kaydı açık")
            .setContentText("Km ve yolculuk bilgileri arka planda da kaydedilir")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "ald_trip_recording"
        const val NOTIFICATION_ID = 1702
    }
}
