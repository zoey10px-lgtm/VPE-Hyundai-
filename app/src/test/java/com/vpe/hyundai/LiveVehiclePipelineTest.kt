package com.vpe.hyundai

import org.junit.Assert.*
import org.junit.Test

/** Regression: partial SDK callbacks -> merged readings -> HPE -> proactive event. */
class LiveVehiclePipelineTest {
    @Test fun changesUpdateMoodAndEventWithoutNewIdentityOrUserPrompt() {
        val key = "vehicle:stable"
        val old = VehicleReadings()
            .mergeObserved(VehicleReadings(odometerKm=850.0))
            .mergeObserved(VehicleReadings(vehicleKey=key))
            .mergeObserved(VehicleReadings(evBatteryLevel=90.0))
        val first = HumanPersonalityEngine.observe(HpeSignals(key,old.odometerKm,old.energyPercent()))
        val updated = old.mergeObserved(VehicleReadings(evBatteryLevel=12.0))
        val second = HumanPersonalityEngine.observe(
            HpeSignals(key,updated.odometerKm,updated.energyPercent()),first)
        assertEquals(first.core,second.core)
        assertTrue(second.affect.hunger > first.affect.hunger)
        assertEquals("low_energy",LiveProactiveEngine().next(updated,second,10000L)?.kind)
    }

    @Test fun newConsoleFieldsMergeWithoutDestroyingKnownMeasurements() {
        val initial = VehicleReadings(odometerKm=800.0,driverOccupied=true)
        val changed = initial.mergeObserved(VehicleReadings(
            cruiseTargetSpeedRaw="13.8889",fanSpeedLevel=4,driverOccupied=false))
        assertEquals(800.0,changed.odometerKm!!,0.0)
        assertEquals(false,changed.driverOccupied)
        assertEquals("13.8889",changed.cruiseTargetSpeedRaw)
        assertEquals(4,changed.fanSpeedLevel)
    }

    @Test fun absentFieldsDoNotEraseMeasuredValuesAndFalseIsKept() {
        val initial = VehicleReadings(odometerKm=12.0,charging=true,evBatteryLevel=40.0)
        val next = initial.mergeObserved(VehicleReadings(charging=false))
            .mergeObserved(VehicleReadings())
        assertEquals(12.0,next.odometerKm!!,0.0)
        assertEquals(40.0,next.evBatteryLevel!!,0.0)
        assertEquals(false,next.charging)
    }
}
