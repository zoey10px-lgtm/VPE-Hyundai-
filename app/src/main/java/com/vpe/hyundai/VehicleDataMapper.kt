package com.vpe.hyundai

/**
 * Maps available SDK readings into the engine's normalized snapshot.
 * Missing SDK properties remain missing; they are never silently fabricated.
 * The caller must supply a genuine value with its verified unit.
 */
data class VehicleReadings(
    val odometerKm: Double? = null,
    val fuelPercent: Double? = null,
    val modelYear: Int? = null
)

object VehicleDataMapper {
    fun merge(
        previous: VehicleSnapshot,
        readings: VehicleReadings,
        currentYear: Int
    ): VehicleSnapshot {
        return previous.copy(
            ageYears = readings.modelYear?.let { (currentYear - it).coerceAtLeast(0).toDouble() }
                ?: previous.ageYears,
            odometerKm = readings.odometerKm?.takeIf { it.isFinite() && it >= 0 }
                ?: previous.odometerKm,
            fuelPercent = readings.fuelPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
                ?: previous.fuelPercent
        )
    }
}
