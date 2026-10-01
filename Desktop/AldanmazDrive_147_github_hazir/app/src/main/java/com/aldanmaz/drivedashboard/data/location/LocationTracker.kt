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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GPS is the primary source.
 *
 * Some Android 12 multimedia units expose the GPS provider but do not deliver
 * callbacks reliably. In that case NETWORK_PROVIDER is kept as a fallback.
 * GPS always wins as soon as a real GPS fix arrives.
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

        var lastGpsFixElapsed = 0L

        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastGpsFixElapsed = SystemClock.elapsedRealtime()
                trySend(location)
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val networkListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Network is only a fallback. Once GPS is producing fixes,
                // never let network location replace the GPS stream.
                val gpsAge = SystemClock.elapsedRealtime() - lastGpsFixElapsed
                if (lastGpsFixElapsed == 0L || gpsAge > 5000L) {
                    trySend(location)
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            // Keep the proven direct GPS behavior from the earlier working
            // versions. This remains the primary source on both devices.
            if (runCatching {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }.getOrDefault(false)
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    800L,
                    0f,
                    gpsListener,
                    Looper.getMainLooper()
                )

                runCatching {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }.getOrNull()?.let {
                    lastGpsFixElapsed = SystemClock.elapsedRealtime()
                    trySend(it)
                }
            }

            // Android 12 multimedia fallback: some units expose NETWORK_PROVIDER
            // even when their GPS callbacks are unavailable.
            if (runCatching {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }.getOrDefault(false)
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1500L,
                    0f,
                    networkListener,
                    Looper.getMainLooper()
                )

                if (lastGpsFixElapsed == 0L) {
                    runCatching {
                        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }.getOrNull()?.let { trySend(it) }
                }
            }

            if (lastGpsFixElapsed == 0L &&
                !runCatching {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }.getOrDefault(false) &&
                !runCatching {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }.getOrDefault(false)
            ) {
                close(IllegalStateException("Konum sağlayıcısı etkin değil"))
                return@callbackFlow
            }
        } catch (error: Throwable) {
            close(error)
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(gpsListener) }
            runCatching { locationManager.removeUpdates(networkListener) }
        }
    }
}
