package com.vpe.hyundai

/** Values fetched from typed Pleos Vehicle SDK APIs. Null means unverified/unavailable. */
data class VehicleReadings(
    val odometerKm: Double? = null,
    val fuelPercent: Double? = null,
    val modelYear: Int? = null,
    val evBatteryLevel: Double? = null,
    val vehicleKey: String? = null,
    val cabinTemperatureC: Double? = null,
    val targetTemperatureC: Double? = null,
    val hvacOn: Boolean? = null,
    val charging: Boolean? = null,
    val parked: Boolean? = null,
    val safetyWarning: Boolean? = null,
    val tripDistanceRaw: String? = null,
    val tripTimeRaw: String? = null,
    val drivingMode: String? = null,
    val washerFluidWarning: Boolean? = null,
    val engineOilState: String? = null,
    val brakeFluidState: String? = null
) {
    fun hasMeasuredDrivingData(): Boolean = odometerKm != null
    fun energyPercent(): Double? = fuelPercent ?: evBatteryLevel
}

/**
 * Preserve historical fields, replace only actually observed SDK fields.
 * Do not infer a model year, history, maintenance or exploration score.
 */
object VehicleDataMapper {
    fun merge(previous: VehicleSnapshot, readings: VehicleReadings, currentYear: Int): VehicleSnapshot =
        previous.copy(
            ageYears = readings.modelYear?.takeIf { it in 1950..currentYear }
                ?.let { (currentYear - it).toDouble() } ?: previous.ageYears,
            odometerKm = readings.odometerKm?.takeIf { it.isFinite() && it >= 0.0 }
                ?: previous.odometerKm,
            fuelPercent = readings.energyPercent()?.takeIf { it.isFinite() && it in 0.0..100.0 }
                ?: previous.fuelPercent
        )
}
