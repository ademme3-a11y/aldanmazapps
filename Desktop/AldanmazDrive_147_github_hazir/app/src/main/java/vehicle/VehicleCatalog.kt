package com.aldanmaz.drivedashboard.data.vehicle

/**
 * Uygulamada seçilebilecek temel araç modları.
 * CAR: otomobil / ticari / kamyon sınıfı
 * CARAVAN: camper van ve çekme karavan
 */
enum class VehicleType {
    CAR,
    CARAVAN
}

enum class VehicleVisualSource {
    FALLBACK_VECTOR,
    DRAWABLE_RESOURCE
}

data class VehicleCatalogItem(
    val id: String,
    val brand: String,
    val model: String,
    val displayName: String,
    val colorName: String,
    val type: VehicleType,
    val visualSource: VehicleVisualSource,
    val drawableName: String? = null,
    val tankCapacityLiters: Double? = null,
    val cityFuelConsumptionLPer100Km: Double? = null,
    val highwayFuelConsumptionLPer100Km: Double? = null,
    val caravanFuelConsumptionLPer100Km: Double? = null
)

/** ALDANMAZ Drive 21 - markasız beyaz araç deposu. */
object VehicleCatalog {

    const val DEFAULT_CAR_ID = "hatchback_white"
    const val DEFAULT_CARAVAN_ID = "camper_van_white"

    private fun car(
        id: String,
        name: String,
        drawable: String
    ) = VehicleCatalogItem(
        id = id,
        brand = "Markasız",
        model = name,
        displayName = name,
        colorName = "Beyaz",
        type = VehicleType.CAR,
        visualSource = VehicleVisualSource.DRAWABLE_RESOURCE,
        drawableName = drawable
    )

    private fun caravan(
        id: String,
        name: String,
        drawable: String
    ) = VehicleCatalogItem(
        id = id,
        brand = "Markasız",
        model = name,
        displayName = name,
        colorName = "Beyaz",
        type = VehicleType.CARAVAN,
        visualSource = VehicleVisualSource.DRAWABLE_RESOURCE,
        drawableName = drawable,
        caravanFuelConsumptionLPer100Km = 8.0
    )

    private val items = listOf(
        car("sedan_white", "Sedan", "vehicle_sedan_white"),
        car(DEFAULT_CAR_ID, "Hatchback", "vehicle_hatchback_white"),
        VehicleCatalogItem(
            id = "dacia_sandero_stepway_2017",
            brand = "Dacia",
            model = "Sandero Stepway 2017",
            displayName = "Dacia Sandero",
            colorName = "Beyaz",
            type = VehicleType.CAR,
            visualSource = VehicleVisualSource.DRAWABLE_RESOURCE,
            drawableName = "vehicle_dacia_sandero_stepway_white",
            tankCapacityLiters = 50.0,
            cityFuelConsumptionLPer100Km = 5.0,
            highwayFuelConsumptionLPer100Km = 4.0,
            caravanFuelConsumptionLPer100Km = 8.0
        ),
        car("suv_white", "SUV", "vehicle_suv_white"),
        car("coupe_white", "Coupe", "vehicle_coupe_white"),
        car("station_wagon_white", "Station Wagon", "vehicle_station_wagon_white"),
        car("cabrio_white", "Cabrio", "vehicle_cabrio_white"),
        car("mini_van_white", "Mini Van", "vehicle_mini_van_white"),
        car("panelvan_white", "Panelvan", "vehicle_panelvan_white"),
        car("pickup_white", "Pickup", "vehicle_pickup_white"),
        car("micro_white", "Micro", "vehicle_micro_white"),
        caravan(DEFAULT_CARAVAN_ID, "Camper Van", "vehicle_camper_van_white"),
        car("truck_white", "Truck", "vehicle_truck_white"),
        caravan("trailer_caravan_white", "Çekme Karavan", "vehicle_trailer_caravan_white")
    )

    fun all(): List<VehicleCatalogItem> = items

    fun cars(): List<VehicleCatalogItem> = items.filter { it.type == VehicleType.CAR }

    fun caravans(): List<VehicleCatalogItem> = items.filter { it.type == VehicleType.CARAVAN }

    fun findById(id: String?): VehicleCatalogItem? = items.firstOrNull { it.id == id }

    fun defaultFor(type: VehicleType): VehicleCatalogItem = when (type) {
        VehicleType.CAR -> requireNotNull(findById(DEFAULT_CAR_ID))
        VehicleType.CARAVAN -> requireNotNull(findById(DEFAULT_CARAVAN_ID))
    }
}
