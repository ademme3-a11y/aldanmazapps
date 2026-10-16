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
 * Firebase transport for paired-device live following.
 *
 * The database rules must remain closed until both Firebase UIDs are paired
 * explicitly. This class intentionally does not attempt to open database access.
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
        val updatedAt: Long
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

    suspend fun publishLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        updatedAt: Long = System.currentTimeMillis()
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
                "updatedAt" to updatedAt
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
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lon = snapshot.child("longitude").getValue(Double::class.java)
                val driver = snapshot.child("driverId").getValue(String::class.java)
                val timestamp = snapshot.child("updatedAt").getValue(Long::class.java)
                if (lat == null || lon == null || driver == null || timestamp == null ||
                    driver !in setOf("1", "2") ||
                    System.currentTimeMillis() - timestamp !in 0L..15_000L
                ) {
                    onLocation(null)
                    return
                }
                onLocation(RemoteLocation(driver, lat, lon, timestamp))
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
                onLocation(null)
            }
        }
        database.child("liveFollow").child(pairedFirebaseUid).addValueEventListener(listener)
        return listener
    }

    fun removeListener(pairedFirebaseUid: String, listener: ValueEventListener) {
        database.child("liveFollow").child(pairedFirebaseUid).removeEventListener(listener)
    }
}
