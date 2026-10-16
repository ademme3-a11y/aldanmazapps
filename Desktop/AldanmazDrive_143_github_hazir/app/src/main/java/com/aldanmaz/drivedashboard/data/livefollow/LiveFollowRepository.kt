package com.aldanmaz.drivedashboard.data.livefollow

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

/**
 * Firebase transport for the two-device live-follow feature.
 *
 * Each installation uses its own Firebase Anonymous Auth UID. The database must
 * remain locked until rules bind the two approved UIDs to their fixed driver roles.
 * Never treat a role selected by the client as authorization by itself.
 */
class LiveFollowRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    enum class DriverRole(val key: String, val otherKey: String) {
        MEHMET("mehmet", "nurdan"),
        NURDAN("nurdan", "mehmet")
    }

    data class RemoteLocation(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val updatedAt: Long = 0L,
        val uid: String = ""
    ) {
        fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
            latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                updatedAt > 0L &&
                nowMillis >= updatedAt &&
                nowMillis - updatedAt <= MAX_LOCATION_AGE_MS
    }

    suspend fun signInInstallation(): String {
        val existing = auth.currentUser
        val user = if (existing != null) {
            existing
        } else {
            auth.signInAnonymously().await().user
        } ?: error("Firebase cihaz kimliği oluşturulamadı.")
        return user.uid
    }

    suspend fun publishLocation(
        role: DriverRole,
        latitude: Double,
        longitude: Double,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        require(latitude in -90.0..90.0) { "Geçersiz enlem." }
        require(longitude in -180.0..180.0) { "Geçersiz boylam." }
        val user = auth.currentUser ?: error("Canlı takip için kimlik doğrulaması gerekli.")
        val payload = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "updatedAt" to timestampMillis,
            "uid" to user.uid
        )
        root().child("locations").child(role.key).setValue(payload).await()
    }

    fun observePartner(
        role: DriverRole,
        onLocation: (RemoteLocation?) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val reference = root().child("locations").child(role.otherKey)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(RemoteLocation::class.java)
                onLocation(location?.takeIf { it.isFresh() })
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        reference.addValueEventListener(listener)
        return { reference.removeEventListener(listener) }
    }

    private fun root(): DatabaseReference = database.reference

    companion object {
        const val MAX_LOCATION_AGE_MS = 60_000L
    }
}
