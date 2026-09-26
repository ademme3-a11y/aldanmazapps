package com.aldanmaz.drivedashboard.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Araç multimedya ünitelerinde FusedLocation bazen ağ/DR konumunu GPS'e karıştırıp
 * araç dururken 8-12 km/s hayalet hız üretebiliyor. Eski çalışan ALD uygulamasındaki
 * davranışa dönerek doğrudan GPS_PROVIDER kullanıyoruz.
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Konum izni verilmedi"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val provider = when {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> LocationManager.GPS_PROVIDER
        }

        try {
            // Eski uygulamada 800 ms doğrudan GPS akışı sahada daha kararlıydı.
            // minDistance=0: duruş fix'i gelirse sıfır hız hemen işlenebilsin.
            locationManager.requestLocationUpdates(
                provider,
                800L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.let { trySend(it) }
        } catch (error: Throwable) {
            close(error)
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }
}
