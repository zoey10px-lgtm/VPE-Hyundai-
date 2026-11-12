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
        val initial = VehicleReadings(odometerKm=800.0,fanSpeedLevel=3)
        val changed = initial.mergeObserved(VehicleReadings(
            cruiseTargetSpeedRaw="13.8889",fanSpeedLevel=4))
        assertEquals(800.0,changed.odometerKm!!,0.0)
        assertEquals("13.8889",changed.cruiseTargetSpeedRaw)
        assertEquals(4,changed.fanSpeedLevel)
    }

    @Test fun evSocTakesPriorityOverFuelToAvoidMaskingConsoleBatteryChanges() {
        val car = VehicleReadings(fuelPercent=80.0,evBatteryLevel=15.0)
        assertEquals(15.0,car.energyPercent()!!,0.0)
        assertEquals("EV_BATTERY_SOC",car.energySource())
        val second = car.mergeObserved(VehicleReadings(evBatteryLevel=10.0))
        assertEquals(10.0,second.energyPercent()!!,0.0)
        assertEquals(80.0,second.fuelPercent!!,0.0)
        val fallback = VehicleReadings(fuelPercent=35.0)
        assertEquals(35.0,fallback.energyPercent()!!,0.0)
        assertEquals("FUEL_PERCENT",fallback.energySource())
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
