package com.aldanmaz.drivedashboard.data.obd.bridge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class ObdHotspotClient(private val context: Context) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    var connectedHost: String? = null
        private set

    suspend fun connect(manualHost: String? = null): Result<ObdBridgeSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val network = ObdNetworkInspector.read(context)
            val candidates = buildList {
                manualHost?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                network.wifiGatewayIp?.let(::add)
                add("192.168.43.1")
                add("192.168.159.1")
                add("192.168.1.1")
            }.distinct()

            var lastError: Throwable? = null
            for (host in candidates) {
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.soTimeout = 8_000
                    s.connect(InetSocketAddress(host, ObdBridgeCodec.PORT), 2_500)
                    val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val first = r.readLine() ?: error("Telefon köprüsünden veri gelmedi")
                    val snapshot = ObdBridgeCodec.decode(first)
                    socket = s
                    reader = r
                    connectedHost = host
                    return@runCatching snapshot
                } catch (t: Throwable) {
                    lastError = t
                    disconnectInternal()
                }
            }
            error("Telefon OBD köprüsü bulunamadı: ${lastError?.localizedMessage ?: "Hotspot ağ geçidi erişilemiyor"}")
        }
    }

    suspend fun readSnapshot(): ObdBridgeSnapshot = withContext(Dispatchers.IO) {
        val line = reader?.readLine() ?: error("Telefon OBD köprüsü bağlantısı kapandı")
        ObdBridgeCodec.decode(line)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal() {
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        reader = null
        socket = null
        connectedHost = null
    }
}
