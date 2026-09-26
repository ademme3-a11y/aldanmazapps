package com.aldanmaz.drivedashboard.data.traffic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aldanmaz.drivedashboard.MainActivity
import com.aldanmaz.drivedashboard.data.alert.CentralVoiceAlertManager
import com.aldanmaz.drivedashboard.data.alert.RecordedVoiceClip
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertPriority
import com.aldanmaz.drivedashboard.data.alert.VoiceAlertType
import com.aldanmaz.drivedashboard.data.ai.GeminiLiveManager
import com.aldanmaz.drivedashboard.trafficagent.AjanAyarlari
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Eski Trafik Ajanı davranışının Aldanmaz Drive içine taşınmış arka plan katmanı.
 * Kullanıcı AJAN'ı açtığında, uygulama başka ekrandayken de konumu takip eder;
 * 2 dakikada bir veya 2 km ilerleyince önümüzdeki 20 km'yi tekrar tarar.
 */
class TrafficAgentBackgroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "ald_traffic_agent"
        private const val NOTIFICATION_ID = 1701
        private const val CHECK_INTERVAL_MS = 2 * 60 * 1000L
        private const val DENSE_CHECK_INTERVAL_MS = 60 * 1000L
        private const val RECHECK_DISTANCE_METERS = 2_000f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = TrafficAgentRepository()
    private lateinit var callback: LocationCallback
    private var lastCheckAt = 0L
    private var lastCheckLocation: Location? = null
    private var checkJob: Job? = null
    private var lastHeading: Float? = null
    private var trafficWasDense = false
    private var slowSince = 0L

    override fun onCreate() {
        super.onCreate()
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return
        }
        createChannel()
        startAsForeground("Trafik Ajanı hazır • GPS bekleniyor")
        startLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = getSharedPreferences("traffic_agent", MODE_PRIVATE).getBoolean("active", false)
        if (!active) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            updateNotification("Konum izni yok • Trafik Ajanı bekliyor")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onLocation)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            updateNotification("Konum izni gerekli")
        }
    }

    private fun onLocation(location: Location) {
        if (location.hasBearing() && (!location.hasSpeed() || location.speed * 3.6f >= 2f)) {
            lastHeading = location.bearing
        }
        val heading = lastHeading ?: return
        if (checkJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val interval = if (trafficWasDense) DENSE_CHECK_INTERVAL_MS else CHECK_INTERVAL_MS
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        if (speedKmh in 1f..15f) { if (slowSince == 0L) slowSince = now } else slowSince = 0L
        val suddenSlowdown = slowSince > 0L && now - slowSince >= 30_000L && now - lastCheckAt >= 30_000L
        val timeDue = lastCheckAt == 0L || now - lastCheckAt >= interval
        val distanceDue = lastCheckLocation?.distanceTo(location)?.let { it >= RECHECK_DISTANCE_METERS } ?: true
        if (!timeDue && !distanceDue && !suddenSlowdown) return

        checkJob = scope.launch {
            updateNotification("Önümüzdeki 20 km taranıyor…")
            val result = repository.checkAhead(location.latitude, location.longitude, heading, 20.0)
            if (result == null) {
                updateNotification("Trafik verisi alınamadı • tekrar denenecek")
                return@launch
            }
            lastCheckAt = System.currentTimeMillis()
            slowSince = 0L
            trafficWasDense = result.delaySeconds >= 120 || result.incidentCount > 0
            lastCheckLocation = Location(location)
            val text = when {
                result.delaySeconds >= 120 -> "Önümüzdeki 20 km • +${max(1, result.delaySeconds / 60)} dk • ${result.incidentCount} olay"
                result.incidentCount > 0 -> "Önümüzdeki 20 km • ${result.incidentCount} trafik olayı"
                else -> "Önümüzdeki 20 km normal"
            }
            updateNotification(text)
            if (result.delaySeconds >= 120 || result.incidentCount > 0) {
                val dynamicText = if (result.delaySeconds >= 120) {
                    "${max(1, result.delaySeconds / 60)} dakika gecikme."
                } else if (result.incidentCount > 0) {
                    "${result.incidentCount} trafik olayı."
                } else {
                    null
                }

                if (!AjanAyarlari.sesliUyariAcikMi(this@TrafficAgentBackgroundService)) {
                    GeminiLiveManager.getInstance(this@TrafficAgentBackgroundService).announceTraffic(
                        "Trafik Ajanı uyarısı. Sürücüye kısa ve sakin biçimde söyle: " +
                            (dynamicText ?: "Önünüzde trafik olayı var.")
                    )
                } else {
                    CentralVoiceAlertManager.getInstance(this@TrafficAgentBackgroundService).playRecorded(
                        type = VoiceAlertType.TRAFFIC,
                        priority = VoiceAlertPriority.HIGH,
                        clip = if (result.delaySeconds >= 300) RecordedVoiceClip.TRAFFIC_HEAVY_INTRO
                        else if (result.delaySeconds >= 120) RecordedVoiceClip.TRAFFIC_MODERATE_INTRO
                        else RecordedVoiceClip.TRAFFIC_WARNING,
                        cooldownKey = "background_traffic_${result.severity}_${result.delaySeconds / 60}",
                        cooldownMs = CHECK_INTERVAL_MS,
                        dynamicTextAfter = dynamicText
                    )
                }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Trafik Ajanı", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("ALDANMAZ DRIVE • Trafik Ajanı")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun startAsForeground(text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(text),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    override fun onDestroy() {
        runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback) }
        checkJob?.cancel()
        super.onDestroy()
    }
}
