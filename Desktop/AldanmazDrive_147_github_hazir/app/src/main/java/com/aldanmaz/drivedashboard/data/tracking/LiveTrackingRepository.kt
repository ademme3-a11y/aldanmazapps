package com.aldanmaz.drivedashboard.data.tracking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.liveTrackingDataStore by preferencesDataStore("live_tracking_settings")

// 170: Firebase konsolunda Realtime Database "United States (us-central1)"
// bölgesiyle oluşturulduysa adres budur. Başka bölge seçildiyse konsoldaki
// gerçek "Realtime Database URL" buraya yazılmalı (https://... ile biter).
private const val LIVE_TRACKING_DATABASE_URL = "https://aldanmaz-drive-default-rtdb.firebaseio.com"

data class LiveTrackingSettings(
    val enabled: Boolean = false,
    val vehicleId: String = ""
)

/**
 * 170: "Canlı Takip" — uygulama açıkken ve internet bağlantısı varken aracın
 * konumunu Firebase Realtime Database'e yazar. Telefon tarafında GitHub Pages
 * üzerinde barındırılan ayrı bir harita sayfası bu veriyi okuyup konumu canlı
 * gösterir (bkz. docs/takip/index.html).
 *
 * Güvenlik notu: vehicleId, tahmin edilmesi pratikte imkansız rastgele bir
 * kimliktir (32 karakter). Realtime Database kuralları bu kimliği bilen
 * herkesin ilgili veriyi okuyup yazabilmesine izin verir — yani güvenlik,
 * linkin gizli tutulmasına dayanır (Google/Apple "konum paylaş" linkleriyle
 * aynı mantık). Link başkasının eline geçerse regenerateVehicleId() ile eski
 * link geçersiz kılınıp yenisi üretilebilir.
 */
class LiveTrackingRepository(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val vehicleIdKey = stringPreferencesKey("vehicle_id")

    val settings: Flow<LiveTrackingSettings> = context.liveTrackingDataStore.data.map {
        LiveTrackingSettings(
            enabled = it[enabledKey] ?: false,
            vehicleId = it[vehicleIdKey] ?: ""
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.liveTrackingDataStore.edit { it[enabledKey] = value }
    }

    /** vehicleId daha önce üretilmediyse üretir ve kaydeder; varsa onu döndürür. */
    suspend fun ensureVehicleId(): String {
        val current = context.liveTrackingDataStore.data.map { it[vehicleIdKey] }.first()
        if (!current.isNullOrBlank()) return current
        val generated = newVehicleId()
        context.liveTrackingDataStore.edit { it[vehicleIdKey] = generated }
        return generated
    }

    /** Eski linki geçersiz kılıp yeni, rastgele bir kimlik üretir. */
    suspend fun regenerateVehicleId(): String {
        val generated = newVehicleId()
        context.liveTrackingDataStore.edit { it[vehicleIdKey] = generated }
        return generated
    }

    private fun newVehicleId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Konumu Firebase'e yazar. Takip kapalıysa, vehicleId yoksa ya da internet
     * yoksa hiçbir şey yapmaz ve sessizce çıkar. Çağıran taraf
     * (DriveDashboardViewModel) bu fonksiyonu her GPS fix'inde değil, birkaç
     * saniyede bir (throttled) çağırmalıdır.
     */
    fun pushLocationIfEnabled(
        enabled: Boolean,
        vehicleId: String,
        latitude: Double,
        longitude: Double,
        speedKmh: Int,
        headingDeg: Float
    ) {
        if (!enabled || vehicleId.isBlank()) return
        if (!isOnline()) return
        runCatching {
            val database = FirebaseDatabase.getInstance(LIVE_TRACKING_DATABASE_URL)
            val ref = database.getReference("liveTracking").child(vehicleId).child("location")
            val payload = mapOf(
                "lat" to latitude,
                "lon" to longitude,
                "speedKmh" to speedKmh,
                "headingDeg" to headingDeg,
                "ts" to ServerValue.TIMESTAMP
            )
            ref.setValue(payload)
        }
    }
}
