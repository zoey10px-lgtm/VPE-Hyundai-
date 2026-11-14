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
    val passenger: Boolean? = null,
    val hvacAuto: Boolean? = null,
    val safetyWarning: Boolean? = null,
    val tripDistanceRaw: String? = null,
    val tripTimeRaw: String? = null,
    val drivingMode: String? = null,
    val washerFluidWarning: Boolean? = null,
    val engineOilState: String? = null,
    val brakeFluidState: String? = null,
    // Confirmed methods in the actual SDK AAR; raw units must be checked on Pleos AVD.
    val rangeRemainingRaw: String? = null,
    val batteryCapacityRaw: String? = null,
    val cruiseControlState: String? = null,
    val cruiseTargetSpeedRaw: String? = null,
    val fanSpeedLevel: Int? = null
) {
    fun hasMeasuredDrivingData(): Boolean = odometerKm != null
    /**
     * Pleos EV uses battery SOC as the primary energy input. Fuel is a fallback
     * for vehicles without a valid EV SOC; two different energy types must not
     * silently overwrite each other in the persona.
     */
    fun energyPercent(): Double? =
        evBatteryLevel?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?: fuelPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }

    fun energySource(): String? = when {
        evBatteryLevel?.let { it.isFinite() && it in 0.0..100.0 } == true -> "EV_BATTERY_SOC"
        fuelPercent?.let { it.isFinite() && it in 0.0..100.0 } == true -> "FUEL_PERCENT"
        else -> null
    }
    /** A callback may contain only one field; null means no new reading. */
    fun mergeObserved(n: VehicleReadings): VehicleReadings = copy(
        odometerKm = n.odometerKm ?: odometerKm,
        fuelPercent = n.fuelPercent ?: fuelPercent,
        modelYear = n.modelYear ?: modelYear,
        evBatteryLevel = n.evBatteryLevel ?: evBatteryLevel,
        vehicleKey = n.vehicleKey ?: vehicleKey,
        cabinTemperatureC = n.cabinTemperatureC ?: cabinTemperatureC,
        targetTemperatureC = n.targetTemperatureC ?: targetTemperatureC,
        hvacOn = n.hvacOn ?: hvacOn,
        charging = n.charging ?: charging,
        parked = n.parked ?: parked,
        passenger = n.passenger ?: passenger,
        hvacAuto = n.hvacAuto ?: hvacAuto,
        safetyWarning = n.safetyWarning ?: safetyWarning,
        tripDistanceRaw = n.tripDistanceRaw ?: tripDistanceRaw,
        tripTimeRaw = n.tripTimeRaw ?: tripTimeRaw,
        drivingMode = n.drivingMode ?: drivingMode,
        washerFluidWarning = n.washerFluidWarning ?: washerFluidWarning,
        engineOilState = n.engineOilState ?: engineOilState,
        brakeFluidState = n.brakeFluidState ?: brakeFluidState,
        rangeRemainingRaw = n.rangeRemainingRaw ?: rangeRemainingRaw,
        batteryCapacityRaw = n.batteryCapacityRaw ?: batteryCapacityRaw,
        cruiseControlState = n.cruiseControlState ?: cruiseControlState,
        cruiseTargetSpeedRaw = n.cruiseTargetSpeedRaw ?: cruiseTargetSpeedRaw,
        fanSpeedLevel = n.fanSpeedLevel ?: fanSpeedLevel
    )
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
