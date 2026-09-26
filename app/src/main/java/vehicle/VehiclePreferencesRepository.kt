package com.aldanmaz.drivedashboard.data.vehicle

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.vehicleDataStore by
preferencesDataStore(
    name = "vehicle_preferences"
)

data class VehiclePreferences(
    val selectedVehicleId: String =
        VehicleCatalog.DEFAULT_CAR_ID,
    val customCarImageUri: String? = null,
    val customCaravanImageUri: String? = null
)

class VehiclePreferencesRepository(
    context: Context
) {
    private val dataStore =
        context.applicationContext.vehicleDataStore

    val preferences: Flow<VehiclePreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                val storedVehicleId =
                    preferences[
                        Keys.SELECTED_VEHICLE_ID
                    ]

                val validVehicleId =
                    VehicleCatalog
                        .findById(storedVehicleId)
                        ?.id
                        ?: VehicleCatalog.DEFAULT_CAR_ID

                VehiclePreferences(
                    selectedVehicleId =
                        validVehicleId,
                    customCarImageUri =
                        preferences[
                            Keys.CUSTOM_CAR_IMAGE_URI
                        ]
                            ?.takeIf { it.isNotBlank() },
                    customCaravanImageUri =
                        preferences[
                            Keys.CUSTOM_CARAVAN_IMAGE_URI
                        ]
                            ?.takeIf { it.isNotBlank() }
                )
            }

    suspend fun saveSelectedVehicle(
        vehicleId: String
    ) {
        val validVehicle =
            VehicleCatalog.findById(vehicleId)
                ?: return

        dataStore.edit { preferences ->
            preferences[
                Keys.SELECTED_VEHICLE_ID
            ] = validVehicle.id
        }
    }

    suspend fun saveCustomVehicleImageUri(
        vehicleType: VehicleType,
        uri: String?
    ) {
        val normalizedUri =
            uri?.trim()?.takeIf { it.isNotEmpty() }

        dataStore.edit { preferences ->
            val key =
                when (vehicleType) {
                    VehicleType.CAR ->
                        Keys.CUSTOM_CAR_IMAGE_URI

                    VehicleType.CARAVAN ->
                        Keys.CUSTOM_CARAVAN_IMAGE_URI
                }

            if (normalizedUri == null) {
                preferences.remove(key)
            } else {
                preferences[key] = normalizedUri
            }
        }
    }

    private object Keys {
        val SELECTED_VEHICLE_ID =
            stringPreferencesKey(
                "selected_vehicle_id"
            )

        val CUSTOM_CAR_IMAGE_URI =
            stringPreferencesKey(
                "custom_car_image_uri"
            )

        val CUSTOM_CARAVAN_IMAGE_URI =
            stringPreferencesKey(
                "custom_caravan_image_uri"
            )
    }
}
