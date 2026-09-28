package com.aldanmaz.drivedashboard.data.obd.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.data.obd.ObdBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class ObdPhoneBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var manager: ObdBluetoothManager
    private var bridgeJob: Job? = null
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Socket>()
    @Volatile private var latestLine: String? = null

    override fun onCreate() {
        super.onCreate()
        manager = ObdBluetoothManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridge()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startInForeground()
                val address = intent?.getStringExtra(EXTRA_ADDRESS)
                    ?: getSharedPreferences("obd_settings", 0).getString("selected_address", null)
                if (address.isNullOrBlank()) {
                    ObdBridgeRuntime.update { it.copy(running = true, errorMessage = "Önce telefonda OBD cihazını seçin") }
                } else {
                    startBridge(address)
                }
            }
        }
        return START_STICKY
    }

    private fun startBridge(address: String) {
        stopBridgeJobsOnly()
        ObdBridgeRuntime.update {
            it.copy(running = true, selectedAddress = address, errorMessage = null)
        }
        startServer()
        bridgeJob = scope.launch {
            while (isActive) {
                val connect = manager.connect(address)
                if (connect.isFailure) {
                    ObdBridgeRuntime.update {
                        it.copy(obdConnected = false, connectionInfo = null,
                            errorMessage = connect.exceptionOrNull()?.message ?: "OBD Bluetooth bağlantısı kurulamadı")
                    }
                    delay(5_000)
                    continue
                }
                val info = requireNotNull(connect.getOrNull())
                ObdBridgeRuntime.update { it.copy(obdConnected = true, connectionInfo = info, errorMessage = null) }

                try {
                    while (isActive) {
                        val data = manager.readLiveData()
                        val now = System.currentTimeMillis()
                        val snapshot = ObdBridgeSnapshot(now, info, data)
                        val line = ObdBridgeCodec.encode(snapshot)
                        latestLine = line
                        ObdBridgeRuntime.update {
                            it.copy(obdConnected = true, connectionInfo = info, liveData = data,
                                lastUpdateEpochMs = now, errorMessage = null)
                        }
                        sendToAll(line)
                        delay(1_500)
                    }
                } catch (t: Throwable) {
                    runCatching { manager.disconnect() }
                    ObdBridgeRuntime.update {
                        it.copy(obdConnected = false, connectionInfo = null,
                            errorMessage = "OBD bağlantısı kesildi: ${t.localizedMessage ?: "yeniden bağlanılıyor"}")
                    }
                    delay(3_000)
                }
            }
        }
    }

    private fun startServer() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        acceptJob = scope.launch {
            try {
                serverSocket = ServerSocket(ObdBridgeCodec.PORT).apply { reuseAddress = true }
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    client.tcpNoDelay = true
                    client.keepAlive = true
                    clients.add(client)
                    updateClientCount()
                    latestLine?.let { send(client, it) }
                }
            } catch (t: Throwable) {
                if (isActive) {
                    ObdBridgeRuntime.update { it.copy(errorMessage = "Hotspot OBD sunucusu açılamadı: ${t.localizedMessage}") }
                }
            }
        }
    }

    private fun sendToAll(line: String) {
        clients.toList().forEach { client ->
            if (!send(client, line)) {
                clients.remove(client)
                runCatching { client.close() }
            }
        }
        updateClientCount()
    }

    private fun send(client: Socket, line: String): Boolean = runCatching {
        synchronized(client) {
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8))
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }.isSuccess

    private fun updateClientCount() {
        ObdBridgeRuntime.update { it.copy(clientCount = clients.count { s -> !s.isClosed }) }
    }

    private fun stopBridgeJobsOnly() {
        bridgeJob?.cancel(); bridgeJob = null
        acceptJob?.cancel(); acceptJob = null
        runCatching { serverSocket?.close() }; serverSocket = null
        clients.forEach { runCatching { it.close() } }; clients.clear()
        latestLine = null
    }

    private fun stopBridge() {
        stopBridgeJobsOnly()
        runBlocking(Dispatchers.IO) { runCatching { manager.disconnect() } }
        ObdBridgeRuntime.reset()
    }

    override fun onDestroy() {
        stopBridgeJobsOnly()
        runBlocking(Dispatchers.IO) { runCatching { manager.disconnect() } }
        ObdBridgeRuntime.reset()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Aldanmaz Drive • OBD Köprü")
                .setContentText("OBD verisi telefon hotspotu üzerinden multimedya sistemine aktarılıyor")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Aldanmaz Drive • OBD Köprü")
                .setContentText("OBD verisi multimedya sistemine aktarılıyor")
                .setOngoing(true)
                .build()
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "OBD Telefon Köprüsü", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "obd_phone_bridge"
        private const val NOTIFICATION_ID = 6601
        const val ACTION_START = "com.aldanmaz.drivedashboard.obdbridge.START"
        const val ACTION_STOP = "com.aldanmaz.drivedashboard.obdbridge.STOP"
        const val EXTRA_ADDRESS = "obd_address"
    }
}
