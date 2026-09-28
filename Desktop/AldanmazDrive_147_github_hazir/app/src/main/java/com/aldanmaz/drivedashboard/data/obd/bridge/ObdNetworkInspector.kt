package com.aldanmaz.drivedashboard.data.obd.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

data class ObdNetworkStatus(
    val wifiConnected: Boolean,
    val cellularInternet: Boolean,
    val validatedInternet: Boolean,
    val wifiGatewayIp: String?,
    val privateIpv4: List<String>
)

object ObdNetworkInspector {
    fun read(context: Context): ObdNetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var wifi = false
        var cell = false
        var validated = false
        var gateway: String? = null

        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            val lp = cm.getLinkProperties(network)
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true
                if (gateway == null) {
                    gateway = lp?.routes
                        ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                        ?.gateway?.hostAddress
                }
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) cell = true
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) validated = true
        }

        val local = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())

        return ObdNetworkStatus(wifi, cell, validated, gateway, local)
    }
}
