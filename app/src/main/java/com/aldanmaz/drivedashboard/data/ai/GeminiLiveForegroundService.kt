package com.aldanmaz.drivedashboard.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.aldanmaz.drivedashboard.MainActivity
import com.aldanmaz.drivedashboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Gemini Live oturumunu Activity yasam dongusunden ayirir.
 * Kullanici Gemini simgesinden baslattiktan sonra uygulama arka plana gitse,
 * baska bir uygulama acilsa veya ALD Drive ekrani yeniden olussa bile oturum
 * foreground service tarafindan tutulur. Yalniz kullanici kapattiginda durur.
 */
class GeminiLiveForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: GeminiLiveManager
    private lateinit var prefs: android.content.SharedPreferences
    private var reconnectJob: Job? = null
    private var stableSessionJob: Job? = null
    private var foregroundStarted = false
    private var reconnectAttempt = 0

    override fun onCreate() {
        super.onCreate()
        manager = GeminiLiveManager.getInstance(applicationContext)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()

        serviceScope.launch {
            manager.state.collectLatest { state ->
                if (foregroundStarted) {
                    updateNotification(state)
                }

                when (state.status) {
                    GeminiLiveStatus.LISTENING -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        if (stableSessionJob?.isActive != true) {
                            stableSessionJob = serviceScope.launch {
                                delay(GeminiReconnectPolicy.STABLE_SESSION_RESET_MS)
                                if (manager.state.value.status == GeminiLiveStatus.LISTENING) {
                                    reconnectAttempt = 0
                                }
                            }
                        }
                    }

                    GeminiLiveStatus.ERROR -> {
                        stableSessionJob?.cancel()
                        stableSessionJob = null
                        if (state.isRecoverableError) {
                            scheduleReconnectIfNeeded()
                        } else {
                            reconnectJob?.cancel()
                            reconnectJob = null
                        }
                    }

                    GeminiLiveStatus.NEEDS_SETUP -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        stableSessionJob?.cancel()
                        stableSessionJob = null
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.edit().putBoolean(KEY_USER_ENABLED, false).apply()
                reconnectJob?.cancel()
                reconnectJob = null
                stableSessionJob?.cancel()
                stableSessionJob = null
                manager.forceStopNow()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                prefs.edit().putBoolean(KEY_USER_ENABLED, true).apply()
                startInForeground()
                manager.start()
            }

            else -> {
                // START_STICKY ile sistem servisi yeniden olusturursa, kullanici daha once
                // acik biraktiysa oturumu tekrar kurmayi dene. Android'in mikrofon FGS
                // kurallari izin vermezse servis sessizce kapanir ve uygulama acikken tekrar baslatilir.
                if (prefs.getBoolean(KEY_USER_ENABLED, false)) {
                    runCatching {
                        startInForeground()
                        manager.start()
                    }.onFailure {
                        prefs.edit().putBoolean(KEY_USER_ENABLED, false).apply()
                        stopSelf()
                    }
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun scheduleReconnectIfNeeded() {
        if (!prefs.getBoolean(KEY_USER_ENABLED, false)) return
        if (reconnectJob?.isActive == true) return

        val delayMs = GeminiReconnectPolicy.reconnectDelayMs(reconnectAttempt)
        reconnectAttempt += 1

        reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (!prefs.getBoolean(KEY_USER_ENABLED, false)) return@launch
            manager.clearError()
            manager.start()
        }
    }

    private fun startInForeground() {
        val notification = buildNotification(manager.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification(state: GeminiLiveState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: GeminiLiveState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            8201,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, GeminiLiveForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            8202,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when (state.status) {
            GeminiLiveStatus.LISTENING -> "Dinliyor • Arka planda aktif"
            GeminiLiveStatus.CONNECTING -> "Gemini Live bağlanıyor…"
            GeminiLiveStatus.STOPPING -> "Gemini Live kapatılıyor…"
            GeminiLiveStatus.ERROR -> if (state.isRecoverableError) {
                "Gemini Live sessizce yeniden bağlanıyor…"
            } else {
                state.message
            }
            GeminiLiveStatus.NEEDS_SETUP -> state.message
            GeminiLiveStatus.IDLE -> "Gemini Live hazır"
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Aldanmaz Drive • Gemini Live")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Gemini'yi Kapat", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gemini Live",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Gemini Live arka plan konuşma ve dinleme oturumu"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foregroundStarted = false
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        stableSessionJob?.cancel()
        serviceScope.cancel()
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "gemini_live_background"
        private const val NOTIFICATION_ID = 8200
        private const val PREFS_NAME = "gemini_live_service"
        private const val KEY_USER_ENABLED = "user_enabled"

        const val ACTION_START = "com.aldanmaz.drivedashboard.gemini.START"
        const val ACTION_STOP = "com.aldanmaz.drivedashboard.gemini.STOP"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, GeminiLiveForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            // 91: X'e basıldığı anda yeniden bağlanma iznini kapat ve yerel Live
            // oturumunu doğrudan sonlandır. Service intent'i ikinci güvenlik katmanıdır.
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_USER_ENABLED, false).apply()
            GeminiLiveManager.getInstance(appContext).forceStopNow()

            val intent = Intent(appContext, GeminiLiveForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { appContext.startService(intent) }
        }

        fun isUserEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_ENABLED, false)
    }
}
