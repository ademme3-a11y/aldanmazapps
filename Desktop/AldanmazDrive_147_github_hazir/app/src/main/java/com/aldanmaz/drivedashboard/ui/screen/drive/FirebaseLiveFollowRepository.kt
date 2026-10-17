package com.aldanmaz.drivedashboard.ui.screen.drive

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Secure paired-device live-follow transport.
 * Supports both legacy lat/lon keys and latitude/longitude keys in RTDB.
 */
class FirebaseLiveFollowRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "live_follow_pairing", Context.MODE_PRIVATE
    )
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance(
        "https://aldanmaz-drive-default-rtdb.europe-west1.firebasedatabase.app/"
    ).reference

    data class RemoteLocation(
        val driverId: String,
        val latitude: Double,
        val longitude: Double,
        val updatedAt: Long,
        val speedKmh: Int? = null,
        val tripDistanceKm: Double? = null,
        val gpsTotalKm: Double? = null,
        val isParkMode: Boolean? = null,
        val parkDurationSeconds: Long? = null
    )

    fun localDeviceId(): String {
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    suspend fun signInAnonymously(): String {
        val current = auth.currentUser
        val user = current ?: auth.signInAnonymously().await().user
        return requireNotNull(user) { "Firebase cihaz kimliği alınamadı." }.uid
    }

    suspend fun pairWith(otherFirebaseUid: String) {
        val ownUid = signInAnonymously()
        require(otherFirebaseUid.isNotBlank() && otherFirebaseUid != ownUid) {
            "Geçerli diğer cihaz UID'sini girin."
        }
        database.child("pairedDevices").child(ownUid).child(otherFirebaseUid)
            .setValue(true).await()
        prefs.edit().putString("paired_uid", otherFirebaseUid).apply()
    }

    suspend fun publishLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        updatedAt: Long = System.currentTimeMillis(),
        speedKmh: Int = 0,
        tripDistanceKm: Double = 0.0,
        gpsTotalKm: Double = 0.0,
        isParkMode: Boolean = false,
        parkDurationSeconds: Long = 0L
    ) {
        require(driverId == "1" || driverId == "2") { "Geçersiz sürücü kimliği." }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Geçersiz GPS koordinatı."
        }
        val uid = signInAnonymously()
        database.child("liveFollow").child(uid).setValue(
            mapOf(
                "driverId" to driverId,
                "deviceId" to localDeviceId(),
                "latitude" to latitude,
                "longitude" to longitude,
                // Keep short aliases for clients/data created by the earlier schema.
                "lat" to latitude,
                "lon" to longitude,
                "updatedAt" to updatedAt,
                "speedKmh" to speedKmh.coerceAtLeast(0),
                "tripDistanceKm" to tripDistanceKm.coerceAtLeast(0.0),
                "gpsTotalKm" to gpsTotalKm.coerceAtLeast(0.0),
                "isParkMode" to isParkMode,
                "parkDurationSeconds" to parkDurationSeconds.coerceAtLeast(0L)
            )
        ).await()
    }

    fun observePairedDevice(
        pairedFirebaseUid: String,
        onLocation: (RemoteLocation?) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        require(pairedFirebaseUid.isNotBlank()) { "Eşleştirilmiş cihaz UID'si gerekli." }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Older records may use lat/lon; newer records use latitude/longitude.
                val lat = snapshot.numberAsDouble("latitude") ?: snapshot.numberAsDouble("lat")
                val lon = snapshot.numberAsDouble("longitude") ?: snapshot.numberAsDouble("lon")
                // Realtime Database can expose driverId as either a string or a number.
                val rawDriver = snapshot.child("driverId").value?.toString()?.trim()
                val driver = rawDriver?.takeIf { it == "1" || it == "2" } ?: "unknown"
                val timestamp = snapshot.numberAsLong("updatedAt") ?: 0L

                // Map display must depend on valid coordinates only. A missing/legacy
                // driverId or stale/missing timestamp must not disable "Haritada göster".
                if (lat == null || lon == null ||
                    lat !in -90.0..90.0 || lon !in -180.0..180.0
                ) {
                    onLocation(null)
                    return
                }
                onLocation(
                    RemoteLocation(
                        driver, lat, lon, timestamp,
                        speedKmh = snapshot.numberAsLong("speedKmh")?.toInt(),
                        tripDistanceKm = snapshot.numberAsDouble("tripDistanceKm"),
                        gpsTotalKm = snapshot.numberAsDouble("gpsTotalKm"),
                        isParkMode = snapshot.child("isParkMode").getValue(Boolean::class.java),
                        parkDurationSeconds = snapshot.numberAsLong("parkDurationSeconds")
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        database.child("liveFollow").child(pairedFirebaseUid).addValueEventListener(listener)
        return listener
    }

    fun removeListener(pairedFirebaseUid: String, listener: ValueEventListener) {
        database.child("liveFollow").child(pairedFirebaseUid).removeEventListener(listener)
    }
}

private fun DataSnapshot.numberAsDouble(vararg keys: String): Double? {
    for (key in keys) {
        val value = child(key).value
        when (value) {
            is Number -> return value.toDouble()
            is String -> value.toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

private fun DataSnapshot.numberAsLong(key: String): Long? {
    return when (val value = child(key).value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}
